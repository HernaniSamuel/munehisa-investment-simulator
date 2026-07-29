package com.munehisa.backend;

import com.munehisa.backend.testsupport.SharedPostgresContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class BackendApplicationTests extends SharedPostgresContainer {

	@Test
	void contextLoads() {
	}

}
