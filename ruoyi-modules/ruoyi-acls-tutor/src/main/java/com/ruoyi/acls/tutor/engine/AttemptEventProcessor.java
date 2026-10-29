package com.ruoyi.acls.tutor.engine;

import com.ruoyi.ems.context.EmsContextManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 尝试事件处理器（阶段 3 丙-2，最小骨架）。
 *
 * <p><b>职责</b>（仅此 3 条）：
 * <ol>
 *   <li>作为 eventSequence 的权威推进点，通过 {@link EmsContextManager#advanceEventSequence(String)}
 *       真正推进并写回 {@code CaseContext.eventSequence}；</li>
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
 *
 * <p>★ 2026-09-25 修复 1.1.B：方法签名从 {@code process(CaseContext, SelectionInput)} 改为
 * {@code process(String sessionId, SelectionInput)}。原实现接收 {@link com.ruoyi.ems.domain.CaseContext}
 * 后调用其 {@code advanceEventSequence()}，但传入的是 {@code getSnapshot(...)} 返回的副本，
 * 推进结果不会写回真正的 cache，导致日志中 {@code eventSequence=-1}、
 * {@code sessionId=null}。改为 sessionId + EmsContextManager 委托推进。</p>
 */
@Slf4j
@Component
public class AttemptEventProcessor {

    private final ScenarioBeatEvaluator evaluator;
    private final EmsContextManager emsContextManager;

    @Autowired
    public AttemptEventProcessor(ScenarioBeatEvaluator evaluator,
                                 @Autowired(required = false) EmsContextManager emsContextManager) {
        this.evaluator = evaluator;
        this.emsContextManager = emsContextManager;
    }

    /**
     * 处理一次尝试事件。
     *
     * <p>调用契约：{@code input} 由调用方用事件信息构建；{@code input == null}
     * 视为"无有效事件"，不推进 eventSequence。</p>
     *
     * @param sessionId 会话 ID；为 null 时跳过 eventSequence 推进，但仍调用 evaluator
     * @param input     beat 选择输入；为 null 时返回空结果
     * @return beat 选择结果；永不为 null
     */
    public ScenarioBeatEvaluator.SelectionResult process(
        String sessionId,
        ScenarioBeatEvaluator.SelectionInput input) {

        if (input == null) {
            ScenarioBeatEvaluator.SelectionResult empty = new ScenarioBeatEvaluator.SelectionResult();
            empty.reason = "no selection input";
            return empty;
        }

        long seq = -1L;
        if (sessionId != null && emsContextManager != null) {
            seq = emsContextManager.advanceEventSequence(sessionId);
        }

        ScenarioBeatEvaluator.SelectionResult result = evaluator.select(input);

        if (result == null) {
            log.warn("[Tutor][AttemptEventProcessor] evaluator 返回 null，sessionId={}, caseId={}, eventSequence={}",
                sessionId, input.caseId, seq);
            return new ScenarioBeatEvaluator.SelectionResult();
        }

        log.info("[Tutor][AttemptEventProcessor] sessionId={}, caseId={}, eventSequence={}, selected={}, reason={}",
            sessionId, input.caseId, seq,
            result.selected != null ? result.selected.getBeatId() : null,
            result.reason);

        return result;
    }
}
