package acls.contract;

import com.ruoyi.acls.contract.CompactPromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DumpCompactPromptTest {

    @Autowired
    private CompactPromptBuilder builder;

    @Test
    void dump() throws Exception {
        Path file = builder.build("stable_narrow");
        System.out.println("\n===== Compact Prompt =====");
        System.out.println("路径: " + file.toAbsolutePath());
        System.out.println("大小: " + Files.size(file) + " 字节");

        String content = Files.readString(file);
        System.out.println("行数: " + content.split("\n", -1).length);
        System.out.println("估计 token: " + (Files.size(file) / 4));

        // 检查关键内容
        String[] mustContain = {
            // 状态机：父级 + 子级
            "F-000-SCENE_START",
            "F-000-INITIAL_SETUP",
            "F_101_DO_ADENOSINE_6MG",
            "F_101_UNSTABLE_CARDIOVERSION",
            // 案例专属意图
            "S-101-1-1", "S-101-2-2", "S-101-3-2", "S-101-4-4",
            // 派生规则
            "derived.qrsWidth", "derived.stable",
            // 变量
            "clinical.isWpw", "phys.heartRate",
            // 护栏
            "WF-001A", "WF-002A",
            // 2025 AHA
            "AHA2025-WPW-ADENOSINE-12",
            // 终止性规则
            "T1.",
        };

        System.out.println("\n关键内容检查:");
        for (String key : mustContain) {
            boolean found = content.contains(key);
            System.out.printf("   包含 %-40s : %s%n", key, found ? "✅" : "❌");
        }

        // 未替换占位符
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\{[a-zA-Z][a-zA-Z0-9_]*\\}").matcher(content);
        java.util.List<String> unresolved = new java.util.ArrayList<>();
        while (m.find()) unresolved.add(m.group());
        System.out.println("\n未替换占位符: "
            + (unresolved.isEmpty() ? "无 ✅" : unresolved + " ❌"));

        // 打印前 100 行
        String[] lines = content.split("\n", -1);
        System.out.println("\n===== 前 100 行 =====");
        for (int i = 0; i < Math.min(100, lines.length); i++) {
            System.out.printf("%4d | %s%n", i + 1, lines[i]);
        }
    }
}
