package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.OutcomeBranchRequest;
import com.ruoyi.ems.spi.dto.OutcomeBranchResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoOpOutcomeBranchExecutor implements OutcomeBranchExecutor {

    @Override
    public OutcomeBranchResult execute(OutcomeBranchRequest request) {
        OutcomeBranchResult r = new OutcomeBranchResult();
        r.setSuccess(true);
        r.setSkipped(true);
        log.debug("[ACLS SPI] OutcomeBranchExecutor No-Op: session={}, branchId={}",
            request != null ? request.getSessionId() : null,
            request != null ? request.getBranchId() : null);
        return r;
    }
}
