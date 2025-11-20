package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.common.ApiResponse;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Material;
import com.company.company_clean_hub_be.entity.MaterialDistribution;
import com.company.company_clean_hub_be.entity.Payroll;
import com.company.company_clean_hub_be.service.ContractService;
import com.company.company_clean_hub_be.service.MaterialDistributionService;
import com.company.company_clean_hub_be.service.MaterialService;
import com.company.company_clean_hub_be.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Nhóm 4 – Tài chính & Báo cáo (Finance & Reporting)
 *
 * Services chính (thuộc tầng service, được sử dụng bởi controller này):
 * - PayrollService (quản lý lương)
 * - PayrollCalculationService (tính lương theo công thức)
 * - PayrollPaymentService (đánh dấu đã trả / ứng lương)
 * - MaterialService (quản lý vật tư)
 * - MaterialDistributionService (cấp phát vật tư)
 * - RevenueReportService, AttendanceReportService, PayrollReportService, ExportService
 *
 * Giải thích:
 * Nhóm này chịu trách nhiệm các endpoint liên quan tới tài chính, tính toán và
 * xuất báo cáo. Gom các chức năng báo cáo & vật tư vào một controller giúp
 * tách riêng concern liên quan tới tiền bạc và báo cáo khỏi vận hành/human resources.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceReportingController {

}
