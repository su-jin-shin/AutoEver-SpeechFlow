package com.demo;

import com.demo.grpc.GrpcServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SttGrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SttGrpcServerApplication.class, args);
	}

	@Bean
	public CommandLineRunner startGrpcServer(GrpcServer grpcServer) {
		return args -> grpcServer.start();
	}

}
