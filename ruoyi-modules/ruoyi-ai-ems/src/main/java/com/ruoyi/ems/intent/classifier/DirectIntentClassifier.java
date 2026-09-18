package com.ruoyi.ems.intent.classifier;

import com.ruoyi.ems.config.loader.CaseIntentDefinitionsLoader;
import com.ruoyi.ems.context.ClassificationContext;
import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.model.IntentDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 直接意图分类器：若输入文本为有效 intentCode，则直接命中该意图。
 * 优先级高于关键词分类器，建议放在分类链最前。
 */
@Slf4j
@Component
public class DirectIntentClassifier implements IntentClassifier {

    @Autowired
    private CaseIntentDefinitionsLoader loader;
    @Override
    public void warmUp()
    {

    }
    @Override
    public String getMode() {
        return "direct";
    }

    @Override
    public IntentCommand classify(String asrText, ClassificationContext context) {
        if (asrText == null || asrText.trim().isEmpty()) {
            return null;
        }

        String code = asrText.trim();
        if (loader.isValidIntentCode(code)) {
            IntentDefinition def = loader.getIntent(code);
            if (def != null) {

                // 使用与 KeywordIntentClassifier 一致的构造方式
                return IntentCommand.keywordHit(code, def.getStandardScript(), getMode());
            } else {
                log.warn("意图编码 {} 有效但定义缺失", code);
            }
        }
        return null; // 不匹配，交给后续分类器
    }
}
