package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.EvaluationRequest;
import com.company.company_clean_hub_be.dto.response.EvaluationResponse;
import com.company.company_clean_hub_be.dto.response.EvaluationDetailResponse;
import java.util.Optional;

public interface EvaluationService {
    EvaluationResponse evaluate(EvaluationRequest request, String evaluatorUsername);
    Optional<EvaluationResponse> getEvaluationByAttendanceId(Long attendanceId);
    Optional<EvaluationDetailResponse> getEvaluationDetails(Long evaluationId);
}
