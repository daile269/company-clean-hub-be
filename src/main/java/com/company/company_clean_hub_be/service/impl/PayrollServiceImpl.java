package com.company.company_clean_hub_be.service.impl;

// payroll request import removed (payroll calculation logic disabled)
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.repository.*;
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
    private  final EmployeeRepository employeeRepository;
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
        BigDecimal totalAdvance = request.getAdvanceSalary() != null ? request.getAdvanceSalary() : BigDecimal.ZERO;
        BigDecimal totalInsurance = request.getInsuranceAmount() != null ? request.getInsuranceAmount() : BigDecimal.ZERO;
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
            BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null
                    ? assignment.getAdditionalAllowance()
                    : BigDecimal.ZERO;


            totalSupportCosts = (totalSupportCosts != null ? totalSupportCosts : BigDecimal.ZERO)
                    .add(support != null ? support : BigDecimal.ZERO)
                    .add(additionalAllowance);
            log.info("SupportCost for assignmentId={} = {}", assignment.getId(), support);

        }

        // Tính lương cuối cùng (cộng bảo hiểm, trừ tiền ứng lương)
        BigDecimal finalSalary = amountTotal.add(totalBonus).add(totalSupportCosts)
                .add(totalInsurance).subtract(totalPenalties).subtract(totalAdvance);
        log.info("amountTotal={}, totalBonus={}, totalSupportCosts={}, totalPenalties={}, insuranceAmount={}, advanceSalary={}, finalSalary={}",
                amountTotal, totalBonus, totalSupportCosts, totalPenalties, totalInsurance, totalAdvance, finalSalary);

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
    public List<PayRollExportExcel> getAllPayRoll(Integer month, Integer year) {

        log.info("[PAYROLL-EXPORT] ===== START getAllPayRoll(month={}, year={}) =====", month, year);

        List<Employee> employees = employeeRepository.findDistinctEmployeesByAssignmentMonthYear(month, year);
        log.info("[PAYROLL-EXPORT] Found {} employees with assignments in month/year",
                employees != null ? employees.size() : null);

        List<PayRollExportExcel> result = new ArrayList<>();
        if (employees == null || employees.isEmpty()) {
            log.info("[PAYROLL-EXPORT] No employees found. RETURN empty list.");
            return result;
        }

        User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        for (Employee employee : employees) {
            Long employeeId = employee.getId();
            log.info("\n[PAYROLL-EXPORT] --- PROCESS EMPLOYEE id={}, name={} ---",
                    employeeId, employee.getName());

            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId);
            log.info("[PAYROLL-EXPORT] Employee {} has {} assignments",
                    employeeId, assignments != null ? assignments.size() : null);

            if (assignments == null || assignments.isEmpty()) {
                log.info("[PAYROLL-EXPORT] Employee {} has NO assignments. Skip.", employeeId);
                continue;
            }

            Payroll persistedPayroll = upsertPayrollFromAssignments(employee, assignments, month, year, accountant);
            if (persistedPayroll == null) {
                log.info("[PAYROLL-EXPORT] Employee {} skipped because no attendance was found.", employeeId);
                continue;
            }

            List<String> projectCompanies = assignments.stream()
                    .map(a -> a.getCustomer() != null ? a.getCustomer().getCompany() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            PayRollExportExcel dto = PayRollExportExcel.builder()
                    .employeeId(employeeId)
                    .employeeName(employee.getName())
                    .bankName(employee.getBankName())
                    .bankAccount(employee.getBankAccount())
                    .phone(employee.getPhone())
                    .projectCompanies(projectCompanies)
                    .totalDays(persistedPayroll.getTotalDays())
                    .totalBonus(persistedPayroll.getBonusTotal())
                    .totalPenalty(persistedPayroll.getPenaltyTotal())
                    .totalAllowance(persistedPayroll.getAllowanceTotal())
                    .totalInsurance(persistedPayroll.getInsuranceTotal())
                    .totalAdvance(persistedPayroll.getAdvanceTotal())
                    .finalSalary(persistedPayroll.getFinalSalary())
                    .build();

            result.add(dto);
            log.info("[PAYROLL-EXPORT] Added payroll row for employee {}", employeeId);
        }

        log.info("[PAYROLL-EXPORT] ===== FINISHED getAllPayRoll. Total rows={} =====",
                result.size());

        return result;
    }

    private Payroll upsertPayrollFromAssignments(Employee employee,
                                                 List<Assignment> assignments,
                                                 Integer month,
                                                 Integer year,
                                                 User accountant) {
        if (month == null || year == null) {
            log.error("[PAYROLL-EXPORT] Month/Year is required.");
            return null;
        }

        List<Attendance> attendances = attendanceRepository
                .findAttendancesByMonthYearAndEmployee(month, year, employee.getId());

        if (attendances == null || attendances.isEmpty()) {
            log.info("[PAYROLL-EXPORT] Employee {} has NO attendance. Skip payroll.", employee.getId());
            return null;
        }

        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        int totalDays = 0;

        for (Assignment assignment : assignments) {
            amountTotal = amountTotal.add(calculateAssignmentAmount(assignment));
            totalDays += assignment.getWorkDays() != null ? assignment.getWorkDays() : 0;

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null
                    ? assignment.getAdditionalAllowance()
                    : BigDecimal.ZERO;

            totalBonus = totalBonus.add(bonus != null ? bonus : BigDecimal.ZERO);
            totalPenalties = totalPenalties.add(penalty != null ? penalty : BigDecimal.ZERO);
            totalSupportCosts = totalSupportCosts
                    .add(support != null ? support : BigDecimal.ZERO)
                    .add(additionalAllowance);
        }

        Payroll payroll = payrollRepository.findByEmployeeAndMonthAndYear(employee.getId(), month, year)
                .orElseGet(() -> Payroll.builder()
                        .accountant(accountant)
                        .createdAt(LocalDateTime.of(year, month, 1, 0, 0))
                        .updatedAt(LocalDateTime.now())
                        .isPaid(false)
                        .paymentDate(null)
                        .advanceTotal(BigDecimal.ZERO)
                        .insuranceTotal(BigDecimal.ZERO)
                        .build());

        BigDecimal advanceTotal = payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO;
        BigDecimal insuranceTotal = payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO;

        BigDecimal finalSalary = amountTotal
                .add(totalBonus)
                .add(totalSupportCosts)
                .subtract(totalPenalties)
                .subtract(insuranceTotal)
                .subtract(advanceTotal);

        payroll.setAccountant(accountant);
        payroll.setBonusTotal(totalBonus);
        payroll.setTotalDays(totalDays);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(totalSupportCosts);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(finalSalary);
        payroll.setUpdatedAt(LocalDateTime.now());

        Payroll savedPayroll = payrollRepository.save(payroll);

        for (Attendance attendance : attendances) {
            attendance.setPayroll(savedPayroll);
        }
        attendanceRepository.saveAll(attendances);

        return savedPayroll;
    }

    private BigDecimal calculateAssignmentAmount(Assignment assignment) {
        if (assignment == null || assignment.getAssignmentType() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = BigDecimal.ZERO;
        AssignmentType type = assignment.getAssignmentType();

        if (type == AssignmentType.FIXED_BY_CONTRACT) {
            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {

                BigDecimal dailyRate = assignment.getSalaryAtTime()
                        .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP);
                amount = dailyRate.multiply(BigDecimal.valueOf(assignment.getWorkDays()));
            }
        } else if (type == AssignmentType.FIXED_BY_DAY) {
            if (assignment.getSalaryAtTime() != null) {
                amount = assignment.getSalaryAtTime();
            }
        } else if (type == AssignmentType.TEMPORARY) {
            if (assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {
                amount = assignment.getSalaryAtTime()
                        .multiply(BigDecimal.valueOf(assignment.getWorkDays()));
            }
        }

        return amount;
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

        log.info("[PAYROLL-DEBUG] ===== START updatePayroll(id={}) =====", id);

        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));

        log.info("[PAYROLL-DEBUG] Loaded payroll: {}", payroll);

        LocalDateTime createdAt = payroll.getCreatedAt();
        Integer month = createdAt != null ? createdAt.getMonthValue() : null;
        Integer year = createdAt != null ? createdAt.getYear() : null;

        log.info("[PAYROLL-DEBUG] Payroll createdAt={}, month={}, year={}",
                createdAt, month, year);

        // derive employeeId
        Long employeeId = null;
        log.info("[PAYROLL-DEBUG] payroll.getAttendances(): size={}",
                payroll.getAttendances() != null ? payroll.getAttendances().size() : null);

        if (payroll.getAttendances() != null && !payroll.getAttendances().isEmpty()) {
            Attendance any = payroll.getAttendances().get(0);
            log.info("[PAYROLL-DEBUG] Pulled employee from payroll-attendance: {}", any);

            if (any != null && any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                employeeId = any.getAssignment().getEmployee().getId();
            }
        }

        if (employeeId == null) {
            log.info("[PAYROLL-DEBUG] employeeId not found. Trying attendanceRepository.findByPayrollId...");
            List<Attendance> byPayroll = attendanceRepository.findByPayrollId(payroll.getId());

            log.info("[PAYROLL-DEBUG] findByPayrollId result size={}",
                    byPayroll != null ? byPayroll.size() : null);

            if (byPayroll != null && !byPayroll.isEmpty()) {
                Attendance any = byPayroll.get(0);
                log.info("[PAYROLL-DEBUG] Pulled employee from attendanceRepository result: {}", any);

                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    employeeId = any.getAssignment().getEmployee().getId();
                }
            }
        }

        log.info("[PAYROLL-DEBUG] RESOLVED employeeId={}", employeeId);

        if (employeeId == null) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }

        // Recompute amounts
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        int totalDays = 0;

        log.info("[PAYROLL-DEBUG] Fetch assignments for month={}, year={}, employee={}",
                month, year, employeeId);

        List<Assignment> assignments =
                assignmentRepository.findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId);

        log.info("[PAYROLL-DEBUG] Total assignments found={}", assignments.size());

        for (Assignment assignment : assignments) {

            log.info("[PAYROLL-DEBUG] --- PROCESS assignment {} (type={}) ---",
                    assignment.getId(), assignment.getAssignmentType());

            if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_CONTRACT) {
                log.info("[PAYROLL-DEBUG] FIXED_BY_CONTRACT salary={}, plannedDays={}, workDays={}",
                        assignment.getSalaryAtTime(), assignment.getPlannedDays(), assignment.getWorkDays());

                if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0) {
                    BigDecimal added = assignment.getSalaryAtTime()
                            .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(assignment.getWorkDays()));

                    log.info("[PAYROLL-DEBUG] Added to amountTotal from this assignment: {}", added);

                    amountTotal = amountTotal.add(added);
                }

            } else if (assignment.getAssignmentType() == AssignmentType.FIXED_BY_DAY) {

                log.info("[PAYROLL-DEBUG] FIXED_BY_DAY salary={}", assignment.getSalaryAtTime());

                if (assignment.getSalaryAtTime() != null) {
                    amountTotal = amountTotal.add(assignment.getSalaryAtTime());
                    log.info("[PAYROLL-DEBUG] Added FIXED_BY_DAY: {}", assignment.getSalaryAtTime());
                }

            } else if (assignment.getAssignmentType() == AssignmentType.TEMPORARY) {

                log.info("[PAYROLL-DEBUG] TEMPORARY salary={}, workDays={}",
                        assignment.getSalaryAtTime(), assignment.getWorkDays());

                if (assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {
                    BigDecimal added = assignment.getSalaryAtTime()
                            .multiply(BigDecimal.valueOf(assignment.getWorkDays()));

                    amountTotal = amountTotal.add(added);
                    log.info("[PAYROLL-DEBUG] Added TEMPORARY: {}", added);
                }
            }

            totalDays += assignment.getWorkDays() != null ? assignment.getWorkDays() : 0;
            log.info("[PAYROLL-DEBUG] Accumulated totalDays = {}", totalDays);

            // --- SUMS ---
            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null
                    ? assignment.getAdditionalAllowance()
                    : BigDecimal.ZERO;

            log.info("[PAYROLL-DEBUG] SUM bonus={}, penalty={}, support={}, additionalAllowance={}",
                    bonus, penalty, support, additionalAllowance);

            totalBonus = totalBonus.add(bonus != null ? bonus : BigDecimal.ZERO);
            totalPenalties = totalPenalties.add(penalty != null ? penalty : BigDecimal.ZERO);
            totalSupportCosts = (totalSupportCosts != null ? totalSupportCosts : BigDecimal.ZERO)
                    .add(support != null ? support : BigDecimal.ZERO)
                    .add(additionalAllowance);

            log.info("[PAYROLL-DEBUG] Accumulated totals => bonus={}, penalty={}, allowance={}",
                    totalBonus, totalPenalties, totalSupportCosts);
        }

        // Apply request values override (allowance is always derived from system data)
        log.info("[PAYROLL-DEBUG] Request override (ignore allowance from request). insurance={}, advance={}",
                request.getInsuranceTotal(), request.getAdvanceTotal());

        // allowanceTotal is strictly computed from daily support costs + assignment additional allowance
        BigDecimal allowanceTotal = totalSupportCosts;

        BigDecimal insuranceTotal = request.getInsuranceTotal() != null ?
                request.getInsuranceTotal() :
                (payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO);

        BigDecimal advanceTotal = request.getAdvanceTotal() != null ?
                request.getAdvanceTotal() :
                (payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO);

        log.info("[PAYROLL-DEBUG] Final applied values => allowance={}, insurance={}, advance={}",
                allowanceTotal, insuranceTotal, advanceTotal);

        BigDecimal finalSalary = amountTotal
                .add(totalBonus)
                .add(allowanceTotal)
                .subtract(totalPenalties)
                .subtract(insuranceTotal)
                .subtract(advanceTotal);

        log.info("[PAYROLL-DEBUG] finalSalary calculation:");
        log.info("[PAYROLL-DEBUG] amountTotal={} + bonus={} + allowance={} - penalty={} - insurance={} - advance={}",
                amountTotal, totalBonus, allowanceTotal, totalPenalties, insuranceTotal, advanceTotal);

        log.info("[PAYROLL-DEBUG] >>> finalSalary={}", finalSalary);

        payroll.setTotalDays(totalDays);
        payroll.setBonusTotal(totalBonus);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(allowanceTotal);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(finalSalary);
        payroll.setUpdatedAt(LocalDateTime.now());

        Payroll updated = payrollRepository.save(payroll);

        log.info("[PAYROLL-DEBUG] ===== END updatePayroll(id={}) =====", id);

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

        log.info("=== mapToResponse START ===");
        log.info("Input payrollId: {}, month: {}, year: {}, fallbackEmployeeId: {}",
                payroll != null ? payroll.getId() : null, month, year, fallbackEmployeeId);

        User accountant = payroll.getAccountant();
        log.info("Accountant: {}", accountant != null ? accountant.getUsername() : "null");

        Long employeeId = null;
        String employeeName = null;
        String employeeCode = null;
        BigDecimal salaryBase = BigDecimal.ZERO;
        // (1) Kiểm tra attendances trong payroll
        log.info("Step 1: Check payroll.getAttendances() ...");
        if (payroll.getAttendances() != null && !payroll.getAttendances().isEmpty()) {
            Attendance any = payroll.getAttendances().get(0);
            log.info("Payroll attendances count: {}", payroll.getAttendances().size());

            if (any != null && any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                Employee emp = any.getAssignment().getEmployee();
                employeeId = emp.getId();
                employeeName = emp.getName();
                employeeCode = emp.getEmployeeCode();
                salaryBase = salaryBase.add(any.getAssignment().getSalaryAtTime());
                log.info("Employee found from payroll attendances: id={}, name={}, code={}",
                        employeeId, employeeName, employeeCode);
            } else {
                log.info("Payroll attendance exists nhưng không có assignment -> employee.");
            }
        } else {
            log.info("Payroll attendances rỗng hoặc null.");
        }

        // (2) Nếu chưa tìm được → thử tìm bằng attendanceRepository.findByPayrollId
        log.info("Step 2: Check attendanceRepository.findByPayrollId ...");
        if (employeeId == null && payroll.getId() != null) {
            List<Attendance> byPayroll = attendanceRepository.findByPayrollId(payroll.getId());
            log.info("Attendances by payrollId {}: {}", payroll.getId(),
                    byPayroll != null ? byPayroll.size() : null);

            if (byPayroll != null && !byPayroll.isEmpty()) {
                Attendance any = byPayroll.get(0);
                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    Employee emp = any.getAssignment().getEmployee();
                    employeeId = emp.getId();
                    employeeName = emp.getName();
                    employeeCode = emp.getEmployeeCode();
                    salaryBase = salaryBase.add(any.getAssignment().getSalaryAtTime());
                    log.info("Employee found from findByPayrollId: id={}, name={}, code={}",
                            employeeId, employeeName, employeeCode);
                } else {
                    log.info("Attendance found nhưng không có assignment -> employee.");
                }
            } else {
                log.info("Không tìm thấy attendances bằng payrollId.");
            }
        }

        // (3) Thử tìm bằng fallback employee + month/year

        log.info("Step 3: Check fallbackEmployeeId attendances for month/year ...");
        if (employeeId == null && fallbackEmployeeId != null) {
            log.info("Query attendances by month={}, year={}, fallbackEmployeeId={}",
                    month, year, fallbackEmployeeId);

            List<Attendance> monthAttendances =
                    attendanceRepository.findAttendancesByMonthYearAndEmployee(month, year, fallbackEmployeeId);

            log.info("Attendances found for fallback: {}", monthAttendances != null ? monthAttendances.size() : null);

            if (monthAttendances != null && !monthAttendances.isEmpty()) {
                Attendance any = monthAttendances.get(0);
                if (any.getAssignment() != null && any.getAssignment().getEmployee() != null) {
                    Employee emp = any.getAssignment().getEmployee();
                    employeeId = emp.getId();
                    employeeName = emp.getName();
                    employeeCode = emp.getEmployeeCode();

                    salaryBase = salaryBase.add(any.getAssignment().getSalaryAtTime());
                    log.info("Employee found from fallback: id={}, name={}, code={}", employeeId, employeeName, employeeCode);
                    log.info("Salary base from fallback attendance: {}", salaryBase);
                } else {
                    log.info("Fallback attendance found nhưng thiếu assignment->employee.");
                }
            }
        }

        if (employeeId == null) {
            log.info("WARNING: Không thể tìm employee bằng bất kỳ phương pháp nào!");
        }

        log.info("Building PayrollResponse ...");

        PayrollResponse response = PayrollResponse.builder()
                .id(payroll.getId())
                .employeeId(employeeId)
                .employeeName(employeeName)
                .employeeCode(employeeCode)
                .salaryBase(salaryBase)
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

        log.info("=== mapToResponse END === PayRes: {}", response);

        return response;
    }

}
