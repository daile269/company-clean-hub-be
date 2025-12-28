package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.CreateRatingRequest;
import com.company.company_clean_hub_be.dto.request.UpdateRatingRequest;
import com.company.company_clean_hub_be.dto.response.RatingResponse;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.*;
import com.company.company_clean_hub_be.service.RatingService;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final AssignmentRepository assignmentRepository;
    private final com.company.company_clean_hub_be.service.UserService userService;
    private final com.company.company_clean_hub_be.repository.UserRepository userRepository;

    @Override
    @Transactional
    public RatingResponse createRating(CreateRatingRequest request) {
        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        String username = userService.getCurrentUsername();
        if (username == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        log.info("[RATING][CREATE] start - user={}, contractId={}, assignmentId={}, employeeId={}, rating={}",
            username, request.getContractId(), request.getAssignmentId(), request.getEmployeeId(), request.getRating());
        // Determine provided target: optional assignmentId or employeeId
        Assignment assignment = null;
        Employee targetEmployee = null;
        if (request.getAssignmentId() != null) {
            assignment = assignmentRepository.findById(request.getAssignmentId())
                    .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
            if (assignment.getEmployee() == null) throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
            targetEmployee = assignment.getEmployee();
        } else if (request.getEmployeeId() != null) {
            targetEmployee = employeeRepository.findById(request.getEmployeeId())
                    .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        }

        // Create rating skeleton (employee may be cleared later for employee callers)
        Rating r = Rating.builder()
            .contract(contract)
            .assignment(assignment)
            .employee(targetEmployee)
            .rating(request.getRating())
            .comment(request.getComment())
            .createdAt(LocalDateTime.now())
            .build();

        com.company.company_clean_hub_be.entity.User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        String roleCode = currentUser.getRole() != null ? currentUser.getRole().getCode() : null;

        if ("CUSTOMER".equalsIgnoreCase(roleCode)) {
            log.info("[RATING][CREATE][CUSTOMER] user={} creating for contractId={}", username, contract.getId());
            // preserve existing behavior: only customer owning contract can create
            ensureCustomerOwnsContract(contract);
            r.setCustomer(contract.getCustomer());
            r.setCreatedBy(username != null ? username : request.getCreatedBy());
            // keep original requirement: customer must provide assignment to rate an employee
            if (assignment == null) {
                log.warn("[RATING][CREATE][CUSTOMER] missing assignment when customer tries to rate employee - user={}", username);
                throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
            }
        } else if ("EMPLOYEE".equalsIgnoreCase(roleCode)) {
            // reviewer is the current employee
            Employee reviewer = employeeRepository.findByUsername(username)
                    .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

            // determine effective contract: prefer assignment's contract when assignment provided
            Contract effectiveContract = contract;
            if (assignment != null && assignment.getContract() != null) {
                effectiveContract = assignment.getContract();
                // update rating's contract/customer accordingly
                r.setContract(effectiveContract);
                r.setCustomer(effectiveContract.getCustomer());
            }

            // ensure reviewer had/has assignment with the effective contract (permission)
            List<Assignment> reviewerAssignments = assignmentRepository.findActiveAssignmentByEmployeeAndContract(reviewer.getId(), effectiveContract.getId());
            if (reviewerAssignments == null || reviewerAssignments.isEmpty()) {
                log.warn("[RATING][CREATE][EMPLOYEE] user={} has no active assignment on contractId={}", username, effectiveContract.getId());
                throw new AppException(ErrorCode.NOT_PERMISSION_REVIEW);
            }

            r.setReviewer(reviewer);
            r.setCreatedBy(username);

            log.info("[RATING][CREATE][EMPLOYEE] reviewerId={} assignmentId={} targetEmployeeId={} contractId={}",
                    reviewer.getId(), assignment != null ? assignment.getId() : null,
                    targetEmployee != null ? targetEmployee.getId() : null, effectiveContract.getId());

            // If caller is employee and did NOT provide employeeId explicitly, do NOT set employee target
            if (request.getEmployeeId() == null) {
                r.setEmployee(null);
            }

            // if neither assignment nor employee target provided, treat as feedback for customer
            if (assignment == null && targetEmployee == null) {
                r.setCustomer(effectiveContract.getCustomer());
            }
        } else {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Rating saved = ratingRepository.save(r);
        log.info("[RATING][CREATE] saved rating id={} by user={}", saved.getId(), username);
        return toResponse(saved);
    }

    @Override
    public RatingResponse getRating(Long id) {
        Rating r = ratingRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));
        return toResponse(r);
    }

    @Override
    public List<RatingResponse> getRatingsByContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        ensureCustomerOwnsContract(contract);

        return ratingRepository.findByContractId(contractId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<RatingResponse> getRatingsByEmployee(Long employeeId) {
        return ratingRepository.findByEmployeeId(employeeId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<RatingResponse> getRatingsByReviewer(Long reviewerId) {
        return ratingRepository.findByReviewerId(reviewerId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<RatingResponse> getRatingsByCustomer(Long customerId) {
        return ratingRepository.findByContractCustomerIdAndEmployeeIsNotNull(customerId).stream().map(this::toResponse).collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public RatingResponse updateRating(Long id, UpdateRatingRequest request) {
        Rating r = ratingRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

        ensureCustomerOwnsContract(r.getContract());

        if (request.getRating() != null) r.setRating(request.getRating());
        if (request.getComment() != null) r.setComment(request.getComment());
        Rating saved = ratingRepository.save(r);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteRating(Long id) {
        Rating r = ratingRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));

        ensureCustomerOwnsContract(r.getContract());

        ratingRepository.delete(r);
    }

    @Override
    public PageResponse<RatingResponse> getRatingsWithFilter(Long contractId, Long assignmentId, Long customerId, Long employeeId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Rating> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (contractId != null) preds.add(cb.equal(root.get("contract").get("id"), contractId));
            if (assignmentId != null) preds.add(cb.equal(root.get("assignment").get("id"), assignmentId));
            if (employeeId != null) preds.add(cb.equal(root.get("employee").get("id"), employeeId));
            if (customerId != null) preds.add(cb.equal(root.get("contract").get("customer").get("id"), customerId));
            if (preds.isEmpty()) return cb.conjunction();
            return cb.and(preds.toArray(new Predicate[0]));
        };

        Page<Rating> pr = ratingRepository.findAll(spec, pageable);
        List<RatingResponse> content = pr.getContent().stream().map(this::toResponse).collect(Collectors.toList());

        return PageResponse.<RatingResponse>builder()
                .content(content)
                .page(pr.getNumber())
                .pageSize(pr.getSize())
                .totalElements(pr.getTotalElements())
                .totalPages(pr.getTotalPages())
                .first(pr.isFirst())
                .last(pr.isLast())
                .build();
    }

    private RatingResponse toResponse(Rating r) {
        return RatingResponse.builder()
                .id(r.getId())
                .contractId(r.getContract() != null ? r.getContract().getId() : null)
                .assignmentId(r.getAssignment() != null ? r.getAssignment().getId() : null)
                .employeeId(r.getEmployee() != null ? r.getEmployee().getId() : null)
                .employeeName(r.getEmployee() != null ? r.getEmployee().getName() : null)
                .employeeCode(r.getEmployee() != null ? r.getEmployee().getEmployeeCode() : null)
                .customerName(r.getContract() != null && r.getContract().getCustomer() != null ? r.getContract().getCustomer().getName() : null)
                .contractDescription(r.getContract() != null ? r.getContract().getDescription() : null)
                .rating(r.getRating())
                .comment(r.getComment())
                .createdBy(r.getCreatedBy())
                .createdAt(r.getCreatedAt())
                .reviewerId(r.getReviewer() != null ? r.getReviewer().getId() : null)
                .reviewerName(r.getReviewer() != null ? r.getReviewer().getName() : null)
                .reviewerRole(r.getReviewer() != null && r.getReviewer().getRole() != null ? r.getReviewer().getRole().getCode() : null)
                .build();
    }

    private void ensureCustomerOwnsContract(Contract contract) {
        String username = userService.getCurrentUsername();
        if (username == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        com.company.company_clean_hub_be.entity.User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        String roleCode = currentUser.getRole() != null ? currentUser.getRole().getCode() : null;
        if ("CUSTOMER".equalsIgnoreCase(roleCode)) {
            if (contract == null || contract.getCustomer() == null || !contract.getCustomer().getId().equals(currentUser.getId())) {
                throw new AppException(ErrorCode.NOT_PERMISSION_REVIEW);
            }
        }
    }
}
