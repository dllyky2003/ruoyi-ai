package com.ruoyi.acls.scheduler;

/**
 * 思考任务回调
 */
@FunctionalInterface
public interface ThinkCallback {
    /**
     * 思考完成时调用
     * @param memberId 组员ID
     * @param response LLM返回的文本
     */
    void onComplete(String memberId, String response);

    /**
     * 思考失败时的回调（可选重写）
     */
    default void onError(String memberId, Throwable error) {
        // 默认空实现
    }
}
