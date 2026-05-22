package com.fyp.cbc.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fyp.cbc.config.FineractLocalDateDeserializer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for client/customer data from Apache Fineract.
 *
 * Fineract returns dates as integer arrays [year, month, day].  The custom
 * {@link FineractLocalDateDeserializer} handles both array and string forms.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientResponse {

    private Long id;
    private Long clientId;
    private Long resourceId;
    private String accountNo;
    private String externalId;
    private StatusData status;
    private Boolean active;

    @JsonDeserialize(using = FineractLocalDateDeserializer.class)
    private LocalDate activationDate;

    private String firstname;
    private String middlename;
    private String lastname;
    private String displayName;
    private String fullname;

    @JsonDeserialize(using = FineractLocalDateDeserializer.class)
    private LocalDate dateOfBirth;

    private String mobileNo;
    private String emailAddress;
    private Long officeId;
    private String officeName;
    private Long staffId;
    private String staffName;
    private TimelineData timeline;
    private Long savingsProductId;
    private String savingsProductName;
    private Long savingsAccountId;
    private GenderData gender;
    private ClientTypeData clientType;
    private ClientClassificationData clientClassification;
    private List<GroupData> groups;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusData {
        private Long id;
        private String code;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimelineData {
        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate submittedOnDate;
        private String submittedByUsername;
        private String submittedByFirstname;
        private String submittedByLastname;

        @JsonDeserialize(using = FineractLocalDateDeserializer.class)
        private LocalDate activatedOnDate;
        private String activatedByUsername;
        private String activatedByFirstname;
        private String activatedByLastname;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenderData {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientTypeData {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientClassificationData {
        private Long id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroupData {
        private Long id;
        private String name;
    }
}
