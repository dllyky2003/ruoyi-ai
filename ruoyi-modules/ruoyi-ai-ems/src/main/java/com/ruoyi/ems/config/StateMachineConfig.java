package com.ruoyi.ems.config;

import lombok.Data;

import java.util.Map;

/**
 * YAML 状态机配置根对象
 * 对应 YAML 文件的顶层结构
 */
@Data
public class StateMachineConfig {

    private String caseId;
    private String caseName;
    private String version;
    private String parent;
    private StateMachineDefinition stateMachine;

    /**
     * ★ 新增：状态中文描述映射
     * key: 状态编码（如 "INITIAL_SETUP"）
     * value: 中文描述（如 "初始设置阶段 - 需完成：开启监护仪、连接电极片、建立静脉通路"）
     */
    private Map<String, String> stateDescriptions;

    /**
     * 获取状态的中文描述
     * @param stateCode 状态编码
     * @return 中文描述，如果不存在则返回 null
     */
    public String getStateDescription(String stateCode) {
        if (stateDescriptions != null && stateDescriptions.containsKey(stateCode)) {
            return stateDescriptions.get(stateCode);
        }
        return null;
    }

    /**
     * 获取状态的中文描述，如果不存在则返回状态编码本身
     */
    public String getStateDescriptionOrDefault(String stateCode) {
        String desc = getStateDescription(stateCode);
        return desc != null ? desc : stateCode;
    }
}
