package org.ruoyi.system.domain.vo;

import lombok.Data;

@Data
public class BaseInfo {
    private String caseName;
    private String patientGender;
    private int patientAge;
    private String mainFlowType;
    private String difficultyLevel;
    private int estimatedDurationMinutes;
    private String learningObjectives;
    private String modelName;
    /**
     * 生成供AI使用的结构化信息描述
     * @return 包含所有字段及其含义的字符串
     */
    public String toAiPromptDescription() {
        return String.format(
            "病例基本信息如下：\n" +
                "- 病例名称（caseName）：%s\n" +
                "- 患者性别（patientGender）：%s\n" +
                "- 患者年龄（patientAge）：%d 岁\n" +
                "- 主流程类型（mainFlowType）：%s\n" +
                "- 难度等级（difficultyLevel）：%s\n" +
                "- 预计持续时间（estimatedDurationMinutes）：%d 分钟\n" +
                "- 学习目标（learningObjectives）：%s\n",
            caseName,
            patientGender,
            patientAge,
            mainFlowType,
            difficultyLevel,
            estimatedDurationMinutes,
            learningObjectives,
            modelName
        );
    }
}
