package com.ruoyi.ems.config.loader;


import com.ruoyi.ems.domain.ExceptionRule.ExceptionRule;
import com.ruoyi.ems.domain.ExceptionsConfig.ExceptionsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常规则加载器。对齐 DerivationRuleLoader 的加载 / 合并 / 查询模式。
 *
 * 文件位置：classpath*:config/acls/**\/_exceptions.yaml
 *   /GLOBAL/         → 全局
 *   /cases/{caseId}/ → 案例专属
 *
 * 合并语义：案例同名 id 覆盖全局。
 */
@Slf4j
@Component
public class ExceptionRuleLoader {

    /** Unity 端追踪的 6 个体征变量，由 vitals_update 覆盖，禁止出现在 effects 中 */
    private static final Set<String> UNITY_TRACKED_VARS = Set.of(
        "phys.heartRate", "phys.systolicBp", "phys.diastolicBp",
        "phys.spo2", "phys.etco2", "phys.respiratoryRate"
    );

    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "MINOR", "TIMING");

    /**
     * unityStateName 白名单，来源：Unity PulseDriverAdapter.arrhythmiaAliases 的 stateName 列。
     * TODO 若引入非心律状态，迁到 config/acls/GLOBAL/_unity_patient_states.yaml。
     */
    private static final Set<String> VALID_UNITY_STATE_NAMES = Set.of(
        "正常窦性心律", "室上速(SVT)", "室颤(VF)", "单形室速(VT)",
        "心房颤动(AFib)", "心房扑动(AFlutter)", "一度房室阻滞",
        "不稳定型心动过缓(二度I型)", "二度II型房室阻滞", "三度房室阻滞",
        "右束支阻滞(RBBB)", "左束支阻滞(LBBB)", "高钾血症",
        "下壁心肌梗死", "心肌缺血", "尖端扭转型室速",
        "心搏停止(Asystole)", "起搏器心律"
    );

    private static final String GLOBAL = "GLOBAL";
    private static final Pattern CASE_ID_PATTERN = Pattern.compile(".*/cases/([^/]+)/.*");

    private final Map<String, List<ExceptionRule>> caseRules = new ConcurrentHashMap<>();
    private volatile List<ExceptionRule> globalRules = new ArrayList<>();
    private volatile boolean loaded = false;

    // ====================================================================
    // 加载入口
    // ====================================================================

    public void loadAll() {
        if (loaded) {
            log.debug("ExceptionRuleLoader 已加载，跳过重复加载");
            return;
        }
        doLoadAll();
        loaded = true;
    }

    public synchronized void reloadAll() {
        log.info("🔄 重新加载异常规则（全量）...");
        caseRules.clear();
        globalRules = new ArrayList<>();
        loaded = false;
        doLoadAll();
        loaded = true;
    }

    public synchronized void reloadCase(String caseId) {
        if (!StringUtils.hasText(caseId)) {
            log.warn("reloadCase: caseId 为空，跳过");
            return;
        }
        log.info("🔄 重新加载案例 [{}] 的异常规则...", caseId);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = "classpath*:config/acls/cases/" + caseId + "/_exceptions.yaml";
            Resource[] resources = resolver.getResources(pattern);

            if (resources.length == 0) {
                log.warn("案例 [{}] 未找到 _exceptions.yaml", caseId);
                return;
            }

            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    ExceptionsConfig config = yaml.loadAs(is, ExceptionsConfig.class);
                    if (config != null && config.getExceptions() != null) {
                        List<ExceptionRule> rules = dedupAndValidate(
                            config.getExceptions(), resource.getDescription());
                        List<ExceptionRule> merged = mergeWithGlobal(rules);
                        caseRules.put(caseId, merged);
                        log.info("📄 重载案例 [{}] 异常规则: 原始 {} 条, 合并后 {} 条",
                            caseId, rules.size(), merged.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("重新加载案例 [{}] 异常规则失败", caseId, e);
        }
    }

    // ====================================================================
    // 内部加载
    // ====================================================================

    private void doLoadAll() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:config/acls/**/_exceptions.yaml");

            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                String path = resource.getURL().toString();
                String caseId = extractCaseId(path);
                try (InputStream is = resource.getInputStream()) {
                    ExceptionsConfig config = yaml.loadAs(is, ExceptionsConfig.class);
                    if (config == null || config.getExceptions() == null) continue;

                    List<ExceptionRule> rules = dedupAndValidate(
                        config.getExceptions(), resource.getDescription());

                    if (GLOBAL.equals(caseId)) {
                        globalRules = rules;
                        log.info("📄 加载全局异常规则: {} 条", rules.size());
                    } else {
                        caseRules.put(caseId, rules);
                        log.info("📄 加载案例 [{}] 异常规则: {} 条", caseId, rules.size());
                    }
                }
            }

            for (Map.Entry<String, List<ExceptionRule>> entry : caseRules.entrySet()) {
                String caseId = entry.getKey();
                List<ExceptionRule> merged = mergeWithGlobal(entry.getValue());
                entry.setValue(merged);
                log.info("✅ 案例 [{}] 合并后异常规则: {} 条", caseId, merged.size());
            }

            caseRules.put(GLOBAL, globalRules);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("加载异常规则失败", e);
        }
    }

    private List<ExceptionRule> mergeWithGlobal(List<ExceptionRule> caseOnly) {
        Map<String, ExceptionRule> idMap = new LinkedHashMap<>();
        for (ExceptionRule rule : globalRules) {
            if (rule.getId() != null) idMap.put(rule.getId(), rule);
        }
        if (caseOnly != null) {
            for (ExceptionRule rule : caseOnly) {
                if (rule.getId() != null) idMap.put(rule.getId(), rule);
            }
        }
        return new ArrayList<>(idMap.values());
    }

    private String extractCaseId(String path) {
        if (path.contains("/GLOBAL/") || path.contains("\\GLOBAL\\")) return GLOBAL;
        Matcher m = CASE_ID_PATTERN.matcher(path);
        return m.find() ? m.group(1) : GLOBAL;
    }

    private List<ExceptionRule> dedupAndValidate(List<ExceptionRule> raw, String source) {
        Map<String, ExceptionRule> byId = new LinkedHashMap<>();
        for (ExceptionRule rule : raw) {
            validateRule(rule, source);
            if (byId.putIfAbsent(rule.getId(), rule) != null) {
                throw new IllegalStateException(
                    "同一异常规则文件内 id 重复: " + rule.getId() + " @ " + source);
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ====================================================================
    // 校验
    // ====================================================================

    private void validateRule(ExceptionRule rule, String source) {
        if (rule == null) throw new IllegalStateException("异常规则为空: " + source);
        if (!StringUtils.hasText(rule.getId()))
            throw new IllegalStateException("异常规则 id 为空: " + source);

        if (rule.getPhase() != null && rule.getPhase().isBlank()) {
            log.warn("异常规则 phase 为空白字符串，视为不限阶段: id={}", rule.getId());
            rule.setPhase(null);
        }


        if (!StringUtils.hasText(rule.getSeverity())
            || !VALID_SEVERITIES.contains(rule.getSeverity()))
            throw new IllegalStateException("非法 severity: id=" + rule.getId()
                + ", severity=" + rule.getSeverity());


        boolean keepCurrent = Boolean.TRUE.equals(rule.getKeepCurrent());
        if (!keepCurrent && !StringUtils.hasText(rule.getJumpTo())) {
            throw new IllegalStateException(
                "异常规则非 keepCurrent 但 jumpTo 为空: id=" + rule.getId());
        }
        if (keepCurrent && StringUtils.hasText(rule.getJumpTo())) {
            log.warn("异常规则 keepCurrent=true 但配置了 jumpTo（将被忽略）: id={}, jumpTo={}",
                rule.getId(), rule.getJumpTo());
        }

        // 约束 1：unityStateName 存在性
        if (!StringUtils.hasText(rule.getUnityStateName()))
            throw new IllegalStateException("unityStateName 为空: id=" + rule.getId());
        if (!VALID_UNITY_STATE_NAMES.contains(rule.getUnityStateName()))
            throw new IllegalStateException("unityStateName 不在 Unity 有效状态列表: id="
                + rule.getId() + ", unityStateName=" + rule.getUnityStateName());

        // 约束 3：condition 与 triggerIntent 至少一个非空
        if (!StringUtils.hasText(rule.getCondition())
            && !StringUtils.hasText(rule.getTriggerIntent()))
            throw new IllegalStateException(
                "condition 与 triggerIntent 不能同时为空: id=" + rule.getId());

        // 约束 2：effects 不得写 Unity 追踪变量；valueExpression 必须以 # 开头
        if (rule.getEffects() != null) {
            for (ExceptionRule.EffectRule effect : rule.getEffects()) {
                if (effect == null || !StringUtils.hasText(effect.getVar()))
                    throw new IllegalStateException("effects.var 为空: id=" + rule.getId());
                if (UNITY_TRACKED_VARS.contains(effect.getVar()))
                    throw new IllegalStateException("effects 不能写 Unity 追踪变量: id="
                        + rule.getId() + ", var=" + effect.getVar());
                if (StringUtils.hasText(effect.getValueExpression())
                    && !effect.getValueExpression().trim().startsWith("#"))
                    throw new IllegalStateException("valueExpression 必须以 # 开头: id="
                        + rule.getId() + ", var=" + effect.getVar()
                        + ", valueExpression=" + effect.getValueExpression());
            }
        }

        // feedback 结构校验：允许全空；interruptCurrentTts=true 时 tts 必须非空
        if (rule.getFeedback() != null) {
            ExceptionRule.Feedback fb = rule.getFeedback();
            validateModeFeedback(fb.getTeaching(), "teaching", rule.getId());
            validateModeFeedback(fb.getLearning(), "learning", rule.getId());
            validateModeFeedback(fb.getExam(),     "exam",     rule.getId());
        }
    }

    private void validateModeFeedback(ExceptionRule.Feedback.ModeFeedback mf,
                                      String modeName, String ruleId) {
        if (mf == null) return;
        if (Boolean.TRUE.equals(mf.getInterruptCurrentTts())
            && !StringUtils.hasText(mf.getTts())) {
            throw new IllegalStateException("interruptCurrentTts=true 但 tts 为空: id="
                + ruleId + ", mode=" + modeName);
        }
    }

    // ====================================================================
    // 对外查询
    // ====================================================================

    public List<ExceptionRule> getRulesForCase(String caseType) {
        List<ExceptionRule> rules = caseRules.get(caseType);
        if (rules == null) rules = caseRules.get(GLOBAL);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }

    public List<ExceptionRule> getGlobalRules() {
        return Collections.unmodifiableList(globalRules);
    }

    public Set<String> getAllCaseIds() {
        return Collections.unmodifiableSet(caseRules.keySet());
    }
}
