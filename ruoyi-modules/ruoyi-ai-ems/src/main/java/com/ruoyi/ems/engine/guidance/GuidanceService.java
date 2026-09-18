package com.ruoyi.ems.engine.guidance;

import com.ruoyi.ems.engine.fsm.ACLSStates;
import com.ruoyi.ems.service.ScenarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 教学引导服务
 *
 * 职责：
 * 1. 根据当前状态和等待时间提供渐进式提示
 * 2. 提示级别：Level 1（温和提醒）→ Level 2（明确指引）→ Level 3（直接建议）
 * 3. 支持从场景配置中加载预设提示
 */
@Slf4j
@Service
public class GuidanceService {

    private static final String GUIDANCE_CACHE_PREFIX = "ems:guidance:";

    // 提示等待阈值（秒）
    private static final int LEVEL1_WAIT_SECONDS = 15;
    private static final int LEVEL2_WAIT_SECONDS = 30;
    private static final int LEVEL3_WAIT_SECONDS = 45;

    // 默认提示（当场景未配置时使用）
    private static final Map<String, List<String>> DEFAULT_HINTS = Map.of(
        "INITIAL_ASSESSMENT", List.of(
            "请评估患者心率和脉搏状态",
            "确认心率是否大于150次/分，是否存在脉搏",
            "立即评估心率和脉搏！"
        ),
        "STABILITY_CHECK", List.of(
            "请评估患者血流动力学状态",
            "检查是否存在低血压、意识改变、休克等不稳定体征",
            "患者是否稳定？请快速判断！"
        ),
        "QRS_EVALUATION", List.of(
            "请读取12导联心电图",
            "判断QRS宽度是否小于0.12秒",
            "立即判断QRS宽度！"
        ),
        "VAGAL_MANEUVER", List.of(
            "建议尝试迷走神经刺激",
            "可考虑Valsalva动作或颈动脉窦按摩",
            "立即执行迷走神经刺激！"
        ),
        "ADENOSINE_6MG", List.of(
            "如迷走刺激无效，可考虑腺苷",
            "准备腺苷6mg快速IV推注，后续用盐水冲管",
            "立即给腺苷6mg快速IV推注！"
        ),
        "ADENOSINE_12MG", List.of(
            "如腺苷6mg无效，可考虑加倍剂量",
            "准备腺苷12mg快速IV推注，后续用盐水冲管",
            "立即给腺苷12mg快速IV推注！"
        ),
        "BETA_BLOCKER_OR_CCB", List.of(
            "如腺苷无效，可考虑β阻滞剂或钙通道阻滞剂",
            "根据患者情况选择β阻滞剂或钙通道阻滞剂",
            "请尽快给予β阻滞剂或钙通道阻滞剂！"
        ),
        "EXPERT_CONSULT", List.of(
            "复杂心律可能需要专家会诊",
            "请考虑邀请专家会诊",
            "请立即联系专家会诊！"
        ),
        "SYNCHRONIZED_CARDIOVERSION", List.of(
            "患者不稳定，准备同步电复律",
            "设置同步模式，能量≥200J，给予镇静",
            "立即执行同步电复律！"
        ),
        "POST_TREATMENT_REASSESS", List.of(
            "治疗后请重新评估患者状态",
            "检查心率、血压、意识是否改善",
            "立即重新评估患者状态！"
        )
    );

    private final ScenarioService scenarioService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, LocalDateTime> lastActionTimeCache;

    public GuidanceService(ScenarioService scenarioService,
                           RedisTemplate<String, Object> redisTemplate) {
        this.scenarioService = scenarioService;
        this.redisTemplate = redisTemplate;
        this.lastActionTimeCache = new ConcurrentHashMap<>();
    }

    /**
     * 获取当前状态的提示
     *
     * @param sessionId    会话ID
     * @param currentState 当前状态
     * @param idleSeconds  空闲秒数（从上次操作至今）
     * @return 提示文本，如无需提示返回 null
     */
    public GuidanceHint getHint(String sessionId, ACLSStates currentState, long idleSeconds) {
        if (currentState == null) {
            return null;
        }

        String stateName = currentState.name();
        List<String> hints = loadHintsForState(sessionId, stateName);

        if (hints == null || hints.isEmpty()) {
            return null;
        }

        GuidanceLevel level;
        String hintText;

        if (idleSeconds > LEVEL3_WAIT_SECONDS && hints.size() >= 3) {
            level = GuidanceLevel.LEVEL_3;
            hintText = hints.get(2);
        } else if (idleSeconds > LEVEL2_WAIT_SECONDS && hints.size() >= 2) {
            level = GuidanceLevel.LEVEL_2;
            hintText = hints.get(1);
        } else if (idleSeconds > LEVEL1_WAIT_SECONDS && hints.size() >= 1) {
            level = GuidanceLevel.LEVEL_1;
            hintText = hints.get(0);
        } else {
            return null;  // 未达到提示阈值
        }

        return GuidanceHint.builder()
            .sessionId(sessionId)
            .state(currentState)
            .level(level)
            .text(hintText)
            .idleSeconds(idleSeconds)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * 获取当前状态的提示（重载：自动计算空闲时间）
     */
    public GuidanceHint getHint(String sessionId, ACLSStates currentState) {
        long idleSeconds = getIdleSeconds(sessionId);
        return getHint(sessionId, currentState, idleSeconds);
    }

    /**
     * 记录用户操作时间（每次检测到操作时调用）
     */
    public void recordAction(String sessionId) {
        lastActionTimeCache.put(sessionId, LocalDateTime.now());
        redisTemplate.opsForValue().set(
            GUIDANCE_CACHE_PREFIX + sessionId + ":lastAction",
            LocalDateTime.now(),
            Duration.ofHours(2)
        );
    }

    /**
     * 计算空闲秒数
     */
    private long getIdleSeconds(String sessionId) {
        LocalDateTime lastAction = lastActionTimeCache.get(sessionId);
        if (lastAction == null) {
            // 尝试从Redis获取
            Object cached = redisTemplate.opsForValue()
                .get(GUIDANCE_CACHE_PREFIX + sessionId + ":lastAction");
            if (cached instanceof LocalDateTime) {
                lastAction = (LocalDateTime) cached;
                lastActionTimeCache.put(sessionId, lastAction);
            } else {
                // 默认从会话开始算起
                return 0;
            }
        }
        return Duration.between(lastAction, LocalDateTime.now()).getSeconds();
    }

    /**
     * 加载状态对应的提示列表
     * 优先从场景配置加载，否则使用默认提示
     */
    @SuppressWarnings("unchecked")
    private List<String> loadHintsForState(String sessionId, String stateName) {
        // 1. 尝试从会话关联的场景配置加载
        try {
            String scenarioId = (String) redisTemplate.opsForHash()
                .get("ems:session:context:" + sessionId, "scenarioId");
            if (scenarioId != null) {
                Map<String, Object> config = scenarioService.getScenarioConfig(scenarioId);
                if (config != null) {
                    Map<String, Object> scenario = (Map<String, Object>) config.get("scenario");
                    if (scenario != null) {
                        List<Map<String, Object>> expectedPath =
                            (List<Map<String, Object>>) scenario.get("expectedPath");
                        if (expectedPath != null) {
                            for (Map<String, Object> step : expectedPath) {
                                if (stateName.equals(step.get("step"))) {
                                    Map<String, Object> hints = (Map<String, Object>) step.get("hints");
                                    if (hints != null) {
                                        return List.of(
                                            (String) hints.get("level_1"),
                                            (String) hints.get("level_2"),
                                            (String) hints.get("level_3")
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从场景配置加载提示失败，使用默认提示: {}", e.getMessage());
        }

        // 2. 使用默认提示
        return DEFAULT_HINTS.get(stateName);
    }

    /**
     * 清除会话的提示缓存（会话结束时调用）
     */
    public void clearSessionCache(String sessionId) {
        lastActionTimeCache.remove(sessionId);
        redisTemplate.delete(GUIDANCE_CACHE_PREFIX + sessionId + ":lastAction");
        log.debug("清除会话提示缓存: {}", sessionId);
    }

    /**
     * 提示级别枚举
     */
    public enum GuidanceLevel {
        LEVEL_1("温和提醒", "💡"),
        LEVEL_2("明确指引", "📌"),
        LEVEL_3("直接建议", "⚠️");

        private final String description;
        private final String icon;

        GuidanceLevel(String description, String icon) {
            this.description = description;
            this.icon = icon;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }
    }
}
