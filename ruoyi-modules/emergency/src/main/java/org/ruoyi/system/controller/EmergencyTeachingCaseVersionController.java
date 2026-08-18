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
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVersionVo;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseVersionBo;
import org.ruoyi.system.service.IEmergencyTeachingCaseVersionService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例定义版本；published 后禁止 UPDATE
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/teachingCaseVersion")
public class EmergencyTeachingCaseVersionController extends BaseController {

    private final IEmergencyTeachingCaseVersionService emergencyTeachingCaseVersionService;

    /**
     * 查询病例定义版本；published 后禁止 UPDATE列表
     */
    @SaCheckPermission("emergency:teachingCaseVersion:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyTeachingCaseVersionVo> list(EmergencyTeachingCaseVersionBo bo, PageQuery pageQuery) {
        return emergencyTeachingCaseVersionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例定义版本；published 后禁止 UPDATE列表
     */
    @SaCheckPermission("emergency:teachingCaseVersion:export")
    @Log(title = "病例定义版本；published 后禁止 UPDATE", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyTeachingCaseVersionBo bo, HttpServletResponse response) {
        List<EmergencyTeachingCaseVersionVo> list = emergencyTeachingCaseVersionService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例定义版本；published 后禁止 UPDATE", EmergencyTeachingCaseVersionVo.class, response);
    }

    /**
     * 获取病例定义版本；published 后禁止 UPDATE详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:teachingCaseVersion:query")
    @GetMapping("/{id}")
    public R<EmergencyTeachingCaseVersionVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyTeachingCaseVersionService.queryById(id));
    }

    /**
     * 新增病例定义版本；published 后禁止 UPDATE
     */
    @SaCheckPermission("emergency:teachingCaseVersion:add")
    @Log(title = "病例定义版本；published 后禁止 UPDATE", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyTeachingCaseVersionBo bo) {
        return toAjax(emergencyTeachingCaseVersionService.insertByBo(bo));
    }

    /**
     * 修改病例定义版本；published 后禁止 UPDATE
     */
    @SaCheckPermission("emergency:teachingCaseVersion:edit")
    @Log(title = "病例定义版本；published 后禁止 UPDATE", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyTeachingCaseVersionBo bo) {
        return toAjax(emergencyTeachingCaseVersionService.updateByBo(bo));
    }

    /**
     * 删除病例定义版本；published 后禁止 UPDATE
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:teachingCaseVersion:remove")
    @Log(title = "病例定义版本；published 后禁止 UPDATE", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyTeachingCaseVersionService.deleteWithValidByIds(List.of(ids), true));
    }
}
