package com.fyp.auth.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for TOTP (Time-based One-Time Password) operations.
 * Uses the dev.samstevens.totp library for RFC 6238 compliant TOTP.
 */
@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "Finnera Banking";
    private static final int SECRET_LENGTH = 32;

    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;

    public TotpService() {
        this.secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        ((DefaultCodeVerifier) this.codeVerifier).setAllowedTimePeriodDiscrepancy(1);
    }

    /**
     * Generate a new TOTP secret.
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Generate an otpauth:// URI for QR code display.
     */
    public String getQrCodeUri(String secret, String username) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /**
     * Verify a TOTP code against a secret.
     * Allows +/- 1 time period of drift.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.length() != 6) {
            return false;
        }
        try {
            return codeVerifier.isValidCode(secret, code);
        } catch (Exception e) {
            log.warn("TOTP verification error: {}", e.getMessage());
            return false;
        }
    }
}
