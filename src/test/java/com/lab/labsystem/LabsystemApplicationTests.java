package com.lab.labsystem;

import org.junit.jupiter.api.Tag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class LabsystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
