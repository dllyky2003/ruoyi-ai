package com.ruoyi.acls.arbiter;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ArbiterResult {
    private boolean allowed;
    private String reason;
    private String userFriendlyMessage;

    public static ArbiterResult allow() {
        return new ArbiterResult(true, null, null);
    }

    public static ArbiterResult deny(String userFriendlyMessage) {
        return new ArbiterResult(false, userFriendlyMessage, userFriendlyMessage);
    }

    public static ArbiterResult deny(String reason, String userFriendlyMessage) {
        return new ArbiterResult(false, reason, userFriendlyMessage);
    }
}
