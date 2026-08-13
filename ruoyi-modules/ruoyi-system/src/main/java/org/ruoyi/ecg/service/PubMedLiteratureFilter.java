package org.ruoyi.ecg.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * PubMed文献检索程序
 * 根据简洁的PubMed检索式筛选CSV中的文献记录
 * 检索条件：H5N1 AND (人类 OR 奶牛) AND 血清学
 */
public class PubMedLiteratureFilter {

    // ============ 词库定义 ============

    // H5N1主题关键词
    private static final Set<String> H5N1_TERMS = new HashSet<>(Arrays.asList(
        "h5n1", "h5n", "h5n1 virus", "h5n1 influenza", "avian influenza h5n1",
        "influenza a virus h5n1", "influenza in birds"
    ));

    // 禽类相关词（辅助判断）
    private static final Set<String> AVIAN_TERMS = new HashSet<>(Arrays.asList(
        "avian", "bird", "birds", "poultry", "fowl", "chicken", "chickens",
        "duck", "ducks", "geese", "turkey", "quail", "wild bird", "waterfowl"
    ));

    // 人类相关词
    private static final Set<String> HUMAN_TERMS = new HashSet<>(Arrays.asList(
        "human", "humans", "people", "person", "persons", "personnel",
        "population", "individual", "individuals", "worker", "workers",
        "staff", "professional", "farm", "farmer", "farmers", "owner",
        "vendor", "trader", "slaughterer", "butcher", "patient", "patients",
        "child", "children", "adult", "adults", "elderly", "pregnant"
    ));

    // 牛相关词
    private static final Set<String> CATTLE_TERMS = new HashSet<>(Arrays.asList(
        "cattle", "bovine", "bovines", "dairy cattle", "dairy cow", "dairy cows",
        "cow", "cows", "calf", "calves", "bull", "bulls", "heifer", "heifers",
        "steer", "steers", "beef cattle", "milk cow"
    ));

    // 血清学关键词
    private static final Set<String> SEROLOGY_TERMS = new HashSet<>(Arrays.asList(
        "seroprevalence", "seroprevalent", "seropositive", "seropositivity",
        "seronegative", "serology", "serological", "serologic",
        "seroepidemiology", "seroepidemiologic", "seroepidemiological",
        "antibody", "antibodies", "igg", "igm", "iga",
        "neutralizing antibody", "neutralizing antibodies",
        "humoral", "immune response", "immunoglobulin",
        "seroconversion", "serosurvey", "serosurveillance",
        "asymptomatic", "subclinical", "serologic tests"
    ));

    // ============ 简洁匹配方法 ============

    /**
     * 检查文本是否包含某个词集中的任意词
     */
    private static boolean matchesAnyTerm(String text, Set<String> termSet) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase();

        for (String term : termSet) {
            if (lowerText.contains(term.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否包含H5N1相关概念
     */
    private static boolean containsH5N1(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        // 主要检查 h5n1
        if (lower.contains("h5n1")) return true;
        // 检查 avian influenza + h5
        if (lower.contains("avian influenza") && lower.contains("h5")) return true;
        // 检查 influenza a virus + h5n1
        if (lower.contains("influenza a virus") && lower.contains("h5n1")) return true;
        // 检查其他变体
        if (lower.contains("h5n1 influenza")) return true;
        if (lower.contains("h5n1 virus")) return true;
        return false;
    }

    /**
     * 检查是否包含血清学相关概念
     */
    private static boolean containsSerology(String text) {
        return matchesAnyTerm(text, SEROLOGY_TERMS);
    }

    /**
     * 检查是否包含人类相关概念
     */
    private static boolean containsHuman(String text) {
        return matchesAnyTerm(text, HUMAN_TERMS);
    }

    /**
     * 检查是否包含牛相关概念
     */
    private static boolean containsCattle(String text) {
        return matchesAnyTerm(text, CATTLE_TERMS);
    }

    /**
     * 简洁PubMed检索式匹配
     * 条件：H5N1 AND (人类 OR 牛) AND 血清学
     */
    public static boolean matchesQuery(Map<String, String> record) {
        // 收集所有文本字段
        String fullText = String.join(" ",
            record.getOrDefault("Title", ""),
            record.getOrDefault("Authors", ""),
            record.getOrDefault("First Author", ""),
            record.getOrDefault("Citation", ""),
            record.getOrDefault("Journal/Book", ""),
            record.getOrDefault("Abstract", "")
        ).toLowerCase();

        // 1. 必须包含H5N1主题
        boolean hasH5N1 = containsH5N1(fullText);
        if (!hasH5N1) return false;

        // 2. 必须包含人类 OR 牛
        boolean hasHuman = containsHuman(fullText);
        boolean hasCattle = containsCattle(fullText);
        if (!hasHuman && !hasCattle) return false;

        // 3. 必须包含血清学关键词
        boolean hasSerology = containsSerology(fullText);
        if (!hasSerology) return false;

        return true;
    }

    /**
     * 获取详细的匹配信息（用于调试）
     */
    public static MatchDetail getMatchDetail(Map<String, String> record) {
        String fullText = String.join(" ",
            record.getOrDefault("Title", ""),
            record.getOrDefault("Authors", ""),
            record.getOrDefault("First Author", ""),
            record.getOrDefault("Citation", ""),
            record.getOrDefault("Journal/Book", ""),
            record.getOrDefault("Abstract", "")
        ).toLowerCase();

        boolean hasH5N1 = containsH5N1(fullText);
        boolean hasHuman = containsHuman(fullText);
        boolean hasCattle = containsCattle(fullText);
        boolean hasSerology = containsSerology(fullText);
        boolean matched = hasH5N1 && (hasHuman || hasCattle) && hasSerology;

        return new MatchDetail(matched, hasSerology, hasH5N1, hasHuman, hasCattle);
    }

    // ============ 匹配详情类 ============
    public static class MatchDetail {
        public final boolean matched;
        public final boolean hasSerology;
        public final boolean hasH5N1;
        public final boolean hasHuman;
        public final boolean hasCattle;

        public MatchDetail(boolean matched, boolean hasSerology, boolean hasH5N1,
                          boolean hasHuman, boolean hasCattle) {
            this.matched = matched;
            this.hasSerology = hasSerology;
            this.hasH5N1 = hasH5N1;
            this.hasHuman = hasHuman;
            this.hasCattle = hasCattle;
        }

        public String toReasonString() {
            if (!matched) return "不匹配";
            List<String> reasons = new ArrayList<>();
            if (hasH5N1) reasons.add("H5N1");
            if (hasHuman) reasons.add("人类");
            if (hasCattle) reasons.add("牛");
            if (hasSerology) reasons.add("血清学");
            return "✓ " + String.join(" + ", reasons);
        }
    }

    // ============ CSV解析 ============

    /**
     * 解析CSV一行，处理引号内的逗号
     */
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        result.add(current.toString().trim());
        return result;
    }

    /**
     * 将CSV行转义
     */
    private static String escapeCsvField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * 读取CSV文件
     */
    private static List<Map<String, String>> readCsv(String filePath) throws IOException {
        List<Map<String, String>> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(filePath)), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("CSV文件为空");
            }

            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> values = parseCsvLine(line);
                Map<String, String> record = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {
                    String value = (i < values.size()) ? values.get(i) : "";
                    record.put(headers.get(i), value);
                }

                records.add(record);
            }
        }

        return records;
    }

    /**
     * 写入CSV文件
     */
    private static void writeCsv(String filePath, List<Map<String, String>> records,
                                  List<String> headers, boolean includeMatchInfo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(Paths.get(filePath)),
                                       StandardCharsets.UTF_8))) {

            // 写入表头
            List<String> headerList = new ArrayList<>(headers);
            if (includeMatchInfo) {
                headerList.add("Match_Reason");
                headerList.add("Has_H5N1");
                headerList.add("Has_Human");
                headerList.add("Has_Cattle");
                headerList.add("Has_Serology");
                headerList.add("Matched");
            }

            for (int i = 0; i < headerList.size(); i++) {
                if (i > 0) writer.write(",");
                writer.write(escapeCsvField(headerList.get(i)));
            }
            writer.newLine();

            // 写入数据
            for (Map<String, String> record : records) {
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) writer.write(",");
                    String value = record.getOrDefault(headers.get(i), "");
                    writer.write(escapeCsvField(value));
                }

                if (includeMatchInfo) {
                    MatchDetail detail = getMatchDetail(record);
                    writer.write("," + escapeCsvField(detail.toReasonString()));
                    writer.write("," + (detail.hasH5N1 ? "1" : "0"));
                    writer.write("," + (detail.hasHuman ? "1" : "0"));
                    writer.write("," + (detail.hasCattle ? "1" : "0"));
                    writer.write("," + (detail.hasSerology ? "1" : "0"));
                    writer.write("," + (detail.matched ? "1" : "0"));
                }
                writer.newLine();
            }
        }
    }

    // ============ 主程序 ============

    public static void main(String[] args) {
        String csvFilePath = args.length > 0 ? args[0] : "d:\\csv-Influenzai-set.csv";
        String outputDir = args.length > 1 ? args[1] : "d:\\output";
        boolean includeMatchInfo = args.length > 2 && args[2].equals("-v");

        try {
            // 创建输出目录
            File dir = new File(outputDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            System.out.println("========================================");
            System.out.println("PubMed文献检索程序");
            System.out.println("检索条件: H5N1 AND (人类 OR 牛) AND 血清学");
            System.out.println("========================================");

            System.out.println("\n开始读取CSV文件: " + csvFilePath);
            List<Map<String, String>> allRecords = readCsv(csvFilePath);
            System.out.println("共读取 " + allRecords.size() + " 条记录");

            // 获取表头
            List<String> headers = new ArrayList<>(allRecords.get(0).keySet());

            System.out.println("开始检索匹配...");
            List<Map<String, String>> matchedRecords = new ArrayList<>();
            List<Map<String, String>> unmatchedRecords = new ArrayList<>();

            int count = 0;
            for (Map<String, String> record : allRecords) {
                count++;
                if (count % 100 == 0) {
                    System.out.println("已处理 " + count + " 条，匹配 " + matchedRecords.size() + " 条");
                }

                if (matchesQuery(record)) {
                    matchedRecords.add(record);
                } else {
                    unmatchedRecords.add(record);
                }
            }

            System.out.println("\n========== 检索结果 ==========");
            System.out.println("总记录数: " + allRecords.size());
            System.out.println("匹配记录数: " + matchedRecords.size());
            System.out.println("匹配率: " + String.format("%.2f%%",
                (double) matchedRecords.size() / allRecords.size() * 100));

            // 保存匹配结果到CSV
            String matchedPath = outputDir + "/matched_results.csv";
            System.out.println("\n保存匹配结果到: " + matchedPath);
            writeCsv(matchedPath, matchedRecords, headers, includeMatchInfo);

            // 保存未匹配记录
            String unmatchedPath = outputDir + "/unmatched_results.csv";
            System.out.println("保存未匹配结果到: " + unmatchedPath);
            writeCsv(unmatchedPath, unmatchedRecords, headers, false);

            // 保存所有记录（带匹配标记）
            String allPath = outputDir + "/all_with_match_info.csv";
            System.out.println("保存完整结果（带匹配标记）到: " + allPath);
            writeCsv(allPath, allRecords, headers, true);

            System.out.println("\n处理完成！");
            System.out.println("输出目录: " + outputDir);
            System.out.println("  - matched_results.csv      : 匹配的记录");
            System.out.println("  - unmatched_results.csv    : 未匹配的记录");
            System.out.println("  - all_with_match_info.csv  : 所有记录（含匹配标记）");

            // 显示匹配示例
            if (!matchedRecords.isEmpty()) {
                System.out.println("\n匹配记录示例（前10条）:");
                int showCount = Math.min(10, matchedRecords.size());
                for (int i = 0; i < showCount; i++) {
                    Map<String, String> record = matchedRecords.get(i);
                    String title = record.getOrDefault("Title", "无标题");
                    String reason = getMatchDetail(record).toReasonString();
                    System.out.println((i+1) + ". " + title);
                    System.out.println("   " + reason);
                }
            }

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
