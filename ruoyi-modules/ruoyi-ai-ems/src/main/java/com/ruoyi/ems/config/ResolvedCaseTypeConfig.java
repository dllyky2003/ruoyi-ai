package com.ruoyi.ems.config;

import com.ruoyi.ems.domain.PhaseDefinition;
import lombok.Data;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * 解析后的完整案例配置（已合并父级）
 *
 * 由 YamlStateMachineLoader 在启动时通过递归合并生成，
 * 包含该案例类型及其所有父级（GENERAL_ACLS）的完整状态和转移规则。
 *
 * 该对象是只读的运行时配置，所有字段在构建完成后不应再修改。
 */
@Data
public class ResolvedCaseTypeConfig {

    // ==================== 基本信息 ====================

    /**
     * 案例类型编码（唯一标识）
     * 如: "tachycardia_stable_narrow"
     */
    private String code;

    /**
     * 案例显示名称
     * 如: "窄QRS稳定心动过速"
     */
    private String caseName;

    /**
     * 配置版本
     */
    private String version;

    /**
     * 父级案例编码（仅用于引用，实际合并后的父级规则已包含在本对象中）
     * 如: "GENERAL_ACLS"
     */
    private String parent;

    /**
     * 进入本域时，需要自动重置为默认值（0/null）的变量清单
     */
    private List<String> resetVarsOnArrival = new ArrayList<>();


    private String initialState;


    private Map<String, List<String>> onEnterRules = new LinkedHashMap<>();

    /**
     * 所有状态编码列表（合并父级 + 子级，去重）
     * 如: ["INITIAL_SETUP", "ASSESS_STABILITY", "CHECK_QRS_WIDTH", ...]
     */
    private List<String> states = new ArrayList<>();

    /**
     * 终止状态编码列表（合并父级 + 子级，去重）
     * 如: ["MONITOR_SINUS_RHYTHM", "WIDE_QRS_ALGORITHM", ...]
     */
    private List<String> endStates = new ArrayList<>();


    // ==================== ★ Phase SM：主图隐藏列表 ====================

    /**
     * 主图（overview）中不显示的状态列表。
     *
     * <p>语义：这些状态在主图中被隐藏，只在内部图（phase 展开图）里显示。</p>
     *
     * <p>典型用途：{@code F-000-TIMEOUT_HANDLING} 这种"每个状态自己的出口"，
     * 在主图里画出来只会造成视觉噪音，医学语义也含糊（不知道该次超时属于哪个状态）。
     * 但它在内部图里原样保留，医学信息不丢。</p>
     */
    private Set<String> overviewExcludeStates = new LinkedHashSet<>();

    public Set<String> getOverviewExcludeStates() { return overviewExcludeStates; }
    public void setOverviewExcludeStates(Set<String> v) { this.overviewExcludeStates = v; }


    private Map<String, String> stateDescriptions;


    public String getStateDescription(String stateCode) {
        if (stateDescriptions != null && stateDescriptions.containsKey(stateCode)) {
            return stateDescriptions.get(stateCode);
        }
        return null;
    }
    // ==================== 转移规则 ====================

    /**
     * 所有转移规则列表（父级规则 + 子级规则，子级追加）
     * 注意：子类可通过在继承链中匹配优先级来覆盖父类规则，
     * 见 RuleMatcher.findMatchingRule() 的实现
     */
    private List<TransitionRule> transitions = new ArrayList<>();

    // ==================== 意图映射（默认跳转） ====================

    /**
     * 意图编码 → 目标状态映射
     * 当 allowUnlistedTransitions = true 时，用于处理未显式定义转移的意图
     * 如: {"S1-1": "CHECK_QRS_WIDTH", "S1-2": "NARROW_QRS_CHECK_RHYTHM"}
     */
    private Map<String, String> intentToStateMapping = new LinkedHashMap<>();

    /**
     * 全局事件意图列表
     * 这些意图不改变主状态，仅记录日志
     * 如: ["G1-1", "G1-2", "G4-1", "G4-2"]
     */
    private Set<String> globalEventIntents = new LinkedHashSet<>();

    // ==================== ★ 新增：常驻安全意图 ====================

    /**
     * 本案例独有的“始终允许”意图（用于构建 LLM 提示词白名单）。
     * 这些意图在任何状态下均可用，且不触发状态切换。
     * 子类通过覆盖父类的该集合来实现意图白名单的定制（如窄QRS剔除 CPR 意图）。
     */
    private Set<String> alwaysAllowedIntents = new LinkedHashSet<>();

    // ==================== ★ Phase SM 新增：阶段配置 ====================

    /**
     * 初始主阶段编码。
     * 会话创建时若为空，PhaseManager 会使用 phases 中 priority 最高者作为初始主阶段。
     */
    private String initialPhase;

    /**
     * 阶段定义列表（合并父级 + 子级，按 id 去重，子级覆盖父级同 id）。
     * YAML 中的声明顺序被保留，用于多候选同优先级时的确定性选择。
     */
    private List<PhaseDefinition> phases = new ArrayList<>();

    /**
     * 反查：state → phase id。
     * 由 YamlStateMachineLoader 在合并完成后统一构建，用于诊断与快速查询。
     */
    private Map<String, String> stateToPhase = new LinkedHashMap<>();

    // ==================== 默认转移配置 ====================

    /**
     * 是否允许未显式列出的转移
     * 若为 true，意图匹配失败时会尝试使用 intentToStateMapping 进行默认跳转
     */
    private boolean allowUnlistedTransitions = false;

    /**
     * 默认转移时执行的动作（Spring Bean 名称或 SpEL 表达式）
     */
    private String defaultTransitionAction;

    // ==================== 便捷查询方法 ====================

    /**
     * 检查是否包含某个状态
     */
    public boolean hasState(String stateCode) {
        return states != null && states.contains(stateCode);
    }

    /**
     * 检查是否为终止状态
     */
    public boolean isEndState(String stateCode) {
        return endStates != null && endStates.contains(stateCode);
    }

    /**
     * 检查是否为初始状态
     */
    public boolean isInitialState(String stateCode) {
        return initialState != null && initialState.equals(stateCode);
    }

    /**
     * 获取某个状态下所有可用的转移规则
     */
    public List<TransitionRule> getTransitionsBySource(String sourceState) {
        if (transitions == null || sourceState == null) {
            return Collections.emptyList();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 获取某个状态下所有可用的意图编码列表（用于 NLU 约束）
     * 注意：此方法仅返回状态专属意图（来自 transitions），不包含常驻安全意图。
     * 如需构建完整的提示词白名单，请使用 {@link #buildPromptWhitelist(String)}
     */
    public Set<String> getAvailableIntents(String sourceState) {
        if (transitions == null || sourceState == null) {
            return Collections.emptySet();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .map(TransitionRule::getIntentCode)
            .filter(Objects::nonNull)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * ★ 核心方法：构建用于 LLM 提示词的完整白名单。
     * 组合 = 当前状态的专属意图（来自 transitions） + 本案例的常驻安全意图（alwaysAllowedIntents）
     *
     * @param currentState 当前状态编码（如 "ASSESS_6MG_EFFECT"）
     * @return 去重后的意图编码集合，可直接用于提示词的 {allowed_intents}
     */
    public Set<String> buildPromptWhitelist(String currentState) {
        Set<String> whitelist = new LinkedHashSet<>();
        // 1. 添加状态专属意图
        if (transitions != null && currentState != null) {
            for (TransitionRule rule : transitions) {
                if (currentState.equals(rule.getSource()) && rule.getIntentCode() != null) {
                    whitelist.add(rule.getIntentCode());
                }
            }
        }
        // 2. 添加常驻安全意图
        if (alwaysAllowedIntents != null) {
            whitelist.addAll(alwaysAllowedIntents);
        }
        return whitelist;
    }

    /**
     * 根据意图编码查找转移规则（用于 RuleMatcher）
     */
    public Optional<TransitionRule> findTransition(String sourceState, String intentCode) {
        if (transitions == null || sourceState == null) {
            return Optional.empty();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .filter(r -> intentCode.equals(r.getIntentCode()))
            .findFirst();
    }

    /**
     * 根据事件编码查找转移规则
     */
    public Optional<TransitionRule> findTransitionByEvent(String sourceState, String eventCode) {
        if (transitions == null || sourceState == null || eventCode == null) {
            return Optional.empty();
        }
        return transitions.stream()
            .filter(r -> sourceState.equals(r.getSource()))
            .filter(r -> eventCode.equals(r.getEvent()))
            .findFirst();
    }

    /**
     * 获取所有状态的数量（用于监控）
     */
    public int getStateCount() {
        return states != null ? states.size() : 0;
    }

    /**
     * 获取转移规则数量（用于监控）
     */
    public int getTransitionCount() {
        return transitions != null ? transitions.size() : 0;
    }

    /**
     * 检查是否为全局事件
     */
    public boolean isGlobalEvent(String intentCode) {
        return globalEventIntents != null && globalEventIntents.contains(intentCode);
    }

    /**
     * 获取默认目标状态
     */
    public String getDefaultTargetState(String intentCode) {
        if (intentToStateMapping == null || intentCode == null) {
            return null;
        }
        return intentToStateMapping.get(intentCode);
    }

    // ==================== ★ Phase SM 便捷查询 ====================

    /**
     * 根据 id 查找阶段定义。
     *
     * @param phaseId 阶段 ID
     * @return 匹配的阶段定义；不存在返回 null
     */
    public PhaseDefinition getPhaseById(String phaseId) {
        if (phases == null || phaseId == null) {
            return null;
        }
        for (PhaseDefinition p : phases) {
            if (phaseId.equals(p.getId())) {
                return p;
            }
        }
        return null;
    }

    /**
     * 查询某状态所属的阶段 ID（反查）。
     *
     * @param state 状态编码
     * @return 阶段 ID；未映射返回 null
     */
    public String getPhaseForState(String state) {
        if (stateToPhase == null || state == null) {
            return null;
        }
        return stateToPhase.get(state);
    }

    /**
     * 检查是否有阶段配置。
     */
    public boolean hasPhases() {
        return phases != null && !phases.isEmpty();
    }

    /**
     * ★ 检查某状态是否应在主图（overview）中隐藏。
     *
     * @param state 状态编码
     * @return true=主图中隐藏（只在内部图显示）
     */
    public boolean isOverviewHidden(String state) {
        return state != null
            && overviewExcludeStates != null
            && overviewExcludeStates.contains(state);
    }

    /**
     * ★ 返回所有叶子阶段（递归展开 children）。
     *
     * <p>运行时逻辑（PhaseManager / 反查表）只关心叶子阶段，
     * 容器阶段不参与。</p>
     *
     * @return 叶子阶段列表；无 phases 时返回空列表
     */
    public List<PhaseDefinition> getLeafPhases() {
        List<PhaseDefinition> result = new ArrayList<>();
        if (phases != null) {
            for (PhaseDefinition p : phases) {
                collectLeafPhases(p, result);
            }
        }
        return result;
    }

    /**
     * 递归收集叶子阶段。
     */
    private void collectLeafPhases(PhaseDefinition phase, List<PhaseDefinition> out) {
        if (phase == null || phase.getId() == null) {
            return;
        }
        if (phase.hasChildren()) {
            for (PhaseDefinition child : phase.getChildren()) {
                collectLeafPhases(child, out);
            }
        } else {
            out.add(phase);
        }
    }
    /**
     * 获取阶段数量（用于监控）。
     */
    public int getPhaseCount() {
        return phases != null ? phases.size() : 0;
    }

    /**
     * 重建 stateToPhase 反查表。
     *
     * <p>★ 递归处理 children：只把叶子阶段的 states 记录进来，
     * 映射到叶子阶段 ID（而非容器阶段 ID）。</p>
     *
     * <p>由 YamlStateMachineLoader 在合并完成后调用；也可在运行时临时重建。</p>
     */
    public void rebuildStateToPhaseIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        if (phases != null) {
            for (PhaseDefinition p : phases) {
                collectStateToPhaseIndex(p, index);
            }
        }
        this.stateToPhase = index;
    }

    /**
     * 递归收集：把叶子阶段的 state → 叶子阶段 ID 写入 index。
     * 容器阶段不写入（它的 states 为空，且有 children）。
     */
    private void collectStateToPhaseIndex(PhaseDefinition phase, Map<String, String> out) {
        if (phase == null || phase.getId() == null) {
            return;
        }
        if (phase.hasChildren()) {
            for (PhaseDefinition child : phase.getChildren()) {
                collectStateToPhaseIndex(child, out);
            }
            return;
        }
        // 叶子阶段
        if (phase.getStates() != null) {
            for (String s : phase.getStates()) {
                if (s != null) {
                    out.put(s, phase.getId());
                }
            }
        }
    }

    /**
     * 创建深拷贝（用于调试或隔离）
     */
    public ResolvedCaseTypeConfig deepCopy() {
        ResolvedCaseTypeConfig copy = new ResolvedCaseTypeConfig();
        copy.setCode(this.code);
        copy.setCaseName(this.caseName);
        copy.setVersion(this.version);
        copy.setParent(this.parent);
        copy.setInitialState(this.initialState);
        copy.setStates(new ArrayList<>(this.states));
        copy.setEndStates(new ArrayList<>(this.endStates));
        copy.setTransitions(new ArrayList<>(this.transitions));
        copy.setIntentToStateMapping(new LinkedHashMap<>(this.intentToStateMapping));
        copy.setGlobalEventIntents(new LinkedHashSet<>(this.globalEventIntents));
        // ★ 拷贝常驻安全意图
        copy.setAlwaysAllowedIntents(new LinkedHashSet<>(this.alwaysAllowedIntents));
        copy.setAllowUnlistedTransitions(this.allowUnlistedTransitions);
        copy.setDefaultTransitionAction(this.defaultTransitionAction);
        // ★ Phase SM 拷贝
        copy.setInitialPhase(this.initialPhase);
        copy.setPhases(new ArrayList<>(this.phases));
        copy.setStateToPhase(new LinkedHashMap<>(this.stateToPhase));
        // ★ 主图隐藏列表拷贝
        copy.setOverviewExcludeStates(this.overviewExcludeStates != null
            ? new LinkedHashSet<>(this.overviewExcludeStates)
            : new LinkedHashSet<>());
        return copy;
    }

    // ==================== Builder 模式（简化构建） ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ResolvedCaseTypeConfig config = new ResolvedCaseTypeConfig();

        public Builder code(String code) { config.code = code; return this; }
        public Builder caseName(String caseName) { config.caseName = caseName; return this; }
        public Builder version(String version) { config.version = version; return this; }
        public Builder parent(String parent) { config.parent = parent; return this; }
        public Builder initialState(String initialState) { config.initialState = initialState; return this; }

        public Builder states(List<String> states) {
            config.states = states != null ? new ArrayList<>(states) : new ArrayList<>();
            return this;
        }

        public Builder endStates(List<String> endStates) {
            config.endStates = endStates != null ? new ArrayList<>(endStates) : new ArrayList<>();
            return this;
        }

        public Builder transitions(List<TransitionRule> transitions) {
            config.transitions = transitions != null ? new ArrayList<>(transitions) : new ArrayList<>();
            return this;
        }

        public Builder intentToStateMapping(Map<String, String> mapping) {
            config.intentToStateMapping = mapping != null ? new LinkedHashMap<>(mapping) : new LinkedHashMap<>();
            return this;
        }

        public Builder globalEventIntents(Set<String> intents) {
            config.globalEventIntents = intents != null ? new LinkedHashSet<>(intents) : new LinkedHashSet<>();
            return this;
        }

        // ★ 新增：设置常驻安全意图
        public Builder alwaysAllowedIntents(Set<String> intents) {
            config.alwaysAllowedIntents = intents != null ? new LinkedHashSet<>(intents) : new LinkedHashSet<>();
            return this;
        }

        public Builder allowUnlistedTransitions(boolean allow) {
            config.allowUnlistedTransitions = allow;
            return this;
        }

        public Builder defaultTransitionAction(String action) {
            config.defaultTransitionAction = action;
            return this;
        }

        // ★ Phase SM 新增
        public Builder initialPhase(String initialPhase) {
            config.initialPhase = initialPhase;
            return this;
        }

        public Builder phases(List<PhaseDefinition> phases) {
            config.phases = phases != null ? new ArrayList<>(phases) : new ArrayList<>();
            return this;
        }

        public Builder stateToPhase(Map<String, String> stateToPhase) {
            config.stateToPhase = stateToPhase != null ? new LinkedHashMap<>(stateToPhase) : new LinkedHashMap<>();
            return this;
        }

        /**
         * ★ 主图隐藏的状态列表。
         */
        public Builder overviewExcludeStates(Set<String> states) {
            config.overviewExcludeStates = states != null
                ? new LinkedHashSet<>(states)
                : new LinkedHashSet<>();
            return this;
        }

        public ResolvedCaseTypeConfig build() {
            // 基本校验
            if (config.code == null || config.code.isEmpty()) {
                throw new IllegalArgumentException("案例编码不能为空");
            }
            if (config.initialState == null || config.initialState.isEmpty()) {
                throw new IllegalArgumentException("初始状态不能为空");
            }
            // ★ Phase SM 校验：若声明了 phases 但未给 initialPhase，自动取 priority 最高者
            if (config.initialPhase == null
                && config.phases != null
                && !config.phases.isEmpty()) {
                config.initialPhase = config.phases.stream()
                    .filter(Objects::nonNull)
                    .filter(p -> p.getId() != null)
                    .max(Comparator.comparingInt(
                        p -> p.getPriority() != null ? p.getPriority() : 0))
                    .map(PhaseDefinition::getId)
                    .orElse(null);
            }
            // ★ 构建 stateToPhase 反查表（若调用方未显式设置）
            if (config.stateToPhase == null || config.stateToPhase.isEmpty()) {
                config.rebuildStateToPhaseIndex();
            }
            return config;
        }
    }
}
