package com.ruoyi.ems.domain.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 临床操作事件（LLM结构化抽取结果）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClinicalAction {
    /**
     * 操作顺序序号
     */
    private Integer seq;

    /**
     * 相对开始时间（秒）
     */
    private Integer timestamp;

    /**
     * 执行人（第一期固定为 "TRAINEE"）
     */
    private String actor;

    /**
     * 操作类型：ASSESS, MONITOR, INTERVENTION, SAFETY, COMMUNICATION
     */
    private String actionType;

    /**
     * 具体操作，如 heart_rate, adenosine, synchronized_cardioversion
     */
    private String detail;

    /**
     * 操作数值，如 160, 6mg, synchronized
     */
    private String value;

    /**
     * 操作结果，如 effective, ineffective
     */
    private String result;

    /**
     * 原始语音片段（用于溯源）
     */
    private String sourceText;
}
