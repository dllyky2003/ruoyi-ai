package org.ruoyi.ecg.models;

public class ScoredNode {
    private String nodeId;
    private String text;
    private float score;

    // 构造方法
    public ScoredNode() {}

    public ScoredNode(String nodeId, String text, float score) {
        this.nodeId = nodeId;
        this.text = text;
        this.score = score;
    }

    // Getter 和 Setter 方法
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }

    @Override
    public String toString() {
        return String.format("ScoredNode{nodeId='%s', text='%s', score=%.4f}", nodeId, text, score);
    }
}
