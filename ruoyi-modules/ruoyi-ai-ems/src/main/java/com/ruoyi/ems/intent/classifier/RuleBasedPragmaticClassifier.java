package com.ruoyi.ems.intent.classifier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 基于规则的语用分类器（只判元语用）。
 *
 * <p>v6：新增"对象+对象"方向（允许两个临床对象相邻，如"电击前清场"），
 * 大幅扩充动作词与对象词表。
 */
@Slf4j
@Component
public class RuleBasedPragmaticClassifier implements PragmaticClassifier {

    private static final Set<String> SIMPLE_ACKS = Set.of(
        "收到", "收到啦", "收到收到",
        "好的", "好", "行",
        "明白", "明白了",
        "是的", "对", "对的"
    );

    /**
     * 动作动词表。长词必须排在短词之前。
     */
    private static final String ACTION_WORDS =
        "(?:检查|评估|测量|测|观察|看|查|摸|听|问诊|询问|问"
            + "|连接|接|放置|放|贴|插入|插|推注|给药|实施|执行|给|做"
            + "|准备|拿|取|打开|开机|开启|启动|开通|开|关|调整|调|设置|选择"
            + "|固定|移动|抬|搬|转移|转运|上"
            + "|通知|呼叫|叫|联系|告诉"
            + "|记录|记|报告|汇报|填写|补|录|确认|核对|交接|解释|回顾|说明"
            + "|接近|靠近|判断|拍|摇|复述|请求|离开|按|数|量|读|说|讲"
            + "|比较|比|分析|使用|清理|继续|采集|保持)";

    /**
     * 对象词表。临床要素 + 定位术语 + 药物 + 状态词。
     */
    private static final String OBJECT_WORDS =
        "(?:瞳孔|对光|PERRL|导联|电极|位置|V[1-6]"
            + "|球囊|面罩|BVM|气道|呼吸|通气|吸引|NPA|鼻咽|呼吸频率|呼吸次数|RR"
            + "|脉搏|脉率|颈动脉|桡动脉|心率|血压|血氧|血糖|血糖值|读数|生命体征"
            + "|意识|定向力|失语|构音|语言|视野|视力|缺损|变化|握力|面部|手臂|平衡|协调|反应|好转"
            + "|AED|除颤|除颤仪|电击|复律|电复律|放电|充电|焦耳|镇静|同步模式|非同步模式|同步|非同步|电"
            + "|医院|卒中|转运|担架|救护车|搬运|LKW时间|LKW|最后正常|最后一次正常|发病时间"
            + "|QRS|窄QRS|宽QRS|窄|宽|SBP|DBP|SpO2|ETCO2|GCS|ROSC|PEA|VF|VT|SVT|AVRT|WPW"
            + "|现场安全|安全|患者|心电|心电图|心电图机|肩"
            + "|抗凝药|抗凝|用药情况|当前用药|用药|药物|华法林|利伐沙班|阿哌沙班|达比加群|艾多沙班|依诺肝素|低分子肝素"
            + "|腺苷|胺碘酮|地尔硫卓|美托洛尔|艾司洛尔"
            + "|现病史|既往史|病史|主诉|物质使用|药物使用|酒精|吸毒|饮酒"
            + "|清场|皮肤|备皮|剃毛|剃除|接触|处理|给氧"
            + "|颜色|温度|湿度"
            + "|胸外按压|按压深度|按压频率|按压"
            + "|房颤|房扑|窦性|心律|节律|预激"
            + "|停搏|心脏停|所有人"
            // 定位术语（新增）
            + "|肋间|第[1-6]肋间|胸骨右缘|胸骨左缘|腋前线|腋中线|锁骨中线"
            + "|左侧腋前线|左腋中线)";

    /**
     * 词间允许跨越的非标点字符数。
     * <p>字符集：排除中英文标点（逗号/句号/问号/感叹号/分号/顿号）。
     */
    private static final String GAP = "[^，。！？,.?!;；、]{0,8}";

    /**
     * 前置检查：
     * <ul>
     *   <li>A: 动作 + 对象</li>
     *   <li>B: 对象 + (动作|问法)</li>
     *   <li>C: 对象 + 对象（允许两个临床对象相邻，覆盖"电击前清场"等）</li>
     * </ul>
     */
    private static final Pattern CONCRETE_INTENT_HINT = Pattern.compile(
        "(?:"
            + ACTION_WORDS + GAP + OBJECT_WORDS
            + "|"
            + OBJECT_WORDS + GAP + "(?:" + ACTION_WORDS + "|怎么样|如何)"
            + "|"
            + OBJECT_WORDS + GAP + OBJECT_WORDS
            + ")"
    );

    private static final Pattern VETO = Pattern.compile(
        "(?<!是)不是"
            + "|(?<!对)不对"
            + "|别做|别这样|别动|别碰|别推|别给|别用|别说|别管"
            + "|不要这(个|样|么|种)|不要这么做|不要做|不要动|不要碰|不要给|不要用"
            + "|否决"
            + "|停下|停止"
            + "|错(了|啦)|搞错|弄错"
            + "|(?<!能)不能这(样|么)|不准|不许"
    );

    private static final Pattern VETO_EXCEPTION = Pattern.compile(
        "别停|别松开|别放手|别中断|别断|别放弃|别慢|别犹豫|别发呆|别停手"
    );

    private static final Pattern STRATEGY_CORRECTION = Pattern.compile(
        "改成|改为|换成|换上|换掉|换一(个|种)"
            + "|调整为|调成|修正为|修正成|纠正|改用|改变为"
            + "|重新(来|做|给|推|用|开始|连接|插|量|测)"
            + "|(不能|别|不要|不应)(只|光|仅|单单|仅仅)"
            + "|(不能|别|不要|不应)(跳过|遗漏|忽略|省掉|漏掉|忘)"
            + "|不(全面|完整)|要全面|要完整"
            + "|(还要|还应该|还要做|还得|还需要)(?!停)"
    );

    private static final Pattern CLOSED_LOOP = Pattern.compile(
        "("
            + "了吗|了没|好了吗|好了没|完了吗|完了没|到了吗|到了没|上了吗|上了没"
            + "|出来了吗|出来了没|明白了吗|可以了吗|行了吗|对了吗|对上了吗"
            + "|到位了吗|就位了吗|开始了吗|开始没|结束了吗|结束没"
            + "|完成了吗|完成没|打上了吗|打上了没"
            + "|做完了吗|做完了没|弄好了吗|弄好了没|准备好了吗|准备好了没"
            + "|核对了吗|核对好了吗|报告了吗|接上了吗|连接好了吗|插上了吗|插好了吗"
            + "|量了吗|测了吗|确认了吗|确认好了吗"
            + "|看到了吗|看见了吗|听见了吗|听到没"
            + "|行没|可以没"
            + "|(有没有|有没)(好转|恢复|改善|变化|反应|知觉)"
            + "|(好转|恢复|改善|清醒)了吗"
            + "|(怎么样|如何)了"
            + ")"
            + "(?:[?？])?"
            + "(?:[。.!！])?"
            + "(?:收到|好|行|明白)?$"
    );

    @Override
    public ActTypePrediction predict(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        final String text = normalize(rawText);

        if (SIMPLE_ACKS.contains(text)) {
            if (log.isDebugEnabled()) {
                log.debug("[Pragmatic] 命中简单确认: {}", text);
            }
            return ActTypePrediction.of(ActType.ANNOUNCEMENT, 0.95, "简单确认");
        }

        if (CONCRETE_INTENT_HINT.matcher(text).find()) {
            if (log.isDebugEnabled()) {
                log.debug("[Pragmatic] 含具体意图特征，跳过元语用: {}", text);
            }
            return null;
        }

        final boolean vetoException = VETO_EXCEPTION.matcher(text).find();
        if (!vetoException && VETO.matcher(text).find()) {
            return ActTypePrediction.of(ActType.VETO, 0.95, "命中否决");
        }

        if (STRATEGY_CORRECTION.matcher(text).find()) {
            return ActTypePrediction.of(ActType.STRATEGY_CORRECTION, 0.90, "命中策略纠偏");
        }

        if (CLOSED_LOOP.matcher(text).find()) {
            return ActTypePrediction.of(ActType.CLOSED_LOOP, 0.90, "命中闭环确认");
        }

        if (log.isDebugEnabled()) {
            log.debug("[Pragmatic] 未命中元语用: {}", text);
        }
        return null;
    }

    private static String normalize(String text) {
        String s = text.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            } else if (c == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", "");
    }
}
