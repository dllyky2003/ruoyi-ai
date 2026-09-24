package com.ruoyi.ems.room.protocol;



import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.room.protocol.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptSnapshotService {

    private static final String PROTOCOL_VERSION = "1.0.0";

    private final EmsContextManager contextManager;

    // TODO: 接入 AttemptManifest 后，从它读 scenarioVersion / ruleBundleVersion / lessonPlanBundleVersion

    public SnapshotMessage buildFullSnapshot(String emsSessionId, String attemptId) {

        if (attemptId == null || attemptId.isBlank()) {
            log.warn("buildFullSnapshot 缺少 attemptId: ems={}", emsSessionId);
            return null;
        }

        CaseContext ctx = contextManager.getSnapshot(emsSessionId);
        if (ctx == null) {
            log.warn("[AttemptSnapshotService] CaseContext 不存在: ems={}", emsSessionId);
            return null;
        }

        Map<String, Object> vars = ctx.getVariables();

        return SnapshotMessage.builder()
            .protocolVersion(PROTOCOL_VERSION)
            .attemptId(attemptId)                                  // ← 修复点
            .stateRevision(ctx.getVersion())
            .eventSequence(ctx.getEventSequence())
            .isFullSnapshot(true)
            .scenarioId(ctx.getCaseType())
            .scenarioVersion("1.0.0")
            .ruleBundleVersion("2026.09.21")
            .lessonPlanBundleVersion("1.0.0")
            .clinicalState(extractClinicalState(vars))
            .protocolState(extractProtocolState(vars))
            .monitorPresentation(extractMonitorPresentation(vars))
            .systemStatus(extractSystemStatus(ctx))
            .events(new ArrayList<>())
            .build();
    }

    public SnapshotMessage buildIncrementalSnapshot(String emsSessionId,
                                                    String attemptId,
                                                    List<SnapshotEvent> events) {
        SnapshotMessage snapshot = buildFullSnapshot(emsSessionId, attemptId);
        if (snapshot == null) return null;
        snapshot.setFullSnapshot(false);
        snapshot.setEvents(events != null ? events : new ArrayList<>());
        return snapshot;
    }

    // ========== 抽取器 ==========

    private ClinicalState extractClinicalState(Map<String, Object> vars) {
        return ClinicalState.builder()
            .rhythm(mapRhythm(getStr(vars, "phys.rhythm")))
            .pulse(mapPulse(getBool(vars, "phys.hasPulse")))
            .perfusion(mapPerfusion(getBool(vars, "derived.stable")))
            .consciousness(mapConsciousness(getStr(vars, "phys.consciousness")))
            .build();
    }

    private ProtocolState extractProtocolState(Map<String, Object> vars) {
        return ProtocolState.builder()
            .currentAlgorithm(getStr(vars, "process.currentAlgorithm"))
            .cprCycleIndex(getInt(vars, "process.cprCycleIndex"))
            .rhythmCheckDue(Boolean.TRUE.equals(getBool(vars, "process.rhythmCheckDue")))
            .build();
    }

    private MonitorPresentation extractMonitorPresentation(Map<String, Object> vars) {
        return MonitorPresentation.builder()
            .rhythm(getStr(vars, "phys.rhythm"))
            .hrDisplay(getFloat(vars, "phys.heartRate"))
            .bpDisplay(getFloat(vars, "phys.systolicBp"))
            .spo2Display(getFloat(vars, "phys.spo2"))
            .trendLabels(List.of())
            .build();
    }

    private SystemStatus extractSystemStatus(CaseContext ctx) {
        Map<String, Object> vars = ctx.getVariables();
        return SystemStatus.builder()
            .paused(!ctx.isClockRunning())
            .deterministicDemoUsed(Boolean.TRUE.equals(getBool(vars, "system.deterministicDemo")))
            .assessmentInterruptionActive(
                Boolean.TRUE.equals(getBool(vars, "system.assessmentInterruptionActive")))
            .build();
    }

    // ========== 映射辅助 ==========

    private String mapRhythm(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase()) {
            case "svt", "narrow_complex_tachycardia" -> "svt";
            case "vf" -> "vf";
            case "vt" -> "vt";
            case "asystole" -> "asystole";
            case "pea" -> "pea";
            case "sinus" -> "sinus";
            default -> raw;
        };
    }

    private String mapPulse(Boolean hasPulse) {
        if (hasPulse == null) return null;
        return hasPulse ? "present" : "absent";
    }

    private String mapPerfusion(Boolean stable) {
        if (stable == null) return null;
        return stable ? "stable" : "unstable";
    }

    private String mapConsciousness(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase()) {
            case "alert" -> "alert";
            case "altered", "drowsy" -> "altered";
            case "unresponsive", "unconscious" -> "unresponsive";
            default -> raw;
        };
    }

    // ========== 类型安全读取 ==========

    private String getStr(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        return v != null ? v.toString() : null;
    }

    private Boolean getBool(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return Boolean.parseBoolean((String) v);
        return null;
    }

    private Integer getInt(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }

    private Float getFloat(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        return null;
    }
}
