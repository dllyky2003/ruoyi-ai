package com.ruoyi.ems.config.loader;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.context.VariableMetaManager;
import com.ruoyi.ems.exception.ConfigurationException;
import com.ruoyi.ems.orchestrator.model.OrchestrationConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 统一案例配置加载入口
 * 负责按顺序加载变量、意图、状态机、动作链，并执行交叉校验
 */
@Slf4j
@Component
public class CaseConfigLoader {

    private final VariableMetaManager variableMetaManager;
    private final CaseIntentDefinitionsLoader intentLoader;
    private final YamlStateMachineLoader stateMachineLoader;
    private final ActionChainConfigLoader actionChainConfigLoader;

    // ==================== 构造器注入（消除字段注入警告） ====================
    public CaseConfigLoader(VariableMetaManager variableMetaManager,
                            CaseIntentDefinitionsLoader intentLoader,
                            YamlStateMachineLoader stateMachineLoader,
                            ActionChainConfigLoader actionChainConfigLoader) {
        this.variableMetaManager = variableMetaManager;
        this.intentLoader = intentLoader;
        this.stateMachineLoader = stateMachineLoader;
        this.actionChainConfigLoader = actionChainConfigLoader;
    }

    // ==================== 启动加载 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("🚀 开始按顺序加载所有案例配置...");
            // 1. 加载变量
            variableMetaManager.load();
            // 2. 加载意图
            intentLoader.init();
            // 3. 加载状态机
            stateMachineLoader.loadAllConfigs();
            // 4. 加载动作链配置（依赖步骤模板库）
            loadActionChains();
            // 5. 校验所有配置
            validateAllConfigs();
            log.info("✅ 所有案例配置加载并校验通过");
        } catch (Exception e) {
            log.error("❌ 案例配置加载失败，应用启动终止", e);
            throw new RuntimeException("案例配置加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载所有案例的动作链配置（内部捕获异常，不抛出）
     */
    private void loadActionChains() {
//        Map<String, ResolvedCaseTypeConfig> configs = stateMachineLoader.getConfigs();
//        for (String caseType : configs.keySet()) {
//            try {
//                actionChainConfigLoader.loadCaseOrchestration(caseType);
//            } catch (Exception e) {
//                log.warn("⚠️ 案例 {} 动作链配置加载失败: {}", caseType, e.getMessage());
//                // 不阻断启动，允许传统模式运行
//            }
//        }
//        log.info("✅ 动作链配置加载完成");
    }

    // ==================== 全量重载（保留 throws 以支持外部调用） ====================

    @SuppressWarnings("unused") // 供管理接口或热重载使用
    public synchronized void reloadAll() throws Exception {
        log.info("🔄 全量热重载开始...");
        variableMetaManager.reload();
        intentLoader.reloadAll();
        stateMachineLoader.loadAllConfigs();
        actionChainConfigLoader.reloadAll();
        validateAllConfigs();
        log.info("✅ 全量热重载完成");
    }

    // ==================== 单案例重载 ====================

    @SuppressWarnings("unused") // 供管理接口或热重载使用
    public synchronized void reloadCase(String caseId) throws Exception {
        log.info("🔄 热重载案例: {}", caseId);
        variableMetaManager.reloadCase(caseId);
        intentLoader.loadIntentsForCase(caseId);
        stateMachineLoader.reloadCase(caseId);
        actionChainConfigLoader.reloadCase(caseId);
        validateConfig(caseId);
        log.info("✅ 案例 {} 热重载完成", caseId);
    }

    // ==================== 校验方法 ====================

    private void validateAllConfigs() {
        Map<String, ResolvedCaseTypeConfig> configs = stateMachineLoader.getConfigs();
        for (Map.Entry<String, ResolvedCaseTypeConfig> entry : configs.entrySet()) {
            validateConfig(entry.getKey(), entry.getValue());
        }
    }

    private void validateConfig(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) {
            throw new ConfigurationException("案例 " + caseId + " 的状态机配置不存在");
        }
        validateConfig(caseId, config);
    }

    private void validateConfig(String caseId, ResolvedCaseTypeConfig config) {
        // 变量校验
        variableMetaManager.validateStateMachine(caseId, config);

        // 意图校验
        for (TransitionRule rule : config.getTransitions()) {
            String intentCode = rule.getIntentCode();
            if (intentCode != null && !intentCode.startsWith("SYS_") && !intentLoader.isValidIntentCode(intentCode)) {
                throw new ConfigurationException(
                    String.format("状态机 [%s] transition 引用的意图编码 '%s' 不存在", caseId, intentCode)
                );
            }
        }

        Map<String, String> mapping = config.getIntentToStateMapping();
        if (mapping != null) {
            for (String code : mapping.keySet()) {
                if (!intentLoader.isValidIntentCode(code)) {
                    throw new ConfigurationException(
                        String.format("状态机 [%s] intentToStateMapping 引用的意图编码 '%s' 不存在", caseId, code)
                    );
                }
            }
        }

        Set<String> globalEvents = config.getGlobalEventIntents();
        if (globalEvents != null) {
            for (String code : globalEvents) {
                if (!intentLoader.isValidIntentCode(code)) {
                    throw new ConfigurationException(
                        String.format("状态机 [%s] globalEventIntents 引用的意图编码 '%s' 不存在", caseId, code)
                    );
                }
            }
        }

        // ★ 校验动作链配置
        OrchestrationConfig orchestrationConfig = actionChainConfigLoader.getOrchestrationConfig(caseId);
        if (orchestrationConfig != null && orchestrationConfig.getActionChains() != null) {
            for (Map.Entry<String, OrchestrationConfig.ChainConfig> entry : orchestrationConfig.getActionChains().entrySet()) {
                String chainId = entry.getKey();
                OrchestrationConfig.ChainConfig chain = entry.getValue();
                // 使用 chain 变量进行校验（消除"未使用"警告）
                if (chain.getSteps() != null) {
                    log.debug("✅ 动作链 {} 包含 {} 个步骤", chainId, chain.getSteps().size());
                }
                // 可进一步校验步骤引用的原子动作、职责等
                // 校验前置条件引用的步骤是否存在
                if (chain.getPreconditions() != null) {
                    for (Map.Entry<String, java.util.List<String>> preconditionEntry : chain.getPreconditions().entrySet()) {
                        String stepId = preconditionEntry.getKey();
                        for (String dep : preconditionEntry.getValue()) {
                            if (chain.getSteps() != null && !chain.getSteps().contains(dep)) {
                                log.warn("动作链 {} 中步骤 {} 依赖的 {} 不在步骤列表中", chainId, stepId, dep);
                            }
                        }
                    }
                }
            }
        }

        log.debug("✅ 案例 {} 配置校验通过", caseId);
    }

    /**
     * 获取案例的动作链配置（供外部使用）
     */
    @SuppressWarnings("unused") // 可能被其他组件调用
    public OrchestrationConfig getOrchestrationConfig(String caseType) {
        return actionChainConfigLoader.getOrchestrationConfig(caseType);
    }
}
