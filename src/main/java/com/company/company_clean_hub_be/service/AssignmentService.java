package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import java.util.List;

public interface AssignmentService {
    List<AssignmentResponse> getAllAssignments();
    AssignmentResponse getAssignmentById(Long id);
    AssignmentResponse createAssignment(AssignmentRequest request);
    AssignmentResponse updateAssignment(Long id, AssignmentRequest request);
    void deleteAssignment(Long id);
}
