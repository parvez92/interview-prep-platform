package com.interview.prep.platform.backend_core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
