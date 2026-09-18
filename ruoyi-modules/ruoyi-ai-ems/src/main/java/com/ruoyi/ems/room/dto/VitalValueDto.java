package com.ruoyi.ems.room.dto;

import lombok.Data;

/**
 * 单个体征变量的值。
 * <p>
 * Unity 上行 vitals_response / vitals_update 时，values 里的每一项都是该结构。
 * 也用于 PhysiologyVariableCache 内部存储。
 * </p>
 */
@Data
public class VitalValueDto {

    /** 原始值。类型由 type 字段说明：number / boolean / enum / string */
    private Object value;

    /** 单位，如 "%"、"mmHg"、"次/分"，可为空 */
    private String unit;

    /** 类型标识：number / boolean / enum / string */
    private String type;

    /** 来源：physiology_engine / engine / npc_action / skill */
    private String source;

    /** 数据时间戳（毫秒）。Unity 上报时可带，服务端写入时若为空则填当前时间 */
    private long timestamp;
}
