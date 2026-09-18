package com.ruoyi.acls.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AckManager {

    private final Map<String, PendingAck> pendingAcks = new ConcurrentHashMap<>();

    /**
     * 等待确认（创建待确认任务并返回 CommandResult）
     *
     * @param sessionId 会话ID
     * @param prompt 确认提示文本
     * @param timeoutMillis 超时时间（毫秒）
     * @return CommandResult 表示等待确认状态
     */
    public CommandResult waitForAck(String sessionId, String prompt, long timeoutMillis) {
        if (pendingAcks.containsKey(sessionId)) {
            log.warn("⏳ 会话 {} 已有待确认任务，覆盖旧任务", sessionId);
        }
        PendingAck pending = new PendingAck(prompt, timeoutMillis, System.currentTimeMillis());
        pendingAcks.put(sessionId, pending);
        log.debug("⏳ [ACK] 创建待确认任务: sessionId={}, prompt={}, timeout={}ms",
            sessionId, prompt, timeoutMillis);

        // ★ 返回等待确认的 CommandResult
        return CommandResult.pending(prompt);
    }

    /**
     * 确认匹配
     */
    public boolean acknowledge(String sessionId, Map<String, Object> slots) {
        PendingAck pending = pendingAcks.get(sessionId);
        if (pending == null) {
            log.warn("⚠️ [ACK] 会话 {} 没有待确认任务", sessionId);
            return false;
        }

        long elapsed = System.currentTimeMillis() - pending.getCreateTime();
        if (elapsed > pending.getTimeoutMillis()) {
            pendingAcks.remove(sessionId);
            log.warn("⏰ [ACK] 会话 {} 待确认任务已超时", sessionId);
            return false;
        }

        pendingAcks.remove(sessionId);
        log.info("✅ [ACK] 会话 {} 确认成功", sessionId);
        return true;
    }

    public boolean hasPendingAck(String sessionId) {
        return pendingAcks.containsKey(sessionId);
    }

    public void cancelPendingAck(String sessionId) {
        pendingAcks.remove(sessionId);
    }

    public PendingAck getPendingAck(String sessionId) {
        return pendingAcks.get(sessionId);
    }

    /**
     * 清理超时任务
     */
    public void cleanTimeoutTasks() {
        long now = System.currentTimeMillis();
        pendingAcks.entrySet().removeIf(entry -> {
            PendingAck pending = entry.getValue();
            return now - pending.getCreateTime() > pending.getTimeoutMillis();
        });
    }

    // ==================== 内部类 ====================

    public static class PendingAck {
        private final String prompt;
        private final long timeoutMillis;
        private final long createTime;

        public PendingAck(String prompt, long timeoutMillis, long createTime) {
            this.prompt = prompt;
            this.timeoutMillis = timeoutMillis;
            this.createTime = createTime;
        }

        public String getPrompt() { return prompt; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public long getCreateTime() { return createTime; }
    }
}
