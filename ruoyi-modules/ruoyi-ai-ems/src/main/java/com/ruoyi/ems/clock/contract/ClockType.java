package com.ruoyi.ems.clock.contract;


/**
 * 5 类时钟类型契约。
 * 螺旋 4 G4-07：订阅者可区分。
 */
public enum ClockType {
    SESSION,      // 会话时钟：案例总时长、暂停/恢复
    PHYSIOLOGY,   // 生理时钟：状态驻留、生理演化节拍
    DRUG,         // 药物时钟：推药后观察窗、二次给药判定
    ASSESSMENT,   // 评估时钟：评分、审计锚点
    TEACHING      // 教学时钟：Tutor beat、反馈节拍
}
