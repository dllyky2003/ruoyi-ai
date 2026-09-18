package com.ruoyi.ems.context;

import com.ruoyi.ems.constant.CaseKeys;
import com.ruoyi.ems.domain.CaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Map 型变量的原子 read-modify-write 工具。
 *
 * <p>背景：
 *   EmsContextManager.batchSetVarsWithVersion 是纯覆盖写 + 乐观锁。
 *   intervention.* 是 Map 型变量，需要"读出 → 改一个字段 → 整体写回"。
 *   多线程（技能 + 派生刷新）下必须处理版本冲突重试。
 *
 * <p><b>命名约定（务必遵守）</b>：
 *   局部变量<b>不要</b>命名为 log / logger，否则会与 @Slf4j 生成的字段冲突。
 *   事件日志列表统一命名为 eventList；Map 副本统一命名为 next。
 */
@Component
@Slf4j
public class MapWriteHelper {

    private static final int MAX_RETRY = 3;

    @Autowired
    private EmsContextManager contextManager;

    // ============================================================
    // 1. Map 型变量 read-modify-write
    // ============================================================

    /** 对 Map 型变量做一次原子 read-modify-write。mutator 收到的已是深拷贝副本。 */
    @SuppressWarnings("unchecked")
    public boolean mutateMap(String sessionId, String mapKey, String writer,
                             Consumer<Map<String, Object>> mutator) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            CaseContext ctx = contextManager.getReadOnlySnapshot(sessionId);
            if (ctx == null) {
                log.warn("[MapWriteHelper] ctx 不存在: session={}, key={}", sessionId, mapKey);
                return false;
            }
            long version = ctx.getVersion();

            Object cur = ctx.getVariables().get(mapKey);
            if (cur == null) {
                cur = contextManager.getVariableDefault(ctx.getCaseType(), mapKey);
            }

            Map<String, Object> next = deepCopyMap(cur);
            mutator.accept(next);

            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId, Map.of(mapKey, next), writer, version);
            if (ok) {
                log.info("[MapWriteHelper] ✓ {} 更新成功: {}", mapKey, next);
                return true;
            }
            log.warn("[MapWriteHelper] 版本冲突，重试 {}/{}: session={}, key={}",
                attempt, MAX_RETRY, sessionId, mapKey);
        }
        log.error("[MapWriteHelper] 重试耗尽: session={}, key={}", sessionId, mapKey);
        return false;
    }

    // ============================================================
    // 2. case.eventLog 追加（★ 本方法曾经有 bug，已修复）
    // ============================================================

    /**
     * 向 case.eventLog 追加一条事件。结构：{code, time, source, params?}
     *
     * <p><b>命名约束</b>：局部变量叫 eventList，绝不叫 log ——
     * 否则可能与 @Slf4j 生成的 log 字段混用，把 Logger 写进变量值。
     */
    @SuppressWarnings("unchecked")
    public boolean appendEvent(String sessionId, String code, String writer,
                               Map<String, Object> params) {
        if (code == null || code.isEmpty()) {
            log.warn("[MapWriteHelper] appendEvent code 为空");
            return false;
        }
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            CaseContext ctx = contextManager.getReadOnlySnapshot(sessionId);
            if (ctx == null) return false;
            long version = ctx.getVersion();

            // ---- 读出已有事件列表（可能是 null / List / 意外类型） ----
            Object cur = ctx.getVariables().get(CaseKeys.CASE_EVENT_LOG);
            List<Map<String, Object>> eventList = new ArrayList<>();
            if (cur instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) {
                        eventList.add(new LinkedHashMap<>((Map<String, Object>) m));
                    }
                }
            } else if (cur != null) {
                log.warn("[MapWriteHelper] case.eventLog 期望 List，实际 {}，已重置",
                    cur.getClass().getName());
            }

            // ---- 构造新事件 ----
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", code);
            entry.put("time", System.currentTimeMillis());
            entry.put("source", writer);
            if (params != null && !params.isEmpty()) {
                entry.put("params", new LinkedHashMap<>(params));
            }
            eventList.add(entry);

            // ---- 显式构造更新 Map（不使用 Map.of，避免编译器歧义） ----
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put(CaseKeys.CASE_EVENT_LOG, eventList);

            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId, updates, writer, version);
            if (ok) return true;

            log.warn("[MapWriteHelper] eventLog 版本冲突，重试 {}/{}: code={}",
                attempt, MAX_RETRY, code);
        }
        return false;
    }

    // ============================================================
    // 3. 扁平变量批量写
    // ============================================================

    /** 批量写扁平变量（非 Map 型）。 */
    public boolean setFlatVars(String sessionId, Map<String, Object> vars, String writer) {
        if (vars == null || vars.isEmpty()) return true;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            CaseContext ctx = contextManager.getReadOnlySnapshot(sessionId);
            if (ctx == null) return false;
            boolean ok = contextManager.batchSetVarsWithVersion(
                sessionId, vars, writer, ctx.getVersion());
            if (ok) return true;
            log.warn("[MapWriteHelper] flat 版本冲突，重试 {}/{}: session={}, keys={}",
                attempt, MAX_RETRY, sessionId, vars.keySet());
        }
        return false;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Object src) {
        if (src == null) return new LinkedHashMap<>();
        if (!(src instanceof Map)) {
            log.warn("[MapWriteHelper] 期望 Map，实际 {}，重置为空 Map",
                src.getClass().getName());
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) src);
    }
}
