package com.ruoyi.ems.intent.model;

import lombok.Data;
import java.util.List;

@Data
public class SubDomain {
    private String subDomainName;
    private String domainCode;
    private String subDomainDescription;
    private List<IntentDefinition> intents;
    private String extractionHint;
}
