package com.ruoyi.ems.intent.classifier;


/**
 * 语用分类器抽象：便于单测 Mock 与未来替换实现（规则/模型混合）。
 */
public interface PragmaticClassifier {
    ActTypePrediction predict(String rawText);
}
