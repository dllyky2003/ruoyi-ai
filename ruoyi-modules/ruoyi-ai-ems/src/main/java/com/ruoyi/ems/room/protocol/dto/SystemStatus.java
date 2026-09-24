package com.ruoyi.ems.room.protocol.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SystemStatus {
    private boolean paused;
    private boolean deterministicDemoUsed;
    private boolean assessmentInterruptionActive;
}
