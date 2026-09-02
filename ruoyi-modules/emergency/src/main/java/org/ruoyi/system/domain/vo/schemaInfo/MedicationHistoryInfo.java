package org.ruoyi.system.domain.vo.schemaInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicationHistoryInfo {

    private Allergy allergy;
    private HomeMed homeMed;
    private TempMed tempMed;
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Allergy {
        private String hasAllergy;      // "无" 或 "有"
        private String drugCategory;    // 药物类别，如 "青霉素"
        private String severity;        // 严重程度
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeMed {
        private List<String> longTerm;  // 长期服用药物列表
        private String anticoagulant;   // 抗凝药服用情况
        private String antiplatelet;    // 抗血小板药服用情况
        private String pde5;            // PDE5抑制剂服用情况
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TempMed {
        private String sedative;        // 镇静药服用情况
        private String nitroGiven;      // 是否给予硝酸甘油
        private String nitroDose;       // 硝酸甘油剂量（若给予）
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class History {
        private String hypertension;    // 高血压病史
        private int hypertensionYears;  // 高血压年数（0表示无）
        private String diabetes;       // 糖尿病病史
        private String renal;          // 肾脏病史
        private String giBleed;        // 消化道出血史
    }
}
