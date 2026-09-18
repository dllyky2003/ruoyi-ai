package com.ruoyi.ems.intent.utils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DrugAliasRegistry {
    private static final Map<String, String> ALIAS_TO_CODE = new LinkedHashMap<>();
    private static final Map<String, List<String>> INTENT_ALIASES = new HashMap<>();

    static {
        // 注册 S-101-2-5 对应的所有别名（按长度降序）
        INTENT_ALIASES.put("S-101-2-5", Arrays.asList(
            "美托洛尔", "倍他乐克", "酒石酸美托洛尔", "metoprolol",
            "地尔硫卓", "合贝爽", "盐酸地尔硫卓", "diltiazem",
            "艾司洛尔", "爱络", "盐酸艾司洛尔", "esmolol"
        ));
    }

    public static String buildDrugPattern(String intentCode) {
        List<String> aliases = INTENT_ALIASES.getOrDefault(intentCode, Collections.emptyList());
        if (aliases.isEmpty()) return "(?!)";
        return "(" + aliases.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")";
    }
    private DrugAliasRegistry() {}
}
