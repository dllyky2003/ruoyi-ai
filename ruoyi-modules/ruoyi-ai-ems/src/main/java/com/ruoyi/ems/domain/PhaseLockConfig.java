package com.ruoyi.ems.domain;


import lombok.Data;

import java.util.List;

@Data
public class PhaseLockConfig {
    private Boolean enabled = false;
    private Long durationMs;
    private List<String> releaseIntents;
    private String releaseCondition;        // SpEL
}
