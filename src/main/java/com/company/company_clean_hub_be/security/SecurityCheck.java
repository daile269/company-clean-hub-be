package com.company.company_clean_hub_be.security;

import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.RatingRepository;
import com.company.company_clean_hub_be.repository.CustomerAssignmentRepository;
import com.company.company_clean_hub_be.entity.Rating;
import com.company.company_clean_hub_be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityCheck {
    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;
    private final AssignmentRepository assignmentRepository;
    private final RatingRepository ratingRepository;
    private final CustomerAssignmentRepository customerAssignmentRepository;

    public boolean isEmployeeSelf(Long employeeId) {
        if (employeeId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        Optional<Employee> emp = employeeRepository.findByUsername(username);
        return emp.map(e -> e.getId() != null && e.getId().equals(employeeId)).orElse(false);
    }

    public boolean isAssignmentOwnedByCurrentUser(Long assignmentId) {
        if (assignmentId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        Optional<Employee> emp = employeeRepository.findByUsername(username);
        if (emp.isEmpty()) return false;
        Long empId = emp.get().getId();
        log.info("[SECURITY] isAssignmentOwnedByCurrentUser start - assignmentId={} username={} empId={}", assignmentId, username, empId);
        return assignmentRepository.findById(assignmentId)
                .map(a -> {
                    boolean owns = a.getEmployee() != null && a.getEmployee().getId() != null && a.getEmployee().getId().equals(empId);
                    log.info("[SECURITY] isAssignmentOwnedByCurrentUser result - assignmentId={} assignmentEmployeeId={} empId={} owns={}", assignmentId,
                            a.getEmployee() != null ? a.getEmployee().getId() : null, empId, owns);
                    return owns;
                })
                .orElseGet(() -> {
                    log.info("[SECURITY] isAssignmentOwnedByCurrentUser result - assignmentId={} not found", assignmentId);
                    return false;
                });
    }

    public boolean isRatingCreatedByCurrentUser(Long ratingId) {
        if (ratingId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        Optional<Employee> emp = employeeRepository.findByUsername(username);
        if (emp.isEmpty()) return false;
        Long empId = emp.get().getId();
        return ratingRepository.findById(ratingId)
                .map(r -> r.getReviewer() != null && r.getReviewer().getId() != null && r.getReviewer().getId().equals(empId))
                .orElse(false);
    }

    public boolean isEmployeeAssignedToCustomer(Long customerId) {
        if (customerId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        Optional<Employee> emp = employeeRepository.findByUsername(username);
        if (emp.isEmpty()) return false;
        Long empId = emp.get().getId();
        // findActiveCustomersByEmployee returns list of Customer entities
        List<com.company.company_clean_hub_be.entity.Customer> customers = assignmentRepository.findActiveCustomersByEmployee(empId);
        if (customers == null || customers.isEmpty()) return false;
        return customers.stream().anyMatch(c -> c != null && c.getId() != null && c.getId().equals(customerId));
    }

    public boolean isCustomerSelf(Long customerId) {
        if (customerId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        return customerRepository.findById(customerId)
                .map(c -> c.getUsername() != null && c.getUsername().equals(username))
                .orElse(false);
    }

    public boolean isEmployeeAssignedToAssignment(Long assignmentId) {
        if (assignmentId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        Optional<Employee> emp = employeeRepository.findByUsername(username);
        if (emp.isEmpty()) return false;
        Long empId = emp.get().getId();

        log.info("[SECURITY] isEmployeeAssignedToAssignment start - assignmentId={} username={} empId={}", assignmentId, username, empId);
        return assignmentRepository.findById(assignmentId)
                .map(a -> {
                    Long contractId = a.getContract() != null ? a.getContract().getId() : null;
                    if (contractId == null) {
                        log.info("[SECURITY] isEmployeeAssignedToAssignment - assignmentId={} has no contract", assignmentId);
                        return false;
                    }
                    List<com.company.company_clean_hub_be.entity.Assignment> myAssignments = assignmentRepository.findActiveAssignmentByEmployeeAndContract(empId, contractId);
                    boolean assigned = myAssignments != null && !myAssignments.isEmpty();
                    log.info("[SECURITY] isEmployeeAssignedToAssignment result - assignmentId={} contractId={} empId={} myAssignmentsCount={} assigned={}",
                            assignmentId, contractId, empId, myAssignments != null ? myAssignments.size() : 0, assigned);
                    return assigned;
                }).orElseGet(() -> {
                    log.info("[SECURITY] isEmployeeAssignedToAssignment - assignmentId={} not found", assignmentId);
                    return false;
                });
    }

    public boolean isRatingOwnedByCustomer(Long ratingId) {
        if (ratingId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        return ratingRepository.findById(ratingId)
                .map(r -> r.getCustomer() != null && r.getCustomer().getUsername() != null && r.getCustomer().getUsername().equals(username))
                .orElse(false);
    }

    /**
     * Kiểm tra nhân viên có quyền đánh giá manager không
     * Logic:
     * 1. assignmentId là assignment của nhân viên đang đăng nhập
     * 2. Kiểm tra assignment đó có thuộc về nhân viên đang đăng nhập không
     * 3. Lấy customerId từ assignment đó
     * 4. Kiểm tra employeeId (người bị đánh giá) có phải manager được phân công quản lý customer đó không
     */
    public boolean canEmployeeReviewManager(Long assignmentId, Long reviewedEmployeeId) {
        if (assignmentId == null || reviewedEmployeeId == null) return false;
        String username = userService.getCurrentUsername();
        if (username == null) return false;
        
        // Lấy thông tin employee hiện tại (người đang đánh giá)
        Optional<Employee> empOpt = employeeRepository.findByUsername(username);
        if (empOpt.isEmpty()) {
            log.info("[SECURITY] canEmployeeReviewManager - user {} is not an employee", username);
            return false;
        }
        Long currentEmpId = empOpt.get().getId();

        log.info("[SECURITY] canEmployeeReviewManager start - assignmentId={} currentEmpId={} reviewedEmployeeId={}", 
                assignmentId, currentEmpId, reviewedEmployeeId);

        return assignmentRepository.findById(assignmentId)
                .map(assignment -> {
                    // 1. Kiểm tra assignment có thuộc về nhân viên đang đăng nhập không
                    Long assignmentOwner = assignment.getEmployee() != null ? assignment.getEmployee().getId() : null;
                    if (assignmentOwner == null || !assignmentOwner.equals(currentEmpId)) {
                        log.info("[SECURITY] canEmployeeReviewManager - assignmentId={} does not belong to currentEmpId={}", 
                                assignmentId, currentEmpId);
                        return false;
                    }

                    // 2. Lấy customerId từ assignment
                    Long customerId = assignment.getContract() != null && assignment.getContract().getCustomer() != null 
                            ? assignment.getContract().getCustomer().getId() : null;
                    if (customerId == null) {
                        log.info("[SECURITY] canEmployeeReviewManager - assignmentId={} has no customer", assignmentId);
                        return false;
                    }

                    // 3. Kiểm tra reviewedEmployeeId có phải manager được phân công quản lý customer đó không
                    boolean isManagerOfCustomer = customerAssignmentRepository
                            .existsByManagerIdAndCustomerId(reviewedEmployeeId, customerId);
                    
                    log.info("[SECURITY] canEmployeeReviewManager result - assignmentId={} customerId={} currentEmpId={} reviewedEmployeeId={} isManager={} → {}",
                            assignmentId, customerId, currentEmpId, reviewedEmployeeId, isManagerOfCustomer,
                            isManagerOfCustomer ? "ALLOWED" : "DENIED");
                    
                    return isManagerOfCustomer;
                }).orElseGet(() -> {
                    log.info("[SECURITY] canEmployeeReviewManager - assignmentId={} not found", assignmentId);
                    return false;
                });
    }
}
