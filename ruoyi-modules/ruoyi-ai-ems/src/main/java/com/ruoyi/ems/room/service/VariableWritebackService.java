package com.ruoyi.ems.room.service;


import com.ruoyi.ems.context.EmsContextManager;
import com.ruoyi.ems.domain.CaseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变量回写服务。
 * <p>
 * NPC 动作完成的 outputs、体征同步等都需要把值写回 CaseContext。
 * 统一走 EmsContextManager.batchSetVarsWithVersion，处理乐观锁重试。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableWritebackService {

    private final EmsContextManager contextManager;

    /**
     * 批量写入变量。version 冲突时自动重试一次。
     *
     * @param emsSessionId 业务会话 ID
     * @param updates      变量名 → 新值
     * @param source       写入来源，用于审计（npc_action / engine / skill 等）
     * @return 是否成功
     */
    public boolean writeAll(String emsSessionId, Map<String, Object> updates, String source) {
        if (emsSessionId == null || updates == null || updates.isEmpty()) {
            return true;
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        updates.forEach((k, v) -> {
            if (k != null && v != null) filtered.put(k, v);
        });
        if (filtered.isEmpty()) return true;

        String src = (source != null && !source.isBlank()) ? source : "SYSTEM";

        CaseContext ctx = contextManager.getSnapshot(emsSessionId);
        long version = ctx != null ? ctx.getVersion() : 0L;

        boolean ok = contextManager.batchSetVarsWithVersion(
            emsSessionId, filtered, src, version);

        if (!ok) {
            CaseContext retry = contextManager.getSnapshot(emsSessionId);
            long retryVersion = retry != null ? retry.getVersion() : 0L;
            ok = contextManager.batchSetVarsWithVersion(
                emsSessionId, filtered, src, retryVersion);

            if (ok) {
                log.debug("变量回写重试成功: session={}, source={}, count={}",
                    emsSessionId, src, filtered.size());
            } else {
                log.error("变量回写失败: session={}, source={}, vars={}",
                    emsSessionId, src, filtered.keySet());
            }
        } else {
            log.debug("变量回写成功: session={}, source={}, vars={}",
                emsSessionId, src, filtered.keySet());
        }
        return ok;
    }

    /**
     * 写入单个变量。
     */
    public boolean write(String emsSessionId, String varName, Object value, String source) {
        if (varName == null || value == null) return false;
        return writeAll(emsSessionId, Map.of(varName, value), source);
    }
}
