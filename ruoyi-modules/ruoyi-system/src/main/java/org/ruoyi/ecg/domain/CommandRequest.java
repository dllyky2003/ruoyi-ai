package org.ruoyi.ecg.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 指令路由请求对象
 *
 * @author ecg-system
 */
@Schema(description = "指令路由请求参数")
public class CommandRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 语音转文本后的内容（ASR输出） */
    @Schema(description = "语音转文本后的内容（ASR输出）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "准备200J除颤")
    @NotBlank(message = "语音文本不能为空")
    private String asrText;

    /** 当前阶段（S0/S1/S2/...） */
    @Schema(description = "当前急救流程阶段",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "S4",
            allowableValues = {"S0", "S1", "S2", "S3", "S4", "S5", "S6"})
    @NotBlank(message = "当前阶段不能为空")
    private String currentPhase;

    /** 会话ID */
    @Schema(description = "考核会话ID", example = "SIM_20260811_001")
    private String sessionId;

    /** 病例ID */
    @Schema(description = "病例ID", example = "CASE_VF_ADULT_001")
    private String caseId;

    public CommandRequest() {
    }

    public CommandRequest(String asrText, String currentPhase) {
        this.asrText = asrText;
        this.currentPhase = currentPhase;
    }

    // ====== Getter / Setter ======

    public String getAsrText() {
        return asrText;
    }

    public void setAsrText(String asrText) {
        this.asrText = asrText;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }
}
