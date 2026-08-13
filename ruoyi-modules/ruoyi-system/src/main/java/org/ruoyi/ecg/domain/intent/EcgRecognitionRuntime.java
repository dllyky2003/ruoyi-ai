package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 指令识别运行时上下文（总实体/聚合根）
 * 职责：在内存中组装所有指令识别配置，提供高速查询接口
 * 生命周期：系统启动时加载 / 后台配置变更时刷新
 */
@Component
public class EcgRecognitionRuntime implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 核心索引1：以意图ID为Key的意图定义Map
     * 用于通过意图编码快速获取定义
     */
    private Map<String, EcgIntentDefinition> intentDefinitionMap;

    /**
     * 核心索引2：以阶段ID(S0/S1/...)为Key的上下文过滤规则Map
     * 用于获取当前阶段允许的意图列表
     */
    private Map<String, EcgContextFiltering> contextFilteringMap;

    /**
     * 核心索引3：以意图ID为Key的绑定槽位列表Map
     * 用于获取某个意图需要校验哪些参数
     */
    private Map<String, List<EcgIntentSlotBinding>> intentSlotBindingsMap;

    /**
     * 核心索引4：以意图ID为Key的语料列表Map
     * 用于候选意图的语义匹配（大模型/向量检索）
     */
    private Map<String, List<EcgUtterancePattern>> utterancePatternsMap;

    /**
     * 辅助索引：槽位定义Map（以slot_name为Key）
     * 用于参数校验时快速获取槽位规则（数据类型、范围、正则）
     */
    private Map<String, EcgSlotDefinition> slotDefinitionMap;

    /**
     * 辅助索引：意图同义词/关系Map（用于消歧）
     */
    private Map<String, List<EcgIntentRelationship>> intentRelationshipsMap;

    /**
     * 辅助索引：复合指令模板Map
     */
    private Map<String, EcgCompoundIntentTemplate> compoundTemplateMap;

    // ====== 构造函数与初始化 ======

    public EcgRecognitionRuntime() {
        this.intentDefinitionMap = new ConcurrentHashMap<>();
        this.contextFilteringMap = new ConcurrentHashMap<>();
        this.intentSlotBindingsMap = new ConcurrentHashMap<>();
        this.utterancePatternsMap = new ConcurrentHashMap<>();
        this.slotDefinitionMap = new ConcurrentHashMap<>();
        this.intentRelationshipsMap = new ConcurrentHashMap<>();
        this.compoundTemplateMap = new ConcurrentHashMap<>();
    }

    // ====== 核心查询方法（供路由引擎调用） ======

    /**
     * 根据阶段ID获取允许的意图列表（用于缩小大模型候选集）
     */
    public List<String> getAllowedIntentIds(String phaseId) {
        EcgContextFiltering filtering = contextFilteringMap.get(phaseId);
        if (filtering == null) return null;
        // 注意：allowedIntents字段存的是JSON数组字符串，需解析为List<String>
        return parseJsonArray(filtering.getAllowedIntents());
    }

    /**
     * 根据意图ID获取意图定义（含默认参数、必填槽位等）
     */
    public EcgIntentDefinition getIntentDefinition(String intentId) {
        return intentDefinitionMap.get(intentId);
    }

    /**
     * 根据意图ID获取该意图需要校验的槽位绑定列表
     */
    public List<EcgIntentSlotBinding> getSlotBindings(String intentId) {
        return intentSlotBindingsMap.get(intentId);
    }

    /**
     * 根据槽位名称获取槽位定义（用于参数值校验）
     */
    public EcgSlotDefinition getSlotDefinition(String slotName) {
        return slotDefinitionMap.get(slotName);
    }

    /**
     * 根据意图ID获取该意图的所有语料（用于语义匹配或Few-shot示例）
     */
    public List<EcgUtterancePattern> getUtterancePatterns(String intentId) {
        return utterancePatternsMap.get(intentId);
    }

    /**
     * 获取所有意图ID列表（用于全量刷新或调试）
     */
    public List<String> getAllIntentIds() {
        return List.copyOf(intentDefinitionMap.keySet());
    }

    // ====== Getter / Setter ======

    public Map<String, EcgIntentDefinition> getIntentDefinitionMap() {
        return intentDefinitionMap;
    }

    public void setIntentDefinitionMap(Map<String, EcgIntentDefinition> intentDefinitionMap) {
        this.intentDefinitionMap = intentDefinitionMap;
    }

    public Map<String, EcgContextFiltering> getContextFilteringMap() {
        return contextFilteringMap;
    }

    public void setContextFilteringMap(Map<String, EcgContextFiltering> contextFilteringMap) {
        this.contextFilteringMap = contextFilteringMap;
    }

    public Map<String, List<EcgIntentSlotBinding>> getIntentSlotBindingsMap() {
        return intentSlotBindingsMap;
    }

    public void setIntentSlotBindingsMap(Map<String, List<EcgIntentSlotBinding>> intentSlotBindingsMap) {
        this.intentSlotBindingsMap = intentSlotBindingsMap;
    }

    public Map<String, List<EcgUtterancePattern>> getUtterancePatternsMap() {
        return utterancePatternsMap;
    }

    public void setUtterancePatternsMap(Map<String, List<EcgUtterancePattern>> utterancePatternsMap) {
        this.utterancePatternsMap = utterancePatternsMap;
    }

    public Map<String, EcgSlotDefinition> getSlotDefinitionMap() {
        return slotDefinitionMap;
    }

    public void setSlotDefinitionMap(Map<String, EcgSlotDefinition> slotDefinitionMap) {
        this.slotDefinitionMap = slotDefinitionMap;
    }

    public Map<String, List<EcgIntentRelationship>> getIntentRelationshipsMap() {
        return intentRelationshipsMap;
    }

    public void setIntentRelationshipsMap(Map<String, List<EcgIntentRelationship>> intentRelationshipsMap) {
        this.intentRelationshipsMap = intentRelationshipsMap;
    }

    public Map<String, EcgCompoundIntentTemplate> getCompoundTemplateMap() {
        return compoundTemplateMap;
    }

    public void setCompoundTemplateMap(Map<String, EcgCompoundIntentTemplate> compoundTemplateMap) {
        this.compoundTemplateMap = compoundTemplateMap;
    }

    // ====== 辅助解析方法（实际项目中可使用JSONUtil） ======
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return List.of();
        // 伪代码：使用 cn.hutool.json.JSONUtil 或 Jackson 解析
        // return JSONUtil.toList(json, String.class);
        return List.of();
    }
}
