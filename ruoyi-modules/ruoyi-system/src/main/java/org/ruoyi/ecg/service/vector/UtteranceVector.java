package org.ruoyi.ecg.service.vector;

import org.ruoyi.ecg.domain.intent.EcgUtterancePattern;

/**
 * 语料向量（内部使用）
 */
public class UtteranceVector {
    private EcgUtterancePattern utterance;
    private float[] vector;

    public UtteranceVector(EcgUtterancePattern utterance, float[] vector) {
        this.utterance = utterance;
        this.vector = vector;
    }

    public EcgUtterancePattern getUtterance() {
        return utterance;
    }

    public void setUtterance(EcgUtterancePattern utterance) {
        this.utterance = utterance;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }
}
