package org.ruoyi.system.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import cn.hutool.core.io.resource.ClassPathResource;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;
import org.ruoyi.system.domain.vo.BaseInfo;
import org.ruoyi.system.domain.vo.GenerateDraftRequest;
import org.ruoyi.system.domain.vo.SysDictDataVo;
import org.ruoyi.system.domain.vo.schemaInfo.ClinicalContextInfo;
import org.ruoyi.system.domain.vo.schemaInfo.MedicationHistoryInfo;
import org.ruoyi.system.domain.vo.schemaInfo.PhysicalExamInfo;
import org.ruoyi.system.service.prompt.PromptTemplate;
import org.ruoyi.system.service.prompt.YamlPromptTemplateLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DirectModelService {
    private final IChatModelService chatModelService;
    private final ChatServiceFactory chatServiceFactory;
    private final ISysDictTypeService dictTypeService;

    @Autowired
    private YamlPromptTemplateLoader promptTemplateLoader;


    private PromptTemplate medicationHistoryTemplate;
    private PromptTemplate initialStatePromptTemplate;
    private PromptTemplate physicalExamPromptTemplate;
    private PromptTemplate consultationPromptTemplate;
    /**
     * 统一加载 prompts/options.yml 中的枚举候选集（缓存到内存）
     */
    private Map<String, Object> optionsRoot = new LinkedHashMap<>();
    private boolean optionsInitialized = false;

    public String generateSync(String prompt, String modelName, String systemPrompt) {
        ChatModelVo modelVo = chatModelService.selectModelByName(modelName);
        if (modelVo == null) {
            throw new IllegalArgumentException("模型不存在: " + modelName);
        }

        // System.out.println("系统提示词:\r\n"+systemPrompt);
        // System.out.println("\r\n  ");
        // System.out.println("用户提示词:\r\n"+prompt);

        AbstractChatService chatService = chatServiceFactory.getOriginalService(modelVo.getProviderCode());

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setModel(modelName);

        ChatModel chatModel = chatService.buildChatModel(modelVo);

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(dev.langchain4j.data.message.SystemMessage.from(systemPrompt));
        }
        messages.add(dev.langchain4j.data.message.UserMessage.userMessage(prompt));

        dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(messages);
        return response.aiMessage().text();
    }
    @PostConstruct
    public void initPrompts() {
        try {


            ClassPathResource resource = new ClassPathResource("commonOptions.yml");
            try (InputStream inputStream = resource.getStream()) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                optionsRoot = mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
            }
            this.optionsInitialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("加载提示词失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getOptionByPath(String path) {
        // path 示例： "medicationHistory.tempMed_nitroDoseOptions"
        if (!optionsInitialized) {
            throw new IllegalStateException("options 尚未初始化");
        }
        String[] parts = path.split("\\.");
        Object cur = optionsRoot;
        for (String p : parts) {
            if (!(cur instanceof Map)) {
                throw new IllegalStateException("options 路径错误，非 Map: " + path);
            }
            cur = ((Map<String, Object>) cur).get(p);
            if (cur == null) {
                throw new IllegalStateException("options 路径未找到: " + path);
            }
        }
        return cur;
    }

    private String getOptionJsonArray(String path) {
        Object v = getOptionByPath(path);
        return JSONUtil.toJsonStr(v);
    }


    // ==================== 业务方法 ====================


    public GenerateDraftRequest getInitialStatePrompt(BaseInfo info) {
        initialStatePromptTemplate= promptTemplateLoader.load("prompts/initialStatePrompt.yml");

        Map<String, List<SysDictDataVo>> dictMap = new LinkedHashMap<>();
        dictMap.put("emergency_ecgs", dictTypeService.selectDictDataByType("emergency_ecgs"));
        dictMap.put("emergency_consciousness", dictTypeService.selectDictDataByType("emergency_consciousness"));
        dictMap.put("ems_airway_patency", dictTypeService.selectDictDataByType("ems_airway_patency"));
        dictMap.put("ems_resp_pattern", dictTypeService.selectDictDataByType("ems_resp_pattern"));
        dictMap.put("ems_circ_hemodynamics", dictTypeService.selectDictDataByType("ems_circ_hemodynamics"));
        dictMap.put("ems_exam_skin", dictTypeService.selectDictDataByType("ems_exam_skin"));

        List<String> missingDicts = dictMap.entrySet().stream()
            .filter(entry -> ObjectUtil.isEmpty(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        if (!missingDicts.isEmpty()) {
            throw new IllegalArgumentException("以下必要字典数据缺失，请检查字典配置: " + String.join(", ", missingDicts));
        }


        List<String> consciousnessValues = dictMap.get("emergency_consciousness").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());
        List<String> airwayValues = dictMap.get("ems_airway_patency").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());
        List<String> breathingValues = dictMap.get("ems_resp_pattern").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());
        List<String> circulationValues = dictMap.get("ems_circ_hemodynamics").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());
        List<String> appearanceValues = dictMap.get("ems_exam_skin").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());


        List<String> ecgValues = dictMap.get("emergency_ecgs").stream()
            .map(SysDictDataVo::getDictValue).collect(Collectors.toList());



        String systemPrompt = initialStatePromptTemplate.getSystemPrompt();

        String learningObjectivesJson = info.getLearningObjectives();
        String ecgOptionsJson = JSONUtil.toJsonStr(ecgValues);
        String consciousnessOptionsJson = JSONUtil.toJsonStr(consciousnessValues);
        String airwayOptionsJson = JSONUtil.toJsonStr(airwayValues);
        String breathingOptionsJson = JSONUtil.toJsonStr(breathingValues);
        String circulationOptionsJson = JSONUtil.toJsonStr(circulationValues);
        String appearanceOptionsJson = JSONUtil.toJsonStr(appearanceValues);

        // 注意：这里的参数顺序必须和 initialStatePrompt.yml 里 userPromptTemplate 的 %s/%d 对应顺序一致
        String userPrompt = String.format(
           initialStatePromptTemplate.getSystemPrompt(),
            info.getCaseName(),
            info.getPatientGender(),
            info.getPatientAge(),
            info.getMainFlowType(),
            info.getDifficultyLevel(),
            info.getEstimatedDurationMinutes(),
            learningObjectivesJson,
            ecgOptionsJson,
            consciousnessOptionsJson,
            airwayOptionsJson,
            breathingOptionsJson,
            circulationOptionsJson,
            appearanceOptionsJson
        );

        GenerateDraftRequest request = new GenerateDraftRequest();
        request.setModel(info.getModelName());
        request.setSystemPrompt(systemPrompt);
        request.setPrompt(userPrompt);
        return request;
    }
    public GenerateDraftRequest getPhysicalExamPrompt(BaseInfo info, EmergencyLibraryItemDefinition.ClinicalInfo clinicalContextInfo) {

        physicalExamPromptTemplate = promptTemplateLoader.load("prompts/physicalExamPrompt.yml");


        String consciousness = clinicalContextInfo.getInitialState().getConsciousness();
        String airway = clinicalContextInfo.getInitialState().getAirway();
        String breathing = clinicalContextInfo.getInitialState().getBreathing();
        String circulation = clinicalContextInfo.getInitialState().getCirculation();
        String ecg = clinicalContextInfo.getInitialState().getEcg();
        List<String> appearanceList = clinicalContextInfo.getInitialState().getAppearance();
        String appearanceJson = JSONUtil.toJsonStr(appearanceList);
        String learningObjectivesJson = info.getLearningObjectives();


        String dorsalisPedisPulseOptionsJson = getOptionJsonArray("physicalExam.dorsalisPedisPulseType");
        String thighArteryPulseOptionsJson = getOptionJsonArray("physicalExam.thighArteryPulseType");
        String jvdOptionsJson = getOptionJsonArray("physicalExam.jvdOptions");
        String crtOptionsJson = getOptionJsonArray("physicalExam.crtOptions");
        String lungOptionsJson = getOptionJsonArray("physicalExam.lungOptions");
        String lungApexOptionsJson = getOptionJsonArray("physicalExam.lungApexOptions");
        String yesNoOptionsJson = getOptionJsonArray("physicalExam.yesNoOptions");
        String pupilOptionsJson = getOptionJsonArray("physicalExam.pupilOptions");
        String faceOptionsJson = getOptionJsonArray("physicalExam.faceOptions");
        String abdomenOptionsJson = getOptionJsonArray("physicalExam.abdomenOptions");

        // 1) 从 YAML 加载 systemPrompt/userPromptTemplate

        String systemPromptTemplate = physicalExamPromptTemplate.getSystemPrompt();
        String userPromptTemplate = physicalExamPromptTemplate.getUserPromptTemplate();

        // 2) 渲染 systemPrompt（注意 %s 参数顺序必须与 YAML 内一致）
        String systemPrompt = String.format(
            systemPromptTemplate,
            jvdOptionsJson,
            crtOptionsJson,
            lungOptionsJson,
            lungApexOptionsJson,
            yesNoOptionsJson,
            pupilOptionsJson,
            faceOptionsJson,
            abdomenOptionsJson,
            dorsalisPedisPulseOptionsJson,
            thighArteryPulseOptionsJson, // femoralPulse
            thighArteryPulseOptionsJson  // thighArteryPulse
        );

        // 3) 渲染 userPrompt
        String userPrompt = String.format(
            userPromptTemplate,
            info.getCaseName(),
            info.getPatientGender(),
            info.getPatientAge(),
            info.getMainFlowType(),
            info.getDifficultyLevel(),
            info.getEstimatedDurationMinutes(),
            learningObjectivesJson,
            consciousness,
            airway,
            breathing,
            circulation,
            ecg,
            appearanceJson
        );

        // 4) 构建请求对象
        GenerateDraftRequest request = new GenerateDraftRequest();
        request.setModel(info.getModelName());
        request.setSystemPrompt(systemPrompt);
        request.setPrompt(userPrompt);
        return request;
    }


    public GenerateDraftRequest getMedicationHistoryPrompt(
        BaseInfo info,
        EmergencyLibraryItemDefinition.ClinicalInfo clinicalContextInfo,
        EmergencyLibraryItemDefinition.PhysicalExam physicalExamInfo
    ) {
        medicationHistoryTemplate= promptTemplateLoader.load("prompts/medicationHistoryPrompt.yml");



        // =============== 1) initialState 取值（用于情景推断）==============
        String consciousness = clinicalContextInfo.getInitialState().getConsciousness();
        String airway = clinicalContextInfo.getInitialState().getAirway();
        String breathing = clinicalContextInfo.getInitialState().getBreathing();
        String circulation = clinicalContextInfo.getInitialState().getCirculation();
        String ecg = clinicalContextInfo.getInitialState().getEcg();
        List<String> appearanceList = clinicalContextInfo.getInitialState().getAppearance();
        String appearanceJson = JSONUtil.toJsonStr(appearanceList);

        String learningObjectivesJson = info.getLearningObjectives();

        // =============== 2) physicalContext：只抽取必要字段 ==============
        String jvd = "";
        String crt = "";
        String accessoryMuscles = "";
        String pupils = "";
        String abdomen = "";

        if (physicalExamInfo != null) {
            if (physicalExamInfo.getVascular() != null) {
                jvd = physicalExamInfo.getVascular().getJvd();
                crt = physicalExamInfo.getVascular().getCrt();
            }
            if (physicalExamInfo.getRespiratory() != null) {
                accessoryMuscles = physicalExamInfo.getRespiratory().getAccessoryMuscles();
            }
            if (physicalExamInfo.getNeuro() != null) {
                pupils = physicalExamInfo.getNeuro().getPupils();
            }
            if (physicalExamInfo.getAbdominal() != null) {
                abdomen = physicalExamInfo.getAbdominal().getAbdomen();
            }
        }

        Map<String, Object> physicalContext = new LinkedHashMap<>();
        Map<String, Object> physicalVascular = new LinkedHashMap<>();
        physicalVascular.put("jvd", jvd);
        physicalVascular.put("crt", crt);
        physicalContext.put("vascular", physicalVascular);

        Map<String, Object> physicalResp = new LinkedHashMap<>();
        physicalResp.put("accessoryMuscles", accessoryMuscles);
        physicalContext.put("respiratory", physicalResp);

        Map<String, Object> physicalNeuro = new LinkedHashMap<>();
        physicalNeuro.put("pupils", pupils);
        physicalContext.put("neuro", physicalNeuro);

        Map<String, Object> physicalAbd = new LinkedHashMap<>();
        physicalAbd.put("abdomen", abdomen);
        physicalContext.put("abdominal", physicalAbd);

        String physicalContextJson = JSONUtil.toJsonStr(physicalContext);


        String hasAllergyOptionsJson = getOptionJsonArray("medicationHistory.allergyHasAllergyOptions");
        String drugCategoryOptionsJson = getOptionJsonArray("medicationHistory.allergyDrugCategoryOptions");
        String severityOptionsJson = getOptionJsonArray("medicationHistory.allergySeverityOptions");

        String longTermOptionsJson = getOptionJsonArray("medicationHistory.homeMedLongTermOptions");
        String anticoagulantOptionsJson = getOptionJsonArray("medicationHistory.homeMedAnticoagulantOptions");
        String antiplateletOptionsJson = getOptionJsonArray("medicationHistory.homeMedAntiplateletOptions");
        String pde5OptionsJson = getOptionJsonArray("medicationHistory.homeMedPde5Options");

        String sedativeOptionsJson = getOptionJsonArray("medicationHistory.tempMedSedativeOptions");
        String nitroGivenOptionsJson = getOptionJsonArray("medicationHistory.tempMedNitroGivenOptions");

        String nitroDoseOptionsJson = getOptionJsonArray("medicationHistory.tempMedNitroDoseOptions");

        String hypertensionOptionsJson = getOptionJsonArray("medicationHistory.historyHypertensionOptions");
        String hypertensionYearsOptionsJson = getOptionJsonArray("medicationHistory.historyHypertensionYearsOptions");
        String diabetesOptionsJson = getOptionJsonArray("medicationHistory.historyDiabetesOptions");
        String renalOptionsJson = getOptionJsonArray("medicationHistory.historyRenalOptions");
        String giOptionsJson = getOptionJsonArray("medicationHistory.historyGiBleedOptions");

        String systemPrompt = String.format(
            medicationHistoryTemplate.getSystemPrompt(),
            hasAllergyOptionsJson,
            drugCategoryOptionsJson,
            severityOptionsJson,
            longTermOptionsJson,
            anticoagulantOptionsJson,
            antiplateletOptionsJson,
            pde5OptionsJson,
            sedativeOptionsJson,
            nitroGivenOptionsJson,
            nitroDoseOptionsJson,
            hypertensionOptionsJson,
            hypertensionYearsOptionsJson,
            diabetesOptionsJson,
            renalOptionsJson,
            giOptionsJson
        );

        String userPrompt = String.format(
            medicationHistoryTemplate.getUserPromptTemplate(),
            info.getCaseName(),
            info.getPatientGender(),
            info.getPatientAge(),
            info.getMainFlowType(),
            info.getDifficultyLevel(),
            info.getEstimatedDurationMinutes(),
            learningObjectivesJson,
            consciousness,
            airway,
            breathing,
            circulation,
            ecg,
            appearanceJson,
            physicalContextJson
        );

        GenerateDraftRequest request = new GenerateDraftRequest();
        request.setModel(info.getModelName());
        request.setSystemPrompt(systemPrompt);
        request.setPrompt(userPrompt);
        return request;
    }

    public GenerateDraftRequest getDialoguePromptOnce(
        BaseInfo info,
        EmergencyLibraryItemDefinition.ClinicalInfo clinicalContextInfo,
        EmergencyLibraryItemDefinition.PhysicalExam physicalExamInfo,
        EmergencyLibraryItemDefinition.MedicationInfo medicationHistoryInfo
    ) {
        consultationPromptTemplate = promptTemplateLoader.load("prompts/consultationPrompt.yml");

        String baseInfoSummary = info.toAiPromptDescription();
        String initialStateSummary = clinicalContextInfo != null ? clinicalContextInfo.generateCardioPulmonaryContext() : "";
        String physicalSummary = physicalExamInfo != null ? physicalExamInfo.generateMedicationBackground() : "";
        String medicationSummary = medicationHistoryInfo != null ? medicationHistoryInfo.generateDiagnosisBackground() : "";

        String userPrompt = String.format(
            consultationPromptTemplate.getUserPromptTemplate(),
            baseInfoSummary,
            initialStateSummary,
            physicalSummary,
            medicationSummary
        );

        String systemPrompt = consultationPromptTemplate.getSystemPrompt();

        GenerateDraftRequest request = new GenerateDraftRequest();
        request.setModel(info.getModelName());
        request.setSystemPrompt(systemPrompt);
        request.setPrompt(userPrompt);
        return request;
    }

}
