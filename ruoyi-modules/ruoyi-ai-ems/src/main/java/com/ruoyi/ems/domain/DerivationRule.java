package com.ruoyi.ems.domain;

import lombok.Data;

import java.util.List;

@Data
public class DerivationRule {
    private String target;          // 目标变量名
    private String type;            // boolean, string, number（可选）
    private String description;     // 描述（可选）
    private String expression;      // SpEL 表达式
    private List<String> dependencies; // 依赖的变量名（可选，用于排序）
}

