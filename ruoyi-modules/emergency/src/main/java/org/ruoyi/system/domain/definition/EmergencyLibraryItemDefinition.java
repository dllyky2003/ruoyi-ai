package org.ruoyi.system.domain.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.ruoyi.system.domain.vo.schemaInfo.ClinicalContextInfo;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 公共库条目版本定义（对应 emergency_library_item_version.definition_json）
 * 根据实际 JSON 结构重新设计
 *
 * @author ageerle
 * @date 2026-08-22
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmergencyLibraryItemDefinition {

    @JsonProperty("basic")
    private BasicInfo basic;

    @JsonProperty("clinical")
    private ClinicalInfo clinical;

    @JsonProperty("physical")
    private PhysicalExam physical;

    @JsonProperty("medication")
    private MedicationInfo medication;

    @JsonProperty("interview")
    private InterviewInfo interview;

    @JsonProperty("resources")
    private ResourcesInfo resources;

    @JsonProperty("stages")
    private StagesInfo stages;

    @JsonProperty("validation")
    private Object validation; // 当前为空对象

    // ==================== 内部类 ====================

    @Data
    public static class BasicInfo {
        private String id;
        private String name;
        private String workflowType;
        private String mode;
        private String tags;
        private String sourceGuideline;
        private String sourceVersion;
        private String status;
        private Integer versionNo;          // 改为 Integer，与 JSON 匹配
        private String description;
        private String learningObjectives;
        private String difficultyLevel;
        private Integer estimatedDurationMinutes;  // 或 Double
        private String sex;
        private Integer age;
        private String sopRef;
        private String guidelineVersion;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClinicalInfo {
        @JsonProperty("clinicalTopic")
        private ClinicalTopic clinicalTopic;

        @JsonProperty("initialState")
        private InitialState initialState;

        @JsonProperty("monitors")
        private List<Monitor> monitors;

        @JsonProperty("labResults")
        private List<LabResult> labResults;



        /**
         * 生成用于 AI 重点查体（心肺血管）配置页面的精简背景信息
         * @return 结构化描述字符串，若无相关数据则返回空字符串
         */
        public String generateCardioPulmonaryContext() {
            StringBuilder sb = new StringBuilder();

            // ========== 1. 初始状态（呼吸、循环、心电图） ==========
            if (initialState != null) {
                if (StringUtils.hasText(initialState.getBreathing())) {
                    sb.append("呼吸状态: ").append(initialState.getBreathing()).append("; ");
                }
                if (StringUtils.hasText(initialState.getCirculation())) {
                    sb.append("循环状态: ").append(initialState.getCirculation()).append("; ");
                }
                if (StringUtils.hasText(initialState.getEcg())) {
                    sb.append("心电图: ").append(initialState.getEcg()).append("; ");
                }
            }

            // ========== 2. 生命体征（心率、血氧、呼吸频率） ==========
            if (monitors != null) {
                for (Monitor monitor : monitors) {
                    String key = monitor.getKey();
                    String item = monitor.getItem();
                    String identifier = (key != null) ? key : item;
                    if (identifier == null) continue;

                    String lowerId = identifier.toLowerCase();
                    String value = monitor.getValue();
                    String unit = monitor.getUnit() != null ? monitor.getUnit() : "";
                    String valueStr = (value != null) ? value + (unit.isEmpty() ? "" : " " + unit) : "";

                    if (lowerId.contains("heart") || lowerId.contains("hr") || lowerId.contains("pulse")) {
                        sb.append("心率: ").append(valueStr).append("; ");
                    } else if (lowerId.contains("spo2") || lowerId.contains("oxygen") || lowerId.contains("o2")) {
                        sb.append("血氧饱和度: ").append(valueStr).append("; ");
                    } else if (lowerId.contains("resp") || lowerId.contains("rr")) {
                        sb.append("呼吸频率: ").append(valueStr).append("; ");
                    }
                    // 血压因字段类型限制暂不处理（可后续扩展）
                }
            }

            // ========== 3. 实验室检查（心肌标志物） ==========
            if (labResults != null) {
                for (LabResult lab : labResults) {
                    String name = lab.getName();
                    if (name == null) continue;
                    String lowerName = name.toLowerCase();
                    String valStr = (lab.getValue() != null) ? lab.getValue() + (lab.getUnit() != null ? " " + lab.getUnit() : "") : "";
                    String flag = (lab.getFlag() != null && !lab.getFlag().isEmpty()) ? " (" + lab.getFlag() + ")" : "";

                    if (lowerName.contains("troponin") || lowerName.contains("cni")) {
                        sb.append("肌钙蛋白: ").append(valStr).append(flag).append("; ");
                    } else if (lowerName.contains("bnp") || lowerName.contains("probnp")) {
                        sb.append("BNP/NT-proBNP: ").append(valStr).append(flag).append("; ");
                    }
                }
            }

            if (sb.length() == 0) {
                return "";
            }
            String result = sb.toString();
            if (result.endsWith("; ")) {
                result = result.substring(0, result.length() - 2);
            }
            return "【心肺血管重点信息】" + result;
        }



        @Data
        public static class ClinicalTopic {
            @JsonProperty("chiefComplaint")
            private String chiefComplaint;

            private String background;
            private String scene;



        }

        @Data
        public static class InitialState {
            private String consciousness;
            private String airway;
            private String breathing;
            private String circulation;
            private String ecg;
            private List<String> appearance;
        }

        @Data
        public static class Monitor {
            private String key;
            private String item;
            private String value;
            private String unit;
            private String hint;
            private Boolean locked;
        }

        @Data
        public static class LabResult {
            private String id;
            private String category;
            private String name;
            private String value;
            private String unit;
            @JsonProperty("referenceRange")
            private String referenceRange;
            private String flag;
            private String visibility;
            @JsonProperty("delaySeconds")
            private Integer delaySeconds;
            @JsonProperty("releaseCondition")
            private String releaseCondition;
            private String note;
            private Boolean locked;
        }
    }

    @Data
    public static class PhysicalExam {
        @JsonProperty("vascular")
        private Vascular vascular;

        @JsonProperty("respiratory")
        private Respiratory respiratory;

        @JsonProperty("neuro")
        private Neuro neuro;

        @JsonProperty("abdominal")
        private Abdominal abdominal;

        /**
         * 生成供AI使用的精简体格检查背景（仅心肺血管关键体征）
         * @return 精简描述字符串，无数据时返回空字符串
         */
        public String generateMedicationBackground() {
            StringBuilder sb = new StringBuilder();

            // ----- 1. 血压（取任一臂有值即可，简化显示） -----
            if (vascular != null) {
                String bp = null;
                if (StringUtils.hasText(vascular.getRightArmBP())) {
                    bp = vascular.getRightArmBP();
                } else if (StringUtils.hasText(vascular.getLeftArmBP())) {
                    bp = vascular.getLeftArmBP();
                }
                if (bp != null) {
                    sb.append("血压: ").append(bp).append("; ");
                }

                // ----- 2. 颈静脉怒张（JVD）-----
                if (StringUtils.hasText(vascular.getJvd())) {
                    sb.append("颈静脉怒张: ").append(vascular.getJvd()).append("; ");
                }

                // ----- 3. 毛细血管充盈时间（CRT）-----
                if (StringUtils.hasText(vascular.getCrt())) {
                    sb.append("CRT: ").append(vascular.getCrt()).append("; ");
                }
//
//                // ----- 4. 外周动脉搏动（只报告异常或关键部位）-----
//                // 足背动脉
//                if (StringUtils.hasText(vascular.getDorsalisPedisPulse())) {
//                    String pulse = vascular.getDorsalisPedisPulse();
//                    if (!"正常".equals(pulse) && !"可及".equals(pulse)) {
//                        sb.append("足背动脉搏动: ").append(pulse).append("; ");
//                    }
//                }
//                // 股动脉（大腿）
//                if (StringUtils.hasText(vascular.getThighArteryPulse())) {
//                    String pulse = vascular.getThighArteryPulse();
//                    if (!"正常".equals(pulse) && !"可及".equals(pulse)) {
//                        sb.append("股动脉搏动: ").append(pulse).append("; ");
//                    }
//                }
                // 股动脉（腹股沟，注意与上面区分，但数据结构相同，取其一即可，此处略重复）
                // 若需要可再添加femoralPulse，但可能与thighArteryPulse重复，根据实际数据决定，这里简化只取thighArteryPulse
            }

            // ----- 5. 呼吸系统（肺部听诊、辅助肌） -----
            if (respiratory != null) {
                // 肺底听诊（只报告异常，如湿啰音、哮鸣音等）
                if (StringUtils.hasText(respiratory.getLungBase())) {
                    String base = respiratory.getLungBase();
                    if (!"清晰".equals(base) && !"正常".equals(base)) {
                        sb.append("肺底: ").append(base).append("; ");
                    }
                }
                // 肺尖听诊（只报告异常）
                if (StringUtils.hasText(respiratory.getLungApex())) {
                    String apex = respiratory.getLungApex();
                    if (!"清晰".equals(apex) && !"正常".equals(apex)) {
                        sb.append("肺尖: ").append(apex).append("; ");
                    }
                }
                // 辅助呼吸肌使用（阳性才报告）
                if (StringUtils.hasText(respiratory.getAccessoryMuscles())) {
                    if ("是".equals(respiratory.getAccessoryMuscles()) || "有".equals(respiratory.getAccessoryMuscles())) {
                        sb.append("使用辅助呼吸肌; ");
                    }
                }
            }

            // 注意：本方法不再包含神经和腹部检查，因为它们对心肺血管问诊配置非必需

            if (sb.length() == 0) {
                return "";
            }
            String result = sb.toString();
            if (result.endsWith("; ")) {
                result = result.substring(0, result.length() - 2);
            }
            return "【重点体格检查】" + result;
        }
        // ---------- 内部类 ----------


        @Data
        public static class Vascular {
            @JsonProperty("rightArmBP")
            private String rightArmBP;

            @JsonProperty("leftArmBP")
            private String leftArmBP;

            private String jvd;

            @JsonProperty("femoralPulse")
            private String femoralPulse;

            @JsonProperty("dorsalisPedisPulse")
            private String dorsalisPedisPulse;

            private String crt;
        }

        @Data
        public static class Respiratory {
            @JsonProperty("lungBase")
            private String lungBase;

            @JsonProperty("lungApex")
            private String lungApex;

            @JsonProperty("accessoryMuscles")
            private String accessoryMuscles;
        }

        @Data
        public static class Neuro {
            private String pupils;
            private String faceSymmetry;
            @JsonProperty("gcsE")
            private Integer gcsE;
            @JsonProperty("gcsV")
            private Integer gcsV;
            @JsonProperty("gcsM")
            private Integer gcsM;
        }

        @Data
        public static class Abdominal {
            private String abdomen;
            @JsonProperty("traumaLocation")
            private String traumaLocation;
        }
    }

    @Data
    public static class MedicationInfo {
        @JsonProperty("allergy")
        private Allergy allergy;

        @JsonProperty("homeMed")
        private HomeMed homeMed;

        @JsonProperty("tempMed")
        private TempMed tempMed;

        @JsonProperty("history")
        private History history;

        /**
         * 生成供 AI 使用的用药与过敏史精简描述（仅含关键决策信息）
         * @return 精简描述字符串
         */
        public String generateDiagnosisBackground() {
            StringBuilder sb = new StringBuilder();

            // 1. 过敏史：只取有无和药物类别（如果“无”则忽略）
            if (allergy != null && "有".equals(allergy.getHasAllergy())) {
                sb.append("过敏史: 有");
                if (StringUtils.hasText(allergy.getDrugCategory())) {
                    sb.append("(").append(allergy.getDrugCategory()).append(")");
                }
                sb.append("; ");
            }

            // 2. 长期用药：只取抗凝、抗血小板、PDE5这三个关键分类（如果“是”则记录）
            if (homeMed != null) {
                if ("是".equals(homeMed.getAnticoagulant())) {
                    sb.append("服用抗凝药; ");
                }
                if ("是".equals(homeMed.getAntiplatelet())) {
                    sb.append("服用抗血小板药; ");
                }
                if ("是".equals(homeMed.getPde5())) {
                    sb.append("服用PDE5抑制剂; ");
                }
                // 长期用药列表：可以简化为“长期服用药物：xxx”但为了精简，只列上述三类，因为这三类对决策最重要
                // 若仍想列出具体药名，可保留列表，但可能过多，建议只取关键类别。
                // 但为了更全面，可以列出非上述类别的药名，但问题要求精简，所以我选择只输出上述三类。
                // 也可以不输出列表，由决策模块自行判断。
            }

            // 3. 临时用药：只取硝酸甘油给予情况（及其剂量）
            if (tempMed != null && "是".equals(tempMed.getNitroGiven())) {
                sb.append("已给予硝酸甘油");
                if (StringUtils.hasText(tempMed.getNitroDose())) {
                    sb.append("(剂量:").append(tempMed.getNitroDose()).append(")");
                }
                sb.append("; ");
            }

            // 4. 既往病史：只保留高血压（含年数）、糖尿病、肾脏病、消化道出血（这些都是影响用药的关键）
            if (history != null) {
                if (StringUtils.hasText(history.getHypertension()) && !"无".equals(history.getHypertension())) {
                    sb.append("高血压病史");
                    if (history.getHypertensionYears() > 0) {
                        sb.append("(").append(history.getHypertensionYears()).append("年)");
                    }
                    sb.append("; ");
                }
                if (StringUtils.hasText(history.getDiabetes()) && !"无".equals(history.getDiabetes())) {
                    sb.append("糖尿病病史; ");
                }
                if (StringUtils.hasText(history.getRenal()) && !"无".equals(history.getRenal())) {
                    sb.append("肾脏病史; ");
                }
                if (StringUtils.hasText(history.getGiBleed()) && !"无".equals(history.getGiBleed())) {
                    sb.append("消化道出血史; ");
                }
            }

            if (sb.length() == 0) {
                return "";
            }
            String result = sb.toString();
            if (result.endsWith("; ")) {
                result = result.substring(0, result.length() - 2);
            }
            return "【关键用药与过敏背景】" + result;
        }



        @Data
        public static class Allergy {
            @JsonProperty("hasAllergy")
            private String hasAllergy;

            @JsonProperty("drugCategory")
            private String drugCategory;

            private String severity;
        }

        @Data
        public static class HomeMed {
            @JsonProperty("longTerm")
            private List<String> longTerm;

            private String anticoagulant;
            private String antiplatelet;
            private String pde5;
        }

        @Data
        public static class TempMed {
            private String sedative;
            @JsonProperty("nitroGiven")
            private String nitroGiven;
            @JsonProperty("nitroDose")
            private String nitroDose;
        }

        @Data
        public static class History {
            private String hypertension;
            @JsonProperty("hypertensionYears")
            private Integer hypertensionYears;
            private String diabetes;
            private String renal;
            @JsonProperty("giBleed")
            private String giBleed;
        }
    }

    @Data
    public static class InterviewInfo {
        @JsonProperty("case_type_id")
        private String caseTypeId;

        @JsonProperty("case_tag")
        private String caseTag;

        @JsonProperty("messages")
        private List<Message> messages;

        @Data
        public static class Message {
            @JsonProperty("turn_index")
            private Integer turnIndex;

            @JsonProperty("speaker_role")
            private String speakerRole;

            private String delegation;

            @JsonProperty("simulator_role")
            private String simulatorRole;

            @JsonProperty("question_q_id")
            private String questionQId;

            @JsonProperty("time_window")
            private String timeWindow;

            @JsonProperty("parallel_operation")
            private String parallelOperation;

            @JsonProperty("question_text")
            private String questionText;

            @JsonProperty("model_answer")
            private String modelAnswer;

            @JsonProperty("keywords_hit")
            private List<String> keywordsHit;

            @JsonProperty("keywords_miss")
            private List<String> keywordsMiss;

            @JsonProperty("medical_consistency_flag")
            private Boolean medicalConsistencyFlag;

            private Integer score;
        }
    }

    @Data
    public static class ResourcesInfo {
        @JsonProperty("resources")
        private List<Resource> resources;

        @Data
        public static class Resource {
            private String id;
            private String kind;
            private String name;
            private String specification;
            @JsonProperty("initialStatus")
            private String initialStatus;
            private Integer quantity;
            private String unit;
            private List<String> prerequisites;
            @JsonProperty("usageLimit")
            private List<String> usageLimit;
            private Boolean locked;
        }
    }

    @Data
    public static class StagesInfo {
        @JsonProperty("phases")
        private List<Phase> phases;

        @JsonProperty("settings")
        private StageSettings settings;

        @Data
        public static class Phase {
            private String id;
            private String name;
            private Integer sequence;
            private String type;
            @JsonProperty("entryRuleId")
            private String entryRuleId;
            @JsonProperty("entryCondition")
            private String entryCondition;
            @JsonProperty("exitRuleId")
            private String exitRuleId;
            @JsonProperty("exitCondition")
            private String exitCondition;
            @JsonProperty("transitionType")
            private String transitionType;
            @JsonProperty("transitionTargetId")
            private String transitionTargetId;
            @JsonProperty("activeTrackIds")
            private List<String> activeTrackIds;
            @JsonProperty("maxDurationSec")
            private Integer maxDurationSec;
            @JsonProperty("nominalDurationSec")
            private Integer nominalDurationSec;
            private String description;
            private List<PhaseRule> rules;
            @JsonProperty("isExpanded")
            private Boolean isExpanded;
            @JsonProperty("isLocked")
            private Boolean isLocked;

            @Data
            public static class PhaseRule {
                private String id;
                private String name;
                private String type;
                private String condition;
                private String consequence;
                @JsonProperty("durationSec")
                private Integer durationSec;
                @JsonProperty("scoreChange")
                private Integer scoreChange;
            }
        }

        @Data
        public static class StageSettings {
            @JsonProperty("cycleSeconds")
            private Integer cycleSeconds;

            @JsonProperty("maxCycles")
            private Integer maxCycles;
        }
    }
}
