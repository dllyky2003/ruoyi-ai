package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StatePhysiology {
    @JsonProperty("phys.heartRate")
    private int heartRate;
    @JsonProperty("phys.systolicBp")
    private int systolicBp;
    @JsonProperty("phys.diastolicBp")
    private int diastolicBp;
    @JsonProperty("phys.spo2")
    private int spo2;
    @JsonProperty("phys.respiratoryRate")
    private int respiratoryRate;
    @JsonProperty("phys.consciousness")
    private String consciousness;

    // ★ 新增缺失字段（根据您的变量定义）
    @JsonProperty("phys.qrsDuration")
    private Double qrsDuration;          // 注意用 Double，因为可能有小数
    @JsonProperty("phys.rrIntervalJitter")
    private Double rrIntervalJitter;
    @JsonProperty("phys.rhythm")
    private String rhythm;
    // 如有其他字段，如 meanArterialPressure, etco2 等，也一并添加

    @JsonProperty("transition_duration")
    private Integer transitionDuration;
}
