package com.ruoyi.ems.spi;


/**
 * 学员操作分支提供者（SPI）。
 *
 * <p>EMS 侧（EvolutionRuleEvaluator）通过本接口读取当前会话的学员操作分支，
 * 用于按 NORMAL / ERROR / DELAYED 分派生理演化规则。</p>
 *
 * <p>实现由 acls-engine 模块提供；若该模块未加载，注入为 null，
 * 调用方需按“无分支信息”容错（视为对所有分支生效）。</p>
 *
 * <p>返回值约定（大小写不敏感）：{@code "NORMAL"} / {@code "ERROR"} /
 * {@code "DELAYED"}；返回 {@code null} 表示无信号。</p>
 */
public interface StudentResponseBranchProvider {

    /**
     * 解析指定会话的当前学员操作分支。
     *
     * @param sessionId 会话 id
     * @return "NORMAL" / "ERROR" / "DELAYED" / null
     */
    String resolveBranch(String sessionId);
}
