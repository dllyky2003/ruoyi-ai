package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.Duty;
import com.ruoyi.ems.npc.TeamManager;
import com.ruoyi.ems.orchestrator.model.*;
import com.ruoyi.ems.orchestrator.service.*;
import com.ruoyi.ems.spel.SpELExpressionEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 编排解释器（OrchestrationInterpreter）集成测试
 * 覆盖核心编排能力：串行/并行执行、前置条件拦截、持续任务、安全校验、动态参数等
 */
class OrchestrationInterpreterIntegrationTest {

    // -------------------- 核心组件（被测试对象） --------------------
    private OrchestrationInterpreter interpreter;
    private AtomicActionDispatcher dispatcher;
    private StageExecutor executor;
    private PreconditionValidator validator;
    private SpELExpressionEvaluator spelEvaluator;
    private ContinuousTaskScheduler scheduler;

    // -------------------- Mock 依赖 --------------------
    @Mock private TeamManager teamManager;
    @Mock private AtomicActionRegistry actionRegistry;
    @Mock private PlanExecutor planExecutor;  // ★ 新增 mock

    // -------------------- 测试前置/后置 --------------------
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 1. 初始化 SpEL 评估器
        spelEvaluator = new SpELExpressionEvaluator();
        spelEvaluator.init();

        // 2. Mock 原子动作元数据
        mockAtomicMeta("IV_PUSH", "IV_MED", 2);
        mockAtomicMeta("CHARGE_DEFIB", "MONITOR_DEFIB", 5);
        mockAtomicMeta("DISCHARGE_SHOCK", "MONITOR_DEFIB", 1);
        mockAtomicMeta("FLUSH_LINE", "IV_MED", 2);

        // 3. 手动组装依赖链
        dispatcher = new AtomicActionDispatcher(teamManager, actionRegistry, spelEvaluator);
        validator = new PreconditionValidator(spelEvaluator);
        executor = new StageExecutor(dispatcher);
        scheduler = new ContinuousTaskScheduler();

        // ★ 4. 使用完整构造函数创建 OrchestrationInterpreter
        interpreter = new OrchestrationInterpreter(
            validator,
            executor,
            scheduler,
            dispatcher,
            teamManager,
            planExecutor
        );
    }

    private void mockAtomicMeta(String refAtomic, String actor, int duration) {
        AtomicActionRegistry.AtomicActionMeta meta = new AtomicActionRegistry.AtomicActionMeta();
        meta.setActor(actor);
        meta.setDurationSec(duration);
        when(actionRegistry.getMeta(refAtomic)).thenReturn(meta);
        when(actionRegistry.getDuration(refAtomic)).thenReturn(duration);
    }

    @AfterEach
    void tearDown() {
        // 清理持续任务线程池
        scheduler.shutdown();
    }

    // ================================================================
    // 测试用例1：并行执行（parallel Stage）
    // ================================================================
    @Test
    void testParallelExecution() throws Exception {
        final String sessionId = "test-session";
        final CaseContext context = new CaseContext();

        TaskConfig task1 = new TaskConfig();
        task1.setRefAtomic("IV_PUSH");
        task1.setActor("IV_MED");
        task1.setParams(Map.of("dose", "6mg"));

        TaskConfig task2 = new TaskConfig();
        task2.setRefAtomic("CHARGE_DEFIB");
        task2.setActor("MONITOR_DEFIB");
        task2.setParams(Map.of("energy", "100J"));

        StageConfig stage = new StageConfig();
        stage.setId("parallel_stage");
        stage.setType("parallel");
        stage.setTasks(List.of(task1, task2));

        OrchestrationConfig config = new OrchestrationConfig();
        config.setDescription("并行测试");
        config.setPreconditions(new PreconditionConfig());
        config.setStages(List.of(stage));

        when(teamManager.assignTaskForDuty(eq(sessionId), any(Duty.class), anyString(), anyInt()))
            .thenReturn(true);

        interpreter.loadContext(sessionId, config, context);
        interpreter.execute();

        verify(teamManager, times(1))
            .assignTaskForDuty(eq(sessionId), eq(Duty.IV_MED), contains("IV_PUSH"), eq(2));
        verify(teamManager, times(1))
            .assignTaskForDuty(eq(sessionId), eq(Duty.MONITOR_DEFIB), contains("CHARGE_DEFIB"), eq(5));

        assertFalse(interpreter.isRunning());
        assertFalse(interpreter.isAborted());
    }

    // ================================================================
    // 测试用例2：前置条件拦截
    // ================================================================
    @Test
    void testPreconditionBlock() throws Exception {
        final String sessionId = "test-session";
        final CaseContext context = new CaseContext();

        OrchestrationConfig config = new OrchestrationConfig();
        config.setDescription("前置条件拦截测试");

        PreconditionConfig pre = new PreconditionConfig();
        pre.setRequiredIntents(List.of("G1-1"));
        config.setPreconditions(pre);

        TaskConfig task = new TaskConfig();
        task.setRefAtomic("IV_PUSH");
        task.setActor("IV_MED");
        StageConfig stage = new StageConfig();
        stage.setId("stage1");
        stage.setType("atomic");
        stage.setTask(task);
        config.setStages(List.of(stage));

        interpreter.loadContext(sessionId, config, context);
        interpreter.execute();

        assertTrue(interpreter.isAborted());
        verify(teamManager, never()).assignTaskForDuty(anyString(), any(), anyString(), anyInt());
    }

    // ================================================================
    // 测试用例3：持续任务（自动停止）
    // ================================================================
    @Test
    void testContinuousTask() throws Exception {
        final String sessionId = "test-session";
        final CaseContext context = new CaseContext();

        ContinuousTaskConfig ctc = new ContinuousTaskConfig();
        ctc.setEnabled(true);
        ctc.setCycleDurationSeconds(1);
        ctc.setLoopCount(3);
        ctc.setInterruptOn(List.of("G2-5", "S3-1"));
        ctc.setAutoReportIntervalSeconds(1);
        FatigueRotationConfig fatigue = new FatigueRotationConfig();
        fatigue.setEveryCycles(2);
        fatigue.setRotateDuty("CPR");
        ctc.setFatigueRotation(fatigue);

        OrchestrationConfig config = new OrchestrationConfig();
        config.setDescription("持续任务测试");
        config.setPreconditions(new PreconditionConfig());
        config.setStages(List.of());
        config.setContinuousTask(ctc);

        interpreter.loadContext(sessionId, config, context);
        interpreter.execute();

        assertTrue(scheduler.isRunning(sessionId));

        int totalWait = 0;
        while (scheduler.isRunning(sessionId) && totalWait < 8000) {
            Thread.sleep(200);
            totalWait += 200;
        }

        assertFalse(scheduler.isRunning(sessionId));
    }

    // ================================================================
    // 测试用例4：持续任务中断
    // ================================================================
    @Test
    void testContinuousTaskInterrupt() throws Exception {
        final String sessionId = "test-session";
        final CaseContext context = new CaseContext();

        ContinuousTaskConfig ctc = new ContinuousTaskConfig();
        ctc.setEnabled(true);
        ctc.setCycleDurationSeconds(10);
        ctc.setLoopCount(null);
        ctc.setInterruptOn(List.of("G2-5", "S3-1"));

        OrchestrationConfig config = new OrchestrationConfig();
        config.setDescription("中断测试");
        config.setPreconditions(new PreconditionConfig());
        config.setStages(List.of());
        config.setContinuousTask(ctc);

        interpreter.loadContext(sessionId, config, context);
        interpreter.execute();

        assertTrue(scheduler.isRunning(sessionId));

        interpreter.interrupt("G2-5");
        Thread.sleep(500);

        assertFalse(scheduler.isRunning(sessionId));
    }

    // ================================================================
    // 测试用例5：安全硬校验
    // ================================================================
    @Test
    void testSafetyGuardInterceptor() {
        final String sessionId = "test-session";
        Map<String, Object> params = Map.of("energy", "300J");

        // 能量超限
        try {
            dispatcher.dispatch(sessionId, "CHARGE_DEFIB", "MONITOR_DEFIB", params, null, new CaseContext());
            fail("应抛出 SafetyGuardException");
        } catch (AtomicActionDispatcher.SafetyGuardException e) {
            // expected
        }

        // 腺苷剂量超限
        params = Map.of("dose", "100mg");
        try {
            dispatcher.dispatch(sessionId, "IV_PUSH", "IV_MED", params, null, new CaseContext());
            fail("应抛出 SafetyGuardException");
        } catch (AtomicActionDispatcher.SafetyGuardException e) {
            // expected
        }
    }

    // ================================================================
    // 测试用例6：完整编排流程（模拟 S2-2 腺苷推注）
    // ================================================================
    @Test
    void testRealJsonConfigLoading() throws Exception {
        final String sessionId = "test-session";
        final CaseContext context = new CaseContext();
        context.setCaseType("stable_narrow");
        context.setVar("systolic_bp", 120);
        context.setVar("adenosine_dose_count", 0);

        OrchestrationConfig config = new OrchestrationConfig();
        config.setDescription("S2-2 腺苷推注");

        PreconditionConfig pre = new PreconditionConfig();
        pre.setRequiredIntents(List.of("G1-1"));
        pre.setRequiredState(Map.of("systolic_bp", 120));
        config.setPreconditions(pre);

        TaskConfig prepare = new TaskConfig();
        prepare.setRefAtomic("IV_PUSH");
        prepare.setActor("IV_MED");
        prepare.setParams(Map.of("dose", "6mg"));

        TaskConfig charge = new TaskConfig();
        charge.setRefAtomic("CHARGE_DEFIB");
        charge.setActor("MONITOR_DEFIB");
        charge.setParams(Map.of("energy", "100J"));

        StageConfig parallelStage = new StageConfig();
        parallelStage.setId("stage_A");
        parallelStage.setType("parallel");
        parallelStage.setTasks(List.of(prepare, charge));

        TaskConfig flush = new TaskConfig();
        flush.setRefAtomic("FLUSH_LINE");
        flush.setActor("IV_MED");
        StageConfig flushStage = new StageConfig();
        flushStage.setId("stage_B");
        flushStage.setType("atomic");
        flushStage.setTask(flush);

        config.setStages(List.of(parallelStage, flushStage));

        when(teamManager.assignTaskForDuty(eq(sessionId), any(Duty.class), anyString(), anyInt()))
            .thenReturn(true);

        interpreter.loadContext(sessionId, config, context);
        interpreter.addCompletedIntent("G1-1");
        interpreter.execute();

        verify(teamManager, times(3))
            .assignTaskForDuty(eq(sessionId), any(Duty.class), anyString(), anyInt());

        assertFalse(interpreter.isRunning());
        assertFalse(interpreter.isAborted());
    }
}
