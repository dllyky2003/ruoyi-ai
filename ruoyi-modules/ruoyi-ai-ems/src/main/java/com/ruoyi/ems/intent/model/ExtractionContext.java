package com.ruoyi.ems.intent.model;

import com.ruoyi.ems.domain.CaseContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 参数抽取上下文，封装抽取所需的原始文本、意图编码，
 * 并通过 CaseContext 访问和修改跨轮次记忆（存储在 vars.__extraction_memory 中）。
 */
public class ExtractionContext {
    private final String rawText;
    private final String intentCode;
    private final CaseContext caseContext;
    private final String caseInstanceId;

    // 内存中的记忆缓存（从 caseContext 加载或新建）
    private Map<String, Object> memoryCache;

    public ExtractionContext(String rawText, String intentCode, CaseContext caseContext, String caseInstanceId) {
        this.rawText = rawText == null ? "" : rawText;
        this.intentCode = intentCode == null ? "UNKNOWN" : intentCode;
        this.caseContext = caseContext;
        this.caseInstanceId = caseInstanceId;
        loadMemory();
    }

    @SuppressWarnings("unchecked")
    private void loadMemory() {
        if (caseContext != null && caseContext.getVariables() != null) {
            Object mem = caseContext.getVariables().get("__extraction_memory");
            if (mem instanceof Map) {
                memoryCache = (Map<String, Object>) mem;
            } else {
                memoryCache = new HashMap<>();
            }
        } else {
            memoryCache = new HashMap<>();
        }
    }

    public String getRawText() { return rawText; }
    public String getIntentCode() { return intentCode; }
    public String getCaseInstanceId() { return caseInstanceId; }

    /**
     * 获取当前记忆（只读副本）
     */
    public Map<String, Object> getMemory() {
        return memoryCache != null ? new HashMap<>(memoryCache) : new HashMap<>();
    }

    /**
     * 从记忆中获取某个键的值
     */
    public Object getFromMemory(String key) {
        return memoryCache != null ? memoryCache.get(key) : null;
    }

    /**
     * 向记忆中写入键值（仅修改内存缓存，需调用 saveMemory 持久化）
     */
    public void putToMemory(String key, Object value) {
        if (memoryCache == null) {
            memoryCache = new HashMap<>();
        }
        memoryCache.put(key, value);
    }

    /**
     * 获取当前 CaseContext 版本号，用于乐观锁更新
     */
    public long getVersion() {
        return caseContext != null ? caseContext.getVersion() : 0L;
    }

    /**
     * 准备要保存的记忆数据（供 ParameterExtractor 调用）
     */
    public Map<String, Object> getMemoryForSave() {
        return memoryCache != null ? new HashMap<>(memoryCache) : new HashMap<>();
    }
}
