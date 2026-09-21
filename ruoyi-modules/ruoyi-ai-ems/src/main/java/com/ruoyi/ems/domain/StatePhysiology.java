package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 单个 Unity stateName 对应的体征基准。
 *
 * <p>YAML 里字段可能带 {@code phys.} 前缀（从 initialVitals 拷贝），
 * 通过 {@code @JsonAlias} 兼容带前缀的写法。
 *
 * <p>未识别字段（如 {@code phys.rrIntervalJitter}）直接忽略。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatePhysiology {

    // ===== 生命体征 =====
    @JsonAlias({"phys.heartRate"})
    private Float heartRate;

    @JsonAlias({"phys.systolicBp"})
    private Float systolicBp;

    @JsonAlias({"phys.diastolicBp"})
    private Float diastolicBp;

    @JsonAlias({"phys.spo2"})
    private Float spo2;

    @JsonAlias({"phys.etco2"})
    private Float etco2;

    @JsonAlias({"phys.respiratoryRate", "phys.respRate", "respRate"})
    private Float respiratoryRate;

    @JsonAlias({"phys.consciousness"})
    private String consciousness;

    /** 心律（可选，供状态机或 sendUnityStateChange 直接读取） */
    @JsonAlias({"phys.rhythm"})
    private String rhythm;

    // ===== ECG 参数 =====
    private String arrhythmiaType;

    private Float prInterval;

    @JsonAlias({"phys.qrsDuration"})
    private Float qrsDuration;

    private Float qtInterval;
    private Float stElevation;
    private Float stDepression;

    // ===== 血压波形参数 =====
    private Float bpRiseTime;
    private Float bpFallTime;
    private Float dicroticNotchDepth;

    // ===== 过渡控制 =====
    @JsonAlias({"transition_duration"})
    private Integer transitionDuration;
}
