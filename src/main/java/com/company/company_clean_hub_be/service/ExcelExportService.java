package com.company.company_clean_hub_be.service;

import java.util.List;

import org.springframework.core.io.ByteArrayResource;

import com.company.company_clean_hub_be.dto.response.CustomerContractGroupDto;
import com.company.company_clean_hub_be.dto.response.EmployeeExportDto;
import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;

public interface ExcelExportService {
    public ByteArrayResource exportUsersToExcel(List<PayRollExportExcel> payRollExportExcels, Integer month,Integer year);
    public ByteArrayResource exportCustomersWithContractsToExcel(List<CustomerContractGroupDto> customerGroups);
    public ByteArrayResource exportEmployeesToExcel(List<EmployeeExportDto> employees);
}
