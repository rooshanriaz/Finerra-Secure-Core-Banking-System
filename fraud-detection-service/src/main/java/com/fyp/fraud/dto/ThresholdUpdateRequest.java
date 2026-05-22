package com.fyp.fraud.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdUpdateRequest {

    @NotNull
    private String thresholdName;

    @NotNull
    @DecimalMin("0.0")
    /** Risk scores use 0–1; PKR amount / velocity / geo thresholds can be larger. */
    @DecimalMax("1000000000.0")
    private Double value;

    private String description;
}
