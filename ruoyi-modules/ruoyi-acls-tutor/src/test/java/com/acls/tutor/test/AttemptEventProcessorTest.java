package com.acls.tutor.test;

import com.ruoyi.acls.tutor.domain.ScenarioBeat;
import com.ruoyi.acls.tutor.engine.AttemptEventProcessor;
import com.ruoyi.acls.tutor.engine.ScenarioBeatEvaluator;
import com.ruoyi.ems.domain.CaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttemptEventProcessorTest {

    private ScenarioBeatEvaluator evaluator;
    private AttemptEventProcessor processor;
    private CaseContext context;

    @BeforeEach
    void setUp() {
        evaluator = mock(ScenarioBeatEvaluator.class);
        processor = new AttemptEventProcessor(evaluator);
        context = mock(CaseContext.class);
    }

    // ============================================================
    // input 防御
    // ============================================================

    @Test
    void input为null时不推进eventSequence并返回空结果() {
        ScenarioBeatEvaluator.SelectionResult r = processor.process(context, null);

        assertNotNull(r);
        assertEquals("no selection input", r.reason);
        assertNull(r.selected);
        verify(context, never()).advanceEventSequence();
        verifyNoInteractions(evaluator);
    }

    // ============================================================
    // 主路径
    // ============================================================

    @Test
    void 正常事件应推进一次eventSequence并透传evaluator结果() {
        when(context.advanceEventSequence()).thenReturn(7L);

        ScenarioBeatEvaluator.SelectionInput input = new ScenarioBeatEvaluator.SelectionInput();
        input.caseId = "stable_narrow";

        ScenarioBeatEvaluator.SelectionResult mockResult = new ScenarioBeatEvaluator.SelectionResult();
        mockResult.reason = "ok";
        mockResult.selected = new ScenarioBeat();
        mockResult.selected.setBeatId("BEAT_X");
        when(evaluator.select(input)).thenReturn(mockResult);

        ScenarioBeatEvaluator.SelectionResult r = processor.process(context, input);

        assertSame(mockResult, r);
        verify(context, times(1)).advanceEventSequence();
        verify(evaluator, times(1)).select(input);
    }

    // ============================================================
    // 兜底
    // ============================================================

    @Test
    void evaluator返回null时兜底返回非null空结果() {
        when(context.advanceEventSequence()).thenReturn(1L);

        ScenarioBeatEvaluator.SelectionInput input = new ScenarioBeatEvaluator.SelectionInput();
        input.caseId = "stable_narrow";
        when(evaluator.select(input)).thenReturn(null);

        ScenarioBeatEvaluator.SelectionResult r = processor.process(context, input);

        assertNotNull(r);
        assertNull(r.selected);
        // 即使 evaluator 返回 null，eventSequence 也已推进——推进语义与结果无关
        verify(context, times(1)).advanceEventSequence();
    }

    @Test
    void context为null时不推进但仍调用evaluator() {
        ScenarioBeatEvaluator.SelectionInput input = new ScenarioBeatEvaluator.SelectionInput();
        input.caseId = "stable_narrow";

        ScenarioBeatEvaluator.SelectionResult mockResult = new ScenarioBeatEvaluator.SelectionResult();
        mockResult.reason = "ok";
        when(evaluator.select(input)).thenReturn(mockResult);

        ScenarioBeatEvaluator.SelectionResult r = processor.process(null, input);

        assertSame(mockResult, r);
        verify(evaluator, times(1)).select(input);
        verifyNoInteractions(context);
    }
}
