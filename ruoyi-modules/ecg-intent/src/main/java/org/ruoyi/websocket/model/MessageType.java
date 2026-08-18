// com.ruoyi.websocket.model.MessageType
package org.ruoyi.websocket.model;

/**
 * WebSocket 消息类型枚举
 *
 * @author ruoyi
 */
public enum MessageType {

    /** 连接 */
    CONNECT("connect"),

    /** 断开连接 */
    DISCONNECT("disconnect"),

    /** 心跳请求 */
    PING("ping"),

    /** 心跳响应 */
    PONG("pong"),

    /** 状态更新 */
    STATE_UPDATE("stateUpdate"),

    /** 事件触发 */
    EVENT_TRIGGER("eventTrigger"),

    /** 报警 */
    ALARM("alarm"),

    /** 心率更新 */
    HEART_RATE_UPDATE("heartRateUpdate"),

    /** 错误 */
    ERROR("error"),

    /** 确认 */
    ACK("ack");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromValue(String value) {
        for (MessageType type : MessageType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
