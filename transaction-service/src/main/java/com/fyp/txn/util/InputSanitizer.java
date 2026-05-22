package com.fyp.txn.util;

import java.util.regex.Pattern;

/**
 * Utility class for input sanitization to prevent injection attacks.
 * Strips SQL injection, XSS, and other dangerous patterns.
 */
public final class InputSanitizer {

    private InputSanitizer() {}

    // SQL injection patterns
    private static final Pattern SQL_INJECTION = Pattern.compile(
        "(?i)(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|CREATE|EXEC|EXECUTE|XP_|SP_|0x)\\b|--|;|/\\*|\\*/|@@|\\bCHAR\\s*\\(|\\bCAST\\s*\\(|\\bCONVERT\\s*\\()",
        Pattern.CASE_INSENSITIVE
    );

    // XSS patterns
    private static final Pattern XSS_SCRIPT = Pattern.compile(
        "<\\s*script[^>]*>|</\\s*script\\s*>|javascript\\s*:|on\\w+\\s*=",
        Pattern.CASE_INSENSITIVE
    );

    // HTML tags
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

    /**
     * Sanitize a string input by removing dangerous patterns.
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        String result = input.trim();
        result = SQL_INJECTION.matcher(result).replaceAll("");
        result = XSS_SCRIPT.matcher(result).replaceAll("");
        result = HTML_TAGS.matcher(result).replaceAll("");
        return result;
    }

    /**
     * Check if input contains potentially dangerous patterns.
     * @return true if input is safe, false if it contains dangerous patterns
     */
    public static boolean isSafe(String input) {
        if (input == null) return true;
        return !SQL_INJECTION.matcher(input).find() && !XSS_SCRIPT.matcher(input).find();
    }

    /**
     * Sanitize a note/description field - allows basic text but strips code.
     */
    public static String sanitizeNote(String note) {
        if (note == null) return null;
        String result = note.trim();
        result = XSS_SCRIPT.matcher(result).replaceAll("");
        result = HTML_TAGS.matcher(result).replaceAll("");
        if (result.length() > 500) {
            result = result.substring(0, 500);
        }
        return result;
    }
}
