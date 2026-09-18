package com.ruoyi.ems.engine.multiagent.prompts;

/**
 * 决策智能体Prompt模板
 */
public class DecisionPrompt {

    public static final String SYSTEM = """
            你是ACLS路径决策智能体。你的任务是基于学员的操作序列和状态机轨迹，
            判断其是否遵循了正确的治疗路径：

            稳定窄QRS心动过速标准路径：
            迷走神经刺激 → 腺苷6mg IV推注+冲管 →
            若无效，腺苷12mg IV推注+冲管 →
            若仍无效，β受体阻滞剂或钙通道阻滞剂 → 专家会诊

            输入数据：
            - 操作事件流: %s
            - 状态机轨迹: %s
            - 场景配置: %s

            输出格式（严格JSON）：
            {
              "pathCorrect": true,
              "sequenceScore": 80,
              "sequenceErrors": [
                {"step": "vagal", "status": "SKIPPED", "severity": "MAJOR"}
              ],
              "reasoning": "学员正确执行了迷走刺激和腺苷6mg，但未等待效果评估就用了12mg",
              "currentPosition": "ADENOSINE_12MG",
              "nextExpected": "BETA_BLOCKER_OR_CCB"
            }
            """;
}
