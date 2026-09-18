package com.ruoyi.ems.asr.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.asr.AsrUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * ASR客户端：连接 Python ASR 网关（asr_gateway.py）。
 * Python 侧已完成 partial 过滤 + 去重 + 合并，Java 只接收 final 句子。
 *
 * <p>连接失败时抛出 {@link AsrUnavailableException}，让上层能区分"外部依赖不可用"与"代码 bug"。
 */
@Slf4j
@Component
public class AsrClient {

    @Value("${funasr.gateway.host:127.0.0.1}")
    private String host;

    @Value("${funasr.gateway.port:8002}")
    private int port;

    @Value("${funasr.gateway.path:/ws/asr}")
    private String path;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, GatewaySession> sessions = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, Boolean>> callbacks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pauseStates = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> sessionTranscripts = new ConcurrentHashMap<>();

    /** 不可用日志节流：距上次打印"网关不可用"的时间戳，避免刷屏 */
    private final AtomicLong lastUnavailableLogAt = new AtomicLong(0L);
    private static final long UNAVAILABLE_LOG_INTERVAL_MS = 30_000L;

    // ==================== 会话生命周期 ====================

    public String startRealtimeRecognition(String sessionId) {
        log.info("启动 ASR 网关会话: {}", sessionId);
        closeSession(sessionId);

        pauseStates.put(sessionId, false);
        sessionTranscripts.put(sessionId, new StringBuilder());

        GatewaySession session;
        try {
            session = new GatewaySession(sessionId);
        } catch (Exception e) {
            // URI 构造异常，属于配置/代码问题
            log.error("构造 ASR 网关连接失败: sessionId={}, url={}",
                sessionId, buildWsUrl(), e);
            throw new RuntimeException("ASR 网关地址非法: " + buildWsUrl(), e);
        }

        boolean connected;
        try {
            connected = session.connectBlocking(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ASR 网关连接被中断: " + sessionId, e);
        } catch (Exception e) {
            // 连接层异常（ConnectException / SocketTimeout / UnknownHost 等）
            throw new AsrUnavailableException(
                "ASR 网关连接失败: " + e.getMessage(), e);
        }

        if (!connected) {
            // 原先是 log.error + RuntimeException，改成 AsrUnavailableException
            throw new AsrUnavailableException("ASR 网关连接超时: " + sessionId);
        }

        sessions.put(sessionId, session);
        log.info("ASR 网关会话已建立: sessionId={}", sessionId);

        return "asr-task-" + sessionId;
    }

    /**
     * 判断某个 sessionId 是否已有活跃的 ASR 连接。
     */
    public boolean isSessionActive(String sessionId) {
        GatewaySession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public void sendAudio(String taskId, byte[] audioData) {
        String sessionId = taskId.replace("asr-task-", "");
        if (Boolean.TRUE.equals(pauseStates.get(sessionId))) {
            log.debug("ASR会话已暂停，丢弃音频: {}", sessionId);
            return;
        }
        GatewaySession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("ASR 网关会话不存在，无法发送音频: {}", sessionId);
            return;
        }
        session.sendAudio(audioData);
    }

    public void registerCallback(String sessionId, BiConsumer<String, Boolean> callback) {
        callbacks.put(sessionId, callback);
        log.info("注册回调: sessionId={}", sessionId);
    }

    public void unregisterCallback(String sessionId) {
        callbacks.remove(sessionId);
        log.info("取消回调: sessionId={}", sessionId);
    }

    public void pause(String sessionId) {
        pauseStates.put(sessionId, true);
        log.info("暂停ASR识别: {}", sessionId);
    }

    public void resume(String sessionId) {
        pauseStates.put(sessionId, false);
        log.info("恢复ASR识别: {}", sessionId);
    }

    public void reset(String sessionId) {
        sessionTranscripts.put(sessionId, new StringBuilder());
        log.info("重置ASR识别: {}", sessionId);
    }

    public String getFullTranscript(String sessionId) {
        StringBuilder sb = sessionTranscripts.get(sessionId);
        return sb != null ? sb.toString() : "";
    }

    public void stopRealtimeRecognition(String taskId) {
        String sessionId = taskId.replace("asr-task-", "");
        log.info("停止 ASR 网关会话: {}", sessionId);
        closeSession(sessionId);
        pauseStates.remove(sessionId);
        sessionTranscripts.remove(sessionId);
    }

    private void closeSession(String sessionId) {
        GatewaySession session = sessions.remove(sessionId);
        if (session != null) {
            session.finishAndClose();
        }
    }

    // ==================== 内部：URL 拼装 ====================

    private String buildWsUrl() {
        return "ws://" + host + ":" + port + path;
    }

    // ==================== 内部：不可用日志节流 ====================

    private void logUnavailableOnce(String msg) {
        long now = System.currentTimeMillis();
        long last = lastUnavailableLogAt.get();
        if (now - last >= UNAVAILABLE_LOG_INTERVAL_MS
            && lastUnavailableLogAt.compareAndSet(last, now)) {
            log.info("ℹ️ ASR 网关当前不可用: {} (地址: {})", msg, buildWsUrl());
        } else {
            log.debug("ASR 网关不可用(已节流): {}", msg);
        }
    }

    private static boolean isConnectionFailure(Throwable ex) {
        return ex instanceof ConnectException
            || ex instanceof SocketTimeoutException
            || ex instanceof UnknownHostException
            || ex instanceof ClosedChannelException;
    }

    private static boolean isConnectionCloseCode(int code) {
        // -1: java-websocket 在 connect 失败时给的状态码
        // 1006: 异常关闭（没有 close frame）
        return code == -1 || code == 1006;
    }

    // ==================== 内部：单个会话的网关连接 ====================

    private class GatewaySession extends WebSocketClient {
        private final String sessionId;
        private volatile boolean finished = false;

        GatewaySession(String sessionId) throws Exception {
            super(new URI(buildWsUrl()));
            this.sessionId = sessionId;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // Python 网关不需要握手，直接可以发音频
            log.info("ASR 网关连接已建立: sessionId={}", sessionId);
        }

        @Override
        public void onMessage(String message) {
            log.debug("ASR 网关消息 [{}]: {}", sessionId, message);
            try {
                JsonNode node = mapper.readTree(message);

                // Python 网关只会推 {"type":"sentence","text":"..."}
                String type = node.path("type").asText("");
                if (!"sentence".equals(type)) {
                    return;
                }

                String text = node.path("text").asText("");
                if (text.isEmpty()) return;

                // 累积文本
                StringBuilder sb = sessionTranscripts.get(sessionId);
                if (sb != null) {
                    sb.append(text).append(" ");
                }

                // 触发回调：Python 保证这是 final
                BiConsumer<String, Boolean> callback = callbacks.get(sessionId);
                if (callback != null) {
                    callback.accept(text, true);
                }
            } catch (Exception e) {
                log.error("解析 ASR 网关消息失败: sessionId={}, message={}", sessionId, message, e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if (isConnectionCloseCode(code)) {
                // ★ 连接从未建立成功 / 异常断开 —— 走节流 INFO
                logUnavailableOnce("ASR 网关连接关闭: code=" + code + ", reason=" + reason);
            } else {
                log.info("ASR 网关连接关闭: sessionId={}, code={}, reason={}",
                    sessionId, code, reason);
            }
        }

        @Override
        public void onError(Exception ex) {
            if (isConnectionFailure(ex)) {
                // ★ 连接被拒 / 超时 / 未知主机 —— 走节流 INFO，不打堆栈
                logUnavailableOnce("ASR 网关连接错误: " + ex.getMessage());
            } else {
                // 真正的异常才打 ERROR
                log.error("ASR 网关异常: sessionId={}", sessionId, ex);
            }
        }

        void sendAudio(byte[] audioData) {
            if (isOpen()) {
                send(audioData);
            } else {
                log.warn("ASR 网关连接已关闭，无法发送音频: sessionId={}", sessionId);
            }
        }

        void finishAndClose() {
            if (finished) return;
            finished = true;
            try {
                if (isOpen()) {
                    send("END");            // Python 网关的结束信号
                    Thread.sleep(500);      // 等最后一句 final
                }
            } catch (Exception e) {
                log.warn("发送结束信号失败: sessionId={}", sessionId, e);
            } finally {
                try {
                    close();
                } catch (Exception e) {
                    log.warn("关闭连接失败: sessionId={}", sessionId, e);
                }
            }
        }
    }
}
