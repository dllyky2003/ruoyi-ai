package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.VariableMetaManager;
import com.ruoyi.ems.domain.VariableMeta;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * VariableMetaManager 独立测试类
 * 不依赖 JUnit/Mockito，直接运行 main 方法测试
 *
 * 自动扫描所有已加载的案例（专属变量），无需硬编码案例名。
 * 新增案例后无需修改测试代码，自动验证所有变量定义。
 */
public class VariableMetaManagerTest {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("🧪 开始测试 VariableMetaManager");
        System.out.println("========================================\n");

        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(TestConfig.class);

        VariableMetaManager manager = context.getBean(VariableMetaManager.class);

        // 1. 基础加载测试
        testLoadVariables(manager);
        // 2. 槽位映射测试
        testResolveSlotMappings(manager);
        // 3. 写入权限测试（遍历所有专属案例）
        testWritePermissionForAllCases(manager);
        // 4. 值校验测试（遍历所有专属案例）
        testValidateValueForAllCases(manager);
        // 5. 打印所有变量（供查看）
        testPrintAllVariables(manager);
        // 6. ★ 自动验证所有加载的案例（通用校验，无需修改）
        testAllLoadedVariables(manager);

        System.out.println("\n========================================");
        System.out.println("✅ 所有测试通过！");
        System.out.println("========================================");

        context.close();
    }

    // ==================== 测试方法 ====================

    private static void testLoadVariables(VariableMetaManager manager) throws Exception {
        System.out.println("📋 测试1: 加载变量");
        System.out.println("----------------------------------------");

        Map<String, VariableMeta> generalVars = manager.getVariablesForCase("unknown_case");
        System.out.println("  通用变量数量: " + generalVars.size());

        Set<String> caseTypes = getLoadedCaseTypes(manager);
        if (caseTypes.isEmpty()) {
            System.out.println("  ⚠️ 没有加载任何专属变量，跳过案例特定检查");
        } else {
            System.out.println("  已加载的专属案例: " + caseTypes);
            String firstCase = caseTypes.iterator().next();
            Map<String, VariableMeta> vars = manager.getVariablesForCase(firstCase);
            System.out.println("  案例 " + firstCase + " 变量数量: " + vars.size());
            assertGeneralVariableExists(manager, "current_rhythm");
            assertGeneralVariableExists(manager, "systolic_bp");
            assertGeneralVariableExists(manager, "heart_rate");
        }

        System.out.println("  ✅ 加载成功\n");
    }

    private static void assertGeneralVariableExists(VariableMetaManager manager, String varName) {
        Map<String, VariableMeta> generalVars = manager.getVariablesForCase("unknown_case");
        if (!generalVars.containsKey(varName)) {
            throw new AssertionError("❌ 通用变量 " + varName + " 不存在");
        }
        System.out.println("    ✅ 通用变量 " + varName + " 存在");
    }

    private static void testResolveSlotMappings(VariableMetaManager manager) {
        System.out.println("📋 测试2: 槽位映射");
        System.out.println("----------------------------------------");

        assertSlotMapping(manager, "qrs_width", "asserted_qrs_width");
        assertSlotMapping(manager, "systolic_bp", "reported_systolic_bp");
        assertSlotMapping(manager, "heart_rate", "reported_heart_rate");
        assertSlotMapping(manager, "outcome", "vagal_effect_observed");
        assertSlotMapping(manager, "rhythm_type", "rhythm_confirmed");

        System.out.println("  ✅ 所有槽位映射正确\n");
    }

    private static void testWritePermissionForAllCases(VariableMetaManager manager) throws Exception {
        System.out.println("📋 测试3: 写入权限（遍历所有专属案例）");
        System.out.println("----------------------------------------");

        Set<String> caseTypes = getLoadedCaseTypes(manager);
        if (caseTypes.isEmpty()) {
            System.out.println("  ⚠️ 没有专属案例，跳过权限测试");
            return;
        }

        for (String caseType : caseTypes) {
            System.out.println("  案例: " + caseType);
            boolean simAllowed = manager.checkWritePermission(caseType, "systolic_bp", "SIMULATOR");
            boolean classAllowed = manager.checkWritePermission(caseType, "systolic_bp", "CLASSIFIER");
            System.out.printf("    systolic_bp: SIMULATOR=%s, CLASSIFIER=%s%n", simAllowed, classAllowed);
            assertTrue(simAllowed, "systolic_bp 应允许 SIMULATOR 写入");
            assertFalse(classAllowed, "systolic_bp 应不允许 CLASSIFIER 写入");

            Map<String, VariableMeta> vars = manager.getVariablesForCase(caseType);
            if (vars.containsKey("adenosine_dose_count")) {
                boolean smAllowed = manager.checkWritePermission(caseType, "adenosine_dose_count", "STATE_MACHINE");
                boolean simAllowed2 = manager.checkWritePermission(caseType, "adenosine_dose_count", "SIMULATOR");
                System.out.printf("    adenosine_dose_count: STATE_MACHINE=%s, SIMULATOR=%s%n", smAllowed, simAllowed2);
                assertTrue(smAllowed, "adenosine_dose_count 应允许 STATE_MACHINE 写入");
                assertFalse(simAllowed2, "adenosine_dose_count 应不允许 SIMULATOR 写入");
            }
        }
        System.out.println("  ✅ 权限校验正确\n");
    }

    private static void testValidateValueForAllCases(VariableMetaManager manager) throws Exception {
        System.out.println("📋 测试4: 值校验（遍历所有专属案例）");
        System.out.println("----------------------------------------");

        Set<String> caseTypes = getLoadedCaseTypes(manager);
        if (caseTypes.isEmpty()) {
            System.out.println("  ⚠️ 没有专属案例，跳过值校验测试");
            return;
        }

        for (String caseType : caseTypes) {
            System.out.println("  案例: " + caseType);
            Map<String, VariableMeta> vars = manager.getVariablesForCase(caseType);

            if (vars.containsKey("adenosine_dose_count")) {
                boolean valid1 = manager.validateValue(caseType, "adenosine_dose_count", 1);
                boolean valid2 = manager.validateValue(caseType, "adenosine_dose_count", 3);
                boolean valid3 = manager.validateValue(caseType, "adenosine_dose_count", 4);
                boolean valid4 = manager.validateValue(caseType, "adenosine_dose_count", -1);
                System.out.printf("    adenosine_dose_count: 1=%s, 3=%s, 4=%s, -1=%s%n", valid1, valid2, valid3, valid4);
                assertTrue(valid1, "值 1 应合法");
                assertTrue(valid2, "值 3 应合法");
                assertFalse(valid3, "值 4 应不合法（超出 max=3）");
                assertFalse(valid4, "值 -1 应不合法（低于 min=0）");
            }

            if (vars.containsKey("vagal_effect_observed")) {
                // ★ 使用 enumOptions 进行校验，测试有效和无效值
                boolean enumValid = manager.validateValue(caseType, "vagal_effect_observed", "success");
                boolean enumInvalid = manager.validateValue(caseType, "vagal_effect_observed", "unknown");
                System.out.printf("    vagal_effect_observed: success=%s, unknown=%s%n", enumValid, enumInvalid);
                assertTrue(enumValid, "success 应在枚举列表中");
                assertFalse(enumInvalid, "unknown 不应在枚举列表中");
            }
        }
        System.out.println("  ✅ 值校验正确\n");
    }

    /**
     * 自动验证所有已加载案例的变量定义是否符合元数据规范。
     * 新增案例后无需修改此方法，它会自动遍历所有 caseType。
     */
    private static void testAllLoadedVariables(VariableMetaManager manager) throws Exception {
        System.out.println("📋 测试6: 自动验证所有加载的案例");
        System.out.println("----------------------------------------");

        Set<String> caseTypes = getLoadedCaseTypes(manager);
        if (caseTypes.isEmpty()) {
            System.out.println("  ⚠️ 没有加载任何专属变量，跳过校验");
            return;
        }

        int totalErrors = 0;
        int totalWarnings = 0;

        for (String caseType : caseTypes) {
            Map<String, VariableMeta> vars = manager.getVariablesForCase(caseType);
            System.out.printf("  验证案例: %s (%d 个变量)%n", caseType, vars.size());

            for (Map.Entry<String, VariableMeta> entry : vars.entrySet()) {
                String varName = entry.getKey();
                VariableMeta meta = entry.getValue();
                boolean hasError = false;

                // --- 通用必填字段检查 ---
                if (meta.getWritableBy() == null || meta.getWritableBy().trim().isEmpty()) {
                    System.err.println("    ❌ 变量 " + varName + " 缺少 writable_by");
                    totalErrors++;
                    hasError = true;
                }

                if (meta.getType() == null || meta.getType().trim().isEmpty()) {
                    System.err.println("    ❌ 变量 " + varName + " 缺少 type");
                    totalErrors++;
                    hasError = true;
                }

                // --- 类型特定约束检查 ---
                if (meta.getType() != null) {
                    String type = meta.getType().toLowerCase();
                    if ("integer".equals(type)) {
                        boolean hasMin = meta.getMin() != null;
                        boolean hasMax = meta.getMax() != null;
                        if (hasMin && !hasMax) {
                            System.err.println("    ❌ 变量 " + varName + " (integer) 定义了 min 但未定义 max");
                            totalErrors++;
                            hasError = true;
                        } else if (!hasMin && hasMax) {
                            System.err.println("    ❌ 变量 " + varName + " (integer) 定义了 max 但未定义 min");
                            totalErrors++;
                            hasError = true;
                        }
                    }
                    if ("string".equals(type)) {
                        // ★ 使用 enumOptions 检查
                        if (meta.getEnumOptions() != null && meta.getEnumOptions().isEmpty()) {
                            System.err.println("    ⚠️ 变量 " + varName + " (string) 定义了空 enum 列表");
                            totalWarnings++;
                        }
                    }
                    if ("boolean".equals(type)) {
                        if (meta.getDefaultValue() != null && !(meta.getDefaultValue() instanceof Boolean)) {
                            System.err.println("    ⚠️ 变量 " + varName + " (boolean) 默认值不是布尔类型");
                            totalWarnings++;
                        }
                    }
                }

                if (!hasError) {
                    // 静默成功
                }
            }
        }

        if (totalErrors > 0) {
            throw new AssertionError("发现 " + totalErrors + " 个严重错误，请修正变量定义。警告数: " + totalWarnings);
        } else if (totalWarnings > 0) {
            System.out.println("  ⚠️ 存在 " + totalWarnings + " 个警告，建议优化变量定义（不影响功能）");
        } else {
            System.out.println("  ✅ 所有变量定义校验通过");
        }
        System.out.println();
    }

    private static void testPrintAllVariables(VariableMetaManager manager) throws Exception {
        System.out.println("📋 测试5: 打印所有变量（供查看）");
        System.out.println("----------------------------------------");

        Set<String> caseTypes = getLoadedCaseTypes(manager);
        if (caseTypes.isEmpty()) {
            System.out.println("  ⚠️ 没有加载任何案例专属变量");
            return;
        }

        for (String caseType : caseTypes) {
            Map<String, VariableMeta> vars = manager.getVariablesForCase(caseType);
            System.out.printf("  📁 %s (%d 个变量):%n", caseType, vars.size());
            int count = 0;
            for (Map.Entry<String, VariableMeta> entry : vars.entrySet()) {
                if (count++ > 10) {
                    System.out.println("    ... 还有 " + (vars.size() - count) + " 个");
                    break;
                }
                VariableMeta meta = entry.getValue();
                System.out.printf("    - %s: %s (默认: %s)%n",
                    entry.getKey(),
                    meta.getType() != null ? meta.getType() : "unknown",
                    meta.getDefaultValue()
                );
            }
        }
        System.out.println();
    }

    // ==================== 反射辅助 ====================

    @SuppressWarnings("unchecked")
    private static Set<String> getLoadedCaseTypes(VariableMetaManager manager) throws Exception {
        Field field = VariableMetaManager.class.getDeclaredField("caseSpecificMetaMap");
        field.setAccessible(true);
        Map<String, Map<String, VariableMeta>> map = (Map<String, Map<String, VariableMeta>>) field.get(manager);
        return map.keySet();
    }

    // ==================== 断言辅助 ====================

    private static void assertSlotMapping(VariableMetaManager manager, String slot, String expectedVar) {
        String actual = manager.resolveSlotToVariable(slot);
        if (!expectedVar.equals(actual)) {
            throw new AssertionError("❌ 槽位 " + slot + " 映射错误: 期望 " + expectedVar + "，实际 " + actual);
        }
        System.out.println("    " + slot + " → " + actual);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("❌ " + message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError("❌ " + message);
    }

    // ==================== 测试配置 ====================

    @Configuration
    @ComponentScan(
        basePackages = "com.ruoyi.ems.context",
        excludeFilters = {
            @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = EmsContextManager.class
            )
        }
    )
    static class TestConfig {

        @Bean
        public ResourcePatternResolver resourcePatternResolver() {
            return new PathMatchingResourcePatternResolver();
        }
    }
}
