package com.ruoyi.acls.contract.diagnose;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次"被拒"的完整诊断报告。
 *
 * <p>核心字段 {@link #verdict} 区分两种根本不同的失败：
 * <ul>
 *   <li><b>NO_OUT_EDGE</b> —— 该状态下此意图无任何出边。属于"流程/学员操作"问题</li>
 *   <li><b>GUARD_FAIL</b> —— 有出边但 guard 不满足。属于"配置/变量"问题</li>
 *   <li><b>GUARD_UNKNOWN</b> —— 有出边但 SpEL 求值异常</li>
 *   <li><b>CONTRACT_MISSING</b> / <b>STATE_NOT_FOUND</b> / <b>NO_REJECTION</b> —— 数据缺失</li>
 * </ul>
 */
@Data
public class DiagnosticReport {

    private Phenomenon phenomenon;

    /** NO_OUT_EDGE / GUARD_FAIL / GUARD_UNKNOWN / CONTRACT_MISSING / STATE_NOT_FOUND / NO_REJECTION */
    private String verdict;

    /** 一句话结论，直接给人读 */
    private String conclusion;

    /** 给学员/教练的建议 */
    private String suggestion;

    /** 该状态的所有出边（供交叉核对） */
    private List<EdgeView> outEdges = new ArrayList<>();

    /** 该状态下能被触发的意图清单（去重，供"应该做什么"用） */
    private List<String> availableIntents = new ArrayList<>();

    /** 仅 GUARD_FAIL 时填充 */
    private RootCause rootCause;

    /** 仅 GUARD_FAIL 时填充 */
    private Impact impact;

    // ============================================================

    @Data
    public static class Phenomenon {
        private String caseId;
        private String sessionId;
        private String stateId;
        private String intentId;
        private String taskId;
        private long timestamp;
    }

    @Data
    public static class EdgeView {
        private String intentCode;
        private String targetState;
        private int priority;
        private String triggerType;
        private String guardRaw;
        private String guardResolved;
        private Boolean satisfied;
        private List<VarView> vars = new ArrayList<>();
    }

    @Data
    public static class VarView {
        private String varName;
        private Object currentValue;
        private List<WriterInfo> writers = new ArrayList<>();
    }

    @Data
    public static class WriterInfo {
        private String module;
        private String filePath;
        private String location;
    }

    @Data
    public static class RootCause {
        private String varName;
        private Object currentValue;
        private String reason;
        private String writerModule;
        private String writerFile;
        private String writerSkill;
    }

    @Data
    public static class Impact {
        private List<String> affectedModules = new ArrayList<>();
        private List<String> affectedIntents = new ArrayList<>();
        private List<String> affectedFiles = new ArrayList<>();
    }
}
