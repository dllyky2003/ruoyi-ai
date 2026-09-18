package com.ruoyi.acls.vr;

import com.ruoyi.ems.npc.IVrClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VrActionExecutor {

    private final IVrClientService vrClientService;
    private final ApplicationEventPublisher eventPublisher;

    public VrTaskHandle execute(String sessionId, String action,
                                Map<String, Object> params,
                                String speech, int durationSeconds) {
        String requestId = UUID.randomUUID().toString();

        log.info("🎬 [VR] 开始执行动作: sessionId={}, requestId={}, action={}, duration={}s",
            sessionId, requestId, action, durationSeconds);

        CompletableFuture<Void> underlyingFuture = vrClientService.sendAction(
            sessionId, action, params, speech, durationSeconds
        );

        CompletableFuture<VrActionResult> resultFuture = underlyingFuture
            .thenApply(ignore -> {
                log.info("✅ [VR] 动作完成: requestId={}, action={}", requestId, action);
                return VrActionResult.success(requestId, Map.of("completed", true));
            })
            .exceptionally(throwable -> {
                log.error("❌ [VR] 动作异常: requestId={}, action={}", requestId, action, throwable);
                return VrActionResult.failure(requestId, throwable.getMessage());
            });

        // ★ 修复：从 details 提取错误信息
        resultFuture.thenAccept(result -> {
            String errorMsg = null;
            if (!result.isSuccess() && result.getDetails() != null) {
                Object err = result.getDetails().get("error");
                if (err != null) {
                    errorMsg = err.toString();
                }
            }
            VrActionCompletedEvent event = new VrActionCompletedEvent(
                this,
                sessionId,
                requestId,
                action,
                params,
                result.isSuccess(),
                errorMsg
            );
            eventPublisher.publishEvent(event);
            log.debug("📢 [事件] 发布 VrActionCompletedEvent: sessionId={}, action={}", sessionId, action);
        });

        return new VrTaskHandle(requestId, resultFuture, this);
    }

    public void cancel(String requestId) {
        log.warn("⏭️ 当前版本不支持取消 VR 任务: requestId={}", requestId);
    }

    public static class VrTaskHandle {
        private final String requestId;
        private final CompletableFuture<VrActionResult> future;
        private final VrActionExecutor executor;

        public VrTaskHandle(String requestId, CompletableFuture<VrActionResult> future, VrActionExecutor executor) {
            this.requestId = requestId;
            this.future = future;
            this.executor = executor;
        }

        public String getRequestId() {
            return requestId;
        }

        public CompletableFuture<VrActionResult> getFuture() {
            return future;
        }

        public void cancel() {
            executor.cancel(requestId);
        }
    }
}
