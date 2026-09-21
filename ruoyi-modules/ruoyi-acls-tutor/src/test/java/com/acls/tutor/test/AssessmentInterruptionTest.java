package com.acls.tutor.test;


import com.ruoyi.acls.tutor.domain.AssessmentInterruption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentInterruptionTest {

    @Test
    void REAL必须pauseSensitive且带rationale() {
        AssessmentInterruption bad = AssessmentInterruption.builder()
            .timeBasis(AssessmentInterruption.TimeBasis.REAL)
            .pauseSensitive(false)
            .build();
        assertFalse(bad.isTimeBasisConsistent());

        AssessmentInterruption good = AssessmentInterruption.builder()
            .timeBasis(AssessmentInterruption.TimeBasis.REAL)
            .pauseSensitive(true)
            .realTimeRationale("D4 评分中断必须按墙上时间计量")
            .build();
        assertTrue(good.isTimeBasisConsistent());
    }

    @Test
    void SIMULATED无需rationale() {
        AssessmentInterruption i = AssessmentInterruption.builder()
            .timeBasis(AssessmentInterruption.TimeBasis.SIMULATED)
            .build();
        assertTrue(i.isTimeBasisConsistent());
    }

    @Test
    void BOTH与REAL同规则() {
        AssessmentInterruption bad = AssessmentInterruption.builder()
            .timeBasis(AssessmentInterruption.TimeBasis.BOTH)
            .pauseSensitive(true)
            .realTimeRationale(" ")
            .build();
        assertFalse(bad.isTimeBasisConsistent());
    }
}
