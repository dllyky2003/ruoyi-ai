package com.ruoyi.acls.adapter;

import com.ruoyi.acls.engine.AclsCommandEngine;
import com.ruoyi.acls.engine.CommandResult;
import com.ruoyi.acls.router.SemanticRouter;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.spi.IntelligentCommandProcessor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ACLS 新引擎适配器 —— 实现旧模块定义的策略接口
 * 这是新旧模块之间的唯一桥梁
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AclsIntelligentProcessor implements IntelligentCommandProcessor {

    private final AclsCommandEngine engine;
    private final SkillRegistry skillRegistry;
    private final SemanticRouter semanticRouter;

    @PostConstruct
    public void init() {
        log.info("✅ AclsIntelligentProcessor Bean 初始化完成");
        log.info("   SkillRegistry 中共有 {} 个技能", skillRegistry.size());
    }

    /**
     * 判断当前处理器是否支持该意图
     *
     * <p>支持规则：意图编码存在于 SkillRegistry 中，或 SemanticRouter 中存在对应路由
     */
    @Override
    public boolean supports(String intentId) {
        if (intentId == null) {
            return false;
        }

        // ★ META_* 不属于技能链路，即使有通配符路由也不该拦截
        if (intentId.startsWith("META_")) {
            log.debug("supports: intentId={}, META_* 不归此处理器处理", intentId);
            return false;
        }

        if (skillRegistry.exists(intentId)) {
            return true;
        }
        return semanticRouter.hasRouteForIntent(intentId);
    }

    @Override
    public Map<String, Object> processCommand(String sessionId, IntentCommand command) {
        try {
            CommandResult result = engine.processCommand(command, sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("animations", result.getAnimations() != null ? result.getAnimations() : List.of());
            response.put("contextUpdates", result.getContextUpdates() != null ? result.getContextUpdates() : Map.of());

            String responseText;
            if (result.isSuccess()) {
                responseText = result.getResponseText();
                if (responseText == null) {
                    responseText = "";    // 允许空，前端自行处理
                }
            } else {
                responseText = result.getErrorMessage() != null ? result.getErrorMessage() : "指令执行失败";
                // ★ 新增：显式映射错误消息，供 SessionService 识别"处理器明确拒绝"
                if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
                    response.put("errorMessage", result.getErrorMessage());
                }
            }
            response.put("responseText", responseText);

            return response;

        } catch (IllegalArgumentException | IllegalStateException e) {
            // 业务失败（如参数校验、状态机无法转换），直接返回失败响应，不触发降级
            log.warn("新引擎业务失败: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("responseText", "操作失败: " + e.getMessage());
            // ★ 新增：与主失败路径保持一致的字段映射
            response.put("errorMessage", e.getMessage());
            response.put("animations", List.of());
            response.put("contextUpdates", Map.of());
            return response;
        } catch (Exception e) {
            log.error("新引擎系统异常，将抛出异常让调用方降级", e);
            throw new RuntimeException("新引擎执行失败: " + e.getMessage(), e);
        }
    }
}
