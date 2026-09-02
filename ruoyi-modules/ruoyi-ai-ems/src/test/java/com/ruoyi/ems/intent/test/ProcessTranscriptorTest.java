package com.ruoyi.ems.intent.test;



import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.VariableMetaManager;
import com.ruoyi.ems.domain.CaseContext;
import com.ruoyi.ems.domain.VariableMeta;
import com.ruoyi.ems.engine.extraction.ProcessTranscriptor;
import com.ruoyi.ems.exception.VariableValidationException;

import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.service.IntentClassificationService;
import com.ruoyi.ems.intent.utils.ParameterExtractor;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessTranscriptorTest {

    @Mock
    private IntentClassificationService intentService;

    @Mock
    private ParameterExtractor parameterExtractor;

    @Mock
    private EmsContextManager contextManager;

    @Mock
    private VariableMetaManager variableMetaManager;

    @InjectMocks
    private ProcessTranscriptor processTranscriptor;

    private static final String CASE_ID = "stable_narrow";
    private static final String SESSION_ID = "test-session";

    @BeforeEach
    void setUp() {
        CaseContext context = new CaseContext();
        when(contextManager.getSnapshot(SESSION_ID)).thenReturn(context);
    }

    @Test
    void processTranscript_shouldThrowException_whenVariableNotDefined() {
        // 模拟分类结果：有效意图，包含参数
        IntentCommand intentResult = IntentCommand.builder()
            .valid(true)
            .intentCode("S2-2")
            .build();
        when(intentService.classify(anyString(), any(ClassificationContext.class)))
            .thenReturn(intentResult);

        // 模拟参数抽取：包含未定义的变量
        Map<String, Object> extractedParams = new HashMap<>();
        extractedParams.put("undefined_var", "some_value");
        when(parameterExtractor.extract(intentResult)).thenReturn(extractedParams);

        // 模拟变量定义（不包含 undefined_var）
        when(variableMetaManager.getVariablesForCase(CASE_ID))
            .thenReturn(Map.of("operation_status", new VariableMeta()));

        // 执行并验证异常
        assertThatThrownBy(() -> processTranscriptor.processTranscript(
            mock(Session.class), "推注腺苷6mg", CASE_ID))
            .isInstanceOf(VariableValidationException.class)
            .hasMessageContaining("变量 'undefined_var' 未在案例");
    }
}
