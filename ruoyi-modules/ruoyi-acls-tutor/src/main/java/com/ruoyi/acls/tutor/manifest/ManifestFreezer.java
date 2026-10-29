package com.ruoyi.acls.tutor.manifest;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.acls.contract.util.CaseFileReviewStatusResolver;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 尝试清单冻结器。
 *
 * <p>职责：读取病例文件、计算 sha256、组装 {@link AttemptManifest}。
 * 不做版本协商、不做业务校验——这些交给调用方或后续 CaseContractBuilder。</p>
 *
 * <p>阶段 3 现状：
 * <ul>
 *   <li>eventSequence 直读 {@link CaseContext#getEventSequence()}（P0-9 双序列）；</li>
 *   <li>assessmentReal 直读 {@link ClockService#assessmentRealElapsedSeconds(String)}（B 步）；</li>
 *   <li>stateRevision 暂用 {@code ctx.getVersion()}，权威变更独立版本留待后续落地。</li>
 * </ul>
 *
 * <p>阶段 4.1 改动：{@code reviewStatus} 由 {@link CaseFileReviewStatusResolver}
 * 从病例 YAML 读取，key 保持纯文件名（与 {@code CaseFileBaseline.files} 键空间一致）。</p>
 */
@Slf4j
@Component
public class ManifestFreezer {

    /** 病例 YAML 资源目录前缀。 */
    private static final String CASE_BASE = "classpath:config/acls/cases/";

    /** 纳入清单的病例文件。 */
    private static final List<String> CASE_FILES = List.of(
        "clinical_rules.yaml",
        "lesson_plan.yaml",
        "scenario_beats.yaml",
        "fallback_paths.yaml",
        "instructor_overrides.yaml"
    );

    @Autowired private ResourceLoader resourceLoader;
    @Autowired private ClockService clockService;
    @Autowired private EmsContextManager contextManager;
    @Autowired private CaseFileReviewStatusResolver reviewStatusResolver;

    /**
     * 冻结尝试清单。
     */
    public AttemptManifest freeze(String sessionId,
                                  String caseId,
                                  String caseVersion,
                                  String planId,
                                  String planVersion,
                                  String schemaVersion,
                                  String frozenBy) {
        if (sessionId == null || caseId == null) {
            throw new IllegalArgumentException("sessionId / caseId 不能为空");
        }
        CaseContext ctx = contextManager.getSnapshot(sessionId);
        if (ctx == null) {
            throw new IllegalStateException("会话快照不存在: " + sessionId);
        }

        Map<String, String> hashes = new LinkedHashMap<>();
        for (String fileName : CASE_FILES) {
            String relative = "config/acls/cases/" + caseId + "/" + fileName;
            String sha = sha256Of("classpath:" + relative);
            if (sha != null) {
                hashes.put(relative, sha);
            }
        }

        // ★ 阶段 4.1：从病例 YAML 读取真实审核状态；key 为纯文件名
        Map<String, String> reviewStatus = new LinkedHashMap<>();
        for (String fileName : CASE_FILES) {
            reviewStatus.put(fileName, resolveReviewStatusSafe(caseId, fileName));
        }

        long stateRevision = ctx.getVersion();
        long eventSequence = ctx.getEventSequence();
        double simulated = elapsedSecondsSafe(sessionId);
        double assessmentReal = assessmentRealElapsedSafe(sessionId);

        AttemptManifest m = AttemptManifest.builder()
            .manifestId(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .attemptId(sessionId)
            .caseId(caseId)
            .caseVersion(caseVersion)
            .planId(planId)
            .planVersion(planVersion)
            .schemaVersion(schemaVersion)
            .fileHashes(hashes)
            .reviewStatus(reviewStatus)
            .stateRevision(stateRevision)
            .eventSequence(eventSequence)
            .simulatedElapsedSeconds(simulated)
            .assessmentRealElapsedSeconds(assessmentReal)
            .frozenAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .frozenBy(frozenBy != null ? frozenBy : "SYSTEM")
            .frozen(true)
            .build();

        log.info("[Tutor][Manifest] 已冻结: manifest={}, session={}, case={}, files={}, stateRev={}, eventSeq={}, sim={}s, real={}s",
            m.getManifestId(), sessionId, caseId, hashes.size(),
            stateRevision, eventSequence, simulated, assessmentReal);
        return m;
    }

    /** 便捷重载：schemaVersion 默认 0.1.0，frozenBy 默认 SYSTEM。 */
    public AttemptManifest freeze(String sessionId, String caseId,
                                  String caseVersion, String planId, String planVersion) {
        return freeze(sessionId, caseId, caseVersion, planId, planVersion, "0.1.0", "SYSTEM");
    }

    /**
     * 读取评估真实时间（秒），来自 ClockService 墙上时间口径，包含暂停。
     * <p>与 simulated elapsed（{@link #elapsedSecondsSafe(String)}）严格分列，不混用。</p>
     */
    private double assessmentRealElapsedSafe(String sessionId) {
        try {
            return clockService.assessmentRealElapsedSeconds(sessionId);
        } catch (Exception e) {
            log.warn("[Tutor][Manifest] 读取 assessmentRealElapsedSeconds 失败: session={}, err={}",
                sessionId, e.getMessage());
            return 0.0;
        }
    }

    private double elapsedSecondsSafe(String sessionId) {
        try {
            return clockService.elapsedSeconds(sessionId);
        } catch (Exception e) {
            log.warn("[Tutor][Manifest] 读取 elapsedSeconds 失败: session={}, err={}",
                sessionId, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 审核状态读取兜底：resolver 未注入或读取失败时回退 pending，不阻断冻结。
     * <p>生产环境 resolver 由 Spring 注入；测试环境若忘记注入也不应导致 freeze 崩溃。</p>
     */
    private String resolveReviewStatusSafe(String caseId, String fileName) {
        if (reviewStatusResolver == null) {
            log.warn("[Tutor][Manifest] reviewStatusResolver 未注入，回退 pending: case={}, file={}",
                caseId, fileName);
            return CaseFileReviewStatusResolver.PENDING;
        }
        try {
            String status = reviewStatusResolver.resolve(caseId, fileName);
            return status != null ? status : CaseFileReviewStatusResolver.PENDING;
        } catch (Exception e) {
            log.warn("[Tutor][Manifest] 读取 review_status 失败，回退 pending: case={}, file={}, err={}",
                caseId, fileName, e.getMessage());
            return CaseFileReviewStatusResolver.PENDING;
        }
    }

    private String sha256Of(String location) {
        try {
            Resource r = resourceLoader.getResource(location);
            if (!r.exists()) {
                return null;
            }
            try (InputStream in = r.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("[Tutor][Manifest] 计算哈希失败: {}, err={}", location, e.getMessage());
            return null;
        }
    }
}
