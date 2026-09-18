#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ACLS 向量分类器测试脚本（合并版）- 全部样本测试
同时加载 GLOBAL 和指定案例的 fast_path_routing.json 与 _intents.json，
测试所有样本（包括未配置质心的意图），对未配置质心的意图，预测结果必然错误，但可统计。
用法：
    python evaluate_vector_classifier.py                    # 仅测试 GLOBAL
    python evaluate_vector_classifier.py --case stable_narrow  # 测试 GLOBAL + 案例合并
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
    print("❌ 请安装 sentence-transformers: pip install sentence-transformers")
    sys.exit(1)

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
    # 适配两种结构
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


def extract_test_samples(intents):
    """从意图定义中提取测试样本（仅正例，跳过负例标记）"""
    samples = []
    for intent in intents:
        intent_code = intent.get("intent_code", "")
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
                    "source": intent.get("name_cn", intent_code)
                })
    return samples


def build_intent_centroids(intents_data, model):
    """从 fast_path_routing.json 构建意图质心"""
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


def classify(text, centroids, model):
    """对单条文本进行分类"""
    embedding = model.encode(text, normalize_embeddings=True)
    best_intent = None
    best_score = -1.0
    for intent_code, centroid in centroids.items():
        score = np.dot(embedding, centroid)
        if score > best_score:
            best_score = score
            best_intent = intent_code
    return best_intent, best_score


def evaluate_combined(intents_data, test_samples, model, source_name):
    """执行评估：测试全部样本，包括未配置质心的意图"""
    centroids = build_intent_centroids(intents_data, model)
    if not centroids:
        print("❌ 未找到任何 corpus 数据，请检查 fast_path_routing.json")
        return

    train_codes = set(centroids.keys())
    print(f"\n📊 构建了 {len(centroids)} 个意图质心")
    print(f"   训练意图编码: {sorted(train_codes)}")

    # 不进行过滤，直接测试全部样本
    total_samples = len(test_samples)
    print(f"📝 测试全部样本: {total_samples} 条")

    # 统计有/无质心的样本数
    has_centroid_count = sum(1 for s in test_samples if s["intent"] in train_codes)
    no_centroid_count = total_samples - has_centroid_count
    print(f"   ├─ 有质心意图的样本: {has_centroid_count} 条")
    print(f"   └─ 无质心意图的样本: {no_centroid_count} 条（预测必定错误）")

    # 分类
    results = []
    confusion = defaultdict(lambda: defaultdict(int))
    correct = 0
    correct_with_centroid = 0
    total_with_centroid = 0
    total_without_centroid = 0

    # 错误分类统计
    error_details = []
    error_by_intent = defaultdict(lambda: {"total": 0, "errors": 0, "high_score_errors": 0})

    for sample in test_samples:
        text = sample["text"]
        true_intent = sample["intent"]
        has_centroid = true_intent in train_codes

        if has_centroid:
            total_with_centroid += 1
            pred_intent, score = classify(text, centroids, model)
            is_correct = (pred_intent == true_intent)
        else:
            # 无质心，无论如何分类都是错误的，但仍然进行预测以记录结果
            total_without_centroid += 1
            pred_intent, score = classify(text, centroids, model)
            is_correct = False  # 必定错误

        if is_correct:
            correct += 1
            if has_centroid:
                correct_with_centroid += 1

        # 记录错误详情
        if not is_correct:
            error_by_intent[true_intent]["total"] += 1
            error_by_intent[true_intent]["errors"] += 1
            if has_centroid and score >= 0.75:
                error_by_intent[true_intent]["high_score_errors"] += 1
            error_details.append({
                "text": text,
                "true": true_intent,
                "pred": pred_intent,
                "score": score,
                "has_centroid": has_centroid,
                "is_correct": is_correct
            })

        confusion[true_intent][pred_intent] += 1
        results.append({
            "text": text,
            "true": true_intent,
            "pred": pred_intent,
            "score": score,
            "has_centroid": has_centroid,
            "correct": is_correct
        })

    total = len(test_samples)
    accuracy = correct / total if total > 0 else 0
    total_errors = total - correct
    accuracy_with_centroid = correct_with_centroid / total_with_centroid if total_with_centroid > 0 else 0

    # ============================================================
    # 输出：总体统计
    # ============================================================
    print(f"\n{'='*60}")
    print(f"📈 向量分类总体准确率 (全部样本): {accuracy:.2%} ({correct}/{total})")
    print(f"   ├─ 有质心意图样本准确率: {accuracy_with_centroid:.2%} ({correct_with_centroid}/{total_with_centroid})")
    print(f"   └─ 无质心意图样本 (预测必错): {total_without_centroid} 条")
    print(f"{'='*60}")

    # ============================================================
    # 输出：错误分类统计（按分数区间）
    # ============================================================
    if total_errors > 0:
        high_score_errors = [e for e in error_details if e["score"] >= 0.75 and e["has_centroid"]]
        mid_score_errors = [e for e in error_details if 0.60 <= e["score"] < 0.75 and e["has_centroid"]]
        low_score_errors = [e for e in error_details if e["score"] < 0.60 and e["has_centroid"]]
        no_centroid_errors = [e for e in error_details if not e["has_centroid"]]

        print(f"\n🔴 错误分类统计 (共 {total_errors} 条错误):")
        print("-" * 60)
        print(f"   ├─ 高置信度错误 (有质心, score >= 0.75，需修复): {len(high_score_errors)} 条")
        print(f"   │  └─ 向量得分高但分类错误 → 需检查 corpus 或增加 hard_negatives")
        print(f"   ├─ 中置信度错误 (有质心, 0.60 <= score < 0.75): {len(mid_score_errors)} 条")
        print(f"   │  └─ 可考虑降低阈值或交给 LLM 兜底")
        print(f"   ├─ 低置信度错误 (有质心, score < 0.60，可接受): {len(low_score_errors)} 条")
        print(f"   │  └─ 向量得分低，应由 LLM 兜底，不视为向量层错误")
        print(f"   └─ 无质心意图样本 (预测必错): {len(no_centroid_errors)} 条")
        print(f"      └─ 这些意图未配置 corpus，无法正确分类，需补充配置")

        high_conf = len(high_score_errors)
        print(f"\n   ⚠️ 高置信度错误 (有质心, score >= 0.75): {high_conf} 条")
        if high_conf > 0:
            print(f"      这些错误需要修复向量配置（检查 corpus 或 hard_negatives）")
        else:
            print(f"      ✅ 无高置信度错误，向量配置精准！")

    # ============================================================
    # 输出：各意图准确率及错误分析（只显示有质心的意图）
    # ============================================================
    print(f"\n📊 各意图向量分类准确率及错误分析 (仅显示有质心的意图):")
    print("-" * 80)

    # 只统计有质心的意图
    intent_stats = {}
    for true_intent in set(r["true"] for r in results if r["true"] in train_codes):
        samples_of_intent = [r for r in results if r["true"] == true_intent]
        correct_of_intent = sum(1 for r in samples_of_intent if r["correct"])
        total_of_intent = len(samples_of_intent)
        errors_of_intent = total_of_intent - correct_of_intent
        high_score_errors_of_intent = sum(1 for e in error_details if e["true"] == true_intent and e["score"] >= 0.75 and e["has_centroid"])

        intent_stats[true_intent] = {
            "correct": correct_of_intent,
            "total": total_of_intent,
            "accuracy": correct_of_intent / total_of_intent if total_of_intent > 0 else 0,
            "errors": errors_of_intent,
            "high_score_errors": high_score_errors_of_intent
        }

    for intent_code, stats in sorted(intent_stats.items(), key=lambda x: x[1]["errors"], reverse=True):
        high_info = f" (高分错误:{stats['high_score_errors']})" if stats['high_score_errors'] > 0 else ""
        warn = " ⚠️" if stats['high_score_errors'] > 0 else ""
        print(f"   {intent_code:20s} {stats['correct']:3d}/{stats['total']:3d}  {stats['accuracy']:.2%}{warn}{high_info}")

    # ============================================================
    # 输出：无质心的意图列表（供参考）
    # ============================================================
    no_centroid_intents = sorted(set(r["true"] for r in results if r["true"] not in train_codes))
    if no_centroid_intents:
        print(f"\n📌 未配置质心的意图列表 ({len(no_centroid_intents)} 个):")
        print("-" * 80)
        for intent in no_centroid_intents:
            count = sum(1 for r in results if r["true"] == intent)
            print(f"   {intent:20s} 样本数: {count}")
        print(f"   💡 建议为这些意图添加 corpus 配置以提升向量分类覆盖")

    # ============================================================
    # 输出：错误明细（优先显示高置信度错误）
    # ============================================================
    if error_details:
        print(f"\n🔍 错误分类明细（共 {len(error_details)} 条，优先显示高置信度错误）:")
        print("-" * 80)
        # 按分数从高到低排序（高分错误优先），且先显示有质心的
        error_details_sorted = sorted([e for e in error_details if e["has_centroid"]], key=lambda x: x["score"], reverse=True)
        # 再加上无质心的
        error_details_sorted += [e for e in error_details if not e["has_centroid"]]
        for err in error_details_sorted[:15]:
            if err["score"] >= 0.75 and err["has_centroid"]:
                high_conf_mark = "🔴 "
            elif not err["has_centroid"]:
                high_conf_mark = "⚪ "
            else:
                high_conf_mark = "   "
            pred_info = err["pred"] if err["pred"] else "NO_MATCH"
            has_centroid_info = " (无质心)" if not err["has_centroid"] else ""
            print(f"   {high_conf_mark} '{err['text'][:35]:35s}...' "
                  f"真实: {err['true']:15s} → 预测: {pred_info:15s} (score: {err['score']:.3f}){has_centroid_info}")
        if len(error_details) > 15:
            print(f"   ... 共 {len(error_details)} 条错误")
    else:
        print(f"\n✅ 全部正确！无任何错误！")

    # ============================================================
    # 输出：阈值建议
    # ============================================================
    # 只基于有质心的错误样本分析
    centroid_errors = [e for e in error_details if e["has_centroid"]]
    if centroid_errors:
        scores = [e["score"] for e in centroid_errors]
        avg_error_score = sum(scores) / len(scores)
        print(f"\n💡 有质心的错误样本平均得分: {avg_error_score:.3f}")
        if avg_error_score > 0.70:
            print(f"   ⚠️ 错误样本平均得分较高 ({avg_error_score:.3f})，建议检查 corpus 或增加 hard_negatives")
        else:
            print(f"   ✅ 错误样本平均得分较低，当前向量配置合理")
    else:
        print(f"\n💡 无有质心的错误样本，向量配置优秀！")

    return results, confusion, intent_stats


def load_combined(case_name=None):
    """
    加载合并的配置：
    - 总是加载 GLOBAL
    - 如果指定 case_name，合并加载案例
    返回: (训练意图数据, 所有测试样本)
    """
    all_intents_data = {}
    all_test_samples = []
    loaded_sources = []

    # 1. 加载 GLOBAL
    print(f"\n📂 加载 GLOBAL...")
    global_intents_data = load_fast_path(GLOBAL_FAST_PATH)
    if global_intents_data:
        all_intents_data.update(global_intents_data)
        print(f"   ├─ 训练意图: {len(global_intents_data)} 个 (GLOBAL/fast_path_routing.json)")

    global_intents = load_intents(GLOBAL_INTENTS)
    if global_intents:
        test_samples = extract_test_samples(global_intents)
        all_test_samples.extend(test_samples)
        print(f"   └─ 测试样本: {len(test_samples)} 条 (GLOBAL/_intents.json)")
        loaded_sources.append("GLOBAL")

    # 2. 加载案例（如果指定）
    if case_name:
        case_path = CASES_DIR / case_name
        if not case_path.exists():
            print(f"❌ 案例目录不存在: {case_path}")
            sys.exit(1)

        print(f"\n📂 加载案例: {case_name}...")
        case_fast_path = case_path / "fast_path_routing.json"
        case_intents_data = load_fast_path(case_fast_path)
        if case_intents_data:
            all_intents_data.update(case_intents_data)
            print(f"   ├─ 训练意图: {len(case_intents_data)} 个 ({case_name}/fast_path_routing.json)")

        case_intents_file = case_path / "_intents.json"
        case_intents = load_intents(case_intents_file)
        if case_intents:
            test_samples = extract_test_samples(case_intents)
            all_test_samples.extend(test_samples)
            print(f"   └─ 测试样本: {len(test_samples)} 条 ({case_name}/_intents.json)")
            loaded_sources.append(case_name)

    # 3. 统计汇总
    print(f"\n📊 合并汇总:")
    print(f"   ├─ 训练意图总计: {len(all_intents_data)} 个")
    print(f"   ├─ 测试样本总计: {len(all_test_samples)} 条")
    print(f"   └─ 数据来源: {', '.join(loaded_sources)}")

    return all_intents_data, all_test_samples


def main():
    parser = argparse.ArgumentParser(description='ACLS 向量分类器测试（合并版）- 全部样本')
    parser.add_argument('--case', '-c', type=str, help='指定案例目录名（如 stable_narrow），不指定则仅测试 GLOBAL')
    parser.add_argument('--model', '-m', type=str, default='BAAI/bge-large-zh-v1.5',
                        help='Embedding 模型名称（默认 BAAI/bge-large-zh-v1.5）')
    args = parser.parse_args()

    print("🚀 ACLS 向量分类器测试（合并版）- 全部样本")
    print(f"📁 工作目录: {ACLS_ROOT}")

    # 加载模型
    print(f"\n📦 加载 Embedding 模型: {args.model}")
    try:
        model = SentenceTransformer(args.model)
        print(f"   ✅ 模型加载完成 (向量维度: {model.get_embedding_dimension()})")
    except Exception as e:
        print(f"   ❌ 模型加载失败: {e}")
        sys.exit(1)

    # 加载合并配置
    all_intents_data, all_test_samples = load_combined(args.case)

    if not all_intents_data:
        print("❌ 未找到任何训练意图数据")
        sys.exit(1)

    if not all_test_samples:
        print("❌ 未找到任何测试样本")
        sys.exit(1)

    # 执行评估
    evaluate_combined(all_intents_data, all_test_samples, model, args.case or "GLOBAL")

    print("\n✅ 测试完成")


if __name__ == "__main__":
    main()
