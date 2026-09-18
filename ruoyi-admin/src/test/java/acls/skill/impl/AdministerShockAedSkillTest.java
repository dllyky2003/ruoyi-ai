package acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.skill.impl.AdministerShockAedSkill;
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
class AdministerShockAedSkillTest {

    @Autowired private EmsContextManager contextManager;
    @Autowired private MapWriteHelper mapWriteHelper;

    /** mock 具体类 VrClientService（VirtualMemberFactory 依赖它） */
    @MockitoBean private VrClientService vrClientService;

    private AdministerShockAedSkill skill;
    private String sessionId;
    private TeamMember executor;

    private static final String MAP_KEY    = "intervention.defib";
    private static final String EVENT_CODE = "DEFIB_SHOCK";

    @BeforeEach
    void setUp() {
        when(vrClientService.sendAction(
            anyString(), anyString(), anyMap(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(null));

        skill = new AdministerShockAedSkill(mapWriteHelper);
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
    // 用例 1：单次 200J 非同步除颤
    // ============================================================
    @Test
    @DisplayName("AED 单次 200J 非同步：写 Map + 追加事件 + 更新扁平量")
    void testAedShock_once() {
        Map<String, Object> slots = Map.of(
            "energy", 200,
            "sync",   false
        );

        CommandResult result = skill.execute(newCommandContext(), slots, executor);
        assertThat(result.isSuccess())
            .as("execute() 应返回成功，实际: " + result)
            .isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        // ---- 1) intervention.defib Map ----
        Object raw = after.getVariables().get(MAP_KEY);
        assertThat(raw)
            .as(MAP_KEY + " 应是 Map；若为 null 检查 writable_by 是否允许 EXECUTOR")
            .isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        assertThat(map.get("status")).isEqualTo("performed");
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(1);
        assertThat(((Number) map.get("lastEnergy")).intValue()).isEqualTo(200);
        assertThat(map.get("lastSync")).isEqualTo(false);
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
            .containsEntry("energy", 200)
            .containsEntry("sync", false);

        // ---- 3) 扁平量 ----
        assertThat(((Number) after.getVariables().get("device.lastShockEnergy")).intValue())
            .isEqualTo(200);
        assertThat(after.getVariables().get("device.lastShockSync")).isEqualTo(false);
    }

    // ============================================================
    // 用例 2：两次除颤（200J → 360J 递增）累加
    // ============================================================
    @Test
    @DisplayName("AED 二次除颤：count 累加，事件日志 2 条")
    void testAedShock_twice() {
        Map<String, Object> s1 = Map.of("energy", 200, "sync", false);
        Map<String, Object> s2 = Map.of("energy", 360, "sync", false);

        assertThat(skill.execute(newCommandContext(), s1, executor).isSuccess()).isTrue();
        assertThat(skill.execute(newCommandContext(), s2, executor).isSuccess()).isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)
            after.getVariables().get(MAP_KEY);
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(2);
        assertThat(((Number) map.get("lastEnergy")).intValue()).isEqualTo(360);
        assertThat(map.get("lastSync")).isEqualTo(false);

        List<?> log = (List<?>) after.getVariables().get("case.eventLog");
        assertThat(log).as("两次除颤 → 两条事件").hasSize(2);
        assertThat(((Map<?, ?>) log.get(0)).get("code")).isEqualTo(EVENT_CODE);
        assertThat(((Map<?, ?>) log.get(1)).get("code")).isEqualTo(EVENT_CODE);

        // 扁平量应保持最后一次
        assertThat(((Number) after.getVariables().get("device.lastShockEnergy")).intValue())
            .isEqualTo(360);
    }

    // ============================================================
    // 用例 3：energy 越界（50J < 100J 下限）
    // ============================================================
// ============================================================
// 用例 3：energy 越界（50J < 100J 下限）
// 注意：execute 不做校验，拦截点在 validateParams（外部调度器职责）
// ============================================================
    @Test
    @DisplayName("AED energy=50 越界应失败（下限 100J）")
    void testAedShock_energyOutOfRange() {
        Map<String, Object> slots = Map.of("energy", 50, "sync", false);
        CommandContext ctx = newCommandContext();

        // ★ 校验入口是 validateParams（不是 execute）
        CommandResult v = skill.validateParams(ctx, slots);
        assertThat(v.isSuccess())
            .as("50J 应触发下限校验失败，实际: " + v)
            .isFalse();

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
