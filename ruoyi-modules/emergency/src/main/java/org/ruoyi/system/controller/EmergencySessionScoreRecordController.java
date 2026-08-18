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
import org.ruoyi.system.domain.vo.EmergencySessionScoreRecordVo;
import org.ruoyi.system.domain.bo.EmergencySessionScoreRecordBo;
import org.ruoyi.system.service.IEmergencySessionScoreRecordService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 会话评分记录（追加式）
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/sessionScoreRecord")
public class EmergencySessionScoreRecordController extends BaseController {

    private final IEmergencySessionScoreRecordService emergencySessionScoreRecordService;

    /**
     * 查询会话评分记录（追加式）列表
     */
    @SaCheckPermission("emergency:sessionScoreRecord:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencySessionScoreRecordVo> list(EmergencySessionScoreRecordBo bo, PageQuery pageQuery) {
        return emergencySessionScoreRecordService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出会话评分记录（追加式）列表
     */
    @SaCheckPermission("emergency:sessionScoreRecord:export")
    @Log(title = "会话评分记录（追加式）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencySessionScoreRecordBo bo, HttpServletResponse response) {
        List<EmergencySessionScoreRecordVo> list = emergencySessionScoreRecordService.queryList(bo);
        ExcelUtil.exportExcel(list, "会话评分记录（追加式）", EmergencySessionScoreRecordVo.class, response);
    }

    /**
     * 获取会话评分记录（追加式）详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:sessionScoreRecord:query")
    @GetMapping("/{id}")
    public R<EmergencySessionScoreRecordVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencySessionScoreRecordService.queryById(id));
    }

    /**
     * 新增会话评分记录（追加式）
     */
    @SaCheckPermission("emergency:sessionScoreRecord:add")
    @Log(title = "会话评分记录（追加式）", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencySessionScoreRecordBo bo) {
        return toAjax(emergencySessionScoreRecordService.insertByBo(bo));
    }

    /**
     * 修改会话评分记录（追加式）
     */
    @SaCheckPermission("emergency:sessionScoreRecord:edit")
    @Log(title = "会话评分记录（追加式）", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencySessionScoreRecordBo bo) {
        return toAjax(emergencySessionScoreRecordService.updateByBo(bo));
    }

    /**
     * 删除会话评分记录（追加式）
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:sessionScoreRecord:remove")
    @Log(title = "会话评分记录（追加式）", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencySessionScoreRecordService.deleteWithValidByIds(List.of(ids), true));
    }
}
