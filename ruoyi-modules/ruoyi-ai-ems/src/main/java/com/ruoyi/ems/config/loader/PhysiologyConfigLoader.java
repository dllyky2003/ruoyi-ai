package com.ruoyi.ems.config.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ruoyi.ems.domain.OperationEffect;
import com.ruoyi.ems.domain.PhysiologyConfig;
import com.ruoyi.ems.domain.StatePhysiology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生理配置加载器（唯一入口）。
 *
 * <p>目录约定：
 * <pre>
 * classpath:physiology/
 *   {caseType}/
 *     default.yml
 *     1.0.0.yml
 *     1.1.0.yml
 * </pre>
 *
 * <p>加载策略：
 * <ul>
 *   <li>按 (caseType, version) 缓存</li>
 *   <li>加载后计算 SHA-256，写入 configHash</li>
 *   <li>找不到时回退到 _default/default.yml</li>
 * </ul>
 */
@Slf4j
@Component
public class PhysiologyConfigLoader {

    private static final String BASE_DIR = "physiology";
    private static final String DEFAULT_VERSION = "default";
    private static final String FALLBACK_CASE = "_default";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, PhysiologyConfig> cache = new ConcurrentHashMap<>();

    // ============================================================
    // 主入口
    // ============================================================

    public PhysiologyConfig loadFor(String caseType, String version) {
        if (caseType == null || caseType.isBlank()) {
            return loadDefault();
        }
        String ver = (version != null && !version.isBlank()) ? version : DEFAULT_VERSION;
        String key = caseType + "@" + ver;

        return cache.computeIfAbsent(key, k -> {
            PhysiologyConfig cfg = tryLoad(caseType, ver);

            // 回退：先试同名 default.yml，再试 _default/default.yml
            if (cfg == null && !DEFAULT_VERSION.equals(ver)) {
                log.warn("⚠️ 配置缺失 {}/{}，回退到 {}/default", caseType, ver, caseType);
                cfg = tryLoad(caseType, DEFAULT_VERSION);
            }
            if (cfg == null && !FALLBACK_CASE.equals(caseType)) {
                log.warn("⚠️ 配置缺失 {}/{}，回退到全局默认", caseType, ver);
                cfg = loadDefault();
            }

            if (cfg != null) {
                log.info("✅ 生理配置已加载: caseType={}, version={}, states={}, operations={}, hash={}",
                    caseType, ver,
                    cfg.getStatePhysiology() != null ? cfg.getStatePhysiology().size() : 0,
                    cfg.getOperationEffects() != null ? cfg.getOperationEffects().size() : 0,
                    abbreviate(cfg.getConfigHash()));
            } else {
                log.error("❌ 生理配置完全缺失: caseType={}, version={}", caseType, ver);
            }
            return cfg;
        });
    }

    /**
     * 兼容单参调用：只给 caseId，无 version。
     * <p>供 EffectManager / StateMachineFacade / CaseConfigLoader 使用。
     */
    public PhysiologyConfig loadCase(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return loadDefault();
        }
        return loadFor(caseId, null);
    }

    public PhysiologyConfig loadDefault() {
        return tryLoad(FALLBACK_CASE, DEFAULT_VERSION);
    }

    // ============================================================
    // 状态查询（兼容 PhysiologyConfigService.getState）
    // ============================================================

    /**
     * 获取某状态下的生理基线。
     *
     * <p>stateId 的语义：
     * <ul>
     *   <li>如果状态机状态 ID 直接等于 Unity stateName（如 "室上速(SVT)"），直接查 statePhysiology</li>
     *   <li>如果状态机状态 ID 是内部编码（如 "F_101_ASSESS_STABILITY"），
     *       通过 rhythmMapping 反查 Unity stateName 后再查</li>
     * </ul>
     *
     * @param caseId  案例 ID / caseType
     * @param stateId 状态机状态 ID 或 Unity stateName
     * @return 找到的 StatePhysiology；找不到返回 null（不抛异常）
     */
    public StatePhysiology getState(String caseId, String stateId) {
        if (caseId == null || stateId == null) {
            return null;
        }
        PhysiologyConfig config = loadCase(caseId);
        if (config == null || config.getStatePhysiology() == null) {
            return null;
        }

        // 1. 直接按 stateId 查
        StatePhysiology direct = config.getStatePhysiology().get(stateId);
        if (direct != null) {
            return direct;
        }

        // 2. 通过 rhythmMapping 反查（stateId → Unity stateName）
        if (config.getRhythmMapping() != null) {
            String mapped = config.getRhythmMapping().get(stateId);
            if (mapped != null) {
                return config.getStatePhysiology().get(mapped);
            }
        }

        log.debug("状态 {} 在案例 {} 生理配置中未定义", stateId, caseId);
        return null;
    }

    /**
     * 兼容旧接口：返回全部状态基线表。
     */
    public Map<String, StatePhysiology> getInterventionStates(String caseId) {
        PhysiologyConfig config = loadCase(caseId);
        return config != null ? config.getStatePhysiology() : null;
    }

    // ============================================================
    // 缓存管理
    // ============================================================

    public void evictCache(String caseId) {
        if (caseId == null) return;
        cache.keySet().removeIf(k -> k.startsWith(caseId + "@"));
        log.debug("🧹 清除案例 {} 的生理配置缓存", caseId);
    }

    public void evictAll() {
        cache.clear();
        log.info("🧹 生理配置缓存已全部清除");
    }

    // ============================================================
    // 内部
    // ============================================================

    private PhysiologyConfig tryLoad(String caseType, String version) {
        String path = String.format("%s/%s/%s.yml", BASE_DIR, caseType, version);
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            log.debug("配置不存在: {}", path);
            return null;
        }

        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            PhysiologyConfig cfg = yamlMapper.readValue(bytes, PhysiologyConfig.class);

            cfg.setConfigVersion(version);
            cfg.setConfigHash(sha256(bytes));

            validate(cfg, path);
            return cfg;
        } catch (IOException e) {
            log.error("配置解析失败: path={}, err={}", path, e.getMessage(), e);
            return null;
        }
    }

    private void validate(PhysiologyConfig cfg, String path) {
        if (cfg.getRhythmMapping() == null || cfg.getRhythmMapping().isEmpty()) {
            log.warn("⚠️ 配置缺少 rhythmMapping: {}", path);
        }
        if (cfg.getStatePhysiology() == null || cfg.getStatePhysiology().isEmpty()) {
            log.warn("⚠️ 配置缺少 statePhysiology: {}", path);
        }

        if (cfg.getOperationEffects() != null && cfg.getStatePhysiology() != null) {
            cfg.getOperationEffects().forEach((opId, effects) -> {
                if (effects == null) return;
                for (OperationEffect eff : effects) {
                    if (eff.getUnityStateName() != null
                        && !cfg.getStatePhysiology().containsKey(eff.getUnityStateName())) {
                        log.warn("⚠️ operationEffect[{}] 引用了不存在的 state: {}",
                            opId, eff.getUnityStateName());
                    }
                }
            });
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbreviate(String hash) {
        if (hash == null) return "<null>";
        return hash.length() <= 8 ? hash : hash.substring(0, 8);
    }
}
