package com.ruoyi.acls.tutor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估中断记录。
 *
 * <p>语义（对应快照 D4）：导师覆盖（override）不扣分，但会触发一次评估中断；
 * 中断期间或受影响的评估项被标记为“不可评估”，不计入扣分，也不计入通过判定。</p>
 *
 * <p>时间基准（对应快照 S3）：{@link #timeBasis} 必填；
 * 当 {@code timeBasis = REAL} 时，必须携带 {@link #realTimeRationale} 且
 * {@link #pauseSensitive} = true，用于标识该中断按评估真实时间（墙上时间）计量，
 * 暂停期间仍累计。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentInterruption {

    /** 中断 ID，一次中断生成一个。 */
    private String interruptionId;

    /** 关联会话。 */
    private String sessionId;

    /** 关联病例。 */
    private String caseId;

    /** 中断原因。 */
    private InterruptionReason reason;

    /** 关联来源引用，例如 override ID / rule ID / beat ID。 */
    private String sourceRef;

    /** 来源类型，例如 INSTRUCTOR_OVERRIDE / RULE / BEAT。 */
    private String sourceType;

    /**
     * 时间基准，必填。
     * <ul>
     *   <li>{@link TimeBasis#SIMULATED}：按模拟时间计量（排除暂停）</li>
     *   <li>{@link TimeBasis#REAL}：按评估真实时间计量（包含暂停）</li>
     *   <li>{@link TimeBasis#BOTH}：双口径都需要记录</li>
     * </ul>
     */
    private TimeBasis timeBasis;

    /** 当中断发生时，已过去的模拟秒数（排除暂停）。 */
    private double simulatedElapsedAtStart;

    /** 当中断结束时，已过去的模拟秒数（排除暂停）；未结束为 0。 */
    private double simulatedElapsedAtEnd;

    /** 当中断发生时，已过去的评估真实秒数（包含暂停）。 */
    private double assessmentRealElapsedAtStart;

    /** 当中断结束时，已过去的评估真实秒数（包含暂停）；未结束为 0。 */
    private double assessmentRealElapsedAtEnd;

    /**
     * 当 {@link #timeBasis} 为 {@link TimeBasis#REAL} 或 {@link TimeBasis#BOTH} 时必填，
     * 说明为什么该中断必须按评估真实时间计量（例如暂停敏感性）。
     */
    private String realTimeRationale;

    /** 该中断是否暂停敏感；当 timeBasis 含 REAL 时必须为 true。 */
    private boolean pauseSensitive;

    /**
     * 受影响项 -> 不可评估标记。
     * key 为评估项 ID（assessmentTag / criticalAction / ruleId 等），
     * value 为 reason（例如 "INSTRUCTOR_OVERRIDE" / "CLOCK_PAUSED"）。
     */
    @Builder.Default
    private Map<String, String> unevaluableItems = new LinkedHashMap<>();

    /** 中断期间额外产生的备注，按顺序保留。 */
    @Builder.Default
    private List<String> notes = new ArrayList<>();

    /** 中断发生时刻（系统墙上时间，用于审计）。 */
    private LocalDateTime startedAt;

    /** 中断结束时刻（系统墙上时间）；未结束为 null。 */
    private LocalDateTime endedAt;

    /** 是否仍在进行中。 */
    @Builder.Default
    private boolean ongoing = true;

    /** 快速判断是否为“导师覆盖”触发的中断（D4 主路径）。 */
    public boolean isFromInstructorOverride() {
        return reason == InterruptionReason.INSTRUCTOR_OVERRIDE;
    }

    /**
     * 自检：timeBasis=REAL/BOTH 时，必须带 realTimeRationale 且 pauseSensitive=true（S3）。
     */
    public boolean isTimeBasisConsistent() {
        if (timeBasis == TimeBasis.REAL || timeBasis == TimeBasis.BOTH) {
            return pauseSensitive
                && realTimeRationale != null
                && !realTimeRationale.isBlank();
        }
        return true;
    }

    /** 中断原因枚举。 */
    public enum InterruptionReason {
        /** 导师覆盖（D4）。 */
        INSTRUCTOR_OVERRIDE,
        /** 时钟暂停期间产生的不可评估窗口。 */
        CLOCK_PAUSED,
        /** 系统/规则触发的中断（例如 fallback escalation）。 */
        SYSTEM_TRIGGERED,
        /** 其他显式中断原因。 */
        OTHER
    }

    /** 时间基准枚举（S3）。 */
    public enum TimeBasis {
        /** 模拟时间，排除暂停。 */
        SIMULATED,
        /** 评估真实时间，包含暂停。 */
        REAL,
        /** 双口径都记录。 */
        BOTH
    }
}
