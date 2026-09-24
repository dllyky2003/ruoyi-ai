package com.ruoyi.ems.spi.dto;

import lombok.Data;

/**
 * 结局分支执行结果。
 */
@Data
public class OutcomeBranchResult {

    private boolean success;

    /** 幂等跳过标记（同一 session 同一 branch 重复调用） */
    private boolean skipped;

    private String errorMessage;

    private int writtenVarCount;

    private String stateBefore;

    private String stateAfter;
}
