package com.ruoyi.acls.tutor.repository;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.acls.tutor.domain.FallbackPath;
import com.ruoyi.acls.tutor.domain.InstructorOverride;
import com.ruoyi.acls.tutor.domain.LessonPlan;
import com.ruoyi.acls.tutor.domain.ScenarioBeat;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 教案包仓库。从 classpath:config/acls/cases/&lt;caseId&gt;/ 加载 4 个文件：
 * <ul>
 *   <li>lesson_plan.yaml          — 根文件（元数据 + 引用）</li>
 *   <li>scenario_beats.yaml       — 剧情节点</li>
 *   <li>fallback_paths.yaml       — 兜底路径</li>
 *   <li>instructor_overrides.yaml — 导师覆盖</li>
 * </ul>
 *
 * <p>加载策略：扫描所有 lesson_plan.yaml，从根文件读 case_id，
 * 再按 case_id 加载同目录的另外 3 个文件。</p>
 *
 * <p>任一副文件缺失或解析失败，不影响其他文件；对应列表保持为空。</p>
 */
@Slf4j
@Component
public class LessonPlanRepository {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** caseId -> 教案包 */
    private final Map<String, LessonPlanBundle> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                "classpath*:config/acls/cases/*/lesson_plan.yaml");

            if (resources.length == 0) {
                log.info("[Tutor][LessonPlanRepo] 未扫描到任何 lesson_plan.yaml，教案库为空");
                return;
            }
            log.info("[Tutor][LessonPlanRepo] 扫描到 {} 个 lesson_plan.yaml", resources.length);

            for (Resource r : resources) {
                try (InputStream is = r.getInputStream()) {
                    LessonPlanFile file = YAML.readValue(is, LessonPlanFile.class);

                    if (file == null || file.getLessonPlan() == null
                        || file.getLessonPlan().getCaseId() == null
                        || file.getLessonPlan().getCaseId().isBlank()) {
                        log.warn("[Tutor][LessonPlanRepo] 跳过无 case_id 的文件: {}", r.getFilename());
                        continue;
                    }

                    LessonPlan plan = file.getLessonPlan();
                    String caseId = plan.getCaseId();

                    LessonPlanBundle bundle = new LessonPlanBundle();
                    bundle.plan = plan;
                    bundle.beats = loadBeats(resolver, caseId);
                    bundle.fallbacks = loadFallbacks(resolver, caseId);
                    bundle.overrides = loadOverrides(resolver, caseId);

                    cache.put(caseId, bundle);

                    log.info("[Tutor][LessonPlanRepo] 已加载 case={} 的教案包: planId={}, version={}, beats={}, fallbacks={}, overrides={}",
                        caseId, plan.getLessonPlanId(), plan.getLessonPlanVersion(),
                        bundle.beats.size(), bundle.fallbacks.size(), bundle.overrides.size());

                } catch (Exception e) {
                    log.warn("[Tutor][LessonPlanRepo] 解析失败: file={}, error={}",
                        r.getFilename(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.warn("[Tutor][LessonPlanRepo] 加载失败（不影响状态机）: {}", e.getMessage(), e);
        }
    }

    // ==================== 子文件加载 ====================

    private List<ScenarioBeat> loadBeats(PathMatchingResourcePatternResolver resolver, String caseId) {
        try {
            Resource[] rs = resolver.getResources(
                "classpath*:config/acls/cases/" + caseId + "/scenario_beats.yaml");
            if (rs.length == 0) {
                log.debug("[Tutor][LessonPlanRepo] case={} 无 scenario_beats.yaml", caseId);
                return Collections.emptyList();
            }
            try (InputStream is = rs[0].getInputStream()) {
                ScenarioBeatsFile f = YAML.readValue(is, ScenarioBeatsFile.class);
                return (f != null && f.getScenarioBeats() != null)
                    ? f.getScenarioBeats() : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("[Tutor][LessonPlanRepo] 加载 scenario_beats 失败: case={}, err={}",
                caseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FallbackPath> loadFallbacks(PathMatchingResourcePatternResolver resolver, String caseId) {
        try {
            Resource[] rs = resolver.getResources(
                "classpath*:config/acls/cases/" + caseId + "/fallback_paths.yaml");
            if (rs.length == 0) {
                log.debug("[Tutor][LessonPlanRepo] case={} 无 fallback_paths.yaml", caseId);
                return Collections.emptyList();
            }
            try (InputStream is = rs[0].getInputStream()) {
                FallbackPathsFile f = YAML.readValue(is, FallbackPathsFile.class);
                return (f != null && f.getFallbackPaths() != null)
                    ? f.getFallbackPaths() : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("[Tutor][LessonPlanRepo] 加载 fallback_paths 失败: case={}, err={}",
                caseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<InstructorOverride> loadOverrides(PathMatchingResourcePatternResolver resolver, String caseId) {
        try {
            Resource[] rs = resolver.getResources(
                "classpath*:config/acls/cases/" + caseId + "/instructor_overrides.yaml");
            if (rs.length == 0) {
                log.debug("[Tutor][LessonPlanRepo] case={} 无 instructor_overrides.yaml", caseId);
                return Collections.emptyList();
            }
            try (InputStream is = rs[0].getInputStream()) {
                InstructorOverridesFile f = YAML.readValue(is, InstructorOverridesFile.class);
                return (f != null && f.getInstructorOverrides() != null)
                    ? f.getInstructorOverrides() : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("[Tutor][LessonPlanRepo] 加载 instructor_overrides 失败: case={}, err={}",
                caseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 查询接口 ====================

    public LessonPlanBundle getBundle(String caseId) {
        return caseId == null ? null : cache.get(caseId);
    }

    public LessonPlan getPlan(String caseId) {
        LessonPlanBundle b = getBundle(caseId);
        return b != null ? b.plan : null;
    }

    public List<ScenarioBeat> getBeats(String caseId) {
        LessonPlanBundle b = getBundle(caseId);
        return b != null ? b.beats : Collections.emptyList();
    }

    public List<FallbackPath> getFallbacks(String caseId) {
        LessonPlanBundle b = getBundle(caseId);
        return b != null ? b.fallbacks : Collections.emptyList();
    }

    public List<InstructorOverride> getOverrides(String caseId) {
        LessonPlanBundle b = getBundle(caseId);
        return b != null ? b.overrides : Collections.emptyList();
    }

    /**
     * 按 beatId 查询。未命中返回 null。
     */
    public ScenarioBeat findBeat(String caseId, String beatId) {
        if (caseId == null || beatId == null) return null;
        for (ScenarioBeat b : getBeats(caseId)) {
            if (beatId.equals(b.getBeatId())) return b;
        }
        return null;
    }

    /**
     * 按 overrideId 查询。未命中返回 null。
     */
    public InstructorOverride findOverride(String caseId, String overrideId) {
        if (caseId == null || overrideId == null) return null;
        for (InstructorOverride o : getOverrides(caseId)) {
            if (overrideId.equals(o.getOverrideId())) return o;
        }
        return null;
    }

    // ==================== 内部结构 ====================

    /**
     * 一个 case 的完整教案包。
     */
    @Data
    public static class LessonPlanBundle {
        private LessonPlan plan;
        private List<ScenarioBeat> beats = new ArrayList<>();
        private List<FallbackPath> fallbacks = new ArrayList<>();
        private List<InstructorOverride> overrides = new ArrayList<>();
    }

    /** YAML 根包装：lesson_plan.yaml */
    @Data
    public static class LessonPlanFile {
        @JsonProperty("lesson_plan")
        @JsonAlias({"lessonPlan"})
        private LessonPlan lessonPlan;
    }

    /** YAML 根包装：scenario_beats.yaml */
    @Data
    public static class ScenarioBeatsFile {
        @JsonProperty("scenario_beats")
        @JsonAlias({"scenarioBeats"})
        private List<ScenarioBeat> scenarioBeats;
    }

    /** YAML 根包装：fallback_paths.yaml */
    @Data
    public static class FallbackPathsFile {
        @JsonProperty("fallback_paths")
        @JsonAlias({"fallbackPaths"})
        private List<FallbackPath> fallbackPaths;
    }

    /** YAML 根包装：instructor_overrides.yaml */
    @Data
    public static class InstructorOverridesFile {
        @JsonProperty("instructor_overrides")
        @JsonAlias({"instructorOverrides"})
        private List<InstructorOverride> instructorOverrides;
    }
}
