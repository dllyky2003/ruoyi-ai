package com.ruoyi.ems.room.event;

import org.springframework.context.ApplicationEvent;

/**
 * 案例正式开始事件。
 *
 * <p>由 {@code EngineSessionControlHandler.handleStartSession} 在
 * pushSnapshot(full=true) 之后发布，供 tutor 侧监听以构建并下发 ATTEMPT_MANIFEST。</p>
 *
 * <p>为何用 Spring Event 而非直接依赖：
 * {@code AttemptManifest} 定义在 acls-tutor 模块，ems 模块不能反向依赖它。
 * 通过事件解耦，ems 只发布"案例已开始"的语义事件，
 * acls-tutor 侧自行决定如何构建 / 下发 Manifest。</p>
 */
public class AttemptStartedEvent extends ApplicationEvent {

    private final String emsSessionId;
    private final String roomSessionId;
    private final String attemptId;
    private final String caseId;

    public AttemptStartedEvent(Object source,
                               String emsSessionId,
                               String roomSessionId,
                               String attemptId,
                               String caseId) {
        super(source);
        this.emsSessionId = emsSessionId;
        this.roomSessionId = roomSessionId;
        this.attemptId = attemptId;
        this.caseId = caseId;
    }

    public String getEmsSessionId()  { return emsSessionId; }
    public String getRoomSessionId() { return roomSessionId; }
    public String getAttemptId()     { return attemptId; }
    public String getCaseId()        { return caseId; }
}
