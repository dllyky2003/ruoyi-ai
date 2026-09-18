package com.ruoyi.ems.intent.parser;

import com.ruoyi.ems.intent.model.ExtractionContext;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class WpwParser {
    public Map<String, Object> parse(ExtractionContext ctx) {
        Map<String, Object> result = new HashMap<>();
        result.put("isWpw", true);
        result.put("contraindicatedDrugs", Arrays.asList("adenosine", "ccb", "beta_blocker", "digoxin"));
        return result;
    }
}
