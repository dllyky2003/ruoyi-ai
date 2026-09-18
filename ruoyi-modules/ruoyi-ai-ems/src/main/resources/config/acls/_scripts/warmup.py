#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ollama embedding 预热脚本 — 把 bge-m3 加载到内存"""

import json
import urllib.request
import time

OLLAMA_URL = "http://localhost:11434"
OLLAMA_MODEL = "bge-m3"

def warmup():
    print(f"🔥 预热 {OLLAMA_MODEL}（首次约 10~40 秒）...")
    data = json.dumps({"model": OLLAMA_MODEL, "prompt": "预热"}).encode("utf-8")
    req = urllib.request.Request(
        f"{OLLAMA_URL}/api/embeddings",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            vec = body.get("embedding", [])
            print(f"✅ 预热完成！耗时 {time.time()-t0:.1f}s，返回维度 {len(vec)}")
    except Exception as e:
        print(f"❌ 预热失败: {e}")
        return False

    # 再看 ollama ps 状态
    try:
        with urllib.request.urlopen(f"{OLLAMA_URL}/api/ps", timeout=5) as resp:
            ps = json.loads(resp.read().decode("utf-8"))
            print("\n📊 当前加载的模型:")
            for m in ps.get("models", []):
                name = m.get("name", "?")
                size = m.get("size", 0) / 1024 / 1024 / 1024
                until = m.get("expires_at", "?")
                print(f"   - {name}   ({size:.2f} GB, 到期 {until})")
    except Exception as e:
        print(f"⚠️ 无法查询 /api/ps: {e}")

    return True

if __name__ == "__main__":
    warmup()
