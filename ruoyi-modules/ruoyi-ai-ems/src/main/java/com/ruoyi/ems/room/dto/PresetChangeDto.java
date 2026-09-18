package com.ruoyi.ems.room.dto;


import lombok.Data;

@Data
public class PresetChangeDto {
    private String triggerType;
    private Float atSeconds;
    private String expression;
    private String event;
    private String targetVariable;
    private String pulseState;
    private Object value;
    private Float transitionSeconds;
    private Integer priority;
    private Boolean once;
}
