package com.ruoyi.acls.vr;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

@Getter
public class VrActionCompletedEvent extends ApplicationEvent {
    private final String sessionId;
    private final String requestId;
    private final String action;
    private final Map<String, Object> params;
    private final boolean success;
    private final String errorMessage;

    public VrActionCompletedEvent(Object source,
                                  String sessionId,
                                  String requestId,
                                  String action,
                                  Map<String, Object> params,
                                  boolean success,
                                  String errorMessage) {
        super(source);
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.action = action;
        this.params = params;
        this.success = success;
        this.errorMessage = errorMessage;
    }
}
