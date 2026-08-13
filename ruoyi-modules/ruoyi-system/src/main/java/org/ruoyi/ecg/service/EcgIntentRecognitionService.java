package org.ruoyi.ecg.service;

import org.ruoyi.ecg.domain.intent.*;
import org.ruoyi.ecg.service.vector.VectorSearchService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 意图识别引擎（整合向量检索 + 规则引擎）
 */
@Service
public class EcgIntentRecognitionService {

    @Resource
    private EcgRecognitionRuntime runtime;

    @Resource
    private VectorSearchService vectorSearchService;

    @Resource
    private org.ruoyi.ecg.service.EcgSlotValidator slotValidator;

    /**
     * 识别用户输入
     */
 public RecognitionResult recognize(String userInput, String phaseId) {
    // ===== 第一步：向量检索 =====
    List<CandidateIntent> candidates = vectorSearchService.search(
        userInput,
        runtime.getUtterancePatternsMap(),
        runtime.getIntentDefinitionMap()
    );

    // ===== 第二步：上下文过滤 =====
    candidates = filterByContext(candidates, phaseId);

    // ===== 第三步：意图关系消歧 =====
    candidates = disambiguate(candidates);

    if (candidates.isEmpty()) {
        return RecognitionResult.unknown("未识别到匹配的意图");
    }

    // ===== 第四步：获取最佳意图 =====
    CandidateIntent best = candidates.get(0);
    EcgIntentDefinition intent = best.getIntentDefinition();

    // ===== 新增：空值检查 =====
    if (intent == null) {
        System.err.println("❌ 意图定义为空，intentId: " + best.getIntentId());
        // 创建临时意图定义
        intent = new EcgIntentDefinition();
        intent.setIntentId(best.getIntentId());
        intent.setDescription(best.getIntentId());
    }

    // ===== 第五步：槽位校验 =====
    SlotValidationResult slotResult = slotValidator.validate(
        intent.getIntentId(),
        userInput
    );

    // ===== 第六步：推荐后续意图 =====
    List<String> suggestedNext = getSuggestedNext(intent.getIntentId());

    return RecognitionResult.success(
        intent,
        best.getScore(),
        slotResult.getExtractedParams(),
        slotResult.getMissingSlots(),
        suggestedNext
    );
}
    /**
     * 上下文过滤
     */
    private List<CandidateIntent> filterByContext(
            List<CandidateIntent> candidates,
            String phaseId) {
        if (phaseId == null) return candidates;

        EcgContextFiltering context = runtime.getContextFilteringMap().get(phaseId);
        if (context == null) return candidates;

        List<String> allowedIntents = runtime.getAllowedIntentIds(phaseId);
        if (allowedIntents == null || allowedIntents.isEmpty()) {
            return candidates;
        }

        Set<String> allowedSet = new HashSet<>(allowedIntents);
        return candidates.stream()
            .filter(c -> allowedSet.contains(c.getIntentId()))
            .collect(Collectors.toList());
    }

    /**
     * 意图关系消歧
     */
    private List<CandidateIntent> disambiguate(List<CandidateIntent> candidates) {
        if (candidates.size() <= 1) return candidates;

        // 处理同义词：保留优先级高的
        Map<String, List<CandidateIntent>> synonymGroups = groupSynonyms(candidates);
        List<CandidateIntent> result = new ArrayList<>();

        for (List<CandidateIntent> group : synonymGroups.values()) {
            group.sort((a, b) -> {
                int p1 = a.getIntentDefinition().getPriority() != null ?
                         a.getIntentDefinition().getPriority() : 0;
                int p2 = b.getIntentDefinition().getPriority() != null ?
                         b.getIntentDefinition().getPriority() : 0;
                return Integer.compare(p2, p1);
            });
            result.add(group.get(0));
        }

        // 处理互斥：排除冲突意图
        result = removeExclusiveIntents(result);

        return result;
    }

    /**
     * 分组同义词
     */
    private Map<String, List<CandidateIntent>> groupSynonyms(List<CandidateIntent> candidates) {
        Map<String, List<CandidateIntent>> groups = new HashMap<>();
        Set<String> processed = new HashSet<>();

        for (CandidateIntent c : candidates) {
            if (processed.contains(c.getIntentId())) continue;

            List<CandidateIntent> group = new ArrayList<>();
            group.add(c);
            processed.add(c.getIntentId());

            // 查找同义词
            List<EcgIntentRelationship> relations =
                runtime.getIntentRelationshipsMap().get(c.getIntentId());
            if (relations != null) {
                for (EcgIntentRelationship rel : relations) {
                    if ("synonym".equals(rel.getRelationshipType())) {
                        String targetId = rel.getTargetIntentId();
                        if (!processed.contains(targetId)) {
                            // 在 candidates 中查找对应的候选
                            for (CandidateIntent other : candidates) {
                                if (targetId.equals(other.getIntentId())) {
                                    group.add(other);
                                    processed.add(targetId);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            groups.put(c.getIntentId(), group);
        }

        return groups;
    }

    /**
     * 移除互斥意图
     */
    private List<CandidateIntent> removeExclusiveIntents(List<CandidateIntent> candidates) {
        if (candidates.size() <= 1) return candidates;

        List<CandidateIntent> result = new ArrayList<>(candidates);

        for (CandidateIntent c1 : candidates) {
            List<EcgIntentRelationship> relations =
                runtime.getIntentRelationshipsMap().get(c1.getIntentId());
            if (relations != null) {
                for (EcgIntentRelationship rel : relations) {
                    if ("excludes".equals(rel.getRelationshipType())) {
                        String targetId = rel.getTargetIntentId();
                        result.removeIf(c -> targetId.equals(c.getIntentId())
                                         && !c.getIntentId().equals(c1.getIntentId()));
                    }
                }
            }
        }

        return result;
    }

    /**
     * 获取推荐后续意图
     */
    private List<String> getSuggestedNext(String intentId) {
        List<EcgIntentRelationship> relations =
            runtime.getIntentRelationshipsMap().get(intentId);
        if (relations == null) return new ArrayList<>();

        return relations.stream()
            .filter(r -> "suggests".equals(r.getRelationshipType())
                      || "sequential".equals(r.getRelationshipType()))
            .map(EcgIntentRelationship::getTargetIntentId)
            .collect(Collectors.toList());
    }
}
