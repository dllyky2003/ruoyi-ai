package com.ruoyi.ems.engine.fsm;

/**
 * ACLS状态机状态定义
 */
public enum ACLSStates {
    INITIAL_ASSESSMENT,          // 初始评估（确认心率>150，有脉搏）
    STABILITY_CHECK,             // 稳定性评估（决策节点：判断患者是否稳定）
    QRS_EVALUATION,              // QRS宽度评估（决策节点：窄QRS/宽QRS）
    VAGAL_MANEUVER,              // 迷走神经刺激（一线治疗）
    ADENOSINE_6MG,               // 腺苷6mg（首剂快速推注）
    ADENOSINE_12MG,              // 腺苷12mg（二剂推注）
    BETA_BLOCKER_OR_CCB,         // β阻滞剂/钙通道阻滞剂（二线治疗）
    EXPERT_CONSULT,              // 专家会诊（疑难病例咨询）
    SYNCHRONIZED_CARDIOVERSION,  // 同步电复律（不稳定患者直接电复律）
    POST_TREATMENT_REASSESS,     // 治疗后再评估（确认疗效）
    COMPLETED,                   // 完成（终止状态）
    WIDE_QRS_PATH                // 宽QRS路径（预留分支，处理宽QRS心动过速）
}
