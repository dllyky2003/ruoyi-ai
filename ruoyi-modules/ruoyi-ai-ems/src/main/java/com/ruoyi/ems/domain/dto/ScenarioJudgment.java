package com.ruoyi.ems.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 学员临床判断结果（LLM从语音中抽取的结论）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioJudgment {
    /**
     * 心率是否 > 150 bpm
     */
    private Boolean heartRateGt150;

    /**
     * 是否存在脉搏
     */
    private Boolean pulsePresent;

    /**
     * 不稳定体征列表：hypotension, altered_mental, chest_pain, shock, acute_heart_failure
     */
    private List<String> unstableSigns;

    /**
     * QRS宽度：NARROW / WIDE
     */
    private String qrsWidth;

    /**
     * 心律规整性：REGULAR / IRREGULAR
     */
    private String rhythmRegularity;

    /**
     * 是否识别为 WPW 综合征
     */
    private Boolean wpwSyndrome;


    private Boolean stable;
}
