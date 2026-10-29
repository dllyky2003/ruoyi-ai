package com.ruoyi.acls.tutor.manifest;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 尝试清单：一次 ACLS 训练尝试的版本冻结与可追溯锚点。
 *
 * <p>只记录权威版本、哈希、审核状态与双序列/双时钟快照，不承载业务规则。
 * 由 ManifestFreezer 在尝试冻结时生成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptManifest {

    /** 清单 ID，一次冻结生成一个。 */
    private String manifestId;

    /** 会话 ID。 */
    private String sessionId;

    /** 尝试 ID；若当前会话内一次 attempt 一冻结，可与 sessionId 同值，后续可扩展多 attempt。 */
    private String attemptId;

    /** 病例 ID。 */
    private String caseId;

    /** 病例版本。 */
    private String caseVersion;

    /** 教案包 ID。 */
    private String planId;

    /** 教案包版本。 */
    private String planVersion;

    /** Schema 版本或清单版本。 */
    private String schemaVersion;

    /**
     * 文件相对路径 -> sha256。
     * 覆盖 clinical_rules / lesson_plan / scenario_beats / fallback_paths / instructor_overrides / schema 等。
     */
    @Builder.Default
    private Map<String, String> fileHashes = new LinkedHashMap<>();

    /**
     * 文件或资源键 -> review_status。
     * 例如 clinical_rules.yaml / lesson_plan.yaml / scenario_beats.yaml。
     */
    @Builder.Default
    private Map<String, String> reviewStatus = new LinkedHashMap<>();
    /**
     * Schema 文件 -> sha256。
     * 键为纯文件名（如 clinical_rules_schema.json），
     * 与 fileHashes 的"相对路径"键空间分离。
     * 由 ManifestService 在冻结后填充。
     */
    @Builder.Default
    private Map<String, String> schemaHashes = new LinkedHashMap<>();

    /**
     * Manifest 总指纹：fileHashes + schemaHashes 的确定性 SHA-256。
     * 用于"报告与 Manifest 冻结一致"的对照。
     * 由 ManifestService 在冻结后填充。
     */
    private String manifestHash;
    /**
     * 权威状态版本：仅状态机迁移、生理权威变化等权威变更时递增。
     * 与 CaseContext.version 的变量版本语义分离，不要混用。
     */
    private long stateRevision;

    /**
     * 事件序列：每处理一个事件递增，用于事件溯源。
     * 与 stateRevision 分离：eventSequence 每事件递增，stateRevision 仅权威变更递增。
     */
    private long eventSequence;

    /**
     * 模拟时间：排除暂停。
     * 来自 CaseContext.elapsedSeconds / ClockService.elapsedSeconds。
     */
    private double simulatedElapsedSeconds;

    /**
     * 评估真实时间：墙上时间口径，用于暂停敏感项。
     * 阶段 3 由 ClockService 增补 assessment_real_elapsed。
     */
    private double assessmentRealElapsedSeconds;


    private String  frozenAt;

    /** 冻结人/系统，例如 SYSTEM / INSTRUCTOR。 */
    private String frozenBy;

    /** 是否已冻结。 */
    @Builder.Default
    private boolean frozen = false;

    /** 备注。 */
    private String notes;

    /**
     * 最小完整性检查：冻结清单必须带齐关键版本与哈希。
     */
    public boolean isComplete() {
        return frozen
            && manifestId != null
            && caseId != null
            && caseVersion != null
            && planId != null
            && planVersion != null
            && schemaVersion != null
            && fileHashes != null
            && !fileHashes.isEmpty()
            && schemaHashes != null
            && !schemaHashes.isEmpty()
            && manifestHash != null;
    }
}
