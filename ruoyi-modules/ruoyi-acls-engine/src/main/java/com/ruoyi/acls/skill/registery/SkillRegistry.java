package com.ruoyi.acls.skill.registery;

import com.ruoyi.acls.skill.EmsSkill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册中心。
 *
 * <p>职责：
 * <ul>
 *   <li>持有所有已注册的 {@link EmsSkill} 对象（来自 YamlSkillLoader 和 Spring Bean）</li>
 *   <li>提供技能对象的查询接口</li>
 * </ul>
 *
 * <p><b>注意：本类不持有技能的 IO 元数据（writes/reads）。</b>
 * 技能 IO 元数据由 {@code YamlSkillLoader.SkillMeta} 维护，包括 complex 类型技能
 * （它们在 YAML 里有声明但由 Spring Bean 注册）。契约构建时：
 * <ul>
 *   <li>技能 IO 元数据从 YamlSkillLoader 取</li>
 *   <li>本类只用于完整性诊断：YAML 声明的技能是否真的注册了</li>
 * </ul>
 */
@Slf4j
@Component
public class SkillRegistry {

    /** keystepId -> 技能对象 */
    private final Map<String, EmsSkill> skillMap = new ConcurrentHashMap<>();

    // ====================================================================
    // 注册
    // ====================================================================

    public void register(EmsSkill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("技能不能为空");
        }
        String keystepId = skill.getKeystepId();
        if (keystepId == null || keystepId.isBlank()) {
            throw new IllegalArgumentException(
                "技能 keystepId 不能为空: " + skill.getClass().getSimpleName());
        }
        EmsSkill existing = skillMap.put(keystepId, skill);
        if (existing != null && existing.getClass() != skill.getClass()) {
            log.warn("⚠️ 技能 {} 被覆盖：{} -> {}",
                keystepId, existing.getClass().getSimpleName(),
                skill.getClass().getSimpleName());
        } else {
            log.debug("✅ 注册技能: {} -> {}", keystepId, skill.getClass().getSimpleName());
        }
    }

    // ====================================================================
    // 原有查询接口（保留）
    // ====================================================================

    /**
     * 查询技能。keystepId 为 null 时不再抛 NPE。
     */
    public EmsSkill getSkill(String keystepId) {
        if (keystepId == null) {
            log.warn("⚠️ 技能查询参数为 null，已忽略");
            return null;
        }
        EmsSkill skill = skillMap.get(keystepId);
        if (skill == null) {
            log.warn("⚠️ 未找到技能: {}", keystepId);
        }
        return skill;
    }

    public int size() {
        return skillMap.size();
    }

    /**
     * 判空后再查，避免 ConcurrentHashMap.containsKey(null) 抛 NPE。
     */
    public boolean exists(String keystepId) {
        return keystepId != null && skillMap.containsKey(keystepId);
    }

    @PostConstruct
    public void logStatus() {
        log.info("📋 技能注册中心当前包含 {} 个技能", skillMap.size());
    }

    // ====================================================================
    // ★ 新增：契约完整性诊断接口
    // ====================================================================

    /**
     * 列出所有已注册的技能 ID（只读视图）。
     *
     * 用途：与 YamlSkillLoader.getAllSkillIds() 比对，找出：
     *   - YAML 声明了但未注册（Spring Bean 缺失）
     *   - 注册了但 YAML 未声明（IO 元数据缺失）
     */
    public Set<String> getAllSkillIds() {
        return Collections.unmodifiableSet(skillMap.keySet());
    }

    /**
     * 取全部技能对象的只读视图。
     */
    public Map<String, EmsSkill> getAllSkills() {
        return Collections.unmodifiableMap(skillMap);
    }

    /**
     * 按类型过滤技能。
     *
     * @param className 简单类名（如 "StandardActionSkill"、"QuerySkill"）
     * @return 匹配的技能 ID 集合
     */
    public Set<String> getSkillIdsByClass(String className) {
        if (className == null || className.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        skillMap.forEach((id, skill) -> {
            if (className.equals(skill.getClass().getSimpleName())) {
                result.add(id);
            }
        });
        return result;
    }

    /**
     * 清空注册表。仅用于测试或极端场景。
     *
     * 注意：正常运行时不应调用——技能注册由 Spring 生命周期管理，
     * 清空后无法通过现有接口重新填充（需要重新触发所有 Bean 的注册逻辑）。
     */
    public void clear() {
        log.warn("⚠️ 技能注册表被清空，共清除 {} 个技能", skillMap.size());
        skillMap.clear();
    }
}
