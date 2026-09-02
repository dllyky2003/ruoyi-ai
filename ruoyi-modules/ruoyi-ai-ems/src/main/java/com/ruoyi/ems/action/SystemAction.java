package com.ruoyi.ems.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();
    private String target;
    private String description;
    @Builder.Default
    private Integer priority = 0;
    @Builder.Default
    private boolean sync = false;
    @Builder.Default
    private Long timeoutMs = 5000L;
    private String source;

    // ==================== 便捷构造方法 ====================

    public static SystemAction drug(String drugName, Object dose, String route) {
        Map<String, Object> params = new HashMap<>();
        params.put("drug", drugName);
        params.put("dose", dose);
        params.put("route", route != null ? route : "IV");
        return SystemAction.builder()
            .type(ActionType.SIMULATE_DRUG.getCode())
            .params(params)
            .description(String.format("给药: %s %s %s", drugName, dose, route))
            .build();
    }

    public static SystemAction cardioversion(int energy, String mode) {
        Map<String, Object> params = new HashMap<>();
        params.put("energy", energy);
        params.put("mode", mode != null ? mode : "synchronized");
        return SystemAction.builder()
            .type(ActionType.SIMULATE_CARDIOVERSION.getCode())
            .params(params)
            .description(String.format("同步电复律: %dJ %s", energy, mode))
            .build();
    }

    public static SystemAction defibrillation(int energy) {
        Map<String, Object> params = new HashMap<>();
        params.put("energy", energy);
        return SystemAction.builder()
            .type(ActionType.SIMULATE_DEFIBRILLATION.getCode())
            .params(params)
            .description(String.format("非同步除颤: %dJ", energy))
            .build();
    }

    public static SystemAction log(String message, Map<String, Object> data) {
        Map<String, Object> params = new HashMap<>();
        params.put("message", message);
        params.put("data", data != null ? data : new HashMap<>());
        return SystemAction.builder()
            .type(ActionType.LOG_EVENT.getCode())
            .params(params)
            .description("日志记录: " + message)
            .build();
    }

    public static SystemAction updateState(String state, Map<String, Object> additional) {
        Map<String, Object> params = new HashMap<>();
        params.put("state", state);
        if (additional != null) {
            params.putAll(additional);
        }
        return SystemAction.builder()
            .type(ActionType.UPDATE_STATE.getCode())
            .params(params)
            .description("状态更新: " + state)
            .build();
    }

    // ==================== 实例方法 ====================

    public SystemAction param(String key, Object value) {
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        this.params.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, Class<T> type) {
        Object value = params != null ? params.get(key) : null;
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return null;
    }

    public String getParamAsString(String key) {
        return getParam(key, String.class);
    }

    public Integer getParamAsInteger(String key) {
        return getParam(key, Integer.class);
    }

    public Boolean getParamAsBoolean(String key) {
        return getParam(key, Boolean.class);
    }

    public boolean isValid() {
        return type != null && !type.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("SystemAction{type='%s', params=%s, target='%s'}", type, params, target);
    }
}
