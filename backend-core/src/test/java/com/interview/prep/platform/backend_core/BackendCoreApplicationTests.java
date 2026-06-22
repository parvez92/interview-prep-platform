package com.interview.prep.platform.backend_core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
class BackendCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
