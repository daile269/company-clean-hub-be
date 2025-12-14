package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ProfileChangeRequestRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ProfileChangeRequestResponse;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest.RequestStatus;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.ProfileChangeRequestRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.ProfileChangeRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProfileChangeRequestServiceImpl implements ProfileChangeRequestService {
    
    private final ProfileChangeRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Override
    public ProfileChangeRequestResponse createRequest(ProfileChangeRequestRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("createProfileChangeRequest by {}: employeeId={}, changeType={}", 
                username, request.getEmployeeId(), request.getChangeType());

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        User requestedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        ProfileChangeRequest changeRequest = ProfileChangeRequest.builder()
                .employee(employee)
                .requestedBy(requestedBy)
                .changeType(request.getChangeType())
                .fieldName(request.getFieldName())
                .oldValue(request.getOldValue())
                .newValue(request.getNewValue())
                .reason(request.getReason())
                .status(RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProfileChangeRequest saved = requestRepository.save(changeRequest);
        log.info("Created profile change request id={}", saved.getId());
        
        return mapToResponse(saved);
    }

    @Override
    public ProfileChangeRequestResponse getRequestById(Long id) {
        ProfileChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND)); // Reuse error code
        return mapToResponse(request);
    }

    @Override
    public List<ProfileChangeRequestResponse> getRequestsByEmployee(Long employeeId) {
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        
        return requestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ProfileChangeRequestResponse> getRequestsWithFilters(
            Long employeeId, RequestStatus status, int page, int pageSize) {
        
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<ProfileChangeRequest> requestPage = requestRepository.findByFilters(
                employeeId, status, pageable);

        List<ProfileChangeRequestResponse> items = requestPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<ProfileChangeRequestResponse>builder()
                .content(items)
                .page(requestPage.getNumber())
                .pageSize(requestPage.getSize())
                .totalElements(requestPage.getTotalElements())
                .totalPages(requestPage.getTotalPages())
                .first(requestPage.isFirst())
                .last(requestPage.isLast())
                .build();
    }

    @Override
    public ProfileChangeRequestResponse approveRequest(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("approveProfileChangeRequest by {}: requestId={}", username, id);

        ProfileChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS); // Reuse error
        }

        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        request.setStatus(RequestStatus.APPROVED);
        request.setApprovedBy(approver);
        request.setApprovedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        ProfileChangeRequest updated = requestRepository.save(request);
        log.info("Approved profile change request id={}", id);
        
        return mapToResponse(updated);
    }

    @Override
    public ProfileChangeRequestResponse rejectRequest(Long id, String rejectionReason) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("rejectProfileChangeRequest by {}: requestId={}", username, id);

        ProfileChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
        }

        User approver = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        request.setStatus(RequestStatus.REJECTED);
        request.setApprovedBy(approver);
        request.setApprovedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);
        request.setUpdatedAt(LocalDateTime.now());

        ProfileChangeRequest updated = requestRepository.save(request);
        log.info("Rejected profile change request id={}", id);
        
        return mapToResponse(updated);
    }

    @Override
    public ProfileChangeRequestResponse cancelRequest(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("cancelProfileChangeRequest by {}: requestId={}", username, id);

        ProfileChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
        }

        request.setStatus(RequestStatus.CANCELLED);
        request.setUpdatedAt(LocalDateTime.now());

        ProfileChangeRequest updated = requestRepository.save(request);
        log.info("Cancelled profile change request id={}", id);
        
        return mapToResponse(updated);
    }

    private ProfileChangeRequestResponse mapToResponse(ProfileChangeRequest request) {
        return ProfileChangeRequestResponse.builder()
                .id(request.getId())
                .employeeId(request.getEmployee().getId())
                .employeeName(request.getEmployee().getName())
                .employeeCode(request.getEmployee().getEmployeeCode())
                .requestedByUserId(request.getRequestedBy().getId())
                .requestedByUsername(request.getRequestedBy().getUsername())
                .changeType(request.getChangeType())
                .fieldName(request.getFieldName())
                .oldValue(request.getOldValue())
                .newValue(request.getNewValue())
                .reason(request.getReason())
                .status(request.getStatus())
                .approvedByUserId(request.getApprovedBy() != null ? request.getApprovedBy().getId() : null)
                .approvedByUsername(request.getApprovedBy() != null ? request.getApprovedBy().getUsername() : null)
                .approvedAt(request.getApprovedAt())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
