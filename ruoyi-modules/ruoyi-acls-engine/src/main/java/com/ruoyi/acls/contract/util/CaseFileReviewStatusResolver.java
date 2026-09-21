package com.ruoyi.acls.contract.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 病例文件聚合审核状态解析器。
 *
 * <p>阶段 4.1：给 {@code CaseContractBuilder} 与 {@code ManifestFreezer} 提供
 * 单点的 "caseId + fileName → 聚合 review_status" 解析。
 * 两处不再各自硬编码 {@code "pending"}。</p>
 *
 * <p>聚合规则（多记录时，最差优先）：
 * rejected &gt; pending &gt; draft &gt; in_review &gt; approved。
 * 空输入 / 字段缺失 / 解析失败 / 文件不存在 → pending（保守）。</p>
 *
 * <p>用 SnakeYAML（Spring Boot 已有传递依赖）；只读取，不写回。
 * 结果带缓存，按 {@code caseId/fileName} 幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseFileReviewStatusResolver {

    public static final String DRAFT     = "draft";
    public static final String IN_REVIEW = "in_review";
    public static final String PENDING   = "pending";
    public static final String APPROVED  = "approved";
    public static final String REJECTED  = "rejected";

    private static final String CASE_BASE = "classpath:config/acls/cases/";

    private final ResourceLoader resourceLoader;

    /** 缓存：caseId/fileName → 聚合状态。 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 解析某病例某文件的聚合 review_status。
     *
     * @return 五个合法值之一；任何异常路径都返回 {@link #PENDING}
     */
    public String resolve(String caseId, String fileName) {
        if (caseId == null || fileName == null) {
            return PENDING;
        }
        String cacheKey = caseId + "/" + fileName;
        return cache.computeIfAbsent(cacheKey, k -> doResolve(caseId, fileName));
    }

    // ============================================================
    // 内部
    // ============================================================

    @SuppressWarnings("unchecked")
    private String doResolve(String caseId, String fileName) {
        String location = CASE_BASE + caseId + "/" + fileName;
        try {
            Resource r = resourceLoader.getResource(location);
            if (!r.exists()) {
                log.debug("[Tutor][ReviewStatus] 文件不存在: {}", location);
                return PENDING;
            }
            try (InputStream in = r.getInputStream()) {
                Object loaded = new Yaml().load(in);
                if (!(loaded instanceof Map)) {
                    return PENDING;
                }
                List<String> statuses = extract(fileName, (Map<String, Object>) loaded);
                return aggregate(statuses);
            }
        } catch (Exception e) {
            log.warn("[Tutor][ReviewStatus] 解析失败: {}, err={}", location, e.getMessage());
            return PENDING;
        }
    }

    /**
     * 按文件结构提取全部 review_status 原始值。
     * 5 个文件结构不同，逐个处理；未知文件类型按顶层 review_status 兜底。
     */
    @SuppressWarnings("unchecked")
    private List<String> extract(String fileName, Map<String, Object> root) {
        switch (fileName) {
            case "lesson_plan.yaml":
                // ★ 修：review_status 在 lesson_plan 嵌套层，不是顶层
                return Collections.singletonList(extractLessonPlanReviewStatus(root));
            case "clinical_rules.yaml":
                return collectNested(root, "case_outcome_branches");
            case "scenario_beats.yaml":
                return collectNested(root, "scenario_beats");
            case "fallback_paths.yaml":
                return collectNested(root, "fallback_paths");
            case "instructor_overrides.yaml":
                return collectNested(root, "instructor_overrides");
            default:
                return Collections.singletonList(asString(root.get("review_status")));
        }
    }

    /** lesson_plan.yaml 的 review_status 位于 lesson_plan 嵌套层；兼容顶层兜底。 */
    @SuppressWarnings("unchecked")
    private String extractLessonPlanReviewStatus(Map<String, Object> root) {
        Object lessonPlan = root.get("lesson_plan");
        if (lessonPlan instanceof Map) {
            Object status = ((Map<String, Object>) lessonPlan).get("review_status");
            if (status != null) {
                return status.toString();
            }
        }
        // 兜底：若将来提到顶层也能读到
        return asString(root.get("review_status"));
    }

    /** 提取 {@code root[listKey][*].review.review_status} 列表。 */
    @SuppressWarnings("unchecked")
    private List<String> collectNested(Map<String, Object> root, String listKey) {
        Object listObj = root.get(listKey);
        if (!(listObj instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<Object>) listObj) {
            if (!(item instanceof Map)) continue;
            Object review = ((Map<String, Object>) item).get("review");
            if (!(review instanceof Map)) continue;
            Object status = ((Map<String, Object>) review).get("review_status");
            if (status != null) {
                result.add(status.toString());
            }
        }
        return result;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }

    // ============================================================
    // 聚合
    // ============================================================

    /**
     * 聚合多个 review_status：最差优先。
     * <p>rejected &gt; pending &gt; draft &gt; in_review &gt; approved；
     * 空输入或含 null → pending；非法值按 pending 处理。</p>
     */
    public static String aggregate(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return PENDING;
        }
        boolean hasNull = false;
        boolean hasPending = false;
        boolean hasRejected = false;
        boolean hasDraft = false;
        boolean hasInReview = false;
        boolean hasApproved = false;

        for (String s : statuses) {
            if (s == null) {
                hasNull = true;
                continue;
            }
            switch (s) {
                case REJECTED:  hasRejected = true; break;
                case PENDING:   hasPending = true; break;
                case DRAFT:     hasDraft = true; break;
                case IN_REVIEW: hasInReview = true; break;
                case APPROVED:  hasApproved = true; break;
                default:        hasPending = true; break; // 非法值保守归 pending
            }
        }

        if (hasRejected)  return REJECTED;
        if (hasPending || hasNull) return PENDING;
        if (hasDraft)     return DRAFT;
        if (hasInReview)  return IN_REVIEW;
        if (hasApproved)  return APPROVED;
        return PENDING;
    }

    /** 非法值判定：非五个合法值之一。 */
    public static boolean isInvalid(String status) {
        if (status == null) return true;
        return !(DRAFT.equals(status) || IN_REVIEW.equals(status)
            || PENDING.equals(status) || APPROVED.equals(status)
            || REJECTED.equals(status));
    }
}
