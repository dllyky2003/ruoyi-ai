package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 规则文件中声明的效果模板（静态）。
 *
 * <p>与 {@link com.ruoyi.ems.spi.dto.EffectSpec} 的区别：
 * <ul>
 *   <li>本类 = 规则文件里的静态模板（含 constraints，未求值）</li>
 *   <li>{@code EffectSpec} = 运行时已求值的授权（{@code resolved=true}）</li>
 * </ul>
 * </p>
 */
@Data
public class EffectSpecStatic {

    @JsonProperty("effect_id")
    @JsonAlias({"effectId"})
    private String effectId;

    @JsonProperty("branch_id")
    @JsonAlias({"branchId"})
    private String branchId;

    @JsonProperty("constraints")
    private Map<String, Object> constraints;
}
