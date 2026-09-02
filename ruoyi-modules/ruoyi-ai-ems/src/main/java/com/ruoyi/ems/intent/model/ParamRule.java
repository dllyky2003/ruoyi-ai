package com.ruoyi.ems.intent.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个参数的抽取规则
 */
@Data
@NoArgsConstructor
public class ParamRule {

    private String type;                    // 数据类型：integer, string, boolean, array

    private String source;                  // 数据来源：numeric_value, keyword, static

    @JsonProperty("static")
    private Object staticValue;             // 静态值（当 source 为 static 时使用），可为字符串或布尔

    private List<String> enumValues;        // 枚举限定列表（原 JSON 中的 enum）

    private List<String> items;             // 当 type 为 array 时，限定数组元素的值列表

    private String unit;                    // 单位：如 J, mg, ml

    private String logic;                   // 自然语言抽取逻辑描述（供LLM参考或人工查看）
}
