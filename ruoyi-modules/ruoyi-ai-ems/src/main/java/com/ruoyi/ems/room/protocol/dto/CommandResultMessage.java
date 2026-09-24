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
public class CommandResultMessage {
    @Builder.Default private String type = "CMD_RESULT";
    private String attemptId;
    private String decision;                 // Decision 枚举 name
    private String primaryRuleId;
    @Builder.Default private List<String> allMatchedRuleIds = new ArrayList<>();
    private String severity;                 // none / low / medium / high
    @Builder.Default private List<String> feedbackKeys = new ArrayList<>();
    @Builder.Default private List<String> assessmentTags = new ArrayList<>();
    private long stateRevision;
    private long eventSequence;
    private long serverSequence;
    private SnapshotMessage snapshot;        // 可选
}
