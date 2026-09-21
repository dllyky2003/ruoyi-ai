package com.acls.tutor.test;

import com.ruoyi.acls.contract.CaseContractBuilder;
import com.ruoyi.acls.contract.models.CaseFileBaseline;
import com.ruoyi.acls.contract.util.CaseFileReviewStatusResolver;
import com.ruoyi.acls.cognitive.ReflexRuleLoader;
import com.ruoyi.acls.contract.util.DerivationGraphBuilder;
import com.ruoyi.acls.contract.util.GuardExpressionParser;
import com.ruoyi.acls.guard.GuardRailLoader;
import com.ruoyi.acls.router.SemanticRoutingLoader;
import com.ruoyi.acls.skill.registery.SkillRegistry;
import com.ruoyi.acls.skill.registery.YamlSkillLoader;
import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.config.loader.DerivationRuleLoader;
import com.ruoyi.ems.config.loader.VariableMetaLoader;
import com.ruoyi.ems.config.loader.YamlStateMachineLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * CaseContractBuilder.buildFileBaseline 单元测试（阶段 4.1）。
 *
 * <p>注意：
 * <ul>
 *   <li>必须用 JUnit 5（org.junit.jupiter.api.*），不能混用 org.junit.Test；</li>
 *   <li>断言用 org.junit.jupiter.api.Assertions，不用 SmallRye 的 Assert；</li>
 *   <li>CaseContractBuilder 依赖全 final，显式构造最稳，不靠 @InjectMocks 猜；</li>
 *   <li>resourceLoader / reviewStatusResolver 用真对象，读 test classpath 上的 YAML；</li>
 *   <li>本测试依赖 {@code stable_narrow} YAML 的 review_status 已从 pending 转为 approved；
 *       若 YAML 未转正，把 {@link #EXPECTED_STATUS} 改回 "pending"。</li>
 * </ul>
 */
class CaseContractBaselineTest {

    /** stable_narrow YAML 转正后期望的审核状态。 */
    private static final String EXPECTED_STATUS = "approved";

    private CaseContractBuilder builder;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader rl = new DefaultResourceLoader();
        builder = new CaseContractBuilder(
            mock(GuardExpressionParser.class),
            mock(DerivationGraphBuilder.class),
            mock(YamlStateMachineLoader.class),
            mock(DerivationRuleLoader.class),
            mock(CaseIntentDefinitionsLoader.class),
            mock(SemanticRoutingLoader.class),
            mock(YamlSkillLoader.class),
            mock(VariableMetaLoader.class),
            mock(GuardRailLoader.class),
            mock(ReflexRuleLoader.class),
            mock(SkillRegistry.class),
            rl,
            new CaseFileReviewStatusResolver(rl)
        );
    }

    @Test
    void buildFileBaseline_stable_narrow应5个文件全在() {
        CaseFileBaseline b = builder.buildFileBaseline("stable_narrow");

        assertEquals(5, b.getFiles().size());
        assertTrue(b.isComplete(), "5 个文件全在时 complete 应为 true");
        assertFalse(b.isAuditBlocked(), "全部 approved 不应被 auditBlocked");

        b.getFiles().values().forEach(f -> {
            assertTrue(f.isExists(), "文件应存在: " + f.getRelativePath());
            assertEquals(64, f.getSha256().length(), "sha256 hex 应为 64 位");
            assertEquals(EXPECTED_STATUS, f.getReviewStatus(),
                "审核状态: " + f.getRelativePath());
        });
    }

    @Test
    void buildFileBaseline_不存在case应全缺失() {
        CaseFileBaseline b = builder.buildFileBaseline("nonexistent_case_xyz");

        assertFalse(b.isComplete());
        b.getFiles().values().forEach(f ->
            assertFalse(f.isExists(), "不存在的 case 下文件不应存在: " + f.getRelativePath()));
        assertNull(b.getFiles().values().iterator().next().getSha256(),
            "文件不存在时 sha256 应为 null");
    }

    @Test
    void buildFileBaseline_应幂等缓存() {
        CaseFileBaseline b1 = builder.buildFileBaseline("stable_narrow");
        CaseFileBaseline b2 = builder.buildFileBaseline("stable_narrow");
        assertSame(b1, b2, "缓存后应返回同一实例");
    }
}
