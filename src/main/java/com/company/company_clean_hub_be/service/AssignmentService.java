package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;

import java.util.List;

public interface AssignmentService {
    List<AssignmentResponse> getAllAssignments();
    PageResponse<AssignmentResponse> getAssignmentsWithFilter(String keyword, int page, int pageSize);
    AssignmentResponse getAssignmentById(Long id);
    AssignmentResponse createAssignment(AssignmentRequest request);
    AssignmentResponse updateAssignment(Long id, AssignmentRequest request);
    void deleteAssignment(Long id);
    TemporaryAssignmentResponse temporaryReassignment(TemporaryReassignmentRequest request);
}
