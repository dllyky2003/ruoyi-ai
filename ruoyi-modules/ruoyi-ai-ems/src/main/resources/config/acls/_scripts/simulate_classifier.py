#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ACLS 多层分类模拟器（关键词 + 向量 + 兜底）
模拟完整的分类流程：
1. 关键词匹配 → 命中则直接返回
2. 向量计算 → 得分 >= 阈值则返回
3. 未命中 → 标记为需要 LLM 兜底

用法：
    python simulate_classifier.py --case stable_narrow --threshold 0.75
    python simulate_classifier.py --case stable_narrow --threshold 0.70 --detail
"""

import json
import sys
import argparse
from pathlib import Path
from collections import defaultdict

# 向量化依赖
try:
    from sentence_transformers import SentenceTransformer
    import numpy as np
    HAS_SENTENCE_TRANSFORMERS = True
except ImportError:
    HAS_SENTENCE_TRANSFORMERS = False
    print("⚠️ 未安装 sentence-transformers，将跳过向量计算")
    print("   安装: pip install sentence-transformers")

# 路径配置
SCRIPT_DIR = Path(__file__).parent.absolute()
ACLS_ROOT = SCRIPT_DIR.parent
GLOBAL_DIR = ACLS_ROOT / "GLOBAL"
GLOBAL_FAST_PATH = GLOBAL_DIR / "fast_path_routing.json"
GLOBAL_INTENTS = GLOBAL_DIR / "_intents.json"
CASES_DIR = ACLS_ROOT / "cases"


def load_fast_path(json_path):
    """从 fast_path_routing.json 加载配置"""
    if not json_path.exists():
        return None
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    if "fast_path_routing" in data:
        return data["fast_path_routing"].get("intents", {})
    elif "intents" in data:
        return data["intents"]
    return data


def load_intents(json_path):
    """从 _intents.json 加载意图定义"""
    if not json_path.exists():
        return None
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    if "intents" in data:
        return data["intents"]
    if "layers" in data:
        intents = []
        for layer in data.get("layers", []):
            for sub_domain in layer.get("sub_domains", []):
                for intent in sub_domain.get("intents", []):
                    if "intent_code" in intent:
                        intents.append(intent)
        return intents
    return data if isinstance(data, list) else []


def extract_samples(intents):
    """从意图定义中提取测试样本（仅正例，跳过负例标记）"""
    samples = []
    for intent in intents:
        intent_code = intent.get("intent_code", "")
        intent_name = intent.get("name_cn", intent_code)
        for example in intent.get("examples", []):
            if "【负例】" in example or "【负例-禁止匹配】" in example:
                continue
            clean_text = example
            for prefix in ["【正例】", "【示例】"]:
                if clean_text.startswith(prefix):
                    clean_text = clean_text[len(prefix):]
            clean_text = clean_text.strip()
            if clean_text:
                samples.append({
                    "text": clean_text,
                    "intent": intent_code,
                    "source": intent_name
                })
    return samples


def match_keywords(text, keyword_config):
    """关键词匹配逻辑"""
    hard = keyword_config.get("hard", [])
    boost = keyword_config.get("boost", [])
    exclude = keyword_config.get("exclude", [])

    # 1. 检查排除词
    for ex in exclude:
        if ex in text:
            return False, "excluded"

    # 2. 检查硬关键词
    for hw in hard:
        if hw in text:
            return True, "hard"

    # 3. 检查提升词
    for bw in boost:
        if bw in text:
            return True, "boost"

    return False, "none"


def build_intent_centroids(intents_data, model):
    """构建意图质心"""
    centroids = {}
    for intent_code, config in intents_data.items():
        corpus = config.get("vector", {}).get("corpus", [])
        if not corpus:
            continue
        embeddings = model.encode(corpus, normalize_embeddings=True)
        centroid = np.mean(embeddings, axis=0)
        centroid = centroid / np.linalg.norm(centroid)
        centroids[intent_code] = centroid
    return centroids


def classify_by_keywords(text, all_keyword_configs):
    """关键词分类"""
    best_intent = None
    best_priority = -1
    best_match_type = "none"
    matched = False

    for intent_code, config in all_keyword_configs.items():
        is_match, match_type = match_keywords(text, config.get("keyword", {}))
        if not is_match:
            continue

        matched = True
        priority = config.get("keyword", {}).get("priority", 0)

        if match_type == "hard":
            if priority > best_priority or best_match_type != "hard":
                best_intent = intent_code
                best_priority = priority
                best_match_type = match_type
        elif match_type == "boost" and best_match_type != "hard":
            if priority > best_priority:
                best_intent = intent_code
                best_priority = priority
                best_match_type = match_type

    return best_intent, best_match_type, matched


def classify_by_vector(text, centroids, model):
    """向量分类"""
    embedding = model.encode(text, normalize_embeddings=True)
    best_intent = None
    best_score = -1.0

    for intent_code, centroid in centroids.items():
        score = np.dot(embedding, centroid)
        if score > best_score:
            best_score = score
            best_intent = intent_code

    return best_intent, best_score


def simulate_classification(text, all_keyword_configs, centroids, model, threshold):
    """模拟完整分类流程"""
    # 第一层：关键词匹配
    kw_intent, kw_match_type, kw_matched = classify_by_keywords(text, all_keyword_configs)

    if kw_matched:
        return {
            "text": text,
            "result": "keyword_hit",
            "intent": kw_intent,
            "match_type": kw_match_type,
            "score": None,
            "details": f"关键词命中 [{kw_match_type}] → {kw_intent}"
        }

    # 第二层：向量计算
    if model is not None and centroids:
        vec_intent, vec_score = classify_by_vector(text, centroids, model)

        if vec_score >= threshold:
            return {
                "text": text,
                "result": "vector_hit",
                "intent": vec_intent,
                "match_type": "vector",
                "score": vec_score,
                "details": f"向量命中 (score: {vec_score:.3f}) → {vec_intent}"
            }
        else:
            return {
                "text": text,
                "result": "below_threshold",
                "intent": vec_intent,
                "match_type": "vector_low",
                "score": vec_score,
                "details": f"向量得分低于阈值 ({vec_score:.3f} < {threshold:.2f})，需 LLM 兜底"
            }

    # 未命中（无向量模型或未安装）
    return {
        "text": text,
        "result": "no_match",
        "intent": None,
        "match_type": "none",
        "score": None,
        "details": "关键词未命中，且向量不可用 → 需 LLM 兜底"
    }


def load_combined(case_name=None):
    """加载合并配置"""
    all_keyword_configs = {}
    all_vector_configs = {}
    all_test_samples = []
    loaded_sources = []

    # 1. 加载 GLOBAL
    print(f"\n📂 加载 GLOBAL...")
    global_config = load_fast_path(GLOBAL_FAST_PATH)
    if global_config:
        all_keyword_configs.update(global_config)
        all_vector_configs.update(global_config)
        print(f"   ├─ 关键词配置: {len(global_config)} 个")

    global_intents = load_intents(GLOBAL_INTENTS)
    if global_intents:
        samples = extract_samples(global_intents)
        all_test_samples.extend(samples)
        print(f"   └─ 测试样本: {len(samples)} 条")
        loaded_sources.append("GLOBAL")

    # 2. 加载案例（如果指定）
    if case_name:
        case_path = CASES_DIR / case_name
        if not case_path.exists():
            print(f"❌ 案例目录不存在: {case_path}")
            sys.exit(1)

        print(f"\n📂 加载案例: {case_name}...")
        case_config = load_fast_path(case_path / "fast_path_routing.json")
        if case_config:
            all_keyword_configs.update(case_config)
            all_vector_configs.update(case_config)
            print(f"   ├─ 关键词配置: {len(case_config)} 个")

        case_intents = load_intents(case_path / "_intents.json")
        if case_intents:
            samples = extract_samples(case_intents)
            all_test_samples.extend(samples)
            print(f"   └─ 测试样本: {len(samples)} 条")
            loaded_sources.append(case_name)

    # 3. 统计汇总
    print(f"\n📊 合并汇总:")
    print(f"   ├─ 关键词配置总计: {len(all_keyword_configs)} 个")
    print(f"   ├─ 测试样本总计: {len(all_test_samples)} 条")
    print(f"   └─ 数据来源: {', '.join(loaded_sources)}")

    return all_keyword_configs, all_vector_configs, all_test_samples


def main():
    parser = argparse.ArgumentParser(description='ACLS 多层分类模拟器')
    parser.add_argument('--case', '-c', type=str, help='指定案例目录名（如 stable_narrow）')
    parser.add_argument('--threshold', '-t', type=float, default=0.75, help='向量相似度阈值（默认 0.75）')
    parser.add_argument('--model', '-m', type=str, default='BAAI/bge-large-zh-v1.5',
                        help='Embedding 模型名称（默认 BAAI/bge-large-zh-v1.5）')
    parser.add_argument('--detail', '-d', action='store_true', help='显示每条样本的详细结果')
    parser.add_argument('--limit', '-l', type=int, default=None, help='限制显示的样本数量（仅配合 --detail）')
    parser.add_argument('--focus', '-f', type=str, default=None, help='仅显示指定意图的结果（如 S-101-2-6）')
    parser.add_argument('--output', '-o', type=str, default=None, help='输出结果到文件（JSONL 格式）')
    args = parser.parse_args()

    print("🚀 ACLS 多层分类模拟器")
    print(f"📁 工作目录: {ACLS_ROOT}")
    print(f"📌 向量阈值: {args.threshold}")

    # 加载配置
    all_keyword_configs, all_vector_configs, all_test_samples = load_combined(args.case)

    if not all_keyword_configs and not all_vector_configs:
        print("❌ 未找到任何配置数据")
        sys.exit(1)

    if not all_test_samples:
        print("❌ 未找到任何测试样本")
        sys.exit(1)

    # 加载模型
    model = None
    centroids = None
    if HAS_SENTENCE_TRANSFORMERS and all_vector_configs:
        try:
            print(f"\n📦 加载 Embedding 模型: {args.model}")
            model = SentenceTransformer(args.model)
            print(f"   ✅ 模型加载完成 (向量维度: {model.get_embedding_dimension()})")
            centroids = build_intent_centroids(all_vector_configs, model)
            print(f"   ✅ 构建了 {len(centroids)} 个意图质心")
        except Exception as e:
            print(f"   ❌ 模型加载失败: {e}")
            model = None
            centroids = None
    else:
        print("\n⚠️ 跳过向量计算（模型不可用）")

    # 过滤样本（如果指定了 focus）
    if args.focus:
        all_test_samples = [s for s in all_test_samples if s["intent"] == args.focus]
        print(f"\n🔍 聚焦意图: {args.focus}，样本数: {len(all_test_samples)}")
        if not all_test_samples:
            print(f"❌ 未找到意图 {args.focus} 的测试样本")
            sys.exit(1)

    # 模拟分类
    print(f"\n⏳ 开始模拟分类...")
    results = []

    for sample in all_test_samples:
        result = simulate_classification(
            sample["text"],
            all_keyword_configs,
            centroids,
            model,
            args.threshold
        )
        result["true_intent"] = sample["intent"]
        result["source"] = sample["source"]
        results.append(result)

    # 统计
    total = len(results)
    keyword_hits = sum(1 for r in results if r["result"] == "keyword_hit")
    vector_hits = sum(1 for r in results if r["result"] == "vector_hit")
    below_threshold = sum(1 for r in results if r["result"] == "below_threshold")
    no_match = sum(1 for r in results if r["result"] == "no_match")

    # 统计正确率（关键词/向量命中且意图正确）
    correct_keyword = sum(1 for r in results if r["result"] == "keyword_hit" and r["intent"] == r["true_intent"])
    correct_vector = sum(1 for r in results if r["result"] == "vector_hit" and r["intent"] == r["true_intent"])
    total_correct = correct_keyword + correct_vector

    print(f"\n{'='*70}")
    print(f"📊 分类结果统计 (阈值: {args.threshold})")
    print(f"{'='*70}")
    print(f"   ├─ 总样本数: {total}")
    print(f"   ├─ 关键词命中: {keyword_hits} ({keyword_hits/total*100:.1f}%)")
    print(f"   │  └─ 正确: {correct_keyword}")
    print(f"   ├─ 向量命中: {vector_hits} ({vector_hits/total*100:.1f}%)")
    print(f"   │  └─ 正确: {correct_vector}")
    print(f"   ├─ 低于阈值 (LLM兜底): {below_threshold} ({below_threshold/total*100:.1f}%)")
    print(f"   └─ 未命中: {no_match} ({no_match/total*100:.1f}%)")
    print(f"\n📈 总体正确率 (关键词+向量命中且正确): {total_correct}/{total} = {total_correct/total*100:.2f}%")

    # 按意图统计
    if args.detail:
        print(f"\n{'='*70}")
        print("📊 按意图分类结果")
        print(f"{'='*70}")

        intent_stats = defaultdict(lambda: {"total": 0, "keyword": 0, "vector": 0, "below": 0, "no_match": 0, "correct": 0})
        for r in results:
            intent = r["true_intent"]
            intent_stats[intent]["total"] += 1
            if r["result"] == "keyword_hit":
                intent_stats[intent]["keyword"] += 1
                if r["intent"] == intent:
                    intent_stats[intent]["correct"] += 1
            elif r["result"] == "vector_hit":
                intent_stats[intent]["vector"] += 1
                if r["intent"] == intent:
                    intent_stats[intent]["correct"] += 1
            elif r["result"] == "below_threshold":
                intent_stats[intent]["below"] += 1
            else:
                intent_stats[intent]["no_match"] += 1

        for intent, stats in sorted(intent_stats.items()):
            kw_pct = stats["keyword"] / stats["total"] * 100 if stats["total"] > 0 else 0
            vec_pct = stats["vector"] / stats["total"] * 100 if stats["total"] > 0 else 0
            below_pct = stats["below"] / stats["total"] * 100 if stats["total"] > 0 else 0
            nom_pct = stats["no_match"] / stats["total"] * 100 if stats["total"] > 0 else 0
            correct_pct = stats["correct"] / stats["total"] * 100 if stats["total"] > 0 else 0
            print(f"   {intent:20s} total:{stats['total']:2d}  KW:{stats['keyword']:2d}  VEC:{stats['vector']:2d}  ↓:{stats['below']:2d}  ✗:{stats['no_match']:2d}  ✅:{stats['correct']:2d} ({correct_pct:.0f}%)")

    # 显示详细结果
    if args.detail:
        print(f"\n{'='*70}")
        print("📝 各样本分类详情")
        print(f"{'='*70}")

        display_results = results
        if args.limit:
            display_results = results[:args.limit]

        for i, r in enumerate(display_results, 1):
            status_icon = "✅" if (r["result"] in ["keyword_hit", "vector_hit"] and r["intent"] == r["true_intent"]) else "❌"
            if r["result"] == "keyword_hit":
                label = f"🔑KW[{r['match_type']}]"
            elif r["result"] == "vector_hit":
                label = f"🧠VEC({r['score']:.3f})"
            elif r["result"] == "below_threshold":
                label = f"⬇️↓({r['score']:.3f})"
            else:
                label = "⬜未命中"

            pred = r["intent"] if r["intent"] else "NO_MATCH"
            print(f"   {status_icon} [{i:2d}] {label:20s} 真实:{r['true_intent']:15s} → 预测:{pred:15s} | {r['text'][:40]}")

        if args.limit and len(results) > args.limit:
            print(f"   ... 共 {len(results)} 条，仅显示前 {args.limit} 条")

    # 导出结果
    if args.output:
        output_path = Path(args.output)
        with open(output_path, 'w', encoding='utf-8') as f:
            for r in results:
                export = {k: v for k, v in r.items() if k != "text" or len(str(v)) < 100}
                export["text"] = r["text"]
                f.write(json.dumps(export, ensure_ascii=False) + '\n')
        print(f"\n💾 结果已导出到: {output_path}")

    # 输出阈值建议
    if total > 0 and below_threshold + no_match > 0:
        scores = [r["score"] for r in results if r["score"] is not None]
        if scores:
            avg_score = sum(scores) / len(scores)
            print(f"\n💡 当前阈值 {args.threshold}，向量平均得分: {avg_score:.3f}")
            if avg_score < args.threshold:
                print(f"   ⚠️ 平均得分低于阈值，建议降低阈值至 {avg_score:.2f} 左右")
            elif avg_score > args.threshold + 0.1:
                print(f"   ✅ 平均得分高于阈值，当前设置合理")

    print("\n✅ 测试完成")


if __name__ == "__main__":
    main()
