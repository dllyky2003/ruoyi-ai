package com.ruoyi.acls.tutor.service;

import com.ruoyi.acls.engine.ClockService;
import com.ruoyi.acls.tutor.domain.AssessmentInterruption;
import com.ruoyi.acls.tutor.domain.AssessmentState;
import com.ruoyi.acls.tutor.engine.ScenarioBeatEvaluator;
import com.ruoyi.acls.tutor.manifest.AttemptManifest;
import com.ruoyi.acls.tutor.manifest.ManifestFreezer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评估引擎：编排 D4 评分中断、双时钟快照与版本冻结。
 *
 * <p>职责边界：
 * <ul>
 *   <li>持有 session → AssessmentState 的内存映射（阶段 3 先内存，阶段 4 再接持久层）；</li>
 *   <li>管理"正在进行的"中断生命周期：begin / end；</li>
 *   <li>提供 D4 导师覆盖便捷入口：recordInstructorOverride；</li>
 *   <li>调用 {@link ManifestFreezer} 冻结 {@link AttemptManifest}，并缓存最近一次。</li>
 * </ul>
 *
 * <p>不做的事（P0-8）：
 * <ul>
 *   <li>不自动演进 case_defined_progression / fail_branch；</li>
 *   <li>不自动触发中断——所有中断由显式调用发起（导师、暂停、规则引擎）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentEngine {

    private final ManifestFreezer manifestFreezer;
    private final ClockService clockService;

    /** sessionId → 评估状态。 */
    private final Map<String, AssessmentState> stateMap = new ConcurrentHashMap<>();

    /** sessionId → 最近一次冻结清单。 */
    private final Map<String, AttemptManifest> lastManifestMap = new ConcurrentHashMap<>();

    /** sessionId → interruptionId → 进行中的中断。 */
    private final Map<String, Map<String, AssessmentInterruption>> ongoingMap =
        new ConcurrentHashMap<>();

    // ============================================================
    // 状态管理
    // ============================================================

    public AssessmentState getOrCreateState(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        return stateMap.computeIfAbsent(sessionId, k -> new AssessmentState());
    }

    public AssessmentState getState(String sessionId) {
        return sessionId != null ? stateMap.get(sessionId) : null;
    }

    /** 清理会话的所有评估侧内存状态。会话结束时调用。 */
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        stateMap.remove(sessionId);
        lastManifestMap.remove(sessionId);
        ongoingMap.remove(sessionId);
        log.info("[Tutor][Assessment] 已清理: session={}", sessionId);
    }

    // ============================================================
    // 中断生命周期
    // ============================================================

    /**
     * 开始一次评估中断。
     *
     * <p>timeBasis = REAL / BOTH 时强制要求 {@code realTimeRationale}（S3）；
     * pauseSensitive 自动置为 true（S3 语义）。</p>
     *
     * @param unevaluableItems 受影响项 → 原因；可为 null
     */
    public AssessmentInterruption beginInterruption(
        String sessionId,
        String caseId,
        AssessmentInterruption.InterruptionReason reason,
        String sourceRef,
        String sourceType,
        AssessmentInterruption.TimeBasis timeBasis,
        String realTimeRationale,
        Map<String, String> unevaluableItems) {

        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason 不能为空");
        }
        if (timeBasis == null) {
            throw new IllegalArgumentException("timeBasis 不能为空");
        }

        boolean pauseSensitive = timeBasis == AssessmentInterruption.TimeBasis.REAL
            || timeBasis == AssessmentInterruption.TimeBasis.BOTH;

        // S3 一致性：REAL / BOTH 必须有 rationale
        if (pauseSensitive && (realTimeRationale == null || realTimeRationale.isBlank())) {
            throw new IllegalArgumentException(
                "timeBasis=" + timeBasis + " 时必须提供 realTimeRationale（S3）");
        }

        double simAtStart = safeElapsed(sessionId);
        double realAtStart = safeAssessmentReal(sessionId);

        AssessmentInterruption interruption = AssessmentInterruption.builder()
            .interruptionId(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .caseId(caseId)
            .reason(reason)
            .sourceRef(sourceRef)
            .sourceType(sourceType)
            .timeBasis(timeBasis)
            .simulatedElapsedAtStart(simAtStart)
            .simulatedElapsedAtEnd(0.0)
            .assessmentRealElapsedAtStart(realAtStart)
            .assessmentRealElapsedAtEnd(0.0)
            .realTimeRationale(realTimeRationale)
            .pauseSensitive(pauseSensitive)
            .unevaluableItems(unevaluableItems != null
                ? new LinkedHashMap<>(unevaluableItems)
                : new LinkedHashMap<>())
            .notes(new ArrayList<>())
            .startedAt(LocalDateTime.now())
            .endedAt(null)
            .ongoing(true)
            .build();

        // 聚合到 AssessmentState；unevaluableItems 走 markUnevaluable 单点同步
        getOrCreateState(sessionId).addInterruption(interruption);

        ongoingMap.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(interruption.getInterruptionId(), interruption);

        log.info("[Tutor][Assessment] 中断开始: session={}, case={}, reason={}, id={}, "
                + "timeBasis={}, sim={}s, real={}s, unevaluable={}",
            sessionId, caseId, reason, interruption.getInterruptionId(),
            timeBasis, simAtStart, realAtStart,
            interruption.getUnevaluableItems().size());
        return interruption;
    }

    /**
     * 结束一次进行中的中断。
     *
     * <p>幂等：找不到对应中断返回 null，不抛异常。</p>
     */
    public AssessmentInterruption endInterruption(String sessionId, String interruptionId) {
        if (sessionId == null || interruptionId == null) {
            return null;
        }
        Map<String, AssessmentInterruption> ongoing = ongoingMap.get(sessionId);
        if (ongoing == null) {
            return null;
        }
        AssessmentInterruption i = ongoing.remove(interruptionId);
        if (i == null) {
            return null;
        }

        i.setOngoing(false);
        i.setEndedAt(LocalDateTime.now());
        i.setSimulatedElapsedAtEnd(safeElapsed(sessionId));
        i.setAssessmentRealElapsedAtEnd(safeAssessmentReal(sessionId));

        log.info("[Tutor][Assessment] 中断结束: session={}, id={}, sim={}s, real={}s",
            sessionId, interruptionId,
            i.getSimulatedElapsedAtEnd(), i.getAssessmentRealElapsedAtEnd());
        return i;
    }

    // ============================================================
    // D4 导师覆盖
    // ============================================================

    /**
     * 记录一次 D4 导师覆盖。
     *
     * <p>语义（D4）：override 不扣分，但触发一次评估中断；受影响的评估项被标记为
     * 不可评估，不计入通过判定。</p>
     *
     * <p>实现：以 SIMULATED 时间基准创建中断（override 本身不暂停敏感），
     * 立即结束中断，{@code affectedItems} 长期保留在 AssessmentState 中。</p>
     *
     * @param overrideId     导师覆盖 ID，例如 IO_PAUSE_CLOCK
     * @param affectedItems  受影响项 → 原因（例如 TAG_QRS_CHECK → INSTRUCTOR_OVERRIDE）
     */
    public AssessmentInterruption recordInstructorOverride(
        String sessionId,
        String caseId,
        String overrideId,
        Map<String, String> affectedItems) {

        AssessmentInterruption i = beginInterruption(
            sessionId,
            caseId,
            AssessmentInterruption.InterruptionReason.INSTRUCTOR_OVERRIDE,
            overrideId,
            "INSTRUCTOR_OVERRIDE",
            AssessmentInterruption.TimeBasis.SIMULATED,
            null,
            affectedItems);

        // D4：override 是瞬时事件，立即结束；unevaluableItems 长期保留
        return endInterruption(sessionId, i.getInterruptionId());
    }

    // ============================================================
    // 阶段 3 丙-2：beat 选择证据链
    // ============================================================

    /**
     * 记录一次 beat 选择结果到证据链。
     *
     * <p><b>只写证据，不触发中断、不驱动状态迁移、不改评分（P0-8）。</b>
     * 是否把该结果升级为中断，由调用方显式调用
     * {@link #beginInterruption} / {@link #recordInstructorOverride} 决定。</p>
     *
     * <p>{@code sessionId == null} 或 {@code result == null} 时不创建 state、直接返回 null；
     * 其它情况一律写入，即使 {@code result.selected == null} 也记录（冲突场景需要留痕）。</p>
     *
     * @return 写入的记录；未写入返回 null
     */
    public AssessmentState.BeatSelectionRecord recordBeatSelection(
        String sessionId,
        ScenarioBeatEvaluator.SelectionResult result) {

        if (sessionId == null || result == null) {
            return null;
        }

        AssessmentState state = getOrCreateState(sessionId);

        AssessmentState.BeatSelectionRecord record = new AssessmentState.BeatSelectionRecord();
        record.setBeatId(result.selected != null ? result.selected.getBeatId() : null);
        record.setOutcomeRef(result.selected != null ? result.selected.getCaseOutcomeRef() : null);
        record.setConflictDetected(result.conflictDetected);
        record.setConflictBeatIds(result.conflictBeatIds != null
            ? new ArrayList<>(result.conflictBeatIds)
            : new ArrayList<>());
        record.setReason(result.reason);
        record.setRecordedAt(LocalDateTime.now());

        state.addBeatSelection(record);
        return record;
    }

    // ============================================================
    // 版本冻结
    // ============================================================

    /**
     * 冻结当前会话的尝试清单。
     *
     * <p>调用 {@link ManifestFreezer#freeze} 并缓存最近一次结果，
     * 便于评估报告或 CaseContract 比对时读取。</p>
     *
     * <p>若 freezer 返回 null（异常兜底 / mock 场景），不写入缓存、不抛异常，
     * 只记录 warning。保证评估主链路不被冻结失败阻断。</p>
     */
    public AttemptManifest freezeAttempt(
        String sessionId,
        String caseId,
        String caseVersion,
        String planId,
        String planVersion,
        String schemaVersion) {

        AttemptManifest m = manifestFreezer.freeze(
            sessionId, caseId, caseVersion,
            planId, planVersion, schemaVersion, "ASSESSMENT_ENGINE");

        if (m == null) {
            log.warn("[Tutor][Assessment] freeze 返回 null，跳过缓存: session={}, case={}",
                sessionId, caseId);
            return null;
        }

        lastManifestMap.put(sessionId, m);
        return m;
    }

    public AttemptManifest getLastManifest(String sessionId) {
        return sessionId != null ? lastManifestMap.get(sessionId) : null;
    }

    // ============================================================
    // 工具
    // ============================================================

    private double safeElapsed(String sessionId) {
        try {
            return clockService.elapsedSeconds(sessionId);
        } catch (Exception e) {
            log.warn("[Tutor][Assessment] 读取 elapsedSeconds 失败: session={}, err={}",
                sessionId, e.getMessage());
            return 0.0;
        }
    }

    private double safeAssessmentReal(String sessionId) {
        try {
            return clockService.assessmentRealElapsedSeconds(sessionId);
        } catch (Exception e) {
            log.warn("[Tutor][Assessment] 读取 assessmentRealElapsedSeconds 失败: session={}, err={}",
                sessionId, e.getMessage());
            return 0.0;
        }
    }
}
