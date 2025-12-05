package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentHistoryResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.RollbackResponse;
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
    List<AssignmentResponse> getEmployeesByCustomer(Long customerId);
    List<AssignmentResponse> getAllEmployeesByCustomer(Long customerId);
        List<com.company.company_clean_hub_be.dto.response.CustomerResponse> getCustomersByEmployee(Long employeeId);
    PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse> getEmployeesNotAssignedToCustomer(Long customerId, int page, int pageSize);
        List<AssignmentResponse> getAssignmentsByEmployee(Long employeeId);
    
    // Lịch sử điều động
    List<AssignmentHistoryResponse> getReassignmentHistory(Long employeeId);
    List<AssignmentHistoryResponse> getReassignmentHistoryByContract(Long contractId);
    AssignmentHistoryResponse getHistoryDetail(Long historyId);
    RollbackResponse rollbackReassignment(Long historyId);
}
