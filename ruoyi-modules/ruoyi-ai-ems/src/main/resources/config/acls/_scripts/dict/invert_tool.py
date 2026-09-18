#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
invert_tool.py — inverted_intents.json 一体化工具（v6 关键词版）

目录布局：
    acls/
    ├── GLOBAL/
    │   └── _intents.json          ← 意图定义
    ├── dict/
    │   └── inverted_intents.json  ← 生成的词典
    └── _scripts/
        └── dict/
            └── invert_tool.py      ← 本脚本

【v6 核心改动】
1. 长度上限从 8 字收到 5 字，强制生成关键词而非短语
2. 扩展叙述/口令词黑名单，拒绝"确保xxx""准备xxx"等长组合
3. 重写 enrich prompt，明确"关键词不是短语"，给正反例
4. is_word_level 报错信息更明确

【四个子命令】

1. postprocess（后处理：去重、过滤、压缩）
   - 剥掉 surface 中的标点、空白
   - 删除长度超限（>5 字或 <2 字）的变体
   - 删除含叙述词（如"我先"、"确保"）的短语
   - 丢弃置信度低于阈值的条目
   - 同一 surface 对同一 intent 只保留最高分
   - 重算 stats 并写回文件

   示例：
       python invert_tool.py postprocess --word-level --dry-run
       python invert_tool.py postprocess --word-level

2. validate（校验：删掉类型或语义明显错误的条目）
   - 规则过滤：长度超限、含叙述词、残留同音/错别字的，直接删
   - LLM 复核：把同义词、口语、方言批量送 Ollama 判断
     是否真的代表该 intent，不合格的删除

   示例：
       python invert_tool.py validate --apply

3. enrich（补全：调 LLM 生成缺失的关键词）
   - 扫描每个 intent 的覆盖率，找出不足 4 个同义词 / 3 个口语的缺口
   - 调用本地 Ollama qwen2.5，让模型按规则生成 2~4 字关键词
   - 生成结果经过词级过滤后入库
   - 最后重新去重、压缩、写回

   示例：
       python invert_tool.py enrich --only IA_004_check_breathing
       python invert_tool.py enrich --max-intents 10

4. pipeline（一键跑完整流程，默认子命令）
   - 初始 postprocess
   - 循环：validate → enrich → 收敛检查
   - 收尾 postprocess

   示例：
       python invert_tool.py

【常用参数】
    --path PATH             指定 inverted_intents.json 路径（默认自动查找）
    --dry-run               演习模式，只打印不写文件
    --no-backup             不生成 .bak 备份文件
    --host HOST             Ollama 服务地址，默认 http://127.0.0.1:11434
    --model MODEL           Ollama 模型名，默认 qwen2.5:latest
    --min-confidence N      置信度下限，默认 0.5
    --max-rounds N          pipeline 最多循环轮数，默认 5
    --max-intents-per-round N
                            pipeline 每轮最多处理的 intent 数，默认 10

【依赖】
    pip install requests
    本地需运行 Ollama，并已拉取 qwen2.5:latest：
        ollama pull qwen2.5:latest

【典型工作流】
    1. 首次生成或大改后：
           python invert_tool.py postprocess --word-level --dry-run
           python invert_tool.py postprocess --word-level
           python invert_tool.py validate --apply
           python invert_tool.py

    2. 日常维护（只补某个 intent）：
           python invert_tool.py enrich --only IA_004_check_breathing

    3. 完全自动收敛（推荐）：
           python invert_tool.py
"""

import json
import re
import time
import shutil
import argparse
from datetime import datetime
from pathlib import Path
from collections import defaultdict

import requests


# ---------------- 配置 ----------------
DEFAULT_FILENAME = "inverted_intents.json"
INTENTS_FILENAME = "_intents.json"
GLOBAL_DIRNAME = "GLOBAL"
DICT_DIRNAME = "dict"

DEFAULT_HOST = "http://127.0.0.1:11434"
DEFAULT_MODEL = "qwen2.5:latest"
TIMEOUT = 300
MAX_RETRY = 3
RETRY_BACKOFF = 2.0
MAX_TOKENS = 800

TYPE_PRIORITY = {
    "标准": 100, "同义词": 90, "口语": 80, "方言": 70,
    "缩写": 60, "英文": 55, "拼音缩写": 50, "同音": 40, "错别字": 30, "": 0,
}
VALID_TYPES = set(TYPE_PRIORITY.keys())
ENRICHABLE_TYPES = {"同义词", "口语"}

TARGETS = {"同义词": 4, "口语": 3, "同音": 0, "错别字": 0}

# 关键词长度：2~5 字（不是短语）
MIN_VARIANT_LEN = 2
MAX_VARIANT_LEN = 5

# 出现即拒绝：叙述句 / 口令式长短语特征
NARRATIVE_WORDS = [
    # 叙述句
    "我先", "让他", "让她", "让它",
    "看一下", "看一看", "听一下", "听一听", "摸一下", "问一下",
    "情况", "时候", "以后", "然后", "接着",
    "马上", "现在", "赶紧", "立刻",
    # 口令式动词前缀
    "确保", "进行", "开始", "准备",
    # 长连接结构
    "并且", "并准备", "并密封", "并报告", "并确认", "并检查",
]


# ---------------- 标点处理 ----------------
_PUNCT_RE = re.compile(r"[。！？，；、！？；、,;:!?\.\s\u3000\u00a0「」『』【】（）()\[\]{}<>《》\"'`~·—…\-]+")


def strip_punct(s: str) -> str:
    if not s:
        return ""
    return _PUNCT_RE.sub("", s).strip()


# ---------------- 路径解析 ----------------
def _acl_root() -> Path:
    """脚本在 _scripts/dict/ 下，acl 根目录是往上两级。"""
    return Path(__file__).resolve().parent.parent.parent


def resolve_index_path(cli_path=None):
    """
    定位 inverted_intents.json。
    优先级：
        1. 命令行 --path
        2. acls/dict/inverted_intents.json   （标准位置）
        3. 脚本同目录
        4. 脚本上级目录
    """
    if cli_path:
        p = Path(cli_path)
        return p if p.exists() else None

    here = Path(__file__).resolve().parent
    acl_root = _acl_root()
    for c in [
        acl_root / DICT_DIRNAME / DEFAULT_FILENAME,
        here / DEFAULT_FILENAME,
        here.parent / DEFAULT_FILENAME,
    ]:
        if c.exists():
            return c
    return None


def resolve_intents_path(index_path: Path):
    """
    定位 _intents.json。
    优先级：
        1. acls/GLOBAL/_intents.json    （标准位置）
        2. acls/GLOBAL/_index.json
        3. 与 index 同级
    """
    here = Path(__file__).resolve().parent
    acl_root = _acl_root()
    candidates = [
        acl_root / GLOBAL_DIRNAME / INTENTS_FILENAME,
        acl_root / GLOBAL_DIRNAME / "_index.json",
        index_path.parent / INTENTS_FILENAME,
        index_path.parent / "_index.json",
    ]
    for c in candidates:
        if c.exists():
            return c
    return None


def print_candidates():
    here = Path(__file__).resolve().parent
    acl_root = _acl_root()
    for c in [
        acl_root / DICT_DIRNAME / DEFAULT_FILENAME,
        here / DEFAULT_FILENAME,
        here.parent / DEFAULT_FILENAME,
    ]:
        print(f"     - {c}")


def load_intent_keywords(index_path):
    """从 GLOBAL/_intents.json 读 intent_code -> keywords 列表。"""
    p = resolve_intents_path(index_path)
    if p is None:
        return {}
    try:
        with p.open("r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        return {}

    keywords_map = {}

    def walk(intents):
        for it in intents:
            ic = it.get("intent_code")
            kw = it.get("keywords", [])
            if ic and kw:
                keywords_map[ic] = [k for k in kw if isinstance(k, str) and k.strip()]

    for layer in data.get("layers", []):
        for sub in layer.get("sub_domains", []):
            walk(sub.get("intents", []))
    if not keywords_map and "intents" in data:
        walk(data["intents"])

    return keywords_map


# ---------------- IO ----------------
def load_data(path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_data(data, path):
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, separators=(",", ":"))


def backup_file(path):
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = path.with_suffix(path.suffix + f".bak_{ts}")
    shutil.copy2(path, backup)
    print(f"💾 备份: {backup}")


def safe_float(v, default=0.0):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def recompute_stats(entries):
    intent_codes = set()
    for lst in entries.values():
        for ce in lst:
            if ce and ce[0]:
                intent_codes.add(ce[0])
    return {
        "total_intents": len(intent_codes),
        "total_surfaces": len(entries),
        "total_mappings": sum(len(v) for v in entries.values()),
    }


# ---------------- 词级判断 ----------------
def is_word_level(surface: str):
    """判断 surface 是否为合格的关键词（不是短语/句子）。"""
    s = (surface or "").strip()
    if not s:
        return False, "空"
    if len(s) < MIN_VARIANT_LEN:
        return False, f"太短(<{MIN_VARIANT_LEN})"
    if len(s) > MAX_VARIANT_LEN:
        return False, f"太长(>{MAX_VARIANT_LEN}字，非关键词)"
    for w in NARRATIVE_WORDS:
        if w in s:
            return False, f"含叙述/口令词'{w}'"
    return True, ""


# ---------------- Ollama ----------------
def call_ollama(host, model, system_prompt, user_prompt):
    url = host.rstrip("/") + "/api/chat"
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "stream": False,
        "options": {
            "temperature": 0.3,
            "top_p": 0.9,
            "num_predict": MAX_TOKENS,
            "format": "json",
        },
    }
    for attempt in range(1, MAX_RETRY + 1):
        try:
            r = requests.post(url, json=payload, timeout=TIMEOUT)
            r.raise_for_status()
            resp = r.json()
        except Exception as e:
            print(f"   ⚠️ LLM 请求失败 第{attempt}次: {e}")
            if attempt < MAX_RETRY:
                time.sleep(RETRY_BACKOFF * attempt)
                continue
            return None

        content = None
        if isinstance(resp, dict):
            msg = resp.get("message")
            if isinstance(msg, dict):
                content = msg.get("content")
        if not isinstance(content, str) or not content.strip():
            return None
        try:
            return json.loads(content)
        except json.JSONDecodeError:
            s = content.find("{")
            e = content.rfind("}")
            if s != -1 and e > s:
                try:
                    return json.loads(content[s:e + 1])
                except json.JSONDecodeError:
                    return None
    return None


def normalize_llm_response(resp):
    if resp is None:
        return None
    if isinstance(resp, dict):
        return resp
    if isinstance(resp, list):
        if all(isinstance(x, dict) for x in resp) and resp:
            if "surface" in resp[0] and "type" in resp[0]:
                return {"variants": resp}
            if "surface" in resp[0] and "keep" in resp[0]:
                return {"results": resp}
        return {"variants": []}
    return None


# ---------------- postprocess ----------------
def to_compact_entry(entry, intent_meta):
    if isinstance(entry, list):
        if not entry:
            return None
        ic = entry[0] if len(entry) > 0 else ""
        t = entry[1] if len(entry) > 1 else ""
        c = safe_float(entry[2] if len(entry) > 2 else 0.0)
        note = entry[3] if len(entry) > 3 else ""
        return [ic, t, c, note] if note else [ic, t, c]
    if not isinstance(entry, dict):
        return None
    ic = entry.get("intent_code", "") or ""
    t = entry.get("type", "") or ""
    c = safe_float(entry.get("confidence", 0.0))
    note = entry.get("note", "") or ""
    if ic and ic not in intent_meta:
        intent_meta[ic] = {
            "name": entry.get("intent_name", "") or "",
            "normalized": entry.get("normalized", "") or "",
        }
    return [ic, t, c, note] if note else [ic, t, c]


def compact_sort_key(e):
    c = safe_float(e[2] if len(e) > 2 else 0.0)
    t = e[1] if len(e) > 1 else ""
    return (c, TYPE_PRIORITY.get(t, 0))


def do_postprocess(data, min_conf, word_level=False):
    old_entries = data.get("entries", {}) or {}
    old_stats = data.get("stats", {}) or {}

    intent_meta = {}
    existing = data.get("intents")
    if isinstance(existing, dict):
        for k, v in existing.items():
            if isinstance(v, dict):
                intent_meta[k] = {
                    "name": v.get("name", "") or "",
                    "normalized": v.get("normalized", "") or "",
                }

    merged = defaultdict(lambda: defaultdict(list))
    removed_low = removed_dup = removed_empty = 0
    removed_word = 0
    removed_word_examples = []

    for surface_raw, lst in old_entries.items():
        if not isinstance(lst, list) or not lst:
            removed_empty += 1
            continue

        surface = strip_punct(surface_raw) if word_level else surface_raw
        if not surface:
            removed_empty += 1
            continue

        if word_level:
            ok, reason = is_word_level(surface)
            if not ok:
                removed_word += len(lst)
                if len(removed_word_examples) < 20:
                    removed_word_examples.append((surface, reason))
                continue

        for e in lst:
            ce = to_compact_entry(e, intent_meta)
            if ce is None:
                continue
            t, c = ce[1], safe_float(ce[2])
            if t == "标准":
                merged[surface][ce[0]].append(ce)
                continue
            if c < min_conf:
                removed_low += 1
                continue
            merged[surface][ce[0]].append(ce)

    kept = {}
    for surface, by_intent in merged.items():
        final = []
        for ic, entries_list in by_intent.items():
            if len(entries_list) == 1:
                final.append(entries_list[0])
            else:
                entries_list.sort(key=compact_sort_key, reverse=True)
                final.append(entries_list[0])
                removed_dup += len(entries_list) - 1
        if not final:
            continue
        final.sort(key=compact_sort_key, reverse=True)
        kept[surface] = final

    new_stats = recompute_stats(kept)
    data["intents"] = intent_meta
    data["entries"] = kept
    data["stats"] = new_stats
    data["dict_version"] = "2.2.0"
    return data, {
        "old_stats": old_stats, "new_stats": new_stats,
        "removed_low": removed_low, "removed_dup": removed_dup,
        "removed_empty": removed_empty,
        "removed_word": removed_word,
        "removed_word_examples": removed_word_examples,
    }


# ---------------- validate ----------------
VALIDATE_SYSTEM_PROMPT = """你是中文医疗/急救意图路由的审核员。
用户会给出一个 intent 和一批语义类变体（同义词/口语/方言）。
请逐条判断：该变体是否真的代表该 intent。

判断标准：
- 同义词：意思相同或非常接近
- 口语：真实口语中用于表达该意图的说法

严格输出 JSON：
{"results":[{"surface":"...","keep":true/false,"reason":"..."}]}
只输出 JSON，不要解释。"""


def apply_deletions(entries, to_delete):
    by_surface = {}
    for item in to_delete:
        by_surface.setdefault(item["surface"], []).append(item["entry_index"])
    removed = 0
    empty = 0
    for surface, indices in by_surface.items():
        if surface not in entries:
            continue
        for idx in sorted(indices, reverse=True):
            if 0 <= idx < len(entries[surface]):
                entries[surface].pop(idx)
                removed += 1
        if not entries[surface]:
            entries.pop(surface, None)
            empty += 1
    return removed, empty


def do_validate(data, host, model, no_llm, check_semantic, word_level=True):
    entries = data.get("entries", {}) or {}
    intent_meta = data.get("intents", {}) or {}
    old_stats = data.get("stats", {}) or {}

    confirmed_delete = []
    semantic_suspects = []

    for surface, lst in entries.items():
        if not isinstance(lst, list):
            continue

        if word_level:
            ok, reason = is_word_level(surface)
            if not ok:
                for idx, entry in enumerate(lst):
                    if not isinstance(entry, list) or len(entry) < 3:
                        continue
                    if entry[1] == "标准":
                        continue
                    ic = entry[0]
                    meta = intent_meta.get(ic, {})
                    confirmed_delete.append({
                        "surface": surface, "entry_index": idx,
                        "reason": f"词级过滤: {reason}",
                        "intent_code": ic, "intent_name": meta.get("name", ""),
                        "type": entry[1], "confidence": safe_float(entry[2]),
                    })
                continue

        for idx, entry in enumerate(lst):
            if not isinstance(entry, list) or len(entry) < 3:
                continue
            ic, t = entry[0], entry[1]
            if t in ("同音", "错别字"):
                meta = intent_meta.get(ic, {})
                confirmed_delete.append({
                    "surface": surface, "entry_index": idx,
                    "reason": "v5 不再维护同音/错别字",
                    "intent_code": ic, "intent_name": meta.get("name", ""),
                    "type": t, "confidence": safe_float(entry[2]),
                })
            elif t in ("同义词", "口语", "方言"):
                if no_llm:
                    continue
                semantic_suspects.append({
                    "surface": surface, "entry_index": idx,
                    "entry": entry,
                })

    if semantic_suspects and not no_llm:
        BATCH = 15
        for start in range(0, len(semantic_suspects), BATCH):
            batch = semantic_suspects[start:start + BATCH]
            payload = []
            for s in batch:
                entry = s["entry"]
                ic = entry[0]
                meta = intent_meta.get(ic, {})
                payload.append({
                    "surface": s["surface"],
                    "intent_code": ic,
                    "intent_name": meta.get("name", ""),
                    "normalized": meta.get("normalized", ""),
                    "type": entry[1],
                    "confidence": safe_float(entry[2]),
                })
            print(f"   LLM 语义复核批次 {start // BATCH + 1}: {len(batch)} 条")
            resp = call_ollama(
                host, model, VALIDATE_SYSTEM_PROMPT,
                json.dumps({"task": "判断变体是否代表该 intent。",
                            "items": payload}, ensure_ascii=False, indent=2)
            )
            resp = normalize_llm_response(resp)
            verdicts = {}
            if resp and isinstance(resp.get("results"), list):
                for r in resp["results"]:
                    if isinstance(r, dict):
                        verdicts[r.get("surface", "")] = r
            for s in batch:
                surface = s["surface"]
                v = verdicts.get(surface)
                if v is None:
                    continue
                keep = bool(v.get("keep", True))
                reason = v.get("reason", "") or "语义不符"
                if not keep:
                    entry = s["entry"]
                    ic = entry[0]
                    meta = intent_meta.get(ic, {})
                    confirmed_delete.append({
                        "surface": surface, "entry_index": s["entry_index"],
                        "reason": reason,
                        "intent_code": ic, "intent_name": meta.get("name", ""),
                        "type": entry[1], "confidence": safe_float(entry[2]),
                    })

    for d in confirmed_delete[:30]:
        print(f"   ✗ [{d['surface']}] {d['type']} {d['confidence']} "
              f"{d['intent_code']} ← {d['reason']}")
    if len(confirmed_delete) > 30:
        print(f"   ... 其余 {len(confirmed_delete) - 30} 条省略")

    deleted_surfaces = list({d["surface"] for d in confirmed_delete})
    removed, empty = apply_deletions(entries, confirmed_delete)

    data["entries"] = entries
    data["stats"] = recompute_stats(entries)
    data["dict_version"] = "2.2.0"

    return data, {
        "suspects": len(semantic_suspects) + len(confirmed_delete),
        "deleted": removed, "empty_surfaces": empty,
        "old_stats": old_stats, "new_stats": data["stats"],
        "deleted_surfaces": deleted_surfaces,
    }


# ---------------- enrich ----------------
ENRICH_SYSTEM_PROMPT = """你是院前急救 ACLS 意图词典的关键词扩展助手。

任务：为给定 intent 补充 ASR 归一化用的**关键词**（不是短语，不是句子）。

【什么是关键词】
关键词是从意图里提炼出的、用户实际会在通话里说的 2~4 字短词，
通常是一个核心名词，或"短动作 + 核心名词"的组合。

【核心规则 —— 必须严格遵守】
1. 长度 2~4 字最佳，最多 5 字。超过 5 字一律不合格。
2. 每条变体必须能独立作为 ASR 文本片段出现。
3. 禁止生造长短语：不要拼凑成"检查+左右手+紧握"这种结构。
4. 禁止从意图名直接拷贝：意图名是描述，不是关键词。
5. 不要输出标点、空格、序号。
6. 不要重复已有 surface。
7. 只输出 variants 数组，不输出 intent_code。

【如何生成 —— 参照 core_terms】
输入会给出 core_terms（该意图的既有语料关键词）。
你应该：
  - 从 core_terms 派生 2~4 字的近义短词
  - 从核心名词、核心动作衍生
  - 不要凭空想象长组合

【正例 —— 检查呼吸（IA_004）】
  呼吸 / 呼息 / 喘气 / 气息 / 看呼吸 / 听呼吸
【正例 —— 评估脉搏（IA_003）】
  脉搏 / 摸脉 / 搭脉 / 脉动 / 摸脉搏
【正例 —— 开启AED（IA_052）】
  开启AED / 打开AED / 启动AED / AED开机 / 开AED

【反例 —— 不要这样】
  检查左右手紧握      （拼凑，应该是"捏手/握手/握力"）
  看皮肤湿不湿乎乎    （拼凑，应该是"皮肤湿/出汗"）
  打开AED并准备使用   （8字长短语）
  确保心律分析期间无人接触患者  （句子）
  检查患者呼吸情况    （6字长短语）

严格 JSON 输出：
{"variants":[{"surface":"...","type":"同义词|口语","confidence":0.95}]}
只输出 JSON，不要解释。"""


def compute_coverage(data):
    entries = data.get("entries", {}) or {}
    intent_meta = data.get("intents", {}) or {}
    coverage = {}
    existing_surfaces = {}
    for surface, lst in entries.items():
        if not isinstance(lst, list):
            continue
        for ce in lst:
            if not isinstance(ce, list) or len(ce) < 3:
                continue
            ic, t = ce[0], ce[1]
            if not ic:
                continue
            coverage.setdefault(ic, defaultdict(set))[t].add(surface)
            existing_surfaces.setdefault(ic, set()).add(surface)
    return coverage, existing_surfaces, intent_meta


def find_gaps(coverage, intent_meta, targets=None):
    if targets is None:
        targets = TARGETS
    gaps = []
    for ic in intent_meta:
        cov = coverage.get(ic, {})
        missing = {t: need - len(cov.get(t, set()))
                   for t, need in targets.items()
                   if need > 0 and len(cov.get(t, set())) < need}
        if missing:
            gaps.append((ic, missing))
    return gaps


def merge_new_variants(entries, ic, variants, existing_surfaces, min_conf):
    added = 0
    rejected = []

    for v in variants:
        if not isinstance(v, dict):
            continue
        surface_raw = (v.get("surface") or "").strip()
        surface = strip_punct(surface_raw)
        t = v.get("type", "") or ""
        c = safe_float(v.get("confidence", 0.0))
        note = v.get("note", "") or ""

        if not surface or t not in ENRICHABLE_TYPES or c <= 0:
            continue
        if c < min_conf:
            continue
        if surface in existing_surfaces:
            continue

        ok, reason = is_word_level(surface)
        if not ok:
            rejected.append((surface, reason))
            continue

        entry = [ic, t, c, note] if note else [ic, t, c]
        entries.setdefault(surface, []).append(entry)
        existing_surfaces.add(surface)
        added += 1
    return added, rejected


def compact_and_dedup(entries, min_conf):
    kept = {}
    for surface, lst in entries.items():
        if not isinstance(lst, list) or not lst:
            continue
        filtered = []
        for ce in lst:
            if not isinstance(ce, list) or len(ce) < 3:
                continue
            t, c = ce[1], safe_float(ce[2])
            if t == "标准" or c >= min_conf:
                filtered.append(ce)
        if not filtered:
            continue
        grouped = defaultdict(list)
        for ce in filtered:
            grouped[ce[0]].append(ce)
        final = []
        for ic, group in grouped.items():
            group.sort(key=lambda x: (safe_float(x[2]), TYPE_PRIORITY.get(x[1], 0)),
                       reverse=True)
            final.append(group[0])
        final.sort(key=lambda x: (safe_float(x[2]), TYPE_PRIORITY.get(x[1], 0)),
                   reverse=True)
        kept[surface] = final
    return kept


def do_enrich(data, host, model, min_conf, max_intents, only=None,
              targets=None, keywords_map=None):
    coverage, existing_surfaces, intent_meta = compute_coverage(data)
    if targets is None:
        targets = TARGETS
    if keywords_map is None:
        keywords_map = {}

    gaps = find_gaps(coverage, intent_meta, targets)
    if only:
        gaps = [(ic, m) for ic, m in gaps if ic == only]
        if not gaps:
            print(f"   ℹ️ {only} 已达标或不存在")
            return data, {"processed": 0, "added": 0,
                          "gaps_before": 0, "gaps_after": 0,
                          "added_surfaces": []}

    gaps_before = len(gaps)
    if max_intents:
        gaps = gaps[:max_intents]

    entries = data.get("entries", {}) or {}
    total_added = 0
    processed = 0
    added_surfaces = set()

    for i, (ic, missing) in enumerate(gaps, 1):
        meta = intent_meta.get(ic, {})
        name = meta.get("name", "")
        norm = meta.get("normalized", name)
        core_terms = keywords_map.get(ic, [])
        print(f"   [{i}/{len(gaps)}] {ic} ({name}) 缺: {missing}")

        user_prompt = json.dumps({
            "intent_code": ic,
            "intent_name": name,
            "normalized": norm,
            "core_terms": core_terms[:15],
            "existing_surfaces": sorted(existing_surfaces.get(ic, set()))[:40],
            "missing": {t: f"还需要至少 {n} 条" for t, n in missing.items()},
            "constraints": {
                "length": f"{MIN_VARIANT_LEN}~{MAX_VARIANT_LEN} 字（关键词，不是短语）",
                "no_sentence": "禁止整句，禁止叙述句，禁止口令短语，不要标点",
                "from_core_terms": "从 core_terms 派生，不要凭空造长组合",
            }
        }, ensure_ascii=False, indent=2)

        resp = call_ollama(host, model, ENRICH_SYSTEM_PROMPT, user_prompt)
        resp = normalize_llm_response(resp)
        if not resp:
            print("      ❌ 生成失败")
            continue

        variants = resp.get("variants", [])
        if not isinstance(variants, list):
            variants = []

        added, rejected = merge_new_variants(
            entries, ic, variants,
            existing_surfaces.setdefault(ic, set()),
            min_conf,
        )
        total_added += added
        processed += 1
        for s in variants:
            if isinstance(s, dict) and s.get("surface"):
                added_surfaces.add(strip_punct(s["surface"]))
        print(f"      ✅ 新增 {added} 条，拒绝 {len(rejected)} 条")
        for s, r in rejected[:3]:
            print(f"         ✗ [{s}] {r}")

    cleaned = compact_and_dedup(entries, min_conf)
    data["entries"] = cleaned
    data["stats"] = recompute_stats(cleaned)
    data["dict_version"] = "2.2.0"

    coverage2, _, _ = compute_coverage(data)
    gaps_after = len(find_gaps(coverage2, intent_meta, targets))

    return data, {
        "processed": processed, "added": total_added,
        "gaps_before": gaps_before, "gaps_after": gaps_after,
        "added_surfaces": list(added_surfaces),
    }


# ---------------- CLI ----------------
def cmd_postprocess(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME); print_candidates(); return
    print(f"📂 使用文件: {path}")
    data = load_data(path)
    data, r = do_postprocess(data, args.min_confidence, word_level=args.word_level)
    print(f"📊 {r['old_stats']} → {r['new_stats']}")
    print(f"   丢弃低置信度 {r['removed_low']}，重复 {r['removed_dup']}，空 {r['removed_empty']}")
    if args.word_level:
        print(f"   词级过滤删除 {r['removed_word']} 条")
        for s, reason in r['removed_word_examples'][:15]:
            print(f"      ✗ [{s}] {reason}")
        if len(r['removed_word_examples']) >= 15:
            print(f"      ... (只显示前 15 条)")
    if args.dry_run:
        print("🚫 dry-run：未修改"); return
    if not args.no_backup:
        backup_file(path)
    data["postprocessed_at"] = datetime.now().isoformat()
    save_data(data, path)
    print(f"✅ 已写回: {path}")


def cmd_validate(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME); print_candidates(); return
    print(f"📂 使用文件: {path}")
    data = load_data(path)
    print("\n🔍 校验...")
    data, r = do_validate(data, args.host, args.model,
                          no_llm=args.no_llm, check_semantic=args.check_semantic,
                          word_level=not args.no_word_filter)
    print(f"\n📋 可疑 {r['suspects']}，删除 {r['deleted']}")
    if not args.apply or args.dry_run:
        print("🚫 未修改文件"); return
    if not args.no_backup:
        backup_file(path)
    data["validated_at"] = datetime.now().isoformat()
    save_data(data, path)
    print(f"✅ 已写回: {path}")


def cmd_enrich(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME); print_candidates(); return
    print(f"📂 使用文件: {path}")
    data = load_data(path)
    keywords_map = load_intent_keywords(path)
    print(f"   keywords 来源: {resolve_intents_path(path)}")
    print(f"   已加载 {len(keywords_map)} 个 intent 的 keywords")

    coverage, _, intent_meta = compute_coverage(data)
    gaps = find_gaps(coverage, intent_meta)
    if args.only:
        gaps = [(ic, m) for ic, m in gaps if ic == args.only]
    print(f"📋 未达标 intent: {len(gaps)}")
    if args.dry_run:
        for ic, missing in gaps[:50]:
            name = intent_meta.get(ic, {}).get("name", "")
            print(f"   - {ic} ({name}) 缺: {missing}")
        return
    if not args.no_backup:
        backup_file(path)
    data, r = do_enrich(data, args.host, args.model,
                        args.min_confidence, args.max_intents,
                        only=args.only, keywords_map=keywords_map)
    save_data(data, path)
    print(f"\n✅ 处理 {r['processed']} intent，新增 {r['added']}")
    print(f"   缺口: {r['gaps_before']} → {r['gaps_after']}")


def cmd_pipeline(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME); print_candidates(); return
    print(f"📂 使用文件: {path}")
    keywords_map = load_intent_keywords(path)
    print(f"   keywords 来源: {resolve_intents_path(path)}")
    print(f"   已加载 {len(keywords_map)} 个 intent 的 keywords")

    if not args.no_backup:
        backup_file(path)

    data = load_data(path)

    print(f"\n{'='*56}\n🧹 初始 postprocess (词级过滤)\n{'='*56}")
    data, r = do_postprocess(data, args.min_confidence, word_level=True)
    print(f"   {r['old_stats']} → {r['new_stats']}")
    print(f"   词级过滤删除 {r['removed_word']} 条")
    for s, reason in r['removed_word_examples'][:10]:
        print(f"      ✗ [{s}] {reason}")
    save_data(data, path)

    prev_added_surfaces = set()
    stalled_rounds = 0

    for round_no in range(1, args.max_rounds + 1):
        print(f"\n{'='*56}\n🔄 第 {round_no} 轮 / 最多 {args.max_rounds}\n{'='*56}")

        print(f"\n[1/2] validate")
        data, r = do_validate(data, args.host, args.model,
                              no_llm=args.no_llm, check_semantic=False,
                              word_level=True)
        print(f"   可疑 {r['suspects']}，删除 {r['deleted']}")
        save_data(data, path)

        deleted_set = set(r.get("deleted_surfaces", []))
        overlap = len(deleted_set & prev_added_surfaces)

        print(f"\n[2/2] enrich")
        data, r2 = do_enrich(data, args.host, args.model,
                             args.min_confidence, args.max_intents_per_round,
                             keywords_map=keywords_map)
        print(f"   处理 {r2['processed']} intent，新增 {r2['added']}")
        print(f"   缺口: {r2['gaps_before']} → {r2['gaps_after']}")
        save_data(data, path)

        prev_added_surfaces = set(r2["added_surfaces"])

        if r2["gaps_after"] == 0:
            print(f"\n✅ 第 {round_no} 轮后收敛（缺口 0）")
            break

        if r2["added"] == 0:
            stalled_rounds += 1
            if stalled_rounds >= 2:
                print(f"\n⚠️ 连续 {stalled_rounds} 轮 enrich 无新增，停止")
                break
        else:
            stalled_rounds = 0

        if prev_added_surfaces and overlap >= len(prev_added_surfaces) * 0.5:
            print(f"\n⚠️ 第 {round_no} 轮检测到反复：上轮新增 {len(prev_added_surfaces)} 条，"
                  f"本轮被删 {overlap} 条")
            break

        if round_no == args.max_rounds:
            print(f"\n⚠️ 达到最大轮数 {args.max_rounds}")

    print(f"\n{'='*56}\n🧹 收尾 postprocess\n{'='*56}")
    data, r = do_postprocess(data, args.min_confidence, word_level=True)
    print(f"   {r['old_stats']} → {r['new_stats']}")
    data["pipeline_at"] = datetime.now().isoformat()
    data["pipeline_targets"] = TARGETS
    data["word_level"] = True
    save_data(data, path)
    print(f"\n🎉 完成: {path}")


def main():
    parser = argparse.ArgumentParser(
        description="inverted_intents.json 一体化工具（v6 关键词版）"
    )
    sub = parser.add_subparsers(dest="cmd", required=False)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--path", default=None)
    common.add_argument("--dry-run", action="store_true")
    common.add_argument("--no-backup", action="store_true")

    p1 = sub.add_parser("postprocess", parents=[common])
    p1.add_argument("--min-confidence", type=float, default=0.5)
    p1.add_argument("--word-level", action="store_true")

    p2 = sub.add_parser("validate", parents=[common])
    p2.add_argument("--host", default=DEFAULT_HOST)
    p2.add_argument("--model", default=DEFAULT_MODEL)
    p2.add_argument("--apply", action="store_true")
    p2.add_argument("--no-llm", action="store_true")
    p2.add_argument("--check-semantic", action="store_true")
    p2.add_argument("--no-word-filter", action="store_true")

    p3 = sub.add_parser("enrich", parents=[common])
    p3.add_argument("--host", default=DEFAULT_HOST)
    p3.add_argument("--model", default=DEFAULT_MODEL)
    p3.add_argument("--only", default=None)
    p3.add_argument("--min-confidence", type=float, default=0.5)
    p3.add_argument("--max-intents", type=int, default=None)

    p4 = sub.add_parser("pipeline", parents=[common])
    p4.add_argument("--host", default=DEFAULT_HOST)
    p4.add_argument("--model", default=DEFAULT_MODEL)
    p4.add_argument("--no-llm", action="store_true")
    p4.add_argument("--min-confidence", type=float, default=0.5)
    p4.add_argument("--max-rounds", type=int, default=5)
    p4.add_argument("--max-intents-per-round", type=int, default=10)

    parser.add_argument("--path", default=None)
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--no-backup", action="store_true")
    parser.add_argument("--no-llm", action="store_true")
    parser.add_argument("--min-confidence", type=float, default=0.5)
    parser.add_argument("--max-rounds", type=int, default=5)
    parser.add_argument("--max-intents-per-round", type=int, default=10)

    args = parser.parse_args()

    if args.cmd == "postprocess":
        cmd_postprocess(args)
    elif args.cmd == "validate":
        cmd_validate(args)
    elif args.cmd == "enrich":
        cmd_enrich(args)
    elif args.cmd == "pipeline":
        cmd_pipeline(args)
    else:
        if not hasattr(args, "dry_run"):
            args.dry_run = False
        cmd_pipeline(args)


if __name__ == "__main__":
    main()
