#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ACLS 训练数据生成器 + 向量质心导出器
从 fast_path_routing.json 生成 JSONL 训练数据，并导出向量质心供 Java 加载

用法：
    python build_training_data.py                    # 生成全部
    python build_training_data.py --case stable_narrow  # 仅生成指定案例
    python build_training_data.py --skip-training    # 仅导出质心，不生成训练数据
"""

import json
import sys
import os
import argparse
import pickle
from pathlib import Path
from datetime import datetime

# 向量化依赖
try:
    from sentence_transformers import SentenceTransformer
    import numpy as np
    HAS_SENTENCE_TRANSFORMERS = True
except ImportError:
    HAS_SENTENCE_TRANSFORMERS = False
    print("⚠️ 未安装 sentence-transformers，将跳过向量质心导出")
    print("   安装: pip install sentence-transformers")

# 拆分依赖
try:
    from sklearn.model_selection import train_test_split
    HAS_SKLEARN = True
except ImportError:
    HAS_SKLEARN = False
    print("⚠️ 未安装 scikit-learn，将使用纯 Python 拆分方案")

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


def extract_training_samples(intents_data, case_name="GLOBAL"):
    """从 fast_path_routing 提取训练样本"""
    samples = []

    for intent_code, config in intents_data.items():
        # 正样本：corpus
        corpus = config.get("vector", {}).get("corpus", [])
        for text in corpus:
            if text and text.strip():
                samples.append({
                    "text": text.strip(),
                    "intent": intent_code,
                    "sample_type": "positive",
                    "source": case_name
                })

        # 负样本：hard_negatives
        hard_negatives = config.get("vector", {}).get("hard_negatives", [])
        for text in hard_negatives:
            if text and text.strip():
                samples.append({
                    "text": text.strip(),
                    "intent": "NEGATIVE_SAMPLE",
                    "sample_type": "negative",
                    "negative_for": intent_code,
                    "source": case_name
                })

    return samples


def split_by_intent_pure_python(samples, test_size=0.15, val_size=0.15, random_seed=42):
    """纯 Python 实现的按意图分组拆分"""
    import random
    random.seed(random_seed)

    train_data, val_data, test_data = [], [], []

    positive_samples = [s for s in samples if s.get("sample_type") == "positive"]
    negative_samples = [s for s in samples if s.get("sample_type") == "negative"]

    intent_groups = {}
    for sample in positive_samples:
        intent = sample['intent']
        if intent not in intent_groups:
            intent_groups[intent] = []
        intent_groups[intent].append(sample)

    for intent, group_samples in intent_groups.items():
        if len(group_samples) < 3:
            train_data.extend(group_samples)
            continue

        random.shuffle(group_samples)
        n = len(group_samples)
        n_test = int(n * test_size)
        n_val = int(n * val_size)

        test_data.extend(group_samples[:n_test])
        val_data.extend(group_samples[n_test:n_test + n_val])
        train_data.extend(group_samples[n_test + n_val:])

    if negative_samples:
        random.shuffle(negative_samples)
        n_neg = len(negative_samples)
        n_train = int(n_neg * (1 - test_size - val_size))
        n_val = int(n_neg * val_size)

        train_data.extend(negative_samples[:n_train])
        val_data.extend(negative_samples[n_train:n_train + n_val])
        test_data.extend(negative_samples[n_train + n_val:])

    return train_data, val_data, test_data


def split_by_intent(samples, test_size=0.15, val_size=0.15, random_seed=42):
    """按意图分组拆分训练/验证/测试集"""
    if HAS_SKLEARN:
        train_data, val_data, test_data = [], [], []

        positive_samples = [s for s in samples if s.get("sample_type") == "positive"]
        negative_samples = [s for s in samples if s.get("sample_type") == "negative"]

        intent_groups = {}
        for sample in positive_samples:
            intent = sample['intent']
            if intent not in intent_groups:
                intent_groups[intent] = []
            intent_groups[intent].append(sample)

        for intent, group_samples in intent_groups.items():
            if len(group_samples) < 3:
                train_data.extend(group_samples)
                continue

            train_val, test = train_test_split(
                group_samples,
                test_size=test_size,
                random_state=random_seed
            )
            test_data.extend(test)

            if len(train_val) > 2:
                train, val = train_test_split(
                    train_val,
                    test_size=val_size / (1 - test_size),
                    random_state=random_seed
                )
                train_data.extend(train)
                val_data.extend(val)
            else:
                train_data.extend(train_val)

        if negative_samples:
            import random
            random.seed(random_seed)
            random.shuffle(negative_samples)
            n_neg = len(negative_samples)
            n_train = int(n_neg * (1 - test_size - val_size))
            n_val = int(n_neg * val_size)

            train_data.extend(negative_samples[:n_train])
            val_data.extend(negative_samples[n_train:n_train + n_val])
            test_data.extend(negative_samples[n_train + n_val:])

        return train_data, val_data, test_data
    else:
        return split_by_intent_pure_python(samples, test_size, val_size, random_seed)


def save_jsonl(data, filepath):
    """保存为 JSONL 格式"""
    with open(filepath, 'w', encoding='utf-8') as f:
        for item in data:
            f.write(json.dumps(item, ensure_ascii=False) + '\n')


def compute_and_export_centroids(intents_data, model, output_dir, case_name):
    """
    计算意图质心并导出为 NumPy 和 JSON 格式
    """
    if not HAS_SENTENCE_TRANSFORMERS or model is None:
        print(f"   ⚠️ 跳过 {case_name}：向量模型不可用")
        return False

    centroids = {}
    intent_names = {}
    dim = None

    for intent_code, config in intents_data.items():
        corpus = config.get("vector", {}).get("corpus", [])
        if not corpus:
            continue

        try:
            embeddings = model.encode(corpus, normalize_embeddings=True)
            centroid = np.mean(embeddings, axis=0)
            centroid = centroid / np.linalg.norm(centroid)
            centroids[intent_code] = centroid
            intent_names[intent_code] = intent_code
            if dim is None:
                dim = len(centroid)
        except Exception as e:
            print(f"   ⚠️ 计算 {intent_code} 质心失败: {e}")
            continue

    if not centroids:
        print(f"   ⚠️ 跳过 {case_name}：无有效质心")
        return False

    # 确保输出目录存在
    output_dir.mkdir(parents=True, exist_ok=True)

    # 1. 导出为 NumPy 格式（.npz，包含所有质心）
    npz_path = output_dir / "centroids.npz"
    np.savez(npz_path, **centroids)
    print(f"   ✅ 导出 NumPy 质心: {npz_path} ({len(centroids)} 个意图, {dim} 维)")

    # 2. 导出为 JSON 格式（便于 Java 解析）
    json_centroids = {}
    for intent_code, centroid in centroids.items():
        json_centroids[intent_code] = centroid.tolist()

    json_path = output_dir / "centroids.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump({
            "version": "1.0.0",
            "generated_at": datetime.now().isoformat(),
            "model": str(model),
            "dimension": dim,
            "intent_count": len(centroids),
            "intents": json_centroids
        }, f, ensure_ascii=False, indent=2)
    print(f"   ✅ 导出 JSON 质心: {json_path} ({len(centroids)} 个意图, {dim} 维)")

    # 3. 导出为 Pickle 格式（Python 专用，快速加载）
    pkl_path = output_dir / "centroids.pkl"
    with open(pkl_path, 'wb') as f:
        pickle.dump({
            "version": "1.0.0",
            "generated_at": datetime.now().isoformat(),
            "model": str(model),
            "dimension": dim,
            "intents": centroids,
            "intent_names": intent_names
        }, f)
    print(f"   ✅ 导出 Pickle 质心: {pkl_path} ({len(centroids)} 个意图)")

    return True


def generate_training_data(fast_path_file, output_dir, case_name, model=None, skip_training=False):
    """生成训练数据"""
    if not fast_path_file.exists():
        print(f"   ⚠️ 跳过 {case_name}：{fast_path_file} 不存在")
        return False

    intents_data = load_fast_path(fast_path_file)
    if not intents_data:
        print(f"   ⚠️ 跳过 {case_name}：未找到意图配置")
        return False

    print(f"\n📂 处理: {case_name}")
    print(f"   ├─ 意图数量: {len(intents_data)}")

    # ============================================================
    # 1. 生成训练数据（除非跳过）
    # ============================================================
    if not skip_training:
        samples = extract_training_samples(intents_data, case_name)

        positive_count = sum(1 for s in samples if s.get("sample_type") == "positive")
        negative_count = sum(1 for s in samples if s.get("sample_type") == "negative")
        print(f"   ├─ 正样本: {positive_count} 条")
        print(f"   ├─ 负样本: {negative_count} 条")
        print(f"   ├─ 总计: {len(samples)} 条")

        if len(samples) >= 3:
            train_data, val_data, test_data = split_by_intent(samples)
            print(f"   ├─ 训练集: {len(train_data)} 条")
            print(f"   ├─ 验证集: {len(val_data)} 条")
            print(f"   └─ 测试集: {len(test_data)} 条")

            output_dir.mkdir(parents=True, exist_ok=True)

            save_jsonl(train_data, output_dir / "train.jsonl")
            save_jsonl(val_data, output_dir / "val.jsonl")
            save_jsonl(test_data, output_dir / "test.jsonl")

            with open(output_dir / "version.txt", 'w', encoding='utf-8') as f:
                f.write("1.0.0\n")
                f.write(f"# 生成时间: {datetime.now().isoformat()}\n")
                f.write(f"# 数据来源: {fast_path_file.name}\n")
                f.write(f"# 意图数量: {len(intents_data)}\n")
                f.write(f"# 样本总数: {len(samples)}\n")
                f.write(f"# 训练集: {len(train_data)}\n")
                f.write(f"# 验证集: {len(val_data)}\n")
                f.write(f"# 测试集: {len(test_data)}")

            print(f"   ✅ 训练数据已保存至: {output_dir}")
        else:
            print(f"   ⚠️ 样本太少，跳过训练数据生成")

    # ============================================================
    # 2. 导出向量质心
    # ============================================================
    if model is not None:
        print(f"   ├─ 计算并导出向量质心...")
        compute_and_export_centroids(intents_data, model, output_dir, case_name)

    return True


def main():
    parser = argparse.ArgumentParser(description='ACLS 训练数据生成器 + 向量质心导出器')
    parser.add_argument('--case', '-c', type=str, help='指定案例目录名，不指定则生成全部')
    parser.add_argument('--skip-training', action='store_true', help='跳过训练数据生成，仅导出质心')
    parser.add_argument('--model', '-m', type=str, default='BAAI/bge-large-zh-v1.5',
                        help='Embedding 模型名称（默认 BAAI/bge-large-zh-v1.5）')
    parser.add_argument('--no-vector', action='store_true', help='跳过向量质心导出')
    args = parser.parse_args()

    print("🚀 ACLS 训练数据生成器 + 向量质心导出器")
    print(f"📁 工作目录: {ACLS_ROOT}")

    # 加载模型
    model = None
    if not args.no_vector and HAS_SENTENCE_TRANSFORMERS:
        try:
            print(f"\n📦 加载 Embedding 模型: {args.model}")
            model = SentenceTransformer(args.model)
            print(f"   ✅ 模型加载完成 (向量维度: {model.get_embedding_dimension()})")
        except Exception as e:
            print(f"   ❌ 模型加载失败: {e}")
            model = None
    elif args.no_vector:
        print("\n⏭️ 跳过向量质心导出")
    else:
        print("\n⚠️ 未安装 sentence-transformers，跳过向量质心导出")

    generated = []

    # 1. 生成全局训练数据
    print("\n📂 [1/2] 生成全局意图训练数据...")
    global_output = GLOBAL_DIR / "_training"
    if generate_training_data(GLOBAL_FAST_PATH, global_output, "GLOBAL", model, args.skip_training):
        generated.append("GLOBAL")

    # 2. 生成案例训练数据
    print("\n📂 [2/2] 生成案例训练数据...")

    if args.case:
        case_path = CASES_DIR / args.case
        if case_path.exists() and case_path.is_dir():
            fast_path_file = case_path / "fast_path_routing.json"
            output_dir = case_path / "_training"
            if generate_training_data(fast_path_file, output_dir, args.case, model, args.skip_training):
                generated.append(args.case)
        else:
            print(f"   ❌ 案例目录不存在: {case_path}")
    else:
        for case_path in CASES_DIR.iterdir():
            if case_path.is_dir() and not case_path.name.startswith('_'):
                fast_path_file = case_path / "fast_path_routing.json"
                output_dir = case_path / "_training"
                if generate_training_data(fast_path_file, output_dir, case_path.name, model, args.skip_training):
                    generated.append(case_path.name)

    print(f"\n✅ 完成！共生成 {len(generated)} 份数据:")
    for name in generated:
        print(f"   ├─ {name}")

    # 输出汇总
    print("\n📊 生成的文件:")
    print("   ├─ _training/train.jsonl   - 训练数据")
    print("   ├─ _training/val.jsonl     - 验证数据")
    print("   ├─ _training/test.jsonl    - 测试数据")
    print("   ├─ _training/centroids.npz - 向量质心 (NumPy格式)")
    print("   ├─ _training/centroids.json- 向量质心 (JSON格式)")
    print("   └─ _training/centroids.pkl - 向量质心 (Pickle格式)")


if __name__ == "__main__":
    main()
