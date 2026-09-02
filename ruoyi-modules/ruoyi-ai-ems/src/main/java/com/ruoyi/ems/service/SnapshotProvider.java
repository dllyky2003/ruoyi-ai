package com.ruoyi.ems.service;


import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;

import com.ruoyi.ems.domain.ContextSnapshot;
import com.ruoyi.ems.engine.StateMachineFacade;
import com.ruoyi.ems.engine.RuleMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SnapshotProvider {

    @Autowired
    private EmsContextManager contextManager;

    @Autowired
    private YamlStateMachineLoader configLoader;

    @Autowired
    private RuleMatcher ruleMatcher;

    @Autowired
    private StateMachineFacade stateMachineFacade;

    /**
     * 生成分类器所需的上下文快照
     */
    public ContextSnapshot getSnapshot(String caseInstanceId) {
        CaseContext context = contextManager.getOrCreate(caseInstanceId);
        String caseType = context.getCaseType();

        ResolvedCaseTypeConfig config = configLoader.getConfig(caseType);
        String currentState = context.getCurrentState();

        if (config == null) {
            return ContextSnapshot.builder()
                .currentState(currentState != null ? currentState : "UNKNOWN")
                .caseType(caseType != null ? caseType : "UNKNOWN")
                .allowedIntents(Collections.emptyList())
                .keyVariables(Collections.emptyMap())
                .build();
        }

        // 获取当前状态下可用的意图列表
        List<String> availableIntents = new ArrayList<>();
        if (currentState != null) {
            availableIntents = config.getTransitions().stream()
                .filter(r -> r.getSource().equals(currentState))
                .map(TransitionRule::getIntentCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        }

        // 提取关键变量（仅 LLM 分类需要的核心变量）
        Map<String, Object> keyVars = extractKeyVariables(context);

        return ContextSnapshot.builder()
            .currentState(currentState != null ? currentState : config.getInitialState())
            .caseType(caseType)
            .allowedIntents(availableIntents)
            .keyVariables(keyVars)
            .build();
    }

    /**
     * 提取关键变量（仅分类器必需的）
     */
    private Map<String, Object> extractKeyVariables(CaseContext context) {
        Map<String, Object> keyVars = new LinkedHashMap<>();

        // 流程标记
        keyVars.put("adenosine_dose_count",
            context.getVar("adenosine_dose_count", Integer.class));
        keyVars.put("vagal_attempted",
            context.getVar("vagal_attempted", Boolean.class));

        // 生理数据
        keyVars.put("current_rhythm",
            context.getVar("current_rhythm", String.class));
        keyVars.put("systolic_bp",
            context.getVar("systolic_bp", Integer.class));
        keyVars.put("consciousness",
            context.getVar("consciousness", String.class));

        // 学员陈述
        keyVars.put("asserted_stable",
            context.getVar("asserted_stable", Boolean.class));
        keyVars.put("asserted_qrs_width",
            context.getVar("asserted_qrs_width", String.class));

        return keyVars;
    }
}
