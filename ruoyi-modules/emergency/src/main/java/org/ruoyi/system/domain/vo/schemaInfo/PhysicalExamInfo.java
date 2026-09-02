package org.ruoyi.system.domain.vo.schemaInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhysicalExamInfo {

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

            // ----- 4. 外周动脉搏动（只报告异常或关键部位）-----
            // 足背动脉
            if (StringUtils.hasText(vascular.getDorsalisPedisPulse())) {
                String pulse = vascular.getDorsalisPedisPulse();
                if (!"正常".equals(pulse) && !"可及".equals(pulse)) {
                    sb.append("足背动脉搏动: ").append(pulse).append("; ");
                }
            }
            // 股动脉（大腿）
            if (StringUtils.hasText(vascular.getThighArteryPulse())) {
                String pulse = vascular.getThighArteryPulse();
                if (!"正常".equals(pulse) && !"可及".equals(pulse)) {
                    sb.append("股动脉搏动: ").append(pulse).append("; ");
                }
            }
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vascular {
        @JsonProperty("rightArmBP")
        private String rightArmBP;

        @JsonProperty("leftArmBP")
        private String leftArmBP;

        @JsonProperty("jvd")
        private String jvd;

        @JsonProperty("femoralPulse")
        private String femoralPulse;

        @JsonProperty("dorsalisPedisPulse")
        private String dorsalisPedisPulse;

        @JsonProperty("thighArteryPulse")
        private String thighArteryPulse;

        @JsonProperty("crt")
        private String crt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Respiratory {
        @JsonProperty("lungBase")
        private String lungBase;

        @JsonProperty("lungApex")
        private String lungApex;

        @JsonProperty("accessoryMuscles")
        private String accessoryMuscles;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Neuro {
        @JsonProperty("pupils")
        private String pupils;

        @JsonProperty("faceSymmetry")
        private String faceSymmetry;

        @JsonProperty("gcsE")
        private String gcsE;    // 若需数字可改为 Integer，但示例为字符串

        @JsonProperty("gcsV")
        private String gcsV;

        @JsonProperty("gcsM")
        private String gcsM;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Abdominal {
        @JsonProperty("abdomen")
        private String abdomen;

        @JsonProperty("traumaLocation")
        private String traumaLocation;
    }
}
