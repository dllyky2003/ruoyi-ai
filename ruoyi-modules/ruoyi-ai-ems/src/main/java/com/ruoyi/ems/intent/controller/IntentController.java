package com.ruoyi.ems.intent.controller;

import com.ruoyi.ems.domain.IntentCommand;
import com.ruoyi.ems.intent.service.IntentClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intent")
public class IntentController {

    @Autowired
    private IntentClassificationService intentService;

    /**
     * 语音文本意图分类接口
     * @param asrText 语音识别后的文本
     * @return 分类结果
     */
    @PostMapping("/classify")
    public IntentCommand classify(@RequestParam("text") String asrText) {
        return intentService.classify(asrText);
    }
}
