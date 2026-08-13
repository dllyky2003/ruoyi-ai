package org.ruoyi.ecg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强版文本清洗工具
 * - 保留 JSON 规则加载（用于自定义补充）
 * - 内置丰富映射：中文数字、英文单位/术语、医学术语缩写
 * - 深度清洗：去除语气词、填充词、重复标点、‘的’结构等
 * - 支持数字掩码 ([NUM])、标点移除
 */
@Component
public class TextCleaner {

    private static final Logger log = LoggerFactory.getLogger(TextCleaner.class);

    // ======================== 内置映射表 ========================

    // 中文数字 → 阿拉伯数字（解析用）
    private static final Map<Character, Integer> CN_NUM_MAP = new HashMap<>();
    static {
        CN_NUM_MAP.put('零', 0); CN_NUM_MAP.put('一', 1); CN_NUM_MAP.put('二', 2);
        CN_NUM_MAP.put('三', 3); CN_NUM_MAP.put('四', 4); CN_NUM_MAP.put('五', 5);
        CN_NUM_MAP.put('六', 6); CN_NUM_MAP.put('七', 7); CN_NUM_MAP.put('八', 8);
        CN_NUM_MAP.put('九', 9); CN_NUM_MAP.put('十', 10); CN_NUM_MAP.put('百', 100);
        CN_NUM_MAP.put('千', 1000); CN_NUM_MAP.put('万', 10000); CN_NUM_MAP.put('两', 2);
    }

    // 英文单位/术语 → 中文（按长度降序）
    private static final Map<String, String> UNIT_MAP = new LinkedHashMap<>();
    static {
        // 监测指标（含数字）
        UNIT_MAP.put("SpO2", "血氧饱和度");
        UNIT_MAP.put("spo2", "血氧饱和度");
        UNIT_MAP.put("EtCO2", "呼气末二氧化碳");
        UNIT_MAP.put("etco2", "呼气末二氧化碳");
        UNIT_MAP.put("FiO2", "吸氧浓度");
        UNIT_MAP.put("fio2", "吸氧浓度");

        // 监测指标（不含数字）
        UNIT_MAP.put("ECG", "心电图"); UNIT_MAP.put("ecg", "心电图");
        UNIT_MAP.put("EKG", "心电图"); UNIT_MAP.put("ekg", "心电图");
        UNIT_MAP.put("BP", "血压"); UNIT_MAP.put("bp", "血压");
        UNIT_MAP.put("HR", "心率"); UNIT_MAP.put("hr", "心率");
        UNIT_MAP.put("RR", "呼吸频率"); UNIT_MAP.put("rr", "呼吸频率");
        UNIT_MAP.put("CVP", "中心静脉压"); UNIT_MAP.put("cvp", "中心静脉压");
        UNIT_MAP.put("ICP", "颅内压"); UNIT_MAP.put("icp", "颅内压");
        UNIT_MAP.put("MAP", "平均动脉压"); UNIT_MAP.put("map", "平均动脉压");
        UNIT_MAP.put("PEEP", "呼气末正压"); UNIT_MAP.put("peep", "呼气末正压");

        // 压力单位
        UNIT_MAP.put("mmHg", "毫米汞柱"); UNIT_MAP.put("mmhg", "毫米汞柱");

        // 质量单位
        UNIT_MAP.put("mg", "毫克"); UNIT_MAP.put("Mg", "毫克"); UNIT_MAP.put("mG", "毫克");
        UNIT_MAP.put("MG", "毫克"); UNIT_MAP.put("g", "克"); UNIT_MAP.put("G", "克");
        UNIT_MAP.put("kg", "千克"); UNIT_MAP.put("Kg", "千克"); UNIT_MAP.put("KG", "千克");
        UNIT_MAP.put("ug", "微克"); UNIT_MAP.put("μg", "微克");

        // 容量单位
        UNIT_MAP.put("ml", "毫升"); UNIT_MAP.put("mL", "毫升");
        UNIT_MAP.put("ML", "毫升"); UNIT_MAP.put("L", "升"); UNIT_MAP.put("l", "升");

        // 长度单位
        UNIT_MAP.put("cm", "厘米"); UNIT_MAP.put("Cm", "厘米"); UNIT_MAP.put("CM", "厘米");
        UNIT_MAP.put("mm", "毫米"); UNIT_MAP.put("Mm", "毫米"); UNIT_MAP.put("MM", "毫米");

        // 时间/能量/其他
        UNIT_MAP.put("min", "分钟"); UNIT_MAP.put("s", "秒"); UNIT_MAP.put("ms", "毫秒");
        UNIT_MAP.put("J", "焦耳"); UNIT_MAP.put("j", "焦耳");
        UNIT_MAP.put("mmol", "毫摩尔");
        UNIT_MAP.put("mEq", "毫当量");
        UNIT_MAP.put("IU", "国际单位"); UNIT_MAP.put("iu", "国际单位");
        UNIT_MAP.put("bpm", "次/分"); UNIT_MAP.put("BPM", "次/分");

        // 医学术语缩写（不含数字）
        UNIT_MAP.put("VF", "心室颤动"); UNIT_MAP.put("vf", "心室颤动");
        UNIT_MAP.put("VT", "室性心动过速"); UNIT_MAP.put("vt", "室性心动过速");
        UNIT_MAP.put("PEA", "无脉性电活动"); UNIT_MAP.put("pea", "无脉性电活动");
        UNIT_MAP.put("Asystole", "心室停搏"); UNIT_MAP.put("asystole", "心室停搏");
        // ROSC 保留不替换
    }

    // 句首填充词（按长度降序）
    private static final List<String> START_FILLERS = Arrays.asList(
            "就是说", "也就是说", "那个那个", "好吧", "好的吧", "那个", "这个", "好的",
            "好", "行", "OK", "ok", "Okay", "okay", "嗯", "呃", "哎", "哎呀", "诶", "嘿", "哈", "噢", "哦", "喔"
    );

    // 句末语气词
    private static final List<String> END_PARTICLES = Arrays.asList(
            "了", "呢", "啊", "呀", "吧", "哦", "噢", "喔", "嘛", "呗",
            "哈", "嘿", "啦", "咧", "喽", "咯", "哪", "呐"
    );

    // 单独语气词（任何位置）
    private static final List<String> SINGLE_PARTICLES = Arrays.asList(
            "啊", "呀", "啦", "哦", "噢", "喔", "嘿", "哈"
    );

    // 保护词（不误删）
    private static final Set<String> PROTECTED_WORDS = new HashSet<>(Arrays.asList(
            "插管", "除颤", "按压", "给药", "推注", "开放气道",
            "肾上腺素", "胺碘酮", "阿托品", "多巴胺",
            "心电图", "血压", "心率", "血氧饱和度"
    ));

    // 标点正则（移除用）
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile(
            "[，。、！？；：\"“”‘’（）\\[\\]【】《》〈〉「」『』〔〕\\.,!?;:'\"\\(\\)<>/\\\\|]"
    );

    // 重复标点清理（保留中文标点）
    private static final Pattern REPEAT_PUNCT = Pattern.compile("([，。、！？；：])\\1+");
    private static final Pattern REPEAT_EN_PUNCT = Pattern.compile("([.,!?;:])\\1+");

    // ======================== JSON 配置加载（保留） ========================

    private Set<String> fillerWords = new HashSet<>();
    private Map<String, String> numberNormalizations = new LinkedHashMap<>();
    private Map<String, String> phoneticCorrections = new LinkedHashMap<>();
    private Map<String, String> colloquialMappings = new LinkedHashMap<>();

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        log.info("加载 TextCleaner 规则 (JSON)...");
        try {
            ClassPathResource resource = new ClassPathResource("config/text-cleaner-rules.json");
            if (!resource.exists()) {
                log.warn("未找到 config/text-cleaner-rules.json，使用内置规则");
                initialized = true;
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            try (InputStream is = resource.getInputStream()) {
                TextCleanerRules rules = mapper.readValue(is, TextCleanerRules.class);

                if (rules.fillerWords != null) fillerWords = new HashSet<>(rules.fillerWords);
                if (rules.numberNormalizations != null) numberNormalizations = new LinkedHashMap<>(rules.numberNormalizations);
                if (rules.phoneticCorrections != null) phoneticCorrections = new LinkedHashMap<>(rules.phoneticCorrections);
                if (rules.colloquialMappings != null) colloquialMappings = new LinkedHashMap<>(rules.colloquialMappings);
            }

            initialized = true;
            log.info("加载成功: 语气词{}, 数字归一{}, 同音纠正{}, 口语映射{}",
                fillerWords.size(), numberNormalizations.size(),
                phoneticCorrections.size(), colloquialMappings.size());
        } catch (Exception e) {
            log.error("加载规则失败，使用内置规则", e);
            initialized = true; // 仍可使用内置映射
        }
    }

    /** 用于接收 text-cleaner-rules.json 的强类型映射，避免 unchecked 警告 */
    private static class TextCleanerRules {
        public List<String> fillerWords;
        public Map<String, String> numberNormalizations;
        public Map<String, String> phoneticCorrections;
        public Map<String, String> colloquialMappings;
    }
    // ======================== 核心归一化方法 ========================

    /**
     * 归一化主入口（对齐 Python TextNormalizer.normalize）
     * @param text 原始文本
     * @param maskNumeric 是否将数字替换为 [NUM]
     * @param clean 是否进行深度清洗（语气词、重复标点等）
     * @param removePunct 是否移除标点符号
     * @return 归一化后的文本
     */
    public String normalize(String text, boolean maskNumeric, boolean clean, boolean removePunct) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        // 1. 中文数字 → 阿拉伯数字
        result = cnNumToArabic(result);

        // 2. 替换含数字的术语（SpO2、EtCO2、FiO2）
        result = replaceTermsWithNumbers(result);

        // 3. 占位符 {param} → [NUM]
        result = result.replaceAll("\\{[^}]+\\}", "[NUM]");

        // 4. 数字掩码
        if (maskNumeric) {
            result = result.replaceAll("\\d+(?:\\.\\d+)?", "[NUM]");
        }

        // 5. 英文单位/术语 → 中文
        result = replaceUnits(result);

        // 6. 深度清洗（语气词、重复标点、填充词等）
        if (clean) {
            result = deepClean(result);
        }

        // 7. 移除标点符号
        if (removePunct) {
            result = removePunctuation(result);
        }

        // 8. 应用 JSON 自定义规则（补充）
        result = applyJsonRules(result);

        // 9. 合并多余空格
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    /**
     * 兼容旧接口：clean(String text)
     * 等价于 normalize(text, true, true, true) （与 Python 默认一致）
     */
    public String clean(String text) {
        return normalize(text, true, true, true);
    }

    // ======================== 内部处理步骤 ========================

    // ----- 中文数字转阿拉伯 -----
    private String cnNumToArabic(String text) {
        if (text == null) return text;
        Pattern p = Pattern.compile("[零一二三四五六七八九十百千万两]+");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String cn = m.group();
            String arabic = parseCnNumber(cn);
            m.appendReplacement(sb, Matcher.quoteReplacement(arabic));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String parseCnNumber(String cn) {
        if (cn == null || cn.isEmpty()) return "0";
        if (cn.equals("十")) return "10";
        cn = cn.replace('两', '二');
        int result = 0;
        int temp = 0;
        for (char c : cn.toCharArray()) {
            Integer val = CN_NUM_MAP.get(c);
            if (val == null) continue;
            if (val >= 10) { // 单位
                if (temp == 0) temp = 1;
                if (val >= 10000) {
                    result = (result + temp) * val;
                    temp = 0;
                } else {
                    temp = (temp > 0) ? temp * val : val;
                }
            } else {
                temp = (temp > 0) ? temp * 10 + val : val;
            }
        }
        return String.valueOf(result + temp);
    }

    // ----- 替换含数字术语 -----
    private String replaceTermsWithNumbers(String text) {
        // 只在 UNIT_MAP 中取包含数字的键（如 SpO2）
        for (Map.Entry<String, String> entry : UNIT_MAP.entrySet()) {
            String key = entry.getKey();
            if (key.matches(".*\\d.*")) { // 含数字
                Pattern p = Pattern.compile(Pattern.quote(key) + "(?![a-zA-Z])");
                text = p.matcher(text).replaceAll(Matcher.quoteReplacement(entry.getValue()));
            }
        }
        return text;
    }

    // ----- 英文单位/术语转中文 -----
    private String replaceUnits(String text) {
        // 先处理带数字单位，如 [NUM]mmHg 或 120mmHg
        for (Map.Entry<String, String> entry : UNIT_MAP.entrySet()) {
            String key = entry.getKey();
            if (key.matches(".*\\d.*")) continue; // 已在上一步处理
            // 匹配前面有数字或 [NUM] 的情况
            Pattern p1 = Pattern.compile("(\\[NUM\\]|\\d+(?:\\.\\d+)?)\\s*" + Pattern.quote(key) + "(?![a-zA-Z])");
            text = p1.matcher(text).replaceAll("$1" + entry.getValue());
            // 匹配独立词
            Pattern p2 = Pattern.compile("(?<![0-9a-zA-Z\\]])" + Pattern.quote(key) + "(?![a-zA-Z])");
            text = p2.matcher(text).replaceAll(Matcher.quoteReplacement(entry.getValue()));
        }
        return text;
    }

    // ----- 深度清洗（语气词、重复标点、‘的’结构等） -----
    private String deepClean(String text) {
        if (text == null) return text;
        String result = text;

        // 1. 清洗重复标点（中文）
        result = REPEAT_PUNCT.matcher(result).replaceAll("$1");
        result = REPEAT_EN_PUNCT.matcher(result).replaceAll("$1");

        // 2. 句首填充词去除
        for (String filler : START_FILLERS) {
            if (result.startsWith(filler)) {
                String rest = result.substring(filler.length());
                if (rest.isEmpty() || "，。、！？,.!? ".indexOf(rest.charAt(0)) >= 0) {
                    result = rest.replaceFirst("^[，。、！？,.!? ]+", "");
                    break;
                }
            }
        }

        // 3. 句末语气词去除（避免误删保护词）
        for (String particle : END_PARTICLES) {
            if (result.endsWith(particle)) {
                String prefix = result.substring(0, result.length() - particle.length());
                if (!prefix.isEmpty()) {
                    char last = prefix.charAt(prefix.length() - 1);
                    // 如果前一个字是保护词的关键字，跳过
                    if ("药路道管律压量率颤动".indexOf(last) >= 0) continue;
                    // 如果去掉后为空，保留
                    result = prefix;
                }
            }
        }

        // 4. 去除“动词+的+名词”中的“的”（非保护词）
        result = result.replaceAll("(检查|观察|评估|确认|核对|记录|报告|调整|准备|开始|继续|停止)的(?!用|人|事|物)", "$1");

        // 5. 去除“完成/结束/停止+了”
        result = result.replaceAll("(完成|结束|完毕|停止)了", "$1");

        // 6. 去除单独的单个语气词（前后有标点或边界）
        for (String particle : SINGLE_PARTICLES) {
            result = result.replaceAll("[，。、！？，.！?]" + particle + "[，。、！？，.！?]", "");
            result = result.replaceAll("^" + particle + "[，。、！？，.！? ]", "");
            result = result.replaceAll("[，。、！？，.！?]" + particle + "$", "");
        }

        // 7. 去除多余空格
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    // ----- 移除标点（仅保留中文、英文、数字、空格、[NUM]） -----
    private String removePunctuation(String text) {
        if (text == null) return text;
        return PUNCTUATION_PATTERN.matcher(text).replaceAll("");
    }

    // ----- 应用 JSON 自定义规则（补充） -----
    private String applyJsonRules(String text) {
        if (text == null) return text;
        String result = text;
        // 数字归一化（简单替换）
        for (Map.Entry<String, String> entry : numberNormalizations.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        // 同音纠正
        for (Map.Entry<String, String> entry : phoneticCorrections.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        // 口语映射
        for (Map.Entry<String, String> entry : colloquialMappings.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        // 补充填充词（来自 JSON）
        for (String filler : fillerWords) {
            result = result.replaceAll("^" + filler + "\\s*", "");
            result = result.replaceAll("\\s*" + filler + "$", "");
        }
        return result;
    }

    // ======================== 额外辅助方法 ========================

    /**
     * 检测是否包含语气词（简单实现）
     */
    public boolean hasFillerWords(String text) {
        if (text == null) return false;
        // 检查内置 + JSON 填充词
        for (String filler : START_FILLERS) {
            if (text.contains(filler)) return true;
        }
        for (String filler : fillerWords) {
            if (text.contains(filler)) return true;
        }
        return false;
    }
}
