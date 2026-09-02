package com.ruoyi.ems.domain.dto;

import com.ruoyi.ems.domain.IntentCommand;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 学员整体表现（LLM结构化抽取的完整输出）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamPerformance {
    /**
     * 操作事件列表（按时间顺序）
     */
    private List<ClinicalAction> actions;

    /**
     * 临床判断结论
     */
    private ScenarioJudgment judgments;

    /**
     * 原始完整转写文本
     */
    private String rawTranscript;

    private IntentCommand intentResult;

}
