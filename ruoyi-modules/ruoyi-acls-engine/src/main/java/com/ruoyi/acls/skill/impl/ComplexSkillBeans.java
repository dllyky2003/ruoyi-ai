package com.ruoyi.acls.skill.impl;

import com.ruoyi.acls.skill.EmsSkill;
import com.ruoyi.acls.skill.impl.AdministerShockAedSkill;
import com.ruoyi.acls.skill.impl.DrugPushSkill;
import com.ruoyi.acls.skill.impl.SynchronizedCardioversionSkill;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.ems.clock.DrugClockService;
import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.context.MapWriteHelper;
import com.ruoyi.ems.effect.DrugIndicationEvaluator;
import com.ruoyi.ems.effect.EffectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 复杂技能 Spring Bean 注册。
 *
 * ⚠️ 契约：YAML 里的 id 必须等于 skill.getKeystepId()。
 *    当前 YamlSkillLoader.createSkillFromConfig 对 complex 类型 return null，
 *    不做任何自动注册，所以必须在 @Bean 方法里显式 registry.register(skill)。
 *
 * <p>★ 2026-09-23：DrugPushSkill 构造器新增 DrugIndicationEvaluator 依赖，
 * 用于给药时判定 correct / wrong_minor / wrong_critical。</p>
 */
@Configuration
public class ComplexSkillBeans {

    @Bean("epinephrinePushSkill")
    public EmsSkill epinephrinePushSkill(EffectManager em,
                                         MapWriteHelper helper,
                                         SkillRegistry registry) {
        EmsSkill skill = new DrugPushSkill(em, helper, "epinephrine");
        registry.register(skill);          // ★ keystepId = "drug_push_epinephrine"
        return skill;
    }

    @Bean("synchronizedCardioversionSkill")
    public EmsSkill synchronizedCardioversionSkill(MapWriteHelper helper,
                                                   SkillRegistry registry) {
        EmsSkill skill = new SynchronizedCardioversionSkill(helper);
        registry.register(skill);          // ★ keystepId = "synchronized_cardioversion"
        return skill;
    }

    @Bean("administerShockAedSkill")
    public EmsSkill administerShockAedSkill(MapWriteHelper helper,
                                            SkillRegistry registry) {
        EmsSkill skill = new AdministerShockAedSkill(helper);
        registry.register(skill);          // ★ keystepId = "administer_shock_aed"
        return skill;
    }


    @Bean("adenosinePushSkill")
    public EmsSkill adenosinePushSkill(EffectManager em,
                                       MapWriteHelper helper,
                                       DrugClockService drugClockService,
                                       EmsContextManager contextManager,
                                       SkillRegistry registry) {
        EmsSkill skill = new DrugPushAdenosineSkill(
            em, helper, drugClockService, contextManager);
        registry.register(skill);          // ★ keystepId = "drug_push_adenosine"
        return skill;
    }
}
