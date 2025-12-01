package com.company.company_clean_hub_be.service.impl;

// payroll request import removed (payroll calculation logic disabled)
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.PayrollRepository;
import com.company.company_clean_hub_be.service.PayrollService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PayrollServiceImpl implements PayrollService {
    private final PayrollRepository payrollRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    @Override
    public PayrollResponse calculatePayroll(PayrollRequest request) {
        log.info("=== START calculatePayroll ===");
        log.info("Request: employeeId={}, month={}, year={}",
                request.getEmployeeId(), request.getMonth(), request.getYear());

        // Kiểm tra bảng lương đã tồn tại
        payrollRepository.findByEmployeeAndMonthAndYear(
                request.getEmployeeId(),
                request.getMonth(),
                request.getYear()
        ).ifPresent(p -> {
            log.error("Payroll already exists for employeeId={} month={} year={}",
                    request.getEmployeeId(), request.getMonth(), request.getYear());
            throw new AppException(ErrorCode.PAYROLL_ALREADY_EXISTS);
        });

        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        BigDecimal totalAdvance  = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        int totalDays = 0;

        List<Assignment> assignments = assignmentRepository
                .findDistinctAssignmentsByAttendanceMonthAndEmployee(
                        request.getMonth(), request.getYear(), request.getEmployeeId());
        log.info("Assignments found: {}", assignments.size());

        // Validate: Check if there are any assignments for this employee in the requested period
        if (assignments.isEmpty()) {
            log.error("No assignments found for employeeId={} month={} year={}",
                    request.getEmployeeId(), request.getMonth(), request.getYear());
            throw new AppException(ErrorCode.NO_ASSIGNMENT_DATA);
        }

        // Validate: Check if there are any attendances for this employee in the requested period
        List<Attendance> attendancesValidation = attendanceRepository.findAttendancesByMonthYearAndEmployee(
                request.getMonth(), request.getYear(), request.getEmployeeId());
        if (attendancesValidation.isEmpty()) {
            log.error("No attendance data found for employeeId={} month={} year={}",
                    request.getEmployeeId(), request.getMonth(), request.getYear());
            throw new AppException(ErrorCode.NO_ATTENDANCE_DATA);
        }

        for (Assignment assignment : assignments) {
            log.info("Processing Assignment id={}, type={}, salary={}, workDays={}",
                    assignment.getId(), assignment.getAssignmentType(),
                    assignment.getSalaryAtTime(), assignment.getWorkDays());

            // Tính lương theo loại assignment
            if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_CONTRACT) {
                if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0) {
                    BigDecimal dailyRate = assignment.getSalaryAtTime()
                            .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP);
                    BigDecimal assignmentAmount = dailyRate.multiply(BigDecimal.valueOf(assignment.getWorkDays()));
                    amountTotal = amountTotal.add(assignmentAmount);
                    log.info("FIXED_BY_CONTRACT: dailyRate={}, assignmentAmount={}", dailyRate, assignmentAmount);
                }
            } else if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_DAY) {
                if (assignment.getSalaryAtTime() != null) {
                    amountTotal = amountTotal.add(assignment.getSalaryAtTime());
                    log.info("FIXED_BY_DAY: assignmentAmount={}", assignment.getSalaryAtTime());
                }
            } else if (assignment.getAssignmentType() == AssignmentType.TEMPORARY) {
                if (assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {
                    BigDecimal assignmentAmount = assignment.getSalaryAtTime()
                            .multiply(BigDecimal.valueOf(assignment.getWorkDays()));
                    amountTotal = amountTotal.add(assignmentAmount);
                    log.info("TEMPORARY: assignmentAmount={}", assignmentAmount);
                }
            }

            totalDays += assignment.getWorkDays() != null ? assignment.getWorkDays() : 0;

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            totalBonus = totalBonus.add(bonus != null ? bonus : BigDecimal.ZERO);
            log.info("Bonus for assignmentId={} = {}", assignment.getId(), bonus);

            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            totalPenalties = totalPenalties.add(penalty != null ? penalty : BigDecimal.ZERO);
            log.info("Penalty for assignmentId={} = {}", assignment.getId(), penalty);

            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            totalSupportCosts = totalSupportCosts.add(support != null ? support : BigDecimal.ZERO).add(assignment.getAdditionalAllowance());
            log.info("SupportCost for assignmentId={} = {}", assignment.getId(), support);

        }

        // Tính lương cuối cùng
        BigDecimal finalSalary = amountTotal.add(totalBonus).add(totalSupportCosts).subtract(totalPenalties);
        log.info("amountTotal={}, totalBonus={}, totalSupportCosts={}, totalPenalties={}, finalSalary={}",
                amountTotal, totalBonus, totalSupportCosts, totalPenalties, finalSalary);

        User user = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> {
                    log.error("Current user not found");
                    return new AppException(ErrorCode.USER_IS_NOT_EXISTS);
                });

        Payroll payroll = Payroll.builder()
                .totalDays(totalDays)
                .bonusTotal(totalBonus)
                .penaltyTotal(totalPenalties)
                .advanceTotal(totalAdvance)
                .allowanceTotal(totalSupportCosts)
                .insuranceTotal(totalInsurance)
                .finalSalary(finalSalary)
                .paymentDate(null)
                .isPaid(false)
                .accountant(user)
                .createdAt(LocalDateTime.of(request.getYear(),request.getMonth() ,1, 0, 0, 0))
                .updatedAt(LocalDateTime.now())
                .build();

        Payroll savedPayroll = payrollRepository.save(payroll);
        log.info("Payroll saved with id={}", savedPayroll.getId());

        // Cập nhật payroll cho attendance
        LocalDate startDate = LocalDate.of(request.getYear(), request.getMonth(), 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        List<Attendance> attendances = attendanceRepository.findAttendancesByMonthYearAndEmployee(
                request.getMonth(), request.getYear(), request.getEmployeeId());
        log.info("Attendances found for payroll: {}", attendances.size());
        if (attendances != null && !attendances.isEmpty()) {
            for (Attendance a : attendances) {
                a.setPayroll(savedPayroll);
            }
            attendanceRepository.saveAll(attendances);
            log.info("Updated payroll for all attendances");
        }

        log.info("=== END calculatePayroll ===");
        return mapToResponse(savedPayroll, request.getMonth(), request.getYear(), request.getEmployeeId());
    }

    @Override
    public PayrollResponse getPayrollById(Long id) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));
        
        LocalDateTime createdAt = payroll.getCreatedAt();
        return mapToResponse(payroll, createdAt.getMonthValue(), createdAt.getYear(), null);
    }

    @Override
    public List<PayrollResponse> getAllPayrolls() {
        return payrollRepository.findAll().stream()
                .map(p -> {
                    LocalDateTime createdAt = p.getCreatedAt();
                    return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear(), null);
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
                    return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear(), null);
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
        return mapToResponse(updatedPayroll, createdAt.getMonthValue(), createdAt.getYear(), null);
    }

    @Override
    public PayrollResponse updatePayroll(Long id, PayrollUpdateRequest request) {
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));

        LocalDateTime createdAt = payroll.getCreatedAt();
        Integer month = createdAt != null ? createdAt.getMonthValue() : null;
        Integer year = createdAt != null ? createdAt.getYear() : null;

        // derive employeeId from attendances linked to payroll
        Long employeeId = null;
        if (payroll.getAttendances() != null && !payroll.getAttendances().isEmpty()) {
            Attendance any = payroll.getAttendances().get(0);
            if (any != null && any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                employeeId = any.getAssignment().getEmployee().getId();
            }
        }
        if (employeeId == null) {
            List<Attendance> byPayroll = attendanceRepository.findByPayrollId(payroll.getId());
            if (byPayroll != null && !byPayroll.isEmpty()) {
                Attendance any = byPayroll.get(0);
                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    employeeId = any.getAssignment().getEmployee().getId();
                }
            }
        }

        if (employeeId == null) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }

        // Recompute amounts similar to calculatePayroll
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        BigDecimal totalAdvance = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        int totalDays = 0;

        List<Assignment> assignments = assignmentRepository
                .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId);

        for (Assignment assignment : assignments) {
            if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_CONTRACT) {
                if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0) {
                    amountTotal = amountTotal.add(
                            assignment.getSalaryAtTime()
                                    .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(assignment.getWorkDays()))
                    );
                }
            } else if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_DAY) {
                if (assignment.getSalaryAtTime() != null) {
                    amountTotal = amountTotal.add(assignment.getSalaryAtTime());
                }
            } else if (assignment.getAssignmentType() == AssignmentType.TEMPORARY) {
                if (assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {
                    amountTotal = amountTotal.add(
                            assignment.getSalaryAtTime().multiply(BigDecimal.valueOf(assignment.getWorkDays()))
                    );
                }
            }

            totalDays += assignment.getWorkDays() != null ? assignment.getWorkDays() : 0;

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            totalBonus = totalBonus.add(bonus != null ? bonus : BigDecimal.ZERO);

            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            totalPenalties = totalPenalties.add(penalty != null ? penalty : BigDecimal.ZERO);

            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            totalSupportCosts = totalSupportCosts.add(support != null ? support : BigDecimal.ZERO);
        }

        // Use provided adjustments if present, otherwise use computed values or existing payroll values
        BigDecimal allowanceTotal = request.getAllowanceTotal() != null ? request.getAllowanceTotal() : (totalSupportCosts);
        BigDecimal insuranceTotal = request.getInsuranceTotal() != null ? request.getInsuranceTotal() : (payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO);
        BigDecimal advanceTotal = request.getAdvanceTotal() != null ? request.getAdvanceTotal() : (payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO);

        BigDecimal finalSalary = amountTotal.add(totalBonus).add(allowanceTotal).subtract(totalPenalties).subtract(insuranceTotal).subtract(advanceTotal);

        payroll.setTotalDays(totalDays);
        payroll.setBonusTotal(totalBonus);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(allowanceTotal);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(finalSalary);
        payroll.setUpdatedAt(LocalDateTime.now());

        Payroll updated = payrollRepository.save(payroll);
        return mapToResponse(updated, month, year, employeeId);
    }

    @Override
    public void deletePayroll(Long id) {
        if (!payrollRepository.existsById(id)) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }
        payrollRepository.deleteById(id);
    }

    private PayrollResponse mapToResponse(Payroll payroll, Integer month, Integer year, Long fallbackEmployeeId) {
        User accountant = payroll.getAccountant();

        // Try to derive employee info from attendances (attendance -> assignment -> employee)
        Long employeeId = null;
        String employeeName = null;
        String employeeCode = null;

        // 1) If payroll entity has attendances loaded — use them
        if (payroll.getAttendances() != null && !payroll.getAttendances().isEmpty()) {
            Attendance any = payroll.getAttendances().get(0);
            if (any != null && any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                Employee emp = any.getAssignment().getEmployee();
                employeeId = emp.getId();
                employeeName = emp.getName();
                employeeCode = emp.getEmployeeCode();
            }
        }

        // 2) If not found, try to load attendances by payroll id (in case payroll wasn't linked back to attendances yet)
        if (employeeId == null && payroll.getId() != null) {
            List<Attendance> byPayroll = attendanceRepository.findByPayrollId(payroll.getId());
            if (byPayroll != null && !byPayroll.isEmpty()) {
                Attendance any = byPayroll.get(0);
                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    Employee emp = any.getAssignment().getEmployee();
                    employeeId = emp.getId();
                    employeeName = emp.getName();
                    employeeCode = emp.getEmployeeCode();
                }
            }
        }

        // 3) If still not found and a fallback employeeId is provided (e.g. during calculatePayroll),
        //    try to fetch attendances for the month/year and derive the employee from assignment
        if (employeeId == null && fallbackEmployeeId != null) {
            List<Attendance> monthAttendances = attendanceRepository.findAttendancesByMonthYearAndEmployee(
                    month, year, fallbackEmployeeId);
            if (monthAttendances != null && !monthAttendances.isEmpty()) {
                Attendance any = monthAttendances.get(0);
                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    Employee emp = any.getAssignment().getEmployee();
                    employeeId = emp.getId();
                    employeeName = emp.getName();
                    employeeCode = emp.getEmployeeCode();
                }
            }
        }

        return PayrollResponse.builder()
            .id(payroll.getId())
            .employeeId(employeeId)
            .employeeName(employeeName)
            .employeeCode(employeeCode)
            .month(month)
            .year(year)
            .totalDays(payroll.getTotalDays())
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
