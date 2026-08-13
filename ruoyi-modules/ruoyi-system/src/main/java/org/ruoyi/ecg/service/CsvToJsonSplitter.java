package org.ruoyi.ecg.service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvToJsonSplitter {

    public static void main(String[] args) {
        String csvFilePath = args.length > 0 ? args[0] : "d:\\csv-Influenzai-set.csv";
        String outputDir = args.length > 1 ? args[1] : "d:\\output";
        int rowsPerFile = args.length > 2 ? Integer.parseInt(args[2]) : 100;

        try {
            new CsvToJsonSplitter().process(csvFilePath, outputDir, rowsPerFile);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("错误: " + e.getMessage());
            System.err.println("用法: java CsvSplitterAdvanced <csv文件路径> <输出目录> [每文件行数]");
        }
    }

    public void process(String csvFilePath, String outputDir, int rowsPerFile) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 读取所有行（支持带引号的换行）
        List<String> rawLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(csvFilePath)), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                rawLines.add(line);
            }
        }

        if (rawLines.isEmpty()) {
            throw new IOException("CSV文件为空");
        }

        // 合并被引号包裹的换行
        List<String> normalizedLines = normalizeCsvLines(rawLines);

        if (normalizedLines.isEmpty()) {
            throw new IOException("没有有效数据");
        }

        System.out.println("共读取 " + normalizedLines.size() + " 行（含表头）");

        String header = normalizedLines.get(0);
        List<String> dataLines = normalizedLines.subList(1, normalizedLines.size());

        int totalFiles = (dataLines.size() + rowsPerFile - 1) / rowsPerFile;
        for (int fileIndex = 0; fileIndex < totalFiles; fileIndex++) {
            int start = fileIndex * rowsPerFile;
            int end = Math.min(start + rowsPerFile, dataLines.size());
            List<String> chunk = dataLines.subList(start, end);

            String fileName = String.format("%s/part_%04d.csv", outputDir, fileIndex + 1);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(Paths.get(fileName)), StandardCharsets.UTF_8))) {

                writer.write(header);
                writer.newLine();

                for (String row : chunk) {
                    writer.write(row);
                    writer.newLine();
                }
            }

            System.out.printf("已写入 %s，包含 %d 条数据行%n", fileName, chunk.size());
        }

        System.out.println("处理完成，共生成 " + totalFiles + " 个CSV文件");
    }

    /**
     * 合并被双引号包裹的换行
     */
    private List<String> normalizeCsvLines(List<String> rawLines) {
        List<String> result = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        boolean inQuotes = false;

        for (String line : rawLines) {
            if (currentLine.length() > 0) {
                currentLine.append("\n");
            }
            currentLine.append(line);

            // 统计当前行的引号数
            for (char c : line.toCharArray()) {
                if (c == '"') {
                    inQuotes = !inQuotes;
                }
            }

            // 如果不在引号内，说明当前行完整
            if (!inQuotes) {
                result.add(currentLine.toString());
                currentLine = new StringBuilder();
            }
        }

        // 如果还有未完成的行，也添加进去
        if (currentLine.length() > 0) {
            result.add(currentLine.toString());
        }

        return result;
    }
}
