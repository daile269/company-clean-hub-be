package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.CustomerAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.CustomerAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.security.UserPrincipal;
import com.company.company_clean_hub_be.service.CustomerAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer-assignments")
public class CustomerAssignmentController {

        private final CustomerAssignmentService customerAssignmentService;

        /**
         * Phân công khách hàng cho quản lý
         */
        @PostMapping
        @PreAuthorize("hasAuthority('CUSTOMER_ASSIGN')")
        public ApiResponse<CustomerAssignmentResponse> assignCustomer(
                        @Valid @RequestBody CustomerAssignmentRequest request,
                        Authentication authentication) {

                Long assignerId = ((UserPrincipal) authentication.getPrincipal()).getId();
                CustomerAssignmentResponse response = customerAssignmentService.assignCustomer(request, assignerId);

                return ApiResponse.success(
                                "Phân công khách hàng thành công",
                                response,
                                HttpStatus.CREATED.value());
        }

        /**
         * Hủy phân công khách hàng
         */
        @DeleteMapping
        @PreAuthorize("hasAuthority('CUSTOMER_ASSIGN')")
        public ApiResponse<Void> revokeAssignment(
                        @RequestParam Long managerId,
                        @RequestParam Long customerId,
                        Authentication authentication) {

                Long requesterId = ((UserPrincipal) authentication.getPrincipal()).getId();
                customerAssignmentService.revokeAssignment(managerId, customerId, requesterId);

                return ApiResponse.success(
                                "Hủy phân công thành công",
                                null,
                                HttpStatus.OK.value());
        }

        /**
         * Lấy danh sách khách hàng được phân công cho một manager
         */
        @GetMapping("/manager/{managerId}/customers")
        @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
        public ApiResponse<PageResponse<CustomerResponse>> getAssignedCustomers(
                        @PathVariable Long managerId,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int pageSize) {
                PageResponse<CustomerResponse> customers = customerAssignmentService.getAssignedCustomers(
                                managerId, keyword, page, pageSize);

                return ApiResponse.success(
                                "Lấy danh sách khách hàng thành công",
                                customers,
                                HttpStatus.OK.value());
        }

        /**
         * Lấy danh sách khách hàng của user hiện tại
         */
        @GetMapping("/my-customers")
        @PreAuthorize("isAuthenticated()")
        public ApiResponse<PageResponse<CustomerResponse>> getMyAssignedCustomers(
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int pageSize) {
                PageResponse<CustomerResponse> customers = customerAssignmentService.getMyAssignedCustomers(
                                keyword, page, pageSize);

                return ApiResponse.success(
                                "Lấy danh sách khách hàng của tôi thành công",
                                customers,
                                HttpStatus.OK.value());
        }

        /**
         * Lấy danh sách phân công của một manager
         */
        @GetMapping("/manager/{managerId}")
        @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
        public ApiResponse<List<CustomerAssignmentResponse>> getAssignmentsByManager(@PathVariable Long managerId) {
                List<CustomerAssignmentResponse> assignments = customerAssignmentService
                                .getAssignmentsByManager(managerId);

                return ApiResponse.success(
                                "Lấy danh sách phân công thành công",
                                assignments,
                                HttpStatus.OK.value());
        }

        /**
         * Lấy danh sách manager được phân công cho một customer
         */
        @GetMapping("/customer/{customerId}")
        public ApiResponse<List<CustomerAssignmentResponse>> getAssignmentsByCustomer(
                        @PathVariable Long customerId,
                        @RequestParam(required = false) String role) {

                List<CustomerAssignmentResponse> assignments = customerAssignmentService
                                .getAssignmentsByCustomer(customerId, role);

                return ApiResponse.success(
                                "Lấy danh sách quản lý của khách hàng thành công",
                                assignments,
                                HttpStatus.OK.value());
        }
}
