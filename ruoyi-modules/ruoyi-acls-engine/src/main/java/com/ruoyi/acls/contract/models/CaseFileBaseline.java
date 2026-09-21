package com.ruoyi.acls.contract.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 案例文件基线（契约侧）。
 *
 * <p>契约构建时对案例 YAML 做一次快照：相对路径 → sha256 + 审核状态。
 * 作为运行时 {@code AttemptManifest} 冻结的“应然值”参照。</p>
 *
 * <p>本类只承载静态可追溯信息，不含 session / attempt / 时钟 / 事件序列；
 * 运行时冻结由 ManifestFreezer 在 AttemptManifest 上完成。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseFileBaseline {

    /** 案例 ID。 */
    private String caseId;

    /** 相对路径 -> 文件基线条目；键与 AttemptManifest.fileHashes 对齐。 */
    @Builder.Default
    private Map<String, FileEntry> files = new LinkedHashMap<>();

    /** 基线采集时刻（契约构建时刻）。 */
    private LocalDateTime capturedAt;

    /** 是否所有预期文件都存在。 */
    @Builder.Default
    private boolean complete = false;

    /** 是否存在审核状态异常（非 pending / approved）。 */
    @Builder.Default
    private boolean auditBlocked = false;

    /**
     * 单文件基线条目。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileEntry {
        /** 相对路径，例如 config/acls/cases/stable_narrow/clinical_rules.yaml。 */
        private String relativePath;

        /** 构建时 sha256（64 位小写 hex）；文件不存在为 null。 */
        private String sha256;

        /** 审核状态。阶段 3 占位 pending；approved 为正式通过。 */
        private String reviewStatus;

        /** 文件是否存在。 */
        private boolean exists;
    }
}
