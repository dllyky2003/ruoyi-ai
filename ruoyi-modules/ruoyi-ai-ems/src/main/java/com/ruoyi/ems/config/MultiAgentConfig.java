package com.ruoyi.ems.config;

import com.ruoyi.ems.engine.multiagent.agents.AssessmentAgent;
import com.ruoyi.ems.engine.multiagent.agents.DecisionAgent;
import com.ruoyi.ems.engine.multiagent.agents.FeedbackAgent;
import com.ruoyi.ems.engine.multiagent.agents.SafetyAgent;
import com.ruoyi.ems.engine.multiagent.supervisor.SupervisorAgent;
import com.ruoyi.ems.engine.multiagent.supervisor.WeightedFusionStrategy;

import dev.langchain4j.model.chat.ChatModel;

import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

@Configuration
public class MultiAgentConfig {

    private final Semaphore semaphore = new Semaphore(1);
    private final ChatServiceFactory chatServiceFactory;
    private final IChatModelService chatModelService;

    public MultiAgentConfig(ChatServiceFactory chatServiceFactory, IChatModelService chatModelService) {
        this.chatServiceFactory = chatServiceFactory;
        this.chatModelService = chatModelService;
    }

    /**
     * 根据模型名称从数据库加载配置并构建 ChatModel
     */
    private ChatModel buildChatModel(String modelName,Double temperature,String seed) {
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new IllegalArgumentException("未找到模型配置: " + modelName);
        }
        modelVo.setTemperature(temperature);
        modelVo.setSeed(seed);
        AbstractChatService service = chatServiceFactory.getOriginalService(modelVo.getProviderCode());
        return service.buildChatModel(modelVo);
    }

//    // 假设数据库中有以下 model_name 记录
//    private static final String MODEL_ASSESSMENT = "qwen2.5-assess";
//    private static final String MODEL_DECISION   = "qwen2.5-decision";
//    private static final String MODEL_SAFETY     = "qwen2.5-safety";
//    private static final String MODEL_FEEDBACK   = "qwen2.5-feedback";
//    private static final String MODEL_EXTRACTION = "qwen2.5-extraction";
//    private static final String MODEL_REPORT     = "qwen2.5-report";
//    private static final String MODEL_DEFAULT    = "qwen2.5-default";
// 假设数据库中有以下 model_name 记录
    private static final String MODEL_ASSESSMENT = "qwen2.5:latest";
    private static final String MODEL_DECISION   = "qwen2.5:latest";
    private static final String MODEL_SAFETY     = "qwen2.5:latest";
    private static final String MODEL_FEEDBACK   = "qwen2.5:latest";
    private static final String MODEL_EXTRACTION = "qwen2.5:latest";
    private static final String MODEL_REPORT     = "qwen2.5:latest";
    private static final String MODEL_DEFAULT    = "qwen2.5:latest";
    @Bean
    @Primary
    public ChatModel defaultChatModel() {
        return buildChatModel(MODEL_DEFAULT,0.6,"");
    }

    @Bean
    public ChatModel assessmentChatModel() {
        return buildChatModel(MODEL_ASSESSMENT,0.6,"");
    }

    @Bean
    public ChatModel decisionChatModel() {
        return buildChatModel(MODEL_DECISION,0.6,"");
    }

    @Bean
    public ChatModel safetyChatModel() {
        return buildChatModel(MODEL_SAFETY,0.1,"42");
    }

    @Bean
    public ChatModel feedbackChatModel() {
        return buildChatModel(MODEL_FEEDBACK,0.1,"42");
    }

    @Bean
    public ChatModel extractionChatModel() {
        return buildChatModel(MODEL_EXTRACTION,0.0,"42");
    }

    @Bean
    public ChatModel reportChatModel() {
        return buildChatModel(MODEL_REPORT,0.7,""  );
    }

    // ---------- 限流装饰器（不变） ----------
    @Bean
    public ChatModel throttledAssessmentChatModel(@Qualifier("assessmentChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    @Bean
    public ChatModel throttledDecisionChatModel(@Qualifier("decisionChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    @Bean
    public ChatModel throttledSafetyChatModel(@Qualifier("safetyChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    @Bean
    public ChatModel throttledFeedbackChatModel(@Qualifier("feedbackChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    @Bean
    public ChatModel throttledExtractionChatModel(@Qualifier("extractionChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    @Bean
    public ChatModel throttledReportChatModel(@Qualifier("reportChatModel") ChatModel model) {
        return new ThrottledChatModel(model, semaphore);
    }

    // ---------- 智能体装配 ----------
    @Bean
    public AssessmentAgent assessmentAgent(@Qualifier("throttledAssessmentChatModel") ChatModel model) {
        return AssessmentAgent.builder()
            .name("临床评估智能体")
            .chatModel(model)
            .temperature(0.1)   // 仅供 Agent 内部使用，实际温度以数据库为准
            .build();
    }

    @Bean
    public DecisionAgent decisionAgent(@Qualifier("throttledDecisionChatModel") ChatModel model) {
        return DecisionAgent.builder()
            .name("路径决策智能体")
            .chatModel(model)
            .temperature(0.1)
            .build();
    }

    @Bean
    public SafetyAgent safetyAgent(@Qualifier("throttledSafetyChatModel") ChatModel model) {
        return SafetyAgent.builder()
            .name("安全稽查智能体")
            .chatModel(model)
            .temperature(0.0)
            .build();
    }

    @Bean
    public FeedbackAgent feedbackAgent(@Qualifier("throttledFeedbackChatModel") ChatModel model) {
        return FeedbackAgent.builder()
            .name("教学反馈智能体")
            .chatModel(model)
            .temperature(0.3)
            .build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(
        AssessmentAgent assessmentAgent,
        DecisionAgent decisionAgent,
        SafetyAgent safetyAgent,
        FeedbackAgent feedbackAgent) {
        return SupervisorAgent.builder()
            .name("ACLS急救评估总调度官")
            .description("协调4个专业智能体，对学员急救操作进行多维评估")
            .subAgents(Arrays.asList(assessmentAgent, decisionAgent, safetyAgent, feedbackAgent))
            .executionStrategy(SupervisorAgent.ExecutionStrategy.PARALLEL)
            .fusionStrategy(new WeightedFusionStrategy())
            .timeout(Duration.ofSeconds(120))
            .build();
    }
}
