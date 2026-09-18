package com.ruoyi.ems.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


public class VariableKeyGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    // 层级 → 类名映射
    private static final Map<String, String> LAYER_MAP = new LinkedHashMap<>();

    static {
        LAYER_MAP.put("phys",         "PhysKeys");
        LAYER_MAP.put("device",       "DeviceKeys");
        LAYER_MAP.put("intervention", "InterventionKeys");
        LAYER_MAP.put("process",      "ProcessKeys");
        LAYER_MAP.put("derived",      "DerivedKeys");
        LAYER_MAP.put("assert",       "AssertKeys");
        LAYER_MAP.put("report",       "ReportKeys");
        // ===== 本次新增（原先都掉进 OtherKeys） =====
        LAYER_MAP.put("case",         "CaseKeys");
        LAYER_MAP.put("clinical",     "ClinicalKeys");
        LAYER_MAP.put("evaluation",   "EvaluationKeys");
        // 其他未匹配的放 OtherKeys
    }

    /**
     * 项目根目录（从 Maven 属性传入，或硬编码为项目路径）
     */
    private static final String PROJECT_ROOT = System.getProperty("project.basedir", ".");

    public static void main(String[] args) throws IOException {
        String moduleRoot = System.getProperty("user.dir");

        File globalConfigDir = new File(moduleRoot, "ruoyi-modules/ruoyi-ai-ems/src/main/resources/config/acls");
        if (!globalConfigDir.exists()) {
            globalConfigDir = new File(moduleRoot, "src/main/resources/config/acls");
        }

        if (!globalConfigDir.exists()) {
            System.err.println("❌ 无法定位 config 目录，请通过参数指定");
            System.err.println("   用法: java VariableKeyGenerator <config目录> <输出目录>");
            System.exit(1);
        }

        // 确定配置目录和输出目录
        String configDir = args.length > 0 ? args[0]
            : moduleRoot + "/ruoyi-modules/ruoyi-ai-ems/src/main/resources/config/acls";
        String outputDir = args.length > 1 ? args[1]
            : moduleRoot + "/ruoyi-modules/ruoyi-ai-ems/src/main/java/com/ruoyi/ems/constant";

        System.out.println("📁 配置目录: " + configDir);
        System.out.println("📁 输出目录: " + outputDir);

        // 1. 收集所有 _variables.json 文件
        List<File> varFiles = collectVariableFiles(new File(configDir));
        System.out.println("📁 找到 " + varFiles.size() + " 个 _variables.json 文件");

        if (varFiles.isEmpty()) {
            System.err.println("⚠️ 未找到任何 _variables.json 文件，跳过生成");
            return;
        }

        // 2. 提取所有变量定义（合并去重）
        Map<String, VariableDef> allVars = new LinkedHashMap<>();
        for (File file : varFiles) {
            extractVariables(file, allVars);
        }
        System.out.println("📖 共提取 " + allVars.size() + " 个变量定义");

        if (allVars.isEmpty()) {
            System.err.println("⚠️ 未提取到任何变量定义，跳过生成");
            return;
        }

        // 3. 按层级分组
        Map<String, List<VariableDef>> byLayer = new LinkedHashMap<>();
        for (VariableDef v : allVars.values()) {
            String layer = extractLayer(v.getName());
            byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(v);
        }

        // 4. 确保输出目录存在
        String packageDir = outputDir;
        File packageDirFile = new File(packageDir);
        if (!packageDirFile.exists()) {
            packageDirFile.mkdirs();
        }

        // 5. 生成每个层级的常量类
        for (Map.Entry<String, List<VariableDef>> entry : byLayer.entrySet()) {
            String className = LAYER_MAP.getOrDefault(entry.getKey(), "OtherKeys");
            String content = generateLayerClass(className, entry.getValue());
            File javaFile = new File(packageDirFile, className + ".java");
            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(content);
            }
            System.out.println("✅ 生成 " + className + ".java (" + entry.getValue().size() + " 个变量)");
        }

        // 6. 生成 AllVarKeys（统一校验入口）
        String allKeysContent = generateAllKeysClass(allVars.values());
        File allKeysFile = new File(packageDirFile, "AllVarKeys.java");
        try (FileWriter writer = new FileWriter(allKeysFile)) {
            writer.write(allKeysContent);
        }
        System.out.println("✅ 生成 AllVarKeys.java (" + allVars.size() + " 个变量)");

        System.out.println("🎉 代码生成完成！");
    }

    // ==================== 文件扫描 ====================

    private static List<File> collectVariableFiles(File baseDir) {
        List<File> files = new ArrayList<>();

        // 1. GLOBAL/_variables.json
        File globalFile = new File(baseDir, "GLOBAL/_variables.json");
        if (globalFile.exists()) {
            files.add(globalFile);
            System.out.println("  📄 GLOBAL/_variables.json");
        }

        // 2. cases/*/_variables.json
        File casesDir = new File(baseDir, "cases");
        if (casesDir.exists() && casesDir.isDirectory()) {
            File[] caseDirs = casesDir.listFiles(File::isDirectory);
            if (caseDirs != null) {
                for (File caseDir : caseDirs) {
                    File varFile = new File(caseDir, "_variables.json");
                    if (varFile.exists()) {
                        files.add(varFile);
                        System.out.println("  📄 cases/" + caseDir.getName() + "/_variables.json");
                    }
                }
            }
        }

        return files;
    }

    // ==================== 变量提取 ====================

    private static void extractVariables(File file, Map<String, VariableDef> allVars) throws IOException {
        JsonNode root = MAPPER.readTree(file);

        // 从文件路径提取 scope（案例类型）
        String scope = extractScopeFromPath(file.getAbsolutePath());

        JsonNode varsNode = root.get("variables");
        if (varsNode == null || !varsNode.isArray()) {
            System.err.println("⚠️ 文件 " + file.getName() + " 没有 variables 数组");
            return;
        }

        for (JsonNode node : varsNode) {
            String name = node.path("name").asText();
            if (name.isEmpty()) continue;

            VariableDef def = allVars.computeIfAbsent(name, k -> new VariableDef());
            def.setName(name);
            if (def.getType() == null) {
                def.setType(node.path("type").asText(null));
            }
            if (def.getDescription() == null) {
                def.setDescription(node.path("description").asText(null));
            }
            if (def.getDefaultValue() == null) {
                JsonNode defaultNode = node.path("default_value");
                if (!defaultNode.isMissingNode()) {
                    def.setDefaultValue(defaultNode.asText());
                }
            }
            // 合并 scope
            if (def.getScope() == null || "GLOBAL".equals(def.getScope())) {
                def.setScope(scope);
            }
            // derived 标记
            if (node.path("derived").asBoolean(false)) {
                def.setDerived(true);
            }
        }
    }

    private static String extractScopeFromPath(String path) {
        // GLOBAL/_variables.json → GLOBAL
        if (path.contains("/GLOBAL/") || path.contains("\\GLOBAL\\")) {
            return "GLOBAL";
        }
        // cases/stable_narrow/_variables.json → stable_narrow
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(".*[/\\\\]cases[/\\\\]([^/\\\\]+)[/\\\\].*");
        java.util.regex.Matcher m = p.matcher(path);
        if (m.find()) {
            return m.group(1);
        }
        return "GLOBAL";
    }

    // ==================== 层级提取 ====================

    private static String extractLayer(String varName) {
        int dotIndex = varName.indexOf('.');
        if (dotIndex > 0) {
            String prefix = varName.substring(0, dotIndex);
            if (LAYER_MAP.containsKey(prefix)) {
                return prefix;
            }
        }
        return "other";
    }

    // ==================== 生成代码 ====================

    private static String generateLayerClass(String className, List<VariableDef> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ============================================================\n");
        sb.append("// ⚠️ 自动生成代码 - 请勿手动编辑\n");
        sb.append("// 源文件: _variables.json\n");
        sb.append("// 生成时间: ").append(new Date()).append("\n");
        sb.append("// 修改后请执行 mvn generate-sources 重新生成\n");
        sb.append("// ============================================================\n\n");
        sb.append("package com.ruoyi.ems.constant;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(className).append("\n");
        sb.append(" * <p>包含 ").append(vars.size()).append(" 个变量常量\n");
        sb.append(" * <p>使用示例: <code>String key = ").append(className).append(".HEART_RATE;</code>\n");
        sb.append(" */\n");
        sb.append("public final class ").append(className).append(" {\n\n");
        sb.append("    private ").append(className).append("() {}\n\n");

        // 排序
        vars.sort(Comparator.comparing(VariableDef::getName));

        for (VariableDef v : vars) {
            String constName = toConstantName(v.getName());
            if (v.getDescription() != null && !v.getDescription().isEmpty()) {
                sb.append("    /** ").append(v.getDescription()).append(" */\n");
            }
            sb.append("    public static final String ").append(constName)
                .append(" = \"").append(v.getName()).append("\";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String generateAllKeysClass(Collection<VariableDef> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.ruoyi.ems.constant;\n\n");
        sb.append("import java.util.Collections;\n");
        sb.append("import java.util.HashSet;\n");
        sb.append("import java.util.Set;\n\n");
        sb.append("/**\n");
        sb.append(" * 所有变量键的统一集合\n");
        sb.append(" * 用于启动时校验配置文件中的变量引用是否合法\n");
        sb.append(" */\n");
        sb.append("public final class AllVarKeys {\n\n");
        sb.append("    private AllVarKeys() {}\n\n");

        sb.append("    public static final Set<String> ALL_VARS = Collections.unmodifiableSet(\n");
        sb.append("        new HashSet<>(java.util.Arrays.asList(\n");

        List<VariableDef> sorted = new ArrayList<>(vars);
        sorted.sort(Comparator.comparing(VariableDef::getName));
        for (int i = 0; i < sorted.size(); i++) {
            VariableDef v = sorted.get(i);
            sb.append("            \"").append(v.getName()).append("\"");
            if (i < sorted.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("        ))\n");
        sb.append("    );\n\n");

        sb.append("    public static boolean isValid(String name) {\n");
        sb.append("        return ALL_VARS.contains(name);\n");
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static String toConstantName(String varName) {
        // phys.heartRate → PHYS_HEART_RATE
        // intervention.adenosineGiven → INTERVENTION_ADENOSINE_GIVEN
        String result = varName
            .replace(".", "_")
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .toUpperCase();
        // 替换非法字符
        result = result.replaceAll("[^A-Z0-9_]", "");
        return result;
    }

    // ==================== 数据模型 ====================

    @Data
    public static class VariableDef {
        private String name;
        private String type;
        private String defaultValue;
        private String scope;
        private boolean derived;
        private String description;
    }
}
