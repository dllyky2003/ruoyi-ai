package org.ruoyi.system.domain;

import lombok.Data;
import org.ruoyi.system.utils.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Data
@Configuration
@PropertySource(value = "classpath:commonOptions.yml", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "system-common-options") // 假设根键是 physical-exam
public class CommonOptions {
    private List<String> jvdOptions;
    private List<String> crtOptions;
    private List<String> lungOptions;
    private List<String> lungApexOptions;
    private List<String> yesNoOptions;
    private List<String> pupilOptions;
    private List<String> faceOptions;
    private List<String> abdomenOptions;
    private List<String> dorsalisPedisPulseType; // 注意：yaml 中是 dorsalis_pedis_pulse_type，但 Java 是驼峰
    private List<String> thighArteryPulseType;

    private MedicationHistory medicationHistory;

    @Data
    public static class MedicationHistory {
        private List<String> homeMedLongTermOptions;
        private List<String> allergyHasAllergyOptions;
        private List<String> allergyDrugCategoryOptions;
        private List<String> allergySeverityOptions;
        private List<String> homeMedAntiplateletOptions;
        private List<String> homeMedAnticoagulantOptions;
        private List<String> homeMedPde5Options;
        private List<String> tempMedSedativeOptions;
        private List<String> tempMedNitroGivenOptions;
        private List<String> tempMedNitroDoseOptions;
        private List<String> historyHypertensionOptions;
        private List<Integer> historyHypertensionYearsOptions; // 整数列表
        private List<String> historyDiabetesOptions;
        private List<String> historyRenalOptions;
        private List<String> historyGiBleedOptions;
    }
}
