package com.ruoyi.ems.npc;

import com.ruoyi.ems.event.TaskCompletedEvent;
import com.ruoyi.ems.event.TaskProgressUpdatedEvent;
import com.ruoyi.ems.event.TaskStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class NpcEventBridge {

    private final NpcUnityBridgeService npcBridge;

    @EventListener
    public void onTaskStarted(TaskStartedEvent evt) {
        npcBridge.pushTask(evt.getSessionId(), evt.getMember(), "STARTED", null);
    }

    @EventListener
    public void onTaskProgress(TaskProgressUpdatedEvent evt) {
        double p = evt.getProgress();
        if (p >= 1.0) return;                                  // ★ 100% 让 COMPLETED 处理
        int bucket = (int) Math.round(p * 20);                 // 5% 一档
        if (evt.getMember().getLastPushedBucket() == bucket) return;
        evt.getMember().setLastPushedBucket(bucket);
        npcBridge.pushTask(evt.getSessionId(), evt.getMember(), "PROGRESS", null);
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent evt) {
        TeamMember m = evt.getMember();
        String phase = evt.isSuccess() ? "COMPLETED" : "FAILED";   // ★ 区分成功/失败
        npcBridge.pushTask(evt.getSessionId(), m, phase, evt.getReport());
        // ★ 如果已有 taskCompletionListeners 发这条 dialogue，就别在这里再发
        // npcBridge.pushDialogue(evt.getSessionId(), m.getId(), evt.getReport(), m.getTtsVoice(), false);
    }
}
