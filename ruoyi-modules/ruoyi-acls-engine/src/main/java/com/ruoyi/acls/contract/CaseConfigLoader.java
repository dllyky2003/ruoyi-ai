package com.ruoyi.acls.contract;

import com.ruoyi.acls.cognitive.ReflexRuleLoader;
import com.ruoyi.acls.contract.models.CaseContract;
import com.ruoyi.acls.contract.models.ContractDiagnostics;
import com.ruoyi.acls.guard.GuardRailLoader;
import com.ruoyi.acls.router.SemanticRoutingLoader;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.acls.skill.registery.YamlSkillLoader;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.*;
import com.ruoyi.ems.config.spi.CaseConfigValidator;
import com.ruoyi.ems.domain.DrugEnum;
import com.ruoyi.ems.domain.Drugs;
import com.ruoyi.ems.domain.PhysiologyConfig;
import com.ruoyi.ems.exception.ConfigurationException;
import com.ruoyi.ems.orchestrator.service.AtomicActionRegistry;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;                       // ★ 新增
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CaseConfigLoader {

    // ==================== 原有 loader ====================
    private final VariableMetaLoader variableMetaManager;
    private final CaseIntentDefinitionsLoader intentLoader;
    private final YamlStateMachineLoader stateMachineLoader;
    private final ObjectProvider<CaseConfigValidator> validators;
    private final RuleConfigLoader ruleConfigLoader;
    private final PhysiologyConfigLoader physiologyConfigLoader;

    // ==================== 新增 loader ====================
    private final DerivationRuleLoader derivationRuleLoader;
    private final ExceptionRuleLoader exceptionRuleLoader;          // ★ 新增
    private final SemanticRoutingLoader semanticRoutingLoader;
    private final GuardRailLoader guardRailLoader;
    private final ReflexRuleLoader reflexRuleLoader;
    private final YamlSkillLoader yamlSkillLoader;
    private final IntentDictLoader intentDictLoader;
    private final AtomicActionRegistry atomicActionRegistry;
    private final SkillRegistry skillRegistry;

    // ★ Spring 侧 complex 技能（由 ComplexSkillBeans 注册）
    // 依赖此 List 会强制 Spring 先创建所有 EmsSkill Bean。
    private final List<EmsSkill> springSkills;

    // ==================== 契约构建器与注册表 ====================
    private final CaseContractBuilder contractBuilder;
    private final CaseContractRegistry contractRegistry;

    // ====================================================================
    // 构造函数
    // ====================================================================
    public CaseConfigLoader(VariableMetaLoader variableMetaManager,
                            CaseIntentDefinitionsLoader intentLoader,
                            YamlStateMachineLoader stateMachineLoader,
                            ObjectProvider<CaseConfigValidator> validators,
                            RuleConfigLoader ruleConfigLoader,
                            PhysiologyConfigLoader physiologyConfigLoader,
                            DerivationRuleLoader derivationRuleLoader,
                            ExceptionRuleLoader exceptionRuleLoader,      // ★ 新增
                            SemanticRoutingLoader semanticRoutingLoader,
                            YamlSkillLoader yamlSkillLoader,
                            GuardRailLoader guardRailLoader,
                            ReflexRuleLoader reflexRuleLoader,
                            IntentDictLoader intentDictLoader,
                            AtomicActionRegistry atomicActionRegistry,
                            SkillRegistry skillRegistry,
                            List<EmsSkill> springSkills,
                            CaseContractBuilder contractBuilder,
                            CaseContractRegistry contractRegistry) {
        this.variableMetaManager = variableMetaManager;
        this.intentLoader = intentLoader;
        this.stateMachineLoader = stateMachineLoader;
        this.validators = validators;
        this.ruleConfigLoader = ruleConfigLoader;
        this.physiologyConfigLoader = physiologyConfigLoader;
        this.derivationRuleLoader = derivationRuleLoader;
        this.exceptionRuleLoader = exceptionRuleLoader;
        this.semanticRoutingLoader = semanticRoutingLoader;
        this.guardRailLoader = guardRailLoader;
        this.reflexRuleLoader = reflexRuleLoader;
        this.yamlSkillLoader = yamlSkillLoader;
        this.intentDictLoader = intentDictLoader;
        this.atomicActionRegistry = atomicActionRegistry;
        this.skillRegistry = skillRegistry;
        this.springSkills = springSkills;
        this.contractBuilder = contractBuilder;
        this.contractRegistry = contractRegistry;
    }

    // ====================================================================
    // 初始化
    // ====================================================================
    @PostConstruct
    public void init() {
        try {
            log.info("🚀 开始按顺序加载所有案例配置...");

            variableMetaManager.load();
            intentLoader.init();
            intentDictLoader.load();
            stateMachineLoader.init();
            derivationRuleLoader.loadAll();
            exceptionRuleLoader.loadAll();                                // ★ 新增

            yamlSkillLoader.loadSkills();

            // ★ Spring 侧 complex 技能兜底注册
            registerSpringSkills();

            atomicActionRegistry.load();
            semanticRoutingLoader.init();
            guardRailLoader.init();
            reflexRuleLoader.init();
            loadAllPhysiologyConfigs();
            ruleConfigLoader.loadAllRules();

            validateSkillCoverage();
            validateDrugEnums();

            buildAndRegisterAllContracts();
            contractRegistry.logAllDiagnostics();
            validateAllConfigs();

            log.info("✅ 所有案例配置加载并校验通过");

        } catch (Exception e) {
            log.error("❌ 案例配置加载失败，应用启动终止", e);
            throw new RuntimeException("案例配置加载失败: " + e.getMessage(), e);
        }
    }

    // ====================================================================
    // ★ Spring 侧技能兜底注册（含 MyBatis 代理过滤）
    // ====================================================================
    /**
     * 把 Spring 容器里所有真实的 {@link EmsSkill} Bean 注册到 SkillRegistry。
     *
     * <p><b>为什么需要过滤 JDK 动态代理？</b>
     * MyBatis 的 {@code @MapperScan} 把 {@code EmsSkill} 接口误当成 mapper 注册了。
     * 我们注入 {@code List<EmsSkill>} 时，Spring 会把 MyBatis 生成的
     * mapper 代理（JDK 动态代理）也当作 {@code EmsSkill} 类型注入进来。
     * 遍历到它时调用 {@code getKeystepId()} 会去查 SQL 映射 → 抛
     * {@code BindingException: Invalid bound statement}。
     *
     * <p>过滤方式：{@link Proxy#isProxyClass(Class)} 识别 JDK 动态代理；
     * 我们自己的 complex 技能都是真实类（{@code DrugPushSkill} 等），不是代理。
     *
     * <p><b>根因修复建议</b>：检查主类或配置类上的 {@code @MapperScan}
     * 路径，不要扫到 {@code com.ruoyi.acls.skill} 或更宽的包。
     */
    private void registerSpringSkills() {
        if (springSkills == null || springSkills.isEmpty()) {
            log.warn("⚠️ 未发现任何 Spring 侧 EmsSkill Bean（ComplexSkillBeans 可能未生效）");
            return;
        }
        int registered = 0;
        int skipped = 0;
        for (EmsSkill skill : springSkills) {
            // 1) 过滤 JDK 动态代理（MyBatis mapper 误注册）
            if (Proxy.isProxyClass(skill.getClass())) {
                log.debug("跳过 JDK 动态代理（可能是 MyBatis 误注册的 EmsSkill mapper）: {}",
                    skill.getClass().getName());
                skipped++;
                continue;
            }
            // 2) 兜底 try/catch：任何异常都跳过，不阻断启动
            String id;
            try {
                id = skill.getKeystepId();
            } catch (Exception e) {
                log.warn("⚠️ 获取 keystepId 失败，跳过技能 {}: {}",
                    skill.getClass().getName(), e.getMessage());
                skipped++;
                continue;
            }
            if (id == null || id.isBlank()) {
                log.warn("⚠️ Spring 技能 {} 的 keystepId 为空，跳过",
                    skill.getClass().getSimpleName());
                skipped++;
                continue;
            }
            if (!skillRegistry.exists(id)) {
                skillRegistry.register(skill);
                registered++;
                log.info("✅ 从 Spring Bean 补充注册技能: {} ({})",
                    id, skill.getClass().getSimpleName());
            }
        }
        log.info("📋 Spring 侧技能注册完成: 新增 {} 个，跳过 {} 个", registered, skipped);
    }

    // ====================================================================
    // 生理配置加载
    // ====================================================================
    private void loadAllPhysiologyConfigs() {
        Map<String, ResolvedCaseTypeConfig> configs = stateMachineLoader.getConfigs();
        for (String caseId : configs.keySet()) {
            if ("GLOBAL".equals(caseId)) continue;
            physiologyConfigLoader.loadCase(caseId);
        }
    }

    // ====================================================================
    // 技能覆盖率诊断
    // ====================================================================
    private void validateSkillCoverage() {
        Set<String> declaredInYaml = yamlSkillLoader.getAllSkillIds();
        Set<String> registered = skillRegistry.getAllSkillIds();

        Set<String> missingInRegistry = new LinkedHashSet<>(declaredInYaml);
        missingInRegistry.removeAll(registered);
        if (!missingInRegistry.isEmpty()) {
            log.error("❌ 以下技能在 YAML 声明但未注册到 SkillRegistry: {}",
                missingInRegistry);
        }

        Set<String> missingInYaml = new LinkedHashSet<>(registered);
        missingInYaml.removeAll(declaredInYaml);
        if (!missingInYaml.isEmpty()) {
            log.warn("⚠️ 以下技能已注册但 YAML 中无 IO 声明（契约构建时会缺失写入变量）: {}",
                missingInYaml);
        }

        log.info("📋 技能覆盖率检查完成: YAML {} 个, Registry {} 个",
            declaredInYaml.size(), registered.size());
    }

    // ====================================================================
    // 药物干预变量一致性校验
    // ====================================================================
    private void validateDrugEnums() {
        List<String> errors = Drugs.findBadInterventionVarKeys();
        int totalWithMap = Drugs.getInterventionDrugCodes().size();
        int totalDrugs = DrugEnum.values().length;

        if (!errors.isEmpty()) {
            throw new ConfigurationException(String.format(
                "药物干预变量一致性校验失败（共 %d 种药物，%d 种有干预 Map，%d 处不一致）：\n  %s",
                totalDrugs, totalWithMap, errors.size(),
                String.join("\n  ", errors)));
        }
        log.info("✅ 药物干预变量一致性校验通过 ({} 种药物中有 {} 种定义了干预 Map 变量)",
            totalDrugs, totalWithMap);
    }

    // ====================================================================
    // 契约构建与注册
    // ====================================================================
    private void buildAndRegisterAllContracts() {
        Map<String, CaseContract> contracts = contractBuilder.buildAll();
        contractRegistry.registerAll(contracts);
    }

    // ====================================================================
    // 重载：全量
    // ====================================================================
    public synchronized void reloadAll() throws Exception {
        log.info("🔄 全量热重载开始...");

        variableMetaManager.reload();
        intentLoader.reloadAll();
        intentDictLoader.reloadAll();
        stateMachineLoader.reloadAll();
        derivationRuleLoader.reloadAll();
        exceptionRuleLoader.reloadAll();                                 // ★ 新增
        yamlSkillLoader.reloadAll();
        atomicActionRegistry.reloadAll();
        semanticRoutingLoader.reloadAll();
        guardRailLoader.reloadAll();
        reflexRuleLoader.reloadAll();

        physiologyConfigLoader.evictAll();
        loadAllPhysiologyConfigs();
        ruleConfigLoader.reloadAll();

        validateSkillCoverage();
        validateDrugEnums();

        contractRegistry.clear();
        buildAndRegisterAllContracts();
        contractRegistry.logAllDiagnostics();

        validateAllConfigs();
        log.info("✅ 全量热重载完成");
    }

    // ====================================================================
    // 重载：单案例
    // ====================================================================
    public synchronized void reloadCase(String caseId) throws Exception {
        log.info("🔄 热重载案例: {}", caseId);

        variableMetaManager.reloadCase(caseId);
        intentLoader.loadIntentsForCase(caseId);
        stateMachineLoader.reloadCase(caseId);
        derivationRuleLoader.reloadCase(caseId);
        exceptionRuleLoader.reloadCase(caseId);                          // ★ 新增
        semanticRoutingLoader.reloadCase(caseId);
        guardRailLoader.reloadCase(caseId);
        reflexRuleLoader.reloadCase(caseId);

        physiologyConfigLoader.evictCache(caseId);
        physiologyConfigLoader.loadCase(caseId);
        ruleConfigLoader.reloadCase(caseId);

        validateDrugEnums();

        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config != null) {
            CaseContract newContract = contractBuilder.build(caseId);
            contractRegistry.replace(caseId, newContract);
        } else {
            log.warn("案例 {} 的状态机配置不存在，跳过契约重建", caseId);
        }

        validateConfig(caseId);
        log.info("✅ 案例 {} 热重载完成", caseId);
    }

    // ====================================================================
    // 校验：全部
    // ====================================================================
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
        log.debug("🔍 开始校验案例: {}", caseId);

        variableMetaManager.validateStateMachine(caseId, config);
        validateIntentReferences(caseId, config);
        validateContract(caseId);

        List<CaseConfigValidator> validatorList =
            validators.stream().collect(Collectors.toList());
        for (CaseConfigValidator validator : validatorList) {
            try {
                validator.validate(caseId, config);
                log.debug("  校验器 {} 通过", validator.getClass().getSimpleName());
            } catch (Exception e) {
                throw new ConfigurationException(
                    String.format("案例 [%s] 扩展校验失败 (validator: %s): %s",
                        caseId, validator.getClass().getSimpleName(), e.getMessage()), e);
            }
        }

        log.debug("✅ 案例 {} 配置校验通过", caseId);
    }

    private void validateContract(String caseId) {
        ContractDiagnostics diag = contractRegistry.getDiagnostics(caseId);
        if (diag == null) {
            log.warn("案例 {} 未找到契约，跳过契约校验", caseId);
            return;
        }

        if (!diag.getUndefinedDerivedVars().isEmpty()) {
            throw new ConfigurationException(String.format(
                "案例 [%s] 契约校验失败：以下派生变量被 Guard 引用但未定义：%s",
                caseId, diag.getUndefinedDerivedVars()));
        }

        if (!diag.getCircularDependencies().isEmpty()) {
            throw new ConfigurationException(String.format(
                "案例 [%s] 契约校验失败：派生变量存在循环依赖：%s",
                caseId, diag.getCircularDependencies()));
        }

        if (!diag.getDeadTransitions().isEmpty()) {
            log.warn("案例 {} 存在死转移（无意图可触发）：{}",
                caseId, diag.getDeadTransitions());
        }
    }

    // ====================================================================
    // 生理配置校验（保留原方法）
    // ====================================================================
    @SuppressWarnings("unused")
    private void validatePhysiology(String caseId, ResolvedCaseTypeConfig config) {
        PhysiologyConfig physConfig = physiologyConfigLoader.loadCase(caseId);
        if (physConfig == null) {
            log.error("案例 [{}] 的生理配置未加载", caseId);
            return;
        }

        Set<String> stateIds = collectAllStateIds(config);
        Set<String> definedStates = physConfig.getStatePhysiology() != null
            ? physConfig.getStatePhysiology().keySet()
            : Collections.emptySet();

        List<String> missingStates = new ArrayList<>();
        for (String stateId : stateIds) {
            if (!definedStates.contains(stateId)) {
                missingStates.add(stateId);
            }
        }
        if (!missingStates.isEmpty()) {
            throw new ConfigurationException(String.format(
                "案例 [%s] 的状态机中引用了以下状态，但生理配置（_physiology.yaml）中未定义基线：%s",
                caseId, String.join(", ", missingStates)));
        }

        log.debug("  生理校验通过：共 {} 个状态，全部在生理配置中有定义", stateIds.size());
    }

    private Set<String> collectAllStateIds(ResolvedCaseTypeConfig config) {
        Set<String> ids = new HashSet<>();

        String initial = config.getInitialState();
        if (initial != null && !initial.isEmpty()) {
            ids.add(initial);
        }

        List<TransitionRule> transitions = config.getTransitions();
        if (transitions != null) {
            for (TransitionRule rule : transitions) {
                String from = rule.getSource();
                String to = rule.getTarget();
                if (from != null && !from.isEmpty()) ids.add(from);
                if (to != null && !to.isEmpty()) ids.add(to);
            }
        }

        if (ids.isEmpty()) {
            log.warn("案例 {} 的状态机未提取到任何状态 ID，请检查状态机定义",
                config.getCode());
        }
        return ids;
    }

    // ====================================================================
    // 意图引用校验（保留原方法）
    // ====================================================================
    private void validateIntentReferences(String caseId, ResolvedCaseTypeConfig config) {
        List<TransitionRule> transitions = config.getTransitions();
        if (transitions != null) {
            for (TransitionRule rule : transitions) {
                String intentCode = rule.getIntentCode();
                if (intentCode != null
                    && !intentCode.startsWith("SYS_")
                    && !intentLoader.isValidIntentCode(intentCode)) {
                    log.error("状态机 [{}] transition 引用的意图编码 '{}' 不存在",
                        caseId, intentCode);
                }
            }
        }

        Map<String, String> mapping = config.getIntentToStateMapping();
        if (mapping != null) {
            for (String code : mapping.keySet()) {
                if (!intentLoader.isValidIntentCode(code)) {
                    throw new ConfigurationException(String.format(
                        "状态机 [%s] intentToStateMapping 引用的意图编码 '%s' 不存在",
                        caseId, code));
                }
            }
        }

        Set<String> globalEvents = config.getGlobalEventIntents();
        if (globalEvents != null) {
            for (String code : globalEvents) {
                if (!intentLoader.isValidIntentCode(code)) {
                    throw new ConfigurationException(String.format(
                        "状态机 [%s] globalEventIntents 引用的意图编码 '%s' 不存在",
                        caseId, code));
                }
            }
        }
    }


    // ====================================================================
    // ★ 新增：状态机可执行意图查询
    // ====================================================================

    /**
     * 查询指定案例、指定状态下的**可触发意图**。
     */
    public List<String> getAvailableIntents(String caseType, String currentState) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseType);
        if (config == null || currentState == null) return Collections.emptyList();

        Set<String> result = new LinkedHashSet<>();

        List<TransitionRule> transitions = config.getTransitions();
        if (transitions != null) {
            for (TransitionRule t : transitions) {
                if (!currentState.equals(t.getSource())) continue;
                String intent = t.getIntentCode();
                if (intent == null || intent.isBlank()) continue;
                if (intent.startsWith("SYS_")) continue;
                result.add(intent);
            }
        }

        Set<String> globalEvents = config.getGlobalEventIntents();
        if (globalEvents != null) {
            for (String g : globalEvents) {
                if (g != null && !g.isBlank() && !g.startsWith("SYS_")) {
                    result.add(g);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 返回从当前状态出发的完整转移明细。
     */
    public List<Map<String, Object>> getAvailableTransitions(String caseType,
                                                             String currentState) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseType);
        if (config == null || currentState == null) return Collections.emptyList();

        List<Map<String, Object>> list = new ArrayList<>();
        List<TransitionRule> transitions = config.getTransitions();
        if (transitions != null) {
            for (TransitionRule t : transitions) {
                if (!currentState.equals(t.getSource())) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("intent", t.getIntentCode());
                row.put("target", t.getTarget());
                row.put("exitReason", t.getExitReason());   // 若字段不存在则删
                row.put("effects",    t.getEffects());      // 同上
                list.add(row);
            }
        }
        return list;
    }

    /**
     * 返回案例的初始状态。
     */
    public String getInitialState(String caseType) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseType);
        return config != null ? config.getInitialState() : null;
    }
}
