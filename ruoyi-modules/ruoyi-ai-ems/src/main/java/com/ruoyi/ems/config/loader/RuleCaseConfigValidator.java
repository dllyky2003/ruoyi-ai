package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.spi.CaseConfigValidator;
import com.ruoyi.ems.exception.ConfigurationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleCaseConfigValidator implements CaseConfigValidator {

    private final RuleConfigLoader ruleConfigLoader;
    private final YamlStateMachineLoader stateMachineLoader;
    private final CaseIntentDefinitionsLoader intentLoader;

    @Override
    public void validate(String caseId, ResolvedCaseTypeConfig config) {
        try {
            ruleConfigLoader.validateCaseRules(caseId, stateMachineLoader, intentLoader);
            log.debug("规则校验通过: caseId={}", caseId);
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("案例 " + caseId + " 规则校验失败: " + e.getMessage(), e);
        }
    }
}
