package com.ruoyi.ems.room.ws;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 物理连接注册表。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>维护 roomSessionId -> (clientId -> Session) 的两级映射</li>
 *   <li>维护 emsSessionId -> roomSessionId 的别名映射（解决 L-11 推送通道问题）</li>
 *   <li>所有对外查询接口统一走 {@link #resolve(String)} 解析</li>
 * </ul>
 */
@Slf4j
@Component
public class WsSessionRegistry {

    /** 主映射表：roomSessionId -> (clientId -> Session) */
    private final Map<String, Map<String, Session>> sessions = new ConcurrentHashMap<>();

    /** 别名表：emsSessionId -> roomSessionId */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    // ==================== 注册 / 注销 ====================

    public void register(String sessionId, String clientId, Session session) {
        if (sessionId == null || clientId == null || session == null) {
            throw new IllegalArgumentException(
                "register 参数不能为 null: sessionId=" + sessionId
                    + ", clientId=" + clientId + ", session=" + session);
        }
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
            .put(clientId, session);
        log.debug("WS 注册: sessionId={}, clientId={}, 当前连接数={}",
            sessionId, clientId, sessions.get(sessionId).size());
    }

    public void unregister(String sessionId, String clientId) {
        if (sessionId == null || clientId == null) return;

        String resolved = resolve(sessionId);
        Map<String, Session> clients = sessions.get(resolved);
        if (clients == null) return;

        clients.remove(clientId);

        if (clients.isEmpty()) {
            sessions.remove(resolved);
            removeAliasesByValue(resolved);
            log.debug("WS 房间已无连接，清理: resolvedSessionId={}", resolved);
        }
        log.debug("WS 注销: sessionId={}, clientId={}", sessionId, clientId);
    }

    // ==================== 别名管理 ====================

    public void registerAlias(String aliasKey, String primaryKey) {
        if (aliasKey == null || primaryKey == null) return;
        if (aliasKey.equals(primaryKey)) return;

        aliases.put(aliasKey, primaryKey);
        log.info("WS 别名注册: {} -> {}", aliasKey, primaryKey);
    }

    public void unregisterAlias(String aliasKey) {
        if (aliasKey == null) return;

        String removed = aliases.remove(aliasKey);
        if (removed != null) {
            log.info("WS 别名注销: {} (原指向 {})", aliasKey, removed);
        }
    }

    // ==================== 查询接口 ====================

    public Session get(String sessionId, String clientId) {
        String resolved = resolve(sessionId);
        if (resolved == null) return null;
        Map<String, Session> clients = sessions.get(resolved);
        return clients != null ? clients.get(clientId) : null;
    }

    public Map<String, Session> getAll(String sessionId) {
        String resolved = resolve(sessionId);
        if (resolved == null) return Collections.emptyMap();
        Map<String, Session> clients = sessions.get(resolved);
        return clients != null ? Map.copyOf(clients) : Collections.emptyMap();
    }

    public Set<String> getClientIds(String sessionId) {
        String resolved = resolve(sessionId);
        if (resolved == null) return Collections.emptySet();
        Map<String, Session> clients = sessions.get(resolved);
        return clients != null ? Set.copyOf(clients.keySet()) : Collections.emptySet();
    }

    public int count(String sessionId) {
        String resolved = resolve(sessionId);
        if (resolved == null) return 0;
        Map<String, Session> clients = sessions.get(resolved);
        return clients != null ? clients.size() : 0;
    }

    public boolean isEmpty(String sessionId) {
        return count(sessionId) == 0;
    }

    // ==================== ★ 对外统一解析入口 ====================

    /**
     * 把任意 key（emsSessionId 或 roomSessionId）解析为 roomSessionId。
     *
     * <p>供 {@code WebSocketMessageSender} 等下行组件使用。相比走
     * {@code RoomRegistry.getByEmsSessionId}，本方法直接命中本类的别名表——
     * 别名在 start_case 时通过 {@link #registerAlias} 写入，实时且可靠。</p>
     *
     * @param sessionId 任意已知 key
     * @return 解析后的 roomSessionId；既不是主表 key 也不是已知别名时返回 null
     */
    public String resolveToRoomSessionId(String sessionId) {
        if (sessionId == null) return null;
        // 优先检查主表
        if (sessions.containsKey(sessionId)) {
            return sessionId;
        }
        // 检查别名表
        return aliases.get(sessionId);
    }

    // ==================== 内部工具 ====================

    private String resolve(String sessionId) {
        if (sessionId == null) return null;
        if (sessions.containsKey(sessionId)) {
            return sessionId;
        }
        String primary = aliases.get(sessionId);
        return primary != null ? primary : sessionId;
    }

    private void removeAliasesByValue(String primaryKey) {
        if (primaryKey == null) return;
        aliases.entrySet().removeIf(e -> primaryKey.equals(e.getValue()));
    }
}
