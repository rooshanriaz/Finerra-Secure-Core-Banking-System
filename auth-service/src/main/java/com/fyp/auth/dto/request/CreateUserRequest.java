package com.fyp.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Create user request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private static final String STRONG_PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$";

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be 3-100 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
    @Pattern(
            regexp = STRONG_PASSWORD_REGEX,
            message = "Password must include uppercase, lowercase, number, and special character"
    )
    private String password;

    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 100, message = "First name must be max 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be max 100 characters")
    private String lastName;

    /**
     * Role names to assign to the user.
     */
    private Set<String> roleNames;
}
