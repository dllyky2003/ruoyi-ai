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
import org.ruoyi.system.domain.vo.EmergencyMediaAssetVo;
import org.ruoyi.system.domain.bo.EmergencyMediaAssetBo;
import org.ruoyi.system.service.IEmergencyMediaAssetService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 文件仅存对象存储，数据库保存元数据
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/mediaAsset")
public class EmergencyMediaAssetController extends BaseController {

    private final IEmergencyMediaAssetService emergencyMediaAssetService;

    /**
     * 查询文件仅存对象存储，数据库保存元数据列表
     */
    @SaCheckPermission("emergency:mediaAsset:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyMediaAssetVo> list(EmergencyMediaAssetBo bo, PageQuery pageQuery) {
        return emergencyMediaAssetService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出文件仅存对象存储，数据库保存元数据列表
     */
    @SaCheckPermission("emergency:mediaAsset:export")
    @Log(title = "文件仅存对象存储，数据库保存元数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyMediaAssetBo bo, HttpServletResponse response) {
        List<EmergencyMediaAssetVo> list = emergencyMediaAssetService.queryList(bo);
        ExcelUtil.exportExcel(list, "文件仅存对象存储，数据库保存元数据", EmergencyMediaAssetVo.class, response);
    }

    /**
     * 获取文件仅存对象存储，数据库保存元数据详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("emergency:mediaAsset:query")
    @GetMapping("/{id}")
    public R<EmergencyMediaAssetVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long id) {
        return R.ok(emergencyMediaAssetService.queryById(id));
    }

    /**
     * 新增文件仅存对象存储，数据库保存元数据
     */
    @SaCheckPermission("emergency:mediaAsset:add")
    @Log(title = "文件仅存对象存储，数据库保存元数据", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyMediaAssetBo bo) {
        return toAjax(emergencyMediaAssetService.insertByBo(bo));
    }

    /**
     * 修改文件仅存对象存储，数据库保存元数据
     */
    @SaCheckPermission("emergency:mediaAsset:edit")
    @Log(title = "文件仅存对象存储，数据库保存元数据", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyMediaAssetBo bo) {
        return toAjax(emergencyMediaAssetService.updateByBo(bo));
    }

    /**
     * 删除文件仅存对象存储，数据库保存元数据
     *
     * @param ids 主键串
     */
    @SaCheckPermission("emergency:mediaAsset:remove")
    @Log(title = "文件仅存对象存储，数据库保存元数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(emergencyMediaAssetService.deleteWithValidByIds(List.of(ids), true));
    }
}
