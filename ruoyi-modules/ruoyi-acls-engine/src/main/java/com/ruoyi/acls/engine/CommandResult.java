package com.ruoyi.acls.engine;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 指令执行结果
 */
@Data
@Builder
public class CommandResult {
    private boolean success;
    private String responseText;          // 反馈给组长的语音文本
    private String sessionId;
    private List<String> animations;      // 需要触发的VR动画列表
    private Map<String, Object> contextUpdates; // 上下文变量更新
    private String errorMessage;
    private long elapsedMillis;

    // ==================== 工厂方法 ====================

    public static CommandResult success() {
        return success("操作成功");
    }

    public static CommandResult success(String text) {
        return CommandResult.builder()
            .success(true)
            .responseText(text)
            .build();
    }

    public static CommandResult failure(String error) {
        return CommandResult.builder()
            .success(false)
            .errorMessage(error)
            .build();
    }

    /**
     * 澄清：需要用户进一步说明（非阻塞）
     */
    public static CommandResult clarify(String message) {
        return CommandResult.builder()
            .success(false)
            .errorMessage(message)
            .build();
    }

    /**
     * 警告：操作被允许但有风险（非阻塞）
     */
    public static CommandResult warning(String message) {
        return CommandResult.builder()
            .success(false)
            .errorMessage(message)
            .build();
    }

    /**
     * ★ 待确认状态：操作已暂停，等待用户确认（阻塞）
     *
     * @param message 待确认提示文本
     * @return CommandResult 表示正在等待确认
     */
    public static CommandResult pending(String message) {
        return CommandResult.builder()
            .success(false)           // 逻辑上尚未完成，但区别于失败
            .responseText(message)
            .build();
    }

    /**
     * 待确认状态（带上下文更新）
     */
    public static CommandResult pending(String message, Map<String, Object> updates) {
        return CommandResult.builder()
            .success(false)
            .responseText(message)
            .contextUpdates(updates)
            .build();
    }

    // ==================== 便捷判读方法 ====================

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public boolean isPending() {
        // pending 状态通过 responseText 非空且 success=false 来识别
        // 但更准确的是用一个独立字段，这里通过约定判断
        // 为简化，引入新的判断方法：如果 responseText 不为空且 success=false，可能表示 pending 或 error
        // 建议在业务层用具体的工厂方法区分
        return !success && responseText != null && errorMessage == null;
    }
}
