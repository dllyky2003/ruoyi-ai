import json
from pathlib import Path

# 原始字典文件
DICT_PATH = Path("dict_v1.3.0.json")

# 上一条回复中生成的 examples_by_intent JSON
EXAMPLES_PATH = Path("examples_by_intent.json")

# 合并后输出文件
OUT_PATH = Path("dict_v1.3.0_with_examples.json")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def merge_examples(data: dict, examples: dict) -> tuple[int, list[str]]:
    merged_count = 0
    missing_codes = []

    for layer in data.get("layers", []):
        for sub_domain in layer.get("sub_domains", []):
            for intent in sub_domain.get("intents", []):
                intent_code = intent.get("intent_code")
                if not intent_code:
                    continue

                if intent_code in examples:
                    intent["examples"] = examples[intent_code]
                    merged_count += 1
                else:
                    missing_codes.append(intent_code)

    return merged_count, missing_codes


def main():
    data = load_json(DICT_PATH)
    examples_payload = load_json(EXAMPLES_PATH)

    # 兼容两种格式：
    # 1. {"examples_by_intent": {...}}
    # 2. 直接就是 {...}
    examples = examples_payload.get("examples_by_intent", examples_payload)

    merged_count, missing_codes = merge_examples(data, examples)

    OUT_PATH.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"已合并 {merged_count} 个意图的 examples")
    print(f"输出文件：{OUT_PATH}")

    if missing_codes:
        print("以下 intent_code 未找到对应例句：")
        for code in missing_codes:
            print(" -", code)


if __name__ == "__main__":
    main()
