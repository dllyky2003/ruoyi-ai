package org.ruoyi.ecg.domain;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置校验结果
 *
 * @author ecg-system
 */
public class ConfigValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否校验通过（无错误） */
    private boolean valid;

    /** 错误列表（阻断性问题，必须修复） */
    private List<String> errors;

    /** 警告列表（建议修复） */
    private List<String> warnings;

    /** 优化建议列表 */
    private List<String> suggestions;

    public ConfigValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.suggestions = new ArrayList<>();
        this.valid = true;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
