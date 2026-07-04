package com.deployflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DeployflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeployflowApiApplication.class, args);
	}

}
