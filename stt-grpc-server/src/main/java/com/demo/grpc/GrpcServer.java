package com.demo.grpc;

import com.demo.config.SttGrpcServerProperties;
import com.demo.flask.SttFlaskClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.grpc.stub.StreamObserver;

import stt.SpeechToTextServiceGrpc;
import stt.SpeechResponse;
import stt.SpeechChunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GrpcServer {
    @Autowired
    private SttGrpcServerProperties properties;

    private Server server;

    public void start() throws IOException, InterruptedException {

        server = ServerBuilder.forPort(properties.getPort())
                .addService(new SpeechToTextServiceImpl())
                .build()
                .start();

        // gRPC 서버 인스턴스를 SttFlaskClient에 설정
        SttFlaskClient.setGrpcServer(this);

        System.out.println("▷ gRPC 서버 시작됨 (포트 " + properties.getPort() + ")");
        server.awaitTermination();

    }

    public void stop() {
        if (server != null) {
            System.out.println("▷ gRPC 서버 종료 중...");
            server.shutdown();
            System.out.println("▷ gRPC 서버 종료 완료");
        }
    }

    static class SpeechToTextServiceImpl extends SpeechToTextServiceGrpc.SpeechToTextServiceImplBase {

        @Override
        public StreamObserver<SpeechChunk> streamSpeechToText(StreamObserver<SpeechResponse> responseObserver) {
            return new StreamObserver<>() {
                private final List<byte[]> audioChunks = new ArrayList<>();
                private String customerId;

                @Override
                public void onNext(SpeechChunk speechChunk) {
                    if (customerId == null) {
                        customerId = speechChunk.getCustomerId();
                    }
                    audioChunks.add(speechChunk.getAudioData().toByteArray());
                    System.out.println("gRPC 서버에서 받은 음성 데이터 크기: " + speechChunk.getAudioData().size() + " bytes");
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("[ERROR] 오류 발생: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.out.println("모든 음성 데이터 수신 완료. STT 변환 시작...");

                    // 음성 데이터를 하나로 합치기
                    byte[] fullAudio = mergeAudioChunks(audioChunks);

                    // 🔥 Flask 서버에 HTTP 요청을 보내 STT 변환
                    String text = SttFlaskClient.sendToFlask(fullAudio);

                    // 변환된 텍스트 응답
                    SpeechResponse response = SpeechResponse.newBuilder()
                            .setText(text)
                            .setSuccess(true)
                            .build();

                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    System.out.println("변환된 텍스트 응답 전송 완료: " + text);
                }
            };
        }

        private byte[] mergeAudioChunks(List<byte[]> audioChunks) {
            int totalSize = audioChunks.stream().mapToInt(chunk -> chunk.length).sum();
            byte[] mergedAudio = new byte[totalSize];

            int index = 0;
             for (byte[] chunk : audioChunks) {
                System.arraycopy(chunk, 0, mergedAudio, index, chunk.length);
                index += chunk.length;
            }
            return mergedAudio;
        }
    }
}

