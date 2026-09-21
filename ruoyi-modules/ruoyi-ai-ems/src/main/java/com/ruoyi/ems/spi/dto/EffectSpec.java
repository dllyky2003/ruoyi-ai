package com.ruoyi.ems.spi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已解析授权效果规格（V3.1 §5.2）。
 *
 * <p>ClinicalRuleEngine 在评估规则时，已完成 constraints 求值，
 * 只返回当前事件/状态下满足全部约束的 {@code resolved=true} 效果。</p>
 *
 * <p>教案层（ScenarioBeat）只能读取 {@code resolved=true} 的 EffectSpec，
 * 通过 {@code effectId + branchId} 匹配候选，<b>不重新解释 constraints</b>。</p>
 *
 * <p>权威归属：
 * <ul>
 *   <li>{@code authorizationId} / {@code authorizedByRuleIds} / {@code effectId}
 *       / {@code branchId} / {@code resolved} — 临床规则层</li>
 *   <li>规则静态定义（constraints）不在此类中，只用于规则文件的审计与契约检查</li>
 * </ul>
 * </p>
 */
@Data
public class EffectSpec {

    /** 本次授权唯一 ID，用于审计追溯（回放时解释"为什么该效果被允许"）。 */
    private String authorizationId;

    /** 产生本授权的规则 ID 列表（可多个规则联合授权）。 */
    private List<String> authorizedByRuleIds = new ArrayList<>();

    /** 效果类型，如 {@code trigger_case_outcome_branch}。 */
    private String effectId;

    /** 分支 ID，指向 {@code clinical_rules.yaml} 中 {@code case_outcome_branches.branch_id}。 */
    private String branchId;

    /** 是否已完成约束求值（true = 运行时可直接执行）。 */
    private boolean resolved;
}
