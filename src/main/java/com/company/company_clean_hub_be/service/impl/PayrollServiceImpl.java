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

        // Initial totals
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

        // Detect COMPANY scope
        boolean hasCompanyScope = assignments.stream()
                .anyMatch(a -> a.getScope() != null && a.getScope() == AssignmentScope.COMPANY);

        for (Assignment assignment : assignments) {
            int realDays = calculateActualWorkDays(assignment);
            totalDays += realDays;

            BigDecimal bonus = defaultZero(attendanceRepository.sumBonusByAssignment(assignment.getId()));
            BigDecimal penalty = defaultZero(attendanceRepository.sumPenaltyByAssignment(assignment.getId()));
            BigDecimal support = defaultZero(attendanceRepository.sumSupportCostByAssignment(assignment.getId()));
            BigDecimal additionalAllowance = defaultZero(assignment.getAdditionalAllowance());

            totalBonus = totalBonus.add(bonus);
            totalPenalties = totalPenalties.add(penalty);
            totalSupportCosts = totalSupportCosts.add(support).add(additionalAllowance);

            // New unified logic (Company + Contract)
            BigDecimal assignmentAmount = calculateAssignmentAmount(assignment, bonus, support.add(additionalAllowance));
            amountTotal = amountTotal.add(assignmentAmount);
        }

        // If COMPANY: add employee allowance to final payroll (not per assignment)
        if (hasCompanyScope && employee.getAllowance() != null) {
            totalSupportCosts = totalSupportCosts.add(employee.getAllowance());
        }

        // Insurance salary
        if (hasCompanyScope && employee.getInsuranceSalary() != null) {
            insuranceTotal = employee.getInsuranceSalary();
        } else {
            insuranceTotal = request.getInsuranceAmount() != null ? request.getInsuranceAmount() : BigDecimal.ZERO;
        }

        // Final salary (same as upsert)
        BigDecimal finalSalary = amountTotal
                .subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

        if (hasCompanyScope && employee.getAllowance() != null) {
            finalSalary = finalSalary.add(employee.getAllowance());
        }

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
        log.debug("[PAYROLL-EXPORT][DEBUG] Input params -> month={}, year={}", month, year);

        List<Employee> employees = employeeRepository.findDistinctEmployeesByAssignmentMonthYear(month, year);
        log.info("[PAYROLL-EXPORT] Found {} employees with assignments in month/year",
                employees != null ? employees.size() : null);
        log.debug("[PAYROLL-EXPORT][DEBUG] Raw employee list: {}", employees);

        List<PayRollAssignmentExportExcel> result = new ArrayList<>();
        if (employees == null || employees.isEmpty()) {
            log.info("[PAYROLL-EXPORT] No employees found. RETURN empty list.");
            log.debug("[PAYROLL-EXPORT][DEBUG] employees is null or empty. employees={}", employees);
            return result;
        }

        User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        log.debug("[PAYROLL-EXPORT][DEBUG] Accountant executing payroll export: {}", accountant);

        for (Employee employee : employees) {
            Long employeeId = employee.getId();
            log.info("[PAYROLL-EXPORT] --- PROCESS EMPLOYEE id={}, name={} ---",
                    employeeId, employee.getName());
            log.debug("[PAYROLL-EXPORT][DEBUG] Employee full object: {}", employee);

            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);

            log.info("[PAYROLL-EXPORT] Employee {} has {} assignments",
                    employeeId, assignments != null ? assignments.size() : null);
            log.debug("[PAYROLL-EXPORT][DEBUG] Raw assignment list for employee {}: {}",
                    employeeId, assignments);

            if (assignments == null || assignments.isEmpty()) {
                log.info("[PAYROLL-EXPORT] Employee {} has NO assignments. Skip.", employeeId);
                log.debug("[PAYROLL-EXPORT][DEBUG] Skipping employee {} because assignments is null or empty", employeeId);
                continue;
            }

            Payroll persistedPayroll = upsertPayrollFromAssignments(employee, assignments, month, year, accountant);
            log.debug("[PAYROLL-EXPORT][DEBUG] Payroll persisted for employee {} -> {}",
                    employeeId, persistedPayroll);

            if (persistedPayroll == null) {
                log.info("[PAYROLL-EXPORT] Employee {} skipped because no attendance was found.", employeeId);
                log.debug("[PAYROLL-EXPORT][DEBUG] persistedPayroll is NULL -> skip employee {}", employeeId);
                continue;
            }

            int totalPlanedDay = 0;
            int totalDays = 0;
            BigDecimal totalBonus = persistedPayroll.getBonusTotal() != null ? persistedPayroll.getBonusTotal() : BigDecimal.ZERO;
            BigDecimal totalPenalty = persistedPayroll.getPenaltyTotal() != null ? persistedPayroll.getPenaltyTotal() : BigDecimal.ZERO;
            BigDecimal totalAllowance = persistedPayroll.getAllowanceTotal() != null ? persistedPayroll.getAllowanceTotal() : BigDecimal.ZERO;
            BigDecimal totalInsurance = persistedPayroll.getInsuranceTotal() != null ? persistedPayroll.getInsuranceTotal() : BigDecimal.ZERO;
            BigDecimal totalAdvance = persistedPayroll.getAdvanceTotal() != null ? persistedPayroll.getAdvanceTotal() : BigDecimal.ZERO;

            log.debug("[PAYROLL-EXPORT][DEBUG] Initial totals for employee {} -> bonus={}, penalty={}, allowance={}, insurance={}, advance={}",
                    employeeId, totalBonus, totalPenalty, totalAllowance, totalInsurance, totalAdvance);

            for (Assignment assignment : assignments) {

                log.debug("[PAYROLL-EXPORT][DEBUG] --- PROCESS ASSIGNMENT {} of employee {} ---", assignment.getId(), employeeId);
                log.debug("[PAYROLL-EXPORT][DEBUG] Assignment full object: {}", assignment);

                int assignmentDays = calculateActualWorkDays(assignment);
                log.debug("[PAYROLL-EXPORT][DEBUG] assignmentDays={}, plannedDays={}",
                        assignmentDays, assignment.getPlannedDays());

                BigDecimal assignmentBonus = defaultZero(attendanceRepository.sumBonusByAssignment(assignment.getId()));
                BigDecimal assignmentPenalty = defaultZero(attendanceRepository.sumPenaltyByAssignment(assignment.getId()));
                BigDecimal assignmentSupport = defaultZero(attendanceRepository.sumSupportCostByAssignment(assignment.getId()));
                BigDecimal additionalAllowance = defaultZero(assignment.getAdditionalAllowance());

                log.debug("[PAYROLL-EXPORT][DEBUG] assignmentBonus={}, assignmentPenalty={}, assignmentSupport={}, additionalAllowance={}",
                        assignmentBonus, assignmentPenalty, assignmentSupport, additionalAllowance);

                AssignmentScope scope = assignment.getScope() != null ? assignment.getScope() : AssignmentScope.CONTRACT;
                log.debug("[PAYROLL-EXPORT][DEBUG] Assignment scope={}", scope);

                BigDecimal assignmentAllowance = assignmentSupport.add(additionalAllowance);

//                if (scope == AssignmentScope.COMPANY && employee.getAllowance() != null) {
//                    assignmentAllowance = assignmentAllowance.add(employee.getAllowance());
//                }

                log.debug("[PAYROLL-EXPORT][DEBUG] final assignmentAllowance={}", assignmentAllowance);

                BigDecimal baseSalary = (scope == AssignmentScope.COMPANY && employee.getMonthlySalary() != null)
                        ? employee.getMonthlySalary()
                        : assignment.getSalaryAtTime();

                log.debug("[PAYROLL-EXPORT][DEBUG] baseSalary resolved = {}", baseSalary);
                String projectCompany = "";
                if (assignment.getContract() != null) {
                    projectCompany = assignment.getContract().getCustomer() != null
                            ? assignment.getContract().getCustomer().getCompany()
                            : null;
                } else {
                    projectCompany = "Văn phòng";
                }

                log.debug("[PAYROLL-EXPORT][DEBUG] projectCompany resolved = {}", projectCompany);

                String assignmentTypeVN = mapAssignmentTypeToVietnamese(assignment.getAssignmentType());
                log.debug("[PAYROLL-EXPORT][DEBUG] assignmentTypeVN={}", assignmentTypeVN);

                BigDecimal assignmentSalary = calculateAssignmentAmount(assignment, assignmentBonus, assignmentSupport);
                log.debug("[PAYROLL-EXPORT][DEBUG] assignmentSalary calculated = {}", assignmentSalary);

                PayRollAssignmentExportExcel dto = PayRollAssignmentExportExcel.builder()
                        .employeeId(employeeId)
                        .employeeName(employee.getName())
                        .bankName(employee.getBankName())
                        .bankAccount(employee.getBankAccount())
                        .phone(employee.getPhone())
                        .assignmentType(assignmentTypeVN)
                        .baseSalary(baseSalary)
                        .projectCompany(projectCompany)
                        .assignmentDays(assignmentDays)
                        .assignmentPlanedDays(assignment.getPlannedDays())
                        .assignmentBonus(assignmentBonus)
                        .assignmentPenalty(assignmentPenalty)
                        .assignmentAllowance(assignmentAllowance)
                        .assignmentInsurance(BigDecimal.ZERO)
                        .assignmentAdvance(BigDecimal.ZERO)
                        .assignmentSalary(assignmentSalary)
                        .totalDays(null)
                        .totalPlanedDays(null)
                        .totalBonus(null)
                        .totalPenalty(null)
                        .totalAllowance(null)
                        .totalInsurance(null)
                        .totalAdvance(null)
                        .finalSalary(null)
                        .companyAllowance(null)
                        .isTotalRow(false)
                        .build();

                log.debug("[PAYROLL-EXPORT][DEBUG] DTO created: {}", dto);

                result.add(dto);

                totalDays += assignmentDays;
                totalPlanedDay += assignment.getPlannedDays();
                totalBonus = totalBonus.add(assignmentBonus);
                totalPenalty = totalPenalty.add(assignmentPenalty);
//                totalAllowance = totalAllowance.add(assignmentAllowance);

                log.debug("[PAYROLL-EXPORT][DEBUG] Updated totals -> days={}, planned={}, bonus={}, penalty={}, allowance={}",
                        totalDays, totalPlanedDay, totalBonus, totalPenalty, totalAllowance);

                log.info("[PAYROLL-EXPORT] Added assignment row for employee {} assignment {}", employeeId, assignment.getId());
            }

            PayRollAssignmentExportExcel totalRow = PayRollAssignmentExportExcel.builder()
                    .employeeId(employeeId)
                    .employeeName(employee.getName())
                    .bankName(employee.getBankName())
                    .bankAccount(employee.getBankAccount())
                    .phone(employee.getPhone())
                    .assignmentType(null)
                    .baseSalary(null)
                    .projectCompany(null)
                    .assignmentDays(null)
                    .assignmentPlanedDays(null)
                    .assignmentBonus(null)
                    .assignmentPenalty(null)
                    .assignmentAllowance(null)
                    .assignmentInsurance(null)
                    .assignmentAdvance(null)
                    .assignmentSalary(null)
                    .totalDays(totalDays)
                    .totalPlanedDays(totalPlanedDay)
                    .totalBonus(totalBonus)
                    .totalPenalty(totalPenalty)
                    .totalAllowance(totalAllowance)
                    .totalInsurance(totalInsurance)
                    .companyAllowance(employee.getAllowance())
                    .totalAdvance(totalAdvance)
                    .finalSalary(persistedPayroll.getFinalSalary())
                    .isTotalRow(true)
                    .build();

            log.debug("[PAYROLL-EXPORT][DEBUG] Total row created for employee {}: {}", employeeId, totalRow);

            result.add(totalRow);
            log.info("[PAYROLL-EXPORT] Added total row for employee {}", employeeId);
        }

        log.info("[PAYROLL-EXPORT] ===== FINISHED getAllPayRollByAssignment. Total rows={} =====",
                result.size());
        log.debug("[PAYROLL-EXPORT][DEBUG] Final result list: {}", result);

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
            case FIXED_BY_COMPANY:
                return "Nhân viên văn phòng";
            default:
                return "";
        }
    }

    private Payroll upsertPayrollFromAssignments(Employee employee,
                                                 List<Assignment> assignments,
                                                 Integer month,
                                                 Integer year,
                                                 User accountant) {
        log.debug("[PAYROLL-EXPORT][DEBUG] Enter upsertPayrollFromAssignments - employeeId={}, month={}, year={}, accountant={}",
                employee != null ? employee.getId() : null, month, year, accountant != null ? accountant.getUsername() : null);

        if (month == null || year == null) {
            log.error("[PAYROLL-EXPORT] Month/Year is required. month={}, year={}", month, year);
            log.debug("[PAYROLL-EXPORT][DEBUG] EXIT upsertPayrollFromAssignments - invalid month/year");
            return null;
        }

        List<Attendance> attendances = attendanceRepository
                .findAttendancesByMonthYearAndEmployee(month, year, employee.getId());
        log.debug("[PAYROLL-EXPORT][DEBUG] Retrieved attendances for employeeId={} count={}", employee.getId(),
                attendances != null ? attendances.size() : null);
        log.trace("[PAYROLL-EXPORT][TRACE] Attendances raw list: {}", attendances);

        if (attendances == null || attendances.isEmpty()) {
            log.info("[PAYROLL-EXPORT] Employee {} has NO attendance. Skip payroll.", employee.getId());
            log.debug("[PAYROLL-EXPORT][DEBUG] EXIT upsertPayrollFromAssignments - no attendances for employeeId={}", employee.getId());
            return null;
        }

        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        BigDecimal totalAdvance   = BigDecimal.ZERO;
        BigDecimal totalInsurance = BigDecimal.ZERO;
        int totalDays = 0;

        log.debug("[PAYROLL-EXPORT][DEBUG] Initial accumulators -> amountTotal={}, totalBonus={}, totalPenalties={}, totalSupportCosts={}, totalAdvance={}, totalInsurance={}, totalDays={}",
                amountTotal, totalBonus, totalPenalties, totalSupportCosts, totalAdvance, totalInsurance, totalDays);

        boolean hasCompanyScope = assignments.stream()
                .anyMatch(a -> a.getScope() != null && a.getScope() == AssignmentScope.COMPANY);
        log.debug("[PAYROLL-EXPORT][DEBUG] hasCompanyScope={} for employeeId={}", hasCompanyScope, employee.getId());
        log.trace("[PAYROLL-EXPORT][TRACE] Assignments raw list: {}", assignments);

        Payroll payroll = payrollRepository
                .findByEmployeeAndMonthAndYear(employee.getId(), month, year)
                .orElseGet(Payroll::new);
        log.debug("[PAYROLL-EXPORT][DEBUG] Loaded existing payroll? {} (id={})",
                payroll.getId() != null, payroll.getId());

        for (Assignment assignment : assignments) {
            log.debug("[PAYROLL-EXPORT][DEBUG] Processing assignment id={} for employeeId={}",
                    assignment != null ? assignment.getId() : null, employee.getId());

            int assignmentRealDays = calculateActualWorkDays(assignment);
            totalDays += assignmentRealDays;
            log.debug("[PAYROLL-EXPORT][DEBUG] assignmentRealDays={}, cumulative totalDays={}", assignmentRealDays, totalDays);

            BigDecimal bonus = attendanceRepository.sumBonusByAssignment(assignment.getId());
            BigDecimal penalty = attendanceRepository.sumPenaltyByAssignment(assignment.getId());
            BigDecimal support = attendanceRepository.sumSupportCostByAssignment(assignment.getId());
            BigDecimal additionalAllowance = assignment.getAdditionalAllowance() != null
                    ? assignment.getAdditionalAllowance()
                    : BigDecimal.ZERO;

            log.debug("[PAYROLL-EXPORT][DEBUG] Raw sums for assignment {} -> bonus={}, penalty={}, support={}, additionalAllowance={}",
                    assignment.getId(), bonus, penalty, support, additionalAllowance);

            BigDecimal safeBonus = bonus != null ? bonus : BigDecimal.ZERO;
            BigDecimal safePenalty = penalty != null ? penalty : BigDecimal.ZERO;
            BigDecimal safeSupport = support != null ? support : BigDecimal.ZERO;

            totalBonus = totalBonus.add(safeBonus);
            totalPenalties = totalPenalties.add(safePenalty);
            totalSupportCosts = totalSupportCosts
                    .add(safeSupport)
                    .add(additionalAllowance);
            BigDecimal suportAssignment = safeSupport.add(additionalAllowance);
            log.debug("[PAYROLL-EXPORT][DEBUG] Updated accumulators after assignment {} -> totalBonus={}, totalPenalties={}, totalSupportCosts={}",
                    assignment.getId(), totalBonus, totalPenalties, totalSupportCosts);

            BigDecimal calculatedAssignmentAmount = calculateAssignmentAmount(assignment, safeBonus, suportAssignment);
            log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount returned {} for assignmentId={}",
                    calculatedAssignmentAmount, assignment.getId());
            amountTotal = amountTotal.add(calculatedAssignmentAmount);
            log.debug("[PAYROLL-EXPORT][DEBUG] amountTotal accumulated = {}", amountTotal);
        }


        payroll.setAccountant(accountant);
        payroll.setCreatedAt(LocalDateTime.of(year, month, 1, 0, 0));
        payroll.setUpdatedAt(LocalDateTime.now());
        payroll.setIsPaid(false);
        payroll.setPaymentDate(null);

        BigDecimal advanceTotal = payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO;
        log.debug("[PAYROLL-EXPORT][DEBUG] payroll.advanceTotal (existing) = {}", advanceTotal);

        BigDecimal insuranceTotal;
        if (hasCompanyScope && employee.getInsuranceSalary() != null) {
            insuranceTotal = employee.getInsuranceSalary();
            log.debug("[PAYROLL-EXPORT][DEBUG] Using employee.insuranceSalary for insuranceTotal = {}", insuranceTotal);
        } else {
            insuranceTotal = payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO;
            log.debug("[PAYROLL-EXPORT][DEBUG] Using payroll.insuranceTotal (existing) = {}", insuranceTotal);
        }
        if (hasCompanyScope){

        }
        // Subtract penalties, insurance and advances from total amount
        BigDecimal deductions = totalPenalties.add(insuranceTotal).add(advanceTotal);
        log.debug("[PAYROLL-EXPORT][DEBUG] Deductions calculated -> totalPenalties={}, insuranceTotal={}, advanceTotal={}, deductions={}",
                totalPenalties, insuranceTotal, advanceTotal, deductions);

        BigDecimal finalAmount = amountTotal.subtract(deductions);
        log.debug("[PAYROLL-EXPORT][DEBUG] Final salary computed before persisting = {} (amountTotal={} - deductions={})",
                finalAmount, amountTotal, deductions);
        if (hasCompanyScope){
            finalAmount = finalAmount.add(employee.getAllowance());
        }
        payroll.setBonusTotal(totalBonus);
        payroll.setTotalDays(totalDays);
        payroll.setPenaltyTotal(totalPenalties);
        payroll.setAdvanceTotal(advanceTotal);
        payroll.setAllowanceTotal(totalSupportCosts);
        payroll.setInsuranceTotal(insuranceTotal);
        payroll.setFinalSalary(finalAmount);
        payroll.setEmployee(employee);

        log.debug("[PAYROLL-EXPORT][DEBUG] Payroll entity before save: {}", payroll);

        Payroll savedPayroll = payrollRepository.save(payroll);
        log.info("[PAYROLL-EXPORT] Payroll saved for employeeId={} payrollId={}", employee.getId(), savedPayroll.getId());
        log.debug("[PAYROLL-EXPORT][DEBUG] Saved payroll details: {}", savedPayroll);

        // Link attendances to saved payroll
        for (Attendance attendance : attendances) {
            attendance.setPayroll(savedPayroll);
            log.trace("[PAYROLL-EXPORT][TRACE] Linking attendance id={} to payroll id={}",
                    attendance != null ? attendance.getId() : null, savedPayroll.getId());
        }
        attendanceRepository.saveAll(attendances);
        log.debug("[PAYROLL-EXPORT][DEBUG] Saved {} attendances with payrollId={}", attendances.size(), savedPayroll.getId());

        return savedPayroll;
    }

    private int calculateActualWorkDays(Assignment assignment) {
        LocalDate today = LocalDate.now();
        log.debug("[PAYROLL-EXPORT][DEBUG] calculateActualWorkDays for assignmentId={} today={}",
                assignment != null ? assignment.getId() : null, today);

        long count = assignment.getAttendances().stream()
                .filter(a -> a.getDate() != null && !a.getDate().isAfter(today))
                .count();

        log.debug("[PAYROLL-EXPORT][DEBUG] Actual work days counted={} for assignmentId={}", count, assignment.getId());
        return (int) count;
    }

    private BigDecimal calculateAssignmentAmount(Assignment assignment, BigDecimal bonus, BigDecimal supportCosts) {
        log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount start - assignmentId={}, bonus={}, supportCosts={}",
                assignment != null ? assignment.getId() : null, bonus, supportCosts);

        if (assignment == null || assignment.getAssignmentType() == null) {
            log.debug("[PAYROLL-EXPORT][DEBUG] assignment or assignmentType is null -> return ZERO");
            return BigDecimal.ZERO;
        }

        BigDecimal amount = BigDecimal.ZERO;
        AssignmentType type = assignment.getAssignmentType();
        AssignmentScope scope = assignment.getScope() != null ? assignment.getScope() : AssignmentScope.CONTRACT;
        int realWorksDay = calculateActualWorkDays(assignment);

        log.debug("[PAYROLL-EXPORT][DEBUG] type={}, scope={}, realWorksDay={}", type, scope, realWorksDay);

        BigDecimal salaryBase;
        if (scope == AssignmentScope.COMPANY) {
            salaryBase = assignment.getEmployee() != null && assignment.getEmployee().getMonthlySalary() != null
                    ? assignment.getEmployee().getMonthlySalary()
                    : BigDecimal.ZERO;
            log.debug("[PAYROLL-EXPORT][DEBUG] Scope COMPANY -> salaryBase (employee.monthlySalary) = {}", salaryBase);
        } else {
            salaryBase = assignment.getSalaryAtTime() != null ? assignment.getSalaryAtTime() : BigDecimal.ZERO;
            log.debug("[PAYROLL-EXPORT][DEBUG] Scope CONTRACT -> salaryBase (assignment.salaryAtTime) = {}", salaryBase);
        }

        if (type == AssignmentType.FIXED_BY_CONTRACT || type == AssignmentType.FIXED_BY_COMPANY) {
            log.debug("[PAYROLL-EXPORT][DEBUG] Fixed type branch. plannedDays={}, salaryBase={}, bonus={}, supportCosts={}, workDaysField={}",
                    assignment.getPlannedDays(), salaryBase, bonus, supportCosts, assignment.getWorkDays());

            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && salaryBase.compareTo(BigDecimal.ZERO) > 0 && assignment.getWorkDays() != null) {
                BigDecimal dailyRate = (salaryBase.add(defaultZero(bonus)).add(defaultZero(supportCosts)))
                        .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP);
                amount = dailyRate.multiply(BigDecimal.valueOf(realWorksDay));
                log.debug("[PAYROLL-EXPORT][DEBUG] Fixed amount computed: dailyRate={}, amount={}", dailyRate, amount);
            } else {
                log.debug("[PAYROLL-EXPORT][DEBUG] Fixed branch conditions not met -> amount stays ZERO");
            }
        } else {
            log.debug("[PAYROLL-EXPORT][DEBUG] Day/Temporary type branch. plannedDays={}, salaryBase={}, bonus={}, supportCosts={}, workDaysField={}",
                    assignment.getPlannedDays(), salaryBase, bonus, supportCosts, assignment.getWorkDays());

            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && salaryBase.compareTo(BigDecimal.ZERO) > 0 && assignment.getWorkDays() != null) {

                BigDecimal salary = (salaryBase.multiply(BigDecimal.valueOf(realWorksDay)));
                amount = amount.add(salary.add(defaultZero(supportCosts)).add(defaultZero(bonus)));
                log.debug("[PAYROLL-EXPORT][DEBUG] Daily/Temporary amount computed: salary={}, amount={}", salary, amount);
            } else {
                log.debug("[PAYROLL-EXPORT][DEBUG] Day/Temporary branch conditions not met -> amount stays ZERO");
            }
        }

        log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount returning amount={}", amount);
        return amount;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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

        Employee employee = payroll.getEmployee();

        // ===== RESET TOTALS =====
        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalPenalties = BigDecimal.ZERO;
        BigDecimal totalSupportCosts = BigDecimal.ZERO;
        int totalDays = 0;

        List<Assignment> assignments =
                assignmentRepository.findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);

        if (assignments.isEmpty()) {
            throw new AppException(ErrorCode.NO_ASSIGNMENT_DATA);
        }

        // Check COMPANY scope
        boolean hasCompanyScope = assignments.stream()
                .anyMatch(a -> a.getScope() != null && a.getScope() == AssignmentScope.COMPANY);

        for (Assignment assignment : assignments) {

            int realDays = calculateActualWorkDays(assignment);
            totalDays += realDays;

            BigDecimal bonus = defaultZero(attendanceRepository.sumBonusByAssignment(assignment.getId()));
            BigDecimal penalty = defaultZero(attendanceRepository.sumPenaltyByAssignment(assignment.getId()));
            BigDecimal support = defaultZero(attendanceRepository.sumSupportCostByAssignment(assignment.getId()));
            BigDecimal additionalAllowance = defaultZero(assignment.getAdditionalAllowance());

            BigDecimal supportForCalc = support.add(additionalAllowance);

            // Tính đúng logic mới
            BigDecimal assignmentAmount = calculateAssignmentAmount(assignment, bonus, supportForCalc);
            amountTotal = amountTotal.add(assignmentAmount);

            // SUM general totals
            totalBonus = totalBonus.add(bonus);
            totalPenalties = totalPenalties.add(penalty);
            totalSupportCosts = totalSupportCosts.add(support).add(additionalAllowance);
        }

        // ===== ADD EMPLOYEE ALLOWANCE IF COMPANY SCOPE =====
        if (hasCompanyScope && employee.getAllowance() != null) {
            totalSupportCosts = totalSupportCosts.add(employee.getAllowance());
        }

        // ===== INSURANCE =====
        BigDecimal insuranceTotal;

        if (hasCompanyScope && employee.getInsuranceSalary() != null) {
            insuranceTotal = employee.getInsuranceSalary();
        } else {
            // lấy theo request hoặc giữ giá trị cũ
            insuranceTotal = request.getInsuranceTotal() != null
                    ? request.getInsuranceTotal()
                    : (payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO);
        }

        // ===== ADVANCE =====
        BigDecimal advanceTotal = request.getAdvanceTotal() != null
                ? request.getAdvanceTotal()
                : (payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO);

        // ===== FINAL SALARY =====
        BigDecimal finalSalary = amountTotal
                .subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

        if (hasCompanyScope && employee.getAllowance() != null) {
            finalSalary = finalSalary.add(employee.getAllowance());
        }

        // ===== UPDATE PAYROLL =====
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
