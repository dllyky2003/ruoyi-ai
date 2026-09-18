package com.ruoyi.ems.asr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class AsrTranscriptionService {

    @Value("${funasr.ws.host:127.0.0.1}")
    private String host;

    @Value("${funasr.ws.port:10095}")
    private int port;

    @Value("${funasr.ws.mode:2pass}")
    private String mode;

    @Value("${funasr.ws.chunk-size:5,10,5}")
    private String chunkSize;

    @Value("${funasr.ws.sample-rate:16000}")
    private int sampleRate;

    @Value("${funasr.ws.itn:true}")
    private boolean itn;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, FunAsrSession> sessions = new ConcurrentHashMap<>();

    /**
     * 启动流式识别会话。
     * @param sessionId 会话 ID
     * @param onSentence 每句话结束时触发的回调，参数是完整句子文本
     */
    public void startStream(String sessionId, Consumer<String> onSentence) {
        log.info("Starting FunASR stream for session: {}", sessionId);
        try {
            FunAsrSession s = new FunAsrSession(sessionId, onSentence);
            if (!s.connectBlocking(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new RuntimeException("FunASR 连接超时");
            }
            sessions.put(sessionId, s);
        } catch (Exception e) {
            log.error("Failed to start FunASR for session {}", sessionId, e);
            throw new RuntimeException("FunASR initialization failed", e);
        }
    }

    public void appendAudio(String sessionId, byte[] audio) {
        FunAsrSession s = sessions.get(sessionId);
        if (s != null) s.sendAudio(audio);
    }

    public void endStream(String sessionId) {
        FunAsrSession s = sessions.remove(sessionId);
        if (s != null) s.close();
    }

    public class FunAsrSession extends WebSocketClient {
        private final String sessionId;
        private final Consumer<String> onSentence;

        FunAsrSession(String sessionId, Consumer<String> onSentence) throws Exception {
            super(new URI("ws://" + host + ":" + port));
            this.sessionId = sessionId;
            this.onSentence = onSentence;
        }

        @Override
        public void onOpen(ServerHandshake h) {
            String handshake = String.format(
                "{\"mode\":\"%s\",\"chunk_size\":\"%s\",\"chunk_interval\":10,"
                    + "\"audio_fs\":%d,\"wav_name\":\"%s\",\"wav_format\":\"pcm\","
                    + "\"is_speaking\":true,\"itn\":%b}",
                mode, chunkSize, sampleRate, sessionId, itn);
            send(handshake);
            log.info("FunASR handshake sent for session: {}", sessionId);
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonNode node = mapper.readTree(message);
                String text = node.path("text").asText("");
                String msgMode = node.path("mode").asText("");
                boolean isFinal = node.path("is_final").asBoolean(false);

                if (text.isEmpty()) return;

                // 只处理最终句，忽略 online 预览
                if ("2pass-offline".equals(msgMode) || isFinal) {
                    log.info("FunASR sentence for session {}: {}", sessionId, text);
                    onSentence.accept(text);   // 立即触发业务回调
                }
            } catch (Exception e) {
                log.error("Parse FunASR message failed: {}", message, e);
            }
        }

        @Override public void onClose(int code, String reason, boolean remote) {
            log.info("FunASR closed for session {}: {} {}", sessionId, code, reason);
        }
        @Override public void onError(Exception ex) {
            log.error("FunASR error for session: {}", sessionId, ex);
        }

        void sendAudio(byte[] audio) {
            if (isOpen()) send(audio);
        }
    }
}
