package com.demo.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SttGrpcServerProperties {

    @Value("${grpc.server.port}")
    private int port;

    public int getPort() {
        return port;
    }

}
