package com.ruoyi.ems.domain;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 生理演化规则。
 *
 * <p><b>两种使用模式</b>：</p>
 * <ol>
 *   <li><b>旧模式（默认）</b>：仅填 rhythm / variable / afterSeconds / delta，
 *       表示"在 rhythm 状态下停留 afterSeconds 秒后，对 variable 应用一次 delta"。</li>
 *   <li><b>新模式</b>：额外填 perTick / stopWhen / bounds / appliesWhen / operation / value，
 *       支持连续演化、干预停止、边界约束、SpEL 条件。</li>
 * </ol>
 *
 * <p><b>向后兼容</b>：perTick 缺省 = false（阶跃应用一次），与现有 YAML 语义一致。</p>
 *
 * <p><b>YAML 示例（新模式）</b>：
 * <pre>
 * evolution_rules:
 *   - id: SVT_SBP_DECAY
 *     description: "SVT 未处理时 SBP 持续下降"
 *     rhythm: svt
 *     variable: phys.systolicBp
 *     after_seconds: 5
 *     delta: -0.4
 *     per_tick: true
 *     bounds: { min: 60, max: 200 }
 *     stop_when: "#variables['intervention.adenosineGiven'] == true"
 * </pre>
 *
 * <p><b>★ 2026-09-23 新增 branch 字段（螺旋 3）</b>：用于按学员操作质量
 * （NORMAL / ERROR / DELAYED）分派生理演化路径。详见
 * {@code com.ruoyi.acls.engine.branch.StudentResponseBranch}。</p>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolutionRule {

    // ==================== 原字段（保留，语义不变） ====================

    /** 适用心律（如 "svt" / "SVT" / "vt"）；为 null 时对所有心律生效。
     *  <p>匹配时大小写不敏感。</p> */
    private String rhythm;

    /** 目标变量完整名（如 "phys.systolicBp"），直接作为 CaseContext 的 key */
    private String variable;

    /** 触发阈值：会话运行满 afterSeconds 秒后开始生效 */
    private long afterSeconds;

    /**
     * 数值变化量。
     * <p>perTick=false 时表示"一次性变化量"；perTick=true 时表示"每 tick 变化量"。</p>
     */
    private Float delta;

    // ==================== 新增字段（全部可选，向后兼容） ====================

    /**
     * 规则唯一标识（病例内唯一）。
     * <p>用于 stop_when 停用状态追踪。为 null 时引擎按
     * {@code rhythm + ":" + variable + ":" + afterSeconds} 自动生成。</p>
     */
    private String id;

    /** 人类可读描述，仅用于日志 */
    private String description;

    /**
     * ★ 螺旋 3：学员操作分支过滤。
     *
     * <p>取值：{@code "normal"} / {@code "error"} / {@code "delayed"}（大小写不敏感）。
     * 为 null 时**对所有分支生效**（向后兼容，旧规则不填即行为不变）。</p>
     *
     * <p>由 {@code EvolutionRuleEvaluator.onTick} 在入口处按
     * {@code StudentResponseResolver.resolve(sessionId)} 的当前分支过滤：
     * 不匹配则直接跳过本规则。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * evolution_rules:
     *   - id: SVT_SBP_DROP_30S
     *     branch: delayed        # 仅当学员延时操作时生效
     *     rhythm: svt
     *     variable: phys.systolicBp
     *     after_seconds: 30
     *     delta: -10.0
     * </pre>
     */
    private String branch;

    /**
     * 额外触发条件（SpEL 表达式），与 afterSeconds 是 AND 关系。
     * <p>除 phys/process/intervention/device/clinical/derived 之外的变量，
     * 一律走 {@code #variables['xxx']} 键访问。</p>
     */
    private String appliesWhen;

    /**
     * 停止条件（SpEL 表达式），命中后规则在本会话内**永久停用**。
     * <p>典型用法：干预达成后停止恶化。</p>
     */
    private String stopWhen;

    /**
     * 是否每 tick 应用一次 delta。
     * <ul>
     *   <li>true  = 连续演化（如 SBP 每秒下降 0.4）</li>
     *   <li>false / null = 仅当 afterSeconds 首次命中时应用一次（默认，与现有 YAML 兼容）</li>
     * </ul>
     */
    private Boolean perTick;

    /** 边界约束。仅对 add / subtract 生效。 */
    private Bounds bounds;

    /**
     * 操作类型：add / subtract / set。
     * <p>为 null 时按 delta 符号推断：delta ≥ 0 → add，delta &lt; 0 → subtract。</p>
     * <p>set 操作时 delta 字段忽略，variable 被直接替换为 value。</p>
     */
    private String operation;

    /** set 操作的目标值（如 rhythm 恶化为 "AFib"）；与 operation=set 配合使用 */
    private Object value;

    @Data
    public static class Bounds {
        private Double min;
        private Double max;
    }
}
