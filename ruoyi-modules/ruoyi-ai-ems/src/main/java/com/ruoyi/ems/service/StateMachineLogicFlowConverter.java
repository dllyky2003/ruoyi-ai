package com.ruoyi.ems.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.ems.config.ResolvedCaseTypeConfig;
import com.ruoyi.ems.config.TransitionRule;
import com.ruoyi.ems.domain.dto.LogicFlowDefinitionDTO;
import com.ruoyi.ems.domain.PhaseDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态机 -> LogicFlow 图数据转换器。
 * 业务状态机定义是唯一事实来源；阶段图只是可视化投影。
 * 不修改状态机执行逻辑。
 *
 * 依赖 LogicFlowDefinitionDTO 包含 phaseGraph、phaseHierarchy、phaseGraphs、PhaseData。
 */
@Component
@RequiredArgsConstructor
public class StateMachineLogicFlowConverter {

    private final ObjectMapper objectMapper;

    private static final int COLUMN_COUNT = 4;
    private static final int START_X = 200;
    private static final int START_Y = 160;
    private static final int GAP_X = 260;
    private static final int GAP_Y = 160;

    public LogicFlowDefinitionDTO convert(
        ResolvedCaseTypeConfig config,
        boolean full,
        List<String> inheritanceChain) {
        if (config == null) {
            throw new IllegalArgumentException("状态机配置不能为空");
        }

        LogicFlowDefinitionDTO result = new LogicFlowDefinitionDTO();
        result.setCaseId(config.getCode());
        result.setCaseName(config.getCaseName());
        result.setVersion(config.getVersion());
        result.setParent(config.getParent());
        result.setFull(full);
        result.setInheritanceChain(inheritanceChain == null
            ? new ArrayList<>() : new ArrayList<>(inheritanceChain));
        result.setStateMachine(toMap(config));

        LogicFlowDefinitionDTO.GraphData graph = new LogicFlowDefinitionDTO.GraphData();
        graph.setNodes(buildNodes(config));
        graph.setEdges(buildEdges(config));
        result.setGraph(graph);

        // 不改变原有完整状态图；新增阶段层级图。
        buildPhaseViews(config, graph, result);
        return result;
    }

    /** 原有状态节点转换逻辑，保持向后兼容。 */
    private List<LogicFlowDefinitionDTO.NodeData> buildNodes(ResolvedCaseTypeConfig config) {
        List<LogicFlowDefinitionDTO.NodeData> result = new ArrayList<>();
        Set<String> states = new LinkedHashSet<>();
        if (config.getStates() != null) states.addAll(config.getStates());
        if (config.getInitialState() != null) states.add(config.getInitialState());
        if (config.getTransitions() != null) {
            for (TransitionRule rule : config.getTransitions()) {
                if (rule == null) continue;
                if (rule.getSource() != null) states.add(rule.getSource());
                if (rule.getTarget() != null) states.add(rule.getTarget());
            }
        }

        Set<String> endStates = new LinkedHashSet<>();
        if (config.getEndStates() != null) endStates.addAll(config.getEndStates());
        Map<String, String> descriptions = config.getStateDescriptions() == null
            ? Collections.emptyMap() : config.getStateDescriptions();

        int index = 0;
        for (String state : states) {
            if (state == null || state.isBlank()) continue;
            LogicFlowDefinitionDTO.NodeData node = new LogicFlowDefinitionDTO.NodeData();
            node.setId(state);
            node.setX(START_X + (index % COLUMN_COUNT) * GAP_X);
            node.setY(START_Y + (index / COLUMN_COUNT) * GAP_Y);
            String description = descriptions.get(state);
            String name = description == null || description.isBlank() ? state : description;
            node.setText(name);
            boolean isStart = state.equals(config.getInitialState());
            boolean isEnd = endStates.contains(state);
            node.setType(isStart || isEnd ? "workflow-ellipse" : "workflow-rect");

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("nodeType", isStart ? "start" : isEnd ? "end" : "state");
            properties.put("stateId", state);
            properties.put("name", name);
            properties.put("description", description);
            properties.put("initial", isStart);
            properties.put("terminal", isEnd);
            if (config.getOnEnterRules() != null) {
                Object rules = config.getOnEnterRules().get(state);
                if (rules != null) {
                    properties.put("onEnterRules", objectMapper.convertValue(rules, Object.class));
                }
            }
            node.setProperties(properties);
            result.add(node);
            index++;
        }
        return result;
    }

    /** 每条真实转移保留为独立边，不合并条件不同的转移。 */
    private List<LogicFlowDefinitionDTO.EdgeData> buildEdges(ResolvedCaseTypeConfig config) {
        List<LogicFlowDefinitionDTO.EdgeData> result = new ArrayList<>();
        if (config.getTransitions() == null) return result;
        int index = 0;
        for (TransitionRule rule : config.getTransitions()) {
            if (rule == null || rule.getSource() == null || rule.getTarget() == null) continue;
            LogicFlowDefinitionDTO.EdgeData edge = new LogicFlowDefinitionDTO.EdgeData();
            edge.setId("transition_" + index);
            edge.setType("polyline");
            edge.setSourceNodeId(rule.getSource());
            edge.setTargetNodeId(rule.getTarget());
            edge.setText(rule.getDescription());
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("transitionIndex", index);
            properties.put("source", rule.getSource());
            properties.put("target", rule.getTarget());
            properties.put("transitionRule", toMap(rule));
            edge.setProperties(properties);
            result.add(edge);
            index++;
        }
        return result;
    }

    /** 根据现有 Mermaid 语义构建多级阶段图，不修改业务状态机。 */
    private void buildPhaseViews(ResolvedCaseTypeConfig config,
                                 LogicFlowDefinitionDTO.GraphData fullGraph,
                                 LogicFlowDefinitionDTO result) {
        List<PhaseDefinition> roots = config.getPhases();
        if (roots == null || roots.isEmpty()) return;
        Set<String> excludes = config.getOverviewExcludeStates() == null
            ? Collections.emptySet() : config.getOverviewExcludeStates();
        Map<String, LogicFlowDefinitionDTO.NodeData> stateNodes = new LinkedHashMap<>();
        for (LogicFlowDefinitionDTO.NodeData node : fullGraph.getNodes()) {
            stateNodes.put(node.getId(), node);
        }
        List<LogicFlowDefinitionDTO.PhaseData> hierarchy = new ArrayList<>();
        Set<String> phaseIds = new LinkedHashSet<>();
        for (PhaseDefinition root : roots) {
            if (root != null) hierarchy.add(phaseData(root, null, 0, excludes, phaseIds));
        }
        result.setPhaseHierarchy(hierarchy);
        result.setPhaseGraph(overviewGraph(config, roots, excludes));
        Map<String, LogicFlowDefinitionDTO.GraphData> graphs = new LinkedHashMap<>();
        for (PhaseDefinition root : roots) {
            if (root != null) addSubgraphs(config, root, excludes, stateNodes, graphs);
        }
        result.setPhaseGraphs(graphs);
    }

    private LogicFlowDefinitionDTO.PhaseData phaseData(PhaseDefinition phase,
                                                       String parentId, int depth, Set<String> excludes, Set<String> seen) {
        if (phase.getId() == null || phase.getId().isBlank())
            throw new IllegalArgumentException("阶段 ID 不能为空");
        if (!seen.add(phase.getId()))
            throw new IllegalArgumentException("重复阶段 ID: " + phase.getId());
        LogicFlowDefinitionDTO.PhaseData data = new LogicFlowDefinitionDTO.PhaseData();
        data.setId(phase.getId());
        data.setDisplayName(phase.getDisplayName() == null ? phase.getId() : phase.getDisplayName());
        data.setParentId(parentId);
        data.setDepth(depth);
        List<String> direct = new ArrayList<>();
        // 与 Mermaid 一致：容器节点只统计子阶段；叶子阶段才拥有 states。
        if (!phase.hasChildren() && phase.getStates() != null) {
            for (String state : phase.getStates()) {
                if (state != null && !excludes.contains(state)) direct.add(state);
            }
        }
        data.setStateIds(direct);
        List<LogicFlowDefinitionDTO.PhaseData> children = new ArrayList<>();
        int count = direct.size();
        if (phase.getChildren() != null) {
            for (PhaseDefinition child : phase.getChildren()) {
                if (child == null) continue;
                LogicFlowDefinitionDTO.PhaseData childData =
                    phaseData(child, phase.getId(), depth + 1, excludes, seen);
                children.add(childData);
                count += childData.getLeafStateCount();
            }
        }
        data.setChildren(children);
        data.setChildCount(children.size());
        data.setLeafStateCount(count);
        return data;
    }

    /** 顶层总览严格沿用 Mermaid：状态映射、顶层 ID、aliases、排除和边聚合。 */
    private LogicFlowDefinitionDTO.GraphData overviewGraph(ResolvedCaseTypeConfig config,
                                                           List<PhaseDefinition> roots, Set<String> excludes) {
        LogicFlowDefinitionDTO.GraphData graph = new LogicFlowDefinitionDTO.GraphData();
        Map<String, String> stateToTop = new LinkedHashMap<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> topIds = new LinkedHashSet<>();
        List<LogicFlowDefinitionDTO.NodeData> nodes = new ArrayList<>();
        int index = 0;
        for (PhaseDefinition top : roots) {
            if (top == null || top.getId() == null) continue;
            topIds.add(top.getId());
            collectLeafStates(top, top.getId(), excludes, stateToTop);
            if (top.getAliases() != null) {
                for (String alias : top.getAliases()) aliases.putIfAbsent(alias, top.getId());
            }
            nodes.add(phaseNode(top, index++, excludes));
        }
        graph.setNodes(nodes);
        Map<String, LogicFlowDefinitionDTO.EdgeData> edges = new LinkedHashMap<>();
        if (config.getTransitions() != null) {
            for (int i = 0; i < config.getTransitions().size(); i++) {
                TransitionRule rule = config.getTransitions().get(i);
                if (rule == null) continue;
                String src = resolveTop(rule.getSource(), excludes, stateToTop, topIds, aliases);
                String dst = resolveTop(rule.getTarget(), excludes, stateToTop, topIds, aliases);
                if (src == null || dst == null || src.equals(dst)) continue;
                appendAggregatedEdge(edges, src, dst, i, "transition");
            }
        }
        graph.setEdges(new ArrayList<>(edges.values()));
        return graph;
    }

    private void collectLeafStates(PhaseDefinition phase, String topId,
                                   Set<String> excludes, Map<String, String> stateToTop) {
        if (phase.isLeaf()) {
            if (phase.getStates() != null) {
                for (String state : phase.getStates()) {
                    if (state != null && !excludes.contains(state)) stateToTop.put(state, topId);
                }
            }
        } else if (phase.getChildren() != null) {
            for (PhaseDefinition child : phase.getChildren()) {
                if (child != null) collectLeafStates(child, topId, excludes, stateToTop);
            }
        }
    }

    private String resolveTop(String id, Set<String> excludes,
                              Map<String, String> stateToTop, Set<String> topIds, Map<String, String> aliases) {
        if (id == null || excludes.contains(id)) return null;
        String direct = stateToTop.get(id);
        if (direct != null) return direct;
        if (topIds.contains(id)) return id;
        return aliases.get(id);
    }

    private void addSubgraphs(ResolvedCaseTypeConfig config, PhaseDefinition phase,
                              Set<String> excludes,
                              Map<String, LogicFlowDefinitionDTO.NodeData> stateNodes,
                              Map<String, LogicFlowDefinitionDTO.GraphData> output) {
        if (phase.getId() == null) return;
        LogicFlowDefinitionDTO.GraphData graph = phase.hasChildren()
            ? containerGraph(phase, excludes)
            : leafGraph(config, phase, excludes, stateNodes);
        output.put(phase.getId(), graph);
        if (phase.getChildren() != null) {
            for (PhaseDefinition child : phase.getChildren()) {
                if (child != null) addSubgraphs(config, child, excludes, stateNodes, output);
            }
        }
    }

    /** 容器图：与 buildContainerSubgraph 一致，仅按 requiresPhases 生成子阶段依赖边。 */
    private LogicFlowDefinitionDTO.GraphData containerGraph(PhaseDefinition parent,
                                                            Set<String> excludes) {
        LogicFlowDefinitionDTO.GraphData graph = new LogicFlowDefinitionDTO.GraphData();
        if (parent.getChildren() == null) return graph;
        Set<String> childIds = new LinkedHashSet<>();
        List<LogicFlowDefinitionDTO.NodeData> nodes = new ArrayList<>();
        int index = 0;
        for (PhaseDefinition child : parent.getChildren()) {
            if (child == null || child.getId() == null) continue;
            childIds.add(child.getId());
            nodes.add(phaseNode(child, index++, excludes));
        }
        graph.setNodes(nodes);
        Map<String, LogicFlowDefinitionDTO.EdgeData> edges = new LinkedHashMap<>();
        for (PhaseDefinition child : parent.getChildren()) {
            if (child == null || child.getId() == null || child.getRequiresPhases() == null) continue;
            for (String required : child.getRequiresPhases()) {
                if (required == null || !childIds.contains(required)) continue;
                String key = required + "\u0000" + child.getId();
                if (edges.containsKey(key)) continue;
                LogicFlowDefinitionDTO.EdgeData edge = new LogicFlowDefinitionDTO.EdgeData();
                edge.setId("dependency_" + parent.getId() + "_" + edges.size());
                edge.setSourceNodeId(required);
                edge.setTargetNodeId(child.getId());
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("edgeType", "phaseDependency");
                props.put("parentPhaseId", parent.getId());
                edge.setProperties(props);
                edges.put(key, edge);
            }
        }
        graph.setEdges(new ArrayList<>(edges.values()));
        return graph;
    }

    /** 叶子图：与 Mermaid 一致，显示阶段状态与内部转移，跳过自环。 */
    private LogicFlowDefinitionDTO.GraphData leafGraph(ResolvedCaseTypeConfig config,
                                                       PhaseDefinition phase, Set<String> excludes,
                                                       Map<String, LogicFlowDefinitionDTO.NodeData> stateNodes) {
        LogicFlowDefinitionDTO.GraphData graph = new LogicFlowDefinitionDTO.GraphData();
        Set<String> members = new LinkedHashSet<>();
        List<LogicFlowDefinitionDTO.NodeData> nodes = new ArrayList<>();
        int index = 0;
        if (phase.getStates() != null) {
            for (String state : phase.getStates()) {
                if (state == null || excludes.contains(state) || !members.add(state)) continue;
                LogicFlowDefinitionDTO.NodeData original = stateNodes.get(state);
                if (original != null) nodes.add(copyStateNode(original, index++));
            }
        }
        graph.setNodes(nodes);
        List<LogicFlowDefinitionDTO.EdgeData> edges = new ArrayList<>();
        if (members.size() > 1 && config.getTransitions() != null) {
            for (int i = 0; i < config.getTransitions().size(); i++) {
                TransitionRule rule = config.getTransitions().get(i);
                if (rule == null || rule.getSource() == null || rule.getTarget() == null) continue;
                if (rule.getSource().equals(rule.getTarget())) continue;
                if (!members.contains(rule.getSource()) || !members.contains(rule.getTarget())) continue;
                LogicFlowDefinitionDTO.EdgeData edge = new LogicFlowDefinitionDTO.EdgeData();
                edge.setId("transition_" + i);
                edge.setSourceNodeId(rule.getSource());
                edge.setTargetNodeId(rule.getTarget());
                edge.setText(rule.getDescription());
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("transitionIndex", i);
                props.put("transitionRule", toMap(rule));
                edge.setProperties(props);
                edges.add(edge);
            }
        }
        graph.setEdges(edges);
        return graph;
    }

    private void appendAggregatedEdge(Map<String, LogicFlowDefinitionDTO.EdgeData> edges,
                                      String src, String dst, int transitionIndex, String edgeType) {
        String key = src + "\u0000" + dst;
        LogicFlowDefinitionDTO.EdgeData edge = edges.get(key);
        if (edge == null) {
            edge = new LogicFlowDefinitionDTO.EdgeData();
            edge.setId("phase_transition_" + edges.size());
            edge.setSourceNodeId(src);
            edge.setTargetNodeId(dst);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("edgeType", edgeType);
            props.put("aggregated", true);
            props.put("transitionIndexes", new ArrayList<Integer>());
            edge.setProperties(props);
            edges.put(key, edge);
        }
        @SuppressWarnings("unchecked")
        List<Integer> indexes = (List<Integer>) edge.getProperties().get("transitionIndexes");
        indexes.add(transitionIndex);
    }

    private LogicFlowDefinitionDTO.NodeData phaseNode(PhaseDefinition phase,
                                                      int index, Set<String> excludes) {
        LogicFlowDefinitionDTO.NodeData node = new LogicFlowDefinitionDTO.NodeData();
        node.setId(phase.getId());
        node.setType("workflow-phase");
        node.setX(START_X + (index % COLUMN_COUNT) * GAP_X);
        node.setY(START_Y + (index / COLUMN_COUNT) * GAP_Y);
        int count = countStates(phase, excludes);
        String name = phase.getDisplayName() == null ? phase.getId() : phase.getDisplayName();
        int childCount = phase.getChildren() == null ? 0 : phase.getChildren().size();
        node.setText(name + (childCount > 0
            ? " (" + childCount + " 个子阶段 / " + count + " 个状态)"
            : " (含 " + count + " 个状态)"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nodeType", "phase");
        props.put("phaseId", phase.getId());
        props.put("childCount", childCount);
        props.put("stateCount", count);
        props.put("hasChildren", phase.hasChildren());
        node.setProperties(props);
        return node;
    }

    private int countStates(PhaseDefinition phase, Set<String> excludes) {
        if (phase.hasChildren()) {
            int total = 0;
            if (phase.getChildren() != null) {
                for (PhaseDefinition child : phase.getChildren()) {
                    if (child != null) total += countStates(child, excludes);
                }
            }
            return total;
        }
        int total = 0;
        if (phase.getStates() != null) {
            for (String state : phase.getStates()) {
                if (state != null && !excludes.contains(state)) total++;
            }
        }
        return total;
    }

    private LogicFlowDefinitionDTO.NodeData copyStateNode(
        LogicFlowDefinitionDTO.NodeData source, int index) {
        LogicFlowDefinitionDTO.NodeData node = new LogicFlowDefinitionDTO.NodeData();
        node.setId(source.getId());
        node.setType(source.getType());
        node.setText(source.getText());
        node.setProperties(new LinkedHashMap<>(source.getProperties()));
        node.setX(START_X + (index % COLUMN_COUNT) * GAP_X);
        node.setY(START_Y + (index / COLUMN_COUNT) * GAP_Y);
        return node;
    }

    private Map<String, Object> toMap(Object source) {
        if (source == null) return new LinkedHashMap<>();
        return objectMapper.convertValue(source,
            new TypeReference<Map<String, Object>>() {});
    }
}
