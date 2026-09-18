package com.ruoyi.acls.vr;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class VrActionResult {
    private String requestId;
    private String status;   // "SUCCESS", "FAILED", "TIMEOUT"
    private Map<String, Object> details;

    public static VrActionResult success(String requestId, Map<String, Object> details) {
        return new VrActionResult(requestId, "SUCCESS", details);
    }

    public static VrActionResult failure(String requestId, String error) {
        return new VrActionResult(requestId, "FAILED", Map.of("error", error));
    }

    public static VrActionResult timeout(String requestId, String error) {
        return new VrActionResult(requestId, "TIMEOUT", Map.of("error", error));
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
