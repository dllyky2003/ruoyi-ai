package com.ruoyi.ems.room.protocol.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 一次 ACLS 尝试的最终评价报告（结构化事实，不含 LLM 文本）。
 *
 * <p>不占 serverSequence：与 ATTEMPT_MANIFEST 同类，是"结束声明"而非时序消息。</p>
 *
 * <p>LLM 教学评语走已有 REST {@code /ems/report/{sessionId}} 异步补，不进本报告。</p>
 */
@Data
@Builder
public class AttemptReport {

    // ===== 锚点 =====
    private String attemptId;
    private String emsSessionId;
    private String roomSessionId;
    private String caseId;
    private String manifestId;      // tutor 侧类型，ems 侧第一版为 null
    private String manifestHash;
    private String frozenAt;        // tutor 侧类型，ems 侧第一版为 null

    // ===== 序列终值 =====
    private long finalStateRevision;
    private long finalEventSequence;
    private long finalServerSequence;

    // ===== 时间 =====
    private double simulatedElapsedSeconds;         // 排除暂停
    private double assessmentRealElapsedSeconds;    // 包含暂停（ClockService 冻结值）

    // ===== 评价变量（evaluation.* 全量） =====
    private Map<String, Object> evaluationVars;

    // ===== 结构化事实 =====
    private List<Map<String, Object>> keyEvents;        // case.eventLog
    private List<Map<String, Object>> beatSelections;   // AssessmentQueryService
    private List<Map<String, Object>> interruptions;    // 第一版为空数组

    // ===== 最终状态 =====
    private ClinicalState finalClinicalState;
    private ProtocolState finalProtocolState;

    // ===== 元数据 =====
    private String generatedAt;         // ISO-8601
    private String generatorVersion;    // "1.0.0"
    private String terminationReason;   // 来自 end_session data.reason，可为 null
}
