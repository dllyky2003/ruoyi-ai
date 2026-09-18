package com.ruoyi.ems.controller;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.domain.entity.EmsSession;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.npc.TeamSize;
import com.ruoyi.ems.room.dto.JoinRequest;
import com.ruoyi.ems.room.dto.JoinResponse;
import com.ruoyi.ems.room.dto.RoomDetail;
import com.ruoyi.ems.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


/**
 * 演练会话控制器
 * <p>
 * 提供会话的创建、结束、状态查询、上下文获取、转写记录查询以及流程图导出等 REST API。
 * 所有接口均以 `/ems/session` 为前缀，统一返回 R&lt;T&gt; 格式。
 * </p>
 *
 * @author system
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ems/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final CaseIntentDefinitionsLoader intentRegistry;

    // ==================== 会话生命周期 ====================

    /**
     * 创建并开始一个新的演练会话。
     *
     * @param scenarioId  场景 ID（案例标识），不可为空
     * @param traineeName 学员姓名（可选）
     * @param mode        模式：EXAM（考核）/ TEACH（教学），默认 EXAM
     * @return 包含新创建会话对象的统一响应 R
     */
    @Operation(summary = "创建并开始会话",
        description = "根据场景ID创建新的演练会话，并自动启动状态机",
        responses = {
            @ApiResponse(responseCode = "200", description = "创建成功",
                content = @Content(schema = @Schema(implementation = R.class))),
            @ApiResponse(responseCode = "400", description = "场景不存在或参数错误"),
            @ApiResponse(responseCode = "500", description = "服务内部错误")
        })
    @PostMapping("/start")
    public R<EmsSession> startSession(
        @Parameter(description = "场景ID（案例标识）", required = true, example = "stable_narrow")
        @RequestParam String scenarioId,
        @Parameter(description = "学员姓名", example = "张三")
        @RequestParam(required = false) String traineeName,
        @Parameter(description = "模式：EXAM（考核）/ TEACH（教学）", example = "EXAM")
        @RequestParam(defaultValue = "EXAM") String mode) {
        try {
            EmsSession session = sessionService.createSession(scenarioId, traineeName, mode, TeamSize.defaultSize());
            sessionService.startSession(session);
            return R.ok(session);
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 结束指定的会话。
     *
     * @param sessionId 会话 ID
     * @return 成功返回 R.ok()
     */
    @Operation(summary = "结束会话",
        description = "结束指定的演练会话，更新状态为COMPLETED")
    @PostMapping("/end/{sessionId}")
    public R<Void> endSession(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            sessionService.endSession(sessionId);
            return R.ok();
        } catch (Exception e) {
            log.error("结束会话失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 会话数据查询 ====================

    /**
     * 获取会话的完整上下文数据（Redis 中存储的所有键值对）。
     *
     * @param sessionId 会话 ID
     * @return 包含所有上下文数据的 Map
     */
    @Operation(summary = "获取会话上下文",
        description = "获取该会话在Redis中存储的所有上下文数据")
    @GetMapping("/{sessionId}/context")
    public R<Map<Object, Object>> getContext(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            return R.ok(sessionService.getSessionContext(sessionId));
        } catch (Exception e) {
            log.error("获取上下文失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取该会话累积的 ASR 语音转写文本。
     *
     * @param sessionId 会话 ID
     * @return 转写文本字符串
     */
    @Operation(summary = "获取转写文本",
        description = "获取该会话累积的ASR语音转写文本")
    @GetMapping("/{sessionId}/transcript")
    public R<String> getTranscript(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            return R.ok(sessionService.getTranscript(sessionId));
        } catch (Exception e) {
            log.error("获取转写文本失败", e);
            return R.fail(e.getMessage());
        }
    }
    @GetMapping("/stateInfo")
    public R<Map<String, Object>> getStateInfo(
        @RequestParam(required = false) String sessionId,
        @RequestParam(required = false) String caseType) {
        try {
            Map<String, Object> result = sessionService.getStateInfo(sessionId, caseType);
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取状态信息失败", e);
            return R.fail(e.getMessage());
        }
    }


    /**
     * 获取会话上下文的所有变量及值。
     *
     * @param sessionId 会话 ID
     * @return 变量 Map，key 为变量名，value 为变量值
     */
    @Operation(summary = "获取所有变量",
        description = "获取当前会话上下文中的所有变量及其值")
    @GetMapping("/{sessionId}/variables")
    public R<Map<String, Object>> getVariables(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            Map<String, Object> variables = sessionService.getModifiedVariables(sessionId);
            return R.ok(variables);
        } catch (RuntimeException e) {
            log.warn("获取变量失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取变量异常", e);
            return R.fail("获取变量失败: " + e.getMessage());
        }
    }
    @Operation(summary = "获取案例默认变量",
        description = "根据案例类型ID获取所有变量的默认值，用于会话创建前的预览")
    @GetMapping("/variables/default")
    public R<Map<String, Object>> getDefaultVariables(
        @Parameter(description = "案例类型ID", required = true)
        @RequestParam String caseType) {
        try {
            Map<String, Object> defaults = sessionService.getDefaultVariables(caseType);
            return R.ok(defaults);
        } catch (Exception e) {
            log.error("获取默认变量失败", e);
            return R.fail(e.getMessage());
        }
    }
    /**
     * 接收客户端主动提交的转写文本（用于模拟 ASR 输入）。
     *
     * @param sessionId  会话 ID
     * @param transcript 转写文本内容
     * @return 成功返回 R.ok()
     */
    @Operation(summary = "提交转写文本",
        description = "接收客户端主动提交的转写文本，用于模拟ASR输入")
    @PostMapping("/{sessionId}/receiveTranscript")
    public R<Void> receiveTranscript(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId,
        @Parameter(description = "转写文本内容", required = true)
        @RequestBody String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            return R.fail("转写文本不能为空");
        }
        try {
            // TODO: 将转写文本交给业务层处理（如发送到消息队列或直接处理）
            // 例如：sessionService.processTranscript(sessionId, transcript);
            log.info("收到转写文本: sessionId={}, text={}", sessionId, transcript);
            return R.ok();
        } catch (Exception e) {
            log.error("处理转写文本失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 状态与流程图 ====================

    /**
     * 获取会话的详细状态总览（包含完整执行路径）。
     *
     * @param sessionId 会话 ID
     * @return 包含 status, isCompleted, currentState, history 的 Map
     */
    @Operation(summary = "获取会话状态总览",
        description = "获取会话的生命周期状态、是否完成、当前状态节点及历史转移记录")
    @GetMapping("/{sessionId}/status")
    public R<Map<String, Object>> getSessionStatus(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            EmsSession session = sessionService.getSessionById(sessionId);
            if (session == null) {
                return R.fail("会话不存在: " + sessionId);
            }

            String currentState = sessionService.getCurrentState(sessionId);
            if (currentState == null) {
                currentState = "UNKNOWN";
            }

            boolean isCompleted = "COMPLETED".equals(session.getStatus());
            List<Map<String, Object>> history = sessionService.getTransitionHistory(sessionId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", sessionId);
            result.put("status", session.getStatus());
            result.put("isCompleted", isCompleted);
            result.put("currentState", currentState);
            result.put("history", history != null ? history : Collections.emptyList());

            return R.ok(result);
        } catch (Exception e) {
            log.error("获取会话状态失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 仅获取当前状态机节点（轻量级查询）。
     *
     * @param sessionId 会话 ID
     * @return 当前状态 ID
     */
    @Operation(summary = "获取当前状态",
        description = "仅获取当前状态机节点，轻量级查询")
    @GetMapping("/{sessionId}/state")
    public R<String> getCurrentState(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            String state = sessionService.getCurrentState(sessionId);
            return R.ok(state != null ? state : "UNKNOWN");
        } catch (Exception e) {
            log.error("获取当前状态失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 仅获取会话的转移历史（执行路径）。
     *
     * @param sessionId 会话 ID
     * @return 转移记录列表
     */
    @Operation(summary = "获取转移历史",
        description = "获取会话的所有状态转移记录（执行路径）")
    @GetMapping("/{sessionId}/history")
    public R<List<Map<String, Object>>> getTransitionHistory(
        @Parameter(description = "会话ID", required = true)
        @PathVariable String sessionId) {
        try {
            List<Map<String, Object>> history = sessionService.getTransitionHistory(sessionId);
            return R.ok(history != null ? history : Collections.emptyList());
        } catch (Exception e) {
            log.error("获取转移历史失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取当前会话的实时流程图（Mermaid 格式）。
     *
     * @param sessionId 会话 ID
     * @return Mermaid 代码字符串
     */
    @Operation(summary = "获取实时流程图",
        description = "根据会话当前状态，动态生成 Mermaid 流程图代码")
    @GetMapping("/liveMermaid")
    public R<String> getLiveMermaid(
        @Parameter(description = "会话ID", required = true)
        @RequestParam String sessionId) {
        try {
            return R.ok(sessionService.getLiveMermaid(sessionId));
        } catch (Exception e) {
            log.error("获取实时流程图失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 获取指定案例的静态流程图（Mermaid 格式）。
     *
     * @param caseId 案例 ID
     * @return Mermaid 代码字符串
     */
    @Operation(summary = "获取静态流程图",
        description = "根据案例 ID 从配置中读取预定义的流程图代码")
    @GetMapping("/mermaid")
    public R<String> getMermaidCode(
        @Parameter(description = "案例ID", required = true, example = "stable_narrow")
        @RequestParam(required = true) String caseId) {
        try {
            return R.ok(sessionService.getCaseMermaid(caseId,false));
        } catch (Exception e) {
            log.error("获取静态流程图失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 意图定义 ====================

    /**
     * 获取指定案例的意图定义（子领域 + 意图列表）。
     * <p>
     * 从配置中读取原始定义，并动态处理示例：
     * <ul>
     *   <li>过滤掉以“【负例】”开头的示例</li>
     *   <li>将示例中的 [NUM] 占位符替换为 1~199 的随机整数</li>
     * </ul>
     * </p>
     *
     * @param caseId 案例 ID（如 "stable_narrow"）
     * @return 子领域列表
     */
    @Operation(summary = "获取意图定义",
        description = "获取指定案例的意图定义，包含子领域及意图列表，示例中的 [NUM] 会被替换为随机数")
    @GetMapping("/definitions")
    public R<List<SubDomain>> getDefinitions(
        @Parameter(description = "案例ID", required = true, example = "stable_narrow")
        @RequestParam(required = true) String caseId) {
        try {
            List<SubDomain> original = intentRegistry.getCaseDefinitions(caseId);
            if (original == null || original.isEmpty()) {
                return R.fail("未找到案例的意图定义: " + caseId);
            }

            // 深拷贝并处理示例
            List<SubDomain> result = deepCopyAndProcessDefinitions(original);
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取意图定义失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 深拷贝意图定义，并处理示例（过滤负例、替换 [NUM]）。
     */
    private List<SubDomain> deepCopyAndProcessDefinitions(List<SubDomain> original) {
        List<SubDomain> result = new ArrayList<>();
        for (SubDomain sub : original) {
            SubDomain clonedSub = new SubDomain();
            // 使用 BeanUtils 复制基本字段
            BeanUtils.copyProperties(sub, clonedSub);

            // 复制并处理 Intent 列表
            List<IntentDefinition> processedIntents = new ArrayList<>();
            if (sub.getIntents() != null) {
                for (IntentDefinition intent : sub.getIntents()) {
                    IntentDefinition clonedIntent = new IntentDefinition();
                    BeanUtils.copyProperties(intent, clonedIntent);

                    // 处理 examples
                    if (intent.getExamples() != null) {
                        List<String> processedExamples = new ArrayList<>();
                        for (String example : intent.getExamples()) {
                            // 1. 过滤掉以“【负例】”开头的示例
                            if (example != null && example.trim().startsWith("【负例")) {
                                continue;
                            }
                            // 2. 将 [NUM] 替换为 1~199 的随机整数
                            String replaced = example.replaceAll("\\[NUM\\]",
                                String.valueOf(ThreadLocalRandom.current().nextInt(1, 200)));
                            processedExamples.add(replaced);
                        }
                        clonedIntent.setExamples(processedExamples);
                    }
                    processedIntents.add(clonedIntent);
                }
            }
            clonedSub.setIntents(processedIntents);
            result.add(clonedSub);
        }
        return result;
    }


    /**
     * 获取指定节点的所有进入条件，并解析当前会话变量值
     *
     * @param sessionId 会话ID
     * @param nodeId    节点ID（状态编码）
     * @return 包含节点进入条件列表及变量快照的 Map
     */
    @Operation(summary = "获取节点进入条件",
        description = "根据会话ID和节点ID，动态获取该节点的所有进入条件，并解析当前变量值")
    @GetMapping("/node/entry")
    public R<Map<String, Object>> getNodeEntryConditions(
        @Parameter(description = "会话ID", required = true)
        @RequestParam String sessionId,
        @Parameter(description = "节点ID（状态编码）", required = true)
        @RequestParam String nodeId) {
        try {
            Map<String, Object> result = sessionService.getNodeEntryConditions(sessionId, nodeId);
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取节点进入条件失败", e);
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/phase/subgraph")
    public R<Map<String, String>> getPhaseSubgraph(@RequestParam(required = false) String sessionId,
                                                   @RequestParam(required = false) String caseType,
                                                   @RequestParam String phaseId) {
        String mermaid = sessionService.getPhaseSubgraphMermaid(sessionId, caseType, phaseId);
        Map<String, String> resp = new HashMap<>();
        resp.put("mermaid", mermaid);
        return R.ok(resp);
    }
}
