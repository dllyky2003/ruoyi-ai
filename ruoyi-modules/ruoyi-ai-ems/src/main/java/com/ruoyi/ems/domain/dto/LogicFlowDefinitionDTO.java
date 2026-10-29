package com.ruoyi.ems.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态机 LogicFlow 可视化定义。
 *
 * 用于将现有 Java 状态机转换为前端 LogicFlow 图形数据。
 *
 * 设计原则：
 *
 * 1. stateMachine 保存完整业务定义，是业务数据来源。
 *
 * 2. graph 保留现有完整状态级图，保持向后兼容。
 *
 * 3. phaseGraph 表示顶层阶段总览图。
 *
 * 4. phaseHierarchy 表示完整阶段层级结构。
 *
 * 5. phaseGraphs 表示各阶段对应的直接子级图，
 *    用于未来支持多级阶段导航。
 *
 * 6. 图形节点及其坐标不参与状态机执行。
 *
 * 7. 当前仅提供读取与展示，不直接修改运行中的状态机。
 */
@Data
public class LogicFlowDefinitionDTO {

    /**
     * 案例 ID。
     */
    private String caseId;

    /**
     * 案例名称。
     */
    private String caseName;

    /**
     * 配置版本。
     */
    private Object version;

    /**
     * 父案例 ID。
     */
    private String parent;

    /**
     * 是否包含父级继承内容。
     */
    private boolean full;

    /**
     * 继承链。
     */
    private List<String> inheritanceChain = new ArrayList<>();

    /**
     * 完整状态级 LogicFlow 图。
     *
     * 保留原有字段及其含义，避免影响现有前端。
     */
    private GraphData graph = new GraphData();

    /**
     * 顶层阶段总览图。
     *
     * 节点代表顶层阶段。
     * 连线代表聚合后的跨阶段状态转移。
     *
     * 不改变实际状态机的转移规则。
     */
    private GraphData phaseGraph = new GraphData();

    /**
     * 完整阶段层级树。
     *
     * 由现有 PhaseDefinition 转换而来。
     *
     * 仅描述阶段组织结构，
     * 不赋予阶段额外的执行语义。
     */
    private List<PhaseData> phaseHierarchy = new ArrayList<>();

    /**
     * 阶段 ID -> 该阶段直接子级图。
     *
     * 对于包含子阶段的阶段：
     * 图中可以显示直接子阶段节点。
     *
     * 对于叶子阶段：
     * 图中显示该阶段所属状态及内部转移。
     *
     * 具体生成规则由转换器实现。
     */
    private Map<String, GraphData> phaseGraphs =
        new LinkedHashMap<>();

    /**
     * 完整状态机业务定义。
     *
     * 保留 phases、onEnterRules、
     * intentToStateMapping 等信息。
     *
     * 此字段不应因前端布局调整而发生变化。
     */
    private Map<String, Object> stateMachine =
        new LinkedHashMap<>();

    /**
     * LogicFlow 图数据。
     */
    @Data
    public static class GraphData {

        /**
         * 图节点。
         */
        private List<NodeData> nodes = new ArrayList<>();

        /**
         * 图连线。
         */
        private List<EdgeData> edges = new ArrayList<>();
    }

    /**
     * LogicFlow 节点。
     */
    @Data
    public static class NodeData {

        /**
         * 节点 ID。
         */
        private String id;

        /**
         * LogicFlow 图形类型。
         *
         * 例如：
         * workflow-rect
         * workflow-ellipse
         * workflow-phase
         */
        private String type;

        /**
         * 节点中心 X 坐标。
         */
        private double x;

        /**
         * 节点中心 Y 坐标。
         */
        private double y;

        /**
         * 节点显示文字。
         */
        private String text;

        /**
         * 节点附加属性。
         *
         * 可包含：
         * nodeType
         * stateId
         * phaseId
         * description
         * initial
         * terminal
         *
         * 以及现有业务属性。
         */
        private Map<String, Object> properties =
            new LinkedHashMap<>();
    }

    /**
     * LogicFlow 连线。
     */
    @Data
    public static class EdgeData {

        /**
         * 连线 ID。
         */
        private String id;

        /**
         * LogicFlow 连线类型。
         */
        private String type = "polyline";

        /**
         * 起始节点 ID。
         */
        private String sourceNodeId;

        /**
         * 目标节点 ID。
         */
        private String targetNodeId;

        /**
         * 连线显示文字。
         */
        private String text;

        /**
         * 连线附加属性。
         *
         * 状态级连线：
         * 保留完整 TransitionRule。
         *
         * 阶段级连线：
         * 可以保留对应原始转移的索引集合。
         */
        private Map<String, Object> properties =
            new LinkedHashMap<>();
    }

    /**
     * 与图形框架无关的阶段层级数据。
     *
     * 仅用于可视化导航和分组关系表达。
     *
     * 不代表具有进入、退出行为的复合状态。
     */
    @Data
    public static class PhaseData {

        /**
         * 阶段 ID。
         */
        private String id;

        /**
         * 阶段显示名称。
         */
        private String displayName;

        /**
         * 父阶段 ID。
         *
         * 顶层阶段为 null。
         */
        private String parentId;

        /**
         * 当前阶段层级深度。
         *
         * 顶层为 0。
         */
        private int depth;

        /**
         * 直接子阶段数量。
         */
        private int childCount;

        /**
         * 当前阶段及其后代包含的叶子状态数量。
         *
         * 应遵循现有 Mermaid 的排除规则。
         */
        private int leafStateCount;

        /**
         * 当前阶段直接包含的状态 ID。
         *
         * 不包含子阶段中的状态。
         */
        private List<String> stateIds = new ArrayList<>();

        /**
         * 子阶段。
         */
        private List<PhaseData> children = new ArrayList<>();
    }
}
