package com.ruoyi.ems.exception;


import lombok.NoArgsConstructor;

/**
 * 变量访问拒绝异常
 *
 * 当写入者无权限修改指定变量时抛出。
 *
 * 写入者类型：
 * - SIMULATOR: 模拟器（写入 L1 生理事实）
 * - CLASSIFIER: NLU 分类器（写入 L2 学员陈述）
 * - STATE_MACHINE: 状态机（写入 L3 流程标记）
 * - TIMER: 计时器（写入 L4 系统元数据）
 * - SYSTEM: 系统（写入 L4 系统元数据）
 *
 * 使用场景：
 * - ContextManager.setVar() 执行权限检查时
 * - SlotToVariableMapper 映射槽位时
 * - 任何尝试修改变量但权限不足的场景
 *
 * @author RuoYi EMS
 * @version 1.0
 */
@NoArgsConstructor
public class VariableAccessDeniedException extends RuntimeException {

    public VariableAccessDeniedException(String message) {
        super(message);
    }

    public VariableAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

    public VariableAccessDeniedException(Throwable cause) {
        super(cause);
    }

    /**
     * 创建带变量名和写入者信息的异常
     */
    public static VariableAccessDeniedException of(String varName, String writer) {
        return new VariableAccessDeniedException(
            String.format("变量 '%s' 不允许 '%s' 写入", varName, writer)
        );
    }

    /**
     * 创建带变量名、写入者和案例类型信息的异常
     */
    public static VariableAccessDeniedException of(String caseType, String varName, String writer) {
        return new VariableAccessDeniedException(
            String.format("案例 '%s' 中变量 '%s' 不允许 '%s' 写入", caseType, varName, writer)
        );
    }
}
