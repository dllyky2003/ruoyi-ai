package com.ruoyi.acls.contract.diagnose;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * 拒绝事件环形缓冲。容量固定，不落盘。
 * 目的：给诊断器提供"最近发生了什么"。
 */
@Slf4j
@Component
public class RejectionRecorder {

    private static final int MAX_SIZE = 200;
    private final Deque<RejectionEvent> events = new ConcurrentLinkedDeque<>();

    @Data
    public static class RejectionEvent {
        private long timestamp;
        private String caseId;
        private String sessionId;
        private String stateId;
        private String intentId;
        private String taskId;
    }

    /** ★ 就是这个 5 参数方法——之前报"无法解析"是因为文件还没建 */
    public void record(String caseId, String sessionId, String stateId,
                       String intentId, String taskId) {
        RejectionEvent e = new RejectionEvent();
        e.setTimestamp(System.currentTimeMillis());
        e.setCaseId(caseId);
        e.setSessionId(sessionId);
        e.setStateId(stateId);
        e.setIntentId(intentId);
        e.setTaskId(taskId);
        events.addFirst(e);
        while (events.size() > MAX_SIZE) events.removeLast();

        log.info("📝 [RejectionRecorder] case={} state={} intent={} task={}",
            caseId, stateId, intentId, taskId);
    }

    public RejectionEvent latest(String caseId) {
        for (RejectionEvent e : events) {
            if (caseId == null || caseId.equals(e.getCaseId())) return e;
        }
        return null;
    }

    public List<RejectionEvent> recent(String caseId, int limit) {
        return events.stream()
            .filter(e -> caseId == null || caseId.equals(e.getCaseId()))
            .limit(limit)
            .collect(Collectors.toList());
    }
}
