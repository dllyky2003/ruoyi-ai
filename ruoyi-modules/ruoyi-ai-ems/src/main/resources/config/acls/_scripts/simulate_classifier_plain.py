#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ACLS 多层分类模拟器（无 Emoji 版本）
"""

import sys
import json
import argparse
from pathlib import Path
from collections import defaultdict

# 强制 stdout 使用 UTF-8
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

try:
    from sentence_transformers import SentenceTransformer
    import numpy as np
    HAS = True
except ImportError:
    HAS = False

SCRIPT_DIR = Path(__file__).parent.absolute()
ACLS_ROOT = SCRIPT_DIR.parent
GLOBAL_DIR = ACLS_ROOT / "GLOBAL"
GLOBAL_FAST_PATH = GLOBAL_DIR / "fast_path_routing.json"
GLOBAL_INTENTS = GLOBAL_DIR / "_intents.json"
CASES_DIR = ACLS_ROOT / "cases"

def load_fast_path(p):
    if not p.exists(): return None
    with open(p, 'r', encoding='utf-8') as f:
        d = json.load(f)
    if "fast_path_routing" in d:
        return d["fast_path_routing"].get("intents", {})
    elif "intents" in d:
        return d["intents"]
    return d

def load_intents(p):
    if not p.exists(): return None
    with open(p, 'r', encoding='utf-8') as f:
        d = json.load(f)
    if "intents" in d:
        return d["intents"]
    if "layers" in d:
        intents = []
        for layer in d.get("layers", []):
            for sub in layer.get("sub_domains", []):
                for intent in sub.get("intents", []):
                    if "intent_code" in intent:
                        intents.append(intent)
        return intents
    return d if isinstance(d, list) else []

def extract_samples(intents):
    samples = []
    for intent in intents:
        code = intent.get("intent_code", "")
        name = intent.get("name_cn", code)
        for ex in intent.get("examples", []):
            if "【负例】" in ex or "【负例-禁止匹配】" in ex:
                continue
            text = ex
            for p in ["【正例】", "【示例】"]:
                if text.startswith(p):
                    text = text[len(p):]
            text = text.strip()
            if text:
                samples.append({"text": text, "intent": code, "source": name})
    return samples

def match_keywords(text, cfg):
    hard = cfg.get("hard", [])
    boost = cfg.get("boost", [])
    exclude = cfg.get("exclude", [])
    for ex in exclude:
        if ex in text:
            return False, "excluded"
    for hw in hard:
        if hw in text:
            return True, "hard"
    for bw in boost:
        if bw in text:
            return True, "boost"
    return False, "none"

def build_centroids(configs, model):
    centroids = {}
    for code, cfg in configs.items():
        corpus = cfg.get("vector", {}).get("corpus", [])
        if not corpus:
            continue
        emb = model.encode(corpus, normalize_embeddings=True)
        centroid = np.mean(emb, axis=0)
        centroid = centroid / np.linalg.norm(centroid)
        centroids[code] = centroid
    return centroids

def classify_keywords(text, configs):
    best = None
    best_prio = -1
    best_type = "none"
    matched = False
    for code, cfg in configs.items():
        kw = cfg.get("keyword", {})
        ok, typ = match_keywords(text, kw)
        if not ok:
            continue
        matched = True
        prio = kw.get("priority", 0)
        if typ == "hard":
            if prio > best_prio or best_type != "hard":
                best = code
                best_prio = prio
                best_type = typ
        elif typ == "boost" and best_type != "hard":
            if prio > best_prio:
                best = code
                best_prio = prio
                best_type = typ
    return best, best_type, matched

def classify_vector(text, centroids, model):
    emb = model.encode(text, normalize_embeddings=True)
    best = None
    best_score = -1.0
    for code, cent in centroids.items():
        score = np.dot(emb, cent)
        if score > best_score:
            best_score = score
            best = code
    return best, best_score

def simulate(text, kw_configs, centroids, model, threshold):
    kw_intent, kw_type, matched = classify_keywords(text, kw_configs)
    if matched:
        return {"text": text, "result": "keyword_hit", "intent": kw_intent, "type": kw_type,
                "score": None, "details": f"Keyword [{kw_type}] -> {kw_intent}"}
    if model is not None and centroids:
        vec_intent, vec_score = classify_vector(text, centroids, model)
        if vec_score >= threshold:
            return {"text": text, "result": "vector_hit", "intent": vec_intent,
                    "type": "vector", "score": vec_score,
                    "details": f"Vector ({vec_score:.3f}) -> {vec_intent}"}
        else:
            return {"text": text, "result": "below_threshold", "intent": vec_intent,
                    "type": "vector_low", "score": vec_score,
                    "details": f"Vector low ({vec_score:.3f} < {threshold:.2f})"}
    return {"text": text, "result": "no_match", "intent": None,
            "type": "none", "score": None, "details": "No match"}

def load_combined(case=None):
    kw_configs = {}
    vec_configs = {}
    all_samples = []
    print("\n[Loading GLOBAL...]")
    gcfg = load_fast_path(GLOBAL_FAST_PATH)
    if gcfg:
        kw_configs.update(gcfg)
        vec_configs.update(gcfg)
        print(f"   KW configs: {len(gcfg)}")
    gint = load_intents(GLOBAL_INTENTS)
    if gint:
        samples = extract_samples(gint)
        all_samples.extend(samples)
        print(f"   Samples: {len(samples)}")
    if case:
        case_path = CASES_DIR / case
        if not case_path.exists():
            print(f"Case dir not found: {case_path}")
            sys.exit(1)
        print(f"\n[Loading case: {case}...]")
        ccfg = load_fast_path(case_path / "fast_path_routing.json")
        if ccfg:
            kw_configs.update(ccfg)
            vec_configs.update(ccfg)
            print(f"   KW configs: {len(ccfg)}")
        cint = load_intents(case_path / "_intents.json")
        if cint:
            samples = extract_samples(cint)
            all_samples.extend(samples)
            print(f"   Samples: {len(samples)}")
    print(f"\n[Summary] Total KW configs: {len(kw_configs)}, Samples: {len(all_samples)}")
    return kw_configs, vec_configs, all_samples

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--case', '-c', help='case name')
    parser.add_argument('--threshold', '-t', type=float, default=0.75)
    parser.add_argument('--model', '-m', default='BAAI/bge-large-zh-v1.5')
    parser.add_argument('--detail', '-d', action='store_true')
    parser.add_argument('--limit', '-l', type=int, default=None)
    parser.add_argument('--focus', '-f', default=None)
    parser.add_argument('--output', '-o', default=None)
    args = parser.parse_args()

    print(f"ACLS Multi-level Classifier Simulator (Plain Text)")
    print(f"Threshold: {args.threshold}")

    kw_configs, vec_configs, samples = load_combined(args.case)
    if not samples:
        print("No samples")
        return

    model = None
    centroids = None
    if HAS and vec_configs:
        try:
            print(f"\nLoading model: {args.model}")
            model = SentenceTransformer(args.model)
            print(f"Model loaded (dim: {model.get_embedding_dimension()})")
            centroids = build_centroids(vec_configs, model)
            print(f"Built {len(centroids)} centroids")
        except Exception as e:
            print(f"Model load failed: {e}")

    if args.focus:
        samples = [s for s in samples if s["intent"] == args.focus]
        print(f"Focused on {args.focus}, samples: {len(samples)}")

    results = []
    for s in samples:
        r = simulate(s["text"], kw_configs, centroids, model, args.threshold)
        r["true_intent"] = s["intent"]
        r["source"] = s["source"]
        results.append(r)

    total = len(results)
    kw_hits = sum(1 for r in results if r["result"] == "keyword_hit")
    vec_hits = sum(1 for r in results if r["result"] == "vector_hit")
    below = sum(1 for r in results if r["result"] == "below_threshold")
    no = sum(1 for r in results if r["result"] == "no_match")
    correct_kw = sum(1 for r in results if r["result"] == "keyword_hit" and r["intent"] == r["true_intent"])
    correct_vec = sum(1 for r in results if r["result"] == "vector_hit" and r["intent"] == r["true_intent"])
    total_correct = correct_kw + correct_vec

    print("\n" + "="*70)
    print(f"RESULTS (threshold={args.threshold})")
    print("="*70)
    print(f"  Total samples: {total}")
    print(f"  Keyword hits: {kw_hits} ({kw_hits/total*100:.1f}%)  correct: {correct_kw}")
    print(f"  Vector hits: {vec_hits} ({vec_hits/total*100:.1f}%)  correct: {correct_vec}")
    print(f"  Below threshold: {below} ({below/total*100:.1f}%)")
    print(f"  No match: {no} ({no/total*100:.1f}%)")
    print(f"\nOverall accuracy: {total_correct}/{total} = {total_correct/total*100:.2f}%")

    if args.detail:
        print("\n" + "="*70)
        print("Per-intent stats")
        print("="*70)
        stats = defaultdict(lambda: {"total":0, "kw":0, "vec":0, "below":0, "no":0, "correct":0})
        for r in results:
            intent = r["true_intent"]
            stats[intent]["total"] += 1
            if r["result"] == "keyword_hit":
                stats[intent]["kw"] += 1
                if r["intent"] == intent:
                    stats[intent]["correct"] += 1
            elif r["result"] == "vector_hit":
                stats[intent]["vec"] += 1
                if r["intent"] == intent:
                    stats[intent]["correct"] += 1
            elif r["result"] == "below_threshold":
                stats[intent]["below"] += 1
            else:
                stats[intent]["no"] += 1
        for intent, st in sorted(stats.items()):
            print(f"{intent:20s} total:{st['total']:2d}  KW:{st['kw']:2d}  VEC:{st['vec']:2d}  LOW:{st['below']:2d}  NONE:{st['no']:2d}  CORRECT:{st['correct']:2d} ({st['correct']/st['total']*100:.0f}%)")

        print("\n" + "="*70)
        print("Sample details (first 300)")
        print("="*70)
        disp = results if not args.limit else results[:args.limit]
        for i, r in enumerate(disp, 1):
            ok = (r["result"] in ["keyword_hit", "vector_hit"] and r["intent"] == r["true_intent"])
            mark = "[OK] " if ok else "[ERR]"
            if r["result"] == "keyword_hit":
                label = f"KW[{r['type']}]"
            elif r["result"] == "vector_hit":
                label = f"VEC({r['score']:.3f})"
            elif r["result"] == "below_threshold":
                label = f"LOW({r['score']:.3f})"
            else:
                label = "NONE"
            pred = r["intent"] if r["intent"] else "NO_MATCH"
            print(f"{mark} [{i:3d}] {label:15s} true:{r['true_intent']:15s} -> pred:{pred:15s} | {r['text'][:50]}")
        if args.limit and len(results) > args.limit:
            print(f"... and {len(results)-args.limit} more")

    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            for r in results:
                json.dump(r, f, ensure_ascii=False)
                f.write('\n')
        print(f"\nExported to {args.output}")

    print("\n[Done]")

if __name__ == "__main__":
    main()
