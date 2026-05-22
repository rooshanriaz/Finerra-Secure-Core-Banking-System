package com.fyp.kyc.dto;

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
public class KycOnboardRequest {

    @NotBlank(message = "CNIC number is required")
    @Pattern(regexp = "^\\d{5}-\\d{7}-\\d{1}$", message = "CNIC must be in format XXXXX-XXXXXXX-X")
    private String cnicNumber;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "DOB must be in YYYY-MM-DD format")
    private String dateOfBirth;

    private String fatherName;

    private String address;

    private String mobileNumber;

    private String email;

    // Fineract-specific fields
    private Integer officeId;
}
