package com.florez.fraud_detection;

import org.springframework.boot.SpringApplication;

public class TestFraudDetectionApplication {

	public static void main(String[] args) {
		SpringApplication.from(FraudDetectionApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
