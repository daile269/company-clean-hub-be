package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.common.ApiResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.Payroll;
import com.company.company_clean_hub_be.service.AssignmentService;
import com.company.company_clean_hub_be.service.AttendanceService;
import com.company.company_clean_hub_be.service.EmployeeService;
import com.company.company_clean_hub_be.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Nhóm 3 – Quản lý nhân sự (HR Management)
 *
 * Services chính (thuộc tầng service, được sử dụng bởi controller này):
 * - EmployeeService (hồ sơ nhân viên)
 * - EmployeeImageService (ảnh đại diện / hồ sơ)
 * - EmployeeCostService (quản lý lương, chi phí theo thời điểm)
 * - EmployeeDocumentService (quản lý giấy tờ: CCCD, Hợp đồng...)
 * - EmployeeRegionService (phân vùng nhân sự)
 *
 * Giải thích:
 * Bộ phận HR quản lý dữ liệu nhân sự: hồ sơ, ảnh, lương cơ bản và các thay đổi
 * liên quan đến chi phí. Gom các endpoint này vào một controller giúp tách bạch
 * trách nhiệm (dữ liệu nhân sự) so với vận hành (assignment/attendance).
 */
@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HRManagementController {
}
