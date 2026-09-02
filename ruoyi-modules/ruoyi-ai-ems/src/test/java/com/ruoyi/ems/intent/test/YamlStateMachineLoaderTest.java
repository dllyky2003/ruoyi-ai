package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.VariableMetaManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest(classes = {YamlStateMachineLoaderTest.TestConfig.class})
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class YamlStateMachineLoaderTest {

    @Autowired
    private YamlStateMachineLoader loader;

    // ==================== 通用基础测试（自动适配所有案例） ====================

    @Test
    void shouldLoadAtLeastOneConfig() {
        Set<String> caseTypes = loader.getLoadedCaseTypes();
        assertThat(caseTypes).isNotEmpty();
    }

    @Test
    void shouldValidateAllCasesWithoutException() {
        // 确保加载和变量校验不抛异常（loadAllConfigs 在 @PostConstruct 已执行）
        assertThatNoException()
            .isThrownBy(() -> loader.loadAllConfigs());
    }

    @Test
    void shouldHaveBasicStructureForAllCases() {
        for (String caseId : loader.getLoadedCaseTypes()) {
            ResolvedCaseTypeConfig config = loader.getConfig(caseId);
            assertThat(config).as("配置 %s 不应为 null", caseId).isNotNull();
            assertThat(config.getStates()).as("案例 %s 应有状态", caseId).isNotEmpty();
            assertThat(config.getTransitions()).as("案例 %s 应有转移规则", caseId).isNotEmpty();
            assertThat(config.getInitialState()).as("案例 %s 应定义初始状态", caseId).isNotBlank();
            assertThat(config.getGlobalEventIntents()).as("案例 %s 应有全局事件意图", caseId).isNotEmpty();
            // 可选：检查所有状态是否在转移中被引用
        }
    }

    // ==================== 特定案例测试（条件化，有则验证） ====================

    @Test
    void shouldLoadNarrowQrsCaseIfExists() {
        if (!loader.exists("tachycardia_stable_narrow")) {
            return; // 忽略，支持未来删除该案例
        }
        ResolvedCaseTypeConfig narrow = loader.getConfig("tachycardia_stable_narrow");
        assertThat(narrow).isNotNull();
        assertThat(narrow.getStates()).contains(
            "CHECK_QRS_WIDTH",
            "NARROW_QRS_CHECK_RHYTHM",
            "DO_VAGAL_MANEUVER",
            "DO_ADENOSINE_6MG",
            "MONITOR_SINUS_RHYTHM"
        );
        assertThat(narrow.getTransitions()).hasSizeGreaterThan(20);
    }

    @Test
    void shouldHaveCorrectConditionExpressionsForNarrowCase() {
        if (!loader.exists("tachycardia_stable_narrow")) {
            return;
        }
        ResolvedCaseTypeConfig narrow = loader.getConfig("tachycardia_stable_narrow");

        // 测试 CHECK_QRS_WIDTH 相关的条件（3条转移）
        List<TransitionRule> qrsTransitions = narrow.getTransitions().stream()
            .filter(t -> "CHECK_QRS_WIDTH".equals(t.getSource()) && "S1-1".equals(t.getIntentCode()))
            .toList();
        assertThat(qrsTransitions).hasSize(3);

        TransitionRule narrowRule = qrsTransitions.stream()
            .filter(t -> "S1-1_narrow".equals(t.getEvent()))
            .findFirst()
            .orElse(null);
        assertThat(narrowRule).isNotNull();
        assertThat(narrowRule.getCondition()).isEqualTo("#asserted_qrs_width == 'narrow'");

        TransitionRule wideRule = qrsTransitions.stream()
            .filter(t -> "S1-1_wide".equals(t.getEvent()))
            .findFirst()
            .orElse(null);
        assertThat(wideRule).isNotNull();
        assertThat(wideRule.getCondition()).isEqualTo("#asserted_qrs_width == 'wide'");

        // 测试稳定性评估的条件（只有稳定分支有条件，重复评估无条件）
        List<TransitionRule> stabilityTransitions = narrow.getTransitions().stream()
            .filter(t -> "ASSESS_STABILITY".equals(t.getSource()) && "S1-3".equals(t.getIntentCode()))
            .filter(t -> !"true".equals(t.getCondition()))  // 排除无条件转移
            .toList();
        assertThat(stabilityTransitions).hasSize(1);
        assertThat(stabilityTransitions.get(0).getCondition()).isEqualTo("#asserted_stable == true");
    }

    // ==================== 已废弃的继承相关测试（移除） ====================
    // 如果您仍希望保留对父流程存在的检查，可改为条件化：
    @Test
    void shouldLoadGeneralAclsIfExists() {
        if (loader.exists("GENERAL_ACLS")) {
            ResolvedCaseTypeConfig general = loader.getConfig("GENERAL_ACLS");
            assertThat(general).isNotNull();
            assertThat(general.getStates()).contains("INITIAL_SETUP", "ASSESS_STABILITY");
            assertThat(general.getGlobalEventIntents()).isNotEmpty();
            assertThat(general.getTransitions()).isNotEmpty();
        }
        // 如果不存在，测试通过（不强制）
    }

    // ==================== 测试配置 ====================

    @Configuration
    @Import({YamlStateMachineLoader.class, VariableMetaManager.class})
    static class TestConfig {
        @Bean
        public ResourcePatternResolver resourcePatternResolver() {
            return new PathMatchingResourcePatternResolver();
        }
    }
}
