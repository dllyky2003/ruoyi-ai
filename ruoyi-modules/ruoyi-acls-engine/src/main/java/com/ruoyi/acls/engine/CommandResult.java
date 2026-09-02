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

    public static CommandResult success(String text) {
        return CommandResult.builder().success(true).responseText(text).build();
    }

    public static CommandResult failure(String error) {
        return CommandResult.builder().success(false).errorMessage(error).build();
    }
}
