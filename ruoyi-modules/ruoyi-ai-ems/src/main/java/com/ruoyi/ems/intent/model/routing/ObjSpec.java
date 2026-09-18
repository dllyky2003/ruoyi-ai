package com.ruoyi.ems.intent.model.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjSpec {
    private String type;   // 现场|患者|生理|病史|药物|设备|导联|流程|沟通|安全|治疗|转运|医院|文档|神经|气道|时间
    private String name;
}
