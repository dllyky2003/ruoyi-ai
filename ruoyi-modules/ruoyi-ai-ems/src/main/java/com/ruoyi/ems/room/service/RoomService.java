package com.ruoyi.ems.room.service;

import com.ruoyi.ems.room.dto.*;
import com.ruoyi.ems.room.model.ClientConnection;
import com.ruoyi.ems.room.model.Role;
import com.ruoyi.ems.room.model.RoomState;
import com.ruoyi.ems.room.model.SessionRoom;
import com.ruoyi.ems.room.registry.RoomRegistry;
import com.ruoyi.ems.room.token.WsTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRegistry registry;
    private final WsTokenService tokenService;
    private final RoomIdGenerator idGenerator;

    @Value("${acls.room.ws-base-url:ws://localhost:8080}")
    private String wsBaseUrl;

    @Value("${acls.room.token-ttl-seconds:300}")
    private long tokenTtlSeconds;

    // ==================== 创建 ====================

    public CreateRoomResponse create(CreateRoomRequest req) {

        Long userId = null;
        try {
            userId = LoginHelper.getUserId();
        } catch (Exception e) {
            log.debug("未登录创建房间，跳过幂等检查");
        }
        log.info("★ create 请求 userId={}", userId);
        if (userId != null) {
            SessionRoom existing = registry.findActiveByCreator(userId);
            log.info("★ findActiveByCreator 结果={}", existing != null ? existing.getSessionId() : "null");
        }
        // 2. 复用：同一用户已有活跃房间，直接返回它
        if (userId != null) {
            SessionRoom existing = registry.findActiveByCreator(userId);
            if (existing != null) {
                log.info("♻️ 用户 {} 复用已有房间 {}", userId, existing.getSessionId());

                Role role = resolveCreatorRole(req);

                // ★ 重新签发 token，旧的可能已经过 5 分钟或已消费
                String token = tokenService.issue(existing.getSessionId(), role, null);
                String wsUrl = buildWsUrl(existing.getSessionId(), role, token);

                return CreateRoomResponse.builder()
                    .sessionId(existing.getSessionId())
                    .caseId(existing.getCaseId())
                    .templateId(existing.getTemplateId())
                    .state(existing.getState().name())
                    .creatorRole(role.getCode())
                    .wsToken(token)
                    .wsUrl(wsUrl)
                    .expiresInSeconds(tokenTtlSeconds)
                    .build();
            }
        }

        // 3. 新建房间（原有逻辑）
        if (req.getCaseId() == null || req.getCaseId().isBlank()) {
            throw new IllegalArgumentException("caseId 不能为空");
        }

        String sessionId = req.getSessionId() != null && !req.getSessionId().isBlank()
            ? req.getSessionId()
            : idGenerator.next();

        if (registry.exists(sessionId)) {
            throw new IllegalStateException("房间已存在: " + sessionId);
        }

        SessionRoom room = registry.create(sessionId, req.getCaseId(), req.getTemplateId());
        room.attachCreator(userId);   // ★ 关键：记录创建者

        Role creatorRole = resolveCreatorRole(req);
        String clientId = UUID.randomUUID().toString().replace("-", "");
        ClientConnection conn = new ClientConnection(clientId, creatorRole, req.getUserId(), null);
        if (!room.join(conn)) {
            registry.remove(sessionId);
            throw new IllegalStateException("创建者加入房间失败");
        }

        String token = tokenService.issue(sessionId, creatorRole, req.getUserId());
        String wsUrl = buildWsUrl(sessionId, creatorRole, token);

        log.info("🏠 房间创建: sessionId={}, caseId={}, creator={}",
            sessionId, req.getCaseId(), userId);

        return CreateRoomResponse.builder()
            .sessionId(sessionId)
            .caseId(req.getCaseId())
            .templateId(req.getTemplateId())
            .state(room.getState().name())
            .creatorRole(creatorRole.getCode())
            .wsToken(token)
            .wsUrl(wsUrl)
            .expiresInSeconds(tokenTtlSeconds)
            .build();
    }

    private Role resolveCreatorRole(CreateRoomRequest req) {
        Role role = Role.fromCode(req.getCreatorRole());
        return role != null ? role : Role.INSTRUCTOR;
    }


    // ==================== 加入 ====================

    public JoinResponse join(String sessionId, JoinRequest req) {
        SessionRoom room = registry.get(sessionId)
            .orElseThrow(() -> new NoSuchElementException("房间不存在: " + sessionId));

        if (room.isTerminal()) {
            throw new IllegalStateException("房间已结束: " + room.getState());
        }

        Role role = Role.fromCode(req.getRole());
        if (role == null) {
            throw new IllegalArgumentException("未知角色: " + req.getRole());
        }

        validateJoinCapacity(room, role);

        String clientId = UUID.randomUUID().toString().replace("-", "");
        ClientConnection conn = new ClientConnection(clientId, role, req.getUserId(), null);
        if (!room.join(conn)) {
            throw new IllegalStateException("加入房间失败");
        }

        String token = tokenService.issue(sessionId, role, req.getUserId());
        String wsUrl = buildWsUrl(sessionId, role, token);

        log.info("👥 客户端加入: sessionId={}, role={}, state={}, clients={}",
            sessionId, role.getCode(), room.getState(), room.size());

        return JoinResponse.builder()
            .sessionId(sessionId)
            .role(role.getCode())
            .wsToken(token)
            .wsUrl(wsUrl)
            .expiresInSeconds(tokenTtlSeconds)
            .state(room.getState().name())
            .build();
    }

    /**
     * 单例角色（ENGINE / SPEAKER）只允许一个连接。
     * VIEWER / INSTRUCTOR 不限。
     */
    private void validateJoinCapacity(SessionRoom room, Role role) {
        switch (role) {
            case ENGINE, SPEAKER -> {
                if (room.hasRole(role)) {
                    throw new IllegalStateException(
                        "房间中已有 " + role.getCode() + " 连接");
                }
            }
            default -> { /* 不限 */ }
        }
    }

    // ==================== 查询 ====================

    public List<RoomSummary> list(String caseId, String stateFilter) {
        List<SessionRoom> rooms = caseId != null && !caseId.isBlank()
            ? registry.findByCaseId(caseId)
            : registry.list();

        return rooms.stream()
            .filter(r -> stateFilter == null
                || r.getState().name().equalsIgnoreCase(stateFilter))
            .map(this::toSummary)
            .collect(Collectors.toList());
    }

    public RoomDetail detail(String sessionId) {
        SessionRoom room = registry.get(sessionId)
            .orElseThrow(() -> new NoSuchElementException("房间不存在: " + sessionId));
        return toDetail(room);
    }

    // ==================== 结束 ====================

    public void end(String sessionId, String reason) {
        SessionRoom room = registry.get(sessionId)
            .orElseThrow(() -> new NoSuchElementException("房间不存在: " + sessionId));

        RoomState before = room.getState();
        room.complete();
        registry.notifyStateChange(room, before, room.getState());

        // 延迟移除，让客户端有机会拉到最终状态
        log.info("🏁 房间结束: sessionId={}, reason={}", sessionId, reason);
    }

    public void destroy(String sessionId) {
        SessionRoom room = registry.remove(sessionId);
        if (room == null) {
            throw new NoSuchElementException("房间不存在: " + sessionId);
        }
    }

    // ==================== 转换 ====================

    private RoomSummary toSummary(SessionRoom room) {
        return RoomSummary.builder()
            .sessionId(room.getSessionId())
            .caseId(room.getCaseId())
            .templateId(room.getTemplateId())
            .state(room.getState().name())
            .clientCount(room.size())
            .roles(room.presentRoles().stream()
                .map(Role::getCode).collect(Collectors.toList()))
            .createdAt(room.getCreatedAt())
            .lastActivityAt(room.getLastActivityAt())
            .build();
    }

    @SuppressWarnings("unchecked")
    private RoomDetail toDetail(SessionRoom room) {
        Map<String, Object> snap = room.snapshot();
        return RoomDetail.builder()
            .sessionId(room.getSessionId())
            .caseId(room.getCaseId())
            .templateId(room.getTemplateId())
            .state(room.getState().name())
            .createdAt(room.getCreatedAt())
            .lastActivityAt(room.getLastActivityAt())
            .pausedDuration(room.getPausedDuration())
            .clients((List<Map<String, Object>>) snap.get("clients"))
            .build();
    }

    private String buildWsUrl(String sessionId, Role role, String token) {
        String base = wsBaseUrl.endsWith("/")
            ? wsBaseUrl.substring(0, wsBaseUrl.length() - 1)
            : wsBaseUrl;
        return String.format("%s/ws/session/%s?role=%s&token=%s",
            base, sessionId, role.getCode(), token);
    }


}
