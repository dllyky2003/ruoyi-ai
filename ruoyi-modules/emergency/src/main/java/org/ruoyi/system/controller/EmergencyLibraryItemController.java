package org.ruoyi.system.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemBo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVo;
import org.ruoyi.system.service.IEmergencyLibraryItemService;
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

import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 公共库对象身份
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/libraryItem")
public class EmergencyLibraryItemController extends BaseController {

    private final IEmergencyLibraryItemService emergencyLibraryItemService;

    /**
     * 查询公共库对象身份列表
     */
    @SaCheckPermission("emergency:libraryItem:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyLibraryItemVo> list(EmergencyLibraryItemBo bo, PageQuery pageQuery) {
        return emergencyLibraryItemService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出公共库对象身份列表
     */
    @SaCheckPermission("emergency:libraryItem:export")
    @Log(title = "公共库对象身份", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyLibraryItemBo bo, HttpServletResponse response) {
        List<EmergencyLibraryItemVo> list = emergencyLibraryItemService.queryList(bo);
        ExcelUtil.exportExcel(list, "公共库对象身份", EmergencyLibraryItemVo.class, response);
    }

    /**
     * 获取公共库对象身份详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:libraryItem:query")
    @GetMapping("/{id}")
    public R<EmergencyLibraryItemVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyLibraryItemService.queryById(id));
    }

    /**
     * 新增公共库对象身份
     */
    @SaCheckPermission("emergency:libraryItem:add")
    @Log(title = "公共库对象身份", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyLibraryItemBo bo) {
        return toAjax(emergencyLibraryItemService.insertByBo(bo));
    }

    /**
     * 修改公共库对象身份
     */
    @SaCheckPermission("emergency:libraryItem:edit")
    @Log(title = "公共库对象身份", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyLibraryItemBo bo) {
        return toAjax(emergencyLibraryItemService.updateByBo(bo));
    }

    /**
     * 删除公共库对象身份
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:libraryItem:remove")
    @Log(title = "公共库对象身份", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyLibraryItemService.deleteWithValidByIds(List.of(ids), true));
    }
}
