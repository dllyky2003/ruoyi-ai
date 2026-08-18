// com.ruoyi.websocket.model.WebSocketMessage
package org.ruoyi.websocket.model;

import cn.hutool.json.JSON;
import lombok.Getter;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.io.Serializable;



/**
 * WebSocket 消息模型
 *
 * @author ruoyi
 */

public class WebSocketMessage<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息类型 */
    private String type;

    /** 消息数据 */
    private T data;

    /** 消息ID */
    private String messageId;

    /** 时间戳 */
    private Long timestamp;

    /** 发送者ID */
    private String senderId;

    /** 主题ID */
    private String themeId;

    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public WebSocketMessage(String type, T data) {
        this();
        this.type = type;
        this.data = data;
    }

    public WebSocketMessage(String type, T data, String themeId) {
        this(type, data);
        this.themeId = themeId;
    }

    // =============================================
    // 静态工厂方法
    // =============================================

    public static WebSocketMessage<JSONObject> connect(String userId, String themeId) {
        JSONObject data = new JSONObject();
        try {
            data.put("userId", userId);
            data.put("themeId", themeId);
            data.put("message", "连接成功");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.CONNECT.getValue(), data, themeId);
    }

    public static WebSocketMessage<JSONObject> pong() {
        JSONObject data = new JSONObject();
        try {
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.PONG.getValue(), data);
    }

    public static WebSocketMessage<JSONObject> stateUpdate(String themeId, String stateId, String stateName) {
        JSONObject data = new JSONObject();
        try {
            data.put("themeId", themeId);
            data.put("stateId", stateId);
            data.put("stateName", stateName);
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.STATE_UPDATE.getValue(), data, themeId);
    }

    public static WebSocketMessage<JSONObject> eventTrigger(String themeId, String eventId, String eventName) {
        JSONObject data = new JSONObject();
        try {
            data.put("themeId", themeId);
            data.put("eventId", eventId);
            data.put("eventName", eventName);
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.EVENT_TRIGGER.getValue(), data, themeId);
    }

    public static WebSocketMessage<JSONObject> alarm(String themeId, String alarmType, String alarmMessage) {
        JSONObject data = new JSONObject();
        try {
            data.put("themeId", themeId);
            data.put("alarmType", alarmType);
            data.put("alarmMessage", alarmMessage);
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.ALARM.getValue(), data, themeId);
    }

    public static WebSocketMessage<JSONObject> heartRateUpdate(String themeId, Integer heartRate) {
        JSONObject data = new JSONObject();
        try {
            data.put("themeId", themeId);
            data.put("heartRate", heartRate);
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.HEART_RATE_UPDATE.getValue(), data, themeId);
    }

    public static WebSocketMessage<JSONObject> error(String message) {
        JSONObject data = new JSONObject();
        try {
            data.put("error", message);
            data.put("timestamp", System.currentTimeMillis());
            return new WebSocketMessage<>(MessageType.ERROR.getValue(), data);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static WebSocketMessage<JSONObject> ack(String message) {
        JSONObject data = new JSONObject();
        try {
            data.put("message", message);
            data.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return new WebSocketMessage<>(MessageType.ACK.getValue(), data);
    }
    public String toJson() {
        org.springframework.boot.configurationprocessor.json.JSONObject obj =
            new org.springframework.boot.configurationprocessor.json.JSONObject();
        try {
            obj.put("type", this.type);
            obj.put("data", this.data);
            obj.put("messageId", this.messageId);
            obj.put("timestamp", this.timestamp);
            obj.put("senderId", this.senderId);
            obj.put("themeId", this.themeId);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return obj.toString();
    }
    // =============================================
    // Getter / Setter
    // =============================================

    public void setType(String type) { this.type = type; }

    public void setData(T data) { this.data = data; }

    public void setMessageId(String messageId) { this.messageId = messageId; }

    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public void setSenderId(String senderId) { this.senderId = senderId; }

    public void setThemeId(String themeId) { this.themeId = themeId; }
}
