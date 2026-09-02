package com.ruoyi.ems.executor.impl;

import com.ruoyi.ems.domain.ExecutionResult;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.executor.IntentExecutor;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.npc.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 团队协调意图执行器
 * 处理：G2-1（角色分配）、G2-2（闭环确认）、G2-3（时间报时）、G2-4（专家会诊）、G2-5（策略纠偏）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamCoordinatorExecutor implements IntentExecutor {

    private final TeamManager teamManager;

    private static final Set<String> SUPPORTED_INTENTS = Set.of(
        "G2-1", "G2-2", "G2-3", "G2-4", "G2-5"
    );

    // 职责名称映射（学员说的中文 → Duty枚举）
    private static final Map<String, Duty> DUTY_MAP = new LinkedHashMap<>();

    static {
        DUTY_MAP.put("按压", Duty.CPR);
        DUTY_MAP.put("胸外按压", Duty.CPR);
        DUTY_MAP.put("CPR", Duty.CPR);
        DUTY_MAP.put("气道", Duty.AIRWAY);
        DUTY_MAP.put("气道管理", Duty.AIRWAY);
        DUTY_MAP.put("通气", Duty.AIRWAY);
        DUTY_MAP.put("插管", Duty.AIRWAY);
        DUTY_MAP.put("药物", Duty.IV_MED);
        DUTY_MAP.put("给药", Duty.IV_MED);
        DUTY_MAP.put("推药", Duty.IV_MED);
        DUTY_MAP.put("静脉", Duty.IV_MED);
        DUTY_MAP.put("监护", Duty.MONITOR_DEFIB);
        DUTY_MAP.put("除颤", Duty.MONITOR_DEFIB);
        DUTY_MAP.put("心电图", Duty.MONITOR_DEFIB);
        DUTY_MAP.put("记录", Duty.RECORD);
        DUTY_MAP.put("计时", Duty.RECORD);
        DUTY_MAP.put("评估", Duty.ASSESS);
        DUTY_MAP.put("指挥", Duty.ASSESS);
        DUTY_MAP.put("辅助", Duty.ASSIST);
        DUTY_MAP.put("搬运", Duty.ASSIST);
    }

    @Override
    public ExecutionResult execute(IntentCommand command, String sessionId) {
        String intentCode = command.getIntentCode();
        Map<String, Object> slots = command.getSlots();

        // 确保团队已初始化
        teamManager.initializeTeam(sessionId, "3MAN"); // 默认三人组，可由前端传入

        switch (intentCode) {
            case "G2-1":
                return handleRoleAssignment(sessionId, slots);
            case "G2-2":
                return handleConfirmation(slots);
            case "G2-3":
                return handleTimeReport(slots);
            case "G2-4":
                return handleConsultation(slots);
            case "G2-5":
                return handleStrategyCorrection(slots);
            default:
                return ExecutionResult.success("✅ 已收到团队指令");
        }
    }

    // ==================== 处理角色分配 G2-1 ====================

    private ExecutionResult handleRoleAssignment(String sessionId, Map<String, Object> slots) {
        String person = (String) slots.getOrDefault("person", "");
        String dutyText = (String) slots.getOrDefault("duty", "");

        // 如果缺少人员或职责信息，尝试从 rawText 中解析
        if (person.isEmpty() || dutyText.isEmpty()) {
            String rawText = (String) slots.getOrDefault("rawText", "");
            Map<String, String> parsed = parseRoleFromText(rawText);
            person = parsed.getOrDefault("person", person);
            dutyText = parsed.getOrDefault("duty", dutyText);
        }

        if (person.isEmpty()) {
            return ExecutionResult.rejected("未识别到人员姓名，请指定谁来执行（如：张三负责按压）");
        }

        if (dutyText.isEmpty()) {
            return ExecutionResult.rejected("未识别到职责，请指定要分配的职责（如：按压、气道、药物）");
        }

        // 查找对应的 Duty
        Duty duty = DUTY_MAP.get(dutyText);
        if (duty == null) {
            return ExecutionResult.rejected("不识别的职责：" + dutyText + "，可用职责：按压、气道、药物、监护、记录、评估、辅助");
        }

        // 查找指定姓名的成员
        Optional<TeamMember> optMember = teamManager.findMemberByName(sessionId, person);
        if (optMember.isEmpty()) {
            return ExecutionResult.rejected("未找到成员：" + person + "，请确认姓名是否正确");
        }

        TeamMember member = optMember.get();

        // 检查成员是否具备该职责
        if (!member.hasDuty(duty)) {
            return ExecutionResult.rejected(
                String.format("%s（%s）不具备 %s 职责，其职责为：%s",
                    member.getFullName(),
                    member.getTitle(),
                    duty.getDisplayName(),
                    member.getDuties().stream()
                        .map(Duty::getDisplayName)
                        .reduce((a, b) -> a + "、" + b)
                        .orElse("无")
                )
            );
        }

        // 检查成员是否空闲
        if (!member.isIdle()) {
            return ExecutionResult.rejected(
                String.format("%s（%s）当前正在 %s，请稍后再分配",
                    member.getFullName(),
                    member.getTitle(),
                    member.getCurrentTask() != null ? member.getCurrentTask() : "执行任务"
                )
            );
        }

        // 分配任务：角色分配本身不需要执行动作，只是记录状态
        // 但我们可以分配一个"待命"任务，或者仅记录成员状态
        member.startTask("待命 " + duty.getDisplayName(), 0);
        member.completeTask(); // 立即完成，表示已就位

        String response = String.format("✅ %s（%s）已分配为 %s，正在就位",
            member.getFullName(),
            member.getTitle(),
            duty.getDisplayName()
        );

        return ExecutionResult.builder()
            .success(true)
            .responseText(response)
            .assignedMember(member)
            .build();
    }

    /**
     * 从文本中解析人员和职责
     * 示例：张三负责按压 → person=张三, duty=按压
     */
    private Map<String, String> parseRoleFromText(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null || text.isEmpty()) return result;

        // 匹配 "XXX负责YYY" 或 "XXX负责YYY"
        Pattern pattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?:负责|管理|执行|做|来)([\\u4e00-\\u9fa5]{2,6})");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            result.put("person", matcher.group(1));
            result.put("duty", matcher.group(2));
        }
        return result;
    }

    // ==================== 处理闭环确认 G2-2 ====================

    private ExecutionResult handleConfirmation(Map<String, Object> slots) {
        return ExecutionResult.success("✅ 确认收到，正在执行！");
    }

    // ==================== 处理时间报时 G2-3 ====================

    private ExecutionResult handleTimeReport(Map<String, Object> slots) {
        String time = (String) slots.getOrDefault("time", "");
        if (time.isEmpty()) {
            return ExecutionResult.success("🕐 当前时间已记录");
        }
        return ExecutionResult.success("🕐 已记录时间：" + time);
    }

    // ==================== 处理专家会诊 G2-4 ====================

    private ExecutionResult handleConsultation(Map<String, Object> slots) {
        return ExecutionResult.success("👨‍⚕️ 已联系专家会诊，请稍后获取意见");
    }

    // ==================== 处理策略纠偏 G2-5 ====================

    private ExecutionResult handleStrategyCorrection(Map<String, Object> slots) {
        String action = (String) slots.getOrDefault("overrideAction", "调整方案");
        return ExecutionResult.success("🔄 已执行策略调整：" + action);
    }

    // ==================== 其他方法 ====================

    @Override
    public boolean supports(IntentCommand command) {
        return command != null && SUPPORTED_INTENTS.contains(command.getIntentCode());
    }

    @Override
    public ExecutorType getType() {
        return ExecutorType.TEAM;
    }
}
