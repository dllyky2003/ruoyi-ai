package org.ruoyi.ecg.service.impl;

import org.ruoyi.ecg.domain.intent.EcgRecognitionRuntime;
import org.ruoyi.ecg.domain.intent.*;
import org.ruoyi.ecg.mapper.intent.*;
import org.ruoyi.ecg.service.vector.VectorSearchService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.ruoyi.ecg.mapper.intent.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EcgRuntimeBuilder {

    @Resource
    private EcgIntentDefinitionMapper intentDefinitionMapper;

    @Resource
    private EcgSlotDefinitionMapper slotDefinitionMapper;

    @Resource
    private EcgIntentSlotBindingMapper intentSlotBindingMapper;

    @Resource
    private EcgUtterancePatternMapper utterancePatternMapper;

    @Resource
    private EcgUtteranceVariantMapper utteranceVariantMapper;

    @Resource
    private EcgContextFilteringMapper contextFilteringMapper;

    @Resource
    private EcgIntentRelationshipMapper intentRelationshipMapper;

    @Resource
    private EcgCompoundIntentTemplateMapper compoundIntentTemplateMapper;

    @Resource
    private EcgRecognitionRuntime runtime;

    @Resource
    private VectorSearchService vectorSearchService;

    @PostConstruct
    public void init() {
       // refresh();
    }

    public void refresh() {
        long startTime = System.currentTimeMillis();
        System.out.println("🔄 开始刷新指令识别总实体...");

        // 1. 加载意图定义
        List<EcgIntentDefinition> intentList = intentDefinitionMapper.selectEcgIntentDefinitionList(null);
        runtime.setIntentDefinitionMap(
            intentList.stream().collect(Collectors.toMap(
                EcgIntentDefinition::getIntentId,
                item -> item
            ))
        );
        System.out.println("  ✅ 加载意图定义: " + intentList.size() + " 条");

        // 2. 加载槽位定义
        List<EcgSlotDefinition> slotList = slotDefinitionMapper.selectEcgSlotDefinitionList(null);
        runtime.setSlotDefinitionMap(
            slotList.stream().collect(Collectors.toMap(
                EcgSlotDefinition::getSlotName,
                item -> item
            ))
        );
        System.out.println("  ✅ 加载槽位定义: " + slotList.size() + " 条");

        // 3. 加载槽位绑定
        List<EcgIntentSlotBinding> bindingList = intentSlotBindingMapper.selectEcgIntentSlotBindingList(null);
        runtime.setIntentSlotBindingsMap(
            bindingList.stream().collect(Collectors.groupingBy(
                EcgIntentSlotBinding::getIntentId
            ))
        );
        System.out.println("  ✅ 加载槽位绑定: " + bindingList.size() + " 条");

        // 4. 加载语料
        List<EcgUtterancePattern> utteranceList = utterancePatternMapper.selectEcgUtterancePatternList(null);
        System.out.println("  📝 从数据库查询到语料: " + utteranceList.size() + " 条");

        runtime.setUtterancePatternsMap(
            utteranceList.stream().collect(Collectors.groupingBy(
                EcgUtterancePattern::getIntentId
            ))
        );
        System.out.println("  ✅ 加载语料: " + utteranceList.size() + " 条");

        // 5. 加载语料变体
        List<EcgUtteranceVariant> variantList = utteranceVariantMapper.selectEcgUtteranceVariantList(null);
        System.out.println("  ✅ 加载语料变体: " + variantList.size() + " 条");

        // 6. 加载上下文过滤
        List<EcgContextFiltering> contextList = contextFilteringMapper.selectEcgContextFilteringList(null);
        runtime.setContextFilteringMap(
            contextList.stream().collect(Collectors.toMap(
                EcgContextFiltering::getPhaseId,
                item -> item
            ))
        );
        System.out.println("  ✅ 加载上下文过滤: " + contextList.size() + " 个阶段");

        // 7. 加载意图关系
        List<EcgIntentRelationship> relationshipList = intentRelationshipMapper.selectEcgIntentRelationshipList(null);
        runtime.setIntentRelationshipsMap(
            relationshipList.stream().collect(Collectors.groupingBy(
                EcgIntentRelationship::getSourceIntentId
            ))
        );
        System.out.println("  ✅ 加载意图关系: " + relationshipList.size() + " 条");

        // 8. 加载复合指令模板
        List<EcgCompoundIntentTemplate> templateList = compoundIntentTemplateMapper.selectEcgCompoundIntentTemplateList(null);
        runtime.setCompoundTemplateMap(
            templateList.stream().collect(Collectors.toMap(
                EcgCompoundIntentTemplate::getTemplateId,
                item -> item
            ))
        );
        System.out.println("  ✅ 加载复合指令模板: " + templateList.size() + " 条");

        // 9. 初始化向量索引
        System.out.println("  📝 开始初始化向量索引，语料数量: " + runtime.getUtterancePatternsMap().size() + " 个意图");
        vectorSearchService.initCorpus(runtime.getUtterancePatternsMap());
        System.out.println("  ✅ 向量检索服务初始化完成，共索引 " + vectorSearchService.getCorpusSize() + " 条语料");

        System.out.println("✅ 总实体刷新完成，耗时: " + (System.currentTimeMillis() - startTime) + " ms");
    }

    /**
     * 获取运行时上下文
     */
    public EcgRecognitionRuntime getRuntime() {
        return runtime;
    }
}
