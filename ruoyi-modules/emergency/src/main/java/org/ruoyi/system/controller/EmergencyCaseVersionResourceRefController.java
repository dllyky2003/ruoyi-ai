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
import org.ruoyi.system.domain.vo.EmergencyCaseVersionResourceRefVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionResourceRefBo;
import org.ruoyi.system.service.IEmergencyCaseVersionResourceRefService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例版本引用的资源配置与公共库版本映射
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/caseVersionResourceRef")
public class EmergencyCaseVersionResourceRefController extends BaseController {

    private final IEmergencyCaseVersionResourceRefService emergencyCaseVersionResourceRefService;

    /**
     * 查询病例版本引用的资源配置与公共库版本映射列表
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyCaseVersionResourceRefVo> list(EmergencyCaseVersionResourceRefBo bo, PageQuery pageQuery) {
        return emergencyCaseVersionResourceRefService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例版本引用的资源配置与公共库版本映射列表
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:export")
    @Log(title = "病例版本引用的资源配置与公共库版本映射", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyCaseVersionResourceRefBo bo, HttpServletResponse response) {
        List<EmergencyCaseVersionResourceRefVo> list = emergencyCaseVersionResourceRefService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例版本引用的资源配置与公共库版本映射", EmergencyCaseVersionResourceRefVo.class, response);
    }

    /**
     * 获取病例版本引用的资源配置与公共库版本映射详细信息
     *
     * @param caseVersionId 主键
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:query")
    @GetMapping("/{caseVersionId}")
    public R<EmergencyCaseVersionResourceRefVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long caseVersionId) {
        return R.ok(emergencyCaseVersionResourceRefService.queryById(caseVersionId));
    }

    /**
     * 新增病例版本引用的资源配置与公共库版本映射
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:add")
    @Log(title = "病例版本引用的资源配置与公共库版本映射", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyCaseVersionResourceRefBo bo) {
        return toAjax(emergencyCaseVersionResourceRefService.insertByBo(bo));
    }

    /**
     * 修改病例版本引用的资源配置与公共库版本映射
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:edit")
    @Log(title = "病例版本引用的资源配置与公共库版本映射", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyCaseVersionResourceRefBo bo) {
        return toAjax(emergencyCaseVersionResourceRefService.updateByBo(bo));
    }

    /**
     * 删除病例版本引用的资源配置与公共库版本映射
     *
     * @param caseVersionIds 主键串
     */
    @SaCheckPermission("emergency:caseVersionResourceRef:remove")
    @Log(title = "病例版本引用的资源配置与公共库版本映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseVersionIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] caseVersionIds) {
        return toAjax(emergencyCaseVersionResourceRefService.deleteWithValidByIds(List.of(caseVersionIds), true));
    }
}
