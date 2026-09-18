package com.ruoyi.ems.controller;

import com.ruoyi.ems.domain.dto.ScoreResult;
import com.ruoyi.ems.domain.entity.EmsEvaluation;
import com.ruoyi.ems.engine.extraction.ProcessTranscriptor;
import com.ruoyi.ems.engine.scorer.ScoringEngine;
import com.ruoyi.ems.service.EvaluationService;
import com.ruoyi.ems.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;

/**
 * 评估控制器
 * 提供评估触发、结果查询、手动评分、智能体轨迹等接口
 */
@Slf4j
@RestController
@RequestMapping("/ems/evaluation")

// 类级别加密，所有接口统一加密（根据需要开启）
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final SessionService sessionService;
    private final ScoringEngine scoringEngine;
    private final ProcessTranscriptor extractionService;

    public EvaluationController(EvaluationService evaluationService,
                                SessionService sessionService,
                                ScoringEngine scoringEngine,
                                ProcessTranscriptor extractionService) {
        this.evaluationService = evaluationService;
        this.sessionService = sessionService;
        this.scoringEngine = scoringEngine;
        this.extractionService = extractionService;
    }

    /**
     * 触发最终评估
     */
    @Operation(summary = "触发最终评估",
        description = "对指定会话执行最终评估，包括多智能体评分、规则引擎和LLM报告生成",
        responses = {
            @ApiResponse(responseCode = "200", description = "评估成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "400", description = "参数错误（会话ID无效或未完成）"),
            @ApiResponse(responseCode = "404", description = "会话不存在"),
            @ApiResponse(responseCode = "500", description = "服务内部错误")
        })
    @PostMapping("/trigger/{sessionId}")
    public R<EmsEvaluation> triggerEvaluation(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @NotBlank(message = "会话ID不能为空")
        @PathVariable String sessionId) {
        try {
            EmsEvaluation evaluation = evaluationService.triggerFinalEvaluation(sessionId);
            return R.ok(evaluation);
        } catch (Exception e) {
            log.error("触发评估失败, sessionId: {}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取评估结果
     */
    @Operation(summary = "获取评估结果",
        description = "根据会话ID查询已完成的评估结果，包含总分、各维度得分、错误列表、评语等",
        responses = {
            @ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "404", description = "评估结果不存在或会话未评估")
        })
    @GetMapping("/{sessionId}")
    public R<EmsEvaluation> getEvaluation(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @NotBlank(message = "会话ID不能为空")
        @PathVariable String sessionId) {
        try {
            EmsEvaluation evaluation = evaluationService.getEvaluationBySessionId(sessionId);
            if (evaluation == null) {
                return R.fail("评估结果不存在，请先触发评估");
            }
            return R.ok(evaluation);
        } catch (Exception e) {
            log.error("获取评估结果失败, sessionId: {}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 手动触发评分（不触发多智能体，仅规则引擎）
     * 用于快速测试规则引擎逻辑，不依赖会话上下文
     */
    @Operation(summary = "手动触发评分（规则引擎）",
        description = "直接传入ASR转录文本，使用规则引擎进行评分（不依赖会话和多智能体）。适用于快速验证规则逻辑。",
        responses = {
            @ApiResponse(responseCode = "200", description = "评分成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "400", description = "转录文本为空或格式错误")
        })
    @PostMapping("/score/manual")
    public R<ScoreResult> manualScore(
        @Parameter(description = "ASR转录文本", required = true,
            schema = @Schema(example = "心率160，稳定，窄QRS", type = "string"))
        @NotBlank(message = "转录文本不能为空")
        @RequestBody String transcript) {
        try {
         //   TeamPerformance performance = extractionService.extract(transcript);
         //   ScoreResult result = scoringEngine.evaluate(performance);
            return R.ok(new ScoreResult());
        } catch (Exception e) {
            log.error("手动评分失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取多智能体诊断轨迹（教官专用）
     * 返回每个智能体的详细评分、推理过程、发现和建议
     */
    @Operation(summary = "获取多智能体诊断轨迹",
        description = "获取某个会话评估中，各个智能体的详细评分、推理过程和发现（教官专用）",
        responses = {
            @ApiResponse(responseCode = "200", description = "查询成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "404", description = "评估结果不存在或未完成多智能体评估")
        })
    @GetMapping("/{sessionId}/agent-trace")
    public R<Object> getAgentTrace(
        @Parameter(description = "会话ID", required = true, example = "abc123def456")
        @NotBlank(message = "会话ID不能为空")
        @PathVariable String sessionId) {
        try {
            EmsEvaluation evaluation = evaluationService.getEvaluationBySessionId(sessionId);
            if (evaluation == null) {
                return R.fail("评估结果不存在");
            }
            Object agentDetails = evaluation.getAgentDetails();
            if (agentDetails == null) {
                return R.fail("智能体轨迹数据为空，可能评估尚未完成");
            }
            return R.ok(agentDetails);
        } catch (Exception e) {
            log.error("获取智能体轨迹失败, sessionId: {}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }
}
