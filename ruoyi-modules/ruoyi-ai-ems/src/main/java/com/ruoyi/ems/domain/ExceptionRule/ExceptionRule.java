package com.ruoyi.ems.domain.ExceptionRule;

import lombok.Data;
import java.util.List;

/**
 * ACLS 异常操作规则。
 * 对应 config/acls/GLOBAL/_exceptions.yaml
 *      config/acls/cases/{caseId}/_exceptions.yaml
 * 合并语义：案例同名 id 覆盖全局。
 */
@Data
public class ExceptionRule {

    private String id;
    private String description;
    private String phase;
    private String triggerIntent;   // 与 condition 至少一个非空
    private String condition;       // 与 triggerIntent 至少一个非空
    private String severity;        // CRITICAL / MINOR / TIMING
    private String unityStateName;  // Unity 有效状态名
    private List<EffectRule> effects;
    private String jumpTo;
    private Feedback feedback;
    /**
     * true = 拦截类规则，不跳转状态机，只改 effects + 记 errorChain + 反馈。
     * 此时 jumpTo 允许为空（会被忽略）。
     */
    private Boolean keepCurrent;
    @Data
    public static class EffectRule {
        private String var;
        /** 静态值，优先于 valueExpression */
        private Object value;
        /** SpEL 动态值，value == null 时求值；必须以 # 开头 */
        private String valueExpression;
    }

    /**
     * 三模式反馈。缺省 = 该模式静默。
     */
    @Data
    public static class Feedback {
        private ModeFeedback teaching;
        private ModeFeedback learning;
        private ModeFeedback exam;

        @Data
        public static class ModeFeedback {
            private String tts;
            /** 仅 tts 非空时有意义；教学默认 false，学习仅 CRITICAL 建议 true，考核固定 false */
            private Boolean interruptCurrentTts;
        }
    }
}
