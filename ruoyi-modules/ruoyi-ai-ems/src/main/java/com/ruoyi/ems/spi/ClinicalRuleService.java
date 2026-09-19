package com.ruoyi.ems.spi;


import com.ruoyi.ems.spi.dto.ClinicalRuleContext;
import com.ruoyi.ems.spi.dto.ValidationResult;

/**
 * 临床规则校验 SPI。
 *
 * <p>由 {@code ruoyi-acls-tutor} 实现。tutor 未部署时由 No-Op 默认实现兜底，
 * 返回 {@link ValidationResult#allowWithoutTutor()}，不改变原状态机行为。</p>
 */
public interface ClinicalRuleService {

    ValidationResult validate(ClinicalRuleContext context);
}
