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
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVo;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseBo;
import org.ruoyi.system.service.IEmergencyTeachingCaseService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例身份；不保存具体教学定义
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/teachingCase")
public class EmergencyTeachingCaseController extends BaseController {

    private final IEmergencyTeachingCaseService emergencyTeachingCaseService;

    /**
     * 查询病例身份；不保存具体教学定义列表
     */
    @SaCheckPermission("emergency:teachingCase:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyTeachingCaseVo> list(EmergencyTeachingCaseBo bo, PageQuery pageQuery) {
        return emergencyTeachingCaseService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例身份；不保存具体教学定义列表
     */
    @SaCheckPermission("emergency:teachingCase:export")
    @Log(title = "病例身份；不保存具体教学定义", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyTeachingCaseBo bo, HttpServletResponse response) {
        List<EmergencyTeachingCaseVo> list = emergencyTeachingCaseService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例身份；不保存具体教学定义", EmergencyTeachingCaseVo.class, response);
    }

    /**
     * 获取病例身份；不保存具体教学定义详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:teachingCase:query")
    @GetMapping("/{id}")
    public R<EmergencyTeachingCaseVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyTeachingCaseService.queryById(id));
    }

    /**
     * 新增病例身份；不保存具体教学定义
     */
    @SaCheckPermission("emergency:teachingCase:add")
    @Log(title = "病例身份；不保存具体教学定义", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyTeachingCaseBo bo) {
        return toAjax(emergencyTeachingCaseService.insertByBo(bo));
    }

    /**
     * 修改病例身份；不保存具体教学定义
     */
    @SaCheckPermission("emergency:teachingCase:edit")
    @Log(title = "病例身份；不保存具体教学定义", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyTeachingCaseBo bo) {
        return toAjax(emergencyTeachingCaseService.updateByBo(bo));
    }

    /**
     * 删除病例身份；不保存具体教学定义
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:teachingCase:remove")
    @Log(title = "病例身份；不保存具体教学定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyTeachingCaseService.deleteWithValidByIds(List.of(ids), true));
    }
}
