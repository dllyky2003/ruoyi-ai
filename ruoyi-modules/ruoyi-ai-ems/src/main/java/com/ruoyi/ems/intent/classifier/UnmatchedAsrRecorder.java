package com.ruoyi.ems.intent.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ruoyi.ems.context.ClassificationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 分类失败 ASR 记录器（双通道，均为合法 JSON）。
 *
 * <p>同一份内容写两个文件：
 * <ul>
 *   <li>{@code *.jsonl} —— <b>机器读</b>。单行 JSON，标准 JSONL 格式，jq / Python / Spark 逐行解析。</li>
 *   <li>{@code *.log}   —— <b>人读</b>。多行 pretty JSON，每条记录之间空行分隔，IDE / less 阅读友好。
 *       文件整体是"JSON 值序列"（多个独立 JSON 值），每个块自身是合法 JSON。</li>
 * </ul>
 *
 * <p><b>两文件都是严格合法 JSON</b>：字符串内部的换行、引号保持 JSON 规范转义形态
 * （{@code \n}、{@code \"}），不做任何"展开"处理。
 *
 * <p><b>stage 说明：</b>
 * <ul>
 *   <li>{@code ALL_FAILED}（当前主用）：所有分类器（级联 + LLM + 兜底）均失败。
 *       由 {@code IntentClassificationService} 在最终失败时统一调用，一条 ASR 只产生一条记录，
 *       {@code reason} 字段内汇总各阶段的诊断。</li>
 *   <li>{@code CASCADE_FAILED}（历史兼容）：级联前三层全部未命中。当前链路已不调用，
 *       保留常量与方法以免破坏其它潜在引用。</li>
 *   <li>{@code TWO_STAGE_FAILED}（历史兼容）：两级 LLM 分类器内部失败。当前链路已不调用。</li>
 * </ul>
 *
 * <p>路径配置：
 * <ul>
 *   <li>{@code ems.unmatched-asr.file}        —— jsonl 路径（默认 logs/unmatched-asr.jsonl）</li>
 *   <li>{@code ems.unmatched-asr.human-file}  —— log  路径（默认从 jsonl 推导：.jsonl → .log）</li>
 * </ul>
 */
@Slf4j
@Component
public class UnmatchedAsrRecorder {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // ============ stage 常量 ============
    /** ★ 主用：所有分类器（级联 + LLM + 兜底）均失败，由 IntentClassificationService 统一记录 */
    public static final String STAGE_ALL_FAILED = "ALL_FAILED";

    /** 历史兼容：级联前三层全部未命中 */
    public static final String STAGE_CASCADE_FAILED = "CASCADE_FAILED";

    /** 历史兼容：两级 LLM 分类器内部失败 */
    public static final String STAGE_TWO_STAGE_FAILED = "TWO_STAGE_FAILED";

    /** 每条 pretty 记录之间的空行数 */
    private static final int BLANK_LINES_BETWEEN = 1;

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 紧凑单行 writer（写 jsonl） */
    private final ObjectWriter compactWriter = objectMapper.writer();
    /** 多行 pretty writer（写 log） */
    private final ObjectWriter prettyWriter = objectMapper.writerWithDefaultPrettyPrinter();

    private final Path jsonlPath;
    private final Path humanPath;
    private final ReentrantLock writeLock = new ReentrantLock();

    public UnmatchedAsrRecorder(
        @Value("${ems.unmatched-asr.file:logs/unmatched-asr.jsonl}") String file,
        @Value("${ems.unmatched-asr.human-file:}") String humanFile) {

        this.jsonlPath = Paths.get(file).toAbsolutePath().normalize();

        if (humanFile == null || humanFile.isEmpty()) {
            String s = this.jsonlPath.toString();
            if (s.endsWith(".jsonl")) {
                this.humanPath = Paths.get(s.substring(0, s.length() - ".jsonl".length()) + ".log");
            } else {
                this.humanPath = Paths.get(s + ".log");
            }
        } else {
            this.humanPath = Paths.get(humanFile).toAbsolutePath().normalize();
        }

        try {
            Path p1 = this.jsonlPath.getParent();
            if (p1 != null) Files.createDirectories(p1);
            Path p2 = this.humanPath.getParent();
            if (p2 != null) Files.createDirectories(p2);
        } catch (IOException e) {
            log.warn("无法创建分类失败记录目录", e);
        }

        log.info("UnmatchedAsrRecorder 初始化完成");
        log.info("  jsonl (机器读): {}", this.jsonlPath);
        log.info("  log   (人读):   {}", this.humanPath);
    }

    // =================================================================
    //  核心记录方法
    // =================================================================

    /**
     * 记录一条分类失败记录（带 LLM 原始响应）。
     *
     * @param asrText     原始 ASR 文本
     * @param context     分类上下文（可为 null）
     * @param stage       失败阶段，使用 {@code STAGE_*} 常量
     * @param reason      失败原因（可多行；写入文件时换行会被 JSON 规范转义）
     * @param actType     推断/最终的语用类型（可为 null）
     * @param llmResponse LLM 返回的原始响应（可为 null）
     */
    public void record(String asrText, ClassificationContext context,
                       String stage, String reason, String actType, String llmResponse) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return;
        }

        // ---------- 1. 构造记录（两个通道共用） ----------
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("ts", LocalDateTime.now().format(TS_FMT));
        rec.put("stage", stage);
        rec.put("asrText", asrText);
        if (context != null) {
            rec.put("sessionId", context.getSessionId());
            rec.put("caseId", context.getCaseId());
            rec.put("currentState", context.getCurrentState());
            rec.put("lastIntent", context.getLastIntent());
        }
        rec.put("actType", actType);
        rec.put("reason", reason);
        rec.put("llmResponse", llmResponse);

        // ---------- 2. 序列化（单行 + 多行） ----------
        String jsonLine;
        String prettyBlock;
        try {
            jsonLine = compactWriter.writeValueAsString(rec);
            prettyBlock = prettyWriter.writeValueAsString(rec);
        } catch (Exception e) {
            log.warn("序列化分类失败记录失败: {}", asrText, e);
            return;
        }

        // ---------- 3. 双写（一把锁保一致） ----------
        writeLock.lock();
        try {
            Files.writeString(jsonlPath,
                jsonLine + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);

            Files.writeString(humanPath,
                prettyBlock + System.lineSeparator().repeat(BLANK_LINES_BETWEEN + 1),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("写入分类失败记录失败: jsonl={}, human={}, text={}",
                jsonlPath, humanPath, asrText, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** 4 参数版本（不带 LLM 响应）。 */
    public void record(String asrText, ClassificationContext context,
                       String stage, String reason, String actType) {
        record(asrText, context, stage, reason, actType, null);
    }

    // =================================================================
    //  便捷方法
    // =================================================================

    /**
     * ★ 主用：记录"所有分类器均失败"。
     *
     * <p>由 {@code IntentClassificationService.recordUnifiedFailure(...)} 在最终失败时调用，
     * {@code reason} 应为汇总后的多行文本（包含级联 / LLM / 兜底三个阶段诊断），
     * {@code llmResponse} 为 LLM 分类器的原始响应（可为 null）。
     */
    public void recordAllFailed(String asrText, ClassificationContext context,
                                String reason, String actType, String llmResponse) {
        record(asrText, context, STAGE_ALL_FAILED, reason, actType, llmResponse);
    }

    /** 历史兼容：记录 TwoStage 失败（带 LLM 响应）。 */
    public void recordTwoStageFailure(String asrText, ClassificationContext context,
                                      String reason, String actType, String llmResponse) {
        record(asrText, context, STAGE_TWO_STAGE_FAILED, reason, actType, llmResponse);
    }

    /** 历史兼容：记录 TwoStage 失败（不带 LLM 响应）。 */
    public void recordTwoStageFailure(String asrText, ClassificationContext context,
                                      String reason, String actType) {
        record(asrText, context, STAGE_TWO_STAGE_FAILED, reason, actType, null);
    }

    /** 历史兼容：记录级联失败。 */
    public void recordCascadeFailure(String asrText, ClassificationContext context,
                                     String reason, String actType) {
        record(asrText, context, STAGE_CASCADE_FAILED, reason, actType, null);
    }
}
