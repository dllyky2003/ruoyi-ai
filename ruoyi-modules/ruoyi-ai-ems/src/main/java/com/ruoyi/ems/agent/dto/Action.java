package com.ruoyi.ems.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Action {
    private String op;
    private Map<String, Object> params;
    public String toFormateString()
    {
        StringBuilder sb = new StringBuilder();
        if (params == null || params.isEmpty()) {
            sb.append("params: {}\n");
        } else {
            sb.append("params: {");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("}\n");
        }
        return sb.toString();
    }
}
