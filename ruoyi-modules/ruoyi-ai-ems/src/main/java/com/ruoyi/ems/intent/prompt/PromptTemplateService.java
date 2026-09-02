package com.ruoyi.ems.intent.prompt;


import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提示词模板服务（分层分类版）
 * <p>
 * 分类流程：
 * 第一步：域路由（从 JSON 的 sub_domains 动态加载）
 * 第二步：细粒度分类（仅从当前域下的意图列表中匹配）
 * <p>
 * 设计原则：
 * 1. 完全动态生成，零硬编码域编码或意图列表
 * 2. 强制 LLM 输出结构化推理链（CoT），便于后处理校验
 * 3. 利用 keywords 字段提升兜底匹配准确率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final CaseIntentDefinitionsLoader intentLoader;

    private String oneStageSystemTemplate;
    private String oneStageUserTemplate;
    private String domainRouterSystemTemplate;
    private String domainRouterUserTemplate;
    private String fineClassifierSystemTemplate;
    private String fineClassifierUserTemplate;
    private String contextSectionTemplate;

    @PostConstruct
    public void init() {
        try {
            oneStageSystemTemplate = loadTemplate("prompts/one_stage_system_prompt.txt");
            oneStageUserTemplate = loadTemplate("prompts/one_stage_user_prompt.txt");
            domainRouterSystemTemplate = loadTemplate("prompts/domain_router_system_prompt.txt");
            domainRouterUserTemplate = loadTemplate("prompts/domain_router_user_prompt.txt");
            fineClassifierSystemTemplate = loadTemplate("prompts/fine_classifier_system_prompt.txt");
            fineClassifierUserTemplate = loadTemplate("prompts/fine_classifier_user_prompt.txt");
           // contextSectionTemplate = loadTemplate("prompts/context_section.txt");
            log.info("✅ 所有提示词模板加载完成");
        } catch (IOException e) {
            log.error("❌ 加载提示词模板失败", e);
            throw new RuntimeException("提示词模板加载失败", e);
        }
    }


    /**
     * 从 classpath 加载模板文件
     */
    private String loadTemplate(String path) throws IOException {
        // 1. 从 classpath 读取
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("⚠️ 模板文件不存在: {}", path);
            return "";
        }
        // 2. 检查是否可读（可选但推荐）
        if (!resource.isReadable()) {
            log.warn("⚠️ 模板文件不可读: {}", path);
            return "";
        }

        // 3. 读取文件内容为 UTF-8 字符串
        String content = Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);

        // 4. 打印加载状态
        log.debug("✅ 加载模板: {} ({} 字符)", path, content.length());

        return content;
    }
    // ============================================================
    // 第一步：域路由（Domain Router）—— 完全动态生成
    // ============================================================

    /**
     * 构建域路由的 System Prompt（精简优化版）
     * 针对 qwen2.5 模型注意力特点优化：规则前置、精简冗余、信息密度高
     */
    public String buildDomainRouterSystemPrompt() {
//        List<SubDomain> allDomains = intentLoader.getAllSubDomains();
//        int domainCount = allDomains.size();
//
//        // 1. 生成所有域的关键词映射（核心信息）
//        String domainKeywordMap = allDomains.stream()
//            .map(domain -> {
//                String code = getDomainCode(domain);
//                String name = domain.getSubDomainName();
//
//                // 提取所有关键词（去重、限制数量）
//                Set<String> allKeywords = domain.getIntents().stream()
//                    .flatMap(def -> def.getKeywords().stream())
//                    .filter(Objects::nonNull)
//                    .limit(10) // 每个域最多展示10个关键词
//                    .collect(Collectors.toSet());
//                String kwStr = allKeywords.isEmpty() ? "无" : String.join("、", allKeywords);
//
//                return String.format("- %s（%s）：%s", code, name, kwStr);
//            })
//            .collect(Collectors.joining("\n"));
//
//        // 2. 生成域描述（简短的职能说明）
//        String domainDescriptions = allDomains.stream()
//            .map(domain -> {
//                String code = getDomainCode(domain);
//                String desc = domain.getSubDomainDescription();
//                if (desc == null || desc.isEmpty()) {
//                    return String.format("- %s：无描述", code);
//                }
//                // 只保留关键职能描述（前40字）
//                String brief = desc.length() > 40 ? desc.substring(0, 40) + "…" : desc;
//                return String.format("- %s：%s", code, brief);
//            })
//            .collect(Collectors.joining("\n"));
//
//        // 3. 合法域编码列表
//        String allCodes = allDomains.stream()
//            .map(this::getDomainCode)
//            .collect(Collectors.joining("、"));
//
//        // 4. 全局语用类型枚举
//        String allActTypes = allDomains.stream()
//            .flatMap(domain -> domain.getIntents().stream())
//            .map(IntentDefinition::getActType)
//            .filter(Objects::nonNull)
//            .distinct()
//            .collect(Collectors.joining("、"));
//
//        // 5. 填充精简模板（规则前置，域定义精简）
//        String prompt = String.format("""
//        【角色设定】
//        你是ACLS急救语音意图路由器。将输入的语音文本归类到以下【%d个域】之一，只输出JSON。
//
//        【输出格式】
//        {
//          "reasoning": "简要推理",
//          "domain": "域代码（必须在[%s]中选择）",
//          "confidence": 0.0-1.0
//        }
//
//        【核心映射规则】
//        语音文本命中以下关键词时，优先选择对应域（关键词匹配 > 语用类型 > 语义）：
//        %s
//
//        【域职能速览】（辅助理解）
//        %s
//
//        【语用类型参考】
//        可选类型：[%s]
//        提示：含“吗/多少/是否”多为询问；含“推/开始/准备”多为指令；含“了/已/转为”多为宣告。
//
//        【置信度参考】
//        - 0.90-1.00：关键词高度匹配
//        - 0.60-0.89：语义合理，存在候选
//        - 0.00-0.59：语义模糊，仅猜测
//
//        【处理流程】
//        1. 先按【核心映射规则】匹配关键词 → 直接选对应域
//        2. 无关键词匹配时，读【域职能速览】选语义最贴切的
//        3. 仍无法区分时，按语用类型辅助判断
//        4. 最终输出domain（必须从[%s]中选）
//        """,
//            domainCount,
//            allCodes,
//            domainKeywordMap,
//            domainDescriptions,
//            allActTypes,
//            allCodes
//        );
//
//        System.out.println(prompt);
//        return prompt;



        List<SubDomain> allDomains = intentLoader.getAllSubDomains();
        if (allDomains == null || allDomains.isEmpty()) {
            return "【警告】无可用域定义，请检查配置文件。";
        }

        // 1. 构建域列表
        String domainList = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                String name = domain.getSubDomainName();
                String desc = domain.getSubDomainDescription();
                if (desc != null && desc.length() > 30) {
                    desc = desc.substring(0, 30) + "…";
                }
                return String.format("- %s | %s | %s", code, name, desc != null ? desc : "无描述");
            })
            .collect(Collectors.joining("\n"));

        // 2. 构建域关键词映射（每个域最多8个关键词）
        String domainKeywordMap = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                Set<String> keywords = domain.getIntents().stream()
                    .flatMap(def -> def.getKeywords().stream())
                    .filter(Objects::nonNull)
                    .limit(8)
                    .collect(Collectors.toSet());
                String kwStr = keywords.isEmpty() ? "无" : String.join("、", keywords);
                return String.format("- %s：%s", code, kwStr);
            })
            .collect(Collectors.joining("\n"));

        // 3. 构建域职能速览
        String domainDescriptions = allDomains.stream()
            .map(domain -> {
                String code = getDomainCode(domain);
                String desc = domain.getSubDomainDescription();
                if (desc == null || desc.isEmpty()) {
                    return String.format("- %s：无描述", code);
                }
                String brief = desc.length() > 40 ? desc.substring(0, 40) + "…" : desc;
                return String.format("- %s：%s", code, brief);
            })
            .collect(Collectors.joining("\n"));

        // 4. 提取所有语用类型
        String allActTypes = allDomains.stream()
            .flatMap(domain -> domain.getIntents().stream())
            .map(IntentDefinition::getActType)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining("、"));

        // 5. 合法域编码列表
        String allCodes = allDomains.stream()
            .map(this::getDomainCode)
            .collect(Collectors.joining("、"));

        // 6. 填充模板
        String prompt = domainRouterSystemTemplate
            .replace("{domainCount}", String.valueOf(allDomains.size()))
            .replace("{domainList}", domainList)
            .replace("{domainKeywordMap}", domainKeywordMap)
            .replace("{domainDescriptions}", domainDescriptions)
            .replace("{allActTypes}", allActTypes)
            .replace("{allCodes}", allCodes);

        // 调试输出（生产环境可关闭）
        // System.out.println(prompt);
        return prompt;
    }

    /**
     * 构建域路由的 User Prompt（带上下文）
     * 与 buildOneStageUserPrompt 保持完全一致的架构
     */
    public String buildDomainRouterUserPrompt(String asrText, ClassificationContext context) {
        // 1. 生成上下文字符串（若 context 为 null 或空，返回空字符串）
        String contextPrompt = (context != null) ? context.buildContextPrompt() : "";

        // 2. 替换占位符
        String prompt = domainRouterUserTemplate
            .replace("{context}", contextPrompt)
            .replace("{asrText}", asrText);

        return prompt;
    }

    // ============================================================
    // 第二步：细粒度分类（Fine Classifier）—— 动态裁剪
    // ============================================================

    /**
     * 构建细粒度分类的 System Prompt
     * 仅包含当前域下的候选意图（动态裁剪），并强制 LLM 按 act_type 过滤
     * 利用 keywords 字段提供精准锚点
     */
    public String buildFineClassifierSystemPrompt(SubDomain domain) {
//        if (domain == null || domain.getIntents() == null || domain.getIntents().isEmpty()) {
//            return "【警告】当前域下无候选意图，请返回空结果。";
//        }
//
//        // 构建候选意图列表字符串
//        StringBuilder listSb = new StringBuilder();
//        listSb.append("【候选意图列表】（共 ").append(domain.getIntents().size()).append(" 个）\n");
//        listSb.append("（格式：编码 [语用类型] 名称 | 关键词 | 示例）\n");
//        for (IntentDefinition def : domain.getIntents()) {
//            listSb.append(formatIntentWithKeywords(def));
//        }
//        String intentList = listSb.toString();
//
//        // 使用 domain_code 而非 sub_domain_name，与域路由输出保持一致
//        String domainCode = getDomainCode(domain);
//
//        // 使用文本块，包含占位符 %s
//        String template = """
//        【角色设定】
//        你是一个精确的ACLS意图分类器。你只需要从下方给定的【候选意图列表】中选出最匹配的一条。
//        注意：仅能选择列表中的编码，不要选择列表外的编码。
//
//        【强制推理步骤】请按以下顺序思考（不输出思考过程，但需遵循）：
//        步骤1：判断当前语音文本的语用类型（act_type），**按以下优先级依次判别**：
//
//          ① **疑问句检测**：若文本以疑问词（什么、多少、几、是否、有没有）开头或结尾，或包含疑问语气词（吗、呢、吧），或语序为疑问语序（如“血压多少？”） → 判定为【询问】。
//             *例外*：若疑问句实际为指令（如“你能推一下药吗？”），因ACLS场景下常为委婉指令，仍判为【指令】（见②）。
//
//          ② **祈使句/指令检测**：若文本为祈使句（以动词原形开头，或含有“请”、“要”、“必须”、“马上”、“准备”等），或明确要求执行操作（如“推”、“打”、“开始”、“充电”、“评估”、“测量”、“复查”、“再测”），且**不满足①中明确的疑问结构** → 判定为【指令】。
//             *注意*：即使带有“了”、“完”、“已”等完成态词，只要核心是“要求执行操作”，仍归为【指令】（如“推完药再量血压”）。
//
//          ③ **陈述句/宣告检测**：若文本为陈述事实、报告结果、回答或确认状态（常见模式：主谓宾结构，含有“是”、“有”、“无”、“为”、“报告”、“显示”、“结果为”等，或明确数据描述如“血压120/80”），且**不满足①②** → 判定为【宣告】。
//
//          ④ **兜底处理**：若以上均无法明确，或文本过于简短（如单个词“血压”），则根据【当前域】和【候选意图列表】的标签分布，选择最接近的语用类型，并降低置信度。
//
//        步骤2：在下方【候选意图列表】中，**优先只扫描 `[xxx]` 标签与步骤1结果相同的意图**。
//        步骤3：如果在相同 `[xxx]` 标签的意图中找到匹配项，直接选择它。
//        步骤4：如果没有任何相同 `[xxx]` 标签的意图匹配，才允许从其他标签中选最接近的，但此时置信度不应高于0.7。
//
//        【当前域】%s
//
//        %s
//
//        【输出格式】
//        只输出JSON，不要解释，严格遵守以下JSON Schema：
//        {
//          "reasoning": "简要说明你的思考过程，包括：步骤1判定的语用类型，步骤2中扫描的标签，步骤3/4的选择依据",
//          "intent_code": "候选列表中的编码",
//          "confidence": 0.0-1.0
//        }
//        """;
//
//        String prompt = template.formatted(domainCode, intentList);
//        System.out.println(prompt);
//        return prompt;




        if (domain == null || domain.getIntents() == null || domain.getIntents().isEmpty()) {
            return "【警告】当前域下无候选意图，请返回空结果。";
        }

        // 1. 构建候选意图列表（复用现有格式化方法）
        StringBuilder listSb = new StringBuilder();
        for (IntentDefinition def : domain.getIntents()) {
            listSb.append(formatIntentWithKeywords(def));
        }
        String intentList = listSb.toString();

        // 2. 获取域编码
        String domainCode = getDomainCode(domain);

        // 3. 填充模板
        return fineClassifierSystemTemplate
            .replace("{domainCode}", domainCode!=null?domainCode:"")
            .replace("{intentList}", intentList);
    }

    /**
     * 构建细粒度分类的 User Prompt
     */
    public String buildFineClassifierUserPrompt(String asrText, ClassificationContext context) {
        String contextPrompt = (context != null) ? context.buildContextPrompt() : "";

        // 2. 填充模板
        return fineClassifierUserTemplate
            .replace("{context}", contextPrompt)
            .replace("{asrText}", asrText);
    }

    // ============================================================
    // 兜底方案：全量一级分类
    // ============================================================



// ============================================================
// 兜底方案：全量一级分类（支持上下文过滤）
// ============================================================


    /**
     * 获取候选意图：
     * - 如果 context 有 availableIntents，则使用过滤后的列表
     * - 否则返回全量意图
     */
    private List<IntentDefinition> getCandidateIntents(ClassificationContext context) {
        if (context != null
            && context.getAvailableIntents() != null
            && !context.getAvailableIntents().isEmpty()) {

            return context.getAvailableIntents().stream()
                .map(intentLoader::getIntent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        return intentLoader.getAllIntentList();
    }



    /**
     * 构建意图列表（紧凑格式，节省 token）
     */
    private String buildIntentList(List<IntentDefinition> intents) {
        if (intents == null || intents.isEmpty()) {
            return "（无候选意图）";
        }

        StringBuilder sb = new StringBuilder();
        for (IntentDefinition def : intents) {
            String code = def.getIntentCode() != null ? def.getIntentCode() : "-";
            String name = def.getNameCn() != null ? def.getNameCn() : "-";
            String actType = def.getActType() != null ? def.getActType() : "-";

            // 关键词（取前3个）
            String keywords = "-";
            if (def.getKeywords() != null && !def.getKeywords().isEmpty()) {
                keywords = def.getKeywords().stream()
                    .limit(3)
                    .collect(Collectors.joining("、"));
            }

            // 示例（取第1个，截断到20字）
            String example = "-";
            if (def.getExamples() != null && !def.getExamples().isEmpty()) {
                example = def.getExamples().get(0);
                if (example.length() > 20) {
                    example = example.substring(0, 20) + "…";
                }
            }

            sb.append(String.format("- %s | %s | [%s] | 关键词：%s | 示例：%s\n",
                code, name, actType, keywords, example));
        }

        return sb.toString();
    }

    /**
     * 构建全量一级分类的 System Prompt（带上下文）
     * 如果上下文中的 availableIntents 不为空，则仅使用这些意图作为候选列表；
     * 否则使用全量意图。
     *
     * @param context 分类上下文，可为 null
     */
    public String buildOneStageSystemPrompt(ClassificationContext context) {
        // 2.1 获取候选意图
        List<IntentDefinition> candidates = getCandidateIntents(context);

        // 2.2 生成意图列表（改为列表格式）
        String intentList = buildIntentList(candidates);

        // 2.4 替换占位符
        String prompt = oneStageSystemTemplate
            .replace("{intentCount}", String.valueOf(candidates.size()))
            .replace("{intentTable}", intentList) ;

        return prompt;
    }
    /**
     * 构建一级分类的 User Prompt（无上下文）
     */
    public String buildOneStageUserPrompt(String asrText,ClassificationContext context) {

        if (context != null) {
           String contextPrompt=context.buildContextPrompt();

            String prompt = oneStageUserTemplate
                .replace("{context}", contextPrompt)
                .replace("{asrText}", asrText) ;

           return prompt;

        }
        return "当前语音文本：\n" + asrText + "\n\n请根据上述规则进行意图分类，严格遵循三步推理。";
    }


// ============================================================
// 私有辅助方法：构建意图列表字符串
// ============================================================

    /**
     * 根据意图定义列表构建格式化的候选意图字符串
     * 按通用/特定分组，显示案例名（如果有）
     */
    private String buildOneStageIntentList(List<IntentDefinition> intents) {
        if (intents == null || intents.isEmpty()) {
            return "（无候选意图）";
        }

        // 按 isGeneral 分组
        List<IntentDefinition> generalList = intents.stream()
            .filter(IntentDefinition::isGeneral)
            .collect(Collectors.toList());
        List<IntentDefinition> specificList = intents.stream()
            .filter(def -> !def.isGeneral())
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        if (!generalList.isEmpty()) {
            sb.append("=== 通用基础意图层（G类） ===\n");
            for (IntentDefinition def : generalList) {
                sb.append(formatIntentWithKeywords(def));
            }
            sb.append("\n");
        }

        if (!specificList.isEmpty()) {
            // 获取案例名称（从第一个特定意图中取 caseCode，或从 loader 获取）
            String caseName = null;
            if (!specificList.isEmpty()) {
                IntentDefinition first = specificList.get(0);
                if (first.getCaseCode() != null) {
                    caseName = first.getCaseCode();
                }
            }
            // 也可从 loader 获取当前案例中文名（若只有一个案例）
            if (caseName == null) {
                caseName = intentLoader.getCaseNameCn();
            }
            sb.append("=== 案例特定意图层（S类")
                .append(caseName != null ? " - " + caseName : "")
                .append("） ===\n");
            for (IntentDefinition def : specificList) {
                sb.append(formatIntentWithKeywords(def));
            }
            sb.append("\n");
        }

        return sb.toString();
    }



    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 获取域编码：优先使用 domain_code，若为 null 或空则回退到 sub_domain_name
     */
    private String getDomainCode(SubDomain domain) {
        if (domain.getDomainCode() != null && !domain.getDomainCode().isEmpty()) {
            return domain.getDomainCode();
        }
        return domain.getSubDomainName();
    }

    /**
     * 提取子域中 act_type 的分布（去重统计，返回 "询问、指令、宣告" 格式）
     */
    private String extractActTypeDistribution(SubDomain domain) {
        if (domain.getIntents() == null) return "";
        Set<String> types = domain.getIntents().stream()
            .map(IntentDefinition::getActType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return String.join("、", types);
    }

    /**
     * 格式化单个意图定义，包含 keywords 展示（增强版）
     */
    private String formatIntentWithKeywords(IntentDefinition def) {
        String example = def.getExamples() != null && !def.getExamples().isEmpty()
            ? def.getExamples().get(0)
            : "无示例";
        if (example.length() > 20) {
            example = example.substring(0, 20) + "...";
        }
        String actType = def.getActType() != null ? def.getActType() : "未定义";

        // 构建 keywords 展示
        String keywordsDisplay = "";
        if (def.getKeywords() != null && !def.getKeywords().isEmpty()) {
            List<String> kw = def.getKeywords();
            // 最多展示 5 个
            if (kw.size() > 5) {
                kw = kw.subList(0, 5);
                keywordsDisplay = String.join("、", kw) + "…";
            } else {
                keywordsDisplay = String.join("、", kw);
            }
        } else {
            keywordsDisplay = "无";
        }

        return String.format("- %s [%s] %s | 关键词：%s | 如：%s\n",
            def.getIntentCode(),
            actType,
            def.getNameCn(),
            keywordsDisplay,
            example);
    }

    /**
     * 基础格式化（兼容旧版本，无 keywords）
     */
    private String formatIntent(IntentDefinition def) {
        String example = def.getExamples() != null && !def.getExamples().isEmpty()
            ? def.getExamples().get(0)
            : "无示例";
        if (example.length() > 20) {
            example = example.substring(0, 20) + "...";
        }
        String actType = def.getActType() != null ? def.getActType() : "未定义";
        return String.format("- %s [%s] %s（如：%s）\n",
            def.getIntentCode(),
            actType,
            def.getNameCn(),
            example);
    }

    // ============================================================
    // 上下文感知方法
    // ============================================================

    /**
     * 域路由 System Prompt（带上下文）
     */
    public String buildDomainRouterSystemPrompt(ClassificationContext context) {
        String basePrompt = buildDomainRouterSystemPrompt();
        if (context == null) {
            return basePrompt;
        }
        return basePrompt;
    }


    /**
     * 细粒度分类 System Prompt（带上下文）
     */
    public String buildFineClassifierSystemPrompt(SubDomain domain, ClassificationContext context) {
        return buildFineClassifierSystemPrompt(domain);
    }



    /**
     * 格式化时间为自然语言
     */
    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int minutes = seconds / 60;
        int remain = seconds % 60;
        if (minutes < 60) return minutes + "分" + remain + "秒";
        int hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "小时" + minutes + "分" + remain + "秒";
    }

    // ============================================================
    // 调试方法
    // ============================================================

    public void printDomainRouterPrompt() {
        log.info("\n========== 域路由 System Prompt ==========\n{}\n==========================================",
            buildDomainRouterSystemPrompt());
    }


}
