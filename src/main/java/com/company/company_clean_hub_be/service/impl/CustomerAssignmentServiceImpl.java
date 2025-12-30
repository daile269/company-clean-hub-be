package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.CustomerAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.CustomerAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.CustomerAssignment;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.repository.CustomerAssignmentRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.security.UserPrincipal;
import com.company.company_clean_hub_be.service.CustomerAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAssignmentServiceImpl implements CustomerAssignmentService {

    private final CustomerAssignmentRepository customerAssignmentRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public CustomerAssignmentResponse assignCustomer(CustomerAssignmentRequest request, Long assignerId) {
        log.info("Bắt đầu phân công khách hàng: managerId={}, customerId={}, assignerId={}",
                request.getManagerId(), request.getCustomerId(), assignerId);

        // Kiểm tra manager tồn tại
        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy quản lý với ID: " + request.getManagerId()));

        // Kiểm tra customer tồn tại
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng với ID: " + request.getCustomerId()));

        // Kiểm tra người phân công
        User assigner = userRepository.findById(assignerId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy người phân công với ID: " + assignerId));

        // Kiểm tra xem đã phân công chưa
        if (customerAssignmentRepository.existsByManagerIdAndCustomerId(request.getManagerId(),
                request.getCustomerId())) {
            throw new IllegalArgumentException("Khách hàng đã được phân công cho quản lý này rồi");
        }

        // Kiểm tra quyền phân công dựa trên role
        validateAssignmentPermission(assigner, manager, customer);

        // Tạo phân công mới
        CustomerAssignment assignment = CustomerAssignment.builder()
                .manager(manager)
                .customer(customer)
                .assignedBy(assigner)
                .build();

        CustomerAssignment savedAssignment = customerAssignmentRepository.save(assignment);
        log.info("Phân công thành công: assignmentId={}", savedAssignment.getId());

        return mapToResponse(savedAssignment);
    }

    /**
     * Kiểm tra quyền phân công dựa trên role
     * - qlt1 có thể phân công bất kỳ customer nào cho qlt2
     * - qlt2 chỉ có thể phân công customer mà họ được phân công cho qlv
     */
    private void validateAssignmentPermission(User assigner, User manager, Customer customer) {
        String assignerRoleCode = assigner.getRole().getCode();
        String managerRoleCode = manager.getRole().getCode();

        log.debug("Kiểm tra quyền: assignerRole={}, managerRole={}", assignerRoleCode, managerRoleCode);

        // Nếu assigner là qlt1 và manager là qlt2 -> cho phép
        if ("qlt1".equalsIgnoreCase(assignerRoleCode) && "qlt2".equalsIgnoreCase(managerRoleCode)) {
            log.debug("Cho phép: qlt1 phân công cho qlt2");
            return;
        }

        // Nếu assigner là qlt2 và manager là qlv
        if ("qlt2".equalsIgnoreCase(assignerRoleCode) && "qlv".equalsIgnoreCase(managerRoleCode)) {
            // Kiểm tra xem qlt2 có được phân công customer này không
            boolean isAssigned = customerAssignmentRepository.existsByManagerIdAndCustomerId(
                    assigner.getId(), customer.getId());

            if (!isAssigned) {
                throw new AccessDeniedException(
                        "Bạn không thể phân công khách hàng này vì bạn chưa được phân công quản lý khách hàng này");
            }

            log.debug("Cho phép: qlt2 phân công khách hàng đã được phân công cho qlv");
            return;
        }

        // Các trường hợp khác -> không cho phép
        throw new AccessDeniedException(
                String.format("Không có quyền phân công. Role của bạn (%s) không thể phân công cho role (%s)",
                        assignerRoleCode, managerRoleCode));
    }

    @Override
    @Transactional
    public void revokeAssignment(Long managerId, Long customerId, Long requesterId) {
        log.info("Bắt đầu hủy phân công: managerId={}, customerId={}, requesterId={}",
                managerId, customerId, requesterId);

        // Kiểm tra phân công tồn tại
        CustomerAssignment assignment = customerAssignmentRepository.findByManagerIdAndCustomerId(managerId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công này"));

        // Kiểm tra quyền hủy phân công
        User requester = userRepository.findById(requesterId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy người yêu cầu với ID: " + requesterId));

        validateRevokePermission(requester, assignment);

        customerAssignmentRepository.delete(assignment);
        log.info("Đã hủy phân công thành công");
    }

    /**
     * Kiểm tra quyền hủy phân công
     * - Người tạo phân công có thể hủy
     * - qlt1 có thể hủy bất kỳ phân công nào (nếu có permission)
     */
    private void validateRevokePermission(User requester, CustomerAssignment assignment) {
        String roleCode = requester.getRole().getCode();

        // Người tạo có thể hủy
        if (assignment.getAssignedBy().getId().equals(requester.getId())) {
            return;
        }

        // qlt1 có thể hủy
        if ("qlt1".equalsIgnoreCase(roleCode)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền hủy phân công này");
    }

    @Override
    public PageResponse<CustomerResponse> getAssignedCustomers(Long managerId, String keyword, int page, int pageSize) {
        log.info("Lấy danh sách khách hàng được phân công cho manager: {}, keyword: {}, page: {}, pageSize: {}",
                managerId, keyword, page, pageSize);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, pageSize, org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<com.company.company_clean_hub_be.entity.Customer> customerPage = customerAssignmentRepository
                .findCustomersByManagerId(managerId, pageable);

        // Filter by keyword if provided
        java.util.List<CustomerResponse> customerResponses = customerPage.getContent().stream()
                .map(this::mapToCustomerResponse)
                .filter(customer -> {
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return true;
                    }
                    String searchTerm = keyword.toLowerCase();
                    return (customer.getCode() != null && customer.getCode().toLowerCase().contains(searchTerm)) ||
                            (customer.getName() != null && customer.getName().toLowerCase().contains(searchTerm)) ||
                            (customer.getPhone() != null && customer.getPhone().toLowerCase().contains(searchTerm)) ||
                            (customer.getEmail() != null && customer.getEmail().toLowerCase().contains(searchTerm)) ||
                            (customer.getTaxCode() != null && customer.getTaxCode().toLowerCase().contains(searchTerm));
                })
                .collect(java.util.stream.Collectors.toList());

        return com.company.company_clean_hub_be.dto.response.PageResponse.<CustomerResponse>builder()
                .content(customerResponses)
                .page(customerPage.getNumber())
                .pageSize(customerPage.getSize())
                .totalElements(customerPage.getTotalElements())
                .totalPages(customerPage.getTotalPages())
                .first(customerPage.isFirst())
                .last(customerPage.isLast())
                .build();
    }

    @Override
    public PageResponse<CustomerResponse> getMyAssignedCustomers(String keyword, int page, int pageSize) {
        Long currentUserId = getCurrentUserId();
        return getAssignedCustomers(currentUserId, keyword, page, pageSize);
    }

    @Override
    public List<CustomerAssignmentResponse> getAssignmentsByManager(Long managerId) {
        log.info("Lấy danh sách phân công của manager: {}", managerId);

        List<CustomerAssignment> assignments = customerAssignmentRepository.findByManagerId(managerId);

        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CustomerAssignmentResponse> getAssignmentsByCustomer(Long customerId) {
        log.info("Lấy danh sách manager được phân công cho customer: {}", customerId);

        List<CustomerAssignment> assignments = customerAssignmentRepository.findByCustomerId(customerId);

        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private CustomerAssignmentResponse mapToResponse(CustomerAssignment assignment) {
        return CustomerAssignmentResponse.builder()
                .id(assignment.getId())
                .managerId(assignment.getManager().getId())
                .managerName(getManagerName(assignment.getManager()))
                .managerUsername(assignment.getManager().getUsername())
                .customerId(assignment.getCustomer().getId())
                .customerName(assignment.getCustomer().getName())
                .customerCode(assignment.getCustomer().getCustomerCode())
                .assignedById(assignment.getAssignedBy().getId())
                .assignedByName(getManagerName(assignment.getAssignedBy()))
                .createdAt(assignment.getCreatedAt())
                .build();
    }

    private String getManagerName(User user) {
        // Nếu là Employee, có thể có firstName/lastName
        // Nếu không, dùng username
        return user.getUsername(); // Đơn giản hóa, có thể cải thiện sau
    }

    private CustomerResponse mapToCustomerResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerCode(customer.getCustomerCode())
                .code(customer.getCustomerCode()) // Set both for compatibility
                .name(customer.getName())
                .address(customer.getAddress())
                .contactInfo(customer.getContactInfo())
                .taxCode(customer.getTaxCode())
                .description(customer.getDescription())
                .company(customer.getCompany())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .username(customer.getUsername())
                .status(customer.getStatus())
                .roleId(customer.getRole().getId())
                .roleName(customer.getRole().getName())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getId();
        }
        throw new IllegalStateException("Không tìm thấy thông tin người dùng hiện tại");
    }
}
