package com.ruoyi.acls.tutor.effect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 效应映射表加载器。
 *
 * <p>读取 {@code config/acls/effects/{drug}.yaml}，不做结构校验——
 * 结构校验由 {@code effect_mapping_schema.json}（IDE / CI 侧）负责，本类只管
 * 把 YAML 读成通用 Map 树，供报告生成器、以及未来的效应解释器共用同一份数据。
 *
 * <p>与 {@code CaseConfigReportService} / {@code CaseContractBuilder} 的关系：
 * 那一组类面向工程机制（状态机、SpEL、Skill 路由），本类和
 * {@link EffectMappingReportService} 面向临床内容（效应映射表）。
 * 两条链路的读者不同、数据源不同，刻意不合并——参见设计契约
 * ACLS-TUTOR-DESIGN-VIRTUAL-PATIENT-V0.1 第二节「原则2：模型与评审解耦」，
 * 这里是同一条解耦原则在配置管理层面的延伸。
 */
@Slf4j
@Component
public class EffectMappingLoader {

    private static final String BASE_PATH = "config/acls/effects/";

    private final Yaml yaml = new Yaml();

    /**
     * 加载指定药物/操作的效应映射表。
     *
     * @param drug 如 "adenosine"，对应 {@code config/acls/effects/adenosine.yaml}
     * @return YAML 根节点；文件不存在时返回 null（由调用方决定如何呈现，
     *         本类不在此处抛异常，因为"表还没写"在导师视图里是正常状态，不是错误）
     */
    public Map<String, Object> load(String drug) {
        ClassPathResource resource = new ClassPathResource(BASE_PATH + drug + ".yaml");
        if (!resource.exists()) {
            log.info("[EffectMapping] 效应映射表尚未创建: {}", resource.getPath());
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = yaml.load(in);
            return root == null ? new LinkedHashMap<>() : root;
        } catch (Exception e) {
            log.error("[EffectMapping] 加载失败: drug={}", drug, e);
            // 加载失败（文件存在但格式损坏）是真正的错误，需要暴露给调用方
            throw new IllegalStateException("效应映射表加载失败: " + drug, e);
        }
    }
}
