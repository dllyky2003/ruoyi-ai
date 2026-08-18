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
import org.ruoyi.system.domain.vo.EmergencyCaseVersionLibraryRefVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionLibraryRefBo;
import org.ruoyi.system.service.IEmergencyCaseVersionLibraryRefService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例引用了哪些公共库版本；用于影响分析与发布校验
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/caseVersionLibraryRef")
public class EmergencyCaseVersionLibraryRefController extends BaseController {

    private final IEmergencyCaseVersionLibraryRefService emergencyCaseVersionLibraryRefService;

    /**
     * 查询病例引用了哪些公共库版本；用于影响分析与发布校验列表
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyCaseVersionLibraryRefVo> list(EmergencyCaseVersionLibraryRefBo bo, PageQuery pageQuery) {
        return emergencyCaseVersionLibraryRefService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例引用了哪些公共库版本；用于影响分析与发布校验列表
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:export")
    @Log(title = "病例引用了哪些公共库版本；用于影响分析与发布校验", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyCaseVersionLibraryRefBo bo, HttpServletResponse response) {
        List<EmergencyCaseVersionLibraryRefVo> list = emergencyCaseVersionLibraryRefService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例引用了哪些公共库版本；用于影响分析与发布校验", EmergencyCaseVersionLibraryRefVo.class, response);
    }

    /**
     * 获取病例引用了哪些公共库版本；用于影响分析与发布校验详细信息
     *
     * @param caseVersionId 主键
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:query")
    @GetMapping("/{caseVersionId}")
    public R<EmergencyCaseVersionLibraryRefVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long caseVersionId) {
        return R.ok(emergencyCaseVersionLibraryRefService.queryById(caseVersionId));
    }

    /**
     * 新增病例引用了哪些公共库版本；用于影响分析与发布校验
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:add")
    @Log(title = "病例引用了哪些公共库版本；用于影响分析与发布校验", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyCaseVersionLibraryRefBo bo) {
        return toAjax(emergencyCaseVersionLibraryRefService.insertByBo(bo));
    }

    /**
     * 修改病例引用了哪些公共库版本；用于影响分析与发布校验
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:edit")
    @Log(title = "病例引用了哪些公共库版本；用于影响分析与发布校验", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyCaseVersionLibraryRefBo bo) {
        return toAjax(emergencyCaseVersionLibraryRefService.updateByBo(bo));
    }

    /**
     * 删除病例引用了哪些公共库版本；用于影响分析与发布校验
     *
     * @param caseVersionIds 主键串
     */
    @SaCheckPermission("emergency:caseVersionLibraryRef:remove")
    @Log(title = "病例引用了哪些公共库版本；用于影响分析与发布校验", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseVersionIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] caseVersionIds) {
        return toAjax(emergencyCaseVersionLibraryRefService.deleteWithValidByIds(List.of(caseVersionIds), true));
    }
}
