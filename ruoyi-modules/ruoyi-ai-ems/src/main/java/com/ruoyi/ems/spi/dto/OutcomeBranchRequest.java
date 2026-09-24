package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结局分支执行请求。
 */
@Data
public class OutcomeBranchRequest {

    /** 会话 id（= attemptId） */
    private String sessionId;

    /** 案例类型 code */
    private String caseType;

    /** 结局分支 id */
    private String branchId;

    /** 触发此请求的 beat id（审计用） */
    private String sourceBeatId;

    /** 已映射到 ems 变量名的键值对（如 phys.rhythm=svt） */
    private Map<String, Object> clinicalStateValues = new LinkedHashMap<>();
}
