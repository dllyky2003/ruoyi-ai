package com.ruoyi.ems.orchestrator.model;

import lombok.Data;

@Data
public class FatigueRotationConfig {
    private Integer everyCycles;                // 每 N 个循环轮换一次
    private String rotateDuty;                  // 轮换的职责，如 "CPR"
}
