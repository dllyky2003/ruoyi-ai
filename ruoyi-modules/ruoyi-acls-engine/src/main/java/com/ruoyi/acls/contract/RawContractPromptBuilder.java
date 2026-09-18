package com.ruoyi.acls.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawContractPromptBuilder {

    private final YamlStateMachineLoader stateMachineLoader;

    private final PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Value("${acls.prompt.output-dir:config/prompts}")
    private String outputDir;

    // ==================================================================
    // 主入口
    // ==================================================================
    public Path build(String caseId) throws IOException {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) {
            throw new IllegalArgumentException("案例不存在: " + caseId);
        }

        StringBuilder sb = new StringBuilder();

        // -------- [SYSTEM] --------
        sb.append(systemPrompt(caseId));

        // -------- 一、合并后的完整状态机 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 一、合并后的完整状态机（父级 + 子级）\n");
        sb.append("# ============================================================\n");
        sb.append("# 本段由 YamlStateMachineLoader 完成父子继承合并后的结果。\n");
        sb.append("# 所有 target / source 必须是本段 states 列表中的状态。\n");
        sb.append("# 状态总数: ").append(config.getStates().size())
            .append("，转移总数: ").append(config.getTransitions().size())
            .append("，终止状态数: ").append(config.getEndStates().size()).append("\n\n");
        sb.append(serializeStateMachine(caseId));

        // -------- 二、案例专属意图 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 二、案例专属意图字典（_intents.json）\n");
        sb.append("# ============================================================\n");
        sb.append("# 状态机中 intentCode 只能引用本段和第三段出现的 intent_code。\n\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/_intents.json"));

        // -------- 三、通用意图字典 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 三、通用意图字典（GLOBAL/_intents.json）\n");
        sb.append("# ============================================================\n\n");
        sb.append(readFirst("classpath*:config/acls/GLOBAL/_intents.json"));

        // -------- 四、派生规则 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 四、派生规则（_derivations.yaml）\n");
        sb.append("# ============================================================\n");
        sb.append("# derived.* 变量只能读、不能写；本段定义了它们的计算逻辑。\n");
        sb.append("# 状态机 condition 引用 derived.* 时，必须与本段定义一致。\n\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/_derivations.yaml"));

        // -------- 五、变量定义 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 五、变量定义（_variables.json）\n");
        sb.append("# ============================================================\n");
        sb.append("# 状态机 condition 和 effects 引用的所有变量，必须在本段有定义。\n");
        sb.append("# resetDefaults 段给出变量在案例启动时的初始值。\n\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/_variables.json"));

        // -------- 六、安全护栏 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 六、安全护栏（guardrails*.yaml）\n");
        sb.append("# ============================================================\n");
        sb.append("# 关键操作（电复律、腺苷、电击、CPR）必须带本段护栏条件。\n\n");
        sb.append(readAll("classpath*:config/routing/guardrails*.yaml"));

        // -------- 七、案例规则 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 七、案例规则（_rules.yaml）\n");
        sb.append("# ============================================================\n\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/_rules.yaml"));

        // -------- 八、反射规则 --------
        sb.append("\n\n# ============================================================\n");
        sb.append("# 八、反射规则（reflex_rules.json）\n");
        sb.append("# ============================================================\n");
        sb.append("# 反射规则不参与状态机转移，仅供理解运行时主动触发逻辑。\n\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/reflex_rules.json"));

        // -------- 九、2025 AHA 指南 --------
        sb.append("\n\n").append(guideline2025AHA());

        // -------- 十、终止性保证 --------
        sb.append("\n\n").append(terminationRules());

        // -------- 十一、输出 Schema --------
        sb.append("\n\n").append(outputSchema());

        // -------- 十二、复查清单 --------
        sb.append("\n\n").append(reviewChecklist());

        // -------- 写出 --------
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(caseId + "-raw-prompt.txt");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

        log.info("✅ 原始契约提示词已生成: {} ({} 字节)",
            file.toAbsolutePath(), Files.size(file));
        return file;
    }

    // ==================================================================
    // 各段内容
    // ==================================================================

    private String systemPrompt(String caseId) {
        return """
        [SYSTEM]
        你是 ACLS 急救培训系统的状态机审核与重构专家。
        你熟悉 2025 AHA Guidelines for CPR and ECC。

        你的任务：
        基于下方提供的契约文件，审核并重构案例 `%s` 的状态机。

        严格约束：
        1. 状态 ID、intentCode、变量名、技能 ID，只能来自下方文件，禁止发明。
        2. 所有 target / source 必须是「# 一、合并后的完整状态机」段中 states 列表里的状态。
        3. 所有 intentCode 必须来自「# 二、案例专属意图」或「# 三、通用意图」段中的 intent_code。
        4. 所有 condition 引用的变量必须来自「# 五、变量定义」段中已定义的变量。
        5. 所有 effects 写入的变量必须是可写变量，且必须与「# 五、变量定义」一致。
        6. 关键操作（同步电复律、腺苷推注、AED 电击、胸外按压）必须带「# 六、安全护栏」中的条件。
        7. 每个非终止状态至少有一条出边。
        8. 每个非终止状态至少有一条兜底转移（条件为空 / 超时 / 指向 TIMEOUT_HANDLING）。
        9. 同一 source 下 priority 必须唯一，不得出现两条同优先级。
        10. 终止状态不得有出边。
        11. 自循环（source == target）必须有次数上限或超时出口。

        你的输出会被自动化校验器检查，违反任何一条都会被拒绝并重试。
        """.formatted(caseId);
    }

    private String serializeStateMachine(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) return "（未找到案例配置: " + caseId + "）";

        List<String> chain = stateMachineLoader.getInheritanceChain(caseId);
        String chainStr = String.join(" → ", chain);

        StateMachineSnapshot snap = StateMachineSnapshot.from(config, chainStr);

        try {
            return YAML_MAPPER.writeValueAsString(snap);
        } catch (Exception e) {
            log.error("序列化状态机失败", e);
            return "（序列化失败: " + e.getMessage() + "）";
        }
    }

    private String guideline2025AHA() {
        return """
        # ============================================================
        # 九、2025 AHA 指南硬性规则
        # ============================================================
        # 依据：2025 AHA Guidelines for CPR and ECC, Part 9:
        #      Adult Advanced Life Support
        #      DOI: 10.1161/CIR.0000000000001376
        # 违反任一项视为 P0 错误。

        - 同步电复律（AF / AFlutter）：首次能量必须 >= 200 J。
          状态机中 device.defibEnergy 或对应 effects 必须体现。

        - WPW / 预激（#clinical.isWpw == true）：
          * 禁用腺苷（6mg 和 12mg）
          * 禁用钙通道阻滞剂（地尔硫卓）
          * 禁用 β 受体阻滞剂（美托洛尔、艾司洛尔）
          * 禁用静脉胺碘酮
          状态机中必须对 F_101_DO_ADENOSINE_12MG、F_101_DO_SECOND_LINE_DRUG
          增加 #clinical.isWpw == false 前置条件。

        - 不稳定优先：
          #derived.stable == false 时，不得先尝试药物，
          必须直接进入 F_101_UNSTABLE_CARDIOVERSION 或 exitReason = 'UNSTABLE'。

        - 索他洛尔：
          已从宽 QRS 稳定心动过速推荐药物中移除，不得出现在任何转移的 effects 或 intentCode 中。

        - 迷走神经刺激：
          在稳定窄 QRS 规律分支中，迷走神经刺激必须先于腺苷。
          如需颈动脉杂音禁忌，必须由护栏段给出条件。

        - 腺苷给药：
          6mg 快速静推，推完立即 20ml 生理盐水冲管。
          12mg 为二剂，仅在 6mg 无效后使用。
        """;
    }

    private String terminationRules() {
        return """
        # ============================================================
        # 十、终止性保证
        # ============================================================
        # 违反任一项视为 P0 错误。

        T1. 每个非终止状态必须至少有一条出边。
        T2. 每个非终止状态必须有一条兜底转移：
            condition 为 null / 恒真 / 基于 process.stateDuration / 指向 TIMEOUT_HANDLING。
        T3. 每个 condition 引用的变量必须有写入来源（effects 或技能写回）。
        T4. 同一 source 下 priority 不得重复。
        T5. 终止状态（endStates）不得有出边。
        T6. 从 initialState 到每个终止状态必须存在有向路径。
        T7. 自循环（source == target）必须有 count 上限或 stateDuration 超时出口。
        """;
    }

    private String outputSchema() {
        return """
        # ============================================================
        # 十一、输出 Schema
        # ============================================================
        # 请严格按以下结构输出 YAML，不要添加额外字段。

        states:
          - id: string          # 必须来自「# 一」段的 states
            name: string        # 必须来自「# 一」段的 stateDescriptions
            terminal: boolean   # 必须与「# 一」段的 endStates 一致

        transitions:
          - id: string          # 格式：T-{source}-{target}-{seq}
            source: string
            target: string
            intentCode: string | null
            condition: string | null    # SpEL 风格，#变量名，字符串用单引号
            priority: integer
            exitReason: string | null
            effects:
              - string          # 格式：varName = value
            description: string
            guidelineRefs:
              - string          # 例：AHA2025-WPW-ADENOSINE-12
            isFallback: boolean

        selfCheck:
          reachableFromInitial: [string]
          unreachableStates: [string]
          trapStates: [string]
          priorityConflicts: [string]
          readWriteGaps: [string]
          guidelineViolations: [string]
          terminationViolations: [string]

        # 仅输出以上内容，不要解释，不要 Markdown 代码块标记。
        """;
    }

    private String reviewChecklist() {
        return """
        # ============================================================
        # 十二、输出前自检清单
        # ============================================================
        # 请逐条检查，并把结果写入 selfCheck 段。

        [ ] 所有 target 都在「# 一」段的 states 中
        [ ] 所有 intentCode 都在「# 二」「# 三」段中
        [ ] 所有 condition 引用的变量都在「# 五」段中定义
        [ ] 所有 effects 写入的变量都是可写变量
        [ ] 没有同 source 下 priority 重复
        [ ] 每个非终止状态都有出边
        [ ] 每个非终止状态都有兜底转移
        [ ] 终止状态没有出边
        [ ] 自循环有次数上限或超时出口
        [ ] 12mg 腺苷有 #clinical.isWpw == false
        [ ] 二线药物有 #clinical.isWpw == false
        [ ] 不稳定分支优先于稳定分支
        [ ] 电复律转移有同步模式 / 能量 / 清场护栏
        [ ] 从 initialState 到每个 terminal 都有路径

        如任一项不满足，请修正后再输出。
        """;
    }

    // ==================================================================
    // 文件读取
    // ==================================================================

    private String readFirst(String pattern) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            if (resources.length == 0) {
                return "（未找到文件: " + pattern + "）";
            }
            return readResource(resources[0]);
        } catch (IOException e) {
            return "（读取失败: " + pattern + " -> " + e.getMessage() + "）";
        }
    }

    private String readAll(String pattern) {
        try {
            Resource[] resources = resolver.getResources(pattern);
            if (resources.length == 0) {
                return "（未找到文件: " + pattern + "）";
            }
            StringBuilder sb = new StringBuilder();
            for (Resource r : resources) {
                sb.append("--- 文件: ").append(r.getFilename()).append(" ---\n");
                sb.append(readResource(r)).append("\n\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "（读取失败: " + pattern + " -> " + e.getMessage() + "）";
        }
    }

    private String readResource(Resource resource) throws IOException {
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
