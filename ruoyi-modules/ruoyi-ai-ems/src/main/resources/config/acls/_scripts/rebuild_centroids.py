#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 _intents.json 重建向量质心（走 Ollama bge-m3）

语料来源（Level 1）：examples + standard_script
输出：centroids.json，Java VectorIntentClassifier 可直接读取

用法：
    python rebuild_centroids.py --dry-run           # 只统计语料，不调用 Ollama
    python rebuild_centroids.py                     # 全量生成
    python rebuild_centroids.py --case stable_narrow # 只做指定案例
"""

import json
import urllib.request
import argparse
import re
from pathlib import Path
from datetime import datetime

try:
    import numpy as np
except ImportError:
    print("❌ 缺少 numpy，请先: pip install numpy")
    raise

# ============================================================
#  路径
# ============================================================
SCRIPT_DIR = Path(__file__).parent.absolute()
ACLS_ROOT  = SCRIPT_DIR.parent
GLOBAL_DIR = ACLS_ROOT / "GLOBAL"
CASES_DIR  = ACLS_ROOT / "cases"

# ============================================================
#  配置
# ============================================================
OLLAMA_URL   = "http://localhost:11434"
OLLAMA_MODEL = "bge-m3"
MIN_CORPUS   = 3              # 少于这个数量的意图跳过
USE_STANDARD_SCRIPT = True    # 是否把 standard_script 也加入语料


# ============================================================
#  语料收集
# ============================================================

def load_intents_from_file(path):
    """读 _intents.json，返回所有 intent 定义列表"""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    result = []
    for layer in data.get("layers", []):
        for sd in layer.get("sub_domains", []):
            for intent in sd.get("intents", []):
                if "intent_code" in intent:
                    result.append(intent)
    return result


def clean_text(s):
    """剔除【负例】/【正例】/【...】 标注"""
    if not s:
        return ""
    s = re.sub(r"【[^】]*】", "", s)
    return s.strip()


def collect_corpus(intent):
    """Level 1 语料：examples + standard_script"""
    texts = []
    seen = set()

    # examples
    for ex in intent.get("examples", []):
        t = clean_text(ex)
        if t and len(t) >= 4 and t not in seen:
            texts.append(t)
            seen.add(t)

    # standard_script
    if USE_STANDARD_SCRIPT:
        std = intent.get("standard_script")
        t = clean_text(std)
        if t and len(t) >= 4 and t not in seen:
            texts.append(t)
            seen.add(t)

    return texts


# ============================================================
#  Ollama 调用
# ============================================================

def ollama_embed_one(text, url, model):
    data = json.dumps({"model": model, "prompt": text}).encode("utf-8")
    req = urllib.request.Request(
        f"{url}/api/embeddings",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = json.loads(resp.read().decode("utf-8"))
        vec = body.get("embedding")
        if not vec:
            raise ValueError(f"empty embedding for: {text[:40]}")
        return vec


def compute_centroid(texts, url, model):
    """归一化每行 → 平均 → 再归一化"""
    embs = [ollama_embed_one(t, url, model) for t in texts]
    arr = np.array(embs, dtype=np.float32)
    norms = np.linalg.norm(arr, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    arr = arr / norms
    c = np.mean(arr, axis=0)
    cn = np.linalg.norm(c)
    if cn > 0:
        c = c / cn
    return c


# ============================================================
#  主流程
# ============================================================

def rebuild_for(intents_json, output_dir, case_name, url, model, dry_run=False):
    if not intents_json.exists():
        print(f"   ⚠️ 跳过 {case_name}: {intents_json} 不存在")
        return False

    intents = load_intents_from_file(intents_json)
    print(f"\n📂 {case_name}: 共 {len(intents)} 个意图")

    # ---------- 干跑 ----------
    if dry_run:
        total = 0
        usable = 0
        skipped = []
        for it in intents:
            c = collect_corpus(it)
            total += len(c)
            if len(c) < MIN_CORPUS:
                skipped.append((it.get("intent_code", "?"), len(c)))
            else:
                usable += 1
        print(f"   ├─ 语料总数: {total}")
        print(f"   ├─ 可用意图: {usable}")
        print(f"   └─ 跳过: {len(skipped)}")
        if skipped:
            for code, n in skipped:
                print(f"      ⏭️ {code}: {n} 条")
        return True

    # ---------- 正式生成 ----------
    centroids = {}
    names = {}
    dim = None
    skipped = 0

    for i, it in enumerate(intents):
        code = it.get("intent_code")
        if not code:
            continue
        corpus = collect_corpus(it)
        if len(corpus) < MIN_CORPUS:
            skipped += 1
            continue
        try:
            c = compute_centroid(corpus, url, model)
            centroids[code] = c
            names[code] = it.get("name_cn", code)
            if dim is None:
                dim = len(c)
        except Exception as e:
            print(f"   ❌ {code} 失败: {e}")
            continue

        if (i + 1) % 10 == 0:
            print(f"   ... {i + 1}/{len(intents)}, 成功 {len(centroids)}")

    if not centroids:
        print(f"   ⚠️ 无有效质心")
        return False

    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / "centroids.json"

    payload = {
        "version": "3.0.0",
        "generated_at": datetime.now().isoformat(),
        "model": f"ollama:{model}",
        "dimension": dim,
        "intent_count": len(centroids),
        "intents": {k: v.tolist() for k, v in centroids.items()},
        "intent_names": names,
    }

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    print(f"   ✅ {out_path}")
    print(f"      {len(centroids)} 个意图, {dim} 维, 跳过 {skipped} 个")
    return True


def main():
    global OLLAMA_URL, OLLAMA_MODEL
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", "-c", type=str, help="仅指定案例")
    parser.add_argument("--url", type=str, default=OLLAMA_URL)
    parser.add_argument("--model", type=str, default=OLLAMA_MODEL)
    parser.add_argument("--dry-run", action="store_true", help="只统计语料，不调用 Ollama")
    args = parser.parse_args()

    OLLAMA_URL = args.url
    OLLAMA_MODEL = args.model

    # 探测 Ollama
    try:
        with urllib.request.urlopen(f"{OLLAMA_URL}/api/tags", timeout=5) as r:
            print(f"✅ Ollama 可达: {OLLAMA_URL}")
    except Exception as e:
        print(f"❌ Ollama 不可达: {e}")
        return

    print(f"📦 模型: {OLLAMA_MODEL}")
    if args.dry_run:
        print("🔍 干跑模式：仅统计语料，不调用 embedding")

    # GLOBAL
    rebuild_for(GLOBAL_DIR / "_intents.json",
                GLOBAL_DIR / "_training",
                "GLOBAL", OLLAMA_URL, OLLAMA_MODEL, args.dry_run)

    # 案例
    if args.case:
        case_dir = CASES_DIR / args.case
        rebuild_for(case_dir / "_intents.json",
                    case_dir / "_training",
                    args.case, OLLAMA_URL, OLLAMA_MODEL, args.dry_run)
    else:
        for case_dir in CASES_DIR.iterdir():
            if case_dir.is_dir() and not case_dir.name.startswith("_"):
                rebuild_for(case_dir / "_intents.json",
                            case_dir / "_training",
                            case_dir.name, OLLAMA_URL, OLLAMA_MODEL, args.dry_run)

    print("\n✅ 完成")


if __name__ == "__main__":
    main()
