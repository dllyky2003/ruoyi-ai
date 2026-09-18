package com.ruoyi.ems.intent.dict;


import java.util.List;

/** 词典根对象（只映射需要的字段） */
public class InvertedDict {
    public String dictVersion;
    public String model;
    public Stats stats;
    public java.util.Map<String, java.util.List<Object[]>> entries;

    public static class Stats {
        public int total_intents;
        public int total_surfaces;
        public int total_mappings;
    }
}
