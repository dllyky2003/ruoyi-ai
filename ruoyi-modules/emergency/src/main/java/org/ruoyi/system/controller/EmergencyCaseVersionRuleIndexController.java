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
import org.ruoyi.system.domain.vo.EmergencyCaseVersionRuleIndexVo;
import org.ruoyi.system.domain.bo.EmergencyCaseVersionRuleIndexBo;
import org.ruoyi.system.service.IEmergencyCaseVersionRuleIndexService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;

/**
 * 病例版本的规则索引
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency/caseVersionRuleIndex")
public class EmergencyCaseVersionRuleIndexController extends BaseController {

    private final IEmergencyCaseVersionRuleIndexService emergencyCaseVersionRuleIndexService;

    /**
     * 查询病例版本的规则索引列表
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:list")
    @GetMapping("/list")
    public TableDataInfo<EmergencyCaseVersionRuleIndexVo> list(EmergencyCaseVersionRuleIndexBo bo, PageQuery pageQuery) {
        return emergencyCaseVersionRuleIndexService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出病例版本的规则索引列表
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:export")
    @Log(title = "病例版本的规则索引", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EmergencyCaseVersionRuleIndexBo bo, HttpServletResponse response) {
        List<EmergencyCaseVersionRuleIndexVo> list = emergencyCaseVersionRuleIndexService.queryList(bo);
        ExcelUtil.exportExcel(list, "病例版本的规则索引", EmergencyCaseVersionRuleIndexVo.class, response);
    }

    /**
     * 获取病例版本的规则索引详细信息
     *
     * @param caseVersionId 主键
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:query")
    @GetMapping("/{caseVersionId}")
    public R<EmergencyCaseVersionRuleIndexVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long caseVersionId) {
        return R.ok(emergencyCaseVersionRuleIndexService.queryById(caseVersionId));
    }

    /**
     * 新增病例版本的规则索引
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:add")
    @Log(title = "病例版本的规则索引", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody EmergencyCaseVersionRuleIndexBo bo) {
        return toAjax(emergencyCaseVersionRuleIndexService.insertByBo(bo));
    }

    /**
     * 修改病例版本的规则索引
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:edit")
    @Log(title = "病例版本的规则索引", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody EmergencyCaseVersionRuleIndexBo bo) {
        return toAjax(emergencyCaseVersionRuleIndexService.updateByBo(bo));
    }

    /**
     * 删除病例版本的规则索引
     *
     * @param caseVersionIds 主键串
     */
    @SaCheckPermission("emergency:caseVersionRuleIndex:remove")
    @Log(title = "病例版本的规则索引", businessType = BusinessType.DELETE)
    @DeleteMapping("/{caseVersionIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] caseVersionIds) {
        return toAjax(emergencyCaseVersionRuleIndexService.deleteWithValidByIds(List.of(caseVersionIds), true));
    }
}
