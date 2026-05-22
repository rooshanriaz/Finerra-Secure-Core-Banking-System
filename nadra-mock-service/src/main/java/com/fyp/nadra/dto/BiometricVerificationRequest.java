package com.fyp.nadra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricVerificationRequest {

    @NotBlank(message = "CNIC number is required")
    @Pattern(regexp = "^\\d{5}-\\d{7}-\\d{1}$", message = "CNIC must be in format XXXXX-XXXXXXX-X")
    private String cnicNumber;

    @NotBlank(message = "Biometric data is required")
    private String biometricData; // Base64-encoded fingerprint/face data (simulated)

    private String biometricType; // FINGERPRINT or FACE
}
