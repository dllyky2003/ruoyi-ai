package com.ruoyi.ems.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sound.sampled.*;
import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class KokoroTtsClient {

    @Value("${kokoro.api.url:http://localhost:8880/v1/audio/speech}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ★ 管理每个会话的播放控制器，用于停止播放
    private final Map<String, PlaybackController> playbackControllers = new ConcurrentHashMap<>();

    /**
     * 服务不可用时的日志节流：距上次"服务不可用"日志的时间戳。
     * 避免 TTS 服务没起时，每次播报都打一行日志刷屏。
     */
    private final AtomicLong lastUnavailableLogAt = new AtomicLong(0L);

    /** 服务不可用日志的最小间隔（毫秒） */
    private static final long UNAVAILABLE_LOG_INTERVAL_MS = 30_000L;

    // ==================== 公开播放方法 ====================

    public void speak(String text) {
        speak(text, "zf_094", 1.2f, null);
    }

    public void speak(String text, String voice, float speed) {
        speak(text, voice, speed, null);
    }

    public void speak(String text, String voice, float speed, String sessionId) {
        try {
            speakStream(text, voice, speed, sessionId);
        } catch (TtsUnavailableException e) {
            // ★ TTS 服务未启动 / 不可达 —— 只提示，不当故障
            logUnavailable(e);
        } catch (Exception e) {
            // 真正的异常（解析错误、播放失败等）才打堆栈
            log.error("TTS 流式合成或播放失败, sessionId={}", sessionId, e);
        }
    }

    @Async
    public void speakAsync(String text) {
        speak(text);
    }

    @Async
    public void speakAsync(String text, String voice, float speed) {
        speak(text, voice, speed);
    }

    @Async
    public void synthesizeAndSend(String sessionId, String content, String voice, float speed) {
        speak(content, voice, speed, sessionId);
    }

    public void stop(String sessionId) {
        PlaybackController controller = playbackControllers.remove(sessionId);
        if (controller != null) {
            controller.stop();
            log.info("🛑 TTS 播放已停止: sessionId={}", sessionId);
        } else {
            log.debug("没有正在播放的 TTS 任务: sessionId={}", sessionId);
        }
    }

    // ==================== 流式核心实现 ====================

    private void speakStream(String text, String voice, float speed, String sessionId) throws Exception {
        URL url = new URL(apiUrl);

        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            // 到这一步基本是 URL 非法，但也一并归为不可用
            throw new TtsUnavailableException("无法建立 TTS 连接: " + e.getMessage(), e);
        }

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "audio/wav");
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(8192);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(10000);

        String jsonBody = String.format(
            "{\"input\":\"%s\",\"voice\":\"%s\",\"speed\":%.2f,\"response_format\":\"wav\",\"stream\":true}",
            escapeJson(text), voice, speed
        );

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            // 写请求体时连不上，按服务不可用处理
            throw new TtsUnavailableException("写入 TTS 请求失败: " + e.getMessage(), e);
        }

        int responseCode;
        try {
            responseCode = conn.getResponseCode();
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            throw new TtsUnavailableException("TTS 服务不可达: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TtsUnavailableException("读取 TTS 响应失败: " + e.getMessage(), e);
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            // 非 200 也算服务不可用（如 502/503/404）
            throw new TtsUnavailableException("TTS 服务返回非 200: " + responseCode, null);
        }

        try (InputStream inputStream = conn.getInputStream()) {
            // 1. 读取 WAV 头部（44 字节）
            byte[] header = new byte[44];
            int bytesRead = 0;
            while (bytesRead < 44) {
                int n = inputStream.read(header, bytesRead, 44 - bytesRead);
                if (n == -1) break;
                bytesRead += n;
            }
            if (bytesRead < 44) {
                // 服务返回了 200 但数据不完整 —— 视为服务端问题，仍按不可用处理
                throw new TtsUnavailableException("TTS 服务未返回完整 WAV 头", null);
            }

            AudioFormat format = parseWavHeader(header);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            PlaybackController controller = null;
            if (sessionId != null) {
                controller = new PlaybackController(line, Thread.currentThread());
                playbackControllers.put(sessionId, controller);
            }

            try {
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                byte pending = 0;
                boolean hasPending = false;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (controller != null && controller.isStopped()) {
                        log.info("播放被中断: sessionId={}", sessionId);
                        break;
                    }

                    int offset = 0;
                    int remaining = bytesRead;

                    if (hasPending) {
                        byte[] two = new byte[2];
                        two[0] = pending;
                        two[1] = buffer[offset];
                        line.write(two, 0, 2);
                        totalRead += 2;
                        offset = 1;
                        remaining = bytesRead - 1;
                        hasPending = false;
                    }

                    while (remaining >= 2) {
                        if (controller != null && controller.isStopped()) {
                            break;
                        }
                        line.write(buffer, offset, 2);
                        totalRead += 2;
                        offset += 2;
                        remaining -= 2;
                    }

                    if (remaining == 1) {
                        pending = buffer[offset];
                        hasPending = true;
                    }
                }

                line.drain();
                line.close();
            } finally {
                if (sessionId != null) {
                    playbackControllers.remove(sessionId);
                }
                if (controller != null) {
                    controller.close();
                }
            }
        }
    }

    // ==================== 非流式合成（保留） ====================

    /**
     * 非流式合成，返回完整 WAV 字节数组。
     * 调用方需要字节数组，因此失败时抛出异常，由调用方决定如何处理；
     * 这里不再打印堆栈。
     */
    public byte[] synthesizeToWav(String text, String voice, float speed) {
        Map<String, Object> request = new HashMap<>();
        request.put("input", text);
        request.put("voice", voice);
        request.put("speed", speed);
        request.put("response_format", "wav");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.valueOf("audio/wav")));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            byte[] responseBytes = restTemplate.execute(
                apiUrl,
                org.springframework.http.HttpMethod.POST,
                restTemplate.httpEntityCallback(entity),
                clientHttpResponse -> clientHttpResponse.getBody().readAllBytes()
            );

            if (responseBytes == null || responseBytes.length == 0) {
                throw new RuntimeException("API 返回空音频数据");
            }
            return responseBytes;

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 连接类问题 —— 统一包装为 TtsUnavailableException，让调用方能区分
            throw new TtsUnavailableException("TTS 服务不可达: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("TTS 合成失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 服务不可用日志节流：短时间内多次不可用只打一行 info。
     */
    private void logUnavailable(TtsUnavailableException e) {
        long now = System.currentTimeMillis();
        long last = lastUnavailableLogAt.get();
        if (now - last >= UNAVAILABLE_LOG_INTERVAL_MS
            && lastUnavailableLogAt.compareAndSet(last, now)) {
            log.info("ℹ️ TTS 服务当前不可用，已跳过语音播报: {} (地址: {})",
                e.getMessage(), apiUrl);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private AudioFormat parseWavHeader(byte[] header) {
        int channels = (header[22] & 0xFF) | ((header[23] & 0xFF) << 8);
        int sampleRate = (header[24] & 0xFF) | ((header[25] & 0xFF) << 8)
            | ((header[26] & 0xFF) << 16) | ((header[27] & 0xFF) << 24);
        int bitsPerSample = (header[34] & 0xFF) | ((header[35] & 0xFF) << 8);
        int audioFormat = (header[20] & 0xFF) | ((header[21] & 0xFF) << 8);
        if (audioFormat != 1) {
            throw new RuntimeException("仅支持 PCM WAV，当前编码: " + audioFormat);
        }
        return new AudioFormat(sampleRate, bitsPerSample, channels, true, false);
    }

    public String getVoices() {
        String voicesUrl = apiUrl.replace("/v1/audio/speech", "/v1/voices");
        return restTemplate.getForObject(voicesUrl, String.class);
    }

    public void speak(String sessionId, String text) {
        speak(text, "zf_094", 1.2f, sessionId);
    }

    // ==================== 内部类 ====================

    /** TTS 服务不可用（连接被拒/超时/非 200），与业务异常区分开 */
    private static class TtsUnavailableException extends RuntimeException {
        public TtsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class PlaybackController {
        private final SourceDataLine line;
        private final Thread playbackThread;
        private volatile boolean stopped = false;

        public PlaybackController(SourceDataLine line, Thread thread) {
            this.line = line;
            this.playbackThread = thread;
        }

        public void stop() {
            stopped = true;
            if (line != null) {
                line.stop();
                line.close();
            }
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
        }

        public boolean isStopped() {
            return stopped;
        }

        public void close() {
            if (line != null && line.isOpen()) {
                line.close();
            }
        }
    }
}
