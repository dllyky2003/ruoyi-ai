package com.ruoyi.ems.intent.test;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.intent.model.IntentDefinition;
import com.ruoyi.ems.intent.model.SubDomain;
import com.ruoyi.ems.intent.prompt.PromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptTemplateService 测试类 - 真实加载意图数据
 * 参考 IntentClassificationServiceTest 的方式，使用真实的 CaseIntentDefinitionsLoader
 */
class PromptTemplateServiceTest {

    private CaseIntentDefinitionsLoader realLoader;
    private PromptTemplateService promptService;

    @BeforeEach
    void setUp() {
        // ============================================================
        // 1. 创建真实的 Loader（与生产环境一致）
        // ============================================================
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        realLoader = new CaseIntentDefinitionsLoader(resolver);

        // 执行加载（会扫描 classpath:config/acls/**/_intents.json）
        realLoader.init();

        // 打印加载状态
        System.out.println("✅ 意图加载完成，共 " + realLoader.getAllIntentList().size() + " 个意图");
        System.out.println("   - 通用意图: " + realLoader.getGeneralIntentCodes().size() + " 个");
        System.out.println("   - 特定意图: " + realLoader.getSpecificIntentCodes().size() + " 个");
        System.out.println("   - 子域: " + realLoader.getSubDomainCount() + " 个");
        realLoader.printSubDomainStats();

        // ============================================================
        // 2. 创建 PromptTemplateService（需要手动初始化模板）
        // ============================================================
        promptService = new PromptTemplateService(realLoader);

        // 手动调用 init() 加载模板文件（绕过 @PostConstruct）
        promptService.init();
    }

    // ============================================================
    // 测试意图加载是否成功
    // ============================================================
    @Test
    void testIntentLoader_shouldLoadAllIntents() {
        // 验证有意图加载
        assertThat(realLoader.getAllIntentList()).isNotEmpty();

        // 验证通用意图存在
        assertThat(realLoader.getGeneralIntentCodes()).isNotEmpty();

        // 验证子域数量 > 0
        assertThat(realLoader.getSubDomainCount()).isGreaterThan(0);

        // 验证特定已知意图存在（如果存在 G1-1）
        IntentDefinition g1 = realLoader.getIntent("G1-1");
        if (g1 != null) {
            assertThat(g1.getNameCn()).isNotEmpty();
        }
    }


    // ============================================================
    // 测试 buildOneStageSystemPrompt（带上下文）
    // ============================================================
    @Test
    void testBuildOneStageSystemPrompt_WithContext() {
        // 构造测试上下文（使用真实的意图编码）
        ClassificationContext context = ClassificationContext.builder()
            .availableIntents(Set.of("G1-1", "G1-2", "G2-1", "S2-1"))
            .currentStateDescription("稳定窄QRS心动过速，准备进行迷走神经刺激")
            .lastIntent("G1-1")
            .elapsedSeconds(45)
            .build();

        String prompt = promptService.buildOneStageSystemPrompt(context);

        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("场景上下文");
        assertThat(prompt).contains("稳定窄QRS心动过速");
        assertThat(prompt).contains("上一个操作");

        // 打印完整 Prompt
        System.out.println("\n========== 一级分类 Prompt（带上下文） ==========\n");
        System.out.println(prompt);
        System.out.println("\n==================================================\n");
    }

    // ============================================================
    // 测试 buildOneStageUserPrompt
    // ============================================================
    @Test
    void testBuildOneStageUserPrompt(ClassificationContext context) {
        String asrText = "大家手离开床，准备放电";
        String prompt = promptService.buildOneStageUserPrompt(asrText,context);

        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains(asrText);
        assertThat(prompt).contains("请根据上述规则进行意图分类");

        System.out.println("\n========== User Prompt ==========\n");
        System.out.println(prompt);
        System.out.println("\n==================================\n");
    }

    // ============================================================
    // 测试候选意图过滤逻辑
    // ============================================================
    @Test
    void testCandidateFiltering_WithAvailableIntents() {
        // 只允许 2 个意图
        Set<String> available = Set.of("G1-1", "G1-2");

        ClassificationContext context = ClassificationContext.builder()
            .availableIntents(available)
            .build();

        String prompt = promptService.buildOneStageSystemPrompt(context);

        // 验证只包含这 2 个意图
        assertThat(prompt).contains("G1-1");
        assertThat(prompt).contains("G1-2");
        // 注意：可能还会包含其他文本，但确保候选列表中没有 G2-1 等
        // 因为 prompt 中候选意图列表是动态生成的，检查是否只列出了 2 个
        assertThat(prompt).contains("候选意图（共 2 个）");
    }

    // ============================================================
    // 独立 main 方法（快速测试，不依赖 JUnit）
    // ============================================================
    public static void main(String[] args) {
        System.out.println("========== 开始测试（main 方式） ==========\n");

        // 1. 创建真实 Loader
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CaseIntentDefinitionsLoader loader = new CaseIntentDefinitionsLoader(resolver);
        loader.init();

        System.out.println("✅ 加载完成，共 " + loader.getAllIntentList().size() + " 个意图");

        // 2. 创建 PromptTemplateService
        PromptTemplateService service = new PromptTemplateService(loader);
        service.init();



        // 4. 打印带上下文 Prompt
        ClassificationContext context = ClassificationContext.builder()
            .availableIntents(Set.of("G1-1", "G1-2", "S2-1"))
            .currentStateDescription("稳定窄QRS心动过速，准备进行迷走神经刺激")
            .lastIntent("G1-1")
            .elapsedSeconds(45)
            .build();

//        System.out.println(service.buildOneStageSystemPrompt(context));
//
//        // 5. 打印 User Prompt
//        System.out.println("\n========== User Prompt ==========\n");
//        System.out.println(service.buildOneStageUserPrompt("大家手离开床，准备放电",context));

        System.out.println(service.buildDomainRouterSystemPrompt());
        System.out.println("\n========== User Prompt ==========\n");
        System.out.println(service.buildDomainRouterUserPrompt("大家手离开床，准备放电",context));
    }
}
