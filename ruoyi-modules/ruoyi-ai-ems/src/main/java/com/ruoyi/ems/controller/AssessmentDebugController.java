package com.ruoyi.ems.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.ems.spi.AssessmentQueryService;
import com.ruoyi.ems.spi.CaseConfigReloadService;
import com.ruoyi.ems.spi.dto.BeatSelectionView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试用：查询 AssessmentState 里的 beat 选择证据。
 *
 * <p>生产环境应通过正式 ReportController 暴露，本类仅用于本轮验证，
 * 后续会删除或迁移。</p>
 */
@SaIgnore
@RestController
@RequestMapping("/ems/assessment-debug")
public class AssessmentDebugController {

    private final ObjectProvider<AssessmentQueryService> assessmentQueryServiceProvider;
    private final ObjectProvider<CaseConfigReloadService> reloadServiceProvider;

    public AssessmentDebugController(
        ObjectProvider<AssessmentQueryService> assessmentQueryServiceProvider,
        ObjectProvider<CaseConfigReloadService> reloadServiceProvider) {
        this.assessmentQueryServiceProvider = assessmentQueryServiceProvider;
        this.reloadServiceProvider = reloadServiceProvider;
    }

    @GetMapping("/beats/{sessionId}")
    public List<BeatSelectionView> getBeatSelections(@PathVariable("sessionId") String sessionId) {
        AssessmentQueryService svc = assessmentQueryServiceProvider.getIfAvailable();
        if (svc == null) {
            return Collections.emptyList();
        }
        return svc.getBeatSelections(sessionId);
    }
   // http://localhost:6039/ems/assessment-debug/reload/stable_narrow
    @GetMapping("/reload/{caseId}")
    public Map<String, Object> reloadCase(@PathVariable("caseId") String caseId) {
        Map<String, Object> r = new HashMap<>();
        CaseConfigReloadService svc = reloadServiceProvider.getIfAvailable();
        if (svc == null) {
            r.put("ok", false);
            r.put("error", "CaseConfigReloadService 未注册");
            return r;
        }
        try {
            svc.reloadCase(caseId);
            r.put("ok", true);
            r.put("caseId", caseId);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", e.getMessage());
        }
        return r;
    }
    //http://localhost:6039/ems/assessment-debug/reload-all
    @GetMapping("/reload-all")
    public Map<String, Object> reloadAll() {
        Map<String, Object> r = new HashMap<>();
        CaseConfigReloadService svc = reloadServiceProvider.getIfAvailable();
        if (svc == null) {
            r.put("ok", false);
            r.put("error", "CaseConfigReloadService 未注册");
            return r;
        }
        try {
            svc.reloadAll();
            r.put("ok", true);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", e.getMessage());
        }
        return r;
    }
}
