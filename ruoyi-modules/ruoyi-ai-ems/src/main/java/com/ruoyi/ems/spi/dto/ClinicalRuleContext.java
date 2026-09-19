package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 临床规则校验入参。
 *
 * <p>tutor 侧自行从 context 读取所需字段；ems 只负责传入快照。</p>
 */
@Data
public class ClinicalRuleContext {

    private String sessionId;
    private String caseType;
    private String currentState;
    private long stateRevision;
    private String intentCode;
    private Map<String, Object> parameters = new HashMap<>();
    private Map<String, Object> variables = new HashMap<>();
}
