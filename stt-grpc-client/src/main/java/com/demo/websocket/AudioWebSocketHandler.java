package com.demo.websocket;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.ArrayList;
import java.util.List;

public class AudioWebSocketHandler extends BinaryWebSocketHandler {
    private final List<byte[]> audioChunks = new ArrayList<>();

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        System.out.println("받은 음성 데이터 크기: " + message.getPayloadLength() + " bytes");
        audioChunks.add(message.getPayload().array());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WebSocket 연결 종료: " + session.getId());
    }
}

