package com.ruoyi.ems.intent.test.extract;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 仅服务于参数抽取测试的最小 Spring 上下文。
 *
 * <p>扫 ParameterExtractor / Parser / PromptTemplateService 所在包，
 * 让 Spring 自动装配它们的依赖，避免手动 new 时对不上构造签名。</p>
 *
 * <p>ChatModel 用桩实现，测试里不会真的走 LLM 兜底。</p>
 */
@Configuration
@ComponentScan(basePackages = {
    "com.ruoyi.ems.intent.utils",     // ParameterExtractor
    "com.ruoyi.ems.intent.prompt",    // PromptTemplateService
    "com.ruoyi.ems.intent.parser"     // 所有 Parser
})
public class ParameterExtractorTestConfig {

    /**
     * ParameterExtractor 构造里 @Qualifier("throttledExtractionChatModel")。
     * 测试环境只需要一个桩，不真的走 LLM。
     *
     * <p>若编译不过（langchain4j 版本差异导致抽象方法签名不同），
     * 换成下面注释里的 Mockito 版本。</p>
     */
    @Bean("throttledExtractionChatModel")
    public ChatModel throttledExtractionChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(List<ChatMessage> messages) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{}"))
                    .build();
            }
        };

        // Mockito 版本（备用）：
        // ChatModel stub = org.mockito.Mockito.mock(ChatModel.class);
        // org.mockito.Mockito.when(stub.chat(org.mockito.ArgumentMatchers.anyList()))
        //     .thenReturn(ChatResponse.builder()
        //         .aiMessage(AiMessage.from("{}"))
        //         .build());
        // return stub;
    }
}
