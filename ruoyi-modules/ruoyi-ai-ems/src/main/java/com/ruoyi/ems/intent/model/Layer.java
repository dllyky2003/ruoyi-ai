package com.ruoyi.ems.intent.model;

import lombok.Data;
import java.util.List;

@Data
public class Layer {
    private String layerCode;      // GENERAL / SPECIFIC
    private String layerName;
    private String layerDescription;
    private List<SubDomain> subDomains;
}
