package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.EvaluationRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.EvaluationResponse;
import com.company.company_clean_hub_be.dto.response.EvaluationDetailResponse;
import com.company.company_clean_hub_be.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER_GENERAL_1', 'MANAGER_GENERAL_2', 'ADMIN')")
    public ResponseEntity<ApiResponse<EvaluationResponse>> evaluate(
            @Valid @RequestBody EvaluationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        EvaluationResponse response = evaluationService.evaluate(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.<EvaluationResponse>builder()
                .success(true)
                .message("Evaluation saved successfully")
                .data(response)
                .code(200)
                .build());
    }

    @GetMapping("/attendance/{attendanceId}")
    public ResponseEntity<ApiResponse<EvaluationResponse>> getEvaluationByAttendanceId(
            @PathVariable Long attendanceId) {
        return evaluationService.getEvaluationByAttendanceId(attendanceId)
                .map(response -> ResponseEntity.ok(ApiResponse.<EvaluationResponse>builder()
                        .success(true)
                        .message("Evaluation found")
                        .data(response)
                        .code(200)
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get comprehensive evaluation details with all related data in a single API call
     * This endpoint consolidates data from multiple sources to minimize frontend API calls
     */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAnyRole('MANAGER_GENERAL_1', 'MANAGER_GENERAL_2', 'ADMIN')")
    public ResponseEntity<ApiResponse<EvaluationDetailResponse>> getEvaluationDetails(
            @PathVariable Long id) {
        return evaluationService.getEvaluationDetails(id)
                .map(response -> ResponseEntity.ok(ApiResponse.<EvaluationDetailResponse>builder()
                        .success(true)
                        .message("Evaluation details retrieved successfully")
                        .data(response)
                        .code(200)
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
