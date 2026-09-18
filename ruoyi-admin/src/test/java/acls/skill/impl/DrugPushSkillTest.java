package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.engine.CommandContext;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.effect.EffectManager;
import com.ruoyi.ems.npc.TeamMember;
import com.ruoyi.ems.npc.VrClientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DrugPushSkill 端到端验证 —— 走真实 EmsContextManager / MapWriteHelper / EffectManager。
 *
 * <p><b>为何 webEnvironment = RANDOM_PORT？</b>
 * EMS 模块有 ServerEndpointExporter Bean（WebSocketConfig），
 * 它要求真实的 Servlet 容器提供 jakarta.websocket.server.ServerContainer。
 * 默认 MOCK 环境没有真实容器 → Bean 初始化抛 IllegalStateException。
 * RANDOM_PORT 会启动真实 Undertow（随机端口），问题消失。
 */
@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DrugPushSkillTest {

    @Autowired private EmsContextManager contextManager;
    @Autowired private MapWriteHelper mapWriteHelper;
    @Autowired private EffectManager effectManager;

    /** mock 具体类 VrClientService（VirtualMemberFactory 依赖它） */
    @MockBean private VrClientService vrClientService;

    private DrugPushSkill skill;
    private String sessionId;
    private TeamMember executor;

    @BeforeEach
    void setUp() {
        when(vrClientService.sendAction(
            anyString(), anyString(), anyMap(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(null));

        skill = new DrugPushSkill(effectManager, mapWriteHelper, "epinephrine");
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
    // 用例 1：单次推注
    // ============================================================
    @Test
    @DisplayName("肾上腺素 1mg 推注：写 Map + 追加事件 + 更新扁平量")
    void testEpinephrinePush_once() {
        CommandContext ctx = newCommandContext();

        Map<String, Object> slots = Map.of(
            "drug", "epinephrine",
            "dose", "1mg",
            "route", "IV"
        );

        CommandResult result = skill.execute(ctx, slots, executor);
        assertThat(result.isSuccess())
            .as("execute() 应返回成功，实际: " + result)
            .isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        // ---- 1) intervention.epinephrine Map ----
        Object raw = after.getVariables().get("intervention.epinephrine");
        assertThat(raw)
            .as("intervention.epinephrine 应是 Map；若为 null 检查 writable_by 是否允许 EXECUTOR")
            .isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        assertThat(map.get("status")).isEqualTo("given");
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(1);
        assertThat(map.get("lastDose")).isEqualTo("1mg");
        assertThat(map.get("lastRoute")).isEqualTo("IV");
        assertThat(((Number) map.get("totalDose")).doubleValue()).isEqualTo(1.0);
        assertThat(map.get("lastTime")).as("lastTime 应为非空时间戳").isNotNull();

        // ---- 2) case.eventLog ----
        Object rawLog = after.getVariables().get("case.eventLog");
        assertThat(rawLog).as("case.eventLog 应是 List").isInstanceOf(List.class);
        List<?> log = (List<?>) rawLog;
        assertThat(log).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) log.get(0);
        assertThat(entry.get("code")).isEqualTo("EPINEPHRINE_PUSH");
        assertThat(entry.get("source")).isEqualTo("EXECUTOR");
        assertThat(entry.get("time")).as("事件时间戳非空").isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) entry.get("params");
        assertThat(params).containsEntry("drug", "epinephrine")
            .containsEntry("dose", "1mg")
            .containsEntry("route", "IV");

        // ---- 3) 扁平量 ----
        assertThat(after.getVariables().get("intervention.lastDrug")).isEqualTo("epinephrine");
        assertThat(after.getVariables().get("intervention.lastDrugDose")).isEqualTo("1mg");
        assertThat(after.getVariables().get("intervention.lastDrugRoute")).isEqualTo("IV");
        assertThat(after.getVariables().get("intervention.lastDrugTime")).isNotNull();
    }

    // ============================================================
    // 用例 2：两次推注累加
    // ============================================================
    @Test
    @DisplayName("肾上腺素二次推注：count/totalDose 累加，事件日志 2 条")
    void testEpinephrinePush_twice() {
        Map<String, Object> slots = Map.of(
            "drug", "epinephrine",
            "dose", "1mg",
            "route", "IV"
        );

        assertThat(skill.execute(newCommandContext(), slots, executor).isSuccess()).isTrue();
        assertThat(skill.execute(newCommandContext(), slots, executor).isSuccess()).isTrue();

        CaseContext after = contextManager.getSnapshot(sessionId);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)
            after.getVariables().get("intervention.epinephrine");
        assertThat(((Number) map.get("count")).intValue()).isEqualTo(2);
        assertThat(((Number) map.get("totalDose")).doubleValue()).isEqualTo(2.0);

        List<?> log = (List<?>) after.getVariables().get("case.eventLog");
        assertThat(log).as("两次推注 → 两条事件").hasSize(2);
    }

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
