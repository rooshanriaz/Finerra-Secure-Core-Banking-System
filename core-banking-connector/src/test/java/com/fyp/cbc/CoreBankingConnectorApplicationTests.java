package com.fyp.cbc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests to verify Spring context loads correctly.
 */
@SpringBootTest
@ActiveProfiles("dev")
class CoreBankingConnectorApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context loads without errors
    }
}
