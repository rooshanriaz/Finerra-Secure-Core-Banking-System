package com.fyp.cbc.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding a client identifier (e.g., CNIC for KYC).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientIdentifierRequest {
    
    @NotNull(message = "Document type ID is required")
    private Long documentTypeId;
    
    @NotBlank(message = "Document key is required")
    private String documentKey;
    
    private String description;
    
    private String status;
}
