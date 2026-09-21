package com.ruoyi.acls.tutor.engine;

import com.ruoyi.ems.domain.CaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 尝试事件处理器（阶段 3 丙-2，最小骨架）。
 *
 * <p><b>职责</b>（仅此 3 条）：
 * <ol>
 *   <li>作为 eventSequence 的权威推进点，调用 {@link CaseContext#advanceEventSequence()}；</li>
 *   <li>调用 {@link ScenarioBeatEvaluator#select} 进行 beat 选择；</li>
 *   <li>返回 {@link ScenarioBeatEvaluator.SelectionResult} 给调用方。</li>
 * </ol>
 * </p>
 *
 * <p><b>不做</b>：
 * <ul>
 *   <li>不驱动状态迁移，不集成 StateMachineFacade；</li>
 *   <li>不自动触发评分中断（P0-8），不写 AssessmentState 证据链；</li>
 *   <li>不解释 constraints（P0-7），不判定 rules 是否允许；</li>
 *   <li>不反向依赖 AssessmentEngine，调用方向由上层编排决定。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class AttemptEventProcessor {

    private final ScenarioBeatEvaluator evaluator;

    @Autowired
    public AttemptEventProcessor(ScenarioBeatEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 处理一次尝试事件。
     *
     * <p>调用契约：{@code input} 由调用方用 {@link CaseContext} + 事件信息构建，
     * processor 不替调用方补字段；{@code input == null} 视为“无有效事件”，
     * 不推进 eventSequence。</p>
     *
     * @param context 运行时上下文；为 null 时跳过 eventSequence 推进，但仍调用 evaluator
     * @param input   beat 选择输入；为 null 时返回空结果
     * @return beat 选择结果；永不为 null
     */
    public ScenarioBeatEvaluator.SelectionResult process(
        CaseContext context,
        ScenarioBeatEvaluator.SelectionInput input) {

        if (input == null) {
            ScenarioBeatEvaluator.SelectionResult empty = new ScenarioBeatEvaluator.SelectionResult();
            empty.reason = "no selection input";
            return empty;
        }

        long seq = -1L;
        if (context != null) {
            seq = context.advanceEventSequence();
        }

        ScenarioBeatEvaluator.SelectionResult result = evaluator.select(input);

        if (result == null) {
            log.warn("[Tutor][AttemptEventProcessor] evaluator 返回 null，sessionId={}, caseId={}, eventSequence={}",
                context != null ? context.getSessionId() : null,
                input.caseId, seq);
            return new ScenarioBeatEvaluator.SelectionResult();
        }

        log.info("[Tutor][AttemptEventProcessor] sessionId={}, caseId={}, eventSequence={}, selected={}, reason={}",
            context != null ? context.getSessionId() : null,
            input.caseId, seq,
            result.selected != null ? result.selected.getBeatId() : null,
            result.reason);

        return result;
    }
}
