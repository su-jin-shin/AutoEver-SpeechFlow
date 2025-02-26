package com.demo.websocket;

import com.demo.grpc.SttGrpcClient;
import com.demo.solace.TopicPublisher;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ServerEndpoint("/audio-stream")
@Component
public class AudioWebSocketServer {

    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
    private static final Map<String, String> userSessions = new ConcurrentHashMap<>();
    private static SttGrpcClient grpcClient; // static으로 변경하여 주입 가능하도록 설정
    private static TopicPublisher topicPublisher;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10); // 최대 10개까지 동시에 실행 가능한 스레드 풀 추가

    @Autowired
    public void setGrpcClient(SttGrpcClient grpcClient) { // ✅ Setter 메서드로 Spring Bean을 주입
        AudioWebSocketServer.grpcClient = grpcClient;
    }

    @Autowired
    public void setTopicPublisher(TopicPublisher topicPublisher) {
        AudioWebSocketServer.topicPublisher = topicPublisher;
    }

    private String formatDate(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    @OnOpen
    public void onOpen(Session session) {
        Date date = new Date(); //웹소켓 세션 시작 시간
        session.getUserProperties().put("sessionStartTime", formatDate(date, "yyyyMMddHHmmssSSS"));
        sessions.add(session);
        System.out.printf("🔗 %s에 WebSocket 연결됨%n", formatDate(date, "yyyy년 M월 d일 HH시 mm분 ss초.SSS"));
    }

    private String makeSessionId(Session session, String customerId) {
        String randomPart = UUID.randomUUID().toString().substring(0, 4);
        String sessionId = session.getUserProperties().get("sessionStartTime") + "_" + customerId + "_" + randomPart;
        session.getUserProperties().put("sessionId", sessionId);
        userSessions.put(sessionId, customerId);
        return sessionId;
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            // ✅ 고객 ID 등록 (첫 메시지)
            if (json.has("type") && "auth".equals(json.get("type").getAsString())) {
                String customerId = json.get("customerId").getAsString();
                System.out.println("✅ 고객 ID 등록 완료: " + customerId + " (Session ID: " + makeSessionId(session, customerId) + ")");
                return;
            }

            // ✅ 녹음 종료 메시지 처리 (비동기 실행)
            if (json.has("type") && "stop".equals(json.get("type").getAsString())) {
                String sessionId = (String) session.getUserProperties().get("sessionId");
                executorService.submit(() -> {
                    String customerId = userSessions.get(sessionId);
                    if (customerId != null) {
                        System.out.println("🛑 녹음 종료 요청 수신! 고객 ID: " + customerId + " & 세션 ID: " + sessionId);
                        topicPublisher.publishMessage("crm/stt/" + customerId + "/" + sessionId + "/stop", "녹음 종료");
                    } else {
                        System.out.println("⚠️ 고객 ID 없음. 녹음 종료 메시지 전송 불가.");
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("❌ JSON 메시지 처리 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(byte[] data, Session session) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        executorService.submit(() -> {
            String customerId = userSessions.get(sessionId);
            if (customerId == null) {
                System.out.println("⚠️ 고객 ID 없음. 데이터를 처리할 수 없습니다. (Session ID: " + sessionId + ")");
                return;
            }

            System.out.println("🎤 WebSocket에서 받은 음성 데이터 크기: " + data.length + " bytes (고객 ID: " + customerId + ")");

            try {
                grpcClient.sendSingleAudioChunk(data, customerId, sessionId);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    @OnClose
    public void onClose(Session session) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        String customerId = userSessions.remove(sessionId);
        System.out.println("❌ WebSocket 연결 종료: " + sessionId + " (고객 ID: " + customerId + ")");
    }

    // 서버 종료 시 실행
    public static void shutdown() {
        executorService.shutdown();
        System.out.println("🛑 WebSocket 서버 종료됨 (스레드 풀 정리)");
    }

    // STT 결과를 브라우저로 전송하는 메서드
    public static void sendToAllClients(String message) {
        if ("STT 변환 실패".equals(message)) {
            System.out.println("⚠️ STT 변환 실패 → 브라우저 전송 생략");
            return; // 브라우저에 전송하지 않음 (blank 처리)
        }

        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
