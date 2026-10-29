package com.ruoyi.acls.tutor.manifest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * AttemptManifest 组装服务。
 *
 * <p>职责：在 {@link ManifestFreezer#freeze} 基础上，补齐 9 Schema 指纹与 manifestHash，
 * 返回完整的 AttemptManifest，由 tutor 侧监听器下发。</p>
 *
 * <p>9 Schema 定义来自 ACLS Tutor 契约 V1.0.0，路径：
 * {@code resources/config/acls/schema/}。
 * 排除 {@code _shared_schema.json}（共享片段）与
 * {@code presentation_mapping_schema.json}（表现层，归螺旋 3）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManifestService {

    /**
     * 9 Schema 文件列表。
     * TODO(契约 V1.0.0 §Manifest)：如契约的 9 Schema 与此不一致，直接改本 list。
     */
    private static final List<String> SCHEMA_FILES = List.of(
        "assessment_rules_schema.json",
        "clinical_rules_schema.json",
        "directional_response_rules_schema.json",
        "effect_mapping_schema.json",
        "evidence_manifest_schema.json",
        "fallback_paths_schema.json",
        "instructor_overrides_schema.json",
        "lesson_plan_schema.json",
        "scenario_beats_schema.json"
    );

    private static final String SCHEMA_BASE = "classpath:config/acls/schema/";

    private static final String DEFAULT_CASE_VERSION   = "1.0.0";
    private static final String DEFAULT_PLAN_ID        = "default";
    private static final String DEFAULT_PLAN_VERSION   = "1.0.0";
    private static final String DEFAULT_SCHEMA_VERSION = "1.0.0";

    private final ManifestFreezer freezer;
    private final ResourceLoader resourceLoader;

    /**
     * 构建并冻结 attempt manifest。
     *
     * @param emsSessionId ems 会话 ID
     * @param attemptId    attempt ID（当前与 sessionId 同值，未来可扩展）
     * @param caseId       病例 ID
     */
    public AttemptManifest buildAndFreeze(String emsSessionId,
                                          String attemptId,
                                          String caseId) {
        if (emsSessionId == null || caseId == null) {
            throw new IllegalArgumentException("emsSessionId / caseId 不能为空");
        }

        // 1. 基础冻结：5 个 case YAML 的 SHA-256 + reviewStatus + 时钟 / 序列
        AttemptManifest base = freezer.freeze(
            emsSessionId,
            caseId,
            DEFAULT_CASE_VERSION,
            DEFAULT_PLAN_ID,
            DEFAULT_PLAN_VERSION,
            DEFAULT_SCHEMA_VERSION,
            "SYSTEM");

        // 2. 9 Schema 指纹
        Map<String, String> schemaHashes = new LinkedHashMap<>();
        for (String fileName : SCHEMA_FILES) {
            String sha = sha256Of(SCHEMA_BASE + fileName);
            if (sha != null) {
                schemaHashes.put(fileName, sha);
            } else {
                log.warn("[Manifest] Schema 文件缺失或计算失败: {}", fileName);
            }
        }
        base.setSchemaHashes(schemaHashes);

        // 3. manifestHash = SHA-256(fileHashes + schemaHashes 的确定性拼接)
        base.setManifestHash(computeManifestHash(base));

        log.info("[Manifest] 构建完成: ems={}, case={}, files={}, schemas={}, manifestHash={}",
            emsSessionId, caseId,
            base.getFileHashes().size(), schemaHashes.size(),
            abbreviate(base.getManifestHash()));
        return base;
    }

    /**
     * 计算 manifestHash。按 key 排序，保证可重现。
     */
    private String computeManifestHash(AttemptManifest m) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> e : new TreeMap<>(m.getFileHashes()).entrySet()) {
                md.update((e.getKey() + "=" + e.getValue() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<String, String> e : new TreeMap<>(m.getSchemaHashes()).entrySet()) {
                md.update((e.getKey() + "=" + e.getValue() + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("[Manifest] manifestHash 计算失败: {}", e.getMessage());
            return null;
        }
    }

    private String sha256Of(String location) {
        try {
            Resource r = resourceLoader.getResource(location);
            if (!r.exists()) return null;
            try (InputStream in = r.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte b : digest) sb.append(String.format("%02x", b));
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("[Manifest] 计算哈希失败: {}, err={}", location, e.getMessage());
            return null;
        }
    }

    private static String abbreviate(String h) {
        if (h == null) return "<null>";
        return h.length() <= 8 ? h : h.substring(0, 8);
    }
}
