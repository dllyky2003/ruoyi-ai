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
import org.ruoyi.system.domain.vo.EmergencySessionEventLogVo;
import org.ruoyi.system.domain.bo.EmergencySessionEventLogBo;
import org.ruoyi.system.service.IEmergencySessionEventLogService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 会话事件日志（追加式/可审计）
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/sessionEventLog")
public class EmergencySessionEventLogController extends BaseController {

    private final IEmergencySessionEventLogService emergencySessionEventLogService;

    /**
     * 查询会话事件日志（追加式/可审计）列表
     */
    @SaCheckPermission("emergency:sessionEventLog:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencySessionEventLogVo> list(EmergencySessionEventLogBo bo, PageQuery pageQuery) {
        return emergencySessionEventLogService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出会话事件日志（追加式/可审计）列表
     */
    @SaCheckPermission("emergency:sessionEventLog:export")
    @Log(title = "会话事件日志（追加式/可审计）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencySessionEventLogBo bo, HttpServletResponse response) {
        List<EmergencySessionEventLogVo> list = emergencySessionEventLogService.queryList(bo);
        ExcelUtil.exportExcel(list, "会话事件日志（追加式/可审计）", EmergencySessionEventLogVo.class, response);
    }

    /**
     * 获取会话事件日志（追加式/可审计）详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:sessionEventLog:query")
    @GetMapping("/{id}")
    public R<EmergencySessionEventLogVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencySessionEventLogService.queryById(id));
    }

    /**
     * 新增会话事件日志（追加式/可审计）
     */
    @SaCheckPermission("emergency:sessionEventLog:add")
    @Log(title = "会话事件日志（追加式/可审计）", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencySessionEventLogBo bo) {
        return toAjax(emergencySessionEventLogService.insertByBo(bo));
    }

    /**
     * 修改会话事件日志（追加式/可审计）
     */
    @SaCheckPermission("emergency:sessionEventLog:edit")
    @Log(title = "会话事件日志（追加式/可审计）", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencySessionEventLogBo bo) {
        return toAjax(emergencySessionEventLogService.updateByBo(bo));
    }

    /**
     * 删除会话事件日志（追加式/可审计）
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:sessionEventLog:remove")
    @Log(title = "会话事件日志（追加式/可审计）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencySessionEventLogService.deleteWithValidByIds(List.of(ids), true));
    }
}
