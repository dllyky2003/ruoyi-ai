package com.ruoyi.ems.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * 带限流功能的 ChatModel 装饰器，实现 LangChain4j 的 ChatModel 接口。
 */
public class ThrottledChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Semaphore semaphore;

    /**
     * 构造器
     * @param delegate 被装饰的 ChatModel 实例
     * @param maxConcurrent 最大并发请求数（信号量许可数）
     */
    public ThrottledChatModel(ChatModel delegate, int maxConcurrent) {
        this.delegate = delegate;
        this.semaphore = new Semaphore(maxConcurrent);
    }

    /**
     * 若需要外部传入 Semaphore，可使用此构造器
     */
    public ThrottledChatModel(ChatModel delegate, Semaphore semaphore) {
        this.delegate = delegate;
        this.semaphore = semaphore;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        try {
            semaphore.acquire();          // 获取许可，若无则阻塞
            return delegate.chat(messages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("请求被中断", e);
        } finally {
            semaphore.release();          // 释放许可
        }
    }

    // 可选：实现其他接口方法（如 supports、generate 等）委托给 delegate
}
