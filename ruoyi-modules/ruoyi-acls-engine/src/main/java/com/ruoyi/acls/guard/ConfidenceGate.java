package com.ruoyi.acls.guard;

import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.domain.IntentCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 置信度闸门
 *
 * <p>职责：根据 LLM 输出的置信度决定是否放行指令。
 *
 * <p>三级处理策略：
 * <ul>
 *   <li><b>高置信度（≥ thresholdExecute）</b>：正常放行，进入后续路由</li>
 *   <li><b>中置信度（≥ thresholdClarify）</b>：触发澄清，要求用户复述确认</li>
 *   <li><b>低置信度（< thresholdClarify）</b>：直接拒绝，要求用户重说</li>
 * </ul>
 */
@Slf4j
@Component
public class ConfidenceGate {

    /**
     * 执行阈值：置信度 >= 此值时直接执行
     */
    @Value("${acls.confidence.execute-threshold:0.7}")
    private double executeThreshold;

    /**
     * 澄清阈值：置信度 >= 此值时触发澄清，低于此值则拒绝
     */
    @Value("${acls.confidence.clarify-threshold:0.45}")
    private double clarifyThreshold;

    /**
     * 检查指令置信度
     *
     * @param command 意图命令（含 confidence 字段）
     * @return null 表示放行（继续后续流程）；非 null 表示需要阻断并返回对应结果
     */
    public CommandResult check(IntentCommand command) {
        if (command == null) {
            return CommandResult.failure("无效的指令");
        }

        Double confidence = command.getConfidence();
        String rawText = command.getRawText();

        // 如果没有置信度信息，默认放行（兼容旧版本）
        if (confidence == null) {
            log.debug("指令无置信度信息，默认放行: {}", rawText);
            return null;
        }

        // 高置信度：放行
        if (confidence >= executeThreshold) {
            log.debug("✅ 置信度 {:.2f} ≥ {:.2f}，放行指令: {}",
                confidence, executeThreshold, rawText);
            return null;
        }

        // 中置信度：触发澄清
        if (confidence >= clarifyThreshold) {
            log.warn("⚠️ 置信度 {:.2f} 介于 {:.2f} 和 {:.2f} 之间，触发澄清: {}",
                confidence, clarifyThreshold, executeThreshold, rawText);
            return CommandResult.clarify(
                String.format("您说的是「%s」吗？请确认或重新说一遍。", rawText)
            );
        }

        // 低置信度：直接拒绝
        log.warn("❌ 置信度 {:.2f} < {:.2f}，拒绝指令: {}",
            confidence, clarifyThreshold, rawText);
        return CommandResult.failure(
            "语音识别不清晰，请重新说一遍您的指令。"
        );
    }

    /**
     * 检查指令置信度（带自定义阈值）
     *
     * @param command 意图命令
     * @param customExecuteThreshold 自定义执行阈值
     * @param customClarifyThreshold 自定义澄清阈值
     * @return null 表示放行；非 null 表示阻断
     */
    public CommandResult check(IntentCommand command,
                               double customExecuteThreshold,
                               double customClarifyThreshold) {
        if (command == null) {
            return CommandResult.failure("无效的指令");
        }

        Double confidence = command.getConfidence();
        if (confidence == null) {
            return null;
        }

        if (confidence >= customExecuteThreshold) {
            return null;
        }

        if (confidence >= customClarifyThreshold) {
            return CommandResult.clarify(
                String.format("您说的是「%s」吗？请确认。", command.getRawText())
            );
        }

        return CommandResult.failure("语音识别不清晰，请重新说一遍。");
    }
}
