package org.ruoyi.ecg.domain;

import java.io.Serializable;

/**
 * 运行时状态统计
 *
 * @author ecg-system
 */
public class EcgRuntimeStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean loaded;
    private int intentCount;
    private int slotCount;
    private int contextCount;
    private int utteranceCount;

    // ====== Getter / Setter ======
    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public int getIntentCount() {
        return intentCount;
    }

    public void setIntentCount(int intentCount) {
        this.intentCount = intentCount;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    public int getContextCount() {
        return contextCount;
    }

    public void setContextCount(int contextCount) {
        this.contextCount = contextCount;
    }

    public int getUtteranceCount() {
        return utteranceCount;
    }

    public void setUtteranceCount(int utteranceCount) {
        this.utteranceCount = utteranceCount;
    }
}
