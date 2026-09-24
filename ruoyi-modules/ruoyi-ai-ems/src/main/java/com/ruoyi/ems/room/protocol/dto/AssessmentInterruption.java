package com.ruoyi.ems.room.protocol.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AssessmentInterruption {
    private String interruptionId;
    private String sourceEventId;
    private String overrideId;
    private long occurredAtRevision;
    @Builder.Default private List<String> affectedCriteria = new ArrayList<>();
    private long affectedEvidenceFromRevision;
    private String affectedEvidenceUntil;    // "criterion_completion_or_attempt_end" 或显式 revision
    private String resultPolicy;             // "not_assessable"
    @Builder.Default private boolean learnerPenalty = false;
}
