package com.ruoyi.ems.spi;

import com.ruoyi.ems.spi.dto.ClinicalRuleContext;
import com.ruoyi.ems.spi.dto.ValidationResult;

public class NoOpClinicalRuleService implements ClinicalRuleService {

    @Override
    public ValidationResult validate(ClinicalRuleContext context) {
        return ValidationResult.allowWithoutTutor();
    }
}
