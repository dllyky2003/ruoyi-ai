package com.ruoyi.acls.tutor.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 学员与团队行为状态。
 */
@Data
public class LearnerState {
    private String activeRole;          // team_leader
    private List<String> issuedCommands = new ArrayList<>();
    private List<String> delegatedTasks = new ArrayList<>();
    private List<String> confirmations = new ArrayList<>();
    private List<String> pendingClarifications = new ArrayList<>();
    private String authorityProfile;    // ems_adult_als_v1
}
