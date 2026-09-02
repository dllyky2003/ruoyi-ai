package com.ruoyi.ems.engine.multiagent.supervisor;


import com.ruoyi.ems.engine.multiagent.Agent;
import com.ruoyi.ems.engine.multiagent.dto.AgentInput;
import com.ruoyi.ems.engine.multiagent.dto.AgentOutput;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
public class SupervisorAgent {

    private final String name;
    private final String description;
    private final List<Agent> subAgents;
    private final ExecutionStrategy executionStrategy;
    private final FusionStrategy fusionStrategy;
    private final Duration timeout;
    private final ExecutorService executor;

    public enum ExecutionStrategy {
        SEQUENTIAL,
        PARALLEL
    }

    @Builder
    public SupervisorAgent(String name, String description,
                           List<Agent> subAgents,
                           ExecutionStrategy executionStrategy,
                           FusionStrategy fusionStrategy,
                           Duration timeout) {
        this.name = name;
        this.description = description;
        this.subAgents = subAgents != null ? subAgents : new ArrayList<>();
        this.executionStrategy = executionStrategy != null ? executionStrategy : ExecutionStrategy.PARALLEL;
        this.fusionStrategy = fusionStrategy != null ? fusionStrategy : new WeightedFusionStrategy();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(15);
        this.executor = Executors.newFixedThreadPool(Math.max(4, this.subAgents.size()));
    }

    public SupervisorResult execute(AgentInput input) {
        log.info("Supervisor [{}] 开始调度 {} 个智能体", name, subAgents.size());
        long startTime = System.currentTimeMillis();

        Map<String, AgentOutput> outputs = new LinkedHashMap<>();

        if (executionStrategy == ExecutionStrategy.PARALLEL) {
            outputs = executeParallel(input);
        } else {
            outputs = executeSequential(input);
        }

        SupervisorResult result = fusionStrategy.fuse(outputs);
        result.setTotalTimeMs(System.currentTimeMillis() - startTime);
        result.setAgentOutputs(outputs);

        log.info("Supervisor [{}] 完成，融合得分: {}", name, result.getFusedScore());
        return result;
    }

    private Map<String, AgentOutput> executeParallel(AgentInput input) {
        Map<String, AgentOutput> outputs = new LinkedHashMap<>();
        List<CompletableFuture<Map.Entry<String, AgentOutput>>> futures = new ArrayList<>();

        for (Agent agent : subAgents) {
            CompletableFuture<Map.Entry<String, AgentOutput>> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        AgentOutput output = agent.execute(input);
                        return Map.entry(agent.getName(), output);
                    } catch (Exception e) {
                        log.error("智能体 [{}] 执行异常", agent.getName(), e);
                        AgentOutput fallback = AgentOutput.builder()
                            .agentName(agent.getName())
                            .score(50)
                            .reasoning("执行异常: " + e.getMessage())
                            .hasCriticalViolation(false)
                            .build();
                        return Map.entry(agent.getName(), fallback);
                    }
                }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                log.warn("部分智能体超时，使用降级结果");
                return null;
            }).join();

        for (CompletableFuture<Map.Entry<String, AgentOutput>> future : futures) {
            try {
                Map.Entry<String, AgentOutput> entry = future.get(1, TimeUnit.SECONDS);
                outputs.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("获取智能体结果失败", e);
                outputs.put("unknown", AgentOutput.builder()
                    .agentName("unknown")
                    .score(50)
                    .reasoning("结果获取失败")
                    .hasCriticalViolation(false)
                    .build());
            }
        }
        return outputs;
    }

    private Map<String, AgentOutput> executeSequential(AgentInput input) {
        Map<String, AgentOutput> outputs = new LinkedHashMap<>();
        for (Agent agent : subAgents) {
            try {
                outputs.put(agent.getName(), agent.execute(input));
            } catch (Exception e) {
                log.error("智能体 [{}] 执行异常", agent.getName(), e);
                outputs.put(agent.getName(), AgentOutput.builder()
                    .agentName(agent.getName())
                    .score(50)
                    .reasoning("执行异常")
                    .hasCriticalViolation(false)
                    .build());
            }
        }
        return outputs;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
