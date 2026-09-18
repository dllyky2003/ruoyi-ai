#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用本地 Ollama (qwen2.5:latest) 为 GLOBAL/fast_path_routing.json 中每个 intent
生成并迭代优化用于向量匹配的高质量样本。

v2.0 新增能力：
  - ACLS 临床安全过滤（高危意图零容忍、否定/已完成/设备缺失剔除）
  - 基于 v1.3 定义文件的槽位强制校验
  - 跨意图冲突检查（多意图命中识别）
  - 上下文状态样本生成（route_guard.requires_context）
  - 语义去重（可选 sentence-transformers）
  - 术语归一化
  - 并发处理（意图级）
  - 质量报告 + 高危人工审核队列
  - 断点续跑（checkpoint）

目录结构假定：
    resources/config/acls/
      ├── _scripts/
      │     ├── generate_vector_samples.py           ← 本脚本
      │     └── prompts/
      │           ├── generate_vector_samples_prompt.txt
      │           └── verify_and_refill_vector_samples_prompt.txt
      ├── GLOBAL/
      │     ├── _intents.json
      │     ├── fast_path_routing.json
      │     └── intent_dict_v1.3.json                ← 可选，v1.3 定义
      ├── arrest/
      ├── cases/
      └── _meta/

用法示例：
    python generate_vector_samples.py
    python generate_vector_samples.py --n 30 --workers 4
    python generate_vector_samples.py --only IA_001_approach_patient,IA_003_check_pulse
    python generate_vector_samples.py --resume
    python generate_vector_samples.py --dry-run
    python generate_vector_samples.py --model qwen2.5:latest
    python generate_vector_samples.py --max-rounds 3
    python generate_vector_samples.py --v13 GLOBAL/intent_dict_v1.3.json
    python generate_vector_samples.py --embedding-model BAAI/bge-m3
"""

import json
import re
import sys
import time
import shutil
import argparse
import hashlib
import urllib.request
import urllib.error
from pathlib import Path
from string import Template
from typing import List, Dict, Any, Tuple, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed

try:
    import numpy as np
except ImportError:
    np = None


# ============ 路径配置 ============
SCRIPT_DIR = Path(__file__).parent.absolute()
ACLS_ROOT = SCRIPT_DIR.parent
GLOBAL_DIR = ACLS_ROOT / "GLOBAL"

DEFAULT_FAST_PATH = GLOBAL_DIR / "fast_path_routing.json"

PROMPTS_DIR = SCRIPT_DIR / "prompts"
PROMPT_TEMPLATE_PATH = PROMPTS_DIR / "generate_vector_samples_prompt.txt"
VERIFY_PROMPT_TEMPLATE_PATH = PROMPTS_DIR / "verify_and_refill_vector_samples_prompt.txt"

_CANDIDATE_INTENT_FILES = [
    GLOBAL_DIR / "_intents.json",
    GLOBAL_DIR / "intent.json",
]
_CANDIDATE_V13_FILES = [
    GLOBAL_DIR / "intent_dict_v1.3.json",
    GLOBAL_DIR / "intent_dict.json",
]


def resolve_default_intents_path() -> Path:
    for p in _CANDIDATE_INTENT_FILES:
        if p.exists():
            return p
    return _CANDIDATE_INTENT_FILES[0]


def resolve_default_v13_path() -> Optional[Path]:
    for p in _CANDIDATE_V13_FILES:
        if p.exists():
            return p
    return None


# ============ Ollama 配置 ============
OLLAMA_URL = "http://localhost:11434/api/generate"
DEFAULT_MODEL = "qwen2.5:latest"
DEFAULT_N = 25
DEFAULT_TIMEOUT = 300
DEFAULT_MAX_ROUNDS = 3
DEFAULT_WORKERS = 3


# ============ ACLS 临床规则 ============
# 高危意图：任何样本错误零容忍，全部进入人工审核队列
HIGH_RISK_INTENTS = {
    "IA_048_chest_compressions",
    "IA_052_turn_on_aed",
    "IA_053_attach_defib_pads",
    "IA_054_clear_for_analysis",
    "IA_055_clear_for_shock",
    "IA_056_administer_shock_aed",
    "IA_057_open_airway",
    "IA_059_ventilate_patient",
    "IA_060_suction_airway",
    "IA_061_insert_NPA",
    "IA_061_inset_NPA",  # 兼容原始 typo
}

# 危险模式：出现即拒绝（高危意图 + 全局）
DANGEROUS_PATTERNS = [
    r"不(要|用|必|需).{0,4}(按压|除颤|电击|通气|气道|抢救)",
    r"停(止|下).{0,4}(按压|电击|通气|抢救)",
    r"(拒绝|不做|放弃).{0,4}(CPR|按压|通气|除颤|抢救)",
    r"(已|已经).{0,4}(停止|放弃).{0,4}(复苏|按压|抢救)",
    r"等(一下|会|人|设备).{0,6}(再|才).{0,4}(按压|除颤|通气)",
    r"先(观察|拍照|记录|汇报).{0,6}(再|然后).{0,4}(按压|除颤)",
]

# 否定/已完成/设备缺失：正样本中应剔除
NEGATIVE_SCOPE_PATTERNS = {
    "negation": [
        r"不(需要|用|要|必)\s*.{0,6}",
        r"别\s*.{0,6}",
        r"无需\s*.{0,6}",
        r"不必\s*.{0,6}",
    ],
    "already_done": [
        r"已(经)?(测|量|查|做|完成|记录|确认|贴|接|开)",
        r"(做|查|测|量)过了",
        r"已完成",
    ],
    "device_missing": [
        r"没(有)?(电|带|找到|拿到)",
        r"设备.{0,4}(坏|缺|没|故障)",
        r"(仪|机|设备).{0,4}(不在|没在)",
    ],
    "info_missing": [
        r"不知道",
        r"不清楚",
        r"没问出来",
        r"无法获取",
    ],
}

# 术语归一化表
TERM_NORMALIZATION = {
    "按胸口": "胸外按压",
    "心脏按压": "胸外按压",
    "按压胸口": "胸外按压",
    "电一下": "实施电击",
    "打电": "实施电击",
    "放电": "实施电击",
    "吹气": "实施通气",
    "捏皮球": "球囊通气",
    "捏球囊": "球囊通气",
    "上氧气": "吸氧",
    "插管": "气管插管",
}


# ============ 提示词加载 ============
def load_prompt_template(path: Path) -> Template:
    if not path.exists():
        print(f"❌ 找不到提示词模板文件: {path}")
        sys.exit(1)
    text = path.read_text(encoding="utf-8")
    return Template(text)


# ============ Ollama 调用 ============
def call_ollama(prompt: str,
                model: str = DEFAULT_MODEL,
                temperature: float = 0.8,
                timeout: int = DEFAULT_TIMEOUT) -> str:
    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": temperature,
            "top_p": 0.9,
            "num_predict": 4096,
        },
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA_URL,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.URLError as e:
        raise RuntimeError(f"无法连接 Ollama ({OLLAMA_URL}): {e}") from e

    try:
        result = json.loads(raw)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"Ollama 返回非 JSON: {raw[:200]}") from e
    return result.get("response", "")


# ============ 文件加载 ============
def load_fast_path(path: Path) -> Tuple[Dict[str, Any], Dict[str, Any], Optional[str]]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if isinstance(data, dict) and "fast_path_routing" in data and isinstance(data["fast_path_routing"], dict):
        return data, data["fast_path_routing"].get("intents", {}), "fast_path_routing"
    if isinstance(data, dict) and "intents" in data and isinstance(data["intents"], dict):
        return data, data["intents"], "intents"
    return data, data if isinstance(data, dict) else {}, None


def save_fast_path(path: Path, data: Dict[str, Any]):
    tmp = path.with_suffix(path.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    tmp.replace(path)


def load_intents_for_context(path: Path) -> Dict[str, Dict[str, Any]]:
    """从 _intents.json 提取上下文。"""
    result: Dict[str, Dict[str, Any]] = {}
    if not path.exists():
        return result

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    def visit(intent: Dict[str, Any]):
        code = intent.get("intent_code")
        if not code:
            return
        result[code] = {
            "name_cn": intent.get("name_cn", code),
            "keywords": intent.get("keywords", []),
            "examples": intent.get("examples", []),
            "standard_script": intent.get("standard_script", ""),
            "aliases": intent.get("aliases", []),
        }

    if isinstance(data, dict) and "layers" in data:
        for layer in data.get("layers", []):
            for sd in layer.get("sub_domains", []):
                for it in sd.get("intents", []):
                    visit(it)
    elif isinstance(data, dict) and "intents" in data and isinstance(data["intents"], list):
        for it in data["intents"]:
            visit(it)
    elif isinstance(data, list):
        for it in data:
            visit(it)
    return result


def load_v13_definitions(path: Optional[Path]) -> Dict[str, Dict[str, Any]]:
    """从 v1.3 定义文件加载完整意图定义（trg / slots / route_guard / confusion_pairs）。"""
    result: Dict[str, Dict[str, Any]] = {}
    if not path or not path.exists():
        return result

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    def visit(it: Dict[str, Any]):
        code = it.get("intent_code")
        if not code:
            return
        result[code] = it
        # 兼容 aliases
        for alias in it.get("aliases", []) or []:
            result[alias] = it

    if isinstance(data, dict) and "layers" in data:
        for layer in data.get("layers", []):
            for sd in layer.get("sub_domains", []):
                for it in sd.get("intents", []):
                    visit(it)
    elif isinstance(data, dict) and "intents" in data:
        if isinstance(data["intents"], list):
            for it in data["intents"]:
                visit(it)
        elif isinstance(data["intents"], dict):
            for code, it in data["intents"].items():
                it.setdefault("intent_code", code)
                visit(it)
    return result


# ============ 提示词填充 ============
def build_prompt(template: Template,
                 intent_code: str,
                 name_cn: str,
                 hard_kws: List[str],
                 boost_kws: List[str],
                 existing_examples: List[str],
                 standard_script: str,
                 n: int) -> str:
    kws = list(dict.fromkeys(list(hard_kws) + list(boost_kws)))
    kw_str = "、".join(kws) if kws else "（无）"
    ex_str = "\n".join(f"- {e}" for e in existing_examples) if existing_examples else "（无）"
    ss_str = standard_script or "（无）"

    return template.safe_substitute(
        intent_code=intent_code,
        name_cn=name_cn,
        standard_script=ss_str,
        keywords=kw_str,
        examples=ex_str,
        n=n,
    )


def build_verify_prompt(template: Template,
                        intent_code: str,
                        name_cn: str,
                        hard_kws: List[str],
                        boost_kws: List[str],
                        existing_examples: List[str],
                        standard_script: str,
                        existing_samples: List[str],
                        n: int) -> str:
    kws = list(dict.fromkeys(list(hard_kws) + list(boost_kws)))
    kw_str = "、".join(kws) if kws else "（无）"
    ex_str = "\n".join(f"- {e}" for e in existing_examples) if existing_examples else "（无）"
    ss_str = standard_script or "（无）"
    samples_json = json.dumps(existing_samples, ensure_ascii=False, indent=2)

    return template.safe_substitute(
        intent_code=intent_code,
        name_cn=name_cn,
        standard_script=ss_str,
        keywords=kw_str,
        examples=ex_str,
        existing_samples=samples_json,
        n=n,
    )


# ============ 响应解析 ============
_PREFIX_PATTERNS = [r"【[^】]*】", r"^\s*[-•*]\s*", r"^\s*\d+[\.\)]\s*"]


def clean_sample(s: str) -> str:
    s = s.strip()
    if (s.startswith('"') and s.endswith('"')) or (s.startswith("'") and s.endswith("'")):
        s = s[1:-1].strip()
    for pat in _PREFIX_PATTERNS:
        s = re.sub(pat, "", s).strip()
    return s


def normalize_terms(s: str) -> str:
    for k, v in TERM_NORMALIZATION.items():
        if k in s and v not in s:
            s = s.replace(k, v)
    return s


def dedupe_clean(samples: List[str]) -> List[str]:
    cleaned: List[str] = []
    seen = set()
    for item in samples or []:
        if not isinstance(item, str):
            continue
        s = clean_sample(item)
        s = normalize_terms(s)
        if not s:
            continue
        if len(s) < 4 or len(s) > 80:
            continue
        if s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
    return cleaned


def parse_json_array(text: str) -> List[str]:
    if not text:
        return []

    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\s*```$", "", t)
    t = t.strip()

    start = t.find("[")
    end = t.rfind("]")
    if start == -1 or end == -1 or end <= start:
        return []

    candidate = t[start:end + 1]

    try:
        arr = json.loads(candidate)
    except json.JSONDecodeError:
        fixed = re.sub(r",\s*([\]}])", r"\1", candidate)
        try:
            arr = json.loads(fixed)
        except json.JSONDecodeError:
            arr = re.findall(r'"((?:[^"\\]|\\.)*)"', candidate)
            arr = [a.encode("utf-8").decode("unicode_escape") if "\\u" in a else a for a in arr]

    if not isinstance(arr, list):
        return []

    return dedupe_clean([x for x in arr if isinstance(x, str)])


def parse_json_object(text: str) -> Dict[str, Any]:
    if not text:
        return {}

    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*", "", t, flags=re.IGNORECASE)
    t = re.sub(r"\s*```$", "", t)
    t = t.strip()

    start = t.find("{")
    end = t.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return {}

    candidate = t[start:end + 1]
    obj = None
    for cand in (candidate, re.sub(r",\s*([\]}])", r"\1", candidate)):
        try:
            obj = json.loads(cand)
            break
        except json.JSONDecodeError:
            continue

    if not isinstance(obj, dict):
        return {}

    def clean_list(val: Any) -> List[str]:
        if not isinstance(val, list):
            return []
        return dedupe_clean([x for x in val if isinstance(x, str)])

    keep = clean_list(obj.get("keep"))
    new = clean_list(obj.get("new"))
    final = clean_list(obj.get("final"))
    if not final:
        final = dedupe_clean(keep + new)

    remove: List[Dict[str, str]] = []
    for item in obj.get("remove", []) or []:
        if isinstance(item, dict):
            s = clean_sample(str(item.get("sample", "")))
            reason = str(item.get("reason", "")).strip()
            if s:
                remove.append({"sample": s, "reason": reason})
        elif isinstance(item, str):
            s = clean_sample(item)
            if s:
                remove.append({"sample": s, "reason": ""})

    try:
        shortage = int(obj.get("shortage", 0))
    except Exception:
        shortage = 0
    if shortage < 0:
        shortage = 0

    return {
        "intent_code": obj.get("intent_code", ""),
        "keep": keep,
        "remove": remove,
        "new": new,
        "final": final,
        "shortage": shortage,
    }


# ============ ACLS 安全过滤 ============
def acls_safety_filter(sample: str, intent_code: str) -> Tuple[bool, str]:
    """
    返回 (是否通过, 拒绝原因)
    """
    # 1. 全局危险模式：任何意图都拒绝
    for pat in DANGEROUS_PATTERNS:
        if re.search(pat, sample):
            return False, f"DANGEROUS: {pat}"

    # 2. 否定/已完成/设备缺失/信息缺失：正样本中剔除
    for scope, pats in NEGATIVE_SCOPE_PATTERNS.items():
        for pat in pats:
            if re.search(pat, sample):
                return False, f"NEGATIVE_SCOPE:{scope}"

    return True, ""


# ============ 槽位校验 ============
def slot_validator(sample: str, intent_def: Dict[str, Any]) -> Tuple[bool, str]:
    """
    基于 v1.3 定义的 slots + evidence_policy 做校验。
    仅校验 required slot，不做 reject 决策（保持 downweight 语义）。
    """
    if not intent_def:
        return True, ""

    trg = intent_def.get("trg", {}) or {}
    slots = trg.get("slots", {}) or {}
    policy = trg.get("evidence_policy", {}) or {}
    required = policy.get("slot_required", []) or []

    if not required:
        return True, ""

    for slot_name in required:
        slot_spec = slots.get(slot_name, {}) or {}
        patterns = slot_spec.get("patterns", {}) or {}
        hit = False
        for _, pats in patterns.items():
            if isinstance(pats, list):
                for p in pats:
                    if p in sample:
                        hit = True
                        break
            if hit:
                break
        if not hit:
            return False, f"MISSING_REQUIRED_SLOT: {slot_name}"

    return True, ""


# ============ 跨意图冲突检查 ============
def cross_intent_conflict_check(sample: str,
                                target_intent: str,
                                all_intents: Dict[str, Any],
                                max_hits: int = 2) -> Tuple[bool, List[str]]:
    """
    检查样本是否同时命中其他意图的 hard regex。
    """
    hits: List[str] = []
    for code, cfg in all_intents.items():
        if code == target_intent:
            continue
        hard = (cfg.get("keyword", {}) or {}).get("hard", []) or []
        for kw in hard:
            if kw and kw in sample:
                hits.append(code)
                break

    if len(hits) >= max_hits:
        return False, hits
    return True, hits


# ============ 上下文样本生成 ============
STATE_PREFIX = {
    "CPR": "[STATE=CPR]",
    "CPR_PAUSE": "[STATE=CPR_PAUSE]",
    "AED": "[STATE=AED]",
    "AED_ANALYSIS": "[STATE=AED_ANALYSIS]",
    "AED_SHOCK_READY": "[STATE=AED_SHOCK_READY]",
    "ECG": "[STATE=ECG]",
    "ECG_ACQUISITION": "[STATE=ECG_ACQUISITION]",
    "TRANSPORT": "[STATE=TRANSPORT]",
    "STROKE_ALERT": "[STATE=STROKE_ALERT]",
    "VENT": "[STATE=VENT]",
    "AIRWAY": "[STATE=AIRWAY]",
    "ROSC": "[STATE=ROSC]",
}


def generate_context_samples(intent_def: Dict[str, Any],
                             base_samples: List[str],
                             max_per_state: int = 3) -> List[str]:
    """
    根据 route_guard.requires_context 生成带上下文前缀的变体。
    """
    if not intent_def or not base_samples:
        return []

    guard = intent_def.get("route_guard", {}) or {}
    req = guard.get("requires_context", {}) or {}
    state_any = req.get("state_tags_any", []) or []
    state_all = req.get("state_tags_all", []) or []

    prefixes: List[str] = []
    for tag in (state_any + state_all):
        if tag in STATE_PREFIX:
            prefixes.append(STATE_PREFIX[tag])

    if not prefixes:
        return []

    contextual: List[str] = []
    for p in prefixes:
        for s in base_samples[:max_per_state]:
            ctx_sample = f"{p} {s}"
            if len(ctx_sample) <= 120:
                contextual.append(ctx_sample)
    return dedupe_clean(contextual)


# ============ 语义去重（可选） ============
_EMBED_FN_CACHE: Dict[str, Any] = {}


def load_embedding_fn(model_name: Optional[str]):
    """
    返回一个 embed_fn: List[str] -> np.ndarray，或 None。
    """
    if not model_name or np is None:
        return None
    if model_name in _EMBED_FN_CACHE:
        return _EMBED_FN_CACHE[model_name]
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        print(f"⚠️ 未安装 sentence-transformers，跳过敏义去重")
        _EMBED_FN_CACHE[model_name] = None
        return None
    try:
        model = SentenceTransformer(model_name)
    except Exception as e:
        print(f"⚠️ 加载 embedding 模型失败 ({model_name}): {e}")
        _EMBED_FN_CACHE[model_name] = None
        return None

    def embed_fn(texts: List[str]):
        return model.encode(texts, normalize_embeddings=True, show_progress_bar=False)

    _EMBED_FN_CACHE[model_name] = embed_fn
    return embed_fn


def semantic_dedupe(samples: List[str],
                    embed_fn,
                    threshold: float = 0.92) -> List[str]:
    if not samples or len(samples) <= 1 or embed_fn is None:
        return samples

    embs = embed_fn(samples)
    sim = embs @ embs.T
    keep: List[str] = []
    dropped = set()
    for i in range(len(samples)):
        if i in dropped:
            continue
        keep.append(samples[i])
        for j in range(i + 1, len(samples)):
            if j not in dropped and sim[i, j] > threshold:
                dropped.add(j)
    return keep


# ============ 质量指标 ============
def compute_quality_metrics(samples: List[str],
                            intent_cfg: Dict[str, Any],
                            negative_samples: List[str],
                            embed_fn) -> Dict[str, Any]:
    if not samples:
        return {"count": 0}

    kw = intent_cfg.get("keyword", {}) or {}
    hard = kw.get("hard", []) or []
    boost = kw.get("boost", []) or []

    regex_hit = sum(1 for s in samples if any(k in s for k in hard + boost))
    regex_coverage = regex_hit / len(samples)

    metrics: Dict[str, Any] = {
        "count": len(samples),
        "regex_coverage": round(regex_coverage, 3),
        "avg_length": round(sum(len(s) for s in samples) / len(samples), 1),
    }

    if embed_fn is not None and np is not None and len(samples) > 1:
        embs = embed_fn(samples)
        sim = embs @ embs.T
        n = len(samples)
        avg_sim = float((sim.sum() - n) / (n * (n - 1)))
        metrics["semantic_diversity"] = round(1 - avg_sim, 3)

        if negative_samples:
            neg_embs = embed_fn(negative_samples)
            cross = embs @ neg_embs.T
            metrics["neg_separation"] = round(1 - float(cross.max(axis=1).mean()), 3)

    return metrics


# ============ 单意图生成 ============
def generate_for_intent(intent_code: str,
                        config: Dict[str, Any],
                        context: Dict[str, Any],
                        n: int,
                        model: str,
                        template: Template,
                        dry_run: bool = False,
                        max_retries: int = 3) -> List[str]:
    kw = config.get("keyword", {}) or {}
    hard = kw.get("hard", []) or []
    boost = kw.get("boost", []) or []
    name_cn = context.get("name_cn") or intent_code
    examples = context.get("examples", []) or []
    ss = context.get("standard_script", "") or ""

    prompt = build_prompt(
        template=template,
        intent_code=intent_code,
        name_cn=name_cn,
        hard_kws=hard,
        boost_kws=boost,
        existing_examples=examples,
        standard_script=ss,
        n=n,
    )

    if dry_run:
        print(f"\n----- DRY RUN 生成 PROMPT for {intent_code} -----")
        print(prompt)
        print("----- END -----")
        return []

    best: List[str] = []
    for attempt in range(1, max_retries + 1):
        temp = 0.8 + 0.1 * (attempt - 1)
        try:
            resp = call_ollama(prompt, model=model, temperature=temp)
        except Exception as e:
            print(f"   ⚠️ 第 {attempt} 次调用失败: {e}")
            time.sleep(2)
            continue

        samples = parse_json_array(resp)
        if len(samples) > len(best):
            best = samples
        if len(samples) >= n:
            break
        print(f"   ↻ 第 {attempt} 次生成 {len(samples)} 条，少于目标 {n}，重试...")
        prompt = prompt + f"\n\n注意：上次只生成了 {len(samples)} 条，请务必一次性给出至少 {n} 条，保持 JSON 数组格式。"
        time.sleep(1)

    return best[:n]


# ============ 单意图迭代优化 ============
def optimize_intent_samples(intent_code: str,
                            config: Dict[str, Any],
                            context: Dict[str, Any],
                            n: int,
                            model: str,
                            gen_template: Template,
                            verify_template: Template,
                            v13_def: Optional[Dict[str, Any]] = None,
                            all_intents: Optional[Dict[str, Any]] = None,
                            embed_fn=None,
                            dry_run: bool = False,
                            max_rounds: int = DEFAULT_MAX_ROUNDS,
                            max_retries: int = 3,
                            enable_context_samples: bool = True) -> List[str]:
    """
    对单个 intent 的样本进行迭代优化。
    新增：ACLS 安全过滤、槽位校验、跨意图冲突、语义去重、上下文样本。
    """
    kw = config.get("keyword", {}) or {}
    hard = kw.get("hard", []) or []
    boost = kw.get("boost", []) or []
    name_cn = context.get("name_cn") or intent_code
    examples = context.get("examples", []) or []
    ss = context.get("standard_script", "") or ""

    current = dedupe_clean(config.get("samples", []) or [])

    if dry_run:
        if not current:
            print(f"\n----- DRY RUN 初始生成 PROMPT for {intent_code} -----")
            generate_for_intent(
                intent_code=intent_code, config=config, context=context,
                n=n, model=model, template=gen_template, dry_run=True,
            )
        else:
            print(f"\n----- DRY RUN 审核 PROMPT for {intent_code} -----")
            prompt = build_verify_prompt(
                template=verify_template, intent_code=intent_code, name_cn=name_cn,
                hard_kws=hard, boost_kws=boost, existing_examples=examples,
                standard_script=ss, existing_samples=current, n=n,
            )
            print(prompt)
            print("----- END -----")
        return current

    if not current:
        print("   初始样本为空，先生成候选样本...")
        current = generate_for_intent(
            intent_code=intent_code, config=config, context=context,
            n=n, model=model, template=gen_template,
            dry_run=False, max_retries=max_retries,
        )
        current = dedupe_clean(current)
        if not current:
            print("   ❌ 初始生成失败，跳过")
            return []

    no_new_rounds = 0
    prev_count = 0

    for rnd in range(1, max_rounds + 1):
        print(f"   🔍 第 {rnd}/{max_rounds} 轮审核，当前 {len(current)} 条")

        prompt = build_verify_prompt(
            template=verify_template, intent_code=intent_code, name_cn=name_cn,
            hard_kws=hard, boost_kws=boost, existing_examples=examples,
            standard_script=ss, existing_samples=current, n=n,
        )

        try:
            resp = call_ollama(prompt, model=model, temperature=0.2)
        except Exception as e:
            print(f"   ⚠️ 审核调用失败: {e}，保留当前样本")
            break

        obj = parse_json_object(resp)
        if not obj:
            print("   ⚠️ 审核响应解析失败，保留当前样本")
            break

        keep = obj.get("keep", [])
        remove = obj.get("remove", [])
        new = obj.get("new", [])
        final = obj.get("final", [])
        if not final:
            final = keep + new
        final = dedupe_clean(final)

        if remove:
            print(f"   🗑️ 本轮删除 {len(remove)} 条")
            for item in remove:
                print(f"      - {item.get('sample')} | 原因: {item.get('reason')}")
        else:
            print("   ✅ 本轮无需删除")

        current = final[:n]
        print(f"   📊 审核后合格 {len(current)} 条")

        if len(current) >= n:
            print(f"   ✅ 已达到目标 {n} 条，停止迭代")
            break

        if len(current) <= prev_count and not new:
            no_new_rounds += 1
            print(f"   ⏸️ 连续无新增合格样本轮次: {no_new_rounds}")
        else:
            no_new_rounds = 0

        prev_count = len(current)

        if no_new_rounds >= 2:
            print("   ⏹️ 连续两轮无新增合格样本，停止迭代，不硬凑")
            break

        shortage = max(0, n - len(current))
        if shortage <= 0:
            break

        print(f"   🧩 需要补生成 {shortage} 条")
        gen_samples = generate_for_intent(
            intent_code=intent_code, config=config, context=context,
            n=shortage, model=model, template=gen_template,
            dry_run=False, max_retries=max_retries,
        )
        gen_samples = dedupe_clean(gen_samples)

        if not gen_samples:
            no_new_rounds += 1
            print(f"   ⚠️ 未生成有效补样本，连续无新增轮次: {no_new_rounds}")
            if no_new_rounds >= 2:
                print("   ⏹️ 连续两轮无新增合格样本，停止迭代，不硬凑")
                break
            continue

        merged = dedupe_clean(current + gen_samples)
        current = merged
        print(f"   ➕ 补生成 {len(gen_samples)} 条，合并后 {len(current)} 条，进入下一轮审核")

    # ===== 后处理：ACLS 安全 + 槽位 + 跨意图 + 语义去重 + 上下文 =====
    current = _postprocess_samples(
        intent_code=intent_code,
        samples=current,
        v13_def=v13_def or {},
        all_intents=all_intents or {},
        embed_fn=embed_fn,
        enable_context_samples=enable_context_samples,
    )

    return current[:n]


# ============ 后处理 ============
def _postprocess_samples(intent_code: str,
                         samples: List[str],
                         v13_def: Dict[str, Any],
                         all_intents: Dict[str, Any],
                         embed_fn,
                         enable_context_samples: bool = True) -> List[str]:
    if not samples:
        return samples

    # 1. ACLS 安全过滤
    safe: List[str] = []
    rejected: List[Tuple[str, str]] = []
    for s in samples:
        ok, reason = acls_safety_filter(s, intent_code)
        if not ok:
            rejected.append((s, reason))
            continue
        safe.append(s)

    if rejected:
        print(f"   🚫 ACLS 安全过滤剔除 {len(rejected)} 条")
        for s, r in rejected[:5]:
            print(f"      - {s} | {r}")

    # 2. 槽位校验
    slot_rejected: List[Tuple[str, str]] = []
    slot_passed: List[str] = []
    for s in safe:
        ok, reason = slot_validator(s, v13_def)
        if not ok:
            slot_rejected.append((s, reason))
            continue
        slot_passed.append(s)

    if slot_rejected:
        print(f"   ⚠️ 槽位校验剔除 {len(slot_rejected)} 条")
        for s, r in slot_rejected[:5]:
            print(f"      - {s} | {r}")

    # 3. 跨意图冲突
    conflict_rejected: List[Tuple[str, List[str]]] = []
    conflict_passed: List[str] = []
    for s in slot_passed:
        ok, conflicts = cross_intent_conflict_check(s, intent_code, all_intents)
        if not ok:
            conflict_rejected.append((s, conflicts))
            continue
        conflict_passed.append(s)

    if conflict_rejected:
        print(f"   ⚠️ 跨意图冲突剔除 {len(conflict_rejected)} 条")
        for s, cs in conflict_rejected[:5]:
            print(f"      - {s} | 冲突: {cs}")

    # 4. 语义去重
    if embed_fn is not None and len(conflict_passed) > 1:
        before = len(conflict_passed)
        conflict_passed = semantic_dedupe(conflict_passed, embed_fn)
        if len(conflict_passed) < before:
            print(f"   🔁 语义去重: {before} → {len(conflict_passed)}")

    # 5. 上下文样本（可选）
    if enable_context_samples and v13_def:
        ctx = generate_context_samples(v13_def, conflict_passed)
        if ctx:
            conflict_passed = dedupe_clean(conflict_passed + ctx)
            print(f"   🌐 加入 {len(ctx)} 条上下文样本")

    return conflict_passed


# ============ 质量报告 & 人工审核队列 ============
def save_quality_report(report: Dict[str, Any], path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
        f.write("\n")


def append_human_review_queue(intent_code: str,
                              samples: List[str],
                              path: Path,
                              reason: str = "high_risk"):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        for s in samples:
            f.write(json.dumps({
                "intent_code": intent_code,
                "sample": s,
                "reason": reason,
                "ts": time.time(),
            }, ensure_ascii=False) + "\n")


# ============ Checkpoint ============
def _checkpoint_path(ckpt_dir: Path, intent_code: str) -> Path:
    h = hashlib.md5(intent_code.encode("utf-8")).hexdigest()[:8]
    return ckpt_dir / f"{intent_code}_{h}.json"


def save_checkpoint(ckpt_dir: Path, intent_code: str, samples: List[str]):
    ckpt_dir.mkdir(parents=True, exist_ok=True)
    p = _checkpoint_path(ckpt_dir, intent_code)
    with open(p, "w", encoding="utf-8") as f:
        json.dump({"intent_code": intent_code, "samples": samples}, f, ensure_ascii=False, indent=2)


def load_checkpoint(ckpt_dir: Path, intent_code: str) -> Optional[List[str]]:
    p = _checkpoint_path(ckpt_dir, intent_code)
    if not p.exists():
        return None
    try:
        with open(p, "r", encoding="utf-8") as f:
            d = json.load(f)
        return d.get("samples")
    except Exception:
        return None


# ============ 主流程 ============
def main():
    default_intents_path = resolve_default_intents_path()
    default_v13_path = resolve_default_v13_path()

    parser = argparse.ArgumentParser(
        description="使用本地 Ollama 为 GLOBAL/fast_path_routing.json 生成并迭代优化向量匹配样本"
    )
    parser.add_argument("--fast-path", default=str(DEFAULT_FAST_PATH))
    parser.add_argument("--intents", default=str(default_intents_path))
    parser.add_argument("--v13", default=str(default_v13_path) if default_v13_path else None,
                        help="v1.3 定义文件路径（用于槽位/route_guard/confusion_pairs）")
    parser.add_argument("--prompt-template", default=str(PROMPT_TEMPLATE_PATH))
    parser.add_argument("--verify-prompt-template", default=str(VERIFY_PROMPT_TEMPLATE_PATH))
    parser.add_argument("--n", type=int, default=DEFAULT_N)
    parser.add_argument("--max-rounds", type=int, default=DEFAULT_MAX_ROUNDS)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--only", default=None)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--no-backup", action="store_true")
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS,
                        help=f"并发数（默认 {DEFAULT_WORKERS}）")
    parser.add_argument("--embedding-model", default=None,
                        help="sentence-transformers 模型名，用于语义去重与质量指标")
    parser.add_argument("--dedupe-threshold", type=float, default=0.92)
    parser.add_argument("--no-context-samples", action="store_true",
                        help="不生成上下文状态样本")
    parser.add_argument("--checkpoint-dir", default=None,
                        help="断点续跑目录")
    parser.add_argument("--report", default=None,
                        help="质量报告输出路径")
    parser.add_argument("--human-review-queue", default=None,
                        help="高危意图人工审核队列输出（JSONL）")
    parser.add_argument("--high-risk-only", action="store_true",
                        help="只处理高危意图")

    args = parser.parse_args()

    fp_path = Path(args.fast_path).resolve()
    intents_path = Path(args.intents).resolve()
    gen_template_path = Path(args.prompt_template).resolve()
    verify_template_path = Path(args.verify_prompt_template).resolve()
    v13_path = Path(args.v13).resolve() if args.v13 else None

    if not fp_path.exists():
        print(f"❌ 找不到 fast_path_routing 文件: {fp_path}")
        sys.exit(1)

    print(f"🚀 开始生成并迭代优化向量匹配样本 (v2.0)")
    print(f"   ACLS_ROOT     : {ACLS_ROOT}")
    print(f"   fast_path     : {fp_path}")
    print(f"   intents       : {intents_path} (exists={intents_path.exists()})")
    print(f"   v13_def       : {v13_path} (exists={v13_path.exists() if v13_path else False})")
    print(f"   gen_tmpl      : {gen_template_path} (exists={gen_template_path.exists()})")
    print(f"   verify_tmpl   : {verify_template_path} (exists={verify_template_path.exists()})")
    print(f"   model         : {args.model}")
    print(f"   n/intent      : {args.n}")
    print(f"   max_rounds    : {args.max_rounds}")
    print(f"   workers       : {args.workers}")
    print(f"   embed_model   : {args.embedding_model}")

    gen_template = load_prompt_template(gen_template_path)
    verify_template = load_prompt_template(verify_template_path)

    if not args.dry_run and not args.no_backup:
        backup = fp_path.with_suffix(fp_path.suffix + ".bak")
        shutil.copy2(fp_path, backup)
        print(f"💾 已备份原文件到: {backup}")

    data, intents_cfg, container_key = load_fast_path(fp_path)
    if not intents_cfg:
        print("❌ fast_path_routing 中未找到任何 intents 配置")
        sys.exit(1)

    context_map = load_intents_for_context(intents_path)
    print(f"📚 从 {intents_path.name} 加载到 {len(context_map)} 条意图上下文")

    v13_defs = load_v13_definitions(v13_path)
    if v13_defs:
        print(f"📚 从 {v13_path.name} 加载到 {len(v13_defs)} 条 v1.3 意图定义")

    embed_fn = load_embedding_fn(args.embedding_model)
    if embed_fn is not None:
        print(f"🧠 已加载 embedding 模型: {args.embedding_model}")

    only_set = set(x.strip() for x in args.only.split(",")) if args.only else None
    target_codes = [
        code for code in intents_cfg.keys()
        if (only_set is None or code in only_set)
    ]
    if args.high_risk_only:
        target_codes = [c for c in target_codes if c in HIGH_RISK_INTENTS]

    print(f"📋 待处理意图数量: {len(target_codes)}")

    ckpt_dir = Path(args.checkpoint_dir).resolve() if args.checkpoint_dir else None
    report_path = Path(args.report).resolve() if args.report else fp_path.parent / "quality_report.json"
    review_path = Path(args.human_review_queue).resolve() if args.human_review_queue else fp_path.parent / "human_review_queue.jsonl"

    total_written = 0
    quality_report: Dict[str, Any] = {}
    start_time = time.time()

    # ===== 并发执行 =====
    def _task(code: str) -> Tuple[str, Optional[List[str]]]:
        config = intents_cfg[code]
        existing_samples = config.get("samples", []) or []
        if args.resume and existing_samples and len(existing_samples) >= args.n:
            return code, existing_samples  # skip

        # checkpoint
        if ckpt_dir:
            ckpt = load_checkpoint(ckpt_dir, code)
            if ckpt and len(ckpt) >= args.n:
                return code, ckpt

        ctx = context_map.get(code, {})
        samples = optimize_intent_samples(
            intent_code=code,
            config=config,
            context=ctx,
            n=args.n,
            model=args.model,
            gen_template=gen_template,
            verify_template=verify_template,
            v13_def=v13_defs.get(code, {}),
            all_intents=intents_cfg,
            embed_fn=embed_fn,
            dry_run=args.dry_run,
            max_rounds=args.max_rounds,
            enable_context_samples=not args.no_context_samples,
        )
        return code, samples

    if args.dry_run:
        # 顺序 dry-run，便于查看
        for idx, code in enumerate(target_codes, 1):
            config = intents_cfg[code]
            ctx = context_map.get(code, {})
            name_cn = ctx.get("name_cn", code)
            print(f"[{idx}/{len(target_codes)}] 🔄 {code} ({name_cn})")
            optimize_intent_samples(
                intent_code=code, config=config, context=ctx,
                n=args.n, model=args.model,
                gen_template=gen_template, verify_template=verify_template,
                v13_def=v13_defs.get(code, {}),
                all_intents=intents_cfg,
                embed_fn=embed_fn,
                dry_run=True, max_rounds=args.max_rounds,
                enable_context_samples=not args.no_context_samples,
            )
        print("\n✅ DRY RUN 完成")
        return

    results: Dict[str, Optional[List[str]]] = {}
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
        future_to_code = {executor.submit(_task, code): code for code in target_codes}
        for idx, fut in enumerate(as_completed(future_to_code), 1):
            code = future_to_code[fut]
            try:
                _, samples = fut.result()
                results[code] = samples
                n_s = len(samples) if samples else 0
                print(f"[{idx}/{len(target_codes)}] ✅ {code} → {n_s} 条")
            except Exception as e:
                print(f"[{idx}/{len(target_codes)}] ❌ {code} 失败: {e}")
                results[code] = None

    # ===== 写回 + 质量报告 + 人工审核队列 =====
    for code in target_codes:
        samples = results.get(code)
        if not samples:
            print(f"   ⚠️ {code} 无有效样本，保留原 samples")
            continue

        config = intents_cfg[code]
        config["samples"] = samples
        total_written += len(samples)

        # 质量指标
        neg_texts = [
            x["text"] for x in (config.get("samples_negative", []) or [])
            if isinstance(x, dict) and x.get("text")
        ]
        metrics = compute_quality_metrics(samples, config, neg_texts, embed_fn)
        quality_report[code] = metrics

        # 高危意图进入人工审核队列
        if code in HIGH_RISK_INTENTS:
            append_human_review_queue(code, samples, review_path, reason="high_risk")

        # checkpoint
        if ckpt_dir:
            save_checkpoint(ckpt_dir, code, samples)

        # 增量落盘
        if container_key == "fast_path_routing":
            data["fast_path_routing"]["intents"] = intents_cfg
        elif container_key == "intents":
            data["intents"] = intents_cfg
        else:
            data = intents_cfg
        save_fast_path(fp_path, data)

    # ===== 报告 =====
    save_quality_report(quality_report, report_path)

    print(f"\n{'='*60}")
    print(f"🎉 完成！共写入 {total_written} 条样本到 {fp_path}")
    print(f"⏱️ 总用时: {time.time() - start_time:.1f}s")
    print(f"📄 输出文件: {fp_path}")
    print(f"📊 质量报告: {report_path}")
    if any(c in HIGH_RISK_INTENTS for c in target_codes):
        print(f"🧑‍⚕️ 人工审核队列: {review_path}")
    if not args.no_backup:
        print(f"🗂️ 备份文件: {fp_path.with_suffix(fp_path.suffix + '.bak')}")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
