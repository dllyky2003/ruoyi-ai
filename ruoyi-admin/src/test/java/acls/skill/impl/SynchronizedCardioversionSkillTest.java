package acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.impl.SynchronizedCardioversionSkill;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SynchronizedCardioversionSkillTest {

    @Autowired private EmsContextManager contextManager;
    @Autowired private MapWriteHelper mapWriteHelper;

    /** mock 具体类 VrClientService（VirtualMemberFactory 依赖它） */
    @MockitoBean private VrClientService vrClientService;

    private SynchronizedCardioversionSkill skill;
    private String sessionId;
    private TeamMember executor;

    private static final String MAP_KEY    = "intervention.cardioversion";
    private static final String EVENT_CODE = "CARDIOVERSION";

    @BeforeEach
    void setUp() {
        when(vrClientService.sendAction(
            anyString(), anyString(), anyMap(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(null));

        skill = new SynchronizedCardioversionSkill(mapWriteHelper);
        sessionId = "test-" + UUID.randomUUID();

        contextManager.getOrCreate(sessionId);
        boolean applied = contextManager.applyResetDefaults(sessionId, "stable_narrow");
        assertThat(applied).as("applyResetDefaults 应成功").isTrue();

        executor = mock(TeamMember.class);
        when(executor.getName()).thenReturn("测试护士");
    }

    @AfterEach
    void tearDown() {
        contextManager.clearContext(sessionId);
    }

    // ============================================================
    // 用例 1：单次 100J 同步电复律
    // ============================================================
    @Test
    @DisplayName("同步电复律 100J：写 Map + 追加事件 + 更新扁平量")
    void testCardioversion_once() {
        Map<String, Object> slots = Map.of(
            "energy",    100,
            "emergency", false
        );

        CommandResult result = skill.execute(newCommandContext(), slots, executor);
        assertThat(result.isSuccess())
            .as("execute() 应返回成功，实际: " + result)
            .isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        // ---- 1) intervention.cardioversion Map ----
        Object raw = after.getVariables().get(MAP_KEY);
        assertThat(raw)
            .as(MAP_KEY + " 应是 Map；若为 null 检查 writable_by 是否允许 EXECUTOR")
            .isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        assertThat(map.get("status")).isEqualTo("performed");
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(1);
        assertThat(((Number) map.get("lastEnergy")).intValue()).isEqualTo(100);
        assertThat(map.get("emergency")).isEqualTo(false);
        assertThat(map.get("lastTime")).as("lastTime 应为非空时间戳").isNotNull();

        // ---- 2) case.eventLog ----
        Object rawLog = after.getVariables().get("case.eventLog");
        assertThat(rawLog).as("case.eventLog 应是 List").isInstanceOf(List.class);
        List<?> log = (List<?>) rawLog;
        assertThat(log).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) log.get(0);
        assertThat(entry.get("code")).isEqualTo(EVENT_CODE);
        assertThat(entry.get("source")).isEqualTo("EXECUTOR");
        assertThat(entry.get("time")).as("事件时间戳非空").isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) entry.get("params");
        assertThat(params)
            .containsEntry("energy", 100)
            .containsEntry("sync", true)
            .containsEntry("emergency", false);

        // ---- 3) 扁平量 ----
        assertThat(((Number) after.getVariables().get("device.lastShockEnergy")).intValue())
            .isEqualTo(100);
        assertThat(after.getVariables().get("device.lastShockSync")).isEqualTo(true);
    }

    // ============================================================
    // 用例 2：两次电复律（100J → 150J 紧急）累加
    // ============================================================
    @Test
    @DisplayName("同步电复律二次：count 累加，事件日志 2 条")
    void testCardioversion_twice() {
        Map<String, Object> s1 = Map.of("energy", 100, "emergency", false);
        Map<String, Object> s2 = Map.of("energy", 150, "emergency", true);

        assertThat(skill.execute(newCommandContext(), s1, executor).isSuccess()).isTrue();
        assertThat(skill.execute(newCommandContext(), s2, executor).isSuccess()).isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)
            after.getVariables().get(MAP_KEY);
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(2);
        assertThat(((Number) map.get("lastEnergy")).intValue()).isEqualTo(150);
        assertThat(map.get("emergency")).isEqualTo(true);

        List<?> log = (List<?>) after.getVariables().get("case.eventLog");
        assertThat(log).as("两次电复律 → 两条事件").hasSize(2);
        assertThat(((Map<?, ?>) log.get(0)).get("code")).isEqualTo(EVENT_CODE);
        assertThat(((Map<?, ?>) log.get(1)).get("code")).isEqualTo(EVENT_CODE);

        // 扁平量应保持最后一次
        assertThat(((Number) after.getVariables().get("device.lastShockEnergy")).intValue())
            .isEqualTo(150);
    }

    // ============================================================
// 用例 3：energy 越界（30J < 50J 下限）
// 注意：execute 不做校验，拦截点在 validateParams（外部调度器职责）
// ============================================================
    @Test
    @DisplayName("同步电复律 energy=30 越界应失败（下限 50J）")
    void testCardioversion_energyOutOfRange() {
        Map<String, Object> slots = Map.of("energy", 30, "emergency", false);
        CommandContext ctx = newCommandContext();

        // ★ 校验入口是 validateParams（不是 execute）
        CommandResult v = skill.validateParams(ctx, slots);
        assertThat(v.isSuccess())
            .as("30J 应触发下限校验失败，实际: " + v)
            .isFalse();

        // 校验失败时不应写 Map —— 真实链路里根本不会继续调 execute
        CaseContext after = contextManager.getSnapshot(sessionId);
        Object raw = after.getVariables().get(MAP_KEY);
        if (raw instanceof Map<?, ?> m) {
            Object countObj = m.get("count");
            int count = (countObj == null) ? 0 : ((Number) countObj).intValue();
            assertThat(count).as("校验失败时不应写 Map").isZero();
        }
    }
    // ============================================================
    // helper
    // ============================================================
    private CommandContext newCommandContext() {
        return new CommandContext(
            sessionId,
            null,
            contextManager.getOrCreate(sessionId),
            null,
            vrClientService,
            null
        );
    }
}
