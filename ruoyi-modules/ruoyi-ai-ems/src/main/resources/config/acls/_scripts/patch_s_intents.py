import json
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
ACLS_DIR   = SCRIPT_DIR.parent
TARGET     = ACLS_DIR / "cases" / "stable_narrow" / "_intents.json"
OUTPUT     = ACLS_DIR / "cases" / "stable_narrow" / "_intents_new.json"

print(f"读取: {TARGET}")

# 给指定意图追加 negative_regex，防止抢占
APPEND_NEGATIVE = {
    "S-101-1-4": [
        "转复为", "R-R间期", "RR间期", "不齐",
        "变成室颤", "推完", "腺苷后室颤", "变宽QRS", "没脉搏"
    ],
    "S-101-4-1": [
        "非同步", "R-on-T", "尖端扭转"
    ],
    "S-101-1-1": [
        "预激", "WPW", "δ波"
    ],
    "S-101-2-5": [
        "预激", "WPW", "宽QRS"
    ],
    "S-101-2-6": [
        "电击键", "可以放电", "清场不彻底"
    ],
    "S-101-2-7": [],  # 只用于占位，若不需要可删
}

with open(TARGET, 'r', encoding='utf-8') as f:
    data = json.load(f)

added = {}

def process(lst):
    for intent in lst:
        code = intent.get('intent_code')
        if not code or code not in APPEND_NEGATIVE:
            continue
        neg = APPEND_NEGATIVE[code]
        if not neg:
            continue
        trg = intent.setdefault('trg', {})
        neg_list = trg.setdefault('negative_regex', [])
        for pat in neg:
            if pat not in neg_list:
                neg_list.append(pat)
                added[code] = added.get(code, 0) + 1

for layer in data.get('layers', []):
    for sd in layer.get('sub_domains', []):
        process(sd.get('intents', []))

with open(OUTPUT, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\n✅ 完成！")
for code in sorted(added.keys()):
    print(f"  {code}: +{added[code]} 条 negative")
print(f"\n总计: {sum(added.values())} 条")
print(f"新文件: {OUTPUT}")
