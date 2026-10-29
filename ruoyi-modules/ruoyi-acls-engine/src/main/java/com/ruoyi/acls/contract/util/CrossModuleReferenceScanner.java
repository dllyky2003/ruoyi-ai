package com.ruoyi.acls.contract.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 跨模块变量血缘扫描器。
 *
 * <p>扫描 ems / engine / tutor 三个模块的相关配置文件，
 * 构建 {@code caseId -> varName -> VariableBlood} 血缘映射。</p>
 *
 * <p><b>变量引用来源（全部支持）：</b>
 * <ol>
 *   <li><b>SpEL {@code #var}</b>：字符串里的 {@code #assert.stable} 形式引用</li>
 *   <li><b>命名空间裸变量</b>：{@code assert.stable} / {@code phys.systolicBp}，
 *       不带 {@code #} 前缀，SpEL root-object accessor 形式</li>
 *   <li><b>Key 位置变量</b>：YAML/JSON 里作为对象字段名的变量</li>
 *   <li><b>嵌套 Map 声明</b>：{@code intervention.sedation: { given: false }}
 *       声明为 {@code intervention.sedation.given} 等叶子路径</li>
 * </ol>
 */
@Slf4j
@Component
public class CrossModuleReferenceScanner {

    // ==================== 常量 ====================

    /** SpEL 变量引用： #varName 或 #a.b.c */
    private static final Pattern VAR_PATTERN =
        Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_.]*)");

    /** 赋值： #var = 或 var =，排除 == != >= <= */
    private static final Pattern ASSIGN_PATTERN =
        Pattern.compile("(?<![!<>=])(?:#)?([a-zA-Z_][a-zA-Z0-9_.]*)\\s*=(?!=)");

    /** 已知变量命名空间前缀 */
    private static final Set<String> KNOWN_NAMESPACES = Set.of(
        "assert", "phys", "derived", "device", "intervention",
        "clinical", "evaluation", "process", "report", "case", "other"
    );

    /** 无 # 前缀的命名空间变量 */
    private static final Pattern NS_VAR_PATTERN = Pattern.compile(
        "\\b(assert|phys|derived|device|intervention|clinical|evaluation|process|report|case|other)"
            + "\\.[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*"
    );

    /** module -> classpath 扫描模式 */
    private static final Map<String, String[]> SCAN_PATTERNS = new LinkedHashMap<>();
    static {
        SCAN_PATTERNS.put("ems", new String[]{
            "classpath*:config/acls/cases/*/_flow.yaml",
            "classpath*:config/acls/cases/*/_derivations.yaml",
            "classpath*:config/acls/cases/*/_exceptions.yaml",
            "classpath*:config/acls/cases/*/_variables.json",
            "classpath*:config/acls/cases/*/_intents.json",
            "classpath*:config/acls/GLOBAL/_flow.yaml",
            "classpath*:config/acls/GLOBAL/_derivations.yaml",
            "classpath*:config/acls/GLOBAL/_variables.json",
            "classpath*:config/acls/GLOBAL/_intents.json",
            "classpath*:physiology/**/*.yml",
            "classpath*:physiology/**/*.yaml",
        });
        SCAN_PATTERNS.put("engine", new String[]{
            "classpath*:config/routing/*.yaml",
            "classpath*:config/routing/*.yml",
            "classpath*:config/skills/**/*.yaml",
            "classpath*:config/skills/**/*.yml",
        });
        SCAN_PATTERNS.put("tutor", new String[]{
            "classpath*:config/acls/cases/*/lesson_plan.yaml",
            "classpath*:config/acls/cases/*/clinical_rules.yaml",
            "classpath*:config/acls/cases/*/scenario_beats.yaml",
            "classpath*:config/acls/cases/*/fallback_paths.yaml",
            "classpath*:config/acls/cases/*/instructor_overrides.yaml",
        });
    }

    /** 命中即视为写入语义的键 */
    private static final Set<String> WRITE_KEYS = Set.of(
        "effect", "effects",
        "set", "sets",
        "assign", "assigns",
        "write", "writes", "writeback",
        "sideeffect", "sideeffects",
        "outputvars", "outputs",
        "action", "actions",
        "target"
    );

    // ==================== 数据模型 ====================

    @Data
    public static class FileRef {
        private String module;
        private String filePath;
        private String sourceType;

        public FileRef() {}
        public FileRef(String module, String filePath, String sourceType) {
            this.module = module;
            this.filePath = filePath;
            this.sourceType = sourceType;
        }
    }

    @Data
    public static class Reference {
        private String module;
        private String filePath;
        private String sourceType;
        private String location;
        private String expression;
        private boolean writer;
    }

    @Data
    public static class VariableBlood {
        private String varName;
        private final List<Reference> writers = new ArrayList<>();
        private final List<Reference> readers = new ArrayList<>();
        private final List<FileRef> files = new ArrayList<>();
        private final Set<String> modules = new LinkedHashSet<>();

        public void addRef(Reference ref) {
            if (ref.isWriter()) writers.add(ref); else readers.add(ref);
            modules.add(ref.getModule());
            for (FileRef f : files) {
                if (f.getFilePath().equals(ref.getFilePath())) return;
            }
            files.add(new FileRef(ref.getModule(), ref.getFilePath(), ref.getSourceType()));
        }

        public List<Reference> allRefs() {
            List<Reference> all = new ArrayList<>(writers);
            all.addAll(readers);
            return all;
        }

        public boolean isCrossModule() {
            return modules.size() > 1;
        }
    }

    // ==================== 字段 ====================

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

    private volatile Map<String, Map<String, VariableBlood>> cache = null;

    // ==================== 公共 API ====================

    public Map<String, VariableBlood> scan(String caseId) {
        ensureCache();
        Map<String, VariableBlood> result = new LinkedHashMap<>();
        mergeInto(result, cache.get("GLOBAL"));
        if (caseId != null && !"GLOBAL".equals(caseId)) {
            mergeInto(result, cache.get(caseId));
        }
        return result;
    }

    public VariableBlood scanVariable(String caseId, String varName) {
        return scan(caseId).get(varName);
    }

    /**
     * 全库查询变量（跨所有 case）。用于诊断器在不知道 caseId 时也能查写入方。
     */
    public VariableBlood scanVariableByVarName(String varName) {
        ensureCache();
        VariableBlood merged = new VariableBlood();
        merged.setVarName(varName);
        for (Map<String, VariableBlood> m : cache.values()) {
            VariableBlood vb = m.get(varName);
            if (vb == null) continue;
            for (Reference r : vb.allRefs()) merged.addRef(r);
        }
        return merged.getFiles().isEmpty() ? null : merged;
    }

    public List<VariableBlood> listCrossModuleVariables(String caseId) {
        return scan(caseId).values().stream()
            .filter(VariableBlood::isCrossModule)
            .sorted(Comparator.comparing(VariableBlood::getVarName))
            .collect(Collectors.toList());
    }

    public Set<String> listAllCaseIds() {
        ensureCache();
        return new LinkedHashSet<>(cache.keySet());
    }

    public synchronized void invalidate() {
        this.cache = null;
        log.info("🧬 CrossModuleReferenceScanner 缓存已失效");
    }

    // ==================== 扫描主流程 ====================

    private void ensureCache() {
        if (cache != null) return;
        synchronized (this) {
            if (cache != null) return;

            Map<String, Map<String, VariableBlood>> built = new HashMap<>();
            Set<String> seen = new HashSet<>();

            for (Map.Entry<String, String[]> entry : SCAN_PATTERNS.entrySet()) {
                String module = entry.getKey();
                for (String pattern : entry.getValue()) {
                    Resource[] resources;
                    try {
                        resources = resolver.getResources(pattern);
                    } catch (Exception ex) {
                        log.warn("扫描 pattern 失败: {} - {}", pattern, ex.getMessage());
                        continue;
                    }
                    for (Resource r : resources) {
                        if (!r.isReadable()) continue;
                        String path = normalizePath(r);
                        if (!seen.add(module + "::" + path)) continue;
                        try {
                            scanResource(module, path, r, built);
                        } catch (Exception ex) {
                            log.warn("扫描文件失败: {} - {}", path, ex.getMessage());
                        }
                    }
                }
            }

            this.cache = built;
            int totalVars = built.values().stream().mapToInt(Map::size).sum();
            log.info("🧬 CrossModuleReferenceScanner 扫描完成: {} 案例, {} 变量",
                built.size(), totalVars);
        }
    }

    private void mergeInto(Map<String, VariableBlood> target,
                           Map<String, VariableBlood> src) {
        if (src == null) return;
        for (VariableBlood vb : src.values()) {
            VariableBlood existing = target.computeIfAbsent(vb.getVarName(), k -> {
                VariableBlood x = new VariableBlood();
                x.setVarName(k);
                return x;
            });
            for (Reference ref : vb.allRefs()) {
                existing.addRef(ref);
            }
        }
    }

    private void scanResource(String module, String filePath, Resource resource,
                              Map<String, Map<String, VariableBlood>> acc) throws Exception {
        String fileName = resource.getFilename();
        if (fileName == null) return;

        String caseId = inferCaseId(filePath, fileName);
        String sourceType = inferSourceType(filePath);

        JsonNode root;
        try (InputStream is = resource.getInputStream()) {
            boolean isJson = fileName.toLowerCase(Locale.ROOT).endsWith(".json");
            root = (isJson ? jsonMapper : yamlMapper).readTree(is);
        }
        if (root == null || root.isNull()) return;

        walk(caseId, module, filePath, sourceType, root, null, null, acc);
    }

    /**
     * 深度遍历 JSON/YAML 树，抽取变量引用。
     */
    private void walk(String caseId, String module, String filePath, String sourceType,
                      JsonNode node, String parentKey, String locationHint,
                      Map<String, Map<String, VariableBlood>> acc) {

        if (node == null || node.isNull()) return;

        // ============ 对象 ============
        if (node.isObject()) {
            String loc = deriveLocationHint(node, locationHint);
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                JsonNode val = e.getValue();

                // 补丁 1：key 位置是变量名 → 声明为 writer
                if (isVarKey(key)) {
                    String varName = key.startsWith("#") ? key.substring(1) : key;
                    addRef(acc, caseId, module, filePath, sourceType,
                        locationHint, key, varName, true);

                    // 补丁 4：如果 value 是对象，把叶子路径也声明（嵌套 Map）
                    if (val.isObject()) {
                        declareNestedVars(acc, caseId, module, filePath, sourceType,
                            locationHint, varName, val);
                    }
                }

                walk(caseId, module, filePath, sourceType, val,
                    key, loc, acc);
            }
            return;
        }

        // ============ 数组 ============
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(caseId, module, filePath, sourceType, child,
                    parentKey, locationHint, acc);
            }
            return;
        }

        // ============ 文本 ============
        if (!node.isTextual()) return;

        String text = node.asText();
        if (text == null || text.isEmpty()) return;

        // 快速剪枝：既无 # 也无 .
        if (text.indexOf('#') < 0 && text.indexOf('.') < 0) return;

        boolean writerByKey = isWriteKey(parentKey);

        // 收集赋值左值（补丁 3：支持无 # 的裸变量赋值）
        Set<String> assigned = new HashSet<>();
        Matcher am = ASSIGN_PATTERN.matcher(text);
        while (am.find()) {
            assigned.add(am.group(1));
        }

        // ---- 3.1 #var 形式 ----
        if (text.indexOf('#') >= 0) {
            Matcher vm = VAR_PATTERN.matcher(text);
            while (vm.find()) {
                String varName = vm.group(1);
                if (varName.isEmpty()) continue;
                boolean isWriter = writerByKey || assigned.contains(varName);
                addRef(acc, caseId, module, filePath, sourceType,
                    locationHint, text, varName, isWriter);
            }
        }

        // ---- 3.2 命名空间裸变量（补丁 2）----
        Matcher nm = NS_VAR_PATTERN.matcher(text);
        while (nm.find()) {
            String varName = nm.group();
            // 跳过已被 #var 处理过的（如果它前面有 #）
            int start = nm.start();
            if (start > 0 && text.charAt(start - 1) == '#') continue;

            boolean isWriter = writerByKey || assigned.contains(varName);
            addRef(acc, caseId, module, filePath, sourceType,
                locationHint, text, varName, isWriter);
        }
    }

    /**
     * 递归声明嵌套对象的叶子路径为变量（补丁 4）。
     *
     * <p>输入：
     * <pre>
     * prefix = "intervention.sedation"
     * obj = { given: false, drug: null, dose: null }
     * </pre>
     *
     * <p>输出：声明 {@code intervention.sedation.given} /
     * {@code intervention.sedation.drug} / {@code intervention.sedation.dose}
     * 三个变量为 writer。</p>
     *
     * <p>只处理合法标识符子键，避免把 {@code "some-key"} / {@code "2024"}
     * 之类当变量名。</p>
     */
    private void declareNestedVars(Map<String, Map<String, VariableBlood>> acc,
                                   String caseId, String module, String filePath, String sourceType,
                                   String locationHint, String prefix, JsonNode obj) {
        if (obj == null || !obj.isObject()) return;

        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String subKey = e.getKey();
            if (!subKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) continue;

            String fullVarName = prefix + "." + subKey;
            JsonNode subVal = e.getValue();

            // 声明当前层
            addRef(acc, caseId, module, filePath, sourceType,
                locationHint, fullVarName, fullVarName, true);

            // 若子节点还是对象，继续递归
            if (subVal != null && subVal.isObject()) {
                declareNestedVars(acc, caseId, module, filePath, sourceType,
                    locationHint, fullVarName, subVal);
            }
        }
    }

    // ==================== 引用收集 ====================

    private void addRef(Map<String, Map<String, VariableBlood>> acc,
                        String caseId, String module, String filePath, String sourceType,
                        String locationHint, String exprOrKey, String varName, boolean writer) {

        Reference ref = new Reference();
        ref.setModule(module);
        ref.setFilePath(filePath);
        ref.setSourceType(sourceType);
        ref.setLocation(locationHint);
        ref.setExpression(truncate(exprOrKey, 240));
        ref.setWriter(writer);

        acc.computeIfAbsent(caseId, k -> new LinkedHashMap<>())
            .computeIfAbsent(varName, k -> {
                VariableBlood vb = new VariableBlood();
                vb.setVarName(k);
                return vb;
            })
            .addRef(ref);
    }

    // ==================== 辅助方法 ====================

    private boolean isVarKey(String key) {
        if (key == null || key.isEmpty()) return false;
        String k = key.startsWith("#") ? key.substring(1) : key;
        int dot = k.indexOf('.');
        if (dot <= 0) return false;
        String ns = k.substring(0, dot);
        if (!KNOWN_NAMESPACES.contains(ns)) return false;
        String rest = k.substring(dot + 1);
        return rest.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*");
    }

    private String deriveLocationHint(JsonNode obj, String current) {
        if (obj.hasNonNull("from") && obj.hasNonNull("to")) {
            return obj.get("from").asText() + " → " + obj.get("to").asText();
        }
        if (obj.hasNonNull("id")) return obj.get("id").asText();
        if (obj.hasNonNull("name")) return obj.get("name").asText();
        return current;
    }

    private boolean isWriteKey(String key) {
        if (key == null) return false;
        String n = key.replace("_", "").toLowerCase(Locale.ROOT);
        if (WRITE_KEYS.contains(n)) return true;
        return n.endsWith("effect") || n.endsWith("write") || n.endsWith("writes");
    }

    private String normalizePath(Resource r) {
        try {
            String url = r.getURL().toString();
            int jar = url.indexOf("!/");
            if (jar >= 0) return url.substring(jar + 2);
            String filePath = r.getFile().getPath().replace('\\', '/');
            int idx = filePath.indexOf("/config/");
            if (idx >= 0) return filePath.substring(idx + 1);
            return r.getFilename();
        } catch (Exception ex) {
            return r.getFilename();
        }
    }

    private String inferCaseId(String filePath, String fileName) {
        String[] parts = filePath.split("/");

        for (int i = 0; i < parts.length - 1; i++) {
            if ("cases".equals(parts[i])) {
                String next = parts[i + 1];
                if (!next.startsWith("_")) return next;
                return "GLOBAL";
            }
        }

        for (int i = 0; i < parts.length - 1; i++) {
            if ("case_specific".equals(parts[i])) {
                String next = parts[i + 1];
                if (!next.startsWith("_")) return next;
            }
        }

        if (filePath.contains("/routing/")) {
            String name = fileName.replaceAll("\\.(ya?ml|json)$", "");
            if (Set.of("common", "guardrails", "semantic-routing").contains(name)) {
                return "GLOBAL";
            }
            return name;
        }

        for (int i = 0; i < parts.length - 1; i++) {
            if ("physiology".equals(parts[i])) {
                String next = parts[i + 1];
                if (!next.startsWith("_")) return next;
            }
        }

        return "GLOBAL";
    }

    private String inferSourceType(String filePath) {
        if (filePath.contains("_flow"))                 return "transition";
        if (filePath.contains("_derivations"))          return "derivation";
        if (filePath.contains("_exceptions"))           return "exception";
        if (filePath.contains("routing/"))              return "routing";
        if (filePath.contains("guardrails"))            return "guard";
        if (filePath.contains("skills/"))               return "skill";
        if (filePath.contains("scenario_beats"))        return "beat";
        if (filePath.contains("clinical_rules"))        return "rule";
        if (filePath.contains("lesson_plan"))           return "lesson";
        if (filePath.contains("fallback_paths"))        return "fallback";
        if (filePath.contains("instructor_overrides"))  return "override";
        if (filePath.contains("drug_indications"))      return "indication";
        if (filePath.contains("physiology/"))           return "physiology";
        return "config";
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max) + "…");
    }
}
