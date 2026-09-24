package com.acls.tutor.test;

import com.ruoyi.ems.clock.ClockService;
import com.ruoyi.acls.tutor.domain.AssessmentInterruption;
import com.ruoyi.acls.tutor.domain.AssessmentState;
import com.ruoyi.acls.tutor.manifest.AttemptManifest;
import com.ruoyi.acls.tutor.manifest.ManifestFreezer;
import com.ruoyi.acls.tutor.service.AssessmentEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssessmentEngineTest {

    private ManifestFreezer freezer;
    private ClockService clockService;
    private AssessmentEngine engine;

    @BeforeEach
    void setUp() {
        freezer = mock(ManifestFreezer.class);
        clockService = mock(ClockService.class);
        engine = new AssessmentEngine(freezer, clockService);
    }

    // ============================================================
    // 状态管理
    // ============================================================

    @Test
    void getOrCreateState应缓存() {
        AssessmentState s1 = engine.getOrCreateState("S-1");
        AssessmentState s2 = engine.getOrCreateState("S-1");
        assertSame(s1, s2);
    }

    @Test
    void getState未创建时返回null() {
        assertNull(engine.getState("S-X"));
    }

    @Test
    void clear应清空所有map() {
        // freeze 需要非 null 返回，才能验证 lastManifestMap 被清空
        AttemptManifest fake = AttemptManifest.builder()
            .manifestId("M-CLEAR")
            .caseId("case")
            .build();
        when(freezer.freeze(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString()))
            .thenReturn(fake);

        engine.getOrCreateState("S-1");
        engine.freezeAttempt("S-1", "case", "v", "plan", "v", "v");

        // 前置：冻结后应有缓存
        assertNotNull(engine.getLastManifest("S-1"));

        engine.clear("S-1");

        assertNull(engine.getState("S-1"));
        assertNull(engine.getLastManifest("S-1"));
    }

    // ============================================================
    // 中断生命周期
    // ============================================================

    @Test
    void beginInterruption应写入state并保持ongoing() {
        when(clockService.elapsedSeconds("S-1")).thenReturn(10.0);
        when(clockService.assessmentRealElapsedSeconds("S-1")).thenReturn(30.0);

        AssessmentInterruption i = engine.beginInterruption(
            "S-1", "stable_narrow",
            AssessmentInterruption.InterruptionReason.CLOCK_PAUSED,
            "CLOCK", "CLOCK",
            AssessmentInterruption.TimeBasis.SIMULATED,
            null, null);

        assertNotNull(i.getInterruptionId());
        assertTrue(i.isOngoing());
        assertEquals(10.0, i.getSimulatedElapsedAtStart(), 1e-6);
        assertEquals(30.0, i.getAssessmentRealElapsedAtStart(), 1e-6);

        AssessmentState s = engine.getState("S-1");
        assertEquals(1, s.getInterruptions().size());
        assertTrue(s.hasOngoingInterruption());
    }

    @Test
    void beginInterruption_REAL无rationale应抛异常() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            engine.beginInterruption(
                "S-1", "stable_narrow",
                AssessmentInterruption.InterruptionReason.CLOCK_PAUSED,
                "CLOCK", "CLOCK",
                AssessmentInterruption.TimeBasis.REAL,
                null, null));
        assertTrue(ex.getMessage().contains("realTimeRationale"));
    }

    @Test
    void beginInterruption_REAL带rationale应成功且pauseSensitive() {
        when(clockService.elapsedSeconds(anyString())).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(anyString())).thenReturn(0.0);

        AssessmentInterruption i = engine.beginInterruption(
            "S-1", "stable_narrow",
            AssessmentInterruption.InterruptionReason.CLOCK_PAUSED,
            "CLOCK", "CLOCK",
            AssessmentInterruption.TimeBasis.REAL,
            "暂停期间仍计入评估真实时间", null);

        assertTrue(i.isPauseSensitive());
        assertTrue(i.isTimeBasisConsistent());
    }

    @Test
    void endInterruption应更新end时间与ongoing() {
        when(clockService.elapsedSeconds("S-1")).thenReturn(5.0, 15.0);
        when(clockService.assessmentRealElapsedSeconds("S-1")).thenReturn(20.0, 40.0);

        AssessmentInterruption i = engine.beginInterruption(
            "S-1", "stable_narrow",
            AssessmentInterruption.InterruptionReason.CLOCK_PAUSED,
            "CLOCK", "CLOCK",
            AssessmentInterruption.TimeBasis.SIMULATED,
            null, null);

        AssessmentInterruption ended = engine.endInterruption("S-1", i.getInterruptionId());

        assertNotNull(ended);
        assertFalse(ended.isOngoing());
        assertNotNull(ended.getEndedAt());
        assertEquals(15.0, ended.getSimulatedElapsedAtEnd(), 1e-6);
        assertEquals(40.0, ended.getAssessmentRealElapsedAtEnd(), 1e-6);

        assertFalse(engine.getState("S-1").hasOngoingInterruption());
    }

    @Test
    void endInterruption_未知名应返回null不抛() {
        assertNull(engine.endInterruption("S-1", "nonexistent-id"));
        assertNull(engine.endInterruption(null, "x"));
        assertNull(engine.endInterruption("S-1", null));
    }

    // ============================================================
    // D4
    // ============================================================

    @Test
    void recordInstructorOverride应立即结束并聚合不可评估项() {
        when(clockService.elapsedSeconds(anyString())).thenReturn(1.0);
        when(clockService.assessmentRealElapsedSeconds(anyString())).thenReturn(2.0);

        AssessmentInterruption i = engine.recordInstructorOverride(
            "S-1", "stable_narrow", "IO_PAUSE_CLOCK",
            Map.of("TAG_QRS_CHECK", "INSTRUCTOR_OVERRIDE"));

        assertNotNull(i);
        assertFalse(i.isOngoing());
        assertTrue(i.isFromInstructorOverride());
        assertEquals(AssessmentInterruption.TimeBasis.SIMULATED, i.getTimeBasis());

        AssessmentState s = engine.getState("S-1");
        assertTrue(s.isUnevaluable("TAG_QRS_CHECK"));
        assertFalse(s.hasOngoingInterruption());
        assertEquals(1, s.getInterruptions().size());
    }

    // ============================================================
    // 冻结
    // ============================================================

    @Test
    void freezeAttempt应调freezer并缓存() {
        AttemptManifest fake = AttemptManifest.builder()
            .manifestId("M-1")
            .caseId("stable_narrow")
            .build();
        when(freezer.freeze("S-1", "stable_narrow", "0.1.0",
            "LP_STABLE_NARROW_001", "0.1.0", "0.1.0", "ASSESSMENT_ENGINE"))
            .thenReturn(fake);

        AttemptManifest m = engine.freezeAttempt("S-1", "stable_narrow",
            "0.1.0", "LP_STABLE_NARROW_001", "0.1.0", "0.1.0");

        assertSame(fake, m);
        assertSame(fake, engine.getLastManifest("S-1"));
    }

    @Test
    void getLastManifest未冻结时返回null() {
        assertNull(engine.getLastManifest("S-X"));
    }

    @Test
    void freezeAttempt_freeze返回null时应跳过缓存不抛() {
        when(freezer.freeze(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString()))
            .thenReturn(null);

        AttemptManifest m = engine.freezeAttempt("S-1", "case", "v", "plan", "v", "v");

        assertNull(m);
        assertNull(engine.getLastManifest("S-1"));
    }
}
