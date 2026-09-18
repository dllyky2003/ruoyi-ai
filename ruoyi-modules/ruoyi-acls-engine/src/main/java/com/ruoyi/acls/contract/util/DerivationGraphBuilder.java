package com.ruoyi.acls.contract.util;

import com.ruoyi.ems.domain.DerivationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DerivationGraphBuilder {

    private final GuardExpressionParser parser;

    public Map<String, Set<String>> buildDependencyGraph(List<DerivationRule> rules) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        if (rules == null) return graph;
        for (DerivationRule rule : rules) {
            if (rule.getTarget() == null || rule.getTarget().isBlank()) continue;
            Set<String> deps = parser.extractReadVariables(rule.getExpression());
            graph.put(rule.getTarget(), deps);
        }
        return graph;
    }

    public List<String> topologicalSort(Map<String, Set<String>> graph, Set<String> cyclicNodes) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> reverseEdges = new LinkedHashMap<>();

        for (String node : graph.keySet()) {
            inDegree.put(node, 0);
            reverseEdges.put(node, new ArrayList<>());
        }
        for (Map.Entry<String, Set<String>> e : graph.entrySet()) {
            String target = e.getKey();
            for (String dep : e.getValue()) {
                if (!graph.containsKey(dep)) continue;
                reverseEdges.get(dep).add(target);
                inDegree.merge(target, 1, Integer::sum);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String next : reverseEdges.get(node)) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) queue.add(next);
            }
        }

        if (sorted.size() != graph.size()) {
            Set<String> remaining = new LinkedHashSet<>(graph.keySet());
            remaining.removeAll(sorted);
            if (cyclicNodes != null) cyclicNodes.addAll(remaining);
            log.warn("派生规则存在循环依赖: {}", remaining);
        }
        return sorted;
    }

    public Set<String> expandToLeafVariables(String var, Map<String, Set<String>> graph) {
        Set<String> result = new LinkedHashSet<>();
        expand(var, graph, result, new LinkedHashSet<>());
        return result;
    }

    private void expand(String var, Map<String, Set<String>> graph,
                        Set<String> result, Set<String> visiting) {
        if (visiting.contains(var)) return;
        visiting.add(var);
        Set<String> deps = graph.get(var);
        if (deps == null || deps.isEmpty()) {
            result.add(var);
        } else {
            for (String dep : deps) {
                if (dep.startsWith("derived.") && graph.containsKey(dep)) {
                    expand(dep, graph, result, visiting);
                } else {
                    result.add(dep);
                }
            }
        }
        visiting.remove(var);
    }
}
