package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.common.ApiResponse;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.service.ContractService;
import com.company.company_clean_hub_be.service.ServiceEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Nhóm 2 – Quản lý vận hành dịch vụ (Operation Management)
 *
 * Services chính (thuộc tầng service, được sử dụng bởi controller này):
 * - CustomerService (quản lý khách hàng)
 * - ContractService (quản lý hợp đồng)
 * - ServicePackageService (gói/dịch vụ, tương tự ServiceEntity)
 * - AssignmentService (phân công / điều động nhân viên)
 * - AttendanceService (chấm công)
 * - AttendanceApprovalService (duyệt bảng công)
 * - ScheduleService (lập lịch, tự động chấm công)
 * - EmployeeWorkHistoryService (xem nhân viên đang làm ở đâu + lịch sử)
 *
 * Giải thích:
 * Đây là luồng vận hành chính: Khách hàng -> Hợp đồng -> Điều động -> Chấm công.
 * Controller này gom các endpoint liên quan tới vận hành dịch vụ để dễ theo dõi
 * và tối ưu hóa workflow (ví dụ: khi tạo hợp đồng tự động sinh assignment,...).
 */
@RestController
@RequestMapping("/api/operation")
@RequiredArgsConstructor
public class OperationController {

}
