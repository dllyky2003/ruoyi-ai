package com.ruoyi.ems.context;

import java.util.Set;

/**
 * ACLS 内部变量白名单。
 *
 * <p>这些变量只用于服务端内部机制（时钟心跳、计时基准、评分链），
 * <b>不下发</b>到 WebSocket 客户端，也<b>不进入</b> case_config 的 initialVitals。</p>
 *
 * <p>引用点：</p>
 * <ul>
 *   <li>{@code EmsContextManager}：广播前过滤</li>
 *   <li>{@code CaseConfigAssembler}：组装 initialVitals 时过滤</li>
 * </ul>
 *
 * <p>新增内部变量时只改此处，避免漏改。</p>
 */
public final class AclsInternalVars {

    private AclsInternalVars() {}

    public static final Set<String> NAMES = Set.of(
        "process.clockTickAt",
        "process.lastInterventionTime",
        "evaluation.errorChain"
    );

    /** 判断变量名是否属于内部变量。 */
    public static boolean isInternal(String varName) {
        return varName != null && NAMES.contains(varName);
    }
}
