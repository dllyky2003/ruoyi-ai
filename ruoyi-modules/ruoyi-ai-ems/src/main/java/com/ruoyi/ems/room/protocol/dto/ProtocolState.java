package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolState {
    private String currentAlgorithm;   // adult_tachycardia
    private Integer cprCycleIndex;     // null-able，对齐 C# int?
    private boolean rhythmCheckDue;
}
