package com.ruoyi.ems.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationEffect {
    private String target;
    private Object delta;          // 可同时接收 int 和 String
    private int duration;
    private int recovery;
    private String curve;
}
