package com.ruoyi.acls.branch;


import com.ruoyi.ems.spi.StudentResponseBranchProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link StudentResponseBranchProvider} 的 acls-engine 实现。
 *
 * <p>把 {@link StudentResponseResolver} 的三态枚举适配为字符串，
 * 供 EMS 侧 EvolutionRuleEvaluator 使用。</p>
 */
@Component
@RequiredArgsConstructor
public class StudentResponseBranchProviderImpl implements StudentResponseBranchProvider {

    private final StudentResponseResolver resolver;

    @Override
    public String resolveBranch(String sessionId) {
        StudentResponseBranch b = resolver.resolve(sessionId);
        return b != null ? b.name() : null;
    }
}
