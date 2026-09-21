package com.acls.tutor.test;

import com.ruoyi.acls.engine.ClockService;
import com.ruoyi.acls.tutor.domain.AssessmentState;
import com.ruoyi.acls.tutor.domain.ScenarioBeat;
import com.ruoyi.acls.tutor.engine.ScenarioBeatEvaluator;
import com.ruoyi.acls.tutor.manifest.ManifestFreezer;
import com.ruoyi.acls.tutor.service.AssessmentEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 阶段 3 丙-2：{@link AssessmentEngine#recordBeatSelection} 的边界测试。
 *
 * <p>只验证"证据链写入"这一件事：写什么、写几条、异常输入不写、
 * 以及写入不越权触发中断 / 不影响不可评估项（P0-8）。</p>
 *
 * <p>不验证 beat 语义（那是 {@code ScenarioBeatEvaluatorTest} 的职责），
 * 也不验证 eventSequence（那是 {@code AttemptEventProcessorTest} 的职责）。</p>
 */
class AssessmentEngineBeatSelectionTest {

    private AssessmentEngine engine;

    @BeforeEach
    void setUp() {
        // 本测试只碰证据链，freezer / clockService 不参与，直接 mock 占位
        engine = new AssessmentEngine(mock(ManifestFreezer.class), mock(ClockService.class));
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 构造一个"正常选中"的结果，调用方可按需覆写字段。 */
    private static ScenarioBeatEvaluator.SelectionResult okResult(String beatId, String outcomeRef) {
        ScenarioBeatEvaluator.SelectionResult r = new ScenarioBeatEvaluator.SelectionResult();
        r.reason = "ok";
        r.selected = new ScenarioBeat();
        r.selected.setBeatId(beatId);
        r.selected.setCaseOutcomeRef(outcomeRef);
        r.conflictDetected = false;
        r.conflictBeatIds = new ArrayList<>();
        return r;
    }

    // ============================================================
    // 空输入防御：不创建 state
    // ============================================================

    @Test
    void result为null时不创建state不写入() {
        assertNull(engine.recordBeatSelection("S-1", null));
        assertNull(engine.getState("S-1"));
    }

    @Test
    void sessionId为null时不创建state不写入() {
        assertNull(engine.recordBeatSelection(null, okResult("B", "O")));
        assertNull(engine.getState(null));
    }

    // ============================================================
    // 主路径
    // ============================================================

    @Test
    void 正常结果写入state证据链() {
        AssessmentState.BeatSelectionRecord rec =
            engine.recordBeatSelection("S-1", okResult("BEAT_X", "OUTCOME_A"));

        assertNotNull(rec);
        assertEquals("BEAT_X", rec.getBeatId());
        assertEquals("OUTCOME_A", rec.getOutcomeRef());
        assertFalse(rec.isConflictDetected());
        assertNotNull(rec.getRecordedAt());

        List<AssessmentState.BeatSelectionRecord> all =
            engine.getState("S-1").getBeatSelections();
        assertEquals(1, all.size());
        assertSame(rec, all.get(0));
    }

    @Test
    void 多次写入累积不覆盖() {
        engine.recordBeatSelection("S-1", okResult("B1", "O1"));
        engine.recordBeatSelection("S-1", okResult("B2", "O2"));

        assertEquals(2, engine.getState("S-1").getBeatSelections().size());
    }

    // ============================================================
    // 冲突 / 无选中场景：仍要留痕
    // ============================================================

    @Test
    void selected为null的冲突场景也记录() {
        ScenarioBeatEvaluator.SelectionResult conflict = new ScenarioBeatEvaluator.SelectionResult();
        conflict.selected = null;
        conflict.conflictDetected = true;
        conflict.conflictBeatIds = new ArrayList<>(List.of("B_OTHER"));
        conflict.reason = "conflict: same group/priority, different outcome";

        AssessmentState.BeatSelectionRecord rec =
            engine.recordBeatSelection("S-1", conflict);

        assertNotNull(rec);
        assertNull(rec.getBeatId());
        assertNull(rec.getOutcomeRef());
        assertTrue(rec.isConflictDetected());
        assertEquals(List.of("B_OTHER"), rec.getConflictBeatIds());
    }

    @Test
    void conflictBeatIds应复制不共享引用() {
        ScenarioBeatEvaluator.SelectionResult r = okResult("B", "O");
        r.conflictBeatIds = new ArrayList<>(List.of("X"));

        AssessmentState.BeatSelectionRecord rec = engine.recordBeatSelection("S-1", r);

        // 改动源列表不应影响已落盘记录
        r.conflictBeatIds.add("Y");
        assertEquals(List.of("X"), rec.getConflictBeatIds());
    }

    // ============================================================
    // 边界：写入证据链不越权（P0-8）
    // ============================================================

    @Test
    void 写入证据链不触发中断() {
        engine.recordBeatSelection("S-1", okResult("B", "O"));

        AssessmentState s = engine.getState("S-1");
        assertFalse(s.hasOngoingInterruption());
        assertTrue(s.getInterruptions().isEmpty());
    }

    @Test
    void 写入证据链不影响unevaluable() {
        engine.recordBeatSelection("S-1", okResult("B", "O"));

        assertFalse(engine.getState("S-1").isUnevaluable("TAG_QRS_CHECK"));
    }

    // ============================================================
    // 与 getBeatSelections 副本语义对齐
    // ============================================================

    @Test
    void getBeatSelections返回副本外部修改不生效() {
        engine.recordBeatSelection("S-1", okResult("B", "O"));

        List<AssessmentState.BeatSelectionRecord> snapshot =
            engine.getState("S-1").getBeatSelections();
        snapshot.clear();

        // 副本被清空，但内部记录仍在
        assertEquals(1, engine.getState("S-1").getBeatSelections().size());
    }
}
