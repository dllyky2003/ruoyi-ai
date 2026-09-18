package com.ruoyi.acls.engine;


import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.npc.IVrClientService;
import com.ruoyi.ems.npc.TeamManager;

import com.ruoyi.ems.tts.KokoroTtsClient;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
@Setter
@Getter
public class CommandContext {

    private final String sessionId;
    private final TeamManager teamManager;
    private final CaseContext caseContext;
    private final KokoroTtsClient ttsService;
    private final IVrClientService vrClientService;
    private final AckManager ackManager;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private String currentCommandId;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private String actType;
    private Map<String, Object> slots;  // 存储意图槽位参数
    private Double confidence;



    public CommandContext(String sessionId, TeamManager teamManager, CaseContext caseContext,
                          KokoroTtsClient ttsService, IVrClientService vrClientService,
                          AckManager ackManager) {
        this.sessionId = sessionId;
        this.teamManager = teamManager;
        this.caseContext = caseContext;
        this.ttsService = ttsService;
        this.vrClientService = vrClientService;
        this.ackManager = ackManager;
    }

    // cancel, isCancelled, setCurrentCommandId, getCurrentCommandId, setVariable, getVariable 等方法与之前相同...
    public void cancel(String reason) { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
    public void setCurrentCommandId(String id) { this.currentCommandId = id; }
    public String getCurrentCommandId() { return currentCommandId; }

    public void setVariable(String key, Object value) { variables.put(key, value); }
    public Object getVariable(String key) { return variables.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) return null;
        if (type.isAssignableFrom(value.getClass())) return (T) value;
        return null;
    }
}
