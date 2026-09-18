package com.ruoyi.ems.room.registry;

import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 全局房间注册表。所有房间的唯一真相源。
 * 单实例内存版；多实例部署时替换为 Redis 实现。
 */
@Slf4j
@Component
public class RoomRegistry {

    private final Map<String, SessionRoom> rooms = new ConcurrentHashMap<>();

    // 房间状态变化监听器，用于广播和持久化
    private final List<Consumer<RoomStateChangeEvent>> stateListeners = new CopyOnWriteArrayList<>();

    public SessionRoom create(String sessionId, String caseId, String templateId) {
        SessionRoom room = new SessionRoom(sessionId, caseId, templateId);
        SessionRoom existing = rooms.putIfAbsent(sessionId, room);
        if (existing != null) {
            throw new IllegalStateException("房间已存在: " + sessionId);
        }
        log.info("🏠 房间已创建: sessionId={}, caseId={}", sessionId, caseId);
        return room;
    }

    public Optional<SessionRoom> get(String sessionId) {
        return Optional.ofNullable(rooms.get(sessionId));
    }

    public SessionRoom getOrThrow(String sessionId) {
        SessionRoom room = rooms.get(sessionId);
        if (room == null) {
            throw new NoSuchElementException("房间不存在: " + sessionId);
        }
        return room;
    }

    public boolean exists(String sessionId) {
        return rooms.containsKey(sessionId);
    }

    public List<SessionRoom> list() {
        return new ArrayList<>(rooms.values());
    }

    public List<SessionRoom> listActive() {
        return rooms.values().stream()
            .filter(r -> r.getState() == RoomState.WAITING
                || r.getState() == RoomState.ACTIVE
                || r.getState() == RoomState.PAUSED)
            .collect(Collectors.toList());
    }

    public List<SessionRoom> findByCaseId(String caseId) {
        return rooms.values().stream()
            .filter(r -> caseId.equals(r.getCaseId()))
            .collect(Collectors.toList());
    }

    /**
     * ★ 按业务会话 ID 查找房间。
     * <p>
     * 房间创建时还没有 emsSessionId，由 start_case_request 触发后通过
     * SessionRoom.attachEmsSession 关联。未关联的房间不会命中此查询。
     * </p>
     */
    public Optional<SessionRoom> getByEmsSessionId(String emsSessionId) {
        if (emsSessionId == null || emsSessionId.isBlank()) {
            return Optional.empty();
        }
        return rooms.values().stream()
            .filter(r -> emsSessionId.equals(r.getEmsSessionId()))
            .findFirst();
    }

    public SessionRoom remove(String sessionId) {
        SessionRoom room = rooms.remove(sessionId);
        if (room != null) {
            log.info("🏠 房间已移除: sessionId={}, 最终状态={}", sessionId, room.getState());
        }
        return room;
    }

    public int size() {
        return rooms.size();
    }

    // ==================== 状态变化通知 ====================

    public void addStateListener(Consumer<RoomStateChangeEvent> listener) {
        stateListeners.add(listener);
    }

    public void notifyStateChange(SessionRoom room, RoomState from, RoomState to) {
        RoomStateChangeEvent evt = new RoomStateChangeEvent(room.getSessionId(), from, to);
        log.info("🔄 房间状态变化: sessionId={}, {} → {}", room.getSessionId(), from, to);
        for (Consumer<RoomStateChangeEvent> l : stateListeners) {
            try { l.accept(evt); } catch (Exception e) {
                log.warn("房间状态监听器异常", e);
            }
        }
    }
    /**
     * 查找指定创建者当前的活跃房间（非终态）。
     * @param creatorUserId 创建者用户ID
     * @return 找到的活跃房间，没有则返回 null
     */
    public SessionRoom findActiveByCreator(Long creatorUserId) {
        if (creatorUserId == null) return null;
        for (SessionRoom room : rooms.values()) {
            if (creatorUserId.equals(room.getCreatorUserId())
                && isActiveState(room.getState())) {
                return room;
            }
        }
        return null;
    }

    /** 判断是否活跃态（非终态） */
    private boolean isActiveState(RoomState state) {
        return state == RoomState.WAITING
            || state == RoomState.ACTIVE
            || state == RoomState.PAUSED;
    }
    public record RoomStateChangeEvent(String sessionId, RoomState from, RoomState to) {}
}
