package com.ruoyi.ems.intent.classifier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 句子级语用分析器。
 *
 * <p>职责：<b>只看句子本身</b>，判断它的语用类型（PROBE / ANNOUNCEMENT / COMMAND）。
 * 不查意图定义、不读上下文、不做元语用判断（那属于
 * {@link RuleBasedPragmaticClassifier} 的职责）。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li><b>职责单一</b>：只回答"这句话本身在表达什么"</li>
 *   <li><b>保守判定</b>：不确定就返回 {@code null}，由调用方按意图级默认值兜底</li>
 *   <li><b>可测试</b>：纯函数，无状态，无副作用</li>
 * </ul>
 *
 * <p>返回语义：</p>
 * <ul>
 *   <li>{@link ActType#PROBE} —— 疑问句（"是否稳定"、"有没有胸痛"）</li>
 *   <li>{@link ActType#ANNOUNCEMENT} —— 宣告句（"患者稳定"、"已完成"、"转复为窦性"）</li>
 *   <li>{@link ActType#COMMAND} —— 祈使句（"立即检查"、"把电极贴上"）</li>
 *   <li>{@code null} —— 无法确定，让调用方走意图级默认</li>
 * </ul>
 *
 * <p>在 {@link CascadeIntentClassifier#resolveActType} 中的位置：</p>
 * <pre>
 *   1. 元语用
 *   2. act_type_rules
 *   3. ★ SentencePragmaticAnalyzer（本类）
 *   4. ACT_DEFAULT_ACT_TYPE
 *   5. default_act_type
 *   6. 全局问句兜底
 *   7. COMMAND
 * </pre>
 *
 * <p>替换了原 {@code CascadeIntentClassifier.looksLikeQuestion} 那个
 * 只有 7 个关键词的粗糙正则，但保持同样的定位——在意图级默认之前插一层
 * 句子级判断。</p>
 */
@Slf4j
@Component
public class SentencePragmaticAnalyzer {

    // ============================================================
    // 强制祈使前缀：紧急性/明确指令，优先级最高
    // 即使后面带疑问结构，也判 COMMAND（"立即检查有没有脉搏"）
    // ============================================================
    private static final Pattern FORCE_IMPERATIVE_PREFIX = Pattern.compile(
        "^(立即|马上|赶紧|赶快|快|抓紧|给我|帮我|来个人|叫人)" +
            "|^把.{1,12}(按|推|拉|贴|放|插|连|拿|打|开|关|抬|搬|固定|绑|系)"
    );

    // ============================================================
    // PROBE：疑问结构
    // ============================================================
    private static final List<Pattern> PROBE_PATTERNS = List.of(
        // 句末问号
        Pattern.compile("[?？]\\s*$"),
        // 疑问词（"是否"、"有没有"、"有无"等）
        Pattern.compile("(是否|有没有|有无|是不是|对不对|能不能|可不可以|行不行)"),
        // 句末语气词
        Pattern.compile("(吗|呢)\\s*[?？]?\\s*$"),
        // 疑问副词
        Pattern.compile("(多少|怎么样|如何|为什么|什么情况|哪儿|哪一|哪几|几点)"),
        // 选择疑问（"A还是B"）
        Pattern.compile(".+还是.+[?？]?\\s*$")
    );

    // ============================================================
    // ANNOUNCEMENT：完成态 / 结果陈述
    // ============================================================
    private static final List<Pattern> ANNOUNCEMENT_PATTERNS = List.of(
        // 完成态标记
        Pattern.compile("^(已|已经|完成了|已完成|搞定了|好了|收到|搞定|做完)"),
        // 患者状态陈述（"患者稳定"/"患者不稳定"/"患者清醒"）
        Pattern.compile("(患者|病患|病人).{0,8}(稳定|不稳定|清醒|昏迷|有反应|无反应|恶化|好转)"),
        // 心律转复/变化
        Pattern.compile("(转复|恢复|转为|变成|出现|发现|确认).{0,8}(窦性|室颤|室速|PEA|停搏|脉搏|心律|循环)"),
        // 生命体征报告（"血压 145"、"心率 151"）
        Pattern.compile("(血压|心率|血氧|SpO2|呼吸|体温|血糖).{0,10}\\d+"),
        // 症状/征象出现
        Pattern.compile("(出现|发生|存在).{0,8}(症状|征象|恶化|并发症|休克|胸痛|意识)"),
        // 动作完成的宣告（"心电图机已开启"、"导联已连接"）
        Pattern.compile(".{2,10}(已|都)(开|连|贴|放|插|关|完|好)")
    );

    // ============================================================
    // COMMAND：祈使动词
    // ============================================================
    private static final List<Pattern> COMMAND_PATTERNS = List.of(
        // 动作词开头（单字动作或多字指令）
        Pattern.compile("^(做|检查|测量|评估|执行|准备|操作|实施|给予|使用|建立|开放|插|贴|放|连|拿|接|推|拉|抬|搬)"),
        // 动作词 + 一下
        Pattern.compile(".{0,6}(一下|一次)")
    );

    /**
     * 分析句子语用类型。
     *
     * <p>判定顺序（从高到低）：</p>
     * <ol>
     *   <li><b>强制祈使前缀</b> → COMMAND</li>
     *   <li><b>疑问结构</b> → PROBE</li>
     *   <li><b>完成态/结果陈述</b> → ANNOUNCEMENT</li>
     *   <li><b>祈使动词</b> → COMMAND</li>
     *   <li>无法确定 → {@code null}</li>
     * </ol>
     *
     * @param sentence 待分析句子，可为 null
     * @return ActType 或 {@code null}
     */
    public ActType analyze(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return null;
        }

        String text = sentence.trim();

        // ========== 1. 强制祈使前缀（优先级最高） ==========
        // "立即检查有没有脉搏" 语义是 COMMAND，不是 PROBE
        if (FORCE_IMPERATIVE_PREFIX.matcher(text).find()) {
            log.debug("[句类] 强制祈使 → COMMAND: '{}'", text);
            return ActType.COMMAND;
        }

        // ========== 2. 疑问结构 → PROBE ==========
        for (Pattern p : PROBE_PATTERNS) {
            if (p.matcher(text).find()) {
                log.debug("[句类] 疑问结构 → PROBE: '{}' (pattern={})",
                    text, p.pattern());
                return ActType.PROBE;
            }
        }

        // ========== 3. 完成态/结果陈述 → ANNOUNCEMENT ==========
        for (Pattern p : ANNOUNCEMENT_PATTERNS) {
            if (p.matcher(text).find()) {
                log.debug("[句类] 完成态/结果陈述 → ANNOUNCEMENT: '{}' (pattern={})",
                    text, p.pattern());
                return ActType.ANNOUNCEMENT;
            }
        }

        // ========== 4. 祈使动词 → COMMAND ==========
        for (Pattern p : COMMAND_PATTERNS) {
            if (p.matcher(text).find()) {
                log.debug("[句类] 祈使动词 → COMMAND: '{}' (pattern={})",
                    text, p.pattern());
                return ActType.COMMAND;
            }
        }

        // ========== 5. 无法确定 ==========
        log.debug("[句类] 无法确定 → null: '{}'", text);
        return null;
    }
}
