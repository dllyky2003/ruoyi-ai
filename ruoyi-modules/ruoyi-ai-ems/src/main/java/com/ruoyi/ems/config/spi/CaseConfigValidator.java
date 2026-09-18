package com.ruoyi.ems.config.spi;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;

/**
 * 案例配置校验器（SPI 接口）
 * 由各模块实现，在 CaseConfigLoader 完成基础加载后被调用
 */
public interface CaseConfigValidator {

    /**
     * 校验指定案例的配置
     * @param caseType 案例类型
     * @param stateMachineConfig 已加载的状态机配置（包含意图、状态等）
     * @throws RuntimeException 校验失败时抛出异常
     */
    void validate(String caseType, ResolvedCaseTypeConfig stateMachineConfig);
}
