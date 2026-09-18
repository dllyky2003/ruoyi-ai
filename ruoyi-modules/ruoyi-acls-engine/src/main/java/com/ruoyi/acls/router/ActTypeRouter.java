package com.ruoyi.acls.router;

import com.ruoyi.acls.engine.AckManager;
import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.impl.QuerySkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 语用分流器（ActType Router）
 *
 * <p>职责：根据 actType 将指令分流到不同的处理通道。
 *
 * <p>处理策略：
 * <ul>
 *   <li><b>INQUIRY</b>（探查性询问）→ 查询通道：NPC 口头回答，不执行动作</li>
 *   <li><b>DECLARE</b>（宣告决策）→ 状态更新通道：更新上下文，不执行动作</li>
 *   <li><b>CANCEL</b>（取消/终止）→ 取消通道：中断当前任务</li>
 *   <li><b>CONFIRM</b>（复述确认）→ 确认通道：匹配 AckManager 等待队列</li>
 *   <li><b>OVERRIDE</b>（纠偏/否决）→ 纠偏通道：强制状态跳转</li>
 *   <li><b>COMMAND</b>（发令性指令）→ 委托给 SemanticRouter 进行完整路由</li>
 *   <li><b>其他</b>→ 返回 NONE，由引擎兜底处理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActTypeRouter {

    private final SkillRegistry skillRegistry;
    private final AckManager ackManager;
    private final SemanticRouter semanticRouter;
    private final TeamManager teamManager;
    private final NpcSkillExecutor npcSkillExecutor;

    /**
     * 路由主入口
     *
     * @param command 意图命令（含 actType、intentCode、slots）
     * @param context 案例上下文
     * @return 路由决策
     */
    public RouteDecision route(IntentCommand command, CaseContext context) {
        String actType = command.getActType();
        String intentCode = command.getIntentCode();
        Map<String, Object> slots = command.getSlots();
        String sessionId = context.getSessionId();

        log.debug("🔀 ActTypeRouter: actType={}, intent={}, session={}", actType, intentCode, sessionId);

        // ========== 1. INQUIRY（探查性询问） ==========
        if ("INQUIRY".equals(actType) || "探查性询问".equals(actType)) {
            return handleInquiry(intentCode, slots, context);
        }

        // ========== 2. DECLARE（宣告决策） ==========
        if ("DECLARE".equals(actType) || "宣告".equals(actType)) {
            return handleDeclaration(intentCode, slots, context);
        }

        // ========== 3. CANCEL（取消/终止） ==========
        if ("CANCEL".equals(actType)) {
            return handleCancel(intentCode, slots, context);
        }

        // ========== 4. CONFIRM（复述确认） ==========
        if ("CONFIRM".equals(actType)) {
            return handleConfirm(sessionId, intentCode, slots, context);
        }

        // ========== 5. OVERRIDE（纠偏/否决） ==========
        if ("OVERRIDE".equals(actType)) {
            return handleOverride(intentCode, slots, context);
        }

        // ========== 6. COMMAND（发令性指令） ==========
        if ("COMMAND".equals(actType) || "发令性指令".equals(actType)
            || "PROBE".equals(actType)) {
            return semanticRouter.route(command, context);
        }

        // ========== 7. 未知语用 ==========
        log.warn("⚠️ 未知语用类型: {}, 意图: {}", actType, intentCode);
        return RouteDecision.none();
    }

    // ==================== 各通道处理逻辑 ====================

    /**
     * 处理探查性询问
     */
    private RouteDecision handleInquiry(String intentCode, Map<String, Object> slots, CaseContext context) {
        EmsSkill skill = skillRegistry.getSkill(intentCode);

        if (skill instanceof QuerySkill) {
            String response = ((QuerySkill) skill).getQueryResponse(
                buildQueryContext(context, slots)
            );
            log.info("💬 [INQUIRY] {} -> {}", intentCode, response);
            return RouteDecision.queryRespond(response);
        }

        if (skill != null) {
            log.warn("⚠️ 意图 {} 不是 QuerySkill，无法处理探查性询问", intentCode);
        }

        String fallback = "我正在查询，请稍候...";
        log.info("💬 [INQUIRY_FALLBACK] {} -> {}", intentCode, fallback);
        return RouteDecision.queryRespond(fallback);
    }

    /**
     * 处理宣告决策
     */
    private RouteDecision handleDeclaration(String intentCode, Map<String, Object> slots, CaseContext context) {
        Map<String, Object> updates = new HashMap<>();

        switch (intentCode) {
            case "declare_svt":
                updates.put("currentState", "svt_diagnosed");
                updates.put("svtDeclared", true);
                break;
            case "declare_afib":
                updates.put("currentState", "afib_diagnosed");
                updates.put("afibDeclared", true);
                break;
            case "declare_unstable":
                updates.put("currentState", "unstable");
                updates.put("unstableDeclared", true);
                break;
            default:
                updates.put(intentCode + "_declared", true);
                updates.put("lastDeclareTime", System.currentTimeMillis());
                break;
        }

        if (slots != null) {
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                updates.put("declare_" + entry.getKey(), entry.getValue());
            }
        }

        String responseText = "已记录：" + intentCode;
        log.info("📝 [DECLARE] {} -> updates: {}", intentCode, updates);

        return RouteDecision.stateUpdate(updates, responseText);
    }

    /**
     * 处理取消指令
     */
    private RouteDecision handleCancel(String intentCode, Map<String, Object> slots, CaseContext context) {
        String target = slots != null ? (String) slots.getOrDefault("target", "当前任务") : "当前任务";
        String responseText = "正在取消：" + target;

        log.info("⏹️ [CANCEL] {} -> {}", intentCode, target);
        return RouteDecision.taskCancel(responseText);
    }

    /**
     * 处理复述确认
     */
    private RouteDecision handleConfirm(String sessionId, String intentCode,
                                        Map<String, Object> slots, CaseContext context) {
        boolean matched = ackManager.acknowledge(sessionId, slots);

        if (matched) {
            log.info("✅ [CONFIRM] 确认匹配成功: session={}, slots={}", sessionId, slots);
            return RouteDecision.confirmAck("确认收到，正在执行。");
        } else {
            log.warn("⚠️ [CONFIRM] 未找到匹配的待确认指令: session={}, slots={}", sessionId, slots);
            return RouteDecision.clarify("未找到待确认的指令，请重新下达。");
        }
    }

    /**
     * 处理纠偏/否决
     */
    private RouteDecision handleOverride(String intentCode, Map<String, Object> slots, CaseContext context) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("overrideTriggered", true);
        updates.put("overrideIntent", intentCode);
        updates.put("overrideTime", System.currentTimeMillis());

        if (slots != null && slots.containsKey("newState")) {
            updates.put("currentState", slots.get("newState"));
        }

        if ("override_stop_drug".equals(intentCode)) {
            updates.put("drugProtocolPaused", true);
        } else if ("override_switch_to_vf".equals(intentCode)) {
            updates.put("currentState", "vf_pulseless_vt");
            updates.put("protocolSwitched", "vf_protocol");
        }

        String responseText = "已推翻原方案，正在切换至新方案。";
        log.info("🔄 [OVERRIDE] {} -> updates: {}", intentCode, updates);

        return RouteDecision.overrideJump(updates, responseText);
    }

    /**
     * 构建查询上下文（供 QuerySkill 使用）
     */
    private CommandContext buildQueryContext(CaseContext context, Map<String, Object> slots) {
        return new CommandContext(
            context.getSessionId(),
            null,
            context,
            null,
            null,
            null
        );
    }

    // ==================== ★ 新增：COMMAND 执行完成后，由外部调用 ====================

    /**
     * 执行技能（由 AclsCommandEngine 在 EXECUTE_SKILL 分支调用）
     * 这个方法替代了原来 CommandRouter 的功能
     */
    public CommandResult executeSkill(
        String skillId,
        CommandContext context,
        Map<String, Object> slots) {

        String sessionId = context.getSessionId();

        // 1. 获取技能
        EmsSkill skill = skillRegistry.getSkill(skillId);
        if (skill == null) {
            log.warn("未找到技能: {}", skillId);
            return CommandResult.failure("未找到对应的急救技能: " + skillId);
        }

        // 2. 获取技能所需的职责
        Duty requiredDuty = skill.getRequiredDuty();

        // 3. 查找空闲的 NPC
        Optional<TeamMember> executorOpt = teamManager.findIdleMemberForDuty(sessionId, requiredDuty);
        if (executorOpt.isEmpty()) {
            log.warn("没有空闲的 {} 可执行技能: {}", requiredDuty.getDisplayName(), skillId);
            return CommandResult.failure("当前没有空闲的 " + requiredDuty.getDisplayName() + "，请稍后重试");
        }

        TeamMember executor = executorOpt.get();
        log.info("🔄 执行技能: {} → {} (执行者: {})", skillId, requiredDuty, executor.getName());

        // 4. ★ 委托给 NpcSkillExecutor 执行
        return npcSkillExecutor.execute(executor, skill, context, slots);

    }
}
