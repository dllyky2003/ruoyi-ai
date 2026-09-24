package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorPresentation {
    private String rhythm;
    private Float hrDisplay;           // null-able，对齐 C# float?
    private Float bpDisplay;
    private Float spo2Display;
    @Builder.Default
    private List<String> trendLabels = new ArrayList<>();
}
