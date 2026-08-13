package org.ruoyi.ecg.domain;
import java.io.Serializable;
import java.util.List;

/**
 * 语音交付质量指标（用于领导力分析）
 */
public class DeliveryMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    private Double clarityScore;       // 清晰度评分 (0-1)
    private Integer speechRate;        // 语速（字/分钟）
    private Double calmnessScore;      // 冷静度评分 (0-1)
    private List<HesitationMarker> hesitationMarkers; // 犹豫标记

    // ====== 构造方法 ======
    public DeliveryMetrics() {}

    public DeliveryMetrics(Double clarityScore, Integer speechRate,
                           Double calmnessScore, List<HesitationMarker> hesitationMarkers) {
        this.clarityScore = clarityScore;
        this.speechRate = speechRate;
        this.calmnessScore = calmnessScore;
        this.hesitationMarkers = hesitationMarkers;
    }

    // ====== Getter / Setter ======
    public Double getClarityScore() { return clarityScore; }
    public void setClarityScore(Double clarityScore) { this.clarityScore = clarityScore; }

    public Integer getSpeechRate() { return speechRate; }
    public void setSpeechRate(Integer speechRate) { this.speechRate = speechRate; }

    public Double getCalmnessScore() { return calmnessScore; }
    public void setCalmnessScore(Double calmnessScore) { this.calmnessScore = calmnessScore; }

    public List<HesitationMarker> getHesitationMarkers() { return hesitationMarkers; }
    public void setHesitationMarkers(List<HesitationMarker> hesitationMarkers) {
        this.hesitationMarkers = hesitationMarkers;
    }

    /**
     * 犹豫标记内部类
     */
    public static class HesitationMarker implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double position;   // 在语音中的位置（秒）
        private Double duration;   // 持续时间（秒）
        private String type;       // 类型：pause / filler / repeat

        public HesitationMarker() {}

        public HesitationMarker(Double position, Double duration, String type) {
            this.position = position;
            this.duration = duration;
            this.type = type;
        }

        public Double getPosition() { return position; }
        public void setPosition(Double position) { this.position = position; }

        public Double getDuration() { return duration; }
        public void setDuration(Double duration) { this.duration = duration; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
