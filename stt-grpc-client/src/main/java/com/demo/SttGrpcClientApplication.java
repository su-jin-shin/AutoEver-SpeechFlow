package com.demo;

import com.demo.solace.TopicPublisher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
public class SttGrpcClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(SttGrpcClientApplication.class, args);
	}

}
