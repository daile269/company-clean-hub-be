package com.company.company_clean_hub_be.service.impl;

// payroll request import removed (payroll calculation logic disabled)
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
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

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Optional<Payroll> payrollCheck =
                payrollRepository.findByEmployeeAndMonthAndYear(
                        request.getEmployeeId(), request.getMonth(), request.getYear());

        if (payrollCheck.isPresent()) {
            throw new AppException(ErrorCode.PAYROLL_ALREADY_EXISTS);
        }

        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        int totalDays = 0;

        BigDecimal advanceTotal = request.getAdvanceSalary() != null ? request.getAdvanceSalary() : BigDecimal.ZERO;
        BigDecimal insuranceTotal = request.getInsuranceAmount() != null ? request.getInsuranceAmount() : BigDecimal.ZERO;

        List<Assignment> assignments = assignmentRepository
                .findDistinctAssignmentsByAttendanceMonthAndEmployee(
                        request.getMonth(), request.getYear(), request.getEmployeeId(), null);

        if (assignments.isEmpty()) {
            throw new AppException(ErrorCode.NO_ASSIGNMENT_DATA);
        }

        List<Attendance> attendances = attendanceRepository.findAttendancesByMonthYearAndEmployee(
                request.getMonth(), request.getYear(), request.getEmployeeId());

        if (attendances.isEmpty()) {
            throw new AppException(ErrorCode.NO_ATTENDANCE_DATA);
        }

        for (Assignment assignment : assignments) {
            int realDays = calculateActualWorkDays(assignment);
            totalDays += realDays;

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            bonus = bonus != null ? bonus : BigDecimal.ZERO;

            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            penalty = penalty != null ? penalty : BigDecimal.ZERO;

            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            support = support != null ? support : BigDecimal.ZERO;

            BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null
                    ? assignment.getAdditionalAllowance()
                    : BigDecimal.ZERO;

            // Tổng bonus, penalty, support
            totalBonus = totalBonus.add(bonus);
            totalPenalties = totalPenalties.add(penalty);
            totalSupportCosts = totalSupportCosts.add(support).add(additionalAllowance);

            // Tính amount theo assignment (đúng theo hàm chuẩn calculateAssignmentAmount)
            BigDecimal assignmentAmount = calculateAssignmentAmount(assignment, bonus, support);
            amountTotal = amountTotal.add(assignmentAmount);
        }

        // Tính finalSalary giống hàm gốc
        BigDecimal finalSalary = amountTotal
                .subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

        User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        Payroll payroll = Payroll.builder()
                .employee(employee)
                .totalDays(totalDays)
                .bonusTotal(totalBonus)
                .penaltyTotal(totalPenalties)
                .advanceTotal(advanceTotal)
                .allowanceTotal(totalSupportCosts)
                .insuranceTotal(insuranceTotal)
                .finalSalary(finalSalary)
                .paymentDate(null)
                .isPaid(false)
                .accountant(accountant)
                .createdAt(LocalDateTime.of(request.getYear(), request.getMonth(), 1, 0, 0))
                .updatedAt(LocalDateTime.now())
                .build();

        Payroll savedPayroll = payrollRepository.save(payroll);

        for (Attendance att : attendances) {
            att.setPayroll(savedPayroll);
        }
        attendanceRepository.saveAll(attendances);

        return mapToResponse(savedPayroll, request.getMonth(), request.getYear(), request.getEmployeeId());
    }

    //    @Override
//    public List<PayRollExportExcel> getAllPayRoll(Integer month, Integer year) {
//        // This method is kept for backward compatibility but now delegates to the new method
//        List<PayRollAssignmentExportExcel> assignmentData = getAllPayRollByAssignment(month, year);
//
//        // Convert to old format for backward compatibility
//        List<PayRollExportExcel> result = new ArrayList<>();
//        for (PayRollAssignmentExportExcel assignment : assignmentData) {
//            if (assignment.getIsTotalRow() != null && assignment.getIsTotalRow()) {
//                // Skip total rows in old format
//                continue;
//            }
//            PayRollExportExcel dto = PayRollExportExcel.builder()
//                    .employeeId(assignment.getEmployeeId())
//                    .employeeName(assignment.getEmployeeName())
//                    .bankName(assignment.getBankName())
//                    .bankAccount(assignment.getBankAccount())
//                    .phone(assignment.getPhone())
//                    .employeeType(assignment.getAssignmentType())
//                    .projectCompanies(assignment.getProjectCompany() != null ? List.of(assignment.getProjectCompany()) : List.of())
//                    .totalDays(assignment.getAssignmentDays())
//                    .totalBonus(assignment.getAssignmentBonus())
//                    .totalPenalty(assignment.getAssignmentPenalty())
//                    .totalAllowance(assignment.getAssignmentAllowance())
//                    .totalInsurance(assignment.getAssignmentInsurance())
//                    .totalAdvance(assignment.getAssignmentAdvance())
//                    .finalSalary(null) // Not available at assignment level
//                    .build();
//            result.add(dto);
//        }
//        return result;
//    }
    @Override
    public List<PayRollAssignmentExportExcel> getAllPayRollByAssignment(Integer month, Integer year) {

        log.info("[PAYROLL-EXPORT] ===== START getAllPayRollByAssignment(month={}, year={}) =====", month, year);

        List<Employee> employees = employeeRepository.findDistinctEmployeesByAssignmentMonthYear(month, year);
        log.info("[PAYROLL-EXPORT] Found {} employees with assignments in month/year",
                employees != null ? employees.size() : null);

        List<PayRollAssignmentExportExcel> result = new ArrayList<>();
        if (employees == null || employees.isEmpty()) {
            log.info("[PAYROLL-EXPORT] No employees found. RETURN empty list.");
            return result;
        }

        User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        for (Employee employee : employees) {
            Long employeeId = employee.getId();
            log.info("[PAYROLL-EXPORT] --- PROCESS EMPLOYEE id={}, name={} ---",
                    employeeId, employee.getName());

            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId,null);
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

            // Calculate totals for all assignments
            int totalDays = 0;
            BigDecimal totalBonus =  persistedPayroll.getBonusTotal() != null ? persistedPayroll.getBonusTotal() : BigDecimal.ZERO;
            BigDecimal totalPenalty = persistedPayroll.getPenaltyTotal() != null ? persistedPayroll.getPenaltyTotal() : BigDecimal.ZERO;
            BigDecimal totalAllowance = persistedPayroll.getAllowanceTotal() != null ? persistedPayroll.getAllowanceTotal() : BigDecimal.ZERO;
            BigDecimal totalInsurance = persistedPayroll.getInsuranceTotal() != null ? persistedPayroll.getInsuranceTotal() : BigDecimal.ZERO;
            BigDecimal totalAdvance = persistedPayroll.getAdvanceTotal() != null ? persistedPayroll.getAdvanceTotal() : BigDecimal.ZERO;

            // Create one row per assignment
            for (Assignment assignment : assignments) {
                // Calculate assignment-specific totals
                int assignmentDays = calculateActualWorkDays(assignment);
                BigDecimal assignmentBonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
                assignmentBonus = assignmentBonus != null ? assignmentBonus : BigDecimal.ZERO;
                BigDecimal assignmentPenalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
                assignmentPenalty = assignmentPenalty != null ? assignmentPenalty : BigDecimal.ZERO;
                BigDecimal assignmentSupport = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
                assignmentSupport = assignmentSupport != null ? assignmentSupport : BigDecimal.ZERO;
                BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null  ? assignment.getAdditionalAllowance()  : BigDecimal.ZERO;
                BigDecimal assignmentAllowance = assignmentSupport.add(additionalAllowance);

                // Get project company
                String projectCompany = assignment.getContract().getCustomer() != null ? assignment.getContract().getCustomer().getCompany() : null;

                // Map assignment type to Vietnamese
                String assignmentTypeVN = mapAssignmentTypeToVietnamese(assignment.getAssignmentType());

                PayRollAssignmentExportExcel dto = PayRollAssignmentExportExcel.builder()
                        .employeeId(employeeId)
                        .employeeName(employee.getName())
                        .bankName(employee.getBankName())
                        .bankAccount(employee.getBankAccount())
                        .phone(employee.getPhone())
                        .assignmentType(assignmentTypeVN)
                        .baseSalary(assignment.getSalaryAtTime())
                        .projectCompany(projectCompany)
                        .assignmentDays(assignmentDays)
                        .assignmentBonus(assignmentBonus)
                        .assignmentPenalty(assignmentPenalty)
                        .assignmentAllowance(assignmentAllowance)
                        .assignmentInsurance(BigDecimal.ZERO) // Insurance is at employee level, not assignment level
                        .assignmentAdvance(BigDecimal.ZERO) // Advance is at employee level, not assignment level
                        .totalDays(null) // Will be set in total row
                        .totalBonus(null)
                        .totalPenalty(null)
                        .totalAllowance(null)
                        .totalInsurance(null)
                        .totalAdvance(null)
                        .finalSalary(null)
                        .isTotalRow(false)
                        .build();

                result.add(dto);
                
                // Accumulate totals
                totalDays += assignmentDays;
                totalBonus = totalBonus.add(assignmentBonus);
                totalPenalty = totalPenalty.add(assignmentPenalty);
                totalAllowance = totalAllowance.add(assignmentAllowance);
                
                log.info("[PAYROLL-EXPORT] Added assignment row for employee {} assignment {}", employeeId, assignment.getId());
            }

            // Add total row for this employee
            PayRollAssignmentExportExcel totalRow = PayRollAssignmentExportExcel.builder()
                    .employeeId(employeeId)
                    .employeeName(employee.getName())
                    .bankName(employee.getBankName())
                    .bankAccount(employee.getBankAccount())
                    .phone(employee.getPhone())
                    .assignmentType(null) // Empty for total row
                    .baseSalary(null)
                    .projectCompany(null) // Empty for total row
                    .assignmentDays(null) // Empty for total row
                    .assignmentBonus(null)
                    .assignmentPenalty(null)
                    .assignmentAllowance(null)
                    .assignmentInsurance(null)
                    .assignmentAdvance(null)
                    .totalDays(totalDays)
                    .totalBonus(totalBonus)
                    .totalPenalty(totalPenalty)
                    .totalAllowance(totalAllowance)
                    .totalInsurance(totalInsurance)
                    .totalAdvance(totalAdvance)
                    .finalSalary(persistedPayroll.getFinalSalary())
                    .isTotalRow(true)
                    .build();

            result.add(totalRow);
            log.info("[PAYROLL-EXPORT] Added total row for employee {}", employeeId);
        }

        log.info("[PAYROLL-EXPORT] ===== FINISHED getAllPayRollByAssignment. Total rows={} =====",
                result.size());

        return result;
    }
    
    private String mapAssignmentTypeToVietnamese(AssignmentType assignmentType) {
        if (assignmentType == null) {
            return "";
        }
        switch (assignmentType) {
            case FIXED_BY_CONTRACT:
                return "Cố định theo tháng";
            case FIXED_BY_DAY:
                return "Cố định theo ngày";
            case TEMPORARY:
                return "Điều động";
            default:
                return "";
        }
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
        BigDecimal totalAdvance   = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        int totalDays = 0;
        Payroll payroll = payrollRepository
                .findByEmployeeAndMonthAndYear(employee.getId(), month, year)
                .orElseGet(Payroll::new);
        for (Assignment assignment : assignments) {
            totalDays += calculateActualWorkDays(assignment);

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
            amountTotal = amountTotal.add(calculateAssignmentAmount(assignment,bonus,support));
        }

        payroll.setAccountant(accountant);
        payroll.setCreatedAt(LocalDateTime.of(year, month, 1, 0, 0));
        payroll.setUpdatedAt(LocalDateTime.now());
        payroll.setIsPaid(false);
        payroll.setPaymentDate(null);

        BigDecimal advanceTotal = payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO;
        BigDecimal insuranceTotal = payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO;

        amountTotal = amountTotal.subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

        payroll.setBonusTotal(totalBonus);
        payroll.setTotalDays(totalDays);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(totalSupportCosts);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(amountTotal);
        payroll.setEmployee(employee);

        Payroll savedPayroll = payrollRepository.save(payroll);

        for (Attendance attendance : attendances) {
            attendance.setPayroll(savedPayroll);
        }
        attendanceRepository.saveAll(attendances);

        return savedPayroll;
    }
    private int calculateActualWorkDays(Assignment assignment) {
        LocalDate today = LocalDate.now();
        return (int) assignment.getAttendances().stream()
                .filter(a -> a.getDate() != null && !a.getDate().isAfter(today))
                .count();
    }
    private BigDecimal calculateAssignmentAmount(Assignment assignment, BigDecimal bonus,BigDecimal supportCosts) {
        if (assignment == null || assignment.getAssignmentType() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = BigDecimal.ZERO;
        AssignmentType type = assignment.getAssignmentType();
        int realWorksDay = calculateActualWorkDays(assignment);
        if (type == AssignmentType.FIXED_BY_CONTRACT) {
            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {
                BigDecimal dailyRate = (assignment.getSalaryAtTime().add(bonus).add(supportCosts)).divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP);
                amount = dailyRate.multiply(BigDecimal.valueOf(realWorksDay));
            }
        } else {
            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && assignment.getSalaryAtTime() != null && assignment.getWorkDays() != null) {

                BigDecimal salary = (assignment.getSalaryAtTime().multiply(BigDecimal.valueOf(realWorksDay)));
                amount = amount.add(salary.add(supportCosts).add(bonus));
            }
        }
        return amount;
    }


    @Override
    public PayrollResponse getPayrollById(Long id) {
        log.info("getPayrollById requested: id={}", id);
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));
        
        LocalDateTime createdAt = payroll.getCreatedAt();
                PayrollResponse resp = mapToResponse(payroll, createdAt.getMonthValue(), createdAt.getYear(), null);
                log.info("getPayrollById completed: id={}, employeeId={}", id, resp.getEmployeeId());
                return resp;
    }

    @Override
    public List<PayrollResponse> getAllPayrolls() {
                log.info("getAllPayrolls requested");
                List<PayrollResponse> result = payrollRepository.findAll().stream()
                                .map(p -> {
                                        LocalDateTime createdAt = p.getCreatedAt();
                                        return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear(), null);
                                })
                                .collect(Collectors.toList());
                log.info("getAllPayrolls completed: count={}", result.size());
                return result;
    }

    @Override
    public PageResponse<PayrollResponse> getPayrollsWithFilter(String keyword, Integer month, Integer year, Boolean isPaid, int page, int pageSize) {
                log.info("getPayrollsWithFilter requested: keyword='{}', month={}, year={}, isPaid={}, page={}, pageSize={}", keyword, month, year, isPaid, page, pageSize);
                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Payroll> payrollPage = payrollRepository.findByFilters(keyword, month, year, isPaid, pageable);

                List<PayrollResponse> payrolls = payrollPage.getContent().stream()
                .map(p -> {
                    LocalDateTime createdAt = p.getCreatedAt();
                    return mapToResponse(p, createdAt.getMonthValue(), createdAt.getYear(), null);
                })
                .collect(Collectors.toList());

                log.info("getPayrollsWithFilter completed: returned={}, totalElements={}", payrolls.size(), payrollPage.getTotalElements());

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
                log.info("updatePaymentStatus requested: id={}, isPaid={}", id, isPaid);
        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));
        
        payroll.setIsPaid(isPaid);
        if (isPaid) {
            payroll.setPaymentDate(LocalDateTime.now());
        }
        payroll.setUpdatedAt(LocalDateTime.now());
        
        Payroll updatedPayroll = payrollRepository.save(payroll);
                log.info("updatePaymentStatus completed: id={}, isPaid={}", id, isPaid);
        LocalDateTime createdAt = updatedPayroll.getCreatedAt();
        return mapToResponse(updatedPayroll, createdAt.getMonthValue(), createdAt.getYear(), null);
    }

    @Override
    public PayrollResponse updatePayroll(Long id, PayrollUpdateRequest request) {

        log.info("[PAYROLL-DEBUG] ===== START updatePayroll(id={}) =====", id);

        Payroll payroll = payrollRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PAYROLL_NOT_FOUND));

        LocalDateTime createdAt = payroll.getCreatedAt();
        Integer month = createdAt != null ? createdAt.getMonthValue() : null;
        Integer year = createdAt != null ? createdAt.getYear() : null;

        Long employeeId = payroll.getEmployee() != null ? payroll.getEmployee().getId() : null;
        if (employeeId == null) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }

        // ===== RESET AMOUNTS =====
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        int totalDays = 0;

        List<Assignment> assignments =
                assignmentRepository.findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);

        for (Assignment assignment : assignments) {

            int realDays = calculateActualWorkDays(assignment);
            totalDays += realDays;

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            bonus = bonus != null ? bonus : BigDecimal.ZERO;

            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            penalty = penalty != null ? penalty : BigDecimal.ZERO;

            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            support = support != null ? support : BigDecimal.ZERO;

            BigDecimal additionalAllowance =
                    assignment.getAdditionalAllowance() != null ? assignment.getAdditionalAllowance() : BigDecimal.ZERO;

            // ==== TÍNH ĐÚNG THEO LOGIC CHUẨN ====
            BigDecimal assignmentAmount = calculateAssignmentAmount(assignment, bonus, support);
            amountTotal = amountTotal.add(assignmentAmount);

            // ==== SUM ====
            totalBonus = totalBonus.add(bonus);
            totalPenalties = totalPenalties.add(penalty);
            totalSupportCosts = totalSupportCosts.add(support).add(additionalAllowance);
        }

        // ===== ÁP DỤNG GIÁ TRỊ REQUEST =====
        BigDecimal insuranceTotal = request.getInsuranceTotal() != null
                ? request.getInsuranceTotal()
                : (payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO);

        BigDecimal advanceTotal = request.getAdvanceTotal() != null
                ? request.getAdvanceTotal()
                : (payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO);

        // ===== FINAL SALARY THEO LOGIC CHUẨN =====
        BigDecimal finalSalary = amountTotal
                .subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

        payroll.setTotalDays(totalDays);
        payroll.setBonusTotal(totalBonus);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(totalSupportCosts);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(finalSalary);
        payroll.setUpdatedAt(LocalDateTime.now());

        Payroll updated = payrollRepository.save(payroll);

        log.info("[PAYROLL-DEBUG] ===== END updatePayroll(id={}) =====", id);

        return mapToResponse(updated, month, year, employeeId);
    }


    @Override
    public void deletePayroll(Long id) {
                log.info("deletePayroll requested: id={}", id);
        if (!payrollRepository.existsById(id)) {
            throw new AppException(ErrorCode.PAYROLL_NOT_FOUND);
        }
        payrollRepository.deleteById(id);
                log.info("deletePayroll completed: id={}", id);
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

        // Get employee directly from payroll (new architecture)
        log.info("Step 1: Check payroll.getEmployee() ...");
        if (payroll.getEmployee() != null) {
            Employee emp = payroll.getEmployee();
            employeeId = emp.getId();
            employeeName = emp.getName();
            employeeCode = emp.getEmployeeCode();
            log.info("Employee found from payroll.employee: id={}, name={}, code={}",
                    employeeId, employeeName, employeeCode);

            // Get salary base from first assignment if available
            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);
            if (!assignments.isEmpty()) {
                salaryBase = assignments.get(0).getSalaryAtTime();
                log.info("Salary base from assignment: {}", salaryBase);
            }
        } else {
            log.info("WARNING: payroll.getEmployee() is null!");
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
