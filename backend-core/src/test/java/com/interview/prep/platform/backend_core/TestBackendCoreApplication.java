package com.interview.prep.platform.backend_core;

import org.springframework.boot.SpringApplication;

public class TestBackendCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(InterviewPrepApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
