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
public class CnicVerificationRequest {

    @NotBlank(message = "CNIC number is required")
    @Pattern(regexp = "^\\d{5}-\\d{7}-\\d{1}$", message = "CNIC must be in format XXXXX-XXXXXXX-X")
    private String cnicNumber;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    private String fatherName;

    private String address;
}
