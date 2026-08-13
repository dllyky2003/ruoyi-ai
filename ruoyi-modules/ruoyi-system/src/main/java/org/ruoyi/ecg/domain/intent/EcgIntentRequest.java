package org.ruoyi.ecg.domain.intent;

import java.io.Serializable;
import java.util.List;

/**
 * 意图新增/修改请求对象
 */
public class EcgIntentRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private EcgIntentDefinition intent;
    private List<EcgIntentSlotBinding> slotBindings;
    private List<EcgUtterancePattern> utterances;

    public EcgIntentDefinition getIntent() { return intent; }
    public void setIntent(EcgIntentDefinition intent) { this.intent = intent; }
    public List<EcgIntentSlotBinding> getSlotBindings() { return slotBindings; }
    public void setSlotBindings(List<EcgIntentSlotBinding> slotBindings) { this.slotBindings = slotBindings; }
    public List<EcgUtterancePattern> getUtterances() { return utterances; }
    public void setUtterances(List<EcgUtterancePattern> utterances) { this.utterances = utterances; }
}
