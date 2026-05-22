package com.fyp.auth.service;

import com.fyp.auth.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordPolicyService {

    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$"
    );

    public static final String POLICY_MESSAGE =
            "Password must be 8-100 characters and include uppercase, lowercase, number, and special character";

    public void validateStrongPassword(String password) {
        if (password == null || !STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException(POLICY_MESSAGE);
        }
    }

    public boolean isStrongPassword(String password) {
        return password != null && STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }
}
