package com.fyp.txn.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotNull(message = "Transaction amount is required")
    @DecimalMin(value = "0.01", message = "Transaction amount must be greater than 0")
    private BigDecimal transactionAmount;

    @NotBlank(message = "Transaction date is required")
    @Pattern(regexp = "^\\d{2} \\w+ \\d{4}$", message = "Date format must be 'dd MMMM yyyy' (e.g., '09 February 2026')")
    private String transactionDate;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;

    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Payment type contains invalid characters")
    private String paymentTypeId;

    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Account number contains invalid characters")
    private String accountNumber;

    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Receipt number contains invalid characters")
    private String receiptNumber;

    private String locale;
    private String dateFormat;
}
