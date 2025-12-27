package com.company.company_clean_hub_be.util;

import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizationUtils {

    @Autowired
    private static UserRepository userRepository;

    @Autowired
    public AuthorizationUtils(UserRepository userRepository) {
        AuthorizationUtils.userRepository = userRepository;
    }

    /**
     * Get current authenticated UserPrincipal from Spring Security context
     */
    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    /**
     * Get the actual User entity from database
     */
    public static User getCurrentUser() {
        UserPrincipal principal = getCurrentUserPrincipal();
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
    }

    /**
     * Validate if CUSTOMER role can access the given customerId
     * Throws 403 if customer tries to access other customer's data
     * Other roles (QLT1, QLT2, QLV, ACCOUNTANT) can access any customer
     */
    public static void validateCustomerAccess(Long customerId) {
        User user = getCurrentUser();

        // Only validate if user is a Customer
        if (user instanceof Customer) {
            Customer customer = (Customer) user;
            if (!customer.getId().equals(customerId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }
        // Other roles can access any customer
    }

    /**
     * Validate if EMPLOYEE role can access the given employeeId
     * Throws 403 if employee tries to access other employee's data
     * Other roles can access any employee
     */
    public static void validateEmployeeAccess(Long employeeId) {
        User user = getCurrentUser();

        // Only validate if user is an Employee
        if (user instanceof Employee) {
            Employee employee = (Employee) user;
            if (!employee.getId().equals(employeeId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }
        // Other roles can access any employee
    }

    /**
     * Check if current user (CUSTOMER) owns the contract
     * Throws 403 if customer tries to access contract they don't own
     * Other roles can access any contract
     */
    public static void validateContractOwnership(Long contractCustomerId) {
        User user = getCurrentUser();

        // Only validate if user is a Customer
        if (user instanceof Customer) {
            Customer customer = (Customer) user;
            if (!customer.getId().equals(contractCustomerId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }
        // Other roles can access any contract
    }
}
