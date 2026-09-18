package com.ruoyi.ems.room.token;

import com.ruoyi.ems.room.model.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化的 WebSocket 连接 token 服务。
 * 生产环境应替换为 JWT + Redis 或 Spring Security。
 */
@Slf4j
@Service
public class WsTokenService {

    private static final long TTL_MS = 5 * 60 * 1000L;   // 5 分钟

    private final SecureRandom random = new SecureRandom();
    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    public String issue(String sessionId, Role role, String userId) {
        String token = generateToken();
        tokens.put(token, new TokenEntry(sessionId, role, userId,
            System.currentTimeMillis() + TTL_MS));
        return token;
    }

    public TokenEntry validate(String token, String sessionId, Role role) {
        if (token == null) return null;
        TokenEntry e = tokens.get(token);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAt) {
            tokens.remove(token);
            return null;
        }
        if (!e.sessionId.equals(sessionId)) return null;
        if (e.role != role) return null;
        return e;
    }

    /** 一次性消费 */
    public TokenEntry consume(String token, String sessionId, Role role) {
        TokenEntry e = validate(token, sessionId, role);
        if (e != null) tokens.remove(token);
        return e;
    }

    public void revoke(String token) {
        tokens.remove(token);
    }

    private String generateToken() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record TokenEntry(String sessionId, Role role, String userId, long expiresAt) {}
}
