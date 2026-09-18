package com.ruoyi.ems.intent.classifier;



import java.util.Objects;

/**
 * 语用预测结果。
 *
 * @param actType    预测的语用类型
 * @param confidence 置信度 [0.00, 1.00]
 * @param reason     命中原因（不包含医疗处置内容）
 */
public record ActTypePrediction(ActType actType, double confidence, String reason) {

    public ActTypePrediction {
        Objects.requireNonNull(actType, "actType must not be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        reason = reason == null ? "" : reason;
    }

    public static ActTypePrediction of(ActType actType, double confidence, String reason) {
        return new ActTypePrediction(actType, confidence, reason);
    }
}
