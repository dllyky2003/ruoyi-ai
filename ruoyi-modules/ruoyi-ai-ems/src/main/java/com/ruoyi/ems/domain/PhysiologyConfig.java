package com.ruoyi.ems.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
public class PhysiologyConfig {
    private int defaultTransitionDuration;
    private Map<String, StatePhysiology> statePhysiology;
    private Map<String, List<OperationEffect>> operationEffects;
}
