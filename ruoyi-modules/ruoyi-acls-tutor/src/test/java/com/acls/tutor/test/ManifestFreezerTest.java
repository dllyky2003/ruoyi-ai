package com.acls.tutor.test;

import com.ruoyi.acls.engine.ClockService;
import com.ruoyi.acls.contract.util.CaseFileReviewStatusResolver;
import com.ruoyi.acls.tutor.manifest.AttemptManifest;
import com.ruoyi.acls.tutor.manifest.ManifestFreezer;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ManifestFreezer 单元测试。
 *
 * <p>策略：
 * <ul>
 *   <li>contextManager / clockService 用 Mockito mock，控制双序列与双时钟读数；</li>
 *   <li>resourceLoader 用真 {@link DefaultResourceLoader}，从 test classpath 读
 *       {@code config/acls/cases/stable_narrow/*.yaml}，验证哈希计算链路；</li>
 *   <li>reviewStatusResolver 用真 {@link CaseFileReviewStatusResolver} 注入，
 *       同样走真 classpath 读取，验证阶段 4.1 审核状态读取链路；</li>
 *   <li>依赖 tutor 模块测试 classpath 上确有这些 YAML（主资源目录透传）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ManifestFreezerTest {

    private static final String SID = "S-1";
    private static final String CASE_ID = "stable_narrow";
    private static final String PLAN_ID = "LP_STABLE_NARROW_001";
    private static final String VER = "0.1.0";

    @Mock EmsContextManager contextManager;
    @Mock ClockService clockService;
    @InjectMocks ManifestFreezer freezer;

    @BeforeEach
    void setup() {
        // resourceLoader / reviewStatusResolver 无对应 @Mock，@InjectMocks 不会填，手动注入真实对象
        ResourceLoader rl = new DefaultResourceLoader();
        ReflectionTestUtils.setField(freezer, "resourceLoader", rl);
        ReflectionTestUtils.setField(freezer, "reviewStatusResolver",
            new CaseFileReviewStatusResolver(rl));
    }

    // ============================================================
    // 1. 正常路径
    // ============================================================

    @Test
    void 正常冻结应产出完整清单() {
        CaseContext ctx = new CaseContext(SID);
        ctx.advanceEventSequence();
        ctx.advanceEventSequence();
        ctx.advanceEventSequence();

        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(12.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(45.5);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST");

        assertNotNull(m.getManifestId());
        assertEquals(SID, m.getSessionId());
        assertEquals(SID, m.getAttemptId());
        assertEquals(CASE_ID, m.getCaseId());
        assertEquals(VER, m.getCaseVersion());
        assertEquals(PLAN_ID, m.getPlanId());
        assertEquals(VER, m.getPlanVersion());
        assertEquals(VER, m.getSchemaVersion());

        // 双序列直读
        assertEquals(ctx.getVersion(), m.getStateRevision());
        assertEquals(3L, m.getEventSequence());

        // 双时钟直读
        assertEquals(12.0, m.getSimulatedElapsedSeconds(), 1e-6);
        assertEquals(45.5, m.getAssessmentRealElapsedSeconds(), 1e-6);

        assertTrue(m.isFrozen());
        assertEquals("TEST", m.getFrozenBy());
        assertNotNull(m.getFrozenAt());
        assertTrue(m.isComplete(), "字段齐全 + fileHashes 非空 + frozen=true 应为 complete");
    }

    @Test
    void 正常冻结应包含5个文件哈希且为64位hex() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST");

        assertEquals(5, m.getFileHashes().size(),
            "stable_narrow 下应有 5 个 YAML 全部命中");

        m.getFileHashes().forEach((path, sha) -> {
            assertTrue(path.startsWith("config/acls/cases/" + CASE_ID + "/"),
                "路径前缀应为 config/acls/cases/" + CASE_ID + "/, 实际=" + path);
            assertNotNull(sha, "sha 不应为 null: " + path);
            assertEquals(64, sha.length(), "sha256 hex 应为 64 位: " + path);
            assertTrue(sha.matches("[0-9a-f]{64}"), "sha256 应全小写 hex: " + sha);
        });

        // 关键文件必须命中
        assertTrue(m.getFileHashes().containsKey("config/acls/cases/stable_narrow/clinical_rules.yaml"));
        assertTrue(m.getFileHashes().containsKey("config/acls/cases/stable_narrow/lesson_plan.yaml"));
        assertTrue(m.getFileHashes().containsKey("config/acls/cases/stable_narrow/scenario_beats.yaml"));
        assertTrue(m.getFileHashes().containsKey("config/acls/cases/stable_narrow/fallback_paths.yaml"));
        assertTrue(m.getFileHashes().containsKey("config/acls/cases/stable_narrow/instructor_overrides.yaml"));
    }

    /**
     * 阶段 4.1 前：YAML 全是 pending 占位，此测试断言 pending。
     * 阶段 4.2 YAML 转正后，此测试的断言需改为 approved（方法名同步改）。
     */
    @Test
    void reviewStatus阶段4应读到YAML真实值() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST");

        assertEquals(5, m.getReviewStatus().size());

        m.getReviewStatus().forEach((k, v) ->
            assertEquals("approved", v, "YAML 转正后应为 approved: " + k));
    }

    // ============================================================
    // 2. 参数与前置校验
    // ============================================================

    @Test
    void sessionId为空应抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            freezer.freeze(null, CASE_ID, VER, PLAN_ID, VER, VER, "TEST"));
    }

    @Test
    void caseId为空应抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
            freezer.freeze(SID, null, VER, PLAN_ID, VER, VER, "TEST"));
    }

    @Test
    void 会话快照不存在应抛IllegalStateException() {
        // mock 默认返回 null，无需 stub
        assertThrows(IllegalStateException.class, () ->
            freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST"));
    }

    // ============================================================
    // 3. 边界
    // ============================================================

    @Test
    void 不存在的caseId时fileHashes应为空且isComplete为false() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, "nonexistent_case_xyz",
            VER, PLAN_ID, VER, VER, "TEST");

        assertTrue(m.getFileHashes().isEmpty(),
            "不存在的 caseId 下所有文件都不存在，fileHashes 应为空");
        assertFalse(m.isComplete(),
            "fileHashes 为空时 isComplete 应为 false");

        // 阶段 4.1：reviewStatus 走 resolver，文件不存在应回退 pending，不抛
        assertEquals(5, m.getReviewStatus().size());
        m.getReviewStatus().forEach((k, v) ->
            assertEquals("pending", v, "文件不存在应回退 pending: " + k));
    }

    @Test
    void frozenBy为null时回退SYSTEM() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, null);

        assertEquals("SYSTEM", m.getFrozenBy());
    }

    @Test
    void clockService抛异常时双时钟回退0不阻断冻结() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenThrow(new RuntimeException("boom"));
        when(clockService.assessmentRealElapsedSeconds(SID)).thenThrow(new RuntimeException("boom"));

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST");

        assertEquals(0.0, m.getSimulatedElapsedSeconds(), 1e-6);
        assertEquals(0.0, m.getAssessmentRealElapsedSeconds(), 1e-6);
        assertTrue(m.isComplete(), "时钟读取失败不应阻断清单冻结");
    }

    @Test
    void reviewStatusResolver未注入时回退pending不阻断冻结() {
        // 清掉 setup 注入的 resolver，模拟生产环境 resolver 未就绪
        ReflectionTestUtils.setField(freezer, "reviewStatusResolver", null);

        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER, VER, "TEST");

        assertEquals(5, m.getReviewStatus().size());
        m.getReviewStatus().forEach((k, v) ->
            assertEquals("pending", v, "resolver 未注入应回退 pending: " + k));
        assertTrue(m.isComplete(), "resolver 缺失不应阻断冻结");
    }

    // ============================================================
    // 4. 便捷重载
    // ============================================================

    @Test
    void 便捷重载应默认schemaVersion为0_1_0且frozenBy为SYSTEM() {
        CaseContext ctx = new CaseContext(SID);
        when(contextManager.getSnapshot(SID)).thenReturn(ctx);
        when(clockService.elapsedSeconds(SID)).thenReturn(0.0);
        when(clockService.assessmentRealElapsedSeconds(SID)).thenReturn(0.0);

        AttemptManifest m = freezer.freeze(SID, CASE_ID, VER, PLAN_ID, VER);

        assertEquals("0.1.0", m.getSchemaVersion());
        assertEquals("SYSTEM", m.getFrozenBy());
    }
}
