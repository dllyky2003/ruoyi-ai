package com.ruoyi.ems.controller;

import com.ruoyi.ems.room.dto.*;
import com.ruoyi.ems.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.CreateSessionResponse;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * ACLS 训练房间控制器。
 * <p>
 * 房间是训练的多客户端协调单元，包含 engine（Unity 生理引擎）、speaker（学员主控）、
 * viewer（观察者）、instructor（导师）四类角色。房间由服务端创建和管理，
 * 客户端通过 REST 发现并加入，通过 WebSocket 建立实时连接。
 * </p>
 *
 * @author system
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ems/room")
@RequiredArgsConstructor
@Tag(name = "训练房间", description = "ACLS 训练房间的创建、发现、加入、结束")
public class RoomController {

    private final RoomService roomService;

    // ==================== 房间生命周期 ====================

    /**
     * 创建并开始一个新的训练房间。
     * <p>
     * 创建者根据 {@code creatorRole} 立即成为房间成员。
     * 常见用法：
     * <ul>
     *   <li>导师端创建：{@code creatorRole=instructor}，先建房间等学员和引擎</li>
     *   <li>Unity 引擎创建：{@code creatorRole=engine}，先建房间等前端</li>
     * </ul>
     * 返回的 {@code wsToken} 用于后续 WebSocket 握手。
     * </p>
     *
     * @param req 创建请求，至少包含 caseId
     * @return 包含 sessionId、wsToken、wsUrl 的响应
     */
    @Operation(summary = "创建训练房间",
        description = "创建房间并让创建者成为第一个成员，返回 WebSocket 连接地址和 token")
    @PostMapping("/create")
    public R<CreateRoomResponse> create(@RequestBody CreateRoomRequest req) {
        try {
            return R.ok(roomService.create(req));
        } catch (IllegalArgumentException e) {
            log.warn("创建房间参数错误: {}", e.getMessage());
            return R.fail(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("创建房间冲突: {}", e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建房间失败", e);
            return R.fail("创建房间失败: " + e.getMessage());
        }
    }

    /**
     * 结束指定房间。
     * <p>
     * 状态变为 COMPLETED，房间对象保留以便查询最终状态和审计。
     * 房间内的客户端会收到 WebSocket 通知。
     * </p>
     *
     * @param sessionId 房间 ID
     * @param reason    可选结束原因
     */
    @Operation(summary = "结束训练房间",
        description = "将房间状态置为 COMPLETED，保留数据供查询")
    @PostMapping("/{sessionId}/end")
    public R<Void> end(
        @Parameter(description = "房间ID", required = true)
        @PathVariable String sessionId,
        @Parameter(description = "结束原因")
        @RequestParam(required = false) String reason) {
        try {
            roomService.end(sessionId, reason);
            return R.ok();
        } catch (Exception e) {
            log.error("结束房间失败: sessionId={}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 彻底删除房间。
     * <p>
     * 从注册表移除，释放内存。通常在房间结束且不再需要查询后由清扫任务调用。
     * </p>
     */
    @Operation(summary = "删除训练房间",
        description = "从注册表移除房间，释放资源")
    @DeleteMapping("/{sessionId}")
    public R<Void> destroy(
        @Parameter(description = "房间ID", required = true)
        @PathVariable String sessionId) {
        try {
            roomService.destroy(sessionId);
            return R.ok();
        } catch (Exception e) {
            log.error("删除房间失败: sessionId={}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 房间发现 ====================

    /**
     * 列出当前活跃的训练房间。
     * <p>
     * 用于客户端"发现房间"：前端打开页面后调用此接口，选择要加入的房间。
     * 默认返回 WAITING / ACTIVE / PAUSED 三种非终态房间。
     * </p>
     *
     * @param caseId 可选，按案例过滤
     * @param state  可选，按状态过滤（WAITING/ACTIVE/PAUSED/COMPLETED/EXPIRED）
     */
    @Operation(summary = "列出训练房间",
        description = "列出当前活跃房间，支持按案例和状态过滤")
    @GetMapping("/list")
    public R<List<RoomSummary>> list(
        @Parameter(description = "案例ID，如 stable_narrow")
        @RequestParam(required = false) String caseId,
        @Parameter(description = "房间状态过滤")
        @RequestParam(required = false) String state) {
        try {
            return R.ok(roomService.list(caseId, state));
        } catch (Exception e) {
            log.error("列出房间失败", e);
            return R.fail(e.getMessage());
        }
    }

    /**
     * 查询房间详情。
     * <p>
     * 包含房间状态、创建时间、暂停时长、当前所有客户端的角色和加入时间。
     * </p>
     */
    @Operation(summary = "获取房间详情",
        description = "查询房间状态、成员列表、暂停时长等信息")
    @GetMapping("/{sessionId}")
    public R<RoomDetail> detail(
        @Parameter(description = "房间ID", required = true)
        @PathVariable String sessionId) {
        try {
            return R.ok(roomService.detail(sessionId));
        } catch (Exception e) {
            log.error("查询房间详情失败: sessionId={}", sessionId, e);
            return R.fail(e.getMessage());
        }
    }

    // ==================== 加入房间 ====================

    /**
     * 加入指定房间。
     * <p>
     * 校验角色容量：
     * <ul>
     *   <li>ENGINE：单例，房间内最多一个</li>
     *   <li>SPEAKER：单例，房间内最多一个</li>
     *   <li>VIEWER：可多个</li>
     *   <li>INSTRUCTOR：可多个</li>
     * </ul>
     * 成功后返回该角色的 {@code wsToken}，有效期 5 分钟，一次性消费。
     * </p>
     *
     * @param sessionId 房间 ID
     * @param req       包含 role 和可选 userId
     */
    @Operation(summary = "加入训练房间",
        description = "以指定角色加入房间，返回 WebSocket 连接 token")
    @PostMapping("/{sessionId}/join")
    public R<JoinResponse> join(
        @Parameter(description = "房间ID", required = true)
        @PathVariable String sessionId,
        @RequestBody JoinRequest req) {
        try {
            return R.ok(roomService.join(sessionId, req));
        } catch (NoSuchElementException e) {
            log.warn("加入房间失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("加入房间冲突: sessionId={}, {}", sessionId, e.getMessage());
            return R.fail(e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("加入房间参数错误: {}", e.getMessage());
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("加入房间失败: sessionId={}", sessionId, e);
            return R.fail("加入房间失败: " + e.getMessage());
        }
    }
}
