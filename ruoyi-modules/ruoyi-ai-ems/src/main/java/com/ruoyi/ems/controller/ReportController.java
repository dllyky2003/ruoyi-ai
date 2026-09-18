package com.ruoyi.ems.controller;

import com.ruoyi.ems.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * 报告控制器
 */
@Slf4j
@RestController
@RequestMapping("/ems/report")

public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 生成详细报告
     */
    @Operation(summary = "生成详细报告",
        description = "根据会话ID生成详细的评语报告，包含学员表现、改进建议等",
        responses = {
            @ApiResponse(responseCode = "200", description = "生成成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "400", description = "会话不存在或未评估")
        })
    @GetMapping("/{sessionId}")
    public R<String> getReport(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @PathVariable String sessionId) {
        try {
            return R.ok(reportService.generateDetailedReport(sessionId));
        } catch (Exception e) {
            log.error("生成报告失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 生成对比报告
     */
    @Operation(summary = "生成对比报告",
        description = "对比多个学员的评估结果，生成横向对比报告",
        responses = {
            @ApiResponse(responseCode = "200", description = "生成成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "400", description = "参数错误")
        })
    @GetMapping("/compare")
    public R<String> getComparisonReport(
        @Parameter(description = "多个会话ID，用逗号分隔", required = true, example = "[\"abc123\",\"def456\"]")
        @RequestParam String[] sessionIds) {
        try {
            return R.ok(reportService.generateComparisonReport(sessionIds));
        } catch (Exception e) {
            log.error("生成对比报告失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 导出Markdown报告
     */
    @Operation(summary = "导出Markdown报告",
        description = "将评语报告导出为Markdown格式文件，方便保存和分享",
        responses = {
            @ApiResponse(responseCode = "200", description = "导出成功",
                content = @Content(mediaType = "text/markdown")),
            @ApiResponse(responseCode = "500", description = "导出失败")
        })
    @GetMapping("/{sessionId}/export/markdown")
    public ResponseEntity<String> exportMarkdown(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @PathVariable String sessionId) {
        try {
            String markdown = reportService.exportMarkdown(sessionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_MARKDOWN);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=report_" + sessionId + ".md");

            return ResponseEntity.ok()
                .headers(headers)
                .body(markdown);

        } catch (Exception e) {
            log.error("导出Markdown失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导出文本报告
     */
    @Operation(summary = "导出文本报告",
        description = "将评语报告导出为纯文本格式文件，方便打印或存档",
        responses = {
            @ApiResponse(responseCode = "200", description = "导出成功",
                content = @Content(mediaType = "text/plain")),
            @ApiResponse(responseCode = "500", description = "导出失败")
        })
    @GetMapping("/{sessionId}/export/txt")
    public ResponseEntity<String> exportTxt(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @PathVariable String sessionId) {
        try {
            String report = reportService.generateDetailedReport(sessionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=report_" + sessionId + ".txt");

            return ResponseEntity.ok()
                .headers(headers)
                .body(report);

        } catch (Exception e) {
            log.error("导出文本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
