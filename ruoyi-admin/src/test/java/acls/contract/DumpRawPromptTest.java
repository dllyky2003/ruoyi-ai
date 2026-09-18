package acls.contract;

import com.ruoyi.acls.contract.RawContractPromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class DumpRawPromptTest {

    @Autowired
    private RawContractPromptBuilder builder;

    @Test
    void dump() throws Exception {
        Path file = builder.build("stable_narrow");
        System.out.println("\n===== Raw Prompt =====");
        System.out.println("路径: " + file.toAbsolutePath());
        System.out.println("大小: " + Files.size(file) + " 字节");

        String content = Files.readString(file);
        System.out.println("行数: " + content.split("\n", -1).length);

        // 检查关键内容
        String[] mustContain = {
            "S-101-1-1", "S-101-2-2", "S-101-3-2", "S-101-4-4",
            "F_101_DO_ADENOSINE_6MG", "F_101_UNSTABLE_CARDIOVERSION",
            "derived.qrsWidth", "derived.stable",
            "WF-001A", "WF-002A",
        };
        for (String key : mustContain) {
            boolean found = content.contains(key);
            System.out.printf("   包含 %-40s : %s%n", key, found ? "✅" : "❌");
        }

        // 打印前 100 行
        String[] lines = content.split("\n", -1);
        System.out.println("\n===== 前 100 行 =====");
        for (int i = 0; i < Math.min(100, lines.length); i++) {
            System.out.printf("%4d | %s%n", i + 1, lines[i]);
        }
    }
}
