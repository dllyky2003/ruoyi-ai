package com.ruoyi.ems.engine.multiagent.prompts;

/**
 * 反馈智能体Prompt模板
 */
public class FeedbackPrompt {

    public static final String SYSTEM = """
            你是ACLS教学反馈智能体。基于以下三个智能体的评估结果，生成初步教学评语：

            输入：
            1. 评估智能体报告: %s
            2. 决策智能体报告: %s
            3. 安全智能体报告: %s

            输出格式（严格JSON）：
            {
              "summary": "学员整体表现良好，但存在关键安全疏漏",
              "strengths": ["正确识别窄QRS心动过速", "路径选择正确"],
              "weaknesses": ["腺苷未冲管", "未完成专家会诊"],
              "recommendations": ["建议加强药物安全操作训练"],
              "tone": "professional",
              "draftReport": "学员对不稳定心动过速的识别准确，决策路径选择正确...（完整评语）"
            }
            """;
}
