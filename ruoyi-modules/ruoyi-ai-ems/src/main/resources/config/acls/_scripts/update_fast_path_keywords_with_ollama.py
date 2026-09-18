#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update fast_path_routing.json keywords using local Ollama (qwen2.5:latest).

功能：
- 读取 ACLS/EMS 的 GLOBAL/fast_path_routing.json（或指定路径）
- 对每个 intent_code：读取其 samples（可结合旧 keywords）
- 调用本地 Ollama 生成新的 keyword.hard / keyword.boost / keyword.exclude / keyword.priority
- 将更新结果写回 fast_path_routing.json（可选择另存输出）

依赖：
- Python 3.9+
- requests（可选：没有 requests 会使用 urllib）

用法示例：
  python update_fast_path_keywords_with_ollama.py \
    --path src/main/resources/config/acls/GLOBAL/fast_path_routing.json \
    --model qwen2.5:latest \
    --max-hard 7 --max-boost 7 --max-exclude 5 \
    --out fast_path_routing.updated.json

也可就地更新：
  python update_fast_path_keywords_with_ollama.py --path ... --inplace

Ollama 默认地址： http://localhost:11434
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


try:
    import requests  # type: ignore

    HAS_REQUESTS = True
except Exception:
    HAS_REQUESTS = False

import urllib.request


@dataclass
class OllamaConfig:
    host: str = "http://localhost:11434"
    model: str = "qwen2.5:latest"
    temperature: float = 0.2
    top_p: float = 0.9
    max_tokens: int = 800
    timeout_sec: int = 120
    max_retries: int = 3
    retry_backoff_sec: float = 2.0


def http_post_json(url: str, payload: Dict[str, Any], timeout_sec: int) -> Dict[str, Any]:
    """POST JSON and parse JSON response."""
    headers = {"Content-Type": "application/json"}

    if HAS_REQUESTS:
        resp = requests.post(url, json=payload, headers=headers, timeout=timeout_sec)
        resp.raise_for_status()
        return resp.json()

    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout_sec) as r:  # nosec B310
        raw = r.read().decode("utf-8")
        return json.loads(raw)


def ollama_chat_json(
    cfg: OllamaConfig,
    system_prompt: str,
    user_prompt: str,
) -> Dict[str, Any]:
    """Use Ollama chat API to request strictly JSON output."""
    url = cfg.host.rstrip("/") + "/api/chat"

    payload: Dict[str, Any] = {
        "model": cfg.model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "stream": False,
        "options": {
            "temperature": cfg.temperature,
            "top_p": cfg.top_p,
            "num_predict": cfg.max_tokens,
        },
    }

    # 尝试启用 response-format JSON（不同 Ollama 版本可能支持不一致）
    # 如果不支持，模型仍会按“只返回 JSON”约束。
    payload["options"]["format"] = "json"

    last_err: Optional[Exception] = None
    for attempt in range(1, cfg.max_retries + 1):
        try:
            return http_post_json(url, payload, timeout_sec=cfg.timeout_sec)
        except Exception as e:
            last_err = e
            if attempt < cfg.max_retries:
                time.sleep(cfg.retry_backoff_sec * attempt)
            else:
                raise last_err

    raise RuntimeError("unreachable")


def extract_json_object(text: str) -> Optional[Dict[str, Any]]:
    """Best-effort extract a single JSON object from text."""
    text = text.strip()
    # 直接 JSON
    if text.startswith("{") and text.endswith("}"):
        try:
            return json.loads(text)
        except Exception:
            pass

    # 提取第一个 {...}
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    candidate = m.group(0)
    try:
        return json.loads(candidate)
    except Exception:
        return None


def normalize_phrase_list(items: List[Any], max_items: int) -> List[str]:
    out: List[str] = []
    seen = set()
    for it in items:
        if not isinstance(it, str):
            continue
        s = it.strip()
        # 去掉明显的空/占位符
        if not s:
            continue
        if s in seen:
            continue
        seen.add(s)
        out.append(s)
        if len(out) >= max_items:
            break
    return out


def build_keyword_prompt(
    intent_code: str,
    intent_display_name: str,
    samples: List[str],
    old_keyword: Dict[str, Any],
    max_hard: int,
    max_boost: int,
    max_exclude: int,
) -> Tuple[str, str]:
    system_prompt = (
        "你是一个中文医疗/急救场景的意图路由工程助手。"
        "你需要为给定 intent_code 生成关键词用于第一层 keyword fast path。"
        "请严格只输出一个 JSON 对象，不要输出任何额外文本。"
        "JSON 字段必须包含：hard, boost, exclude, priority。"
        "hard/boost/exclude 均为字符串数组。priority 为 0 到 100 的整数。"
    )

    # 把旧关键词也喂给模型：允许更新/完善
    old_hard = old_keyword.get("hard", []) if isinstance(old_keyword, dict) else []
    old_boost = old_keyword.get("boost", []) if isinstance(old_keyword, dict) else []
    old_exclude = old_keyword.get("exclude", []) if isinstance(old_keyword, dict) else []
    old_priority = old_keyword.get("priority", None) if isinstance(old_keyword, dict) else None

    user_prompt = {
        "intent_code": intent_code,
        "intent_display_name": intent_display_name,
        "existing_keyword": {
            "hard": old_hard,
            "boost": old_boost,
            "exclude": old_exclude,
            "priority": old_priority,
        },
        "samples": samples[:30],
        "rules": {
            "hard": f"给出 {max_hard} 条左右（不超过该值）的高精度强触发短语。命中 hard 时应高度指向该 intent。",
            "boost": f"给出 {max_boost} 条左右的中等精度软触发短语。用于增加置信度但允许一定噪声。",
            "exclude": f"给出 {max_exclude} 条左右的排除短语：常见但容易把该 intent 和相邻/相似 intent 混淆的表达；命中 exclude 时应倾向排除该 intent。没有也可以输出空数组。",
            "priority": "根据 hard 触发的可靠度与覆盖范围给出 0-100 整数。hard 越可靠/越关键，priority 越高。建议参考：50 为中等，70+ 为高确定，30- 为较弱。"
        },
        "output_requirements": {
            "json_only": True,
            "hard": "字符串数组",
            "boost": "字符串数组",
            "exclude": "字符串数组",
            "priority": "0-100 整数",
        }
    }

    return system_prompt, json.dumps(user_prompt, ensure_ascii=False)


def validate_keywords_payload(payload: Dict[str, Any], max_hard: int, max_boost: int, max_exclude: int) -> Dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("Model output is not a JSON object")

    hard = payload.get("hard", [])
    boost = payload.get("boost", [])
    exclude = payload.get("exclude", [])
    priority = payload.get("priority", 50)

    if not isinstance(hard, list) or not isinstance(boost, list) or not isinstance(exclude, list):
        raise ValueError("Fields hard/boost/exclude must be arrays")

    hard_n = normalize_phrase_list(hard, max_hard)
    boost_n = normalize_phrase_list(boost, max_boost)
    exclude_n = normalize_phrase_list(exclude, max_exclude)

    if isinstance(priority, (float, int)):
        p = int(round(float(priority)))
    elif isinstance(priority, str) and priority.strip().isdigit():
        p = int(priority.strip())
    else:
        p = 50

    p = max(0, min(100, p))

    # 若模型输出空，至少保证字段存在
    if not hard_n and not boost_n:
        # 退化策略：用 samples 中的前几个句子做 soft 兜底（尽量不要空）
        # 这里不直接用 samples，因为 validate_keywords_payload 没有样本；
        # 上层会处理。
        pass

    return {
        "hard": hard_n,
        "boost": boost_n,
        "exclude": exclude_n,
        "priority": p,
    }


def update_fast_path_keywords(
    in_path: Path,
    out_path: Optional[Path],
    cfg: OllamaConfig,
    max_hard: int,
    max_boost: int,
    max_exclude: int,
    dry_run: bool,
    backup: bool,
) -> None:
    if not in_path.exists():
        raise FileNotFoundError(str(in_path))

    data = json.loads(in_path.read_text(encoding="utf-8"))

    fast = data.get("fast_path_routing")
    if not isinstance(fast, dict):
        raise ValueError("Invalid fast_path_routing.json: missing fast_path_routing object")

    intents = fast.get("intents")
    if not isinstance(intents, dict):
        raise ValueError("Invalid fast_path_routing.json: missing fast_path_routing.intents object")

    intents_updated = 0

    # 避免无谓多次调用：跳过已存在关键词且用户没有强制
    # 这里仍然全量更新；如果你想“只补缺”，我可以再给你改。

    # 准备输出
    if out_path is None:
        out_path = in_path

    if dry_run:
        print("[dry-run] will not write file.")

    if backup and not dry_run and out_path.resolve() == in_path.resolve():
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_path = in_path.with_suffix(in_path.suffix + f".bak_{ts}")
        shutil.copy2(in_path, backup_path)
        print(f"Backup created: {backup_path}")

    total_intents = len(intents)
    print(f"Found {total_intents} intents. Updating keywords...")

    for i, (intent_code, intent_obj) in enumerate(intents.items(), start=1):
        if not isinstance(intent_obj, dict):
            continue

        keyword_obj = intent_obj.get("keyword", {})
        if not isinstance(keyword_obj, dict):
            keyword_obj = {}

        samples = intent_obj.get("samples", [])
        if not isinstance(samples, list) or not samples:
            print(f"[{i}/{total_intents}] {intent_code}: no samples, skip")
            continue

        # samples 清洗：只保留字符串
        samples_clean = [s for s in samples if isinstance(s, str) and s.strip()]
        if not samples_clean:
            print(f"[{i}/{total_intents}] {intent_code}: samples empty after clean, skip")
            continue

        # 尽量减少 token：取前若干条样本
        samples_for_prompt = samples_clean[:20]

        system_prompt, user_prompt = build_keyword_prompt(
            intent_code=intent_code,
            intent_display_name=intent_code,
            samples=samples_for_prompt,
            old_keyword=keyword_obj,
            max_hard=max_hard,
            max_boost=max_boost,
            max_exclude=max_exclude,
        )

        print(f"[{i}/{total_intents}] Generating keywords for {intent_code}...")

        # Ollama 调用
        try:
            resp = ollama_chat_json(cfg, system_prompt, user_prompt)
        except Exception as e:
            print(f"   ❌ Ollama call failed for {intent_code}: {e}")
            continue

        # chat API 返回：{"message": {"role":..., "content": "..."}, ...}
        content = None
        if isinstance(resp, dict):
            message = resp.get("message")
            if isinstance(message, dict):
                content = message.get("content")

        if not isinstance(content, str):
            print(f"   ❌ Unexpected Ollama response format for {intent_code}, skip")
            continue

        payload = extract_json_object(content)
        if payload is None:
            print(f"   ❌ Failed to parse JSON for {intent_code}, skip. raw={content[:200]}")
            continue

        try:
            validated = validate_keywords_payload(payload, max_hard, max_boost, max_exclude)
        except Exception as e:
            print(f"   ❌ Keyword validation failed for {intent_code}: {e}")
            continue

        # 如果 hard+boost 仍然全空：用 samples 兜底（把前面几条分到 boost）
        if (not validated["hard"]) and (not validated["boost"]):
            boost_fallback = normalize_phrase_list(samples_for_prompt, max_boost)
            validated["boost"] = boost_fallback
            validated["priority"] = int(validated.get("priority", 50))

        # 写回
        # 保持结构：keyword: {hard, boost, exclude, priority}
        intent_obj["keyword"] = validated
        intents_updated += 1

        # 小概率：模型生成到很长，做简单截断（避免极端）
        for key in ["hard", "boost", "exclude"]:
            if isinstance(intent_obj["keyword"].get(key), list):
                cleaned = []
                seen = set()
                for s in intent_obj["keyword"][key]:
                    if not isinstance(s, str):
                        continue
                    s2 = s.strip()
                    if not s2 or s2 in seen:
                        continue
                    seen.add(s2)
                    cleaned.append(s2)
                intent_obj["keyword"][key] = cleaned

    data["fast_path_routing"] = fast

    if dry_run:
        print(f"[dry-run] Updated {intents_updated}/{total_intents} intents (not written).")
        return

    if out_path.resolve() == in_path.resolve():
        in_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"✅ Updated in-place: {in_path}")
    else:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"✅ Updated output: {out_path}")

    print(f"Updated keywords for {intents_updated}/{total_intents} intents.")


def main():
    parser = argparse.ArgumentParser(description="Update fast_path_routing.json keyword lists using local Ollama")

    parser.add_argument(
        "--path",
        type=str,
        default=None,
        help="fast_path_routing.json 路径。默认: 自动指向 GLOBAL/fast_path_routing.json（脚本父目录同级 GLOBAL）。",
    )
    parser.add_argument("--model", type=str, default="qwen2.5:latest", help="Ollama 模型名")
    parser.add_argument("--host", type=str, default="http://localhost:11434", help="Ollama host")

    parser.add_argument("--max-hard", type=int, default=7)
    parser.add_argument("--max-boost", type=int, default=7)
    parser.add_argument("--max-exclude", type=int, default=5)

    parser.add_argument("--temperature", type=float, default=0.2)
    parser.add_argument("--top-p", type=float, default=0.9)
    parser.add_argument("--max-tokens", type=int, default=800)

    parser.add_argument("--inplace", action="store_true", help="就地更新输入文件")
    parser.add_argument("--out", type=str, default=None, help="输出到指定文件（不写则默认和输入同一路径同名不变）")

    parser.add_argument("--dry-run", action="store_true", help="不写文件，只打印更新情况")
    parser.add_argument("--no-backup", action="store_true", help="就地更新时不创建备份")

    args = parser.parse_args()

    # 默认路径：脚本文件所在目录的父目录同级 GLOBAL/fast_path_routing.json
    if args.path is None:
        script_dir = Path(__file__).resolve().parent
        acl_root = script_dir.parent
        in_path = acl_root / "GLOBAL" / "fast_path_routing.json"
    else:
        in_path = Path(args.path)

    if not in_path.exists():
        print(f"❌ fast_path_routing.json not found: {in_path}")
        sys.exit(1)

    out_path = None
    if args.out:
        out_path = Path(args.out)

    if args.inplace:
        out_path = in_path
    elif out_path is None:
        # 默认不改名：如果不 inplace，则只在不指定 out 时仍默认输出到新文件。
        # 但为避免意外覆盖，这里默认仍按“另存一份”。
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_path = in_path.with_name(in_path.stem + f".updated_{ts}" + in_path.suffix)

    cfg = OllamaConfig(
        host=args.host,
        model=args.model,
        temperature=args.temperature,
        top_p=args.top_p,
        max_tokens=args.max_tokens,
    )

    update_fast_path_keywords(
        in_path=in_path,
        out_path=out_path,
        cfg=cfg,
        max_hard=args.max_hard,
        max_boost=args.max_boost,
        max_exclude=args.max_exclude,
        dry_run=args.dry_run,
        backup=not args.no_backup,
    )


if __name__ == "__main__":
    main()
