package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

public interface ExcelExportService {
    public ByteArrayResource exportUsersToExcel(List<PayRollExportExcel> payRollExportExcels, Integer month,Integer year);
}
