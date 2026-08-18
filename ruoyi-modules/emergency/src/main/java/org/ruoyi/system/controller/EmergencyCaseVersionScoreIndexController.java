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
import org.ruoyi.system.domain.vo.EmergencyCaseVersionScoreIndexVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionScoreIndexBo;
import org.ruoyi.system.service.IEmergencyCaseVersionScoreIndexService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例版本评分项索引
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/caseVersionScoreIndex")
public class EmergencyCaseVersionScoreIndexController extends BaseController {

    private final IEmergencyCaseVersionScoreIndexService emergencyCaseVersionScoreIndexService;

    /**
     * 查询病例版本评分项索引列表
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyCaseVersionScoreIndexVo> list(EmergencyCaseVersionScoreIndexBo bo, PageQuery pageQuery) {
        return emergencyCaseVersionScoreIndexService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例版本评分项索引列表
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:export")
    @Log(title = "病例版本评分项索引", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyCaseVersionScoreIndexBo bo, HttpServletResponse response) {
        List<EmergencyCaseVersionScoreIndexVo> list = emergencyCaseVersionScoreIndexService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例版本评分项索引", EmergencyCaseVersionScoreIndexVo.class, response);
    }

    /**
     * 获取病例版本评分项索引详细信息
     *
     * @param caseVersionId 主键
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:query")
    @GetMapping("/{caseVersionId}")
    public R<EmergencyCaseVersionScoreIndexVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long caseVersionId) {
        return R.ok(emergencyCaseVersionScoreIndexService.queryById(caseVersionId));
    }

    /**
     * 新增病例版本评分项索引
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:add")
    @Log(title = "病例版本评分项索引", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyCaseVersionScoreIndexBo bo) {
        return toAjax(emergencyCaseVersionScoreIndexService.insertByBo(bo));
    }

    /**
     * 修改病例版本评分项索引
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:edit")
    @Log(title = "病例版本评分项索引", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyCaseVersionScoreIndexBo bo) {
        return toAjax(emergencyCaseVersionScoreIndexService.updateByBo(bo));
    }

    /**
     * 删除病例版本评分项索引
     *
     * @param caseVersionIds 主键串
     */
    @SaCheckPermission("emergency:caseVersionScoreIndex:remove")
    @Log(title = "病例版本评分项索引", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseVersionIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] caseVersionIds) {
        return toAjax(emergencyCaseVersionScoreIndexService.deleteWithValidByIds(List.of(caseVersionIds), true));
    }
}
