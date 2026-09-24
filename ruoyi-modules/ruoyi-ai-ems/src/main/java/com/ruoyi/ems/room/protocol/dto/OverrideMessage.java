package com.ruoyi.ems.room.protocol.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OverrideMessage {
    @Builder.Default private String type = "OVR";
    private String attemptId;
    private String overrideId;               // IO_PAUSE_CLOCK / IO_FORCE_SYNC_MODE
    private String reason;
    private long expectedStateRevision;
    private ProposedEffect proposedEffect;
    @Builder.Default private boolean auditRequired = true;
    private boolean triggersAssessmentInterruption;
    private AssessmentInterruption assessmentInterruption;
}


