package com.fyp.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Basic tests for Auth Service.
 * Full integration tests require MySQL/Redis to be running.
 */
class AuthServiceApplicationTests {

    @Test
    void applicationClassExists() {
        // Verify the main application class exists
        assertDoesNotThrow(() -> Class.forName("com.fyp.auth.AuthServiceApplication"));
    }

    @Test
    void mainMethodExists() {
        // Verify main method exists (doesn't run it)
        assertDoesNotThrow(() -> 
            AuthServiceApplication.class.getDeclaredMethod("main", String[].class));
    }
}
