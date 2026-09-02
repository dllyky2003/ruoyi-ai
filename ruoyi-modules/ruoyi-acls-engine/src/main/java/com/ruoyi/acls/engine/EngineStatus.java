package com.ruoyi.acls.engine;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 引擎运行时状态
 */
@Data
@Builder
public class EngineStatus {
    private String sessionId;
    private String engineMode;
    private boolean cognitiveEnabled;
    private int activeCommandCount;
    private int pendingThinkTasks;
    private Map<String, Object> memberStates;
}
