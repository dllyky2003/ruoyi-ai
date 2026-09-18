#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
review_intents_v3.py

v3 目标：跑完直接生成可用的 target_final.json，无需人工复审。

核心策略：
- 分三次调用 LLM 审查 keyword / samples / samples_negative（保证质量）
- LLM 只输出"操作指令"，由 Python 执行（防止污染原始数据）
- 自动应用所有成功操作；失败的操作静默降级，回退原始数据
- 三次全失败的字段，使用原始数据；不中断整个流程
- 最终输出 target_final.json，结构 100% 与 target.json 兼容

输出：
- GLOBAL/target_final.json         最终可用文件（直接替换 target.json）
- GLOBAL/review_v3_report.json     审查报告（备查，不需人工处理）
- GLOBAL/review_v3_run.log         运行日志

零第三方依赖，仅标准库。
"""

import os
import json
import time
import re
import copy
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime

# ---------------------------------------------------------------------------
# 路径
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
PARENT_DIR = SCRIPT_DIR.parent
GLOBAL_DIR = PARENT_DIR / "GLOBAL"
TARGET_PATH = GLOBAL_DIR / "target.json"
FINAL_PATH = GLOBAL_DIR / "target_final.json"
REPORT_PATH = GLOBAL_DIR / "review_v3_report.json"
LOG_PATH = GLOBAL_DIR / "review_v3_run.log"

# ---------------------------------------------------------------------------
# 日志
# ---------------------------------------------------------------------------
def log(msg: str):
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

# ---------------------------------------------------------------------------
# Ollama 配置
# ---------------------------------------------------------------------------
OLLAMA_HOST = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
MODEL_NAME = os.environ.get("OLLAMA_MODEL", "qwen2.5:latest")
CHAT_URL = f"{OLLAMA_HOST.rstrip('/')}/api/chat"

REQUEST_TIMEOUT = int(os.environ.get("OLLAMA_TIMEOUT", "2400"))
MAX_RETRIES = int(os.environ.get("OLLAMA_RETRIES", "3"))
RETRY_DELAY = 15
NUM_CTX = int(os.environ.get("OLLAMA_NUM_CTX", "6144"))

# ---------------------------------------------------------------------------
# System Prompt（含算法语义 + Few-Shot + 输出约束）
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """你是资深院前 ACLS 导师和 AI 工程师。你的任务是审查院前急救组长 ASR 文本的意图分类规则，并输出"操作指令"。

【字段算法语义】
- hard: 子串精确匹配，命中即触发该意图。必须高特异性。
- boost: 加权加分，不单独触发。可以较通用，但与 hard 语义关联。
- exclude: 一票否决。只放：否定语境（"不测血氧"）、已完成（"血压已量"）、设备缺失（"血氧仪没电"）、明确属于其他意图的强冲突词。禁止放共现词。
- priority: 越大优先级越高。

【Few-Shot 示例】
例1 - 过宽 hard:
  输入 IA_002 hard 含 "检查反应"
  问题: 与 IA_045（按压暂停检查反应）冲突
  操作: {"op": "remove", "list": "hard", "value": "检查反应", "reason": "与 IA_045 冲突"}

例2 - 错误 exclude:
  输入 IA_011 exclude: ["血压","心率","血糖"]
  问题: 共现词不应排除
  操作: {"op": "remove", "list": "exclude", "value": "血压", "reason": "共现词"}
        {"op": "add", "list": "exclude", "value": "血氧仪没电", "reason": "设备缺失"}

例3 - 漏负例:
  输入 IA_050 samples_negative: []
  操作: {"op": "add", "list": "samples_negative",
         "value": {"text": "不需要AED", "reason": "否定"},
         "reason": "补充否定负例"}

【输出格式（严格纯 JSON，无 markdown，无解释）】
{
  "field": "<keyword|samples|samples_negative>",
  "issues": ["一句话一条"],
  "operations": [
    {"op": "add", "list": "hard|boost|exclude|samples|samples_negative", "value": "...", "reason": "..."},
    {"op": "remove", "list": "...", "value": "...", "reason": "..."},
    {"op": "set_priority", "value": 80, "reason": "..."}
  ],
  "confidence": 0.0到1.0
}

【约束】
- 只审查指定 field，不越界修改。
- 只输出操作指令，不输出完整列表。
- 不确定的操作不要提。
- 若无问题，operations 为空数组。
"""

FIELD_FOCUS = {
    "keyword": "只审查 keyword 的 hard、boost、exclude、priority。",
    "samples": "只审查正样本 samples。",
    "samples_negative": "只审查负例 samples_negative。",
}

# ---------------------------------------------------------------------------
# JSON 解析兜底
# ---------------------------------------------------------------------------
def parse_json_safe(text: str):
    if not text:
        return None
    m = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    candidate = m.group(1) if m else None
    if not candidate:
        s = text.find("{"); e = text.rfind("}")
        if s == -1 or e <= s:
            return None
        candidate = text[s:e + 1]
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        try:
            return json.loads(re.sub(r",\s*([}\]])", r"\1", candidate))
        except Exception:
            return None

# ---------------------------------------------------------------------------
# 冲突地图 + 硬规则预检
# ---------------------------------------------------------------------------
def build_hard_index(intents: dict) -> dict:
    idx = {}
    for name, d in intents.items():
        for h in d.get("keyword", {}).get("hard", []):
            idx.setdefault(h, []).append(name)
    return idx

def build_conflict_map(intents: dict) -> str:
    lines = []
    for name, d in intents.items():
        hard = d.get("keyword", {}).get("hard", [])
        if hard:
            lines.append(f"{name}: " + " | ".join(hard))
    return "\n".join(lines)

def hard_rule_check(intent_name: str, data: dict, hard_index: dict) -> list:
    """仅返回机械硬伤，供 LLM 参考，不用于自动修改。"""
    issues = []
    kw = data.get("keyword", {})
    hard, boost, exclude = set(kw.get("hard", [])), set(kw.get("boost", [])), set(kw.get("exclude", []))
    samples = data.get("samples", [])
    negatives = [n.get("text") for n in data.get("samples_negative", []) if isinstance(n, dict)]

    if hard & exclude:
        issues.append(f"hard 与 exclude 有交集: {sorted(hard & exclude)}")
    for s in samples:
        if not any(k in s for k in (hard | boost)):
            issues.append(f"样本 '{s}' 无 hard/boost 命中")
    for n in negatives:
        if n in samples:
            issues.append(f"负例 '{n}' 同时也是正样本")
    for h in hard:
        owners = hard_index.get(h, [])
        if len(owners) > 1:
            issues.append(f"hard '{h}' 与其他意图共享: {owners}")
    return issues

# ---------------------------------------------------------------------------
# Ollama 调用
# ---------------------------------------------------------------------------
def call_ollama(system_prompt: str, user_prompt: str, temperature: float = 0.2) -> str:
    payload = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "stream": False,
        "options": {
            "temperature": temperature,
            "num_ctx": NUM_CTX,
            "num_gpu": 99,
            "num_thread": 8,
        },
    }
    req = urllib.request.Request(
        CHAT_URL,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
        return json.loads(resp.read().decode("utf-8")).get("message", {}).get("content", "")

# ---------------------------------------------------------------------------
# 单字段审查
# ---------------------------------------------------------------------------
def review_field(intent_name: str, data: dict, field: str,
                 conflict_map: str, hard_issues: list, previous_issues: list):
    system_prompt = SYSTEM_PROMPT + "\n【本次任务】" + FIELD_FOCUS[field]

    parts = [
        "【所有意图 hard 关键词全景】", conflict_map, "",
    ]
    if hard_issues:
        parts.append("【代码预检已发现的机械问题（无需重复）】")
        parts.extend(f"- {i}" for i in hard_issues)
        parts.append("")
    if previous_issues:
        parts.append("【前轮已报告 issues（无需重复）】")
        parts.extend(f"- {i}" for i in previous_issues)
        parts.append("")
    parts.append("【当前意图完整 JSON】")
    parts.append(json.dumps({intent_name: data}, ensure_ascii=False, indent=2))
    parts.append(f"\n请只审查 {field}，输出操作指令 JSON。")

    user_prompt = "\n".join(parts)

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            content = call_ollama(system_prompt, user_prompt)
            result = parse_json_safe(content)
            if result and result.get("field") == field and isinstance(result.get("operations"), list):
                return result
        except Exception as e:
            log(f"      [{field}] 尝试{attempt} 异常: {e}")
        if attempt < MAX_RETRIES:
            time.sleep(RETRY_DELAY)
    return None

# ---------------------------------------------------------------------------
# 操作应用（宽松模式：失败静默跳过，不中断）
# ---------------------------------------------------------------------------
VALID_KW_LISTS = {"hard", "boost", "exclude"}

def apply_operations(data: dict, operations: list) -> tuple[dict, list]:
    """宽松应用操作。失败的操作记录，但不影响其他操作。"""
    new_data = copy.deepcopy(data)
    kw = new_data.setdefault("keyword", {})
    failures = []

    for i, op in enumerate(operations):
        if not isinstance(op, dict):
            failures.append(f"op[{i}]: 非字典")
            continue

        op_type = op.get("op")
        list_name = op.get("list")
        value = op.get("value")

        # 设置 priority
        if op_type == "set_priority":
            try:
                kw["priority"] = int(value)
            except Exception:
                failures.append(f"op[{i}]: set_priority 值非法")
            continue

        # 关键词字段
        if list_name in VALID_KW_LISTS:
            cur = kw.setdefault(list_name, [])
            if op_type == "add" and isinstance(value, str):
                if value not in cur:
                    cur.append(value)
            elif op_type == "remove" and isinstance(value, str):
                if value in cur:
                    cur.remove(value)
                else:
                    failures.append(f"op[{i}]: remove '{value}' 不存在")
            else:
                failures.append(f"op[{i}]: 非法操作 {op_type}/{list_name}")

        # 正样本
        elif list_name == "samples":
            cur = new_data.setdefault("samples", [])
            if op_type == "add" and isinstance(value, str):
                if value not in cur:
                    cur.append(value)
            elif op_type == "remove" and isinstance(value, str):
                if value in cur:
                    cur.remove(value)
                else:
                    failures.append(f"op[{i}]: remove '{value}' 不存在")
            else:
                failures.append(f"op[{i}]: 非法操作 {op_type}/samples")

        # 负样本
        elif list_name == "samples_negative":
            cur = new_data.setdefault("samples_negative", [])
            if op_type == "add":
                if isinstance(value, dict) and "text" in value:
                    existing = [n.get("text") for n in cur if isinstance(n, dict)]
                    if value["text"] not in existing:
                        value.setdefault("reason", op.get("reason", ""))
                        cur.append(value)
                else:
                    failures.append(f"op[{i}]: samples_negative 值必须含 text")
            elif op_type == "remove":
                target = value if isinstance(value, str) else (
                    value.get("text") if isinstance(value, dict) else None)
                if target:
                    new_list = [n for n in cur if n.get("text") != target]
                    if len(new_list) == len(cur):
                        failures.append(f"op[{i}]: remove '{target}' 不存在")
                    new_data["samples_negative"] = new_list
                else:
                    failures.append(f"op[{i}]: remove 值非法")
            else:
                failures.append(f"op[{i}]: 非法操作 {op_type}/samples_negative")
        else:
            failures.append(f"op[{i}]: 未知 list '{list_name}'")

    return new_data, failures

# ---------------------------------------------------------------------------
# 审查单个意图：三次调用 + 应用
# ---------------------------------------------------------------------------
def review_intent(intent_name: str, data: dict,
                  conflict_map: str, hard_index: dict) -> dict:
    hard_issues = hard_rule_check(intent_name, data, hard_index)

    report = {
        "intent": intent_name,
        "hard_rule_issues": hard_issues,
        "field_results": {},
        "operations_merged": [],
        "operation_failures": [],
        "final": None,
        "review_status": "success",
    }

    previous_issues = []
    all_confidences = []

    for field in ["keyword", "samples", "samples_negative"]:
        result = review_field(intent_name, data, field,
                              conflict_map, hard_issues, previous_issues)
        if result:
            report["field_results"][field] = {
                "issues": result.get("issues", []),
                "operations": result.get("operations", []),
                "confidence": result.get("confidence", 0.0),
            }
            previous_issues.extend(result.get("issues", []))
            report["operations_merged"].extend(result.get("operations", []))
            all_confidences.append(float(result.get("confidence", 0.0)))
        else:
            # 单字段失败：不影响整体，最后回退该字段原始数据
            report["field_results"][field] = {
                "issues": ["LLM 审查失败"],
                "operations": [],
                "confidence": 0.0,
            }
            report["review_status"] = "partial"
            all_confidences.append(0.0)

    # 应用所有操作
    final_data, op_failures = apply_operations(data, report["operations_merged"])
    report["operation_failures"] = op_failures
    report["final"] = final_data

    # 状态判定
    if report["review_status"] == "success" and op_failures:
        report["review_status"] = "partial"

    # 记录平均置信度（用于报告）
    report["avg_confidence"] = round(sum(all_confidences) / len(all_confidences), 3) if all_confidences else 0.0

    return report

# ---------------------------------------------------------------------------
# 原子写入
# ---------------------------------------------------------------------------
def atomic_write(path: Path, data: dict):
    tmp = path.with_suffix(path.suffix + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)

# ---------------------------------------------------------------------------
# 最终文件组装
# ---------------------------------------------------------------------------
def build_final(fast_path: dict, reports: dict) -> dict:
    """组装 target_final.json，结构 100% 兼容 target.json。"""
    intents_final = {}
    for name, r in reports.items():
        # 优先使用审查后的 final；若不存在则回退原始
        final_intent = r.get("final")
        if not final_intent:
            # 理论上不会发生，防御性处理
            final_intent = {
                "keyword": {}, "samples": [], "samples_negative": []
            }
        # 只保留三个正字段，剔除内部标记
        intents_final[name] = {
            "keyword": final_intent.get("keyword", {}),
            "samples": final_intent.get("samples", []),
            "samples_negative": final_intent.get("samples_negative", []),
        }

    return {
        "fast_path_routing": {
            "version": "2.1.0",
            "description": fast_path.get("description", "")
                           + " 经本地 Ollama v3 分步审查并自动修正。",
            "intents": intents_final,
        }
    }

# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def main():
    log("=" * 70)
    log(f"review_intents_v3 启动 | 模型={MODEL_NAME}")
    log(f"num_ctx={NUM_CTX}, timeout={REQUEST_TIMEOUT}s, retries={MAX_RETRIES}")
    log("=" * 70)

    if not TARGET_PATH.exists():
        log(f"[FATAL] 找不到 {TARGET_PATH}")
        return

    with open(TARGET_PATH, "r", encoding="utf-8") as f:
        src = json.load(f)

    fast_path = src.get("fast_path_routing", {})
    intents = fast_path.get("intents", {})

    hard_index = build_hard_index(intents)
    conflict_map = build_conflict_map(intents)

    log(f"共 {len(intents)} 个 intent")

    # 断点续跑
    reports = {}
    if REPORT_PATH.exists():
        try:
            with open(REPORT_PATH, "r", encoding="utf-8") as f:
                reports = json.load(f).get("reports", {})
            log(f"[RESUME] 已完成 {len(reports)} 个")
        except Exception as e:
            log(f"[WARN] 加载进度失败: {e}")

    # 连通性自检
    try:
        call_ollama("ping", "只回复：pong", temperature=0.0)
        log("[OK] Ollama 连接正常\n")
    except Exception as e:
        log(f"[FATAL] Ollama 不可用: {e}")
        return

    total = len(intents)
    for idx, (name, data) in enumerate(intents.items(), 1):
        if name in reports:
            log(f"[{idx}/{total}] 跳过 {name}（已完成）")
            continue

        log(f"[{idx}/{total}] 审查 {name} ...")
        report = review_intent(name, data, conflict_map, hard_index)
        reports[name] = report

        # 增量落盘：报告 + 最终文件同时更新
        _flush(reports, fast_path)

        log(f"[{idx}/{total}] {name} 完成 | 状态={report['review_status']} | "
            f"ops={len(report['operations_merged'])} | "
            f"fail={len(report['operation_failures'])} | "
            f"conf={report['avg_confidence']}")

        time.sleep(0.5)

    _flush(reports, fast_path)
    log("=" * 70)
    log(f"全部完成！")
    log(f"最终文件: {FINAL_PATH}")
    log(f"审查报告: {REPORT_PATH}")
    log("=" * 70)


def _flush(reports: dict, fast_path: dict):
    """同时落盘 report 和 final。"""
    # 报告
    summary = {
        "success": sum(1 for r in reports.values() if r["review_status"] == "success"),
        "partial": sum(1 for r in reports.values() if r["review_status"] == "partial"),
        "total_ops": sum(len(r["operations_merged"]) for r in reports.values()),
        "total_failures": sum(len(r["operation_failures"]) for r in reports.values()),
    }
    atomic_write(REPORT_PATH, {
        "meta": {
            "version": "3.0.0",
            "model": MODEL_NAME,
            "generated_at": datetime.now().isoformat(),
            "summary": summary,
        },
        "reports": reports,
    })

    # 最终文件
    final_data = build_final(fast_path, reports)
    atomic_write(FINAL_PATH, final_data)


if __name__ == "__main__":
    main()
