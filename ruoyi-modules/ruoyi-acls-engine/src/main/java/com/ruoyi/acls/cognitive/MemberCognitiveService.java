package com.ruoyi.acls.cognitive;

import com.ruoyi.acls.member.VirtualTeamMember;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.Duty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 组员认知服务 —— 为每个组员构建专属的 LLM 提示词
 * 实现“角色隔离”和“感知过滤”
 */
@Slf4j
@Service
public class MemberCognitiveService {

    /**
     * 构建系统提示词（角色身份 + 职责边界）
     */
    public String buildRolePrompt(VirtualTeamMember member) {
        String base = "你是一名经验丰富的急救团队成员，正在参与ACLS模拟训练。\n";
        base += "姓名：" + member.getName() + "，职务：" + member.getTitle() + "\n";

        if (member.hasDuty(Duty.MONITOR_DEFIB)) {
            return base + """
                    核心职责：
                    1. 持续关注心电监护仪，识别心律变化。
                    2. 发现可除颤心律（VF/VT）时，立即预警并准备除颤。
                    3. 你无权指挥按压或给药，只负责监护和除颤。
                    性格：果断、警觉。
                    输出格式：请用一句话提出建议，或回复“待命”。
                    """;
        } else if (member.hasDuty(Duty.CPR)) {
            return base + """
                    核心职责：
                    1. 专注于胸外按压，保证深度5-6cm，频率100-120次/分。
                    2. 感知到按压中断超时（>5秒）时主动提醒。
                    3. 你无权决定除颤或药物。
                    性格：专注、耐力强。
                    输出格式：请用一句话提出建议，或回复“待命”。
                    """;
        } else if (member.hasDuty(Duty.IV_MED)) {
            return base + """
                    核心职责：
                    1. 负责建立静脉通路和给药。
                    2. 跟踪肾上腺素等药物间隔时间，及时提醒。
                    3. 你无权决定除颤。
                    性格：细心、严谨。
                    输出格式：请用一句话提出建议，或回复“待命”。
                    """;
        } else {
            return base + """
                    你是一名辅助成员，协助组长完成急救任务。
                    如果没有特别需要，回复“待命”。
                    """;
        }
    }

    /**
     * 构建用户提示词（当前感知数据 —— 模拟人类注意力局限）
     */
    public String buildPerceivedData(VirtualTeamMember member, CaseContext context) {
        Map<String, Object> snapshot = context.getVariablesSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("- 场景已进行时间: ").append(context.getElapsedSeconds()).append(" 秒\n");

        // 根据不同职责过滤不同感知信息
        if (member.hasDuty(Duty.MONITOR_DEFIB)) {
            Object rhythm = snapshot.getOrDefault("current_rhythm", "未知");
            sb.append("- 当前心律: ").append(rhythm).append("\n");
            sb.append("- 是否已除颤: ").append(snapshot.getOrDefault("defib_attempted", "否")).append("\n");
        }
        if (member.hasDuty(Duty.CPR)) {
            sb.append("- 当前按压深度: ").append(snapshot.getOrDefault("compression_depth", "未测量")).append(" cm\n");
            sb.append("- 按压中断时间: ").append(snapshot.getOrDefault("hands_off_time", "0")).append(" 秒\n");
        }
        if (member.hasDuty(Duty.IV_MED)) {
            sb.append("- 肾上腺素已给次数: ").append(snapshot.getOrDefault("epi_count", "0")).append("\n");
            sb.append("- 最近一次给药时间: ").append(snapshot.getOrDefault("last_med_time", "未知")).append(" 秒前\n");
        }

        // 所有组员共享的信息（通用场景）
        sb.append("- 组长最新指令: ").append(snapshot.getOrDefault("last_command", "无")).append("\n");

        return sb.toString();
    }
}
