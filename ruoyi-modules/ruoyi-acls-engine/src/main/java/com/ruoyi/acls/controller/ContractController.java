package com.ruoyi.acls.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.acls.contract.CaseConfigLoader;
import com.ruoyi.acls.contract.CaseConfigReportService;
import com.ruoyi.acls.contract.CaseContractRegistry;
import com.ruoyi.acls.contract.diagnose.BlockingDiagnoser;
import com.ruoyi.acls.contract.diagnose.DiagnosticReport;
import com.ruoyi.acls.contract.models.CaseContract;
import com.ruoyi.acls.contract.models.ContractDiagnostics;
import com.ruoyi.acls.contract.models.DerivedVarContract;
import com.ruoyi.acls.contract.models.IntentContract;
import com.ruoyi.acls.contract.models.StateContract;
import com.ruoyi.acls.contract.models.TransitionContract;
import com.ruoyi.acls.contract.models.VariableContract;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner;
import com.ruoyi.acls.contract.util.DanglingRefChecker;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;

import com.ruoyi.ems.domain.dto.LogicFlowDefinitionDTO;
import com.ruoyi.ems.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.datatransfer.SystemFlavorMap;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner;
import com.ruoyi.acls.contract.util.CrossModuleReferenceScanner.VariableBlood;
/**
 * 契约维护控制器
 * <p>
 * 提供契约（CaseContract）的查询、诊断、反查、报告与热重载 REST API。
 * 所有接口均以 {@code /ems/contract} 为前缀。
 * </p>
 *
 * @author system
 * @since 1.0.0
 */
@SaIgnore
@Slf4j
@RestController
@RequestMapping("/ems/contract")
@RequiredArgsConstructor
public class ContractController {

    private final CaseContractRegistry contractRegistry;
    private final CaseConfigLoader caseConfigLoader;
    private final YamlStateMachineLoader stateMachineLoader;
    private final CaseConfigReportService caseConfigReportService;
    private final SessionService sessionService;
    private final CrossModuleReferenceScanner crossModuleReferenceScanner;
    private final BlockingDiagnoser blockingDiagnoser;
    private final DanglingRefChecker danglingRefChecker;
    // ==================== 案例列表与概览 ====================
    /**
     * 获取案例状态机的 LogicFlow 图形数据。
     *
     * @param caseId 案例ID
     * @param full 是否包含继承的 GLOBAL 状态机
     * @return LogicFlow 图形定义
     */
    @Operation(
        summary = "获取案例 LogicFlow 状态机",
        description = "根据案例ID获取状态机图形数据，支持查看案例自身配置或包含继承关系的完整配置"
    )
    @GetMapping("/{caseId}/definition")
    public R<LogicFlowDefinitionDTO> getDefinition(
        @Parameter(description = "案例ID", required = true)
        @PathVariable("caseId") String caseId,

        @Parameter(description = "是否包含继承的状态机")
        @RequestParam(defaultValue = "false") boolean full) {

        try {
            log.info(
                "[LogicFlow] 开始获取案例状态机: caseId={}, full={}",
                caseId,
                full
            );

            LogicFlowDefinitionDTO definition =
                sessionService.getCaseLogicFlow(caseId, full);

            if (definition == null) {
                return R.fail("未找到案例状态机配置: " + caseId);
            }

            log.info(
                "[LogicFlow] 案例状态机获取成功: caseId={}, full={}",
                caseId,
                full
            );

            return R.ok(definition);

        } catch (IllegalArgumentException e) {

            log.warn(
                "[LogicFlow] 请求参数错误: caseId={}, message={}",
                caseId,
                e.getMessage()
            );

            return R.fail(e.getMessage());

        } catch (Exception e) {

            log.error(
                "[LogicFlow] 获取案例状态机失败: caseId={}, full={}",
                caseId,
                full,
                e
            );

            return R.fail("获取案例状态机失败: " + e.getMessage());
        }
    }
    @Operation(summary = "列出所有案例", description = "返回已构建契约的所有案例 ID 列表")
    @GetMapping("/cases")
    public R<List<String>> listCases() {
        try {
            List<String> caseIds = new ArrayList<>(contractRegistry.getAllCaseIds());
            Collections.sort(caseIds);
            return R.ok(caseIds);
        } catch (Exception e) {
            log.error("列出契约案例失败", e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取全部案例概览", description = "返回所有案例的统计与诊断概览")
    @GetMapping("/overview")
    public R<List<Map<String, Object>>> overview() {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (String caseId : contractRegistry.getAllCaseIds()) {
                CaseContract c = contractRegistry.get(caseId);
                if (c == null) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("caseId", caseId);
                item.put("stateCount", c.getStateContracts().size());
                item.put("transitionCount", c.getTransitionContracts().size());
                item.put("intentCount", c.getIntentContracts().size());
                item.put("variableCount", c.getVariableContracts().size());
                item.put("derivedVarCount", c.getDerivedVarContracts().size());

                ContractDiagnostics diag = contractRegistry.getDiagnostics(caseId);
                if (diag != null) {
                    item.put("undefinedDerivedVars", diag.getUndefinedDerivedVars().size());
                    item.put("deadTransitions", diag.getDeadTransitions().size());
                    item.put("selfLoops", diag.getSelfLoops().size());
                    item.put("unwritableVariables", diag.getUnwritableVariables().size());
                    item.put("orphanVariables", diag.getOrphanVariables().size());
                    item.put("emptyIntents", diag.getEmptyIntents().size());
                    item.put("circularDependencies", diag.getCircularDependencies().size());
                    item.put("hasErrors", diag.hasErrors());
                }
                result.add(item);
            }
            return R.ok(result);
        } catch (Exception e) {
            log.error("获取案例概览失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 契约详情 ====================

    @Operation(summary = "获取契约详情", description = "返回指定案例的完整契约对象")
    @GetMapping("/detail")
    public R<CaseContract> detail(
        @Parameter(description = "案例ID", required = true, example = "stable_narrow")
        @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) {
                return R.fail("案例不存在或契约未构建: " + caseId);
            }
            return R.ok(contract);
        } catch (Exception e) {
            log.error("获取契约详情失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== Mermaid ====================
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

    private String appendDiagnosticClasses(String baseMermaid, CaseContract contract) {
        Set<String> errorNodes = new LinkedHashSet<>();
        Set<String> warningNodes = new LinkedHashSet<>();

        for (TransitionContract tc : contract.getTransitionContracts().values()) {
            if (tc.isDead()) {
                if (tc.getSourceState() != null) errorNodes.add(tc.getSourceState());
                if (tc.getTargetState() != null) errorNodes.add(tc.getTargetState());
            } else if (tc.isSelfLoop()) {
                if (tc.getSourceState() != null) warningNodes.add(tc.getSourceState());
            }
        }

        if (errorNodes.isEmpty() && warningNodes.isEmpty()) {
            return baseMermaid;
        }

        StringBuilder sb = new StringBuilder(baseMermaid);
        sb.append("\n    %% ===== 契约诊断标记 =====");
        sb.append("\n    classDef contractError fill:#fff1f0,stroke:#ff4d4f,stroke-width:3px,color:#a8071a;");
        sb.append("\n    classDef contractWarning fill:#fff7e6,stroke:#fa8c16,stroke-width:3px,color:#ad4e00;");

        if (!errorNodes.isEmpty()) {
            String ids = errorNodes.stream()
                .map(ContractController::toSafeId)
                .collect(Collectors.joining(","));
            sb.append("\n    class ").append(ids).append(" contractError;");
        }
        if (!warningNodes.isEmpty()) {
            String ids = warningNodes.stream()
                .map(ContractController::toSafeId)
                .collect(Collectors.joining(","));
            sb.append("\n    class ").append(ids).append(" contractWarning;");
        }

        return sb.toString();
    }

    private static String toSafeId(String stateId) {
        if (stateId == null) return "null";
        return stateId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    // ==================== 状态 ====================

    @Operation(summary = "获取状态列表", description = "返回指定案例的所有状态契约")
    @GetMapping("/states")
    public R<Map<String, StateContract>> states(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);
            return R.ok(contract.getStateContracts());
        } catch (Exception e) {
            log.error("获取状态列表失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取状态详情", description = "返回某个状态节点的完整契约")
    @GetMapping("/state")
    public R<StateContract> state(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "状态ID", required = true) @RequestParam String stateId) {
        try {
            StateContract sc = contractRegistry.getState(caseId, stateId);
            if (sc == null) return R.fail("状态不存在: " + stateId);
            return R.ok(sc);
        } catch (Exception e) {
            log.error("获取状态详情失败: caseId={}, stateId={}", caseId, stateId, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 转移 ====================

    @Operation(summary = "获取转移列表", description = "返回指定案例的所有转移契约")
    @GetMapping("/transitions")
    public R<Map<String, TransitionContract>> transitions(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);
            return R.ok(contract.getTransitionContracts());
        } catch (Exception e) {
            log.error("获取转移列表失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取转移详情", description = "返回指定转移的完整契约")
    @GetMapping("/transition")
    public R<TransitionContract> transition(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "转移ID", required = true) @RequestParam String transitionId) {
        try {
            TransitionContract tc = contractRegistry.getTransition(caseId, transitionId);
            if (tc == null) return R.fail("转移不存在: " + transitionId);
            return R.ok(tc);
        } catch (Exception e) {
            log.error("获取转移详情失败: caseId={}, transitionId={}",
                caseId, transitionId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "按级别筛选转移", description = "level: dead/selfLoop/deviation/disabled/all")
    @GetMapping("/transitions/filter")
    public R<List<TransitionContract>> filterTransitions(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "筛选级别", required = true) @RequestParam String level) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);

            List<TransitionContract> all =
                new ArrayList<>(contract.getTransitionContracts().values());
            List<TransitionContract> filtered;
            switch (level.toLowerCase()) {
                case "dead":
                    filtered = all.stream().filter(TransitionContract::isDead).collect(Collectors.toList());
                    break;
                case "selfloop":
                    filtered = all.stream().filter(TransitionContract::isSelfLoop).collect(Collectors.toList());
                    break;
                case "deviation":
                    filtered = all.stream().filter(TransitionContract::isDeviation).collect(Collectors.toList());
                    break;
                case "disabled":
                    filtered = all.stream().filter(t -> !t.isEnabled()).collect(Collectors.toList());
                    break;
                default:
                    filtered = all;
            }
            return R.ok(filtered);
        } catch (Exception e) {
            log.error("筛选转移失败: caseId={}, level={}", caseId, level, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 意图 ====================

    @Operation(summary = "获取意图列表", description = "返回指定案例的所有意图契约")
    @GetMapping("/intents")
    public R<Map<String, IntentContract>> intents(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);
            return R.ok(contract.getIntentContracts());
        } catch (Exception e) {
            log.error("获取意图列表失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取意图详情", description = "返回指定意图的契约")
    @GetMapping("/intent")
    public R<IntentContract> intent(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "意图编码", required = true) @RequestParam String intentCode) {
        try {
            IntentContract ic = contractRegistry.getIntent(caseId, intentCode);
            if (ic == null) return R.fail("意图不存在: " + intentCode);
            return R.ok(ic);
        } catch (Exception e) {
            log.error("获取意图详情失败: caseId={}, intentCode={}", caseId, intentCode, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 变量 ====================

    @Operation(summary = "获取变量列表", description = "返回指定案例的所有变量契约")
    @GetMapping("/variables")
    public R<Map<String, VariableContract>> variables(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);
            return R.ok(contract.getVariableContracts());
        } catch (Exception e) {
            log.error("获取变量列表失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取变量详情", description = "返回指定变量的双向血缘")
    @GetMapping("/variable")
    public R<VariableContract> variable(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "变量名", required = true) @RequestParam String varName) {
        try {
            VariableContract vc = contractRegistry.getVariable(caseId, varName);
            if (vc == null) return R.fail("变量不存在: " + varName);
            return R.ok(vc);
        } catch (Exception e) {
            log.error("获取变量详情失败: caseId={}, varName={}", caseId, varName, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 派生变量 ====================

    @Operation(summary = "获取派生变量列表", description = "返回所有派生变量契约")
    @GetMapping("/derivedVars")
    public R<Map<String, DerivedVarContract>> derivedVars(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("案例不存在: " + caseId);
            return R.ok(contract.getDerivedVarContracts());
        } catch (Exception e) {
            log.error("获取派生变量列表失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 诊断 ====================

    @Operation(summary = "获取诊断报告", description = "返回指定案例的契约诊断结果")
    @GetMapping("/diagnostics")
    public R<ContractDiagnostics> diagnostics(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            ContractDiagnostics diag = contractRegistry.getDiagnostics(caseId);
            if (diag == null) return R.fail("案例不存在或契约未构建: " + caseId);
            return R.ok(diag);
        } catch (Exception e) {
            log.error("获取诊断报告失败: caseId={}", caseId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "获取全部诊断报告", description = "返回所有案例的诊断报告")
    @GetMapping("/diagnostics/all")
    public R<Map<String, ContractDiagnostics>> allDiagnostics() {
        try {
            return R.ok(contractRegistry.getAllDiagnostics());
        } catch (Exception e) {
            log.error("获取全部诊断报告失败", e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 反查 ====================

    @Operation(summary = "反查变量的读取转移", description = "查找所有读取该变量的状态转移")
    @GetMapping("/trace/transitions-reading")
    public R<List<TransitionContract>> traceTransitionsReading(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "变量名", required = true) @RequestParam String varName) {
        try {
            return R.ok(contractRegistry.findTransitionsReading(caseId, varName));
        } catch (Exception e) {
            log.error("反查失败: caseId={}, varName={}", caseId, varName, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "反查转移的触发意图", description = "返回能触发指定转移的所有意图编码")
    @GetMapping("/trace/transition-triggers")
    public R<List<String>> traceTransitionTriggers(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "转移ID", required = true) @RequestParam String transitionId) {
        try {
            return R.ok(contractRegistry.findTriggeringIntents(caseId, transitionId));
        } catch (Exception e) {
            log.error("反查失败: caseId={}, transitionId={}", caseId, transitionId, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "反查意图的触发转移", description = "返回执行指定意图后可能触发的转移")
    @GetMapping("/trace/intent-transitions")
    public R<List<String>> traceIntentTransitions(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "意图编码", required = true) @RequestParam String intentCode) {
        try {
            return R.ok(contractRegistry.findTransitionsTriggeredBy(caseId, intentCode));
        } catch (Exception e) {
            log.error("反查失败: caseId={}, intentCode={}", caseId, intentCode, e);
            return R.fail(e.getMessage());
        }
    }

    @Operation(summary = "反查变量的写入意图", description = "返回所有能写入指定变量的意图编码")
    @GetMapping("/trace/variable-writers")
    public R<List<String>> traceVariableWriters(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId,
        @Parameter(description = "变量名", required = true) @RequestParam String varName) {
        try {
            return R.ok(contractRegistry.findIntentsWriting(caseId, varName));
        } catch (Exception e) {
            log.error("反查失败: caseId={}, varName={}", caseId, varName, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== ★ 配置报告（新增） ====================

    @Operation(summary = "获取案例配置报告（Markdown）",
        description = "生成包含状态机、意图、技能、变量、派生、诊断的完整 Markdown 报告。" +
            "浏览器直接打开预览；加 ?download=true 下载 .md 文件。")
    @GetMapping(value = "/report", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<String> report(
        @Parameter(description = "案例ID", required = true, example = "stable_narrow")
        @RequestParam String caseId,
        @Parameter(description = "是否触发下载")
        @RequestParam(defaultValue = "false") boolean download) {
        try {
            String markdown = caseConfigReportService.generateReport(caseId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
            if (download) {
                headers.setContentDispositionFormData(
                    "attachment", "case-config-report-" + caseId + ".md");
            }
            return ResponseEntity.ok().headers(headers).body(markdown);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("请求错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("生成案例配置报告失败: caseId={}", caseId, e);
            return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body("生成失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取案例配置报告（JSON 包装）",
        description = "与 /report 相同，但用 R<String> 包装，便于前端 axios 直接消费。")
    @GetMapping("/report/json")
    public R<String> reportJson(
        @Parameter(description = "案例ID", required = true, example = "stable_narrow")
        @RequestParam String caseId) {
        try {
            return R.ok(caseConfigReportService.generateReport(caseId));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("生成案例配置报告失败: caseId={}", caseId, e);
            return R.fail("生成失败: " + e.getMessage());
        }
    }

    // ==================== 热重载 ====================

    @Operation(summary = "全量热重载", description = "重新加载所有配置并重建契约")
    @PostMapping("/reloadAll")
    public R<Map<String, Object>> reloadAll() {
        try {
            long startTime = System.currentTimeMillis();
            caseConfigLoader.reloadAll();
            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("elapsedMs", elapsed);
            result.put("caseCount", contractRegistry.getAllCaseIds().size());
            result.put("caseIds", new ArrayList<>(contractRegistry.getAllCaseIds()));

            log.info("✅ 契约全量热重载完成，耗时 {}ms", elapsed);
            return R.ok(result);
        } catch (Exception e) {
            log.error("契约全量热重载失败", e);
            return R.fail("热重载失败: " + e.getMessage());
        }
    }

    @Operation(summary = "单案例热重载", description = "仅重载指定案例的配置")
    @PostMapping("/reloadCase")
    public R<Map<String, Object>> reloadCase(
        @Parameter(description = "案例ID", required = true) @RequestParam String caseId) {
        try {
            long startTime = System.currentTimeMillis();
            caseConfigLoader.reloadCase(caseId);
            long elapsed = System.currentTimeMillis() - startTime;

            CaseContract contract = contractRegistry.get(caseId);
            if (contract == null) return R.fail("热重载后契约不存在: " + caseId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("caseId", caseId);
            result.put("elapsedMs", elapsed);
            result.put("transitionCount", contract.getTransitionContracts().size());
            result.put("intentCount", contract.getIntentContracts().size());
            result.put("variableCount", contract.getVariableContracts().size());

            log.info("✅ 案例 {} 契约热重载完成，耗时 {}ms", caseId, elapsed);
            return R.ok(result);
        } catch (Exception e) {
            log.error("契约单案例热重载失败: caseId={}", caseId, e);
            return R.fail("热重载失败: " + e.getMessage());
        }
    }



    /**
     * 列出该案例所有跨模块变量（出现在 ≥2 个模块的血缘图节点）。
     * 对应快照 §6.1 的 GET /ems/contract/variables/crossModule
     */
    @GetMapping("/variables/crossModule")
    public R<List<VariableBlood>> listCrossModuleVariables(@RequestParam String caseId) {
        try {
            return R.ok(crossModuleReferenceScanner.listCrossModuleVariables(caseId));
        } catch (Exception e) {
            log.error("扫描跨模块血缘失败: caseId={}", caseId, e);
            return R.fail("扫描失败: " + e.getMessage());
        }
    }

    /**
     * 单个变量的完整血缘：所有 writers / readers / files。
     * 对应快照 §6.1 的 GET /ems/contract/variable/crossModule
     */
    @GetMapping("/variable/crossModule")
    public R<CrossModuleReferenceScanner.VariableBlood> scanVariable(@RequestParam String caseId,
                                                                     @RequestParam String varName) {
        try {
            CrossModuleReferenceScanner.VariableBlood vb = crossModuleReferenceScanner.scanVariable(caseId, varName);
            if (vb == null) return R.fail("变量不存在或未被任何文件引用: " + varName);
            return R.ok(vb);
        } catch (Exception e) {
            log.error("扫描单变量血缘失败: caseId={}, varName={}", caseId, varName, e);
            return R.fail("扫描失败: " + e.getMessage());
        }
    }


    /**
     * 一步诊断：从被拒现象反查根因。
     *
     * 示例：
     *   GET /ems/contract/diagnose?caseId=stable_narrow
     *   GET /ems/contract/diagnose?caseId=stable_narrow&stateId=F_101_CHECK_RHYTHM&intentId=IA_053_attach_defib_pads
     */
    @GetMapping("/diagnose")
    public R<DiagnosticReport> diagnose(
        @RequestParam String caseId,
        @RequestParam(required = false) String sessionId,
        @RequestParam(required = false) String stateId,
        @RequestParam(required = false) String intentId) {
        try {
            return R.ok(blockingDiagnoser.diagnose(caseId, sessionId, stateId, intentId));
        } catch (Exception e) {
            log.error("诊断失败", e);
            return R.fail("诊断失败: " + e.getMessage());
        }
    }

    @GetMapping("/danglingRefs")
    public R<DanglingRefChecker.DanglingReport> danglingRefs(@RequestParam String caseId) {
        try {

            return R.ok(danglingRefChecker.check(caseId));
        } catch (Exception e) {
            log.error("悬空引用检查失败: caseId={}", caseId, e);
            return R.fail("检查失败: " + e.getMessage());
        }
    }
}
