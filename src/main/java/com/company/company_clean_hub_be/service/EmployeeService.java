package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.EmployeeRequest;
import com.company.company_clean_hub_be.dto.response.EmployeeResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.EmploymentType;

import java.util.List;

public interface EmployeeService {
    List<EmployeeResponse> getAllEmployees();
    PageResponse<EmployeeResponse> getEmployeesWithFilter(String keyword, EmploymentType employmentType, int page, int pageSize);
    EmployeeResponse getEmployeeById(Long id);
    EmployeeResponse createEmployee(EmployeeRequest request);
    EmployeeResponse updateEmployee(Long id, EmployeeRequest request);
    void deleteEmployee(Long id);
}
