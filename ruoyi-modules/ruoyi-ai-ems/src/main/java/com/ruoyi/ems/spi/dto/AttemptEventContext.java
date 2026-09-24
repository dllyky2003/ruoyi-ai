package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 尝试事件上下文（SPI DTO）。
 *
 * <p>由 ems 在状态迁移提交后构建，传给 AttemptEventSink。</p>
 *
 * <p>字段命名与 TransitionContext 保持一致，便于 tutor 侧复用。</p>
 */
@Data
public class AttemptEventContext {

    /** 会话 id（= attemptId） */
    private String sessionId;

    /** 案例类型 code，例如 stable_narrow */
    private String caseType;

    /** 迁移前状态 */
    private String fromState;

    /** 迁移后状态 */
    private String toState;

    /** 触发的学员意图 code（可能为空，表示变量驱动） */
    private String intentCode;

    /** 当前权威状态版本号（ems 侧 version，作为 stateRevision 的近似） */
    private Long stateRevision;

    /** 事件序号（若 ems 侧已维护；否则为 null，tutor 侧可自行推进） */
    private Long eventSequence;

    /** 本次迁移命中的临床规则 ID（若 tutor 提供了 ValidationResult） */
    private String primaryRuleId;

    /** 本次迁移已解析授权（若 tutor 提供了 ValidationResult） */
    private List<EffectSpec> allowedEffects;

    /** 迁移后的变量快照（只读用途） */
    private Map<String, Object> variables = new HashMap<>();

    /** 当前训练模式（instructor_demo / free_training 等；ems 侧先传 null） */
    private String trainingMode;
}
