package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态转移上下文（前后钩子共用）。
 */
@Data
public class TransitionContext {

    private String sessionId;
    private String caseType;
    private String fromState;
    private String toState;
    private String intentCode;
    private long stateRevisionBefore;
    private long stateRevisionAfter;
    private Map<String, Object> variables = new HashMap<>();
}
