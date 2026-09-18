package com.ruoyi.ems.intent.dict;



/** 词典中的一条映射（已补全 intentName/normalized） */
public record InvertedHit(
    String intentCode,
    String intentName,
    String normalized,
    String type,
    double confidence,
    String note
) {}
