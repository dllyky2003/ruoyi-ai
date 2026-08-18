package org.ruoyi.system.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.ruoyi.system.domain.vo.EmergencyCaseVersionStageIndexVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionStageIndexBo;
import org.ruoyi.system.service.IEmergencyCaseVersionStageIndexService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例版本的阶段索引
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/caseVersionStageIndex")
public class EmergencyCaseVersionStageIndexController extends BaseController {

    private final IEmergencyCaseVersionStageIndexService emergencyCaseVersionStageIndexService;

    /**
     * 查询病例版本的阶段索引列表
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyCaseVersionStageIndexVo> list(EmergencyCaseVersionStageIndexBo bo, PageQuery pageQuery) {
        return emergencyCaseVersionStageIndexService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例版本的阶段索引列表
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:export")
    @Log(title = "病例版本的阶段索引", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyCaseVersionStageIndexBo bo, HttpServletResponse response) {
        List<EmergencyCaseVersionStageIndexVo> list = emergencyCaseVersionStageIndexService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例版本的阶段索引", EmergencyCaseVersionStageIndexVo.class, response);
    }

    /**
     * 获取病例版本的阶段索引详细信息
     *
     * @param caseVersionId 主键
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:query")
    @GetMapping("/{caseVersionId}")
    public R<EmergencyCaseVersionStageIndexVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long caseVersionId) {
        return R.ok(emergencyCaseVersionStageIndexService.queryById(caseVersionId));
    }

    /**
     * 新增病例版本的阶段索引
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:add")
    @Log(title = "病例版本的阶段索引", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyCaseVersionStageIndexBo bo) {
        return toAjax(emergencyCaseVersionStageIndexService.insertByBo(bo));
    }

    /**
     * 修改病例版本的阶段索引
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:edit")
    @Log(title = "病例版本的阶段索引", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyCaseVersionStageIndexBo bo) {
        return toAjax(emergencyCaseVersionStageIndexService.updateByBo(bo));
    }

    /**
     * 删除病例版本的阶段索引
     *
     * @param caseVersionIds 主键串
     */
    @SaCheckPermission("emergency:caseVersionStageIndex:remove")
    @Log(title = "病例版本的阶段索引", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseVersionIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] caseVersionIds) {
        return toAjax(emergencyCaseVersionStageIndexService.deleteWithValidByIds(List.of(caseVersionIds), true));
    }
}
