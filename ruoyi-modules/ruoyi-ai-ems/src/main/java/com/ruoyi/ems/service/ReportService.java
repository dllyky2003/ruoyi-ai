package com.ruoyi.ems.service;

import com.ruoyi.ems.domain.entity.EmsEvaluation;
import com.ruoyi.ems.domain.entity.EmsSession;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReportService {

    private final ChatModel chatModel;
    private final EvaluationService evaluationService;
    private final SessionService sessionService;

    public ReportService(@Qualifier("throttledReportChatModel") ChatModel chatModel,
                         EvaluationService evaluationService,
                         SessionService sessionService) {
        this.chatModel = chatModel;
        this.evaluationService = evaluationService;
        this.sessionService = sessionService;
    }

    /**
     * 生成详细报告
     */
    public String generateDetailedReport(String sessionId) {
        EmsEvaluation evaluation = evaluationService.getEvaluationBySessionId(sessionId);
        if (evaluation == null) {
            return "评估结果不存在，请先完成评估";
        }

        EmsSession session = sessionService.getSessionById(sessionId);
        if (session == null) {
            return "会话不存在";
        }

        String userPrompt = """
                你是一名资深ACLS导师。基于以下学员考核数据，生成一份详细的教学评价报告。

                要求：
                1. 语气专业、客观、有建设性
                2. 先肯定做得好的地方
                3. 指出不足之处并给出具体改进建议
                4. 推荐后续训练场景

                考核数据：
                - 学员: %s
                - 场景: %s
                - 总分: %d
                - 是否通过: %s
                - 各维度得分: %s
                - 错误列表: %s
                - 警告: %s
                - 原始评语: %s

                请生成一份完整的教学评价报告（300-500字）。
                """.formatted(
            session.getTraineeName() != null ? session.getTraineeName() : "未命名学员",
            session.getScenarioId(),
            evaluation.getTotalScore(),
            evaluation.getIsPass() ? "通过" : "不通过",
            evaluation.getDimensionScores(),
            evaluation.getCriticalErrors(),
            evaluation.getWarnings(),
            evaluation.getReportText() != null ? evaluation.getReportText() : "无"
        );

        try {
            List<ChatMessage> messages = List.of(
                SystemMessage.from("你是一位专业的ACLS导师，善于撰写客观、建设性的教学评价报告。"),
                UserMessage.from(userPrompt)
            );

            ChatResponse response = chatModel.chat(messages);
            String detailedReport = response.aiMessage().text();

            return detailedReport != null ? detailedReport : evaluation.getReportText();

        } catch (Exception e) {
            log.error("生成详细报告失败", e);
            return evaluation.getReportText() != null ?
                evaluation.getReportText() :
                "报告生成失败，请查看基础评分数据";
        }
    }

    /**
     * 生成对比报告（多学员对比）
     */
    public String generateComparisonReport(String... sessionIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("【学员成绩对比】\n\n");

        for (String sessionId : sessionIds) {
            EmsEvaluation eval = evaluationService.getEvaluationBySessionId(sessionId);
            EmsSession session = sessionService.getSessionById(sessionId);
            if (eval != null && session != null) {
                sb.append("学员: ").append(session.getTraineeName())
                    .append(" | 总分: ").append(eval.getTotalScore())
                    .append(" | 通过: ").append(eval.getIsPass() ? "✅" : "❌")
                    .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 导出报告为Markdown
     */
    public String exportMarkdown(String sessionId) {
        String detailedReport = generateDetailedReport(sessionId);
        EmsEvaluation eval = evaluationService.getEvaluationBySessionId(sessionId);
        EmsSession session = sessionService.getSessionById(sessionId);

        String md = "# ACLS考核评价报告\n\n" +
            "## 基本信息\n\n" +
            "| 项目 | 内容 |\n" +
            "|------|------|\n" +
            "| 会话ID | " + sessionId + " |\n" +
            "| 学员 | " + (session != null ? session.getTraineeName() : "未知") + " |\n" +
            "| 总分 | " + (eval != null ? eval.getTotalScore() : "N/A") + " |\n" +
            "| 结果 | " + (eval != null && eval.getIsPass() ? "通过" : "不通过") + " |\n" +
            "| 日期 | " + (eval != null ? eval.getCreatedAt() : "N/A") + " |\n\n" +
            "## 详细评语\n\n" +
            detailedReport;

        return md;
    }
}
