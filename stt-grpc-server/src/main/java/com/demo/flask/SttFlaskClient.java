package com.demo.flask;

import com.demo.grpc.GrpcServer;
import io.grpc.Server;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class SttFlaskClient {

    private static final String FLASK_SERVER_URL = "http://localhost:5000/stt"; // Flask STT 서버 주소
    private static int failCount;
    private static GrpcServer grpcServer;

    public static void setGrpcServer(GrpcServer server) {
        grpcServer = server;
    }
    
    public static String sendToFlask(byte[] audioData) {
        try {
            URL url = new URL(FLASK_SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setDoOutput(true);

            // 음성 데이터를 Flask 서버로 전송
            try (OutputStream outputStream = conn.getOutputStream()) {
                outputStream.write(audioData);
                outputStream.flush();
            }

            // Flask 서버의 응답을 읽기
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return reader.readLine(); // 변환된 텍스트 반환
                }
            } else {
                System.err.println("[ERROR] STT 서버 오류: 응답 코드 " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        failCount++;
        if (failCount > 1000 && grpcServer != null) {
            System.out.println("[ERROR] 1000번 초과 실패 - gRPC 서버 종료");
            grpcServer.stop(); // gRPC 서버 종료
        }

        return "STT 변환 실패";
    }
}

