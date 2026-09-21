package com.acls.tutor.test;


import com.ruoyi.ems.domain.CaseContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaseContextEventSequenceTest {

    @Test
    void advanceEventSequence_递增且不动version() {
        CaseContext ctx = new CaseContext("S-1");
        long v0 = ctx.getVersion();

        assertEquals(1L, ctx.advanceEventSequence());
        assertEquals(2L, ctx.advanceEventSequence());

        assertEquals(2L, ctx.getEventSequence());
        assertEquals(v0, ctx.getVersion(), "eventSequence 不应影响 version");
    }

    @Test
    void copy_应保留eventSequence() {
        CaseContext ctx = new CaseContext("S-1");
        ctx.advanceEventSequence();
        ctx.advanceEventSequence();

        CaseContext c = ctx.copy();
        assertEquals(2L, c.getEventSequence());
        // 副本推进不应影响原件
        c.advanceEventSequence();
        assertEquals(3L, c.getEventSequence());
        assertEquals(2L, ctx.getEventSequence());
    }

    @Test
    void 构造后eventSequence应为0() {
        assertEquals(0L, new CaseContext("S-1").getEventSequence());
    }
}
