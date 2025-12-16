package com.company.company_clean_hub_be.service.impl;

// payroll request import removed (payroll calculation logic disabled)
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.dto.response.PayrollAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentScope;
import com.company.company_clean_hub_be.entity.AssignmentType;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.Payroll;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.PayrollRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.PayrollService;
import com.company.company_clean_hub_be.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    public List<PayrollAssignmentResponse> calculatePayroll(PayrollRequest request) {
        log.info("=== START calculatePayroll ===");
        log.info("Request: employeeId={}, month={}, year={}",
                request.getEmployeeId(), request.getMonth(), request.getYear());

        // If employeeId is null, calculate for all employees
        if (request.getEmployeeId() == null) {
            log.info("Bulk calculation for all employees");
            return calculatePayrollForAllEmployees(request.getMonth(), request.getYear());
        }

        // Single employee calculation
        return calculatePayrollForSingleEmployee(request);
    }

    private List<PayrollAssignmentResponse> calculatePayrollForAllEmployees(Integer month, Integer year) {
        log.info("[BULK-CALC] Calculating payroll for all employees: month={}, year={}", month, year);
        
        List<Employee> employees = employeeRepository.findDistinctEmployeesByAssignmentMonthYear(month, year);
        log.info("[BULK-CALC] Found {} employees with assignments", employees.size());
        
        List<PayrollAssignmentResponse> result = new ArrayList<>();
        User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        
        for (Employee employee : employees) {
            Long employeeId = employee.getId();
            log.info("[BULK-CALC] Processing employee id={}, name={}", employeeId, employee.getName());
            
            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);
            
            if (assignments == null || assignments.isEmpty()) {
                log.info("[BULK-CALC] Employee {} has no assignments, skip", employeeId);
                continue;
            }
            
            // Check if payroll already exists
            Optional<Payroll> existingPayroll = payrollRepository.findByEmployeeAndMonthAndYear(employeeId, month, year);
            if (existingPayroll.isPresent()) {
                log.info("[BULK-CALC] Payroll already exists for employee {}, skip creation", employeeId);
                continue;
            }
            
            Payroll payroll = upsertPayrollFromAssignments(employee, assignments, month, year, accountant,null);
            
            if (payroll == null) {
                log.info("[BULK-CALC] Employee {} skipped (no attendance)", employeeId);
                continue;
            }
            
            // Convert to response objects
            List<PayrollAssignmentResponse> employeeResponses = convertPayrollToAssignmentResponses(
                    payroll, employee, assignments, month, year);
            result.addAll(employeeResponses);
        }
        
        log.info("[BULK-CALC] Completed. Total response rows: {}", result.size());
        return result;
    }

    private List<PayrollAssignmentResponse> calculatePayrollForSingleEmployee(PayrollRequest request) {
        log.info("[SINGLE-CALC] Calculating payroll for employee: {}", request.getEmployeeId());
        
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

//        // If COMPANY: add employee allowance to final payroll (not per assignment)
//        if (hasCompanyScope && employee.getAllowance() != null) {
//            totalSupportCosts = totalSupportCosts.add(employee.getAllowance());
//        }

        // Insurance salary
        if (hasCompanyScope && employee.getInsuranceSalary() != null) {
            insuranceTotal = employee.getInsuranceSalary();
        } else {
            insuranceTotal = request.getInsuranceAmount() != null ? request.getInsuranceAmount() : BigDecimal.ZERO;
        }

        // Final salary (same as upsert)
        BigDecimal finalSalary = amountTotal
                .subtract(totalPenalties.add(insuranceTotal).add(advanceTotal));

//        if (hasCompanyScope && employee.getAllowance() != null) {
//            finalSalary = finalSalary.add(employee.getAllowance());
//        }

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

        // Convert to assignment responses
        return convertPayrollToAssignmentResponses(savedPayroll, employee, assignments, 
                request.getMonth(), request.getYear());
    }

    private List<PayrollAssignmentResponse> convertPayrollToAssignmentResponses(
            Payroll payroll, Employee employee, List<Assignment> assignments, Integer month, Integer year) {
        
        List<PayrollAssignmentResponse> result = new ArrayList<>();
        
        int totalPlanedDay = 0;
        int totalDays = 0;
        BigDecimal totalBonus = payroll.getBonusTotal() != null ? payroll.getBonusTotal() : BigDecimal.ZERO;
        BigDecimal totalPenalty = payroll.getPenaltyTotal() != null ? payroll.getPenaltyTotal() : BigDecimal.ZERO;
        BigDecimal totalAllowance = payroll.getAllowanceTotal() != null ? payroll.getAllowanceTotal() : BigDecimal.ZERO;
        BigDecimal totalInsurance = payroll.getInsuranceTotal() != null ? payroll.getInsuranceTotal() : BigDecimal.ZERO;
        BigDecimal totalAdvance = payroll.getAdvanceTotal() != null ? payroll.getAdvanceTotal() : BigDecimal.ZERO;
        
        // Create assignment rows
        for (Assignment assignment : assignments) {
            int assignmentDays = calculateActualWorkDays(assignment);
            BigDecimal assignmentBonus = defaultZero(attendanceRepository.sumBonusByAssignment(assignment.getId()));
            BigDecimal assignmentPenalty = defaultZero(attendanceRepository.sumPenaltyByAssignment(assignment.getId()));
            BigDecimal assignmentSupport = defaultZero(attendanceRepository.sumSupportCostByAssignment(assignment.getId()));
            BigDecimal additionalAllowance = defaultZero(assignment.getAdditionalAllowance());
            
            AssignmentScope scope = assignment.getScope() != null ? assignment.getScope() : AssignmentScope.CONTRACT;
            BigDecimal assignmentAllowance = assignmentSupport.add(additionalAllowance);
            
            BigDecimal baseSalary = assignment.getSalaryAtTime();
            
            String projectCompany = "";
            if (assignment.getContract() != null) {
                projectCompany = assignment.getContract().getCustomer() != null
                        ? assignment.getContract().getCustomer().getCompany()
                        : null;
            } else {
                projectCompany = "Văn phòng";
            }
            
            String assignmentTypeVN = mapAssignmentTypeToVietnamese(assignment.getAssignmentType());
            BigDecimal assignmentSalary = calculateAssignmentAmount(assignment, assignmentBonus, assignmentSupport);
            
            PayrollAssignmentResponse dto = PayrollAssignmentResponse.builder()
                    .payrollId(payroll.getId())
                    .employeeId(employee.getId())
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
            
            result.add(dto);
            
            totalDays += assignmentDays;
            totalPlanedDay += assignment.getPlannedDays();
        }
        
        // Create total row
        PayrollAssignmentResponse totalRow = PayrollAssignmentResponse.builder()
                .payrollId(payroll.getId())
                .employeeId(employee.getId())
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
                .finalSalary(payroll.getFinalSalary())
                .isTotalRow(true)
                .build();
        
        result.add(totalRow);
        return result;
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
        log.info("[PAYROLL-EXPORT] Found {} employees with assignments", employees.size());

        List<PayRollAssignmentExportExcel> result = new ArrayList<>();

        for (Employee employee : employees) {
            Long employeeId = employee.getId();
            log.info("[PAYROLL-EXPORT] Processing employee id={}, name={}", employeeId, employee.getName());

            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);

            if (assignments == null || assignments.isEmpty()) {
                log.info("[PAYROLL-EXPORT] Employee {} has NO assignments. Skip.", employeeId);
                continue;
            }
            User accountant = userRepository.findByUsername(userService.getCurrentUsername())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
            Map<String,String> note = new HashMap<>();
            Payroll persistedPayroll = upsertPayrollFromAssignments(employee, assignments, month, year, accountant,note );
//            List<Map.Entry<String, String>> entries =
//                    new ArrayList<>(note.entrySet());
//
//            for (int i = entries.size() - 1; i >= 0; i--) {
//                Map.Entry<String, String> entry = entries.get(i);
//                log.info("note key={}, value={}", entry.getKey(), entry.getValue());
//            }
            if (persistedPayroll == null) {
                log.info("[PAYROLL-EXPORT] Employee {} skipped because no attendance was found.", employeeId);
                continue;
            }

            // Collect project names
            List<String> projectNames = assignments.stream()
                    .map(assignment -> {
                        if (assignment.getContract() != null && assignment.getContract().getCustomer() != null) {
                            return assignment.getContract().getCustomer().getCompany();
                        }
                        return "Văn phòng";
                    })
                    .distinct()
                    .collect(Collectors.toList());

            // Calculate total days
            int totalDays = 0;
            int totalPlannedDays = 0;
            for (Assignment assignment : assignments) {
                totalDays += calculateActualWorkDays(assignment);
                totalPlannedDays += assignment.getPlannedDays() != null ? assignment.getPlannedDays() : 0;
            }

            // Calculate salary before advance
            BigDecimal salaryBeforeAdvance = persistedPayroll.getFinalSalary();
            if (persistedPayroll.getAdvanceTotal() != null) {
                salaryBeforeAdvance = salaryBeforeAdvance.add(persistedPayroll.getAdvanceTotal());
            }

            List<Map.Entry<String, String>> entries = new ArrayList<>(note.entrySet());
            StringBuilder sb = new StringBuilder();
            for (int i = entries.size() - 1; i >= 0; i--) {
                Map.Entry<String, String> e = entries.get(i);
                String key = e.getKey();
                String val = e.getValue() != null ? e.getValue() : "";
                sb.append(key).append(": ").append(val);
                if (i > 0) {
                    sb.append('\n');
                }
            }
            String noteStr = sb.toString();
            
            // Create single summary row for employee
            PayRollAssignmentExportExcel summaryRow = PayRollAssignmentExportExcel.builder()
                    .employeeId(employeeId)
                    .employeeName(employee.getName())
                    .bankName(employee.getBankName())
                    .bankAccount(employee.getBankAccount())
                    .phone(employee.getPhone())
                    .assignmentType(null) // Not used in summary
                    .baseSalary(null) // Not used in summary
                    .projectCompany(String.join(", ", projectNames)) // Join all projects
                    .assignmentDays(null)
                    .assignmentPlanedDays(null)
                    .assignmentBonus(null)
                    .assignmentPenalty(null)
                    .assignmentAllowance(null)
                    .assignmentInsurance(null)
                    .assignmentAdvance(null)
                    .assignmentSalary(null)
                    .totalDays(totalDays)
                    .totalPlanedDays(totalPlannedDays)
                    .totalBonus(persistedPayroll.getBonusTotal())
                    .totalPenalty(persistedPayroll.getPenaltyTotal())
                    .totalAllowance(persistedPayroll.getAllowanceTotal())
                    .totalInsurance(persistedPayroll.getInsuranceTotal())
                    .companyAllowance(employee.getAllowance())
                    .totalSalaryBeforeAdvance(salaryBeforeAdvance)
                    .totalAdvance(persistedPayroll.getAdvanceTotal())
                    .finalSalary(persistedPayroll.getFinalSalary())
                    .note(noteStr)
                    .isTotalRow(true) // Mark as summary row
                    .build();

            result.add(summaryRow);
            log.info("[PAYROLL-EXPORT] Added summary row for employee {}", employeeId);
        }

        log.info("[PAYROLL-EXPORT] COMPLETED. Total rows: {}", result.size());
        return result;
    }

    @Override
    public PageResponse<PayrollAssignmentResponse> getPayrollAssignmentsWithFilter(
            String keyword, Integer month, Integer year, int page, int pageSize) {
        
        log.info("[PAYROLL-FILTER] Getting filtered assignments: keyword='{}', month={}, year={}, page={}, pageSize={}",
                keyword, month, year, page, pageSize);
        
        // Get employees based on filter
        List<Employee> allEmployees = employeeRepository.findDistinctEmployeesByAssignmentMonthYear(month, year);
        
        // Apply keyword filter
        List<Employee> filteredEmployees = allEmployees;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredEmployees = allEmployees.stream()
                    .filter(emp -> 
                        (emp.getName() != null && emp.getName().toLowerCase().contains(lowerKeyword)) ||
                        (emp.getEmployeeCode() != null && emp.getEmployeeCode().toLowerCase().contains(lowerKeyword)) ||
                        (emp.getPhone() != null && emp.getPhone().contains(keyword))
                    )
                    .collect(Collectors.toList());
        }
        
        log.info("[PAYROLL-FILTER] After filtering: {} employees", filteredEmployees.size());
        
        // Calculate total elements (need to count all assignment + total rows)
        int totalAssignmentRows = 0;
        for (Employee emp : filteredEmployees) {
            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, emp.getId(), null);
            if (assignments != null && !assignments.isEmpty()) {
                // assignments count + 1 total row
                totalAssignmentRows += assignments.size() + 1;
            }
        }
        
        int totalElements = totalAssignmentRows;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        
        // Manual pagination: collect all responses then slice
        List<PayrollAssignmentResponse> allResponses = new ArrayList<>();
        
        for (Employee employee : filteredEmployees) {
            Long employeeId = employee.getId();
            
            List<Assignment> assignments = assignmentRepository
                    .findDistinctAssignmentsByAttendanceMonthAndEmployee(month, year, employeeId, null);
            
            if (assignments == null || assignments.isEmpty()) {
                continue;
            }
            
            // Get or find payroll
            Optional<Payroll> payrollOpt = payrollRepository.findByEmployeeAndMonthAndYear(employeeId, month, year);
            
            if (!payrollOpt.isPresent()) {
                log.info("[PAYROLL-FILTER] No payroll found for employee {}, skip", employeeId);
                continue;
            }
            
            Payroll payroll = payrollOpt.get();
            
            // Convert to responses
            List<PayrollAssignmentResponse> employeeResponses = convertPayrollToAssignmentResponses(
                    payroll, employee, assignments, month, year);
            allResponses.addAll(employeeResponses);
        }
        
        // Apply pagination
        int start = page * pageSize;
        int end = Math.min(start + pageSize, allResponses.size());
        
        List<PayrollAssignmentResponse> paginatedContent = (start < allResponses.size()) 
                ? allResponses.subList(start, end)
                : new ArrayList<>();
        
        log.info("[PAYROLL-FILTER] Returning {} items (page {}/{})", paginatedContent.size(), page, totalPages);
        
        return PageResponse.<PayrollAssignmentResponse>builder()
                .content(paginatedContent)
                .page(page)
                .pageSize(pageSize)
                .totalElements((long) allResponses.size())
                .totalPages((int) Math.ceil((double) allResponses.size() / pageSize))
                .first(page == 0)
                .last(page >= totalPages - 1)
                .build();
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
                                                 User accountant, Map<String,String> note) {
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
        String finalRow = "( ";
        for (Assignment assignment : assignments) {

            log.debug("[PAYROLL-EXPORT][DEBUG] Processing assignment id={} for employeeId={}",
                    assignment != null ? assignment.getId() : null, employee.getId());

            int assignmentRealDays = calculateActualWorkDays(assignment);
            totalDays += assignmentRealDays;
            log.debug("[PAYROLL-EXPORT][DEBUG] assignmentRealDays={}, cumulative totalDays={}", assignmentRealDays, totalDays);
            String key = assignment.getContract() != null ? assignment.getContract().getCustomer().getCompany() : "Văn phòng" ;
            String value = mapAssignmentTypeToVietnamese(assignment.getAssignmentType())+" :";
            if (note != null){
                        note.put(key,value);
            }
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

            BigDecimal calculatedAssignmentAmount = calculateAssignmentAmount(assignment, safeBonus, suportAssignment,note);
            value  = note.get(key);
            log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount returned {} for assignmentId={}",
                    calculatedAssignmentAmount, assignment.getId());
            amountTotal = amountTotal.add(calculatedAssignmentAmount);
            finalRow += calculatedAssignmentAmount +" +";
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
        // Subtract penalties, insurance and advances from total amount
        BigDecimal deductions = totalPenalties.add(insuranceTotal).add(advanceTotal);
        log.debug("[PAYROLL-EXPORT][DEBUG] Deductions calculated -> totalPenalties={}, insuranceTotal={}, advanceTotal={}, deductions={}",
                totalPenalties, insuranceTotal, advanceTotal, deductions);

        BigDecimal finalAmount = amountTotal.subtract(deductions);
        finalRow += String.format(
                " ) - (%s + %s + %s) = %s",
                totalPenalties,
                insuranceTotal,
                advanceTotal,
                finalAmount
        );
        note.put("Tổng",finalRow);
        log.debug("[PAYROLL-EXPORT][DEBUG] Final salary computed before persisting = {} (amountTotal={} - deductions={})",
                finalAmount, amountTotal, deductions);
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
        salaryBase = assignment.getSalaryAtTime() != null ? assignment.getSalaryAtTime() : BigDecimal.ZERO;
        log.debug("[PAYROLL-EXPORT][DEBUG] Scope CONTRACT -> salaryBase (assignment.salaryAtTime) = {}", salaryBase);
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
    private BigDecimal calculateAssignmentAmount(Assignment assignment, BigDecimal bonus, BigDecimal supportCosts, Map <String, String>  note) {
        log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount start - assignmentId={}, bonus={}, supportCosts={}",
                assignment != null ? assignment.getId() : null, bonus, supportCosts);

        if (assignment == null || assignment.getAssignmentType() == null) {
            log.debug("[PAYROLL-EXPORT][DEBUG] assignment or assignmentType is null -> return ZERO");
            return BigDecimal.ZERO;
        }
        String key = assignment.getContract() != null ? assignment.getContract().getCustomer().getCompany() : "Văn phòng";
        String value = "";
        if (note != null) value  = note.get(key);
        BigDecimal amount = BigDecimal.ZERO;
        AssignmentType type = assignment.getAssignmentType();
        AssignmentScope scope = assignment.getScope() != null ? assignment.getScope() : AssignmentScope.CONTRACT;
        int realWorksDay = calculateActualWorkDays(assignment);

        log.debug("[PAYROLL-EXPORT][DEBUG] type={}, scope={}, realWorksDay={}", type, scope, realWorksDay);

        BigDecimal salaryBase;
        salaryBase = assignment.getSalaryAtTime() != null ? assignment.getSalaryAtTime() : BigDecimal.ZERO;
        log.debug("[PAYROLL-EXPORT][DEBUG] Scope CONTRACT -> salaryBase (assignment.salaryAtTime) = {}", salaryBase);
        if (type == AssignmentType.FIXED_BY_CONTRACT || type == AssignmentType.FIXED_BY_COMPANY) {
            log.debug("[PAYROLL-EXPORT][DEBUG] Fixed type branch. plannedDays={}, salaryBase={}, bonus={}, supportCosts={}, workDaysField={}",
                    assignment.getPlannedDays(), salaryBase, bonus, supportCosts, assignment.getWorkDays());

            if (assignment.getPlannedDays() != null && assignment.getPlannedDays() > 0
                    && salaryBase.compareTo(BigDecimal.ZERO) > 0 && assignment.getWorkDays() != null) {
                BigDecimal dailyRate = (salaryBase.add(defaultZero(bonus)).add(defaultZero(supportCosts)))
                        .divide(BigDecimal.valueOf(assignment.getPlannedDays()), 2, RoundingMode.HALF_UP);
                amount = dailyRate.multiply(BigDecimal.valueOf(realWorksDay));
                log.debug("[PAYROLL-EXPORT][DEBUG] Fixed amount computed: dailyRate={}, amount={}", dailyRate, amount);
                value += String.format(
                        " (%s + %s + %s) / %d * %d = %s; ",
                        salaryBase,
                        defaultZero(bonus),
                        defaultZero(supportCosts),
                        assignment.getPlannedDays(),
                        realWorksDay,
                        amount
                );
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
                value += String.format(
                        " %s * %d + %s + %s = %s; ",
                        salaryBase,
                        realWorksDay,
                        defaultZero(supportCosts),
                        defaultZero(bonus),
                        amount
                );
            } else {
                log.debug("[PAYROLL-EXPORT][DEBUG] Day/Temporary branch conditions not met -> amount stays ZERO");
            }
        }
        log.debug("[PAYROLL-EXPORT][DEBUG] calculateAssignmentAmount returning amount={}", amount);
        if (note != null) note.put(key,value);
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
        
//        LocalDateTime createdAt = payroll.getCreatedAt();
//                PayrollResponse resp = mapToResponse(payroll, createdAt.getMonthValue(), createdAt.getYear(), null);
//                log.info("getPayrollById completed: id={}, employeeId={}", id, resp.getEmployeeId());
//                return resp;
        PayrollUpdateRequest payrollUpdateRequest = new PayrollUpdateRequest(payroll.getAllowanceTotal(),payroll.getInsuranceTotal(),payroll.getAdvanceTotal());
         PayrollResponse rs =  this.updatePayroll(id, payrollUpdateRequest);
        return rs;
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
