package org.ruoyi.ecg.domain.intent;

import java.util.List;
import java.util.Map;

/**
 * 槽位校验结果
 */
public class SlotValidationResult {
    /** 是否全部通过 */
    private boolean valid;

    /** 提取的参数 */
    private Map<String, Object> extractedParams;

    /** 缺失的槽位列表 */
    private List<String> missingSlots;

    /** 无效的槽位列表 */
    private List<String> invalidSlots;

    /** 错误信息 */
    private String errorMessage;

    // ====== Getter / Setter ======
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public Map<String, Object> getExtractedParams() {
        return extractedParams;
    }

    public void setExtractedParams(Map<String, Object> extractedParams) {
        this.extractedParams = extractedParams;
    }

    public List<String> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<String> missingSlots) {
        this.missingSlots = missingSlots;
    }

    public List<String> getInvalidSlots() {
        return invalidSlots;
    }

    public void setInvalidSlots(List<String> invalidSlots) {
        this.invalidSlots = invalidSlots;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
