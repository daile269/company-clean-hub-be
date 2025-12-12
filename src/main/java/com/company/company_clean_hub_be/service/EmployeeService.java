package com.company.company_clean_hub_be.service;

import java.util.List;

import com.company.company_clean_hub_be.dto.request.EmployeeRequest;
import com.company.company_clean_hub_be.dto.response.EmployeeExportDto;
import com.company.company_clean_hub_be.dto.response.EmployeeResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;

public interface EmployeeService {
    List<EmployeeResponse> getAllEmployees();
    PageResponse<EmployeeResponse> getEmployeesWithFilter(String keyword, com.company.company_clean_hub_be.entity.EmploymentType employmentType, int page, int pageSize);
    EmployeeResponse getEmployeeById(Long id);
    EmployeeResponse createEmployee(EmployeeRequest request);
    EmployeeResponse updateEmployee(Long id, EmployeeRequest request);
    void deleteEmployee(Long id);
    List<EmployeeExportDto> getAllEmployeesForExport();
    List<EmployeeExportDto> getEmployeesForExportByType(com.company.company_clean_hub_be.entity.EmploymentType employmentType);
}
