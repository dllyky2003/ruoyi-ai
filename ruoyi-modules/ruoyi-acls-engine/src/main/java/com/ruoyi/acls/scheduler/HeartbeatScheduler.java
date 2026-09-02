package com.ruoyi.acls.scheduler;

import com.ruoyi.acls.cognitive.CognizantTeamMember;
import com.ruoyi.acls.config.AclsEngineProperties;
import com.ruoyi.acls.factory.VirtualMemberFactory;
import com.ruoyi.ems.domain.CaseContext; // 旧模块的类，provided依赖，安全
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final VirtualMemberFactory memberFactory;
    private final AclsEngineProperties properties;

    /**
     * 独立于旧模块的定时任务，专门驱动新组员的自主思考
     * 每100ms执行一次
     */
    @Scheduled(fixedDelayString = "${acls.scheduler.tick-interval:100}")
    public void tick() {
        if (!properties.getEngine().isEnabled()) {
            return;
        }

        // 遍历所有活跃会话（需要你实现获取活跃session列表的方法）
        // 这里为了示例，假设通过某个ContextManager获取
        for (String sessionId : getActiveSessions()) {
            // 获取该会话的所有虚拟组员
            var members = memberFactory.getAll(sessionId);
            if (members.isEmpty()) continue;

            // 获取该会话的上下文（旧模块的CaseContext）
            CaseContext context = getContext(sessionId);
            if (context == null) continue;

            // 驱动每个组员的自主心跳（反射 + 慢思考）
            for (var member : members) {
                if (member instanceof CognizantTeamMember) {
                    ((CognizantTeamMember) member).autonomousTick(context);
                }
            }
        }
    }

    // 伪代码，实际需要注入 CaseContextManager
    private List<String> getActiveSessions() { return List.of(); }
    private CaseContext getContext(String id) { return null; }
}
