package com.ruoyi.ems.domain.TeachingMode;


/**
 * 教学 / 学习 / 考核 三模式。
 * 决定异常命中后的反馈时机与内容；effects 与 jumpTo 三模式统一执行。
 */
public enum TeachingMode {
    /** 教学：AI 当组长，示范 + 讲解；异常规则用于学员提问时的讲解 */
    TEACHING,
    /** 学习：学员操作，仅 CRITICAL 即时打断，其余后置复盘 */
    LEARNING,
    /** 考核：学员操作，全程静默，异常仅记录用于事后评分 */
    EXAM
}
