package com.ruoyi.ems.service;

import com.ruoyi.ems.domain.CaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 事件日志时间戳派生器。
 *
 * <p>从 {@code case.eventLog} 中提取 12 个时间戳，输出到 {@code derived.*Time}。
 * 该逻辑不使用 SpEL，直接操作 List，避免表达式冗长与 SpEL 异常风险。
 *
 * <p>行为约定：
 * <ul>
 *   <li>{@code case.eventLog} 为 null / 非 List / 空列表 → 全部返回 null</li>
 *   <li>事件元素为 null 或缺 code 字段 → 跳过该元素</li>
 *   <li>事件的 time 字段非数字 → 返回 null（不抛异常）</li>
 *   <li>无匹配事件 → 返回 null</li>
 * </ul>
 */
@Component
@Slf4j
public class EventLogTimeDeriver {

    /** 事件日志变量名 */
    private static final String EVENT_LOG_KEY = "case.eventLog";

    /** 事件 code 与派生变量的映射（首次时间） */
    private static final Map<String, String> FIRST_TIME_MAPPINGS;
    /** 事件 code 与派生变量的映射（末次时间） */
    private static final Map<String, String> LAST_TIME_MAPPINGS;

    static {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("FIRST_CONTACT",     "derived.firstContactTime");
        first.put("ECG_12LEAD",        "derived.firstECGTime");
        first.put("DEFIB",             "derived.firstDefibTime");
        first.put("DRUG_PUSH",         "derived.firstDrugTime");
        first.put("ROSC",              "derived.roscTime");
        first.put("SCENE_EXIT",        "derived.sceneExitTime");
        first.put("HOSPITAL_ARRIVAL",  "derived.hospitalArrivalTime");
        first.put("CPR_START",         "derived.cprStartTime");
        first.put("CPR_STOP",          "derived.cprStopTime");
        FIRST_TIME_MAPPINGS = Collections.unmodifiableMap(first);

        Map<String, String> last = new LinkedHashMap<>();
        last.put("FLUSH", "derived.lastFlushTime");
        last.put("DEFIB", "derived.lastShockTime");
        LAST_TIME_MAPPINGS = Collections.unmodifiableMap(last);
        // derived.lastInterventionTime 单独处理（不限 code）
    }

    /**
     * 从 CaseContext 中提取事件日志并派生时间戳。
     *
     * @return key 为派生变量名，value 为 Long 时间戳或 null
     */
    public Map<String, Object> derive(CaseContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (ctx == null) {
            fillNulls(result);
            return result;
        }

        List<Map<String, Object>> log = readEventLog(ctx);

        // 首次时间
        for (Map.Entry<String, String> e : FIRST_TIME_MAPPINGS.entrySet()) {
            result.put(e.getValue(), findFirstTime(log, e.getKey()));
        }
        // 末次时间
        for (Map.Entry<String, String> e : LAST_TIME_MAPPINGS.entrySet()) {
            result.put(e.getValue(), findLastTime(log, e.getKey()));
        }
        // 末次干预（不限 code，取最后一条事件的 time）
        result.put("derived.lastInterventionTime", findLastTime(log, null));

        return result;
    }

    /**
     * 填充所有派生 key 为 null，用于 ctx 为 null 时保持一致输出结构。
     */
    private void fillNulls(Map<String, Object> result) {
        FIRST_TIME_MAPPINGS.values().forEach(k -> result.put(k, null));
        LAST_TIME_MAPPINGS.values().forEach(k -> result.put(k, null));
        result.put("derived.lastInterventionTime", null);
    }

    /**
     * 读取事件日志。返回空列表而非 null，简化后续判断。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readEventLog(CaseContext ctx) {
        Map<String, Object> vars = ctx.getVariables();
        if (vars == null) {
            return Collections.emptyList();
        }
        Object raw = vars.get(EVENT_LOG_KEY);
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) raw;
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        // 逐元素过滤非 Map
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    /**
     * 查找指定 code 的首次事件时间。
     */
    private Long findFirstTime(List<Map<String, Object>> log, String code) {
        if (log.isEmpty()) return null;
        for (Map<String, Object> event : log) {
            if (event == null) continue;
            if (Objects.equals(event.get("code"), code)) {
                Long t = toLong(event.get("time"));
                if (t != null) return t;
            }
        }
        return null;
    }

    /**
     * 查找指定 code 的末次事件时间；code 为 null 时匹配任意 code。
     */
    private Long findLastTime(List<Map<String, Object>> log, String code) {
        if (log.isEmpty()) return null;
        for (int i = log.size() - 1; i >= 0; i--) {
            Map<String, Object> event = log.get(i);
            if (event == null) continue;
            if (code == null || Objects.equals(event.get("code"), code)) {
                Long t = toLong(event.get("time"));
                if (t != null) return t;
            }
        }
        return null;
    }

    /**
     * 安全转换为 Long。非数字、非 Number 均返回 null。
     */
    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            log.debug("事件日志 time 字段无法解析为 Long: {}", v);
            return null;
        }
    }
}
