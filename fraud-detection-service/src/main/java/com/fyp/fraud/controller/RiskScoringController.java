package com.fyp.fraud.controller;

import com.fyp.fraud.dto.ApiResponse;
import com.fyp.fraud.dto.RiskScoringRequest;
import com.fyp.fraud.dto.RiskScoringResponse;
import com.fyp.fraud.service.RiskScoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/fraud")
@RequiredArgsConstructor
public class RiskScoringController {

    private final RiskScoringService riskScoringService;

    @PostMapping("/score")
    public ResponseEntity<ApiResponse<RiskScoringResponse>> scoreTransaction(
            @Valid @RequestBody RiskScoringRequest request) {

        log.info("Risk scoring request: txnId={}, type={}, amount={}",
            request.getTransactionId(), request.getTransactionType(), request.getAmount());

        RiskScoringResponse response = riskScoringService.scoreTransaction(request);
        return ResponseEntity.ok(ApiResponse.success("Risk scoring completed", response));
    }
}
