package org.ruoyi.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.core.validate.AddGroup;
import org.ruoyi.common.core.validate.EditGroup;
import org.ruoyi.common.excel.utils.ExcelUtil;
import org.ruoyi.common.idempotent.annotation.RepeatSubmit;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.system.domain.CommonOptions;
import org.ruoyi.system.domain.bo.EmergencyLibraryItemVersionBo;
import org.ruoyi.system.domain.bo.EmergencyTeachingCaseVersionBo;
import org.ruoyi.system.domain.vo.EmergencyLibraryItemVersionVo;
import org.ruoyi.system.domain.vo.EmergencyTeachingCaseVersionVo;
import org.ruoyi.system.service.IEmergencyLibraryItemVersionService;
import org.ruoyi.system.service.IEmergencyTeachingCaseVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 急救方案版本管理（公共库冻结定义；published 后禁止 UPDATE）
 *
 * @author ageerle
 * @date 2026-08-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/emergency")
public class EmergencyApiController extends BaseController {

    private final IEmergencyTeachingCaseVersionService emergencyTeachingCaseVersionService;
    @Autowired
    private CommonOptions commonOptions;


    @GetMapping("/listAllTeachingCaseVersion")
    public R<List<EmergencyTeachingCaseVersionVo>> listAll(EmergencyTeachingCaseVersionBo bo) {
        if (StringUtils.isBlank(bo.getStatus())) {
            bo.setStatus("published");
        }
        List<EmergencyTeachingCaseVersionVo> list = emergencyTeachingCaseVersionService.queryList(bo);
        return R.ok(list);
    }


    @GetMapping("/getSystemDefineOptions")
    public CommonOptions getSystemDefineOptions() {
        return commonOptions;
    }
}
