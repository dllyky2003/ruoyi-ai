package com.ruoyi.ems.domain.dto;
import com.ruoyi.ems.domain.RuleDefinition;
import lombok.Data;
import java.util.List;

@Data
public class RuleConfig {
    private String caseId;
    private List<RuleDefinition> rules;
}
