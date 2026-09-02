package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.intent.classifier.FallbackClassifier;
import com.ruoyi.ems.intent.classifier.OneStageClassifier;
import com.ruoyi.ems.intent.classifier.TwoStageClassifier;
import com.ruoyi.ems.intent.config.IntentClassifierProperties;
import com.ruoyi.ems.intent.model.IntentCandidate;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.intent.service.IntentClassificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntentClassificationServiceTest {

    @Mock
    private OneStageClassifier oneStageClassifier;

    @Mock
    private TwoStageClassifier twoStageClassifier;

    @Mock
    private FallbackClassifier fallbackClassifier;

    @Mock
    private IntentClassifierProperties properties;

    @Mock
    private CaseIntentDefinitionsLoader intentRegistry;

    @InjectMocks
    private IntentClassificationService service;

    private static final String SAMPLE_TEXT = "R-R间期规不规则？心律是规则的还是乱的？";

    @BeforeEach
    void setUp() {
        lenient().when(properties.getMode()).thenReturn("one-stage");
        lenient().when(properties.isFallbackOnError()).thenReturn(true);
    }

    // 辅助：构造有效的 IntentResult（Builder 模式）
    private IntentCommand validResult(String code, double confidence) {
        return IntentCommand.builder()
            .valid(true)
            .intentCode(code)
            .confidence(confidence)
            .rawText(SAMPLE_TEXT)
            .build();
    }

    private IntentCommand invalidResult(String error) {
        return IntentCommand.builder()
            .valid(false)
            .errorMessage(error)
            .rawText(SAMPLE_TEXT)
            .build();
    }

    // -------------------- 基本分类测试 --------------------

    @Test
    void classify_shouldReturnInvalid_whenTextIsNull() {
        IntentCommand result = service.classify(null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("语音文本为空");
        verifyNoInteractions(oneStageClassifier, twoStageClassifier, fallbackClassifier);
    }

    @Test
    void classify_shouldReturnInvalid_whenTextIsEmpty() {
        IntentCommand result = service.classify("   ");
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("语音文本为空");
    }

    @Test
    void classify_shouldUseOneStageClassifier_whenModeIsOneStage() {
        when(properties.getMode()).thenReturn("one-stage");
        IntentCommand raw = validResult("S2-2", 0.9);
        raw.setCandidates(Collections.singletonList(
            IntentCandidate.builder().intentCode("S2-2").confidence(0.9).build()
        ));
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(raw);

        IntentDefinition def = new IntentDefinition();
        def.setIntentCode("S2-2");
        def.setSubDomain("治疗");
        when(intentRegistry.getIntent("S2-2")).thenReturn(def);
        when(intentRegistry.getSubDomainByName("治疗")).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getIntentCode()).isEqualTo("S2-2");
        assertThat(result.getConfidence()).isEqualTo(0.9);
        assertThat(result.getClassifyMode()).isEqualTo("one-stage");
        assertThat(result.getMatchedDefinition()).isNotNull();
        verify(oneStageClassifier, times(1)).classify(SAMPLE_TEXT);
        verify(twoStageClassifier, never()).classify(anyString());
    }

    @Test
    void classify_shouldUseTwoStageClassifier_whenModeIsTwoStage() {
        when(properties.getMode()).thenReturn("two-stage");
        IntentCommand raw = validResult("G4-1", 0.85);
        raw.setCandidates(Collections.singletonList(
            IntentCandidate.builder().intentCode("G4-1").confidence(0.85).build()
        ));
        when(twoStageClassifier.classify(SAMPLE_TEXT)).thenReturn(raw);

        IntentDefinition def = new IntentDefinition();
        def.setIntentCode("G4-1");
        def.setSubDomain("生命体征");
        when(intentRegistry.getIntent("G4-1")).thenReturn(def);
        when(intentRegistry.getSubDomainByName("生命体征")).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getClassifyMode()).isEqualTo("two-stage");
        verify(twoStageClassifier, times(1)).classify(SAMPLE_TEXT);
        verify(oneStageClassifier, never()).classify(anyString());
    }

    // -------------------- Fallback 测试 --------------------

    @Test
    void classify_shouldUseFallback_whenPrimaryFailsAndFallbackEnabled() {
        when(properties.isFallbackOnError()).thenReturn(true);
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(invalidResult("分类失败"));

        IntentCommand fallbackRaw = validResult("G2-6", 0.6);
        fallbackRaw.setCandidates(Collections.singletonList(
            IntentCandidate.builder().intentCode("G2-6").confidence(0.6).build()
        ));
        when(fallbackClassifier.classify(SAMPLE_TEXT)).thenReturn(fallbackRaw);

        IntentDefinition def = new IntentDefinition();
        def.setIntentCode("G2-6");
        def.setSubDomain("设备");
        when(intentRegistry.getIntent("G2-6")).thenReturn(def);
        when(intentRegistry.getSubDomainByName("设备")).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getIntentCode()).isEqualTo("G2-6");
        assertThat(result.getClassifyMode()).isEqualTo("fallback");
        verify(fallbackClassifier, times(1)).classify(SAMPLE_TEXT);
    }

    @Test
    void classify_shouldNotUseFallback_whenFallbackDisabled() {
        when(properties.isFallbackOnError()).thenReturn(false);
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(invalidResult("分类失败"));

        IntentCommand result = service.classify(SAMPLE_TEXT);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("分类失败");
        verify(fallbackClassifier, never()).classify(anyString());
    }

    // -------------------- 候选排序与增强测试 --------------------

    @Test
    void enrichResult_shouldSortCandidatesByConfidenceDescending() {
        when(properties.getMode()).thenReturn("one-stage");
        IntentCandidate c1 = IntentCandidate.builder().intentCode("A").confidence(0.8).build();
        IntentCandidate c2 = IntentCandidate.builder().intentCode("B").confidence(0.9).build();
        IntentCandidate c3 = IntentCandidate.builder().intentCode("C").confidence(0.7).build();
        List<IntentCandidate> candidates = Arrays.asList(c1, c2, c3);

        IntentCommand raw = validResult("A", 0.8);
        raw.setCandidates(candidates);
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(raw);

        // 为所有可能的意图提供存根
        when(intentRegistry.getIntent(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            IntentDefinition def = new IntentDefinition();
            def.setIntentCode(code);
            def.setSubDomain("域" + code);
            return def;
        });
        when(intentRegistry.getSubDomainByName(anyString())).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        // 验证排序结果：最佳候选为 B（置信度 0.9）
        assertThat(result.getIntentCode()).isEqualTo("B");
        assertThat(result.getConfidence()).isEqualTo(0.9);
        List<IntentCandidate> sorted = result.getCandidates();
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).getIntentCode()).isEqualTo("B");
        assertThat(sorted.get(1).getIntentCode()).isEqualTo("A");
        assertThat(sorted.get(2).getIntentCode()).isEqualTo("C");
        // 实际只会调用一次 getIntent（为最佳候选）
        verify(intentRegistry, times(1)).getIntent(anyString());
    }

    @Test
    void enrichResult_shouldUseScoreWhenConfidenceIsNull() {
        IntentCandidate c1 = IntentCandidate.builder().intentCode("A").score(0.75).build();
        IntentCandidate c2 = IntentCandidate.builder().intentCode("B").confidence(0.85).build();
        IntentCandidate c3 = IntentCandidate.builder().intentCode("C").score(0.9).build();
        List<IntentCandidate> candidates = Arrays.asList(c1, c2, c3);

        IntentCommand raw = validResult("A", 0.5);
        raw.setCandidates(candidates);
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(raw);

        when(intentRegistry.getIntent(anyString())).thenAnswer(inv -> {
            IntentDefinition d = new IntentDefinition();
            d.setIntentCode(inv.getArgument(0));
            d.setSubDomain("域");
            return d;
        });
        when(intentRegistry.getSubDomainByName("域")).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        assertThat(result.getIntentCode()).isEqualTo("C");
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }

    @Test
    void enrichResult_shouldHandleNoCandidatesButValidResult() {
        IntentCommand raw = validResult("S1-1", 0.6);
        raw.setCandidates(null);
        when(oneStageClassifier.classify(SAMPLE_TEXT)).thenReturn(raw);

        IntentDefinition def = new IntentDefinition();
        def.setIntentCode("S1-1");
        def.setSubDomain("诊断");
        when(intentRegistry.getIntent("S1-1")).thenReturn(def);
        when(intentRegistry.getSubDomainByName("诊断")).thenReturn(new SubDomain());

        IntentCommand result = service.classify(SAMPLE_TEXT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getIntentCode()).isEqualTo("S1-1");
        assertThat(result.getCandidates()).hasSize(1);
        assertThat(result.getCandidates().get(0).getIntentCode()).isEqualTo("S1-1");
        assertThat(result.getCandidates().get(0).getConfidence()).isEqualTo(0.6);
    }

    // -------------------- 热加载测试 --------------------

    @Test
    void loadCaseConfig_shouldCallRegistryLoadIntentsForCase() throws Exception {
        String caseId = "stable_narrow";
        service.loadCaseConfig(caseId);
        verify(intentRegistry, times(1)).loadIntentsForCase(caseId);
    }

    @Test
    void loadCaseConfig_shouldPropagateExceptionWhenRegistryFails() throws Exception {
        String caseId = "invalid";
        doThrow(new RuntimeException("加载失败")).when(intentRegistry).loadIntentsForCase(caseId);
        assertThatThrownBy(() -> service.loadCaseConfig(caseId))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("全量热加载失败");
    }

    @Test
    void reloadIntentDictionary_shouldCallRegistryLoadIntentsForCase() throws Exception {
        String caseId = "tachycardia_stable_narrow";
        service.reloadIntentDictionary(caseId);
        verify(intentRegistry, times(1)).loadIntentsForCase(caseId);
    }

    @Test
    void getLoadStatus_shouldReturnFormattedStatus() {
        when(intentRegistry.getAllIntentList()).thenReturn(Collections.singletonList(new IntentDefinition()));
        when(intentRegistry.getSubDomainCount()).thenReturn(5);
        when(properties.getMode()).thenReturn("one-stage");

        String status = service.getLoadStatus();
        assertThat(status).contains("意图总数: 1");
        assertThat(status).contains("子域数: 5");
        assertThat(status).contains("模式: one-stage");
    }

    // -------------------- setMode 测试 --------------------

    @Test
    void setMode_shouldUpdatePropertiesMode() {
        service.setMode("two-stage");
        verify(properties, times(1)).setMode("two-stage");
    }
}
