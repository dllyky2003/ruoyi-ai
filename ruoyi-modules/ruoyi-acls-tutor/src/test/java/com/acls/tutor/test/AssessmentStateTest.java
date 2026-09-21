package com.acls.tutor.test;

import com.ruoyi.acls.tutor.domain.AssessmentInterruption;
import com.ruoyi.acls.tutor.domain.AssessmentState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentStateTest {

    @Test
    void markUnevaluable_应同步到ScoreSnapshot() {
        AssessmentState s = new AssessmentState();
        s.markUnevaluable("TAG_QRS_CHECK", "INSTRUCTOR_OVERRIDE");

        assertTrue(s.isUnevaluable("TAG_QRS_CHECK"));
        assertEquals("INSTRUCTOR_OVERRIDE", s.getUnevaluableItems().get("TAG_QRS_CHECK"));
        assertTrue(s.getScoreSnapshot().getUnevaluableItems().contains("TAG_QRS_CHECK"));
    }

    @Test
    void markUnevaluable_重复标记应保留首因() {
        AssessmentState s = new AssessmentState();
        s.markUnevaluable("TAG_X", "INSTRUCTOR_OVERRIDE");
        s.markUnevaluable("TAG_X", "CLOCK_PAUSED");
        assertEquals("INSTRUCTOR_OVERRIDE", s.getUnevaluableItems().get("TAG_X"));
        assertEquals(1, s.getScoreSnapshot().getUnevaluableItems().size());
    }

    @Test
    void addInterruption_应聚合UnevaluableItems() {
        AssessmentState s = new AssessmentState();
        AssessmentInterruption i = AssessmentInterruption.builder()
            .interruptionId("I-001")
            .reason(AssessmentInterruption.InterruptionReason.INSTRUCTOR_OVERRIDE)
            .timeBasis(AssessmentInterruption.TimeBasis.SIMULATED)
            .unevaluableItems(Map.of("TAG_QRS_CHECK", "INSTRUCTOR_OVERRIDE"))
            .ongoing(true)
            .build();

        s.addInterruption(i);

        assertTrue(s.isUnevaluable("TAG_QRS_CHECK"));
        assertTrue(s.hasOngoingInterruption());
        assertEquals(1, s.getInterruptions().size());
    }

    @Test
    void 无中断时hasOngoing应false() {
        assertFalse(new AssessmentState().hasOngoingInterruption());
    }

    @Test
    void 空或空白itemId应被忽略() {
        AssessmentState s = new AssessmentState();
        s.markUnevaluable(null, "X");
        s.markUnevaluable("", "X");
        s.markUnevaluable("   ", "X");
        assertTrue(s.getUnevaluableItems().isEmpty());
        assertTrue(s.getScoreSnapshot().getUnevaluableItems().isEmpty());
    }
}
