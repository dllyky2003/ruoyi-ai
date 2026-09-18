package com.ruoyi.ems.engine.multiagent.dto;

import com.ruoyi.ems.domain.dto.TeamPerformance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 智能体统一输入对象
 *
 * 用于将学员操作数据、状态机轨迹、场景配置等信息传递给各个智能体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInput {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 学员操作表现（结构化事件流 + 临床判断）
     */
    private TeamPerformance performance;

    /**
     * 状态机轨迹（状态变更历史）
     */
    private List<Map<String, Object>> traces;

    /**
     * 场景配置（含预期路径、评分规则、提示等）
     */
    private Map<String, Object> scenarioConfig;

    /**
     * 当前状态机状态名称
     */
    private String currentState;

    /**
     * 学员姓名
     */
    private String traineeName;

    /**
     * 考核模式：TEACH / EXAM
     */
    private String mode;

    /**
     * 额外扩展数据（用于智能体间传递临时数据）
     */
    private Map<String, Object> extraData;

    /**
     * 获取操作事件列表（便捷方法）
     */
    public List<?> getActions() {
        return performance != null ? performance.getActions() : null;
    }

    /**
     * 获取临床判断（便捷方法）
     */
    public Object getJudgments() {
        return performance != null ? performance.getJudgments() : null;
    }

    /**
     * 检查是否处于教学模式
     */
    public boolean isTeachMode() {
        return "TEACH".equalsIgnoreCase(mode);
    }

    /**
     * 检查是否处于考核模式
     */
    public boolean isExamMode() {
        return "EXAM".equalsIgnoreCase(mode);
    }

    /**
     * 获取扩展数据中的指定值
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtraData(String key, Class<T> type) {
        if (extraData == null) {
            return null;
        }
        Object value = extraData.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * 设置扩展数据
     */
    public void putExtraData(String key, Object value) {
        if (extraData == null) {
            extraData = new java.util.HashMap<>();
        }
        extraData.put(key, value);
    }

    @Override
    public String toString() {
        return String.format("AgentInput{sessionId='%s', mode='%s', actions=%d, traces=%d}",
            sessionId,
            mode,
            performance != null && performance.getActions() != null ? performance.getActions().size() : 0,
            traces != null ? traces.size() : 0
        );
    }
}
