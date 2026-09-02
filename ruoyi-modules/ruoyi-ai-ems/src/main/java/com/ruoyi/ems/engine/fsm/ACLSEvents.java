package com.ruoyi.ems.engine.fsm;

/**
 * ACLS状态机事件定义
 */
public enum ACLSEvents {
    RATE_CONFIRMED,          // 心率确认
    PULSE_CONFIRMED,         // 脉搏确认
    STABLE,                  // 患者稳定
    UNSTABLE,                // 患者不稳定
    NARROW_QRS,              // QRS波窄（<120ms）
    WIDE_QRS,                // QRS波宽（≥120ms）
    VAGAL_DONE,              // 迷走神经刺激操作完成
    ADENOSINE_6MG_DONE,      // 腺苷6mg静脉推注完成
    ADENOSINE_12MG_DONE,     // 腺苷12mg静脉推注完成
    TREATMENT_DONE,          // 当前治疗方案执行完毕
    EXPERT_CONSULT_DONE,     // 专家会诊已完成
    CARDIOVERSION_DONE,      // 同步电复律操作完成
    REASSESS_DONE            // 治疗后再评估完成
}
