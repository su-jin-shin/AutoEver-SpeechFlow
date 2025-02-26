package com.demo.grpc;

import com.demo.solace.TopicPublisher;
import com.demo.websocket.AudioWebSocketServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import stt.SpeechToTextServiceGrpc;
import stt.SpeechResponse;
import stt.SpeechChunk;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SttGrpcClient {

    private final TopicPublisher topicPublisher;

    private static final int MAX_ATTEMPT_COUNT = 100;
    private final ManagedChannel channel;
    private final SpeechToTextServiceGrpc.SpeechToTextServiceStub asyncStub;
    private static int failCount;
    private static final ConcurrentHashMap<String, AtomicInteger> chunkCounters = new ConcurrentHashMap<>(); // 세션별 chunkId 관리

    @Autowired
    public SttGrpcClient(TopicPublisher topicPublisher) {
        this.topicPublisher = topicPublisher;
        // gRPC 서버 주소 설정 (localhost:50051)
        this.channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        this.asyncStub = SpeechToTextServiceGrpc.newStub(channel);
    }

    public void sendSingleAudioChunk(byte[] audioChunk, String customerId, String sessionId) throws InterruptedException {
        System.out.println("gRPC 서버로 음성 데이터 전송 시작...");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        chunkCounters.putIfAbsent(sessionId, new AtomicInteger(0)); // 새로운 세션이면 chunk 카운터 초기화

        StreamObserver<SpeechResponse> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(SpeechResponse response) {
                String sttText = response.getText();
                System.out.println("실시간 STT 변환 결과: " + sttText);

                if ("STT 변환 실패".equals(sttText)) {
                    System.out.println("⚠️ STT 변환 실패 → WebSocket 및 Solace 전송 생략");
                    return;
                }

                int currentChunkId = chunkCounters.get(sessionId).getAndIncrement(); //세션별로 chunkId 증가
                AudioWebSocketServer.sendToAllClients(sttText); // WebSocket을 통해 브라우저로 STT 데이터 전송

                final String topic = String.format("crm/stt/%s/%s/chunks/%d", customerId, sessionId, currentChunkId);
                System.out.println("Solace 비동기 전송 준비 - topic: " + topic + ", message: " + sttText);

                CompletableFuture.runAsync(() -> {
                    try {
                        topicPublisher.publishMessage(topic, sttText); // ✅ 지역 변수 활용
                        System.out.println("Solace 메시지 비동기 전송 완료");
                    } catch (Exception e) {
                        System.err.println("[ERROR] Solace 메시지 비동기 전송 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            }

            @Override
            public void onError(Throwable t) {
                failCount++; // 실패 횟수 증가
                System.err.println("[ERROR] gRPC 오류 발생 (" + failCount + "번째)" + t.getMessage());

                if (failCount > MAX_ATTEMPT_COUNT) {
                    System.err.println("[ERROR] 10번 초과 실패 - gRPC 연결 종료");
                    shutdown(); // gRPC 연결 종료
                }
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("음성 데이터 전송 완료");
                finishLatch.countDown();
            }
        };

        // 비동기 스트리밍 요청
        StreamObserver<SpeechChunk> requestObserver = asyncStub.streamSpeechToText(responseObserver);

        try {
            SpeechChunk speechChunk = SpeechChunk.newBuilder()
                    .setCustomerId(customerId)
                    .setAudioData(com.google.protobuf.ByteString.copyFrom(audioChunk))
                    .build();
            requestObserver.onNext(speechChunk);
        } catch (Exception e) {
            requestObserver.onError(e);
        }

        requestObserver.onCompleted();
        finishLatch.await(5, TimeUnit.SECONDS); // gRPC 응답 대기
    }

    public void shutdown() {
        System.out.println("gRPC 클라이언트 연결 종료 중...");
        channel.shutdown(); // gRPC 채널 종료
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("❌ 강제 종료 실행");
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            channel.shutdownNow();
        }
        System.out.println("gRPC 연결 종료 완료");
        // System.exit(1); // 애플리케이션 강제 종료
    }

}


