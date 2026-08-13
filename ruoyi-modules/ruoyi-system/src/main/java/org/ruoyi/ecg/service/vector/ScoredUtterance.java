package org.ruoyi.ecg.service.vector;

/**
 * 带分数的语料（内部使用）
 */
public class ScoredUtterance {
    private UtteranceVector utterance;
    private float score;

    public ScoredUtterance(UtteranceVector utterance, float score) {
        this.utterance = utterance;
        this.score = score;
    }

    public UtteranceVector getUtterance() {
        return utterance;
    }

    public void setUtterance(UtteranceVector utterance) {
        this.utterance = utterance;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }
}
