package com.fyp.auth.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Change password request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    private static final String STRONG_PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$";

    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    @Pattern(
            regexp = STRONG_PASSWORD_REGEX,
            message = "Password must include uppercase, lowercase, number, and special character"
    )
    private String newPassword;
}
