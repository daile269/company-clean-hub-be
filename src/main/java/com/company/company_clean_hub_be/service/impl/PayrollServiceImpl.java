package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.PayrollRepository;
import com.company.company_clean_hub_be.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollServiceImpl implements PayrollService {
    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final AssignmentRepository assignmentRepository;

//    @Override
//    public PayrollResponse calculatePayroll(PayrollRequest request) {
//        // Kiểm tra bảng lương tháng này đã tồn tại chưa
//        payrollRepository.findByEmployeeAndMonthAndYear(
//                request.getEmployeeId(), 
//                request.getMonth(), 
//                request.getYear()
//        ).ifPresent(p -> {
//            throw new AppException(ErrorCode.PAYROLL_ALREADY_EXISTS);
//        });
//
//        // Lấy thông tin nhân viên
//        Employee employee = employeeRepository.findById(request.getEmployeeId())
//                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
//
//        // Tính ngày đầu và cuối tháng
//        YearMonth yearMonth = YearMonth.of(request.getYear(), request.getMonth());
//        LocalDate startDate = yearMonth.atDay(1);
//        LocalDate endDate = yearMonth.atEndOfMonth();
//
//        // Lấy danh sách chấm công trong tháng
//        List<Attendance> attendances = attendanceRepository.findByEmployeeAndDateBetween(
//                request.getEmployeeId(), 
//                startDate, 
//                endDate
//        );
//
//        // Tính tổng công thực tế
//        int totalDays = attendances.size();
//
//        // Tính tổng thưởng, phạt, phụ cấp
//        BigDecimal bonusTotal = attendances.stream()
//                .map(Attendance::getBonus)
//                .filter(bonus -> bonus != null)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        BigDecimal penaltyTotal = attendances.stream()
//                .map(Attendance::getPenalty)
//                .filter(penalty -> penalty != null)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        BigDecimal supportCostTotal = attendances.stream()
//                .map(Attendance::getSupportCost)
//                .filter(cost -> cost != null)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        BigDecimal overtimeTotal = attendances.stream()
//                .map(Attendance::getOvertimeAmount)
//                .filter(overtime -> overtime != null)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        // Tính lương cơ bản theo loại nhân viên
//        BigDecimal salaryBase = calculateBaseSalary(employee, totalDays, endDate);
//
//        // Tính bảo hiểm
//        BigDecimal insuranceTotal = BigDecimal.ZERO;
//        if (employee.getSocialInsurance() != null) {
//            insuranceTotal = insuranceTotal.add(employee.getSocialInsurance());
//        }
//        if (employee.getHealthInsurance() != null) {
//            insuranceTotal = insuranceTotal.add(employee.getHealthInsurance());
//        }
//
//        // Tính phụ cấp nhân viên
//        BigDecimal allowanceTotal = employee.getAllowance() != null ? 
//                employee.getAllowance() : BigDecimal.ZERO;
//
//        // Tính lương cuối cùng
//        BigDecimal finalSalary = salaryBase
//                .add(bonusTotal)
//                .add(supportCostTotal)
//                .add(overtimeTotal)
//                .add(allowanceTotal)
//                .subtract(penaltyTotal)
//                .subtract(insuranceTotal);
//
//        // Tạo bảng lương
//        Payroll payroll = Payroll.builder()
//                .employee(employee)
//                .totalDays(totalDays)
//                .salaryBase(salaryBase)
//                .bonusTotal(bonusTotal)
//                .penaltyTotal(penaltyTotal)
//                .advanceTotal(BigDecimal.ZERO)
//                .allowanceTotal(allowanceTotal.add(supportCostTotal).add(overtimeTotal))
//                .insuranceTotal(insuranceTotal)
//                .finalSalary(finalSalary)
//                .isPaid(false)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//
//        Payroll savedPayroll = payrollRepository.save(payroll);
//        return mapToResponse(savedPayroll, request.getMonth(), request.getYear());
//    }

    /**
     * Tính lương cơ bản theo 3 loại nhân viên
     */
//    private BigDecimal calculateBaseSalary(Employee employee, int totalDays, LocalDate endDate) {
//        EmploymentType type = employee.getEmploymentType();
//
//        switch (type) {
//            case FIXED_BY_CONTRACT:
//                // Lương = (Lương cứng / Công chuẩn) × Công thực tế
//                // Lấy assignment gần nhất để biết công chuẩn
//                List<Assignment> assignments = assignmentRepository.findActiveAssignmentsByEmployee(
//                        employee.getId(),
//                        endDate
//                );
//
//                if (assignments.isEmpty()) {
//                    throw new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
//                }
//
//                Assignment assignment = assignments.get(0);
//                Integer workDays = assignment.getWorkDays() != null ? assignment.getWorkDays() : 26;
//
//                return employee.getBaseSalary()
//                        .divide(BigDecimal.valueOf(workDays), 2, RoundingMode.HALF_UP)
//                        .multiply(BigDecimal.valueOf(totalDays));
//
//            case FIXED_BY_DAY:
//                // Lương = Lương cố định của hợp đồng (không phụ thuộc công)
//                List<Assignment> activeAssignments = assignmentRepository.findActiveAssignmentsByEmployee(
//                        employee.getId(),
//                        endDate
//                );
//
//                if (activeAssignments.isEmpty()) {
//                    throw new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
//                }
//
//                Assignment currentAssignment = activeAssignments.get(0);
//                return currentAssignment.getSalaryAtTime() != null ?
//                        currentAssignment.getSalaryAtTime() : employee.getBaseSalary();
//
//            case TEMPORARY:
//                // Lương = Số ngày công × Đơn giá/ngày
//                BigDecimal dailySalary = employee.getDailySalary() != null ?
//                        employee.getDailySalary() : BigDecimal.ZERO;
//                return dailySalary.multiply(BigDecimal.valueOf(totalDays));
//
//            default:
//                return BigDecimal.ZERO;
//        }
//    }

    @Override
    public PayrollResponse getPayrollById(Long id) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));
        
        LocalDateTime createdAt = payroll.getCreatedAt();
        return mapToResponse(payroll, createdAt.getMonthValue(), createdAt.getYear());
    }

    @Override
    public List<PayrollResponse> getAllPayrolls() {
        return payrollRepository.findAll().stream()
                .map(p -> {
                    LocalDateTime createdAt = p.getCreatedAt();
                    return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear());
                })
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<PayrollResponse> getPayrollsWithFilter(String keyword, Integer month, Integer year, Boolean isPaid, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Payroll> payrollPage = payrollRepository.findByFilters(keyword, month, year, isPaid, pageable);

        List<PayrollResponse> payrolls = payrollPage.getContent().stream()
                .map(p -> {
                    LocalDateTime createdAt = p.getCreatedAt();
                    return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear());
                })
                .collect(Collectors.toList());

        return PageResponse.<PayrollResponse>builder()
                .content(payrolls)
                .page(payrollPage.getNumber())
                .pageSize(payrollPage.getSize())
                .totalElements(payrollPage.getTotalElements())
                .totalPages(payrollPage.getTotalPages())
                .first(payrollPage.isFirst())
                .last(payrollPage.isLast())
                .build();
    }

    @Override
    public PayrollResponse updatePaymentStatus(Long id, Boolean isPaid) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));
        
        payroll.setIsPaid(isPaid);
        if (isPaid) {
            payroll.setPaymentDate(LocalDateTime.now());
        }
        payroll.setUpdatedAt(LocalDateTime.now());
        
        Payroll updatedPayroll = payrollRepository.save(payroll);
        LocalDateTime createdAt = updatedPayroll.getCreatedAt();
        return mapToResponse(updatedPayroll, createdAt.getMonthValue(), createdAt.getYear());
    }

    @Override
    public void deletePayroll(Long id) {
        if (!payrollRepository.existsById(id)) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }
        payrollRepository.deleteById(id);
    }

    private PayrollResponse mapToResponse(Payroll payroll, Integer month, Integer year) {
        Employee employee = payroll.getEmployee();
        User accountant = payroll.getAccountant();
        
        return PayrollResponse.builder()
                .id(payroll.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeeCode(employee.getEmployeeCode())
                .month(month)
                .year(year)
                .totalDays(payroll.getTotalDays())
                .salaryBase(payroll.getSalaryBase())
                .bonusTotal(payroll.getBonusTotal())
                .penaltyTotal(payroll.getPenaltyTotal())
                .advanceTotal(payroll.getAdvanceTotal())
                .allowanceTotal(payroll.getAllowanceTotal())
                .insuranceTotal(payroll.getInsuranceTotal())
                .finalSalary(payroll.getFinalSalary())
                .isPaid(payroll.getIsPaid())
                .paymentDate(payroll.getPaymentDate())
                .accountantId(accountant != null ? accountant.getId() : null)
                .accountantName(accountant != null ? accountant.getUsername() : null)
                .createdAt(payroll.getCreatedAt())
                .updatedAt(payroll.getUpdatedAt())
                .build();
    }
}
