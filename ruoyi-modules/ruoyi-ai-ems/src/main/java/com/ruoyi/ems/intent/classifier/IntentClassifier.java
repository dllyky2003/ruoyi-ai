package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;

/**
 * 意图分类器接口
 */
public interface IntentClassifier {
    /**
     * 对语音文本进行意图分类
     * @param asrText 语音识别后的文本
     * @return 分类结果
     */
    default IntentCommand classify(String asrText) {
        return classify(asrText, null);
    }
    /**
     * 获取分类器模式名称
     */
    String getMode();

    IntentCommand classify(String asrText, ClassificationContext context);

    void warmUp();
}
