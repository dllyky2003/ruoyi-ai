package org.ruoyi.system.controller;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.system.domain.definition.EmergencyLibraryItemDefinition;
import org.ruoyi.system.domain.vo.BaseInfo;
import org.ruoyi.system.domain.vo.GenerateDraftRequest;
import org.ruoyi.system.domain.vo.schemaInfo.ClinicalContextInfo;
import org.ruoyi.system.domain.vo.schemaInfo.MedicationHistoryInfo;
import org.ruoyi.system.domain.vo.schemaInfo.PhysicalExamInfo;
import org.ruoyi.system.service.DirectModelService;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemVersionBo;
import org.ruoyi.system.service.IEmergencyLibraryItemVersionService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 急救方案版本管理（公共库冻结定义；published 后禁止 UPDATE）
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/libraryItemVersion")
public class EmergencyLibraryItemVersionController extends BaseController {

    private final IEmergencyLibraryItemVersionService emergencyLibraryItemVersionService;
    private final DirectModelService directModelService;

    // ==================== 列表页接口 ====================
    @PostMapping("/aiGenerate")
    public R<String> generateDraft(@RequestBody @Valid BaseInfo req) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            EmergencyLibraryItemVersionVo emergencyLibraryItemVersionVo = new EmergencyLibraryItemVersionVo();
            EmergencyLibraryItemDefinition definitionJsonObject = new EmergencyLibraryItemDefinition();
            emergencyLibraryItemVersionVo.setDefinitionJson(definitionJsonObject);

            emergencyLibraryItemVersionVo.setName(req.getCaseName());
            emergencyLibraryItemVersionVo.setAge(req.getPatientAge());
            emergencyLibraryItemVersionVo.setSex(req.getPatientGender());
            emergencyLibraryItemVersionVo.setWorkflowType(req.getMainFlowType());
            emergencyLibraryItemVersionVo.setDifficultyLevel(req.getDifficultyLevel());
            emergencyLibraryItemVersionVo.setEstimatedDurationMinutes(req.getEstimatedDurationMinutes());
            emergencyLibraryItemVersionVo.setLearningObjectives(req.getLearningObjectives());

            //第一步，生成初始状态数据
            GenerateDraftRequest request = directModelService.getInitialStatePrompt(req);
            String clinicalContext = directModelService.generateSync(request.getPrompt(), request.getModel(), request.getSystemPrompt());

            EmergencyLibraryItemDefinition.ClinicalInfo clinicalContextData = mapper.readValue(clinicalContext, EmergencyLibraryItemDefinition.ClinicalInfo.class);
            definitionJsonObject.setClinical(clinicalContextData);

            request = directModelService.getPhysicalExamPrompt(req, clinicalContextData);
            String physicalExamContext = directModelService.generateSync(request.getPrompt(), request.getModel(), request.getSystemPrompt());

            EmergencyLibraryItemDefinition.PhysicalExam physicalExamInfo = mapper.readValue(physicalExamContext, EmergencyLibraryItemDefinition.PhysicalExam.class);
            definitionJsonObject.setPhysical(physicalExamInfo);

            request = directModelService.getMedicationHistoryPrompt(req, clinicalContextData, physicalExamInfo);
            String medicationExamContext = directModelService.generateSync(request.getPrompt(), request.getModel(), request.getSystemPrompt());

            EmergencyLibraryItemDefinition.MedicationInfo medicationHistoryInfo = mapper.readValue(clinicalContext, EmergencyLibraryItemDefinition.MedicationInfo.class);
            definitionJsonObject.setMedication(medicationHistoryInfo);

            request = directModelService.getDialoguePromptOnce(req, clinicalContextData, physicalExamInfo, medicationHistoryInfo);
            String DialogueContext = directModelService.generateSync(request.getPrompt(), request.getModel(), request.getSystemPrompt());

            EmergencyLibraryItemDefinition.InterviewInfo interviewInfo = mapper.readValue(DialogueContext, EmergencyLibraryItemDefinition.InterviewInfo.class);
            definitionJsonObject.setInterview(interviewInfo);

            return R.ok(DialogueContext);
        } catch (Exception e) {
            return R.fail("生成失败：" + e.getMessage());
        }
    }

    /**
     * 查询急救方案版本列表
     */
    @SaCheckPermission("system:libraryItemVersion:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyLibraryItemVersionVo> list(EmergencyLibraryItemVersionBo bo, PageQuery pageQuery) {
        return emergencyLibraryItemVersionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出急救方案版本列表
     */
    @SaCheckPermission("system:libraryItemVersion:export")
    @Log(title = "急救方案版本", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyLibraryItemVersionBo bo, HttpServletResponse response) {
        List<EmergencyLibraryItemVersionVo> list = emergencyLibraryItemVersionService.queryList(bo);
        ExcelUtil.exportExcel(list, "急救方案版本", EmergencyLibraryItemVersionVo.class, response);
    }

    /**
     * 获取急救方案版本详细信息（用于弹窗编辑）
     *
     * @param id 主键
     */
    @SaCheckPermission("system:libraryItemVersion:query")
    @GetMapping("/{id}")
    public R<EmergencyLibraryItemVersionVo> getInfo(@NotNull(message = "主键不能为空")
                                                    @PathVariable Long id) {
        EmergencyLibraryItemVersionVo vo = emergencyLibraryItemVersionService.queryById(id);
        return R.ok(vo);
    }

    /**
     * 新增急救方案版本
     */
    @SaCheckPermission("system:libraryItemVersion:add")
    @Log(title = "急救方案版本", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyLibraryItemVersionBo bo) {
        return toAjax(emergencyLibraryItemVersionService.insertByBo(bo));
    }

    /**
     * 修改急救方案版本
     */
    @SaCheckPermission("system:libraryItemVersion:edit")
    @Log(title = "急救方案版本", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyLibraryItemVersionBo bo) {
        return toAjax(emergencyLibraryItemVersionService.updateByBo(bo));
    }

    /**
     * 删除急救方案版本
     *
     * @param ids 主键串
     */
    @SaCheckPermission("system:libraryItemVersion:remove")
    @Log(title = "急救方案版本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyLibraryItemVersionService.deleteWithValidByIds(List.of(ids), true));
    }

    // ==================== 配置页面接口（新增） ====================

    /**
     * 获取方案配置详情（用于配置页面加载完整数据）
     * 路径：GET /emergency/libraryItemVersion/configure?id=xxx
     * 菜单权限：system:libraryItemVersion:configure
     *
     * @param id 版本ID（通过 Query 参数传递，而非路径参数）
     */
    @SaCheckPermission("system:libraryItemVersion:configure")
    @GetMapping("/configure")
    public R<EmergencyLibraryItemVersionVo> getConfigureInfo(@NotNull(message = "主键不能为空")
                                                             @RequestParam Long id) {
        EmergencyLibraryItemVersionVo vo = emergencyLibraryItemVersionService.queryById(id);
        return R.ok(vo);
    }

    /**
     * 保存方案配置草稿（支持新增和修改，根据是否有 id 自动判断）
     * 路径：POST /emergency/libraryItemVersion/configure/save
     * 菜单权限：system:libraryItemVersion:configure
     */
    @SaCheckPermission("system:libraryItemVersion:configure")
    @Log(title = "急救方案配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/configure/save")
    public R<Void> saveConfigure(@RequestBody EmergencyLibraryItemVersionBo bo) {
        if (bo.getId() == null) {
            // 新增
            return toAjax(emergencyLibraryItemVersionService.insertByBo(bo));
        } else {
            System.out.println(" getConsciousness: " + bo.getDefinitionJson());
            return toAjax(emergencyLibraryItemVersionService.updateByBo(bo));
        }
    }

    /**
     * 发布方案版本
     * 路径：POST /emergency/libraryItemVersion/configure/publish
     * 菜单权限：system:libraryItemVersion:configure:publish
     *
     * @param id 版本ID
     */
    @SaCheckPermission("system:libraryItemVersion:configure:publish")
    @Log(title = "急救方案配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/configure/publish")
    public R<Void> publish(@NotNull(message = "主键不能为空") @RequestParam Long id) {
        // TODO: 实现发布逻辑
        EmergencyLibraryItemVersionBo bo = new EmergencyLibraryItemVersionBo();
        bo.setId(id);
        bo.setStatus("published");
        return toAjax(emergencyLibraryItemVersionService.updateByBo(bo));
    }

    /**
     * 获取方案流程图（Mermaid 代码）
     * 路径：GET /emergency/libraryItemVersion/mermaid?id=xxx
     */

    @SaCheckPermission("system:libraryItemVersion:query")
    @GetMapping("/mermaid")
    public R<String> getMermaidCode(@RequestParam(required = false) Long id) {
        // TODO: 根据 id 从数据库或状态机配置中获取实际的 mermaid 代码
        String mermaidCode = """
            flowchart TD
                                   %% ============ 准备与评估阶段 ============
                                   subgraph prep["① 准备与评估"]
                                       direction TB
                                       A["团队到达"] --> B["初始评估"]
                                       B --> C["稳定性评估"]
                                       C --> D["QRS宽度判读"]
                                       D --> E["节律规则性判读"]
                                   end

                                   %% ============ 规则窄QRS治疗阶段 ============
                                   subgraph treatment["② 规则窄QRS治疗"]
                                       direction TB
                                       F["迷走神经刺激"]:::redBg
                                       G["评估迷走效果"]
                                       G -->|成功| J["转复窦性监测"]
                                       G -->|失败| H["腺苷6mg推注"]
                                       H --> I["评估6mg效果"]
                                       I -->|成功| J
                                       I -->|失败| K["腺苷12mg推注"]
                                       K --> L["评估12mg效果"]
                                       L -->|成功| J
                                       L -->|失败| M["二线药物"]
                                       M --> N["评估二线效果"]
                                       N -->|成功| J
                                       N -->|失败| O["专家会诊"]
                                   end

                                   %% ============ 电复律与会诊 ============
                                   subgraph cardio["③ 电复律与会诊"]
                                       direction TB
                                       P["稳定电复律"]
                                       Q["紧急电复律"]
                                       O -->|S2-6 选择电复律| P
                                       O -->|SYS_TRANSFER 转上级| T["案例结束"]
                                       P -->|成功| J
                                       P -->|失败| P
                                       P -->|失败转会诊| O
                                       Q -->|S2-6 开始准备| Q
                                       Q -->|G1-1 清场完成| Q
                                       Q -->|成功| J
                                       Q -->|失败| Q
                                   end

                                   %% ============ 结果与异常处理 ============
                                   subgraph result["④ 结果与异常"]
                                       direction TB
                                       R["转宽QRS算法"]
                                       S["转不规则算法"]
                                       ER["错误处理"]
                                       TO["超时处理"]
                                   end

                                   %% ============ 跨子图连接 ============
                                   %% 从评估到治疗
                                   E -->|S1-2 规则| F
                                   E -->|S1-2 不规则| S
                                   D -->|S1-1 宽QRS| R
                                   C -->|S3-1 恶化| Q
                                   B -->|S1-3 不稳定| Q
                                   C -->|S1-3 稳定| D
                                   B -->|S1-3 稳定| C
                                   A --> B

                                   %% 回溯与重复
                                   C -->|S1-1 回溯QRS| B
                                   C -->|S1-3 重复评估| C
                                   D -->|S1-1 重复判读| D
                                   D -->|S1-3 回溯稳定| C
                                   E -->|S1-2 重复判读| E
                                   E -->|S1-1 回溯QRS| D
                                   B -->|S1-1 早期QRS判读| B
                                   B -->|S1-2 早期节律判读| B

                                   %% 错误处理与超时
                                   ER -->|SYS_ERROR_FIXED 纠正后返回评估| C
                                   ER -->|SYS_ERROR_FIXED_VAGAL 补做迷走| F
                                   ER -->|SYS_ERROR_FIXED_DOSE 纠正剂量| H
                                   F -->|SYS_TIMEOUT_VAGAL 超时| TO
                                   TO -->|SYS_TIMEOUT_CONTINUE 继续评估| C
                                   TO -->|SYS_TIMEOUT_NEXT 自动推进腺苷| H

                                   %% 终止连接
                                   J -->|SYS_TERMINATE 完成救治| T
                                   R --> T
                                   S --> T

                                   %% ============ 样式定义 ============
                                   classDef setup fill:#e1f5fe,stroke:#0288d1
                                   classDef assess fill:#fff9c4,stroke:#fbc02d
                                   classDef treat fill:#c8e6c9,stroke:#388e3c
                                   classDef success fill:#a5d6a7,stroke:#2e7d32
                                   classDef danger fill:#ffcdd2,stroke:#c62828
                                   classDef algorithm fill:#d1c4e9,stroke:#4527a0
                                   classDef term fill:#ffccbc,stroke:#d84315
                                   classDef redBg fill:#ff0000,color:#ffffff,stroke:#388e3c,stroke-width:1px

                                   class A setup
                                   class B,C,D,E assess
                                   class G,H,I,K,L,M,N,O,P,Q treat
                                   class J success
                                   class R,S algorithm
                                   class T term
                                   class ER danger
                                   class TO danger
        """;
        return R.ok(mermaidCode);
    }
}
