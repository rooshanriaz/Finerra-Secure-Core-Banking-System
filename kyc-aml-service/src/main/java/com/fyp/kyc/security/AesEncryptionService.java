package com.fyp.kyc.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for PII (Personally Identifiable Information).
 * 
 * Encrypts sensitive data like CNIC, DOB, address before database storage.
 * Uses AES/GCM/NoPadding for authenticated encryption (provides both
 * confidentiality and integrity).
 */
@Slf4j
@Service
public class AesEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits
    private static final int GCM_TAG_LENGTH = 128;  // 128 bits
    
    private final SecretKey secretKey;

    public AesEncryptionService(@Value("${encryption.aes.secret-key}") String secretKeyStr) {
        this.secretKey = deriveKey(secretKeyStr);
        log.info("AES-256-GCM encryption service initialized");
    }

    /**
     * Encrypt a plaintext string using AES-256-GCM.
     * The IV is prepended to the ciphertext for storage.
     * 
     * @param plaintext The data to encrypt
     * @return Base64-encoded ciphertext (IV + encrypted data + auth tag)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data for storage
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt an AES-256-GCM encrypted string.
     * 
     * @param ciphertext Base64-encoded ciphertext (IV + encrypted data + auth tag)
     * @return Decrypted plaintext
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            
            // Extract IV from the beginning
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * Generate a SHA-256 hash of the input (for lookups without decryption).
     * 
     * @param input The data to hash
     * @return Hex-encoded SHA-256 hash
     */
    public String hash(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.replaceAll("-", "").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash data", e);
        }
    }

    /**
     * Derive a 256-bit AES key from a passphrase using SHA-256.
     */
    private SecretKey deriveKey(String passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            // Use first 32 bytes (256 bits) for AES-256
            return new SecretKeySpec(Arrays.copyOf(keyBytes, 32), ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive encryption key", e);
        }
    }
}
