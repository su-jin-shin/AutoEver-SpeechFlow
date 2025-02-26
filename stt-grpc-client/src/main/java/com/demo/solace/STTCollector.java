package com.demo.solace;

import com.solacesystems.jcsmp.*;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class STTCollector {
    private final SpringJCSMPFactory solaceFactory;
    private final TopicPublisher topicPublisher;
    private static final ConcurrentHashMap<String, TreeMap<Integer, String>> sessionBuffers = new ConcurrentHashMap<>();

    private JCSMPSession session;
    private XMLMessageConsumer consumer;

    public STTCollector(SpringJCSMPFactory solaceFactory, TopicPublisher topicPublisher) throws JCSMPException {
        this.solaceFactory = solaceFactory;
        this.topicPublisher = topicPublisher;
        startSTTStopSubscription();
    }

    private JCSMPSession getSession() throws JCSMPException {
        if (session == null || session.isClosed()) {
            session = solaceFactory.createSession();
            session.connect();
        }
        return session;
    }

    private void startSTTChunkSubscription(String topicName) {
        new Thread(() -> {
            try {
                session = getSession(); //세션 생성
                subscribeToSTTChunks(topicName); //구독 시작

                while (true) {
                    Thread.sleep(5000);

                    if (session.isClosed()) {
                        System.out.println("[ERROR] 세션이 닫혀 있습니다! 다시 연결 시도...");
                        session = getSession();
                        subscribeToSTTStop();
                    }
                }
            } catch (JCSMPException | InterruptedException e) {
                System.err.println("[ERROR] Solace STT 조각 구독 실패: " + e.getMessage());
            }
        }).start();
    }

    private void startSTTStopSubscription() {
        new Thread(() -> {
            try {
                session = getSession(); //세션 생성
                subscribeToSTTStop(); //구독 시작

                while (true) {
                    Thread.sleep(5000);

                    if (session.isClosed()) {
                        System.out.println("[ERROR] 세션이 닫혀 있습니다! 다시 연결 시도...");
                        session = getSession();
                        subscribeToSTTStop();
                    }
                }
            } catch (JCSMPException | InterruptedException e) {
                System.err.println("[ERROR] Solace STT 종료 구독 실패: " + e.getMessage());
            }
        }).start();
    }

    //STT 조각 수신 및 저장
    public void subscribeToSTTChunks(String receivedTopicName) throws JCSMPException {
        String[] topicParts = receivedTopicName.split("/");
        String topicName = String.format("crm/stt/%s/%s/chunks/*", topicParts[2], topicParts[3]);
        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);

        // 기존 consumer가 실행 중이면 중지
        if (consumer != null) {
            consumer.close();
        }

        consumer = session.getMessageConsumer(new XMLMessageListener() {
            public void onReceive(BytesXMLMessage msg) {
                if (msg instanceof TextMessage) {
                    String receivedText = ((TextMessage) msg).getText();
                    System.out.println("STT 조각 수신: " + receivedText);
                    //processSTTChunk(receivedText);
                }
            }

            @Override
            public void onException(JCSMPException e) {
                System.err.println("[ERROR] Solace STT 조각 수신 오류: " + e.getMessage());
            }
        });

        //session.addSubscription(topic);
        consumer.start(); // 전역 변수로 유지되는 consumer 실행
        System.out.println("STT 조각 구독 시작: " + topicName);
    }

    private void processSTTChunk(String receivedText) {
        try {
            JSONObject json = new JSONObject(receivedText);
            String customerId = json.getString("customerId");
            String sessionId = json.getString("sessionId");
            int chunkId = json.getInt("chunkId");
            String text = json.getString("text");

            String key = customerId + "/" + sessionId;
            sessionBuffers.putIfAbsent(key, new TreeMap<>());
            sessionBuffers.get(key).put(chunkId, text);

            // DB에도 STT 조각 저장
            // saveSTTChunkToDB(customerId, sessionId, chunkId, text);

        } catch (Exception e) {
            System.err.println("[ERROR] JSON 파싱 오류: " + e.getMessage());
        }
    }

    // STT 종료 메시지 구독을 실행하는 메서드
    public void subscribeToSTTStop() throws JCSMPException {
        String topicName = "crm/stt/*/*/stop";
        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);

        consumer = session.getMessageConsumer(new XMLMessageListener() {
            public void onReceive(BytesXMLMessage msg) {
                System.out.println("메시지 핸들러 실행됨");
                if (msg instanceof TextMessage) {
                    //String receivedText = ((TextMessage) msg).getText();
                    String receivedTopicName = msg.getDestination().getName();
                    System.out.println("녹음 종료 메시지 수신: " + receivedTopicName);
                    //processFinalSTT();
                    startSTTChunkSubscription(receivedTopicName);
                }
            }

            @Override
            public void onException(JCSMPException e) {
                System.err.println("[ERROR] Solace STT 종료 메시지 수신 오류: " + e.getMessage());
            }
        });

        session.addSubscription(topic);
        consumer.start();
        System.out.println("Solace STT 녹음 종료 구독 시작...");
    }

    private void processFinalSTT(String receivedText) {
        try {
            JSONObject json = new JSONObject(receivedText);
            String customerId = json.getString("customerId");
            String sessionId = json.getString("sessionId");

            String key = customerId + "/" + sessionId;
            if (!sessionBuffers.containsKey(key)) {
                System.out.println("⚠️ 저장된 STT 조각 없음.");
                return;
            }

            TreeMap<Integer, String> chunks = sessionBuffers.get(key);
            String fullConversation = String.join(" ", chunks.values());

            System.out.println("최종 STT 대화: " + fullConversation);

            // Solace에 최종 대화 저장
            sendFullConversationToSolace(customerId, sessionId, fullConversation);

            // TA 실행 (상담 요약)
            String summarizedText = callTAEngine(fullConversation);
            sendTASummaryToSolace(customerId, sessionId, summarizedText);

            // 메모리 정리
            sessionBuffers.remove(key);

        } catch (Exception e) {
            System.err.println("[ERROR] STT 조각 합치기 오류: " + e.getMessage());
        }
    }

    private void saveSTTChunkToDB(String customerId, String sessionId, int messageNumber, String text) {
        // STT 조각을 DB에 저장하는 코드(구현하기)
        System.out.println("DB에 STT 조각 저장: [" + messageNumber + "] " + text);
    }

    private void sendFullConversationToSolace(String customerId, String sessionId, String fullConversation) {
        System.out.println("Solace로 최종 대화 전송 완료: " + fullConversation);
        topicPublisher.publishMessage("crm/stt/" + customerId + "/" + sessionId + "/full", fullConversation);
    }

    private void sendTASummaryToSolace(String customerId, String sessionId, String summarizedText) {
        System.out.println("Solace로 상담 요약 전송 완료: " + summarizedText);
        topicPublisher.publishMessage("crm/summary/" + customerId + "/" + sessionId, summarizedText);
    }

    private String callTAEngine(String fullConversation) {
        System.out.println("TA 엔진 실행 중...");
        return "요약된 상담 내용 (예시)";
    }

}