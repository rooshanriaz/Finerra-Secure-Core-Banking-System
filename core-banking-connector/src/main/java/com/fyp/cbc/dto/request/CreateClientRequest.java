package com.fyp.cbc.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new client (customer onboarding).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateClientRequest {
    
    @NotNull(message = "Office ID is required")
    private Long officeId;
    
    @NotBlank(message = "First name is required")
    private String firstname;
    
    @NotBlank(message = "Last name is required")
    private String lastname;
    
    private String middlename;
    
    private String externalId;
    
    @NotBlank(message = "Date of birth is required")
    private String dateOfBirth;
    
    private String mobileNo;
    
    private String emailAddress;
    
    @Builder.Default
    private Boolean active = true;
    
    private String activationDate;
    
    private String submittedOnDate;
    
    private Long savingsProductId;
    
    private Long legalFormId;
    
    private Long genderId;
    
    private Long clientTypeId;
    
    private Long clientClassificationId;
    
    private AddressData address;
    
    @Builder.Default
    private String locale = "en";
    
    @Builder.Default
    private String dateFormat = "dd MMMM yyyy";
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressData {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String stateProvinceId;
        private String countryId;
        private String postalCode;
    }
}
