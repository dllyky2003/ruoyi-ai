package com.ruoyi.ems.room.model;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * ★ 新增：Unity 的 attemptId（契约 V1.0.0）。
     *
     * <p>来源：WebSocket 握手 URL query 参数 {@code attemptId}。
     * 服务器端不生成，只记录 Unity 传来的值。</p>
     *
     * <p>用途：快照协议（SNAPSHOT）必须带 attemptId，
     * 且必须与 Unity 本地 {@code AppContext.AttemptId} 一致，
     * 否则 Unity 端 {@code SnapshotApplier} 会拒绝快照。</p>
     */
    private volatile String attemptId;

    // ==================== 启动/暂停标志 ====================

    /** 已发 case_ready 的 clientId 集合 */
    private final Set<String> readyClients = ConcurrentHashMap.newKeySet();

    /** 是否已发 case_start */
    private volatile boolean started;

    /** 是否处于暂停 */
    private volatile boolean paused;

    /**
     * 该房间是否已消费 start_session。
     * <p>
     * 语义：客户端发过 start_session 且服务端真正开始过案例。
     * 用于拒绝"已开始案例的房间被复用"——复用会带来上一局的脏 emsSessionId、
     * 脏 currentState、脏变量值。
     * </p>
     */
    private volatile boolean startSessionConsumed = false;


    private final AtomicLong clientSequenceGen = new AtomicLong(0);


    /** A-3：本 attempt 已接受的最大 clientSequence，0 表示尚未收到任何 CMD */
    private final java.util.concurrent.atomic.AtomicLong lastClientSequence
        = new java.util.concurrent.atomic.AtomicLong(0L);

    /** A-3：读取上次接受的 clientSequence（0 = 尚未收到） */
    public long getLastClientSequence() {
        return lastClientSequence.get();
    }

    /** A-3：覆盖写入 clientSequence（仅在通过检查后调用） */
    public void setLastClientSequence(long seq) {
        lastClientSequence.set(seq);
    }
    public long nextClientSequence() {
        return clientSequenceGen.incrementAndGet();
    }

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

    /**
     * ★ 新增：记录 Unity 的 attemptId（契约 V1.0.0）。
     *
     * <p>从 WebSocket 握手 query 参数读取，用于快照协议的 attemptId 透传。
     * 服务器端不生成 attemptId，只记录 Unity 传来的值。</p>
     *
     * <p>为什么必须记录：</p>
     * <ul>
     *   <li>契约 4.1 要求 SNAPSHOT 消息必须带 attemptId</li>
     *   <li>Unity 端 {@code SnapshotApplier} 会校验 attemptId 与本地是否一致</li>
     *   <li>不一致时返回 {@code RejectedAttemptMismatch}，快照直接被丢弃</li>
     *   <li>为空时返回 {@code RejectedInvalid}，同样被丢弃</li>
     * </ul>
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>未设置（null/空）→ 写入</li>
     *   <li>已设置且相同 → 无副作用</li>
     *   <li>已设置且不同 → 覆盖并保留日志（正常不应出现，重连时 AppContext.AttemptId 不变）</li>
     * </ul>
     *
     * <p>线程安全：与 {@link #attachEmsSession(String)} 一致，走 {@code lock}。</p>
     *
     * @param attemptId Unity 客户端生成的 attemptId，形如 {@code attempt-<32位hex>}
     */
    public void attachAttemptId(String attemptId) {
        if (attemptId == null || attemptId.isBlank()) {
            return;
        }

        lock.lock();
        try {
            String old = this.attemptId;
            if (old == null || old.isBlank()) {
                this.attemptId = attemptId;
                touch();
            } else if (!old.equals(attemptId)) {
                // 正常不应出现：Unity 的 AppContext.AttemptId 在一次尝试内保持不变。
                // 若出现，说明客户端行为异常，覆盖旧值并留痕，便于排查。
                this.attemptId = attemptId;
                touch();
            }
            // 相同则无副作用
        } finally {
            lock.unlock();
        }
    }

    public boolean hasEmsSession() {
        return emsSessionId != null;
    }

    /**
     * 解绑业务会话。
     *
     * <p>用于房间"结束→重启"场景：旧会话已 COMPLETED，需要重新创建新会话。</p>
     */
    public void detachEmsSession() {
        lock.lock();
        try {
            this.emsSessionId = null;
            touch();
        } finally {
            lock.unlock();
        }
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
     * <p>★ 当前策略：允许单角色启动，等价于 {@link #isEngineReady()}。</p>
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

    /**
     * ★ 新增：标记 start_session 已消费。
     *
     * <p>在 {@code EngineSessionControlHandler} 收到 start_session 时调用。
     * 一旦标记，{@code RoomService.create} 在复用判断时会拒绝复用该房间，
     * 避免下一局被上一局的脏 emsSessionId / currentState 污染。</p>
     *
     * <p>幂等：重复调用无副作用。</p>
     */
    public void markStartSessionConsumed() {
        lock.lock();
        try {
            if (!this.startSessionConsumed) {
                this.startSessionConsumed = true;
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
        m.put("attemptId", attemptId);   // ★ 新增

        // 启动/暂停标志
        m.put("started", started);
        m.put("paused", paused);
        m.put("startSessionConsumed", startSessionConsumed);
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
}
