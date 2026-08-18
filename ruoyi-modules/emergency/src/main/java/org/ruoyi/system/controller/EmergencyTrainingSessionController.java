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
import org.ruoyi.system.domain.vo.EmergencyTrainingSessionVo;
import org.ruoyi.system.domain.bo.EmergencyTrainingSessionBo;
import org.ruoyi.system.service.IEmergencyTrainingSessionService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 训练会话
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/trainingSession")
public class EmergencyTrainingSessionController extends BaseController {

    private final IEmergencyTrainingSessionService emergencyTrainingSessionService;

    /**
     * 查询训练会话列表
     */
    @SaCheckPermission("emergency:trainingSession:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyTrainingSessionVo> list(EmergencyTrainingSessionBo bo, PageQuery pageQuery) {
        return emergencyTrainingSessionService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出训练会话列表
     */
    @SaCheckPermission("emergency:trainingSession:export")
    @Log(title = "训练会话", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyTrainingSessionBo bo, HttpServletResponse response) {
        List<EmergencyTrainingSessionVo> list = emergencyTrainingSessionService.queryList(bo);
        ExcelUtil.exportExcel(list, "训练会话", EmergencyTrainingSessionVo.class, response);
    }

    /**
     * 获取训练会话详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:trainingSession:query")
    @GetMapping("/{id}")
    public R<EmergencyTrainingSessionVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyTrainingSessionService.queryById(id));
    }

    /**
     * 新增训练会话
     */
    @SaCheckPermission("emergency:trainingSession:add")
    @Log(title = "训练会话", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyTrainingSessionBo bo) {
        return toAjax(emergencyTrainingSessionService.insertByBo(bo));
    }

    /**
     * 修改训练会话
     */
    @SaCheckPermission("emergency:trainingSession:edit")
    @Log(title = "训练会话", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyTrainingSessionBo bo) {
        return toAjax(emergencyTrainingSessionService.updateByBo(bo));
    }

    /**
     * 删除训练会话
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:trainingSession:remove")
    @Log(title = "训练会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyTrainingSessionService.deleteWithValidByIds(List.of(ids), true));
    }
}
