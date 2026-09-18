#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
optimize_dict.py — 对 inverted_intents.json 做语义质量审查

两个子命令：
  optimize    对每个 intent 调 LLM 输出优化建议（不修改原文件）
  apply       应用建议（需审核后执行）

用法：
  python optimize_dict.py optimize --dry-run
  python optimize_dict.py optimize --only IA_052_turn_on_aed
  python optimize_dict.py optimize --max-intents 10
  python optimize_dict.py optimize          # 全量
  python optimize_dict.py apply             # 应用 optimize 输出的建议
  python optimize_dict.py conflicts         # 扫描跨 intent 冲突的 surface
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
SUGGESTIONS_FILENAME = "_optimize_suggestions.json"
DEFAULT_HOST = "http://127.0.0.1:11434"
DEFAULT_MODEL = "qwen2.5:latest"
TIMEOUT = 300
MAX_RETRY = 3
RETRY_BACKOFF = 2.0
MAX_TOKENS = 1500
SAVE_EVERY = 5

TARGET_MIN_LEN = 2
TARGET_MAX_LEN = 6
HARD_MAX_LEN = 8


# ---------------- 路径与 IO ----------------
def resolve_index_path(cli_path=None):
    if cli_path:
        p = Path(cli_path)
        return p if p.exists() else None
    here = Path(__file__).resolve().parent
    for c in [
        here / DEFAULT_FILENAME,
        here.parent / DEFAULT_FILENAME,
        here.parent / "GLOBAL" / DEFAULT_FILENAME,
        here.parent / "_scripts" / DEFAULT_FILENAME,
    ]:
        if c.exists():
            return c
    return None


def print_candidates():
    here = Path(__file__).resolve().parent
    for c in [
        here / DEFAULT_FILENAME,
        here.parent / DEFAULT_FILENAME,
        here.parent / "GLOBAL" / DEFAULT_FILENAME,
        here.parent / "_scripts" / DEFAULT_FILENAME,
    ]:
        print(f"     - {c}")


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


# ---------------- Ollama ----------------
OPTIMIZE_SYSTEM_PROMPT = """你是院前急救 ACLS 意图词典的审核员。
用户给出一个 intent 和它现有的所有变体（surface），你要逐条判断该变体是否合格，并给出优化建议。

【合格标准】
1. 长度：2~8 字最佳。超过 8 字直接不合格。
2. 真实性：真实急救对话里会出现。不是从意图名硬拆出来的拼凑短语。
3. 简洁性：能表达核心动作或核心名词，不啰嗦。
4. 语义一致：确实是该 intent 的同义表达。
5. 无泛化：不能只是通用词（如"AED"、"患者"、"检查"）单独出现。

【不合格示例】
- "开启AED并准备使用"：10 字太长，是意图名
- "确保心律分析期间无人接触患者"：14 字过长
- "分析心律清场"：拼凑
- "检查左右手紧握"：别扭，真实对话是"捏手"
- "看皮肤湿不湿乎乎"：拼凑

【合格示例】
- "摸脉"、"脉搏"、"呼吸"、"吹气"、"按压"
- "打开气道"、"胸外按压"、"上担架"
- "LKW"、"NPA"、"AED电击"

【输出格式】
严格输出 JSON：
{
  "actions": [
    {"surface": "开启AED", "action": "keep", "reason": "简洁自然"},
    {"surface": "开启AED并准备使用", "action": "delete", "reason": "10字太长"},
    {"surface": "打开AED", "action": "replace", "new_surface": "开AED", "reason": "更简洁"}
  ],
  "add": [
    {"surface": "启动AED", "type": "同义词", "confidence": 0.9, "reason": "常用表达"}
  ]
}

只输出 JSON，不要解释。action 只能是 keep/delete/replace。
"""


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
            "temperature": 0.2,
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


# ---------------- 收集 intent 的 surface ----------------
def collect_intent_surfaces(data, ic):
    """返回该 intent 的 surface 列表：[{"surface":..., "type":..., "confidence":..., "note":...}]"""
    result = []
    entries = data.get("entries", {})
    for surface, lst in entries.items():
        for item in lst:
            if not isinstance(item, list) or len(item) < 3:
                continue
            if item[0] == ic:
                result.append({
                    "surface": surface,
                    "type": item[1],
                    "confidence": safe_float(item[2]),
                    "note": item[3] if len(item) > 3 else "",
                })
    return result


# ---------------- 提示词 ----------------
def build_optimize_prompt(ic, name, normalized, surfaces):
    return json.dumps({
        "intent_code": ic,
        "intent_name": name,
        "normalized": normalized,
        "existing_surfaces": surfaces,
        "rules": {
            "length": f"目标 {TARGET_MIN_LEN}~{TARGET_MAX_LEN} 字，最长不超过 {HARD_MAX_LEN} 字",
            "authenticity": "必须真实急救对话里会说",
            "no_fabrication": "不能是意图名硬拆的拼凑短语",
            "no_generic": "不能只是通用词单独出现",
        }
    }, ensure_ascii=False, indent=2)


# ---------------- optimize 子命令 ----------------
def cmd_optimize(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME)
        print_candidates()
        return

    print(f"📂 使用文件: {path}")
    data = load_data(path)
    intents = data.get("intents", {}) or {}
    if not intents:
        print("❌ 词典里没有 intents 元数据")
        return

    ic_list = sorted(intents.keys())
    if args.only:
        ic_list = [ic for ic in ic_list if ic == args.only]
        if not ic_list:
            print(f"❌ 未找到 intent: {args.only}")
            return
    if args.max_intents:
        ic_list = ic_list[:args.max_intents]

    print(f"📋 待审查 intent: {len(ic_list)}")

    suggestions = {}
    for i, ic in enumerate(ic_list, 1):
        meta = intents[ic]
        name = meta.get("name", "")
        normalized = meta.get("normalized", name)

        surfaces = collect_intent_surfaces(data, ic)
        if not surfaces:
            print(f"[{i}/{len(ic_list)}] {ic} ({name}) 无 surface，跳过")
            continue

        print(f"[{i}/{len(ic_list)}] {ic} ({name}) - {len(surfaces)} 条")

        user_prompt = build_optimize_prompt(ic, name, normalized, surfaces)
        resp = call_ollama(args.host, args.model, OPTIMIZE_SYSTEM_PROMPT, user_prompt)
        if not resp:
            print(f"   ❌ 生成失败")
            continue

        actions = resp.get("actions", [])
        adds = resp.get("add", [])
        if not isinstance(actions, list):
            actions = []
        if not isinstance(adds, list):
            adds = []

        keep = [a for a in actions if a.get("action") == "keep"]
        delete = [a for a in actions if a.get("action") == "delete"]
        replace = [a for a in actions if a.get("action") == "replace"]

        suggestions[ic] = {
            "intent_name": name,
            "existing_count": len(surfaces),
            "keep": keep,
            "delete": delete,
            "replace": replace,
            "add": adds,
        }

        print(f"   ✅ keep {len(keep)} / delete {len(delete)} / replace {len(replace)} / add {len(adds)}")
        for a in delete[:3]:
            print(f"      ✗ 删 [{a.get('surface')}] - {a.get('reason', '')}")
        for a in replace[:3]:
            print(f"      ⇄ [{a.get('surface')}] -> [{a.get('new_surface')}] - {a.get('reason', '')}")
        for a in adds[:3]:
            print(f"      + 加 [{a.get('surface')}] ({a.get('type')})")

        if i % SAVE_EVERY == 0:
            _save_suggestions(path, suggestions)
            print(f"   💾 中间落盘")

    _save_suggestions(path, suggestions)
    print(f"\n🎉 建议已保存到: {Path(path).parent / SUGGESTIONS_FILENAME}")

    # 摘要
    total_delete = sum(len(s["delete"]) for s in suggestions.values())
    total_replace = sum(len(s["replace"]) for s in suggestions.values())
    total_add = sum(len(s["add"]) for s in suggestions.values())
    print(f"\n📊 汇总：")
    print(f"   待删除: {total_delete}")
    print(f"   待替换: {total_replace}")
    print(f"   待新增: {total_add}")


def _save_suggestions(index_path, suggestions):
    out_path = Path(index_path).parent / SUGGESTIONS_FILENAME
    with out_path.open("w", encoding="utf-8") as f:
        json.dump({
            "generated_at": datetime.now().isoformat(),
            "total_intents": len(suggestions),
            "suggestions": suggestions,
        }, f, ensure_ascii=False, indent=2)


# ---------------- apply 子命令 ----------------
def cmd_apply(args):
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME)
        print_candidates()
        return

    sug_path = Path(path).parent / SUGGESTIONS_FILENAME
    if not sug_path.exists():
        print(f"❌ 未找到建议文件: {sug_path}")
        print("   先跑 python optimize_dict.py optimize")
        return

    print(f"📂 词典: {path}")
    print(f"📂 建议: {sug_path}")

    data = load_data(path)
    with sug_path.open("r", encoding="utf-8") as f:
        sug_data = json.load(f)
    suggestions = sug_data.get("suggestions", {})

    entries = data.get("entries", {}) or {}
    intent_meta = data.get("intents", {}) or {}

    deleted = 0
    replaced = 0
    added = 0
    skipped_duplicates = 0

    for ic, s in suggestions.items():
        # 1. 删除
        for a in s.get("delete", []):
            surface = a.get("surface", "")
            if not surface or surface not in entries:
                continue
            # 只删该 intent 的条目，不删其他 intent
            original = entries[surface]
            new_lst = [item for item in original if not (isinstance(item, list) and item[0] == ic)]
            if not new_lst:
                del entries[surface]
            else:
                entries[surface] = new_lst
            deleted += 1

        # 2. 替换
        for a in s.get("replace", []):
            old_surface = a.get("surface", "")
            new_surface = a.get("new_surface", "")
            if not old_surface or not new_surface:
                continue
            if old_surface not in entries:
                continue
            # 找原条目
            target = None
            remaining = []
            for item in entries[old_surface]:
                if isinstance(item, list) and item[0] == ic and target is None:
                    target = item
                else:
                    remaining.append(item)
            if target is None:
                continue
            if remaining:
                entries[old_surface] = remaining
            else:
                del entries[old_surface]

            # 加入新 surface
            new_item = [ic, target[1], target[2]]
            if len(target) > 3:
                new_item.append(target[3])
            # 检查是否已存在
            if new_surface in entries:
                # 已存在，检查是否已有同 intent 条目
                has_same = any(
                    isinstance(x, list) and x[0] == ic
                    for x in entries[new_surface]
                )
                if not has_same:
                    entries[new_surface].append(new_item)
                    replaced += 1
                else:
                    skipped_duplicates += 1
            else:
                entries[new_surface] = [new_item]
                replaced += 1

        # 3. 新增
        for a in s.get("add", []):
            surface = a.get("surface", "").strip()
            vtype = a.get("type", "同义词")
            conf = safe_float(a.get("confidence", 0.9))
            if not surface:
                continue
            if vtype not in ("同义词", "口语"):
                continue
            if len(surface) > HARD_MAX_LEN or len(surface) < TARGET_MIN_LEN:
                continue
            new_item = [ic, vtype, conf]
            if surface in entries:
                has_same = any(
                    isinstance(x, list) and x[0] == ic
                    for x in entries[surface]
                )
                if not has_same:
                    entries[surface].append(new_item)
                    added += 1
                else:
                    skipped_duplicates += 1
            else:
                entries[surface] = [new_item]
                added += 1

    # 重算 stats
    intent_codes = set()
    for lst in entries.values():
        for ce in lst:
            if ce and ce[0]:
                intent_codes.add(ce[0])
    data["stats"] = {
        "total_intents": len(intent_codes),
        "total_surfaces": len(entries),
        "total_mappings": sum(len(v) for v in entries.values()),
    }
    data["optimized_at"] = datetime.now().isoformat()
    data["optimize_params"] = {
        "deleted": deleted,
        "replaced": replaced,
        "added": added,
        "skipped_duplicates": skipped_duplicates,
    }

    if args.dry_run:
        print(f"\n🚫 dry-run：未修改")
        print(f"   待删除 {deleted}，待替换 {replaced}，待新增 {added}")
        return

    if not args.no_backup:
        backup_file(path)

    save_data(data, path)
    print(f"\n✅ 已应用：")
    print(f"   删除 {deleted}")
    print(f"   替换 {replaced}")
    print(f"   新增 {added}")
    print(f"   跳过重复 {skipped_duplicates}")
    print(f"   stats: {data['stats']}")
    print(f"   {path}")


# ---------------- conflicts 子命令 ----------------
def cmd_conflicts(args):
    """扫描跨 intent 共享的 surface（真实歧义）"""
    path = resolve_index_path(args.path)
    if path is None:
        print("❌ 未找到 " + DEFAULT_FILENAME)
        print_candidates()
        return

    print(f"📂 使用文件: {path}")
    data = load_data(path)
    entries = data.get("entries", {}) or {}
    intent_meta = data.get("intents", {}) or {}

    conflicts = []
    for surface, lst in entries.items():
        if not isinstance(lst, list):
            continue
        ics = set()
        for item in lst:
            if isinstance(item, list) and item[0]:
                ics.add(item[0])
        if len(ics) > 1:
            conflicts.append({
                "surface": surface,
                "intents": sorted(ics),
                "intent_names": [intent_meta.get(ic, {}).get("name", "") for ic in sorted(ics)],
            })

    if not conflicts:
        print("✅ 没有跨 intent 冲突的 surface")
        return

    print(f"\n⚠️ 发现 {len(conflicts)} 个跨 intent 共享的 surface：\n")
    for c in conflicts:
        print(f"  [{c['surface']}]")
        for ic, name in zip(c["intents"], c["intent_names"]):
            print(f"      → {ic} ({name})")
        print()

    # 输出文件
    out_path = Path(path).parent / "_conflicts.json"
    with out_path.open("w", encoding="utf-8") as f:
        json.dump({
            "generated_at": datetime.now().isoformat(),
            "total_conflicts": len(conflicts),
            "conflicts": conflicts,
        }, f, ensure_ascii=False, indent=2)
    print(f"📝 详细清单: {out_path}")


# ---------------- main ----------------
def main():
    parser = argparse.ArgumentParser(description="inverted_intents.json 语义质量优化工具")
    sub = parser.add_subparsers(dest="cmd", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--path", default=None, help="不指定则自动查找")
    common.add_argument("--dry-run", action="store_true")
    common.add_argument("--no-backup", action="store_true")

    p1 = sub.add_parser("optimize", parents=[common], help="对每个 intent 调 LLM 输出建议")
    p1.add_argument("--host", default=DEFAULT_HOST)
    p1.add_argument("--model", default=DEFAULT_MODEL)
    p1.add_argument("--only", default=None)
    p1.add_argument("--max-intents", type=int, default=None)

    p2 = sub.add_parser("apply", parents=[common], help="应用建议")
    p3 = sub.add_parser("conflicts", parents=[common], help="扫描跨 intent 冲突")

    args = parser.parse_args()

    if args.cmd == "optimize":
        cmd_optimize(args)
    elif args.cmd == "apply":
        cmd_apply(args)
    elif args.cmd == "conflicts":
        cmd_conflicts(args)


if __name__ == "__main__":
    main()
