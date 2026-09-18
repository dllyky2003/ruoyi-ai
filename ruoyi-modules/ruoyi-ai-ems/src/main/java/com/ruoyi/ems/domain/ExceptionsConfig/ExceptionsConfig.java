package com.ruoyi.ems.domain.ExceptionsConfig;

import com.ruoyi.ems.domain.ExceptionRule.ExceptionRule;
import lombok.Data;
import java.util.List;

@Data
public class ExceptionsConfig {
    private List<ExceptionRule> exceptions;
}
