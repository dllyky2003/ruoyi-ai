package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.OutcomeBranchRequest;
import com.ruoyi.ems.spi.dto.OutcomeBranchResult;


/**
 * 病例结局分支执行 SPI。
 *
 * <p>tutor 侧在 beat 命中、proposed_effect 为 trigger_case_outcome_branch 时调用，
 * 请求 ems 写临床变量并触发状态机重评估。</p>
 *
 * <p>服务器唯一临床权威：最终变量写入由 ems 侧完成。</p>
 */
public interface OutcomeBranchExecutor {

    OutcomeBranchResult execute(OutcomeBranchRequest request);
}
