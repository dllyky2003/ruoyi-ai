package com.ruoyi.ems.room.model;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 房间对象。拥有一个 sessionId 的完整状态。
 * <p>
 * 生命周期状态：
 * <ul>
 *   <li>CREATED   刚创建，尚无客户端连接</li>
 *   <li>WAITING   有连接，但必需角色未到齐</li>
 *   <li>ACTIVE    所有必需角色到齐，训练进行中</li>
 *   <li>PAUSED    必需角色中有人断开，等待重连</li>
 *   <li>COMPLETED 训练正常结束</li>
 *   <li>EXPIRED   PAUSED 超时未重连，房间失效</li>
 * </ul>
 * </p>
 * <p>
 * 房间与业务会话（EmsSession）通过 emsSessionId 关联。
 * 房间层只负责连接与状态协调，不承载状态机逻辑。
 * </p>
 * <p>
 * 线程安全：修改状态的公共方法都持有 lock。
 * </p>
 *
 * <p>★ 当前策略：允许单角色（ENGINE）启动。
 * {@link #hasAllRequiredRoles()} 与 {@link #allRequiredReady()} 均以
 * ENGINE 就绪为准，不再要求 SPEAKER 等其它角色存在。
 * 未来若需恢复多角色要求，改回 hasAllRequiredRoles 的遍历逻辑即可。</p>
 */
@Getter
public class SessionRoom {

    // ==================== 不可变标识 ====================

    private final String sessionId;
    private final String caseId;
    private final String templateId;
    private final long createdAt;
    private volatile Long creatorUserId;


    // ==================== 连接与角色索引 ====================

    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private final Map<Role, Set<String>> roleIndex = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    // ==================== 生命周期状态 ====================

    private volatile RoomState state = RoomState.CREATED;
    private volatile long lastActivityAt;
    private volatile long pausedAt;

    // ==================== 业务会话关联 ====================

    /** 关联的 EmsSession.id，由 start_case_request 触发创建后写入 */
    private volatile String emsSessionId;

    // ==================== 启动/暂停标志 ====================

    /** 已发 case_ready 的 clientId 集合 */
    private final Set<String> readyClients = ConcurrentHashMap.newKeySet();

    /** 是否已发 case_start */
    private volatile boolean started;

    /** 是否处于暂停 */
    private volatile boolean paused;

    // ==================== 构造 ====================

    public SessionRoom(String sessionId, String caseId, String templateId) {
        this.sessionId = sessionId;
        this.caseId = caseId;
        this.templateId = templateId;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
    }

    public void attachCreator(Long userId) {
        this.creatorUserId = userId;
    }

    /**
     * 移除某个角色的所有连接。用于 WebSocket 断线时清理占位。
     */
    public void leaveAllOfRole(Role role) {
        if (role == null) return;
        lock.lock();
        try {
            Set<String> ids = roleIndex.get(role);
            if (ids == null) return;
            for (String id : new HashSet<>(ids)) {
                clients.remove(id);
                readyClients.remove(id);
            }
            roleIndex.remove(role);
            touch();
            advanceStateAfterLeave();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 加入一个客户端。重复 clientId 会拒绝。
     *
     * @return true 表示加入成功
     */
    public boolean join(ClientConnection conn) {
        if (conn == null || conn.getClientId() == null) return false;

        lock.lock();
        try {
            if (clients.containsKey(conn.getClientId())) return false;

            clients.put(conn.getClientId(), conn);
            roleIndex.computeIfAbsent(conn.getRole(), k -> ConcurrentHashMap.newKeySet())
                .add(conn.getClientId());
            touch();

            advanceStateAfterJoin();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 移除一个客户端。
     *
     * @return true 表示成功移除
     */
    public boolean leave(String clientId) {
        if (clientId == null) return false;

        lock.lock();
        try {
            ClientConnection conn = clients.remove(clientId);
            if (conn == null) return false;

            Set<String> ids = roleIndex.get(conn.getRole());
            if (ids != null) {
                ids.remove(clientId);
                if (ids.isEmpty()) roleIndex.remove(conn.getRole());
            }
            readyClients.remove(clientId);
            touch();

            advanceStateAfterLeave();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Optional<ClientConnection> getClient(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    public List<ClientConnection> getClientsByRole(Role role) {
        if (role == null) return List.of();
        Set<String> ids = roleIndex.get(role);
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
            .map(clients::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 返回房间内所有客户端。广播场景使用。
     */
    public List<ClientConnection> getAllClients() {
        return new ArrayList<>(clients.values());
    }

    public boolean hasRole(Role role) {
        if (role == null) return false;
        Set<String> ids = roleIndex.get(role);
        return ids != null && !ids.isEmpty();
    }

    public int size() {
        return clients.size();
    }

    public Set<Role> presentRoles() {
        return new HashSet<>(roleIndex.keySet());
    }

    // ==================== 状态机 ====================

    private void advanceStateAfterJoin() {
        if (state == RoomState.COMPLETED || state == RoomState.EXPIRED) return;

        if (state == RoomState.PAUSED) {
            if (hasAllRequiredRoles()) {
                state = RoomState.ACTIVE;
                pausedAt = 0;
            }
            return;
        }

        if (hasAllRequiredRoles()) {
            state = RoomState.ACTIVE;
        } else if (state == RoomState.CREATED) {
            state = RoomState.WAITING;
        }
    }

    private void advanceStateAfterLeave() {
        if (state == RoomState.COMPLETED || state == RoomState.EXPIRED) return;

        if (!hasAllRequiredRoles()) {
            if (state == RoomState.ACTIVE) {
                state = RoomState.PAUSED;
                pausedAt = System.currentTimeMillis();
            }
            // WAITING 状态下有人离开，保持 WAITING，由清扫任务决定是否 EXPIRED
        }
    }

    /**
     * ★ 当前策略：允许单角色启动。
     * <p>只要 ENGINE 连接存在，房间即视为"必需角色到齐"，
     * 状态机从 WAITING 推进到 ACTIVE。</p>
     *
     * <p>未来若需恢复多角色要求，改回：
     * <pre>
     * for (Role r : Role.requiredRoles()) {
     *     if (!hasRole(r)) return false;
     * }
     * return true;
     * </pre>
     * </p>
     */
    private boolean hasAllRequiredRoles() {
        return hasRole(Role.ENGINE);
    }

    public void complete() {
        lock.lock();
        try {
            if (state != RoomState.EXPIRED) {
                state = RoomState.COMPLETED;
                touch();
            }
        } finally {
            lock.unlock();
        }
    }

    public void expire() {
        lock.lock();
        try {
            if (state == RoomState.PAUSED || state == RoomState.WAITING) {
                state = RoomState.EXPIRED;
                touch();
            }
        } finally {
            lock.unlock();
        }
    }

    private void touch() {
        lastActivityAt = System.currentTimeMillis();
    }

    public boolean isTerminal() {
        return state == RoomState.COMPLETED || state == RoomState.EXPIRED;
    }

    public boolean isPausable() {
        return state == RoomState.PAUSED;
    }

    public long getPausedDuration() {
        return pausedAt > 0 ? System.currentTimeMillis() - pausedAt : 0;
    }

    // ==================== 业务会话关联 ====================

    /**
     * 关联业务会话 ID。仅允许关联一次。
     */
    public void attachEmsSession(String emsSessionId) {
        if (emsSessionId == null) {
            throw new IllegalArgumentException("emsSessionId 不能为空");
        }
        lock.lock();
        try {
            if (this.emsSessionId != null) {
                throw new IllegalStateException("房间已关联业务会话: " + this.emsSessionId);
            }
            this.emsSessionId = emsSessionId;
            touch();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEmsSession() {
        return emsSessionId != null;
    }

    // ==================== 启动/暂停 ====================

    public void markReady(String clientId) {
        if (clientId == null) return;
        readyClients.add(clientId);
        touch();
    }

    public boolean isReady(String clientId) {
        return readyClients.contains(clientId);
    }

    /**
     * ENGINE 角色是否已就绪（发过 case_ready）。
     * <p>本方法只检查 ENGINE，不要求其他必需角色存在。</p>
     */
    public boolean isEngineReady() {
        return getClientsByRole(Role.ENGINE).stream()
            .anyMatch(c -> readyClients.contains(c.getClientId()));
    }

    /**
     * 所有必需角色是否已就绪。
     * <p>
     * ★ 当前策略：允许单角色启动。
     * 因此本方法等价于 {@link #isEngineReady()}——
     * 只要 ENGINE 就绪，即视为全部就绪。
     * </p>
     *
     * <p>未来若需恢复多角色要求，改回：
     * <pre>
     * if (!isEngineReady()) return false;
     * for (Role r : Role.requiredRoles()) {
     *     if (r == Role.ENGINE) continue;
     *     if (!hasRole(r)) return false;
     * }
     * return true;
     * </pre>
     * </p>
     */
    public boolean allRequiredReady() {
        return isEngineReady();
    }

    public void markStarted() {
        lock.lock();
        try {
            if (!started) {
                started = true;
                touch();
            }
        } finally {
            lock.unlock();
        }
    }

    public void markPaused() {
        lock.lock();
        try {
            if (!paused && started) {
                paused = true;
                touch();
            }
        } finally {
            lock.unlock();
        }
    }

    public void markResumed() {
        lock.lock();
        try {
            if (paused) {
                paused = false;
                touch();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isRunning() {
        return started && !paused && !isTerminal();
    }

    // ==================== 快照 ====================

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", sessionId);
        m.put("caseId", caseId);
        m.put("templateId", templateId);
        m.put("state", state.name());
        m.put("createdAt", createdAt);
        m.put("lastActivityAt", lastActivityAt);
        m.put("pausedAt", pausedAt);
        m.put("pausedDuration", getPausedDuration());
        m.put("clientCount", clients.size());
        m.put("roles", presentRoles().stream()
            .map(Role::getCode)
            .collect(Collectors.toList()));

        // 业务会话关联
        m.put("emsSessionId", emsSessionId);

        // 启动/暂停标志
        m.put("started", started);
        m.put("paused", paused);
        m.put("readyClients", new ArrayList<>(readyClients));

        // 成员明细
        List<Map<String, Object>> members = clients.values().stream()
            .map(c -> {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("clientId", c.getClientId());
                mm.put("role", c.getRole().getCode());
                mm.put("userId", c.getUserId());
                mm.put("joinedAt", c.getJoinedAt());
                mm.put("ready", readyClients.contains(c.getClientId()));
                return mm;
            })
            .collect(Collectors.toList());
        m.put("clients", members);

        return m;
    }

    /**
     * 解绑业务会话。
     *
     * <p>用于房间"结束→重启"场景：旧会话已 COMPLETED，需要重新创建新会话。
     * <p>与 {@link #attachEmsSession(String)} 配对使用。
     */
    public void detachEmsSession() {
        this.emsSessionId = null;
    }
}
