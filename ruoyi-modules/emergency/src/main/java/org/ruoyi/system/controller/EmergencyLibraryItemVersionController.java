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
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemVersionBo;
import org.ruoyi.system.service.IEmergencyLibraryItemVersionService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 公共库冻结定义；published 后禁止 UPDATE
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

    /**
     * 查询公共库冻结定义；published 后禁止 UPDATE列表
     */
    @SaCheckPermission("emergency:libraryItemVersion:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyLibraryItemVersionVo> list(EmergencyLibraryItemVersionBo bo, PageQuery pageQuery) {
        return emergencyLibraryItemVersionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出公共库冻结定义；published 后禁止 UPDATE列表
     */
    @SaCheckPermission("emergency:libraryItemVersion:export")
    @Log(title = "公共库冻结定义；published 后禁止 UPDATE", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyLibraryItemVersionBo bo, HttpServletResponse response) {
        List<EmergencyLibraryItemVersionVo> list = emergencyLibraryItemVersionService.queryList(bo);
        ExcelUtil.exportExcel(list, "公共库冻结定义；published 后禁止 UPDATE", EmergencyLibraryItemVersionVo.class, response);
    }

    /**
     * 获取公共库冻结定义；published 后禁止 UPDATE详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:libraryItemVersion:query")
    @GetMapping("/{id}")
    public R<EmergencyLibraryItemVersionVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyLibraryItemVersionService.queryById(id));
    }

    /**
     * 新增公共库冻结定义；published 后禁止 UPDATE
     */
    @SaCheckPermission("emergency:libraryItemVersion:add")
    @Log(title = "公共库冻结定义；published 后禁止 UPDATE", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyLibraryItemVersionBo bo) {
        return toAjax(emergencyLibraryItemVersionService.insertByBo(bo));
    }

    /**
     * 修改公共库冻结定义；published 后禁止 UPDATE
     */
    @SaCheckPermission("emergency:libraryItemVersion:edit")
    @Log(title = "公共库冻结定义；published 后禁止 UPDATE", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyLibraryItemVersionBo bo) {
        return toAjax(emergencyLibraryItemVersionService.updateByBo(bo));
    }

    /**
     * 删除公共库冻结定义；published 后禁止 UPDATE
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:libraryItemVersion:remove")
    @Log(title = "公共库冻结定义；published 后禁止 UPDATE", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyLibraryItemVersionService.deleteWithValidByIds(List.of(ids), true));
    }
}
