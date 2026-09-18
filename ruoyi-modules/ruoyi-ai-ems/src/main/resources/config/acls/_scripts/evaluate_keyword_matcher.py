#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用本地 Ollama (qwen2.5:latest) 为 GLOBAL/fast_path_routing.json 中每个 intent
自动生成用于向量匹配计算的高质量样本。

当前目录结构假定：
    resources/config/acls/
      ├── _scripts/
      │     └── generate_vector_samples.py   ← 本脚本
      ├── GLOBAL/
      │     ├── _intents.json                ← 意图定义（第一个文件）
      │     └── fast_path_routing.json       ← 关键词路由（第二个文件，会被更新）
      ├── arrest/
      ├── cases/
      └── _meta/

生成结果写回 fast_path_routing.json，每个 intent 新增 "samples" 字段。

用法示例：
    # 默认：每个 intent 生成 25 条样本，就地更新 GLOBAL/fast_path_routing.json
    python generate_vector_samples.py

    # 自定义每个 intent 的样本数量
    python generate_vector_samples.py --n 30

    # 只处理部分 intent（逗号分隔）
    python generate_vector_samples.py --only IA_001_approach_patient,IA_003_check_pulse

    # 断点续跑：跳过已有 samples 的 intent
    python generate_vector_samples.py --resume

    # 只打印 prompt，不调用模型
    python generate_vector_samples.py --dry-run

    # 指定模型
    python generate_vector_samples.py --model qwen2.5:latest
"""

import json
import re
import sys
import time
import shutil
import argparse
import urllib.request
import urllib.error
from pathlib import Path
from typing import List, Dict, Any, Tuple, Optional


# ============ 路径配置 ============
SCRIPT_DIR = Path(__file__).parent.absolute()
ACLS_ROOT = SCRIPT_DIR.parent
GLOBAL_DIR = ACLS_ROOT / "GLOBAL"

DEFAULT_FAST_PATH = GLOBAL_DIR / "fast_path_routing.json"

# 优先用 _intents.json，找不到再回退到 intent.json
_CANDIDATE_INTENT_FILES = [
    GLOBAL_DIR / "_intents.json",
    GLOBAL_DIR / "intent.json",
]


def resolve_default_intents_path() -> Path:
    for p in _CANDIDATE_INTENT_FILES:
        if p.exists():
            return p
    return _CANDIDATE_INTENT_FILES[0]


# ============ Ollama 配置 ============
OLLAMA_URL = "http://localhost:11434/api/generate"
DEFAULT_MODEL = "qwen2.5:latest"
DEFAULT_N = 25
DEFAULT_TIMEOUT = 300


# ============ Ollama 调用 ============
def call_ollama(prompt: str,
                model: str = DEFAULT_MODEL,
                temperature: float = 0.8,
                timeout: int = DEFAULT_TIMEOUT) -> str:
    """调用本地 Ollama，返回模型生成的文本。"""
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
    """加载 fast_path_routing.json，返回 (顶层data, intents dict, 容器key)。"""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if isinstance(data, dict) and "fast_path_routing" in data and isinstance(data["fast_path_routing"], dict):
        return data, data["fast_path_routing"].get("intents", {}), "fast_path_routing"
    if isinstance(data, dict) and "intents" in data and isinstance(data["intents"], dict):
        return data, data["intents"], "intents"
    return data, data if isinstance(data, dict) else {}, None


def save_fast_path(path: Path, data: Dict[str, Any]):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def load_intents_for_context(path: Path) -> Dict[str, Dict[str, Any]]:
    """从 _intents.json 提取每个 intent_code 的元数据用于提示词上下文。"""
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


# ============ 提示词构造 ============
def build_prompt(intent_code: str,
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

    prompt = f"""你是急救医疗 (EMS) 领域的专家，正在为一个意图识别系统的【向量匹配模块】生成中文训练样本。

【目标意图】
- 编码: {intent_code}
- 名称: {name_cn}
- 标准动作描述: {ss_str}
- 关键词参考: {kw_str}
- 已有少量示例:
{ex_str}

【任务】
生成 {n} 条高质量、多样化的中文自然语言样本，用于向量相似度匹配训练。

【硬性要求】
1. 每条样本是一句急救人员或调度员在真实工作场景中可能说出的中文句子。
2. 表达必须多样：口语化 / 正式 / 简短指令 / 完整描述 / 带语气词，都要覆盖。
3. 尽量覆盖同义词、近义短语、不同句式（陈述句、祈使句、疑问句、含场景的复合句）。
4. 每条样本必须只指向【目标意图】，不要与其他意图混淆（例如"检查脉搏"不要写成"检查呼吸"）。
5. 不要出现"正例/负例/示例"等标记，不要带【】括号。
6. 每条样本独立完整，不带编号、不带解释、不带引号。

【输出格式】
只输出一个 JSON 数组，例如：
["样本1","样本2","样本3", ...]

不要输出 markdown 代码块，不要输出任何额外文字或说明。
"""
    return prompt


# ============ 响应解析 ============
_PREFIX_PATTERNS = [r"【[^】]*】", r"^\s*[-•*]\s*", r"^\s*\d+[\.\)]\s*"]


def clean_sample(s: str) -> str:
    s = s.strip()
    if (s.startswith('"') and s.endswith('"')) or (s.startswith("'") and s.endswith("'")):
        s = s[1:-1].strip()
    for pat in _PREFIX_PATTERNS:
        s = re.sub(pat, "", s).strip()
    return s


def parse_json_array(text: str) -> List[str]:
    """从模型响应中提取 JSON 数组；尽量宽容。"""
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

    cleaned: List[str] = []
    seen = set()
    for item in arr:
        if not isinstance(item, str):
            continue
        s = clean_sample(item)
        if not s:
            continue
        if len(s) < 4 or len(s) > 80:
            continue
        if s in seen:
            continue
        seen.add(s)
        cleaned.append(s)
    return cleaned


# ============ 单意图生成 ============
def generate_for_intent(intent_code: str,
                        config: Dict[str, Any],
                        context: Dict[str, Any],
                        n: int,
                        model: str,
                        dry_run: bool = False,
                        max_retries: int = 3) -> List[str]:
    kw = config.get("keyword", {}) or {}
    hard = kw.get("hard", []) or []
    boost = kw.get("boost", []) or []
    name_cn = context.get("name_cn") or intent_code
    examples = context.get("examples", []) or []
    ss = context.get("standard_script", "") or ""

    prompt = build_prompt(intent_code, name_cn, hard, boost, examples, ss, n)

    if dry_run:
        print(f"\n----- DRY RUN PROMPT for {intent_code} -----")
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


# ============ 主流程 ============
def main():
    default_intents_path = resolve_default_intents_path()

    parser = argparse.ArgumentParser(description="使用本地 Ollama 为 GLOBAL/fast_path_routing.json 生成向量匹配样本")
    parser.add_argument("--fast-path", default=str(DEFAULT_FAST_PATH),
                        help=f"fast_path_routing.json 路径（默认 {DEFAULT_FAST_PATH}）")
    parser.add_argument("--intents", default=str(default_intents_path),
                        help=f"_intents.json 路径（默认 {default_intents_path}）")
    parser.add_argument("--n", type=int, default=DEFAULT_N,
                        help=f"每个意图生成的样本数（默认 {DEFAULT_N}）")
    parser.add_argument("--model", default=DEFAULT_MODEL,
                        help=f"Ollama 模型名（默认 {DEFAULT_MODEL}）")
    parser.add_argument("--only", default=None,
                        help="只处理指定 intent_code（逗号分隔）")
    parser.add_argument("--resume", action="store_true",
                        help="跳过已有 samples 的 intent")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印 prompt，不调用模型")
    parser.add_argument("--no-backup", action="store_true",
                        help="不备份原文件")
    args = parser.parse_args()

    fp_path = Path(args.fast_path).resolve()
    intents_path = Path(args.intents).resolve()

    if not fp_path.exists():
        print(f"❌ 找不到 fast_path_routing 文件: {fp_path}")
        sys.exit(1)

    print(f"🚀 开始生成向量匹配样本")
    print(f"   ACLS_ROOT : {ACLS_ROOT}")
    print(f"   fast_path : {fp_path}")
    print(f"   intents   : {intents_path} (exists={intents_path.exists()})")
    print(f"   model     : {args.model}")
    print(f"   n/intent  : {args.n}")

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

    only_set = set(x.strip() for x in args.only.split(",")) if args.only else None
    target_codes = [
        code for code in intents_cfg.keys()
        if (only_set is None or code in only_set)
    ]

    print(f"📋 待处理意图数量: {len(target_codes)}")

    total_written = 0
    start_time = time.time()

    for idx, code in enumerate(target_codes, 1):
        config = intents_cfg[code]
        existing_samples = config.get("samples", [])

        if args.resume and existing_samples:
            print(f"[{idx}/{len(target_codes)}] ⏭️  {code} 已有 {len(existing_samples)} 条样本，跳过")
            continue

        ctx = context_map.get(code, {})
        name_cn = ctx.get("name_cn", code)
        print(f"[{idx}/{len(target_codes)}] 🔄 {code} ({name_cn}) ... 生成中")

        t0 = time.time()
        samples = generate_for_intent(
            intent_code=code,
            config=config,
            context=ctx,
            n=args.n,
            model=args.model,
            dry_run=args.dry_run,
        )
        cost = time.time() - t0

        if args.dry_run:
            continue

        if not samples:
            print(f"   ❌ 未获得有效样本，跳过")
            continue

        config["samples"] = samples
        total_written += len(samples)
        print(f"   ✅ 生成 {len(samples)} 条，用时 {cost:.1f}s")

        # 增量落盘，避免中断丢进度
        if container_key == "fast_path_routing":
            data["fast_path_routing"]["intents"] = intents_cfg
        elif container_key == "intents":
            data["intents"] = intents_cfg
        else:
            data = intents_cfg
        save_fast_path(fp_path, data)

    if not args.dry_run:
        print(f"\n{'='*60}")
        print(f"🎉 完成！共写入 {total_written} 条样本到 {fp_path}")
        print(f"⏱️ 总用时: {time.time() - start_time:.1f}s")
        print(f"📄 输出文件: {fp_path}")
        if not args.no_backup:
            print(f"🗂️ 备份文件: {fp_path.with_suffix(fp_path.suffix + '.bak')}")
        print(f"{'='*60}")


if __name__ == "__main__":
    main()
