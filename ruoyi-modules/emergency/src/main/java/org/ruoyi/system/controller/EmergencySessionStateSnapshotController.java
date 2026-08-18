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
import org.ruoyi.system.domain.vo.EmergencySessionStateSnapshotVo;
import org.ruoyi.system.domain.bo.EmergencySessionStateSnapshotBo;
import org.ruoyi.system.service.IEmergencySessionStateSnapshotService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 会话状态快照（追加式）
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/sessionStateSnapshot")
public class EmergencySessionStateSnapshotController extends BaseController {

    private final IEmergencySessionStateSnapshotService emergencySessionStateSnapshotService;

    /**
     * 查询会话状态快照（追加式）列表
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencySessionStateSnapshotVo> list(EmergencySessionStateSnapshotBo bo, PageQuery pageQuery) {
        return emergencySessionStateSnapshotService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出会话状态快照（追加式）列表
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:export")
    @Log(title = "会话状态快照（追加式）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencySessionStateSnapshotBo bo, HttpServletResponse response) {
        List<EmergencySessionStateSnapshotVo> list = emergencySessionStateSnapshotService.queryList(bo);
        ExcelUtil.exportExcel(list, "会话状态快照（追加式）", EmergencySessionStateSnapshotVo.class, response);
    }

    /**
     * 获取会话状态快照（追加式）详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:query")
    @GetMapping("/{id}")
    public R<EmergencySessionStateSnapshotVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencySessionStateSnapshotService.queryById(id));
    }

    /**
     * 新增会话状态快照（追加式）
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:add")
    @Log(title = "会话状态快照（追加式）", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencySessionStateSnapshotBo bo) {
        return toAjax(emergencySessionStateSnapshotService.insertByBo(bo));
    }

    /**
     * 修改会话状态快照（追加式）
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:edit")
    @Log(title = "会话状态快照（追加式）", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencySessionStateSnapshotBo bo) {
        return toAjax(emergencySessionStateSnapshotService.updateByBo(bo));
    }

    /**
     * 删除会话状态快照（追加式）
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:sessionStateSnapshot:remove")
    @Log(title = "会话状态快照（追加式）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencySessionStateSnapshotService.deleteWithValidByIds(List.of(ids), true));
    }
}
