package com.watchpost.manager.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送 WebSocket 处理器 (PRD: 大盘/详情页实时指标与告警推送)。
 * 客户端连接 /ws/realtime, 服务端广播采集结果与告警事件。
 * v1 单机: 内存会话表; 集群期可替换为 Redis Pub/Sub 广播 (SPI 预留)。
 */
@Slf4j
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("ws connected: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("ws closed: {} (total={})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可发送订阅/心跳; v1 简单回显 pong
        if ("ping".equals(message.getPayload())) {
            try { session.sendMessage(new TextMessage("pong")); } catch (IOException ignored) {}
        }
    }

    /** 广播一条实时消息 (采集结果/告警事件)。 */
    public void broadcast(String type, Object payload) {
        if (sessions.isEmpty()) return;
        try {
            String json = mapper.writeValueAsString(Map.of("type", type, "data", payload,
                    "ts", System.currentTimeMillis()));
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try { s.sendMessage(msg); }
                    catch (IOException e) { log.debug("ws send failed: {}", e.getMessage()); }
                }
            }
        } catch (Exception e) {
            log.warn("ws broadcast serialize failed: {}", e.getMessage());
        }
    }

    public int onlineCount() {
        return sessions.size();
    }
}
