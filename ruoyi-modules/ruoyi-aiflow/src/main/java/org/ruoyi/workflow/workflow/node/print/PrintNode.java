package org.ruoyi.workflow.workflow.node.print;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ruoyi.workflow.entity.WorkflowComponent;
import org.ruoyi.workflow.entity.WorkflowNode;
import org.ruoyi.workflow.util.JsonUtil;
import org.ruoyi.workflow.workflow.NodeProcessResult;
import org.ruoyi.workflow.workflow.WfNodeIODataUtil;
import org.ruoyi.workflow.workflow.WfNodeState;
import org.ruoyi.workflow.workflow.WfState;
import org.ruoyi.workflow.workflow.data.NodeIOData;
import org.ruoyi.workflow.workflow.node.AbstractWfNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PrintNode extends AbstractWfNode {

    public PrintNode(WorkflowComponent wfComponent,
                     WorkflowNode nodeDef,
                     WfState wfState,
                     WfNodeState nodeState) {
        super(wfComponent, nodeDef, wfState, nodeState);
    }

    @Override
    public NodeProcessResult onProcess() {
        PrintNodeConfig config = JsonUtil.fromJson(
            StringUtils.defaultIfBlank(node.getNodeConfig(), "{}"),
            PrintNodeConfig.class
        );

        String message = config == null
            ? "工作流调试日志"
            : StringUtils.defaultIfBlank(config.getMessage(), "工作流调试日志");

        List<NodeIOData> inputs = state.getInputs();
        if (inputs == null) {
            inputs = new ArrayList<>();
        }

        // 控制台逐项打印，排查时更清楚
        log.info("========== [{}] 开始 ==========", message);
        log.info("节点名称：{}", node.getTitle());
        log.info("节点 UUID：{}", node.getUuid());
        log.info("输入参数数量：{}", inputs.size());

        for (NodeIOData input : inputs) {
            log.info(
                "输入参数 => name: {}, content: {}",
                input.getName(),
                JsonUtil.toJson(input.getContent())
            );
        }

        log.info("完整输入参数 JSON：{}", JsonUtil.toJson(inputs));
        log.info("========== [{}] 结束 ==========", message);

        // 原样透传，后续节点仍然能够继续引用这些参数
        List<NodeIOData> outputs = new ArrayList<>(
            WfNodeIODataUtil.changeInputsToOutputs(inputs)
        );

        // 在运行记录前端额外提供一份总调试信息
        ObjectNode debugData = JsonUtil.createObjectNode();
        debugData.put("message", message);
        debugData.put("nodeUuid", node.getUuid());
        debugData.put("nodeTitle", node.getTitle());
        debugData.put("time", Instant.now().toString());
        debugData.put("inputs", JsonUtil.toJson(inputs));
        outputs.add(NodeIOData.createByText(
            "debug_data",
            "json",
            JsonUtil.toJson(debugData)
        ));

        return NodeProcessResult.builder()
            .content(outputs)
            .build();
    }
}
