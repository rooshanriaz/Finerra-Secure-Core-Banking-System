package com.fyp.txn.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input sanitization utility for anti-injection protection.
 * Strips SQL injection and XSS patterns from string inputs.
 */
@Slf4j
@Component
public class InputSanitizer {

    // SQL injection patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(')|(--)|(;)|(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|EXEC|EXECUTE|TRUNCATE|DECLARE)\\b)",
        Pattern.CASE_INSENSITIVE
    );

    // XSS patterns
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(<script[^>]*>.*?</script>)|(<[^>]+on\\w+\\s*=)|(<iframe)|(<object)|(<embed)|" +
        "(javascript:)|(vbscript:)|(data:text/html)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // HTML tag pattern
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    /**
     * Sanitize a string input by removing dangerous patterns.
     * 
     * @param input The raw input string
     * @return Sanitized string, or null if input is null
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input.trim();

        // Check for SQL injection
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            log.warn("SQL injection pattern detected in input: {}", 
                sanitized.substring(0, Math.min(50, sanitized.length())));
            sanitized = SQL_INJECTION_PATTERN.matcher(sanitized).replaceAll("");
        }

        // Check for XSS
        if (XSS_PATTERN.matcher(sanitized).find()) {
            log.warn("XSS pattern detected in input");
            sanitized = XSS_PATTERN.matcher(sanitized).replaceAll("");
        }

        // Remove HTML tags
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll("");

        return sanitized.trim();
    }

    /**
     * Validate that input is safe (does not modify it).
     * 
     * @param input The input to validate
     * @return true if input contains no dangerous patterns
     */
    public boolean isSafe(String input) {
        if (input == null || input.isBlank()) {
            return true;
        }
        return !SQL_INJECTION_PATTERN.matcher(input).find()
            && !XSS_PATTERN.matcher(input).find();
    }
}
