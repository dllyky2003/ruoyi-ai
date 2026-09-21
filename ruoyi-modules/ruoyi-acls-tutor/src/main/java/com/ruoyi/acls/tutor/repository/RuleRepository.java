package com.ruoyi.acls.tutor.repository;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.acls.tutor.domain.CaseOutcomeBranch;
import com.ruoyi.acls.tutor.domain.ClinicalRule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临床规则仓库。从 classpath:config/acls/cases/&lt;caseId&gt;/clinical_rules.yaml 加载。
 *
 * <p>最小版本：只支持按 caseId 缓存，查询时用 (intentCode, currentState) 精确匹配。</p>
 *
 * <p>V3.1 扩展：同时加载 case_outcome_branches（病例结局分支）。</p>
 *
 * <p>YAML 使用 snake_case（rule_id / match_intent / applies_when_current_state / feedback_key），
 * 通过 {@link PropertyNamingStrategies#SNAKE_CASE} 映射到 Java camelCase 字段。</p>
 */
@Slf4j
@Component
public class RuleRepository {


    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** caseId -> 规则列表 */
    private final Map<String, List<ClinicalRule>> cache = new ConcurrentHashMap<>();

    /** caseId -> (branchId -> CaseOutcomeBranch) */
    private final Map<String, Map<String, CaseOutcomeBranch>> branchesCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                "classpath*:config/acls/cases/*/clinical_rules.yaml");

            if (resources.length == 0) {
                log.warn("[Tutor][RuleRepo] 未扫描到任何 clinical_rules.yaml，规则库为空");
                return;
            }
            log.info("[Tutor][RuleRepo] 扫描到 {} 个 clinical_rules.yaml", resources.length);

            for (Resource r : resources) {
                try (InputStream is = r.getInputStream()) {
                    RuleFile file = YAML.readValue(is, RuleFile.class);

                    int rulesSize = (file == null || file.getRules() == null)
                        ? 0 : file.getRules().size();
                    int branchesSize = (file == null || file.getCaseOutcomeBranches() == null)
                        ? 0 : file.getCaseOutcomeBranches().size();

                    // ★ 诊断日志：把读到的根对象字段打出来
                    log.info("[Tutor][RuleRepo] 解析文件: file={}, caseId={}, version={}, rulesSize={}, branchesSize={}",
                        r.getFilename(),
                        file == null ? "<null>" : file.getCaseId(),
                        file == null ? "<null>" : file.getVersion(),
                        rulesSize, branchesSize);

                    if (file == null || file.getCaseId() == null || file.getCaseId().isBlank()) {
                        log.warn("[Tutor][RuleRepo] 跳过无 case_id 的文件: {}", r.getFilename());
                        continue;
                    }

                    // ---- 规则：轻量校验 ----
                    List<ClinicalRule> raw = file.getRules() != null
                        ? file.getRules() : new ArrayList<>();

                    List<ClinicalRule> valid = new ArrayList<>(raw.size());
                    for (ClinicalRule cr : raw) {
                        if (cr.getRuleId() == null || cr.getRuleId().isBlank()
                            || cr.getMatchIntent() == null || cr.getMatchIntent().isBlank()) {
                            log.warn("[Tutor][RuleRepo] 跳过无效规则: ruleId={}, matchIntent={}, " +
                                    "decision={}, state={}",
                                cr.getRuleId(), cr.getMatchIntent(),
                                cr.getDecision(), cr.getAppliesWhenCurrentState());
                            continue;
                        }
                        valid.add(cr);
                    }
                    cache.put(file.getCaseId(), valid);

                    // ---- 病例结局分支：轻量校验 ----
                    Map<String, CaseOutcomeBranch> branches = new LinkedHashMap<>();
                    if (file.getCaseOutcomeBranches() != null) {
                        for (CaseOutcomeBranch b : file.getCaseOutcomeBranches()) {
                            if (b == null || b.getBranchId() == null || b.getBranchId().isBlank()) {
                                log.warn("[Tutor][RuleRepo] 跳过无 branch_id 的病例结局分支");
                                continue;
                            }
                            branches.put(b.getBranchId(), b);
                        }
                    }
                    branchesCache.put(file.getCaseId(), branches);

                    log.info("[Tutor][RuleRepo] 已加载 case={} 的规则 {} 条 (原始 {} 条, 过滤 {} 条), 结局分支 {} 条",
                        file.getCaseId(), valid.size(), raw.size(),
                        raw.size() - valid.size(), branches.size());

                } catch (Exception e) {
                    // 单个文件失败不影响其他文件
                    log.warn("[Tutor][RuleRepo] 解析文件失败: file={}, error={}",
                        r.getFilename(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.warn("[Tutor][RuleRepo] 加载规则失败（不影响状态机）: {}", e.getMessage(), e);
        }
    }

    /**
     * 精确匹配：intentCode + currentState 都相等时命中。未命中返回 null。
     *
     * <p>appliesWhenCurrentState 为 null 视为"任意状态都适用"（通配）。</p>
     */
    public ClinicalRule findMatch(String caseId, String intentCode, String currentState) {
        if (caseId == null || intentCode == null) {
            return null;
        }
        List<ClinicalRule> rules = cache.get(caseId);
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (ClinicalRule r : rules) {
            boolean intentOk = intentCode.equals(r.getMatchIntent());
            boolean stateOk = r.getAppliesWhenCurrentState() == null
                || r.getAppliesWhenCurrentState().equals(currentState);
            if (intentOk && stateOk) {
                return r;
            }
        }
        return null;
    }

    // ==================== V3.1 新增：病例结局分支查询 ====================

    /**
     * 按 branchId 查询病例结局分支。未命中返回 null。
     */
    public CaseOutcomeBranch findBranch(String caseId, String branchId) {
        if (caseId == null || branchId == null) {
            return null;
        }
        Map<String, CaseOutcomeBranch> map = branchesCache.get(caseId);
        return map != null ? map.get(branchId) : null;
    }

    /**
     * 获取某 case 的全部结局分支（不可变视图）。无则返回空 Map。
     */
    public Map<String, CaseOutcomeBranch> getBranches(String caseId) {
        if (caseId == null) {
            return Collections.emptyMap();
        }
        Map<String, CaseOutcomeBranch> map = branchesCache.get(caseId);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    // ==================== YAML 根对象 ====================

    @Data
    public static class RuleFile {
        @JsonProperty("case_id")
        @JsonAlias({"caseId"})
        private String caseId;

        @JsonProperty("version")
        private String version;

        @JsonProperty("rules")
        private List<ClinicalRule> rules;

        // V3.1 新增
        @JsonProperty("case_outcome_branches")
        @JsonAlias({"caseOutcomeBranches"})
        private List<CaseOutcomeBranch> caseOutcomeBranches;
    }
}
