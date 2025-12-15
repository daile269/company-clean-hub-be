package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.ProfileChangeRequestRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ProfileChangeRequestResponse;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest.RequestStatus;
import com.company.company_clean_hub_be.service.ProfileChangeRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile-change-requests")
@RequiredArgsConstructor
public class ProfileChangeRequestController {

    private final ProfileChangeRequestService profileChangeRequestService;

    @PostMapping
    @PreAuthorize("hasAuthority('REQUEST_PROFILE_CHANGE')")
    public ResponseEntity<ProfileChangeRequestResponse> createRequest(
            @Valid @RequestBody ProfileChangeRequestRequest request) {
        return ResponseEntity.ok(profileChangeRequestService.createRequest(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('APPROVE_PROFILE_CHANGE', 'REQUEST_PROFILE_CHANGE')")
    public ResponseEntity<ProfileChangeRequestResponse> getRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(profileChangeRequestService.getRequestById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyAuthority('APPROVE_PROFILE_CHANGE', 'REQUEST_PROFILE_CHANGE')")
    public ResponseEntity<List<ProfileChangeRequestResponse>> getRequestsByEmployee(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(profileChangeRequestService.getRequestsByEmployee(employeeId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPROVE_PROFILE_CHANGE')")
    public ResponseEntity<PageResponse<ProfileChangeRequestResponse>> getRequestsWithFilters(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(profileChangeRequestService.getRequestsWithFilters(
                employeeId, status, page, pageSize));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVE_PROFILE_CHANGE')")
    public ResponseEntity<ProfileChangeRequestResponse> approveRequest(@PathVariable Long id) {
        return ResponseEntity.ok(profileChangeRequestService.approveRequest(id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('APPROVE_PROFILE_CHANGE')")
    public ResponseEntity<ProfileChangeRequestResponse> rejectRequest(
            @PathVariable Long id,
            @RequestParam String rejectionReason) {
        return ResponseEntity.ok(profileChangeRequestService.rejectRequest(id, rejectionReason));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('REQUEST_PROFILE_CHANGE')")
    public ResponseEntity<ProfileChangeRequestResponse> cancelRequest(@PathVariable Long id) {
        return ResponseEntity.ok(profileChangeRequestService.cancelRequest(id));
    }
}
