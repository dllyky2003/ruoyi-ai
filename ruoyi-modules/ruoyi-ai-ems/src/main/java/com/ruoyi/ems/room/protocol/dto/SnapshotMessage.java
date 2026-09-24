package com.ruoyi.ems.room.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SnapshotMessage {
    private String protocolVersion;
    private String attemptId;
    private long stateRevision;
    private long eventSequence;
    private long serverSequence;
    @JsonProperty("isFullSnapshot")
    private boolean isFullSnapshot;
    private String scenarioId;
    private String scenarioVersion;
    private String ruleBundleVersion;
    private String lessonPlanBundleVersion;
    private ClinicalState clinicalState;
    private ProtocolState protocolState;
    private MonitorPresentation monitorPresentation;
    private SystemStatus systemStatus;
    @Builder.Default
    private List<SnapshotEvent> events = new ArrayList<>();
}
