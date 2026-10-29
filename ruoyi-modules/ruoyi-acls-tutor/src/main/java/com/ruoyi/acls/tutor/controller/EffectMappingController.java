package com.ruoyi.acls.tutor.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.ruoyi.acls.tutor.effect.EffectMappingReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 效应映射表 — 导师视图报告端点。
 *
 * <p>与 {@code ContractController}（工程侧报告，面向开发者）刻意分开路径，
 * 避免两类不同读者的入口混在一起。若项目已有统一的导师端管理界面，
 * 建议把这个接口挂到那个界面下，而不是单独给导师一个新地址记。
 *
 * <p>返回纯 Markdown 文本，前端按需渲染（或先用浏览器插件/在线 Markdown 查看器
 * 验证内容，等有正式管理界面后再接入渲染组件）。
 */
@SaIgnore
@RestController
@RequestMapping("/acls/tutor/effect")
@RequiredArgsConstructor
public class EffectMappingController {

    private final EffectMappingReportService reportService;

    /**
     * 获取指定药物/操作的导师视图报告。
     * 例：GET /acls/tutor/effect/adenosine/report
     */
    @GetMapping(value = "/{drug}/report", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<String> report(@PathVariable String drug) {
        String report = reportService.generateReport(drug);
        return ResponseEntity.ok(report);
    }
}
