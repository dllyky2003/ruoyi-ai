// com.ruoyi.websocket.utils.WebSocketUtils
package org.ruoyi.ecg.websocket.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ruoyi.ecg.websocket.model.WebSocketMessage;
import org.ruoyi.ecg.websocket.server.WebSocketSessionManager;
import org.springframework.boot.configurationprocessor.json.JSONObject;

/**
 * WebSocket 工具类
 *
 * @author ruoyi
 */
public class WebSocketUtils {

    private static final Logger log = LoggerFactory.getLogger(WebSocketUtils.class);

    public static void sendStateUpdate(String themeId, String stateId, String stateName) {
        WebSocketMessage<JSONObject> message = WebSocketMessage.stateUpdate(themeId, stateId, stateName);
        int count = WebSocketSessionManager.sendToTheme(themeId, message);
        log.info("【WebSocket】状态更新推送：主题={}，状态={}，接收数={}", themeId, stateName, count);
    }

    public static void sendEventTrigger(String themeId, String eventId, String eventName) {
        WebSocketMessage<JSONObject> message = WebSocketMessage.eventTrigger(themeId, eventId, eventName);
        int count = WebSocketSessionManager.sendToTheme(themeId, message);
        log.info("【WebSocket】事件触发推送：主题={}，事件={}，接收数={}", themeId, eventName, count);
    }

    public static void sendAlarm(String themeId, String alarmType, String alarmMessage) {
        WebSocketMessage<JSONObject> message = WebSocketMessage.alarm(themeId, alarmType, alarmMessage);
        int count = WebSocketSessionManager.sendToTheme(themeId, message);
        log.info("【WebSocket】报警推送：主题={}，类型={}，接收数={}", themeId, alarmType, count);
    }

    public static void sendHeartRateUpdate(String themeId, Integer heartRate) {
        WebSocketMessage<JSONObject> message = WebSocketMessage.heartRateUpdate(themeId, heartRate);
        int count = WebSocketSessionManager.sendToTheme(themeId, message);
        log.info("【WebSocket】心率推送：主题={}，心率={}，接收数={}", themeId, heartRate, count);
    }

    public static boolean sendToUser(String userId, WebSocketMessage<?> message) {
        return WebSocketSessionManager.sendToUser(userId, message);
    }

    public static int sendToTheme(String themeId, WebSocketMessage<?> message) {
        return WebSocketSessionManager.sendToTheme(themeId, message);
    }

    public static int broadcast(WebSocketMessage<?> message) {
        return WebSocketSessionManager.broadcast(message);
    }

    public static JSONObject getStatus() {
        return WebSocketSessionManager.getStatus();
    }

    public static int getOnlineCount() {
        return WebSocketSessionManager.getOnlineCount();
    }

    public static boolean isOnline(String userId) {
        return WebSocketSessionManager.isOnline(userId);
    }
}
