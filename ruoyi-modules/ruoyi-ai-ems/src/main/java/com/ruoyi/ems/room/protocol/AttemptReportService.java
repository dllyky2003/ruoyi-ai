package com.ruoyi.ems.room.protocol;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.dto.AttemptReport;
import com.ruoyi.ems.room.protocol.dto.ClinicalState;
import com.ruoyi.ems.room.protocol.dto.ProtocolState;
import com.ruoyi.ems.room.protocol.dto.SnapshotMessage;
import com.ruoyi.ems.spi.AssessmentQueryService;
import com.ruoyi.ems.spi.dto.BeatSelectionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 ems 侧运行时上下文组装最终评价报告。
 *
 * <p>调用时机：{@code EngineSessionControlHandler.handleEndSession} 内，
 * clockService.stop() 之后、sessionService.endSession() 之前。此时：</p>
 * <ul>
 *   <li>assessmentRealElapsedSeconds 已被 ClockService 冻结</li>
 *   <li>CaseContext 仍存活，evaluation.* / case.eventLog 可读</li>
 * </ul>
 *
 * <p>不依赖 acls-tutor 类型，避免反向依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptReportService {

    private static final String GENERATOR_VERSION = "1.0.0";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EmsContextManager contextManager;
    private final ClockService clockService;
    private final AttemptSequencer sequencer;
    private final AttemptSnapshotService snapshotService;
    private final ProtocolBridgeService protocolBridge;
    private final ObjectProvider<AssessmentQueryService> assessmentQueryProvider;

    /**
     * 组装报告。CaseContext 不存在时返回 null（不抛异常）。
     */
    public AttemptReport buildReport(String emsSessionId, String roomSessionId, String attemptId) {
        CaseContext ctx = contextManager.getSnapshot(emsSessionId);
        if (ctx == null) {
            log.warn("[AttemptReport] CaseContext 不存在，无法生成报告: ems={}", emsSessionId);
            return null;
        }

        Map<String, Object> vars = ctx.getVariables();

        // 复用快照抽取器
        ClinicalState clinical = null;
        ProtocolState protocol = null;
        try {
            SnapshotMessage snap = snapshotService.buildFullSnapshot(emsSessionId, attemptId);
            if (snap != null) {
                clinical = snap.getClinicalState();
                protocol = snap.getProtocolState();
            }
        } catch (Exception e) {
            log.warn("[AttemptReport] 快照构建失败（报告继续）: {}", e.getMessage());
        }

        return AttemptReport.builder()
            .attemptId(attemptId)
            .emsSessionId(emsSessionId)
            .roomSessionId(roomSessionId)
            .caseId(ctx.getCaseType())
            .manifestId(null)                                    // tutor 侧类型，ems 侧不可见
            .manifestHash(protocolBridge.getManifestHash(emsSessionId))
            .frozenAt(null)                                      // 同上
            .finalStateRevision(ctx.getVersion())
            .finalEventSequence(ctx.getEventSequence())
            .finalServerSequence(sequencer.current(emsSessionId))
            .simulatedElapsedSeconds(ctx.getElapsedSeconds())
            .assessmentRealElapsedSeconds(clockService.assessmentRealElapsedSeconds(emsSessionId))
            .evaluationVars(extractEvaluationVars(vars))
            .keyEvents(extractKeyEvents(vars))
            .beatSelections(extractBeatSelections(emsSessionId))
            .interruptions(new ArrayList<>())
            .finalClinicalState(clinical)
            .finalProtocolState(protocol)
            .generatedAt(LocalDateTime.now().format(ISO))
            .generatorVersion(GENERATOR_VERSION)
            .terminationReason(null)
            .build();
    }

    // ===== 抽取器 =====

    private Map<String, Object> extractEvaluationVars(Map<String, Object> vars) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("evaluation.")) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractKeyEvents(Map<String, Object> vars) {
        Object raw = vars.get("case.eventLog");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private List<Map<String, Object>> extractBeatSelections(String emsSessionId) {
        AssessmentQueryService svc = assessmentQueryProvider.getIfAvailable();
        if (svc == null) return new ArrayList<>();
        try {
            List<BeatSelectionView> views = svc.getBeatSelections(emsSessionId);
            if (views == null) return new ArrayList<>();
            List<Map<String, Object>> out = new ArrayList<>(views.size());
            for (BeatSelectionView v : views) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("beatId", v.getBeatId());
                m.put("outcomeRef", v.getOutcomeRef());
                m.put("conflictDetected", v.isConflictDetected());
                m.put("reason", v.getReason());
                m.put("recordedAt", v.getRecordedAt() != null ? v.getRecordedAt().toString() : null);
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("[AttemptReport] beatSelections 读取失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
