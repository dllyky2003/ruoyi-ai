package com.ruoyi.ems.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契约 §12 —— 审计写入（开发阶段 JSONL 实现）。
 *
 * <p>每个 attempt 一个 {@code {audit.dir}/{attemptId}.jsonl}，一行一条 JSON。</p>
 *
 * <p><b>为什么用 JSONL：</b></p>
 * <ul>
 *   <li>append-only，天然符合审计不可变语义</li>
 *   <li>不需要建表，开发阶段零依赖</li>
 *   <li>后续迁 DB 只需读 JSONL 批量 insert</li>
 * </ul>
 *
 * <p><b>阶段 3 评价报告（2026-09-25）</b>：新增 {@link #writeReport}，
 * 报告为覆盖式 JSON 文件 {@code {audit.dir}/{attemptId}.report.json}，
 * 与 JSONL 事件流分离。</p>
 */
@Slf4j
@Service
public class AttemptLogService {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;
    private final Path auditDir;

    /** 每 attempt 一把锁，避免并发 append 交错 */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public AttemptLogService(ObjectMapper objectMapper,
                             @Value("${ems.audit.dir:./data/audit}") String auditDir) {
        this.objectMapper = objectMapper;
        this.auditDir = Paths.get(auditDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.auditDir);
            log.info("📂 [OVR-3] 审计目录: {}", this.auditDir);
        } catch (IOException e) {
            log.error("🚨 [OVR-3] 无法创建审计目录: {}", this.auditDir, e);
        }
    }

    /**
     * 记录导师覆盖事件（OVR-3）。
     */
    public void recordOverride(String attemptId,
                               String roomSessionId,
                               String emsSessionId,
                               String overrideId,
                               String effectId,
                               String reason,
                               Long stateRevision,
                               Object proposedEffect) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventType", AuditEventType.TUTOR_OVERRIDE.name());
        row.put("attemptId", attemptId);
        row.put("roomSessionId", roomSessionId);
        row.put("emsSessionId", emsSessionId);
        row.put("action", overrideId);
        row.put("stateRevision", stateRevision);
        row.put("createBy", "TUTOR");
        row.put("createTime", LocalDateTime.now().format(TS_FMT));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("effectId", effectId);
        detail.put("reason", reason);
        detail.put("proposedEffect", proposedEffect);
        row.put("detail", detail);

        append(attemptId, row);
    }

    /**
     * ★ 阶段 3 新增：写最终评价报告（覆盖式）。
     *
     * <p>与 JSONL 事件流分离，落在同目录。路径：{@code {audit.dir}/{attemptId}.report.json}。
     * 覆盖而非追加，因为一个 attempt 只有一份最终报告。</p>
     */
    public void writeReport(String attemptId, Object report) {
        if (attemptId == null || attemptId.isBlank() || report == null) return;
        Path file = auditDir.resolve(attemptId + ".report.json");
        Object lock = locks.computeIfAbsent(attemptId, k -> new Object());

        synchronized (lock) {
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(report);
                Files.writeString(
                    file,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                );
                log.info("📋 [Report] 报告落盘: file={}, bytes={}",
                    file.getFileName(), json.length());
            } catch (IOException e) {
                log.error("🚨 [Report] 报告写入失败: attemptId={}", attemptId, e);
            }
        }
    }

    private void append(String attemptId, Map<String, Object> row) {
        Path file = auditDir.resolve(attemptId + ".jsonl");
        Object lock = locks.computeIfAbsent(attemptId, k -> new Object());

        synchronized (lock) {
            try {
                String line = objectMapper.writeValueAsString(row) + System.lineSeparator();
                Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
                log.info("📝 [OVR-3] 审计写入: file={}, eventType={}, action={}, rev={}",
                    file.getFileName(), row.get("eventType"),
                    row.get("action"), row.get("stateRevision"));
            } catch (IOException e) {
                log.error("🚨 [OVR-3] 审计写入失败: attemptId={}", attemptId, e);
            }
        }
    }
}
