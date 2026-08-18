package org.ruoyi.workflow.workflow.node.print;

import lombok.Data;

@Data
public class PrintNodeConfig {

    /**
     * 日志标识，用于区分不同调试节点。
     */
    private String message = "工作流调试日志";
}
