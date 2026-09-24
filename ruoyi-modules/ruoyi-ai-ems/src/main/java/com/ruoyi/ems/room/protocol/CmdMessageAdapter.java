package com.ruoyi.ems.room.protocol;

import com.ruoyi.ems.room.protocol.dto.CmdMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 契约 V1.0.0 §5.1 适配层。
 *
 * <p>旧 asr_transcript 只有 text + isFinal，没有 clientSequence /
 * idempotencyKey / expectedStateRevision / timeBasis。这里把它包装成
 * 标准 CMD，让 CmdMessageHandler 可以统一处理。</p>
 *
 * <p>过渡期策略：</p>
 * <ul>
 *   <li>idempotencyKey：生成 UUID（旧协议无幂等，按新键处理）</li>
 *   <li>clientSequence：用 attemptId 内自增器维护</li>
 *   <li>expectedStateRevision：从 CaseContext 读最新 revision</li>
 *   <li>timeBasis：默认 simulated（旧协议无 real 语义）</li>
 * </ul>
 */
@Slf4j
@Component
public class CmdMessageAdapter {

    public CmdMessage fromAsrTranscript(String attemptId,
                                        String emsSessionId,
                                        String text,
                                        long expectedStateRevision,
                                        long nextClientSequence) {
        CmdMessage cmd = new CmdMessage();
        cmd.setAttemptId(attemptId);
        cmd.setClientSequence(nextClientSequence);
        cmd.setIdempotencyKey(UUID.randomUUID().toString());
        cmd.setExpectedStateRevision(expectedStateRevision);
        cmd.setInterventionId(null);              // 由意图分类层填
        cmd.setParameters(Map.of("emsSessionId", emsSessionId));
        cmd.setRawInput(text);
        cmd.setTimeBasis("simulated");
        return cmd;
    }
}
