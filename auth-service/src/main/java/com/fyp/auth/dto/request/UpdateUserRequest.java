package com.fyp.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update user request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Size(max = 100, message = "First name must be max 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be max 100 characters")
    private String lastName;
}
