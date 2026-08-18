package org.ruoyi.workflow.workflow.node.ecgAction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ruoyi.common.core.exception.base.BaseException;
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
import java.util.List;

import static org.ruoyi.common.chat.enums.ErrorEnum.A_WF_NODE_CONFIG_ERROR;
import static org.ruoyi.common.chat.enums.ErrorEnum.A_WF_NODE_CONFIG_NOT_FOUND;
import static org.ruoyi.workflow.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

@Slf4j
public class EcgAction extends AbstractWfNode {

    public EcgAction(WorkflowComponent wfComponent, WorkflowNode nodeDef, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, nodeDef, wfState, nodeState);
    }

    @Override
    public NodeProcessResult onProcess() {
        String objectConfig = node.getNodeConfig();
        if (StringUtils.isBlank(objectConfig)) {
            throw new BaseException(A_WF_NODE_CONFIG_NOT_FOUND.getInfo());
        }

        EcgActionConfig config = JsonUtil.fromJson(objectConfig, EcgActionConfig.class);
        if (config == null) {
            log.warn("ECG Action 节点配置解析失败，nodeId={}", "NodeId123456");
            throw new BaseException(A_WF_NODE_CONFIG_ERROR.getInfo());
        }

        String resultName = StringUtils.defaultIfBlank("语音识别文本内容", "原始识别文本内容");
        String category = "意图类别";
        String time = java.time.Instant.now().toString();

        List<NodeIOData> outputs = new java.util.ArrayList<>();

        // 分别输出多个命名变量
        outputs.add(NodeIOData.createByText("resultName", "string", resultName));
        outputs.add(NodeIOData.createByText("Category", "string", category));
        outputs.add(NodeIOData.createByText("Time", "string", time));

        // 额外输出一个整体 JSON，方便下游节点直接使用
        com.fasterxml.jackson.databind.node.ObjectNode data = JsonUtil.createObjectNode();
        data.put("resultName", resultName);
        data.put("Category", category);
        data.put("Time", time);
        data.put("query", StringUtils.defaultString(config.getQuery()));

        outputs.add(NodeIOData.createByText(
            "data",
            "json",
            JsonUtil.toJson(data)
        ));

        return NodeProcessResult.builder().content(outputs).build();

    }

}
