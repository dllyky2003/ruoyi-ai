package com.ruoyi.ems.engine.extraction;

/**
 * 结构化抽取 Prompt 模板集中管理
 *
 * 职责：
 * 1. 统一管理所有 LLM 交互的 System Prompt 和 User Prompt 模板
 * 2. 支持模板参数化（使用 %s 占位符）
 * 3. 便于后续迭代调优和版本管理
 */
public class PromptTemplates {

    // ==================== 1. 结构化抽取主模板 ====================

    /**
     * 结构化抽取 System Prompt
     * 定义角色、任务、输出格式、容错规则
     */
    public static final String EXTRACTION_SYSTEM = """
            你是ACLS急救语音分析专家。你的任务是从院前急救ASR文本中提取标准化操作事件。

            【ASR容错规则】
            1. 药物名称纠错："肝素"→"腺苷"（结合心率快、推注语境）
            2. 数字纠错："一百多"→推断为>150，"一百六"→160
            3. 忽略背景噪音：警报声、监护仪滴滴声、呼吸机气流声
            4. 若某个药物名称听不清，根据上下文（心率快、推注、冲管）自动补全为"腺苷"

            【隐含意图推理】
            - "患者叫不醒，身上湿冷" → unstable_signs: ['altered_mental','shock']
            - "血压80/40" → 识别为低血压体征
            - "摸不到脉" → pulsePresent: false

            【操作类型定义】
            - ASSESS: 评估类操作（心率、脉搏、血压、意识、QRS宽度、稳定性）
            - MONITOR: 监护类操作（建立监护、吸氧、建立静脉通路）
            - INTERVENTION: 干预类操作（迷走神经刺激、腺苷给药、电复律、抗心律失常药）
            - SAFETY: 安全检查（镇静、同步模式设置、药物冲管）
            - COMMUNICATION: 沟通类操作（指令下达、确认、报告、专家会诊）

            【输出格式】必须严格遵循以下JSON Schema，不要有任何额外文字：
            {
              "actions": [
                {
                  "seq": 1,
                  "timestamp": 5,
                  "actor": "TRAINEE",
                  "actionType": "ASSESS",
                  "detail": "heart_rate",
                  "value": "160",
                  "result": null,
                  "sourceText": "心率160"
                }
              ],
              "judgments": {
                "heartRateGt150": true,
                "pulsePresent": true,
                "unstableSigns": [],
                "qrsWidth": "NARROW",
                "rhythmRegularity": "REGULAR",
                "wpwSyndrome": false
              }
            }
            """;

    /**
     * 结构化抽取 User Prompt 模板
     * 参数：%s = ASR转写文本
     */
    public static final String EXTRACTION_USER_TEMPLATE = """
            请分析以下ASR转写的急救语音文本，提取结构化操作事件：

            【ASR文本】
            %s

            【要求】
            1. 按时间顺序提取所有临床操作事件
            2. 对模糊信息进行合理推断（遵循ASR容错规则）
            3. 严格按JSON Schema输出
            """;

    // ==================== 2. Few-shot 增强版（用于复杂场景） ====================

    /**
     * Few-shot 示例（用于提升抽取准确率）
     */
    public static final String EXTRACTION_FEW_SHOT_EXAMPLES = """
            【示例1：基础评估】
            输入："患者心率160，有脉搏，血压110/70，神志清楚"
            输出：
            {
              "actions": [
                {"seq":1, "timestamp":3, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"heart_rate", "value":"160", "sourceText":"心率160"},
                {"seq":2, "timestamp":6, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"pulse", "value":"PRESENT", "sourceText":"有脉搏"},
                {"seq":3, "timestamp":9, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"blood_pressure", "value":"110/70", "sourceText":"血压110/70"},
                {"seq":4, "timestamp":12, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"consciousness", "value":"ALERT", "sourceText":"神志清楚"}
              ],
              "judgments": {
                "heartRateGt150": true,
                "pulsePresent": true,
                "unstableSigns": [],
                "qrsWidth": "UNKNOWN",
                "rhythmRegularity": null,
                "wpwSyndrome": false
              }
            }

            【示例2：不稳定识别】
            输入："患者血压80/40，叫不醒，身上湿冷，考虑休克"
            输出：
            {
              "actions": [
                {"seq":1, "timestamp":2, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"blood_pressure", "value":"80/40", "sourceText":"血压80/40"},
                {"seq":2, "timestamp":5, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"consciousness", "value":"UNRESPONSIVE", "sourceText":"叫不醒"},
                {"seq":3, "timestamp":8, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"skin", "value":"COLD_CLAMMY", "sourceText":"身上湿冷"}
              ],
              "judgments": {
                "heartRateGt150": false,
                "pulsePresent": true,
                "unstableSigns": ["hypotension", "altered_mental", "shock"],
                "qrsWidth": "UNKNOWN",
                "rhythmRegularity": null,
                "wpwSyndrome": false
              }
            }

            【示例3：药物操作】
            输入："给6毫克腺苷快速推注，马上用盐水冲管"
            输出：
            {
              "actions": [
                {"seq":1, "timestamp":2, "actor":"TRAINEE", "actionType":"INTERVENTION",
                 "detail":"adenosine", "value":"6mg", "sourceText":"6毫克腺苷"},
                {"seq":2, "timestamp":4, "actor":"TRAINEE", "actionType":"SAFETY",
                 "detail":"flush", "value":"saline", "sourceText":"盐水冲管"}
              ],
              "judgments": {
                "heartRateGt150": false,
                "pulsePresent": null,
                "unstableSigns": null,
                "qrsWidth": null,
                "rhythmRegularity": null,
                "wpwSyndrome": false
              }
            }

            【示例4：电复律】
            输入："患者不稳定，准备同步电复律，给2毫克咪达唑仑镇静"
            输出：
            {
              "actions": [
                {"seq":1, "timestamp":2, "actor":"TRAINEE", "actionType":"ASSESS",
                 "detail":"instability", "value":"UNSTABLE", "sourceText":"患者不稳定"},
                {"seq":2, "timestamp":5, "actor":"TRAINEE", "actionType":"INTERVENTION",
                 "detail":"synchronized_cardioversion", "value":"prepared", "sourceText":"准备同步电复律"},
                {"seq":3, "timestamp":8, "actor":"TRAINEE", "actionType":"SAFETY",
                 "detail":"sedation", "value":"midazolam 2mg", "sourceText":"给2毫克咪达唑仑镇静"}
              ],
              "judgments": {
                "heartRateGt150": false,
                "pulsePresent": true,
                "unstableSigns": ["unstable"],
                "qrsWidth": "UNKNOWN",
                "rhythmRegularity": null,
                "wpwSyndrome": false
              }
            }
            """;

    /**
     * Few-shot 增强版 System Prompt
     */
    public static final String EXTRACTION_SYSTEM_WITH_FEW_SHOT =
        EXTRACTION_SYSTEM + "\n\n" + EXTRACTION_FEW_SHOT_EXAMPLES;

    // ==================== 3. 精简版（用于快速响应） ====================

    /**
     * 精简版 User Prompt（用于实时流式场景，Token 更少，响应更快）
     */
    public static final String EXTRACTION_USER_COMPACT_TEMPLATE = """
            提取以下急救语音中的操作事件（JSON格式）：
            %s
            """;

    // ==================== 4. 评测与诊断专用 ====================

    /**
     * 诊断 Prompt：分析抽取结果的置信度
     */
    public static final String DIAGNOSTIC_PROMPT = """
            你是ACLS语音抽取质量评估专家。分析以下抽取结果与原始文本的一致性。

            原始文本：%s
            抽取结果：%s

            请输出：
            {
              "confidenceScore": 85,
              "uncertainItems": ["心率值可能听错"],
              "suggestedCorrections": ["建议重新听取心率部分"]
            }
            """;

    // ==================== 5. 工具方法 ====================

    /**
     * 构建完整抽取 Prompt（含 Few-shot）
     */
    public static String buildExtractionPrompt(String transcript, boolean useFewShot) {
        String systemPrompt = useFewShot ? EXTRACTION_SYSTEM_WITH_FEW_SHOT : EXTRACTION_SYSTEM;
        String userPrompt = String.format(EXTRACTION_USER_TEMPLATE, transcript);
        return systemPrompt + "\n\n" + userPrompt;
    }

    /**
     * 构建精简抽取 Prompt
     */
    public static String buildCompactExtractionPrompt(String transcript) {
        return String.format(EXTRACTION_USER_COMPACT_TEMPLATE, transcript);
    }

    /**
     * 构建诊断 Prompt
     */
    public static String buildDiagnosticPrompt(String transcript, String extractedJson) {
        return String.format(DIAGNOSTIC_PROMPT, transcript, extractedJson);
    }

    private PromptTemplates() {
        // 工具类，禁止实例化
    }
}
