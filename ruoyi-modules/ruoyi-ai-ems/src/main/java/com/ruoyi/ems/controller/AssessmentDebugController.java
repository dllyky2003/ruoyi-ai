package com.ruoyi.ems.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.ems.spi.AssessmentQueryService;
import com.ruoyi.ems.spi.dto.BeatSelectionView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

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

    public AssessmentDebugController(ObjectProvider<AssessmentQueryService> provider) {
        this.assessmentQueryServiceProvider = provider;
    }

    @GetMapping("/beats/{sessionId}")
    public List<BeatSelectionView> getBeatSelections(@PathVariable("sessionId") String sessionId) {
        AssessmentQueryService svc = assessmentQueryServiceProvider.getIfAvailable();
        if (svc == null) {
            return Collections.emptyList();
        }
        return svc.getBeatSelections(sessionId);
    }
}
