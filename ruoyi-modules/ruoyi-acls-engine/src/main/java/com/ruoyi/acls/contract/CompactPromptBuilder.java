package com.ruoyi.acls.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.domain.VariableMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompactPromptBuilder {

    private final YamlStateMachineLoader stateMachineLoader;
    private final VariableMetaLoader variableMetaLoader;

    private final PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern VAR_PATTERN =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_.]+)");

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

        Set<String> referencedVars = collectReferencedVars(config);

        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt(caseId));

        // 一、状态机
        sb.append("\n\n# ============================================================\n");
        sb.append("# 一、合并后的完整状态机（父级 + 子级）\n");
        sb.append("# 状态总数: ").append(config.getStates().size())
            .append("，转移总数: ").append(config.getTransitions().size())
            .append("，终止状态数: ").append(config.getEndStates().size()).append("\n");
        sb.append("# 以下转移为现有配置，可作为参考；可优化、合并、调整优先级，\n");
        sb.append("# 但不应删除已有临床分支，除非在 selfCheck 中说明理由。\n");
        sb.append("# ★ 输出时只输出子级状态（F_101_*、F_102_*、F_103_*、F_104_*），\n");
        sb.append("#   父级状态（F-000-*）由 GLOBAL/_flow.yaml 提供，不要输出。\n");
        sb.append("#   但涉及子级的转移（source 或 target 含子级状态）必须输出。\n");
        sb.append("# ============================================================\n");
        sb.append(serializeStateMachine(caseId));

        // 二、案例专属意图
        sb.append("\n\n# ============================================================\n");
        sb.append("# 二、案例专属意图（仅列 code / name / act / 用途）\n");
        sb.append("# ============================================================\n");
        sb.append(compactIntents("classpath*:config/acls/cases/**/" + caseId + "/_intents.json"));

        // 三、通用意图
        sb.append("\n\n# ============================================================\n");
        sb.append("# 三、通用意图（仅列 code / name / act / 用途）\n");
        sb.append("# ============================================================\n");
        sb.append(compactIntents("classpath*:config/acls/GLOBAL/_intents.json"));

        // 四、派生规则
        sb.append("\n\n# ============================================================\n");
        sb.append("# 四、派生规则（derived.* 只读，不能写）\n");
        sb.append("# ============================================================\n");
        sb.append(readFirst("classpath*:config/acls/cases/**/" + caseId + "/_derivations.yaml"));

        // 五、变量
        sb.append("\n\n# ============================================================\n");
        sb.append("# 五、变量清单（只列状态机引用到的变量）\n");
        sb.append("# ============================================================\n");
        sb.append(compactVariables(caseId, referencedVars));

        // 六、安全护栏
        sb.append("\n\n# ============================================================\n");
        sb.append("# 六、安全护栏（id / trigger / condition / action / severity）\n");
        sb.append("# ============================================================\n");
        sb.append(compactGuardRails());

        // 六点五、状态 ↔ 意图对应
        sb.append("\n\n").append(stateIntentMapping());

        // 六点六、药物状态执行契约
        sb.append("\n\n").append(drugStateContract());

        // 六点七、兜底转移规则
        sb.append("\n\n").append(fallbackRule());

        // 六点八、未定义状态处理
        sb.append("\n\n").append(unresolvedStates(config));

        // 七~十
        sb.append("\n\n").append(guideline2025AHA());
        sb.append("\n\n").append(terminationRules());
        sb.append("\n\n").append(outputSchema());
        sb.append("\n\n").append(reviewChecklist());

        // 十一
        sb.append("\n\n").append(finalReminder());

        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(caseId + "-compact-prompt.txt");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        log.info("✅ 压缩提示词已生成: {} ({} 字节)",
            file.toAbsolutePath(), Files.size(file));
        return file;
    }

    // ==================================================================
    // 收集状态机引用的变量
    // ==================================================================
    private Set<String> collectReferencedVars(ResolvedCaseTypeConfig config) {
        Set<String> all = new LinkedHashSet<>();

        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getCondition() != null) {
                    all.addAll(extractVars(t.getCondition()));
                }
                if (t.getEffects() != null) {
                    for (TransitionRule.EffectRule e : t.getEffects()) {
                        // value 可能是字面量，也可能是复杂对象；用 String.valueOf 兜底
                        Object value = e.getValue();
                        if (value != null) {
                            all.addAll(extractVars(String.valueOf(value)));
                        }
                        // valueExpression 是 SpEL 表达式，需要提取变量
                        String valueExpr = e.getValueExpression();
                        if (valueExpr != null) {
                            all.addAll(extractVars(valueExpr));
                        }
                        // var 本身也是变量
                        if (e.getVar() != null) {
                            all.add(e.getVar());
                        }
                    }
                }
            }
        }

        String derivations = readFirst(
            "classpath*:config/acls/cases/**/" + config.getCode() + "/_derivations.yaml");
        if (!derivations.startsWith("（")) {
            all.addAll(extractVars(derivations));
        }

        return all;
    }

    private Set<String> extractVars(String expr) {
        Set<String> vars = new LinkedHashSet<>();
        if (expr == null) return vars;
        Matcher m = VAR_PATTERN.matcher(expr);
        while (m.find()) {
            String name = m.group(1);
            if (!name.startsWith("utils") && !name.startsWith("ctx")) {
                vars.add(name);
            }
        }
        return vars;
    }

    // ==================================================================
    // 意图压缩
    // ==================================================================
    private String compactIntents(String pattern) {
        String raw = readFirst(pattern);
        if (raw.startsWith("（")) return raw;

        try {
            JsonNode root = JSON.readTree(raw);
            ArrayNode out = JSON.createArrayNode();
            JsonNode layers = root.get("layers");
            if (layers == null) return raw;

            for (JsonNode layer : layers) {
                JsonNode subDomains = layer.get("sub_domains");
                if (subDomains == null) continue;
                for (JsonNode sub : subDomains) {
                    String domainCode = sub.path("domain_code").asText("");
                    String subName = sub.path("sub_domain_name").asText("");
                    JsonNode intents = sub.get("intents");
                    if (intents == null) continue;

                    for (JsonNode intent : intents) {
                        ObjectNode c = JSON.createObjectNode();
                        c.put("code", intent.path("intent_code").asText());
                        c.put("name", intent.path("name_cn").asText());
                        c.put("act", intent.path("act").asText());
                        if (!domainCode.isEmpty()) c.put("domain", domainCode);
                        if (!subName.isEmpty()) c.put("sub", subName);
                        String desc = intent.path("description_cn").asText("");
                        if (!desc.isEmpty()) {
                            if (desc.length() > 60) {
                                desc = desc.substring(0, 60) + "...";
                            }
                            c.put("desc", desc);
                        }
                        out.add(c);
                    }
                }
            }
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            log.warn("意图压缩失败，回退原文: {}", e.getMessage());
            return raw;
        }
    }

    // ==================================================================
    // 变量压缩
    // ==================================================================
    private String compactVariables(String caseId, Set<String> referenced) {
        Map<String, VariableMeta> allVars = variableMetaLoader.getVariablesForCase(caseId);
        if (allVars == null || allVars.isEmpty()) {
            return "（变量未加载: " + caseId + "）";
        }

        Set<String> allNeeded = new LinkedHashSet<>(referenced);
        for (String v : referenced) {
            String p = v;
            while (p.contains(".")) {
                p = p.substring(0, p.lastIndexOf('.'));
                allNeeded.add(p);
            }
        }

        ArrayNode out = JSON.createArrayNode();
        for (Map.Entry<String, VariableMeta> e : allVars.entrySet()) {
            String name = e.getKey();
            if (!allNeeded.contains(name)) continue;

            VariableMeta meta = e.getValue();
            ObjectNode c = JSON.createObjectNode();
            c.put("name", name);

            String nameCn = meta.getNameCn();
            if (nameCn != null && !nameCn.isEmpty()) {
                c.put("nameCn", nameCn);
            }

            String type = meta.getType();
            if (type != null) {
                c.put("type", type);
            }

            String unit = meta.getUnit();
            if (unit != null && !unit.isEmpty()) {
                c.put("unit", unit);
            }

            if (meta.getEnumOptions() != null && !meta.getEnumOptions().isEmpty()) {
                ArrayNode vals = JSON.createArrayNode();
                for (VariableMeta.EnumOption opt : meta.getEnumOptions()) {
                    if (opt.getValue() != null) {
                        vals.add(opt.getValue());
                    }
                }
                if (vals.size() > 0) {
                    c.set("enum", vals);
                }
            }

            if (meta.getMin() != null) c.put("min", meta.getMin());
            if (meta.getMax() != null) c.put("max", meta.getMax());

            String desc = meta.getDescription();
            if (desc != null && !desc.isEmpty()) {
                if (desc.length() > 60) desc = desc.substring(0, 60) + "...";
                c.put("desc", desc);
            }

            String writableBy = meta.getWritableBy();
            if (writableBy != null && !writableBy.isEmpty()) {
                c.put("writableBy", writableBy);
            }

            out.add(c);
        }

        ObjectNode result = JSON.createObjectNode();
        result.set("variables", out);

        Map<String, Object> defaults = variableMetaLoader.getResetDefaults(caseId);
        if (defaults != null && !defaults.isEmpty()) {
            result.set("resetDefaults", JSON.valueToTree(defaults));
        }

        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("变量序列化失败: {}", e.getMessage());
            return "（序列化失败: " + e.getMessage() + "）";
        }
    }

    // ==================================================================
    // 护栏压缩
    // ==================================================================
    @SuppressWarnings("unchecked")
    private String compactGuardRails() {
        try {
            Resource[] resources = resolver.getResources(
                "classpath*:config/routing/guardrails*.yaml");
            if (resources.length == 0) return "（未找到护栏文件）";

            Yaml yaml = new Yaml();
            List<Map<String, Object>> compact = new ArrayList<>();

            for (Resource r : resources) {
                String raw;
                try (var is = r.getInputStream()) {
                    raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                Map<String, Object> root = yaml.load(raw);
                if (root == null) continue;
                Object guardrails = root.get("guardrails");
                if (!(guardrails instanceof List)) continue;

                for (Map<String, Object> rule : (List<Map<String, Object>>) guardrails) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rule.get("id"));
                    c.put("trigger", rule.get("trigger"));
                    c.put("condition", rule.get("condition"));
                    c.put("action", rule.get("action"));
                    c.put("severity", rule.get("severity"));
                    compact.add(c);
                }
            }
            return yaml.dump(compact);
        } catch (Exception e) {
            log.warn("护栏压缩失败，回退原文: {}", e.getMessage());
            return readAll("classpath*:config/routing/guardrails*.yaml");
        }
    }

    // ==================================================================
    // 状态机序列化
    // ==================================================================
    private String serializeStateMachine(String caseId) {
        ResolvedCaseTypeConfig config = stateMachineLoader.getConfig(caseId);
        if (config == null) return "（未找到案例配置: " + caseId + "）";

        List<String> chain = stateMachineLoader.getInheritanceChain(caseId);
        StateMachineSnapshot snap =
            StateMachineSnapshot.from(config, String.join(" → ", chain));

        try {
            return YAML.writeValueAsString(snap);
        } catch (Exception e) {
            return "（序列化失败: " + e.getMessage() + "）";
        }
    }

    // ==================================================================
    // [SYSTEM] 段
    // ==================================================================
    private String systemPrompt(String caseId) {
        return """
        [SYSTEM]
        你是 ACLS 急救培训系统的状态机审核与重构专家，熟悉 2025 AHA 指南。
        任务：基于下方契约，审核并重构案例 `%s` 的状态机。

        严格约束：
        1. 状态 ID、intentCode、变量名只能来自下方文件，禁止发明。
        2. 所有 target / source 必须是「# 一」段 states 列表中的状态，
           或「# 六点八」段明确允许追加的状态。
        3. intentCode 必须来自「# 二」「# 三」段中的 code，
           且必须按「# 六点五」的状态-意图对应表绑定。
        4. condition 引用的变量必须来自「# 五」段。
        5. effects 写入的变量必须是「# 五」段中列出的可写变量。
        5.1 effects 格式（★ 必须严格遵守）：
            effects 是对象列表，每项格式：
              - var: "变量名"
                value: 字面量值          # 或
              - var: "变量名"
                valueExpression: "SpEL表达式"
            不要写 "变量名 = 值" 这种字符串。
            正确示例：
              effects:
                - var: "intervention.adenosine.count"
                  value: 1
                - var: "intervention.adenosine.status"
                  value: "given"
                - var: "process.lastInterventionTime"
                  valueExpression: "T(System).currentTimeMillis()"
            错误示例（不要这样输出）：
              effects:
                - "intervention.adenosine.count = 1"
        6. 关键操作（电复律、腺苷、AED 电击、CPR）必须带「# 六」段护栏条件。
        7. 每个非终止状态至少有一条出边。
        8. 每个非终止状态至少有一条兜底转移（condition 为空 / 超时 / 指向 TIMEOUT_HANDLING）。
        9. 同一 source 下 priority 必须唯一。
        10. 终止状态不得有出边（F_101_EXIT_TO_PARENT 例外，可退回父级）。
        11. 自循环必须有次数上限或超时出口。
        12. YAML 格式：见「# 九」和「# 十一」的 [OUTPUT_FORMAT]。
        13. ★ 输出结构：必须按 _flow.yaml 结构输出。
            顶层是 caseId / caseName / version / parent / description /
            stateDescriptions / stateMachine。
            状态和转移在 stateMachine 下面。
            不要输出顶层 states / transitions。
            stateMachine.states 是字符串列表，不是对象列表。
            stateMachine.transitions 的每项只包含：
            source / target / intentCode / condition / priority /
            exitReason / effects / description。
            不要输出 id / guidelineRefs / isFallback。
        14. ★ 必须用 ```yaml ... ``` 代码块包裹完整 YAML。
            代码块外不要有任何文字。

        说明：endStates 中 F_000-TERMINAL_END 是案例级终止，不得有出边；
              F_101_EXIT_TO_PARENT 是子状态机退出，可以有出边。
        """.formatted(caseId);
    }

    // ==================================================================
    // 六点五、状态 ↔ 意图对应表
    // ==================================================================
    private String stateIntentMapping() {
        return """
        # ============================================================
        # 六点五、状态 ↔ 意图 对应表（必须严格按此绑定）
        # ============================================================
        # 生成转移时，每条转移的 intentCode 必须是下列对应关系中的意图。
        # 若某状态在表中未列出，intentCode 用 null（纯条件转移）。
        #
        # F_101_ASSESS_STABILITY        → S-101-1-3  (血流动力学稳定性评估)
        # F_101_CHECK_QRS_WIDTH         → S-101-1-1  (QRS宽度判读)
        # F_101_CHECK_RHYTHM            → S-101-1-2  (节律规则性判读)
        # F_101_DO_VAGAL_MANEUVER       → S-101-2-1  (迷走神经刺激执行)
        # F_101_DO_ADENOSINE_6MG        → S-101-2-2  (腺苷推注执行)
        # F_101_DO_ADENOSINE_12MG       → S-101-2-2  (腺苷推注执行)
        # F_101_DO_SECOND_LINE_DRUG     → S-101-2-5  (二线药物执行)
        # F_101_MONITOR_SINUS_RHYTHM    → S-101-2-8  (治疗结果报告)
        # F_101_UNSTABLE_CARDIOVERSION  → S-101-2-6  (同步电复律序列执行)
        #
        # 例外：WPW 相关转移、恶化触发转移、ROSC 退出转移、
        #      兜底转移，intentCode 用 null，由 condition 自动触发。
        #
        # 注意：入口转移（F-000-INITIAL_SETUP → F_101_ASSESS_STABILITY）
        #      写在 stable_narrow/_flow.yaml 的 transitions 中，
        #      source 是 F-000-INITIAL_SETUP，target 是 F_101_ASSESS_STABILITY。
        #      这类转移也必须输出。
        """;
    }

    // ==================================================================
    // 六点六、药物/干预状态执行契约（★ effects 改成对象格式）
    // ==================================================================
    private String drugStateContract() {
        return """
        # ============================================================
        # 六点六、药物/干预状态执行契约（防止状态卡住）
        # ============================================================
        # 以下状态必须在"技能执行后"有变量写入，否则出边条件永远为 false。
        # 生成时必须为每个状态补一条自循环，effects 用对象格式写入变量。
        #
        # ★ effects 格式（必须严格遵守）：
        #   - var: "变量名"
        #     value: 字面量值            # 数字、字符串、布尔
        #     或
        #   - var: "变量名"
        #     valueExpression: "SpEL表达式"
        #
        # 各状态的自循环 effects 示例：
        #
        # F_101_DO_ADENOSINE_6MG:
        #   - 自循环 intentCode = S-101-2-2
        #   - effects:
        #       - var: "intervention.adenosine.count"
        #         value: 1
        #       - var: "intervention.adenosine.status"
        #         value: "given"
        #       - var: "process.lastInterventionTime"
        #         valueExpression: "T(System).currentTimeMillis()"
        #
        # F_101_DO_ADENOSINE_12MG:
        #   - 自循环 intentCode = S-101-2-2
        #   - effects:
        #       - var: "intervention.adenosine.count"
        #         value: 2
        #       - var: "intervention.adenosine.status"
        #         value: "given"
        #       - var: "process.lastInterventionTime"
        #         valueExpression: "T(System).currentTimeMillis()"
        #
        # F_101_DO_VAGAL_MANEUVER:
        #   - 自循环 intentCode = S-101-2-1
        #   - effects:
        #       - var: "intervention.vagal.count"
        #         value: 1
        #       - var: "intervention.vagal.status"
        #         value: "performed"
        #       - var: "intervention.vagal.effect"
        #         value: "pending"
        #       - var: "process.lastInterventionTime"
        #         valueExpression: "T(System).currentTimeMillis()"
        #
        # F_101_UNSTABLE_CARDIOVERSION:
        #   - 自循环 intentCode = S-101-2-6
        #   - effects:
        #       - var: "intervention.cardioversion.count"
        #         valueExpression: "(intervention.cardioversion.count == null ? 1 : intervention.cardioversion.count + 1)"
        #       - var: "intervention.cardioversion.lastEnergy"
        #         value: 200
        #       - var: "process.lastInterventionTime"
        #         valueExpression: "T(System).currentTimeMillis()"
        #   - 次数上限必须由 #intervention.cardioversion.count < 3 控制
        #   - 执行前必须检查设备就绪：
        #     #device.defibConnected == true &&
        #     #device.defibPadsAttached == true &&
        #     #device.defibSyncMode == true &&
        #     #device.defibCharged == true
        #
        # 注意：intervention.cardioversion 是 object 变量，
        #      success 和 count 是它的子字段。
        # 注意：valueExpression 里不用写 # 前缀，直接写 SpEL 表达式。
        """;
    }

    // ==================================================================
    // 六点七、兜底转移规则
    // ==================================================================
    private String fallbackRule() {
        return """
        # ============================================================
        # 六点七、兜底转移规则
        # ============================================================
        - 兜底转移 priority 固定为 999。
        - 其他临床转移 priority 范围 0~100。
        - 同一 source 下，兜底转移必须是唯一的（不与其他 priority 重复）。
        - 案例级终止状态（F_000-TERMINAL_END）不得有出边。
        - 子状态机退出（F_101_EXIT_TO_PARENT）可以有出边，但兜底转移必须
          存在，指向 F-000-INITIAL_SETUP 或 F-000-TIMEOUT_HANDLING。
        - 追加的 F_102/F_103/F_104 状态不能只有兜底转移，必须有业务出边
          指向对应的 F_102_EXIT_TO_PARENT / F_103_EXIT_TO_PARENT / F_104_EXIT_TO_PARENT，
          否则状态机会卡在这些状态里。
        """;
    }

    // ==================================================================
    // 六点八、未定义状态处理
    // ==================================================================
    private String unresolvedStates(ResolvedCaseTypeConfig config) {
        Set<String> declared = new HashSet<>(
            config.getStates() != null ? config.getStates() : Collections.emptyList());
        Set<String> referenced = new LinkedHashSet<>();
        if (config.getTransitions() != null) {
            for (TransitionRule t : config.getTransitions()) {
                if (t.getSource() != null) referenced.add(t.getSource());
                if (t.getTarget() != null) referenced.add(t.getTarget());
            }
        }
        Set<String> unresolved = new LinkedHashSet<>(referenced);
        unresolved.removeAll(declared);

        StringBuilder sb = new StringBuilder();
        sb.append("# ============================================================\n");
        sb.append("# 六点八、未定义状态处理\n");
        sb.append("# ============================================================\n");
        sb.append("# 以下状态被现有转移引用，但不在 states 列表中：\n");

        if (unresolved.isEmpty()) {
            sb.append("# （无——所有引用状态都已在 states 中声明）\n");
        } else {
            for (String s : unresolved) {
                Set<String> asTarget = new LinkedHashSet<>();
                Set<String> asSource = new LinkedHashSet<>();
                if (config.getTransitions() != null) {
                    for (TransitionRule t : config.getTransitions()) {
                        if (s.equals(t.getTarget()) && t.getSource() != null) {
                            asTarget.add(t.getSource());
                        }
                        if (s.equals(t.getSource()) && t.getTarget() != null) {
                            asSource.add(t.getTarget());
                        }
                    }
                }
                sb.append("#   - ").append(s);
                if (!asTarget.isEmpty()) {
                    sb.append("（被 ").append(String.join(", ", asTarget))
                        .append(" 引用为 target）");
                }
                if (!asSource.isEmpty()) {
                    sb.append("（作为 source，指向 ").append(String.join(", ", asSource))
                        .append("）");
                }
                sb.append("\n");
            }
        }

        sb.append("#\n");
        sb.append("# 处理规则：\n");
        sb.append("#   1. 语义明确的状态（如 F_103_CPR_START 是心脏骤停）：\n");
        sb.append("#      必须在 stateMachine.states 列表末尾追加该状态，\n");
        sb.append("#      并为其补一条业务出边指向 F_103_EXIT_TO_PARENT，\n");
        sb.append("#      再加一条兜底转移（priority=999）指向 F-000-TIMEOUT_HANDLING。\n");
        sb.append("#   2. 子状态机退出状态（如 F_102_EXIT_TO_PARENT）：\n");
        sb.append("#      若对应的父状态被追加，则也必须追加；否则删除引用它的转移。\n");
        sb.append("#   3. 语义不明或冗余的状态：\n");
        sb.append("#      删除引用它的转移。\n");
        sb.append("#\n");
        sb.append("# 注意：不允许引用 states 列表外的状态。要么追加，要么删除引用。\n");
        sb.append("#       不允许发明 states 列表中没有的状态 ID。\n");
        sb.append("#\n");
        sb.append("# ★ 只输出子级状态（F_101_* / F_102_* / F_103_* / F_104_*），\n");
        sb.append("#   父级状态（F-000-*）由 GLOBAL/_flow.yaml 提供。\n");

        return sb.toString();
    }

    // ==================================================================
    // 七、2025 AHA 硬性规则
    // ==================================================================
    private String guideline2025AHA() {
        return """
        # ============================================================
        # 七、2025 AHA 硬性规则（违反 P0）
        # ============================================================
        # 以下规则来自 2025 AHA Guidelines for CPR and ECC, Part 9。
        # 引用时请使用细粒度 ruleId：
        #   AHA2025-CV-ENERGY        同步电复律能量
        #   AHA2025-WPW-ADENOSINE-12 12mg 腺苷 WPW 禁忌
        #   AHA2025-WPW-CCB-BB       二线药物 WPW 禁忌
        #   AHA2025-INSTABILITY-FIRST 不稳定优先电复律
        #
        - AF/AFlutter 同步电复律首次能量 >= 200 J
          （对应 AHA2025-CV-ENERGY）
        - WPW（#clinical.isWpw == true）禁用：腺苷、CCB、β阻滞剂、静脉胺碘酮
          （对应 AHA2025-WPW-ADENOSINE-12 和 AHA2025-WPW-CCB-BB）
        - 12mg 腺苷、二线药物必须有 #clinical.isWpw == false 前置
          （对应 AHA2025-WPW-ADENOSINE-12 和 AHA2025-WPW-CCB-BB）
        - 不稳定（#derived.stable == false）必须先电复律，不得先用药
          （对应 AHA2025-INSTABILITY-FIRST）
        - 同步电复律前必须检查：同步模式、贴片、充电、清场
          （对应 AHA2025-CV-ENERGY）
        - 索他洛尔已从宽 QRS 稳定推荐中移除
        - 迷走神经刺激先于腺苷
        """;
    }

    // ==================================================================
    // 八、终止性保证
    // ==================================================================
    private String terminationRules() {
        return """
        # ============================================================
        # 八、终止性保证（违反 P0）
        # ============================================================
        T1. 非终止状态至少一条出边
        T2. 非终止状态至少一条兜底转移
        T3. condition 引用的变量必须有写入来源
        T4. 同 source 下 priority 唯一
        T5. 案例级终止状态不得有出边
        T6. 初始状态到每个终止状态有路径
        T7. 自循环有次数上限或超时出口
        """;
    }

    // ==================================================================
    // 九、输出 Schema（★ 改成 _flow.yaml 结构 + effects 对象格式）
    // ==================================================================
    private String outputSchema() {
        return """
        # ============================================================
        # 九、输出 Schema（必须严格按此结构输出）
        # ============================================================
        # 输出必须是 _flow.yaml 的结构：顶层是元数据 + stateMachine，
        # 不是顶层的 states / transitions。
        #
        # 完整示例如下（注意缩进，顶层 0 空格，stateMachine 下一级 2 空格，
        # 列表项 4 空格 + "- " 前缀）：

        caseId: "stable_narrow"
        caseName: "窄QRS稳定心动过速（SVT）"
        version: "1.1.0"
        parent: "GLOBAL"
        description: "迷走→腺苷阶梯→监测/升级"

        stateDescriptions:
          F_101_ASSESS_STABILITY: "📋 进入校验（确认稳定+窄QRS）"
          F_101_CHECK_QRS_WIDTH: "📐 QRS宽度确认"
          F_101_CHECK_RHYTHM: "🔄 节律类型确认（规则性）"
          F_101_DO_VAGAL_MANEUVER: "💆 迷走神经刺激（一线）"
          F_101_DO_ADENOSINE_6MG: "💉 腺苷 6mg IV（二线）"
          F_101_DO_ADENOSINE_12MG: "💉 腺苷 12mg IV（三线）"
          F_101_DO_SECOND_LINE_DRUG: "💊 二线药物（β受体阻滞剂/CCB）"
          F_101_MONITOR_SINUS_RHYTHM: "📊 监测窦性心律（60s窗口）"
          F_101_UNSTABLE_CARDIOVERSION: "⚡ 同步电复律（紧急干预）"
          F_101_EXIT_TO_PARENT: "🚪 退出到父级"
          F_102_ASSESS_STABILITY: "不稳定/宽QRS子级评估"
          F_102_EXIT_TO_PARENT: "🚪 退出到父级"
          F_103_CPR_START: "心脏骤停CPR开始"
          F_103_EXIT_TO_PARENT: "🚪 退出到父级"
          F_104_ASSESS_BRADY: "有症状心动过缓评估"
          F_104_EXIT_TO_PARENT: "🚪 退出到父级"

        stateMachine:
          states:
            - F_101_ASSESS_STABILITY
            - F_101_CHECK_QRS_WIDTH
            - F_101_CHECK_RHYTHM
            - F_101_DO_VAGAL_MANEUVER
            - F_101_DO_ADENOSINE_6MG
            - F_101_DO_ADENOSINE_12MG
            - F_101_DO_SECOND_LINE_DRUG
            - F_101_MONITOR_SINUS_RHYTHM
            - F_101_UNSTABLE_CARDIOVERSION
            - F_101_EXIT_TO_PARENT
            - F_102_ASSESS_STABILITY
            - F_102_EXIT_TO_PARENT
            - F_103_CPR_START
            - F_103_EXIT_TO_PARENT
            - F_104_ASSESS_BRADY
            - F_104_EXIT_TO_PARENT

          endStates:
            - F_101_EXIT_TO_PARENT

          transitions:
            - source: "F-000-INITIAL_SETUP"
              target: "F_101_ASSESS_STABILITY"
              condition: "#derived.stable == true && #derived.qrsWidth == 'narrow' && #derived.rhythmRegular == true && #derived.isSinusRhythm == false"
              priority: 30
              description: "稳定+窄QRS+规则且非窦性 → 进入SVT子级"

            - source: "F_101_ASSESS_STABILITY"
              target: "F_101_CHECK_QRS_WIDTH"
              intentCode: "S-101-1-3"
              condition: "#derived.stable == true && #derived.qrsWidth == 'narrow'"
              priority: 10
              description: "进入校验通过 → 继续"

            - source: "F_101_DO_ADENOSINE_6MG"
              target: "F_101_DO_ADENOSINE_6MG"
              intentCode: "S-101-2-2"
              condition: "#clinical.isWpw == false && (#intervention.adenosine.count == null || #intervention.adenosine.count == 0)"
              priority: 10
              effects:
                - var: "intervention.adenosine.count"
                  value: 1
                - var: "intervention.adenosine.status"
                  value: "given"
                - var: "process.lastInterventionTime"
                  valueExpression: "T(System).currentTimeMillis()"
              description: "腺苷 6mg 推注执行"

            # ... 其余转移按此格式输出

        # ============================================================
        # 结构要点（必须遵守）
        # ============================================================
        # 1. 顶层字段：caseId、caseName、version、parent、description、
        #    stateDescriptions、stateMachine。不要有顶层 states / transitions。
        # 2. stateMachine.states 是字符串列表，不是对象列表。
        #    只列子级状态（F_101_*、F_102_*、F_103_*、F_104_*），
        #    父级状态（F-000-*）由 GLOBAL/_flow.yaml 提供，不要列。
        # 3. stateMachine.endStates 只列子级终止状态（F_101_EXIT_TO_PARENT）。
        # 4. stateMachine.transitions 的每项只包含：
        #    source / target / intentCode / condition / priority /
        #    exitReason / effects / description。
        #    不要输出 id / guidelineRefs / isFallback。
        # 5. ★ effects 是对象列表，每项格式：
        #      - var: "变量名"
        #        value: 字面量值          # 或
        #      - var: "变量名"
        #        valueExpression: "SpEL表达式"
        #    不要写 "变量名 = 值" 这种字符串。
        # 6. 没有值时可省略或写 null（如 intentCode: null）。
        # 7. 用 ```yaml ... ``` 包裹完整 YAML。
        #    代码块内缩进必须保留：
        #    - 顶层字段：0 空格
        #    - stateMachine 下一级字段：2 空格
        #    - states / transitions 列表项：4 空格 + "- " 前缀
        """;
    }

    // ==================================================================
    // 十、输出前自检
    // ==================================================================
    private String reviewChecklist() {
        return """
        # ============================================================
        # 十、输出前自检
        # ============================================================
        [ ] 输出被 ```yaml ... ``` 包裹
        [ ] 顶层是 caseId / caseName / version / parent / description /
            stateDescriptions / stateMachine，不是顶层 states / transitions
        [ ] stateMachine.states 是字符串列表，只列子级状态
        [ ] stateMachine.transitions 每项只含 source / target / intentCode /
            condition / priority / exitReason / effects / description
        [ ] 没有输出 id / guidelineRefs / isFallback
        [ ] ★ effects 是对象列表，每项含 var + value 或 var + valueExpression
        [ ] ★ 没有 "变量名 = 值" 这种字符串 effects
        [ ] target/source 都在「# 一」states 中，或已按「# 六点八」追加
        [ ] intentCode 都在「# 二」「# 三」中，且符合「# 六点五」对应表
        [ ] condition 变量都在「# 五」中
        [ ] effects 写入的变量都在「# 五」中，且是可写变量
        [ ] 同 source 无 priority 重复
        [ ] 每个非终止状态有出边
        [ ] 每个非终止状态有兜底转移（priority=999）
        [ ] F_102/F_103/F_104 有业务出边指向对应 EXIT_TO_PARENT
        [ ] 自循环有次数上限或超时出口
        [ ] 药物状态有自循环写入 intervention.* 变量
        [ ] 同步电复律有设备就绪检查
        [ ] 12mg 腺苷、二线药物有 #clinical.isWpw == false
        [ ] 不稳定分支优先于稳定分支
        [ ] YAML 缩进正确（顶层 0 空格，stateMachine 下一级 2 空格，列表项 4 空格）
        """;
    }

    // ==================================================================
    // 十一、最后提醒
    // ==================================================================
    private String finalReminder() {
        return """
        # ============================================================
        # 十一、输出前必须再次确认（最近距离，逐条核对）
        # ============================================================

        [OUTPUT_FORMAT]
        1. ★ 必须按 _flow.yaml 结构输出，顶层是元数据 + stateMachine。
           正确缩进：
             caseId: "stable_narrow"
             caseName: "窄QRS稳定心动过速（SVT）"
             version: "1.1.0"
             parent: "GLOBAL"
             description: "..."

             stateDescriptions:
               F_101_ASSESS_STABILITY: "📋 进入校验"

             stateMachine:
               states:
                 - F_101_ASSESS_STABILITY
                 - F_101_CHECK_QRS_WIDTH
               endStates:
                 - F_101_EXIT_TO_PARENT
               transitions:
                 - source: "F-000-INITIAL_SETUP"
                   target: "F_101_ASSESS_STABILITY"
                   condition: "..."
                   priority: 30
                   description: "..."
        2. 不要输出顶层 states / transitions。
        3. stateMachine.states 是字符串列表，不是对象列表。
        4. stateMachine.transitions 每项只含：
           source / target / intentCode / condition / priority /
           exitReason / effects / description。
           不要有 id / guidelineRefs / isFallback。
        5. ★ 用 ```yaml ... ``` 代码块包裹完整 YAML。
           代码块外不要有任何文字。
           代码块内缩进必须保留（每层 2 空格）。

        [EFFECTS_FORMAT]
        6. ★ effects 是对象列表，每项格式：
             - var: "变量名"
               value: 字面量值
             或
             - var: "变量名"
               valueExpression: "SpEL表达式"
        7. ★ 不要写 "变量名 = 值" 这种字符串。
           错误示例（不要这样输出）：
             effects:
               - "intervention.adenosine.count = 1"
           正确示例：
             effects:
               - var: "intervention.adenosine.count"
                 value: 1

        [LOGIC_CHECK]
        8. 每条 condition 引用的变量，必须在 effects 或技能写回中有来源。
        9. intentCode 必须按「# 六点五」的对应表绑定。
        10. 12mg 腺苷、二线药物、WPW 相关转移必须有 #clinical.isWpw 检查。
        11. 药物状态必须有自循环，effects 写入 intervention.* 变量。
        12. 同步电复律自循环必须检查：
            #device.defibConnected == true &&
            #device.defibPadsAttached == true &&
            #device.defibSyncMode == true &&
            #device.defibCharged == true
        13. 兜底转移 priority=999。
        14. 未定义状态按「# 六点八」处理。
        15. F_102/F_103/F_104 必须有业务出边指向对应 EXIT_TO_PARENT。

        [FINAL_INSTRUCTION]
        请输出完整的 _flow.yaml 内容，用 ```yaml ... ``` 包裹。
        输出前逐条核对以上 15 条。如有不满足，先修正再输出。
        代码块外不要输出任何文字。
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
            try (var is = resources[0].getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
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
                sb.append("--- ").append(r.getFilename()).append(" ---\n");
                try (var is = r.getInputStream()) {
                    sb.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
                sb.append("\n\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "（读取失败: " + pattern + " -> " + e.getMessage() + "）";
        }
    }
}
