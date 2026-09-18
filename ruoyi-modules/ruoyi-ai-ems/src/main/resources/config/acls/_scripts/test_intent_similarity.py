import argparse
import json
import logging
import platform
import sys
import time
from datetime import datetime
from pathlib import Path

import numpy as np
import sentence_transformers
from sentence_transformers import SentenceTransformer

NEG_INF = -1e9
logger = logging.getLogger("intent_similarity")


def setup_logging(verbose: bool = False):
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )


# ---------------------------------------------------------------------------
# 数据加载与校验
# ---------------------------------------------------------------------------
def load_intents_from_fast_path_json(path: Path) -> dict:
    """加载 fast_path_routing.json，返回 {intent_code: [samples]}。
    样本会 strip 并去重（意图内去重）。"""
    if not path.exists():
        raise FileNotFoundError(f"找不到 fast_path_routing.json: {path}")
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError(f"顶层 JSON 必须是对象，实际是 {type(raw).__name__}")

    fast = raw.get("fast_path_routing")
    if not isinstance(fast, dict):
        raise ValueError("缺少或类型错误: fast_path_routing")

    intents = fast.get("intents")
    if not isinstance(intents, dict):
        raise ValueError("缺少或类型错误: fast_path_routing.intents")

    out, dropped_intents, total_dups = {}, [], 0
    for intent_code, obj in intents.items():
        if not isinstance(obj, dict):
            logger.warning("意图 %s 不是对象，跳过", intent_code)
            dropped_intents.append(intent_code)
            continue
        samples = obj.get("samples", [])
        if not isinstance(samples, list):
            logger.warning("意图 %s 的 samples 不是数组，跳过", intent_code)
            dropped_intents.append(intent_code)
            continue

        cleaned, seen, dup = [], set(), 0
        for s in samples:
            if not isinstance(s, str):
                continue
            s2 = s.strip()
            if not s2:
                continue
            if s2 in seen:
                dup += 1
                continue
            seen.add(s2)
            cleaned.append(s2)

        total_dups += dup
        if dup:
            logger.info("意图 %s 内部去重 %d 条", intent_code, dup)
        if cleaned:
            out[intent_code] = cleaned
        else:
            logger.warning("意图 %s 无有效样本，跳过", intent_code)
            dropped_intents.append(intent_code)

    if total_dups:
        logger.info("意图内去重总条数: %d", total_dups)
    if dropped_intents:
        logger.info("被跳过的意图数: %d", len(dropped_intents))
    return out


def find_cross_intent_duplicates(intents: dict) -> list:
    """检测同一文本出现在多个意图中的情况。"""
    text_to_intents = {}
    for code, samples in intents.items():
        for s in samples:
            text_to_intents.setdefault(s, []).append(code)
    return [
        {"text": t, "intents": codes}
        for t, codes in text_to_intents.items()
        if len(codes) > 1
    ]


# ---------------------------------------------------------------------------
# 数值工具
# ---------------------------------------------------------------------------
def normalize_embeddings(X: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(X, axis=1, keepdims=True) + 1e-12
    return X / norms


def upper_triangle_no_diag(values_2d: np.ndarray) -> np.ndarray:
    n = values_2d.shape[0]
    if n <= 1:
        return np.array([], dtype=np.float32)
    idx = np.triu_indices(n, k=1)
    return values_2d[idx].astype(np.float32)


def summarize_array(arr) -> dict:
    empty = {
        "count": 0,
        "mean": None, "median": None, "min": None, "max": None,
        "p05": None, "p25": None, "p75": None, "p95": None, "std": None,
    }
    if arr is None:
        return empty
    arr = np.asarray(arr)
    if arr.size == 0:
        return empty
    arr = arr[np.isfinite(arr)]
    if arr.size == 0:
        return empty
    return {
        "count": int(arr.size),
        "mean": float(np.mean(arr)),
        "median": float(np.median(arr)),
        "min": float(np.min(arr)),
        "max": float(np.max(arr)),
        "p05": float(np.quantile(arr, 0.05)),
        "p25": float(np.quantile(arr, 0.25)),
        "p75": float(np.quantile(arr, 0.75)),
        "p95": float(np.quantile(arr, 0.95)),
        "std": float(np.std(arr)),
    }


# ---------------------------------------------------------------------------
# 相似度与统计
# ---------------------------------------------------------------------------
def compute_similarity(emb: np.ndarray, chunk_size: int | None = None,
                       max_mem_mb: int = 256) -> np.ndarray:
    """分块计算 n x n 余弦相似度矩阵（已 clip 到 [-1, 1]）。
    自动根据 n 估算块大小以控制峰值内存。"""
    n = emb.shape[0]
    if chunk_size is None:
        # 单块内存 ≈ chunk_size * n * 4 字节
        chunk_size = max(1, int(max_mem_mb * 1024 * 1024 / (n * 4)))
        chunk_size = min(chunk_size, n)

    sim = np.empty((n, n), dtype=np.float32)
    for start in range(0, n, chunk_size):
        end = min(start + chunk_size, n)
        block = emb[start:end] @ emb.T
        sim[start:end] = np.clip(block, -1.0, 1.0).astype(np.float32)
    return sim


def compute_intra(sim: np.ndarray, labels: np.ndarray, intent_codes: list):
    per_intent = {}
    chunks = []
    for li, code in enumerate(intent_codes):
        idx = np.where(labels == li)[0]
        if idx.size <= 1:
            per_intent[code] = summarize_array(np.array([], dtype=np.float32))
            continue
        block = sim[np.ix_(idx, idx)]
        vals = upper_triangle_no_diag(block)
        per_intent[code] = summarize_array(vals)
        chunks.append(vals)
    all_vals = np.concatenate(chunks) if chunks else np.array([], dtype=np.float32)
    return per_intent, all_vals


def compute_inter_unordered(sim: np.ndarray, labels: np.ndarray) -> np.ndarray:
    """无序跨意图对（i < j 且 label_i != label_j）。"""
    n = sim.shape[0]
    chunks = []
    for i in range(n - 1):
        row = sim[i, i + 1:]
        mask = labels[i + 1:] != labels[i]
        if mask.any():
            chunks.append(row[mask].astype(np.float32))
    return np.concatenate(chunks) if chunks else np.array([], dtype=np.float32)


def compute_separation(sim: np.ndarray, labels: np.ndarray, intent_codes: list):
    """pos_max 排除自身；neg_max 取其它意图样本最大相似度。
    单样本意图 pos_max 保持 NaN。"""
    n = sim.shape[0]
    pos_max = np.full(n, np.nan, dtype=np.float64)

    for li in range(len(intent_codes)):
        idx = np.where(labels == li)[0]
        if idx.size < 2:
            continue
        block = sim[np.ix_(idx, idx)].copy()
        np.fill_diagonal(block, NEG_INF)
        pos_max[idx] = block.max(axis=1)

    same = labels[:, None] == labels[None, :]
    sim_neg = np.where(same, NEG_INF, sim)
    neg_max = sim_neg.max(axis=1).astype(np.float64)
    neg_max = np.where(neg_max <= NEG_INF / 2, np.nan, neg_max)

    margin = pos_max - neg_max  # NaN 自动传播
    return pos_max, neg_max, margin


def compute_nearest_neighbor(sim: np.ndarray, labels: np.ndarray, intent_codes: list,
                             top_confusions_n: int):
    n = sim.shape[0]
    sim2 = sim.copy()
    np.fill_diagonal(sim2, NEG_INF)
    nn_idx = sim2.argmax(axis=1)
    nn_pred = labels[nn_idx]
    top1_acc = float(np.mean(nn_pred == labels))

    def topk_hit_rate(k: int):
        if n <= k:
            return None
        topk = np.argpartition(sim2, -k, axis=1)[:, -k:]
        hit = (labels[topk] == labels[:, None]).any(axis=1)
        return float(np.mean(hit))

    topk_rates = {
        "top3_hit_rate": topk_hit_rate(3),
        "top5_hit_rate": topk_hit_rate(5),
        "top10_hit_rate": topk_hit_rate(10),
    }

    confusion = {}
    for i in range(n):
        true_li = int(labels[i])
        pred_li = int(nn_pred[i])
        if true_li == pred_li:
            continue
        key = (intent_codes[true_li], intent_codes[pred_li])
        confusion[key] = confusion.get(key, 0) + 1

    top = sorted(confusion.items(), key=lambda x: x[1], reverse=True)[: max(0, top_confusions_n)]
    top_json = [
        {"true_intent": t, "pred_intent": p, "count": int(c)}
        for (t, p), c in top
    ]
    return top1_acc, topk_rates, top_json, nn_pred


def per_intent_accuracy(labels: np.ndarray, nn_pred: np.ndarray, intent_codes: list) -> dict:
    out = {}
    for li, code in enumerate(intent_codes):
        mask = labels == li
        if mask.sum() == 0:
            continue
        out[code] = {
            "count": int(mask.sum()),
            "top1_accuracy": float(np.mean(nn_pred[mask] == labels[mask])),
        }
    return out


# ---------------------------------------------------------------------------
# 环境信息
# ---------------------------------------------------------------------------
def get_library_versions() -> dict:
    versions = {
        "python": platform.python_version(),
        "numpy": np.__version__,
        "sentence_transformers": getattr(sentence_transformers, "__version__", "unknown"),
    }
    try:
        import torch
        versions["torch"] = torch.__version__
        versions["cuda_available"] = bool(torch.cuda.is_available())
    except Exception:
        versions["torch"] = "unknown"
        versions["cuda_available"] = None
    return versions


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fast-json", type=str, default=None,
                        help="fast_path_routing.json 路径")
    parser.add_argument("--model", type=str,
                        default="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--output", type=str, default=None,
                        help="输出报告完整路径（.json）")
    parser.add_argument("--output-dir", type=str, default=None,
                        help="输出目录（未指定 --output 时使用）")
    parser.add_argument("--top-confusions", type=int, default=20)
    parser.add_argument("--chunk-size", type=int, default=None,
                        help="相似度分块大小（默认按内存自动）")
    parser.add_argument("--device", type=str, default=None, help="cpu/cuda，默认自动")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    setup_logging(args.verbose)
    t0 = time.time()

    # 解析路径
    if args.fast_json is None:
        script_dir = Path(__file__).resolve().parent
        acl_root = script_dir.parent
        fast_json_path = acl_root / "GLOBAL" / "fast_path_routing.json"
    else:
        fast_json_path = Path(args.fast_json)

    try:
        intents = load_intents_from_fast_path_json(fast_json_path)
    except Exception as e:
        logger.error("加载失败: %s", e)
        sys.exit(1)

    if not intents:
        logger.error("没有有效意图")
        sys.exit(1)

    intent_codes = sorted(intents.keys())
    total_intents = len(intent_codes)
    logger.info("意图数: %d", total_intents)

    # 跨意图重复检测
    cross_dups = find_cross_intent_duplicates(intents)
    if cross_dups:
        logger.warning("发现 %d 条跨意图重复样本（同一文本出现在多个意图）", len(cross_dups))
        for d in cross_dups[:10]:
            logger.warning("  文本: %r 出现在: %s", d["text"][:60], d["intents"])
    else:
        logger.info("未发现跨意图重复样本")

    # 扁平化
    all_texts, all_labels = [], []
    for li, code in enumerate(intent_codes):
        for s in intents[code]:
            all_texts.append(s)
            all_labels.append(li)
    all_labels = np.asarray(all_labels, dtype=np.int32)
    n = len(all_texts)
    if n < 2:
        logger.error("样本数 %d < 2，无法计算", n)
        sys.exit(1)
    logger.info("样本总数: %d", n)

    single_sample_intents = [c for c in intent_codes if len(intents[c]) == 1]
    if single_sample_intents:
        logger.warning("单样本意图 %d 个（其 separation 统计会被排除）: %s",
                       len(single_sample_intents), single_sample_intents[:10])

    # 编码
    logger.info("加载模型: %s", args.model)
    try:
        model = SentenceTransformer(args.model, device=args.device)
    except Exception as e:
        logger.error("模型加载失败: %s", e)
        sys.exit(1)

    emb = model.encode(
        all_texts,
        batch_size=args.batch_size,
        show_progress_bar=True,
        normalize_embeddings=False,  # 我们自己做归一化
    )
    emb = normalize_embeddings(np.asarray(emb, dtype=np.float32))
    logger.info("编码完成，维度: %d", emb.shape[1])

    # 相似度
    logger.info("计算相似度矩阵 (%d x %d) ...", n, n)
    sim = compute_similarity(emb, chunk_size=args.chunk_size)

    # intra
    logger.info("统计意图内相似度 ...")
    intra_per_intent, intra_all = compute_intra(sim, all_labels, intent_codes)

    # inter（无序）
    logger.info("统计意图间相似度（无序对）...")
    inter_vals = compute_inter_unordered(sim, all_labels)

    # separation
    logger.info("统计分离度 ...")
    pos_max, neg_max, margin = compute_separation(sim, all_labels, intent_codes)
    valid = np.isfinite(margin)
    separation = {
        "pos_max": summarize_array(pos_max),
        "neg_max": summarize_array(neg_max),
        "margin": summarize_array(margin),
        "margin_gt_0_rate": float(np.mean(margin[valid] > 0)) if valid.any() else None,
        "margin_gt_0_count": int(np.sum(margin[valid] > 0)),
        "valid_count": int(valid.sum()),
        "n": int(n),
        "note": "单样本意图 pos_max=NaN，已从统计中排除",
    }

    # nearest neighbor
    logger.info("最近邻判别 ...")
    nn_top1, nn_topk, top_confusions, nn_pred = compute_nearest_neighbor(
        sim, all_labels, intent_codes, args.top_confusions)

    # 每意图准确率
    per_intent_acc = per_intent_accuracy(all_labels, nn_pred, intent_codes)

    # 输出路径
    if args.output is not None:
        output_path = Path(args.output)
    else:
        out_dir = Path(args.output_dir) if args.output_dir else Path.cwd()
        out_dir.mkdir(parents=True, exist_ok=True)
        out_name = f"intent_similarity_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        output_path = out_dir / out_name
    output_path.parent.mkdir(parents=True, exist_ok=True)

    elapsed = time.time() - t0
    report = {
        "run_at": datetime.now().isoformat(timespec="seconds"),
        "elapsed_seconds": round(elapsed, 2),
        "fast_json_path": str(fast_json_path),
        "model": args.model,
        "device": args.device or "auto",
        "args": vars(args),
        "library_versions": get_library_versions(),
        "total_intents": total_intents,
        "intent_codes": intent_codes,
        "total_samples": int(n),
        "single_sample_intents": single_sample_intents,
        "cross_intent_duplicates_count": len(cross_dups),
        "cross_intent_duplicates": cross_dups[:50],
        "intra_intent_similarity": {
            "all": summarize_array(intra_all),
            "per_intent": intra_per_intent,
        },
        "inter_intent_similarity": summarize_array(inter_vals),
        "separation_nearest_pos_vs_neg": separation,
        "nearest_neighbor": {
            "top1_accuracy": nn_top1,
            **nn_topk,
        },
        "per_intent_nearest_neighbor_accuracy": per_intent_acc,
        "top_confusions_top1_nearest_neighbor": top_confusions,
        "notes": (
            "intra 与 inter 均为无序样本对(i<j)；"
            "separation：pos_max 排除自身，neg_max 取任意其它意图样本最大相似度，"
            "单样本意图 pos_max=NaN 且从统计中排除；"
            "相似度已 clip 到 [-1,1]；样本已 strip 并去重；"
            "nearest-neighbor 为留一法 Top-1 准确率，非线上泛化性能。"
        ),
    }

    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    logger.info("报告已保存: %s", output_path)
    logger.info("Top-1 acc: %.4f | margin_gt_0: %s | 耗时 %.1fs",
                nn_top1,
                f"{separation['margin_gt_0_rate']:.4f}"
                if separation["margin_gt_0_rate"] is not None else "N/A",
                elapsed)
    if top_confusions:
        f = top_confusions[0]
        logger.info("Top confusion: %s -> %s (count=%d)",
                    f["true_intent"], f["pred_intent"], f["count"])


if __name__ == "__main__":
    main()
