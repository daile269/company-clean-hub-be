package com.company.company_clean_hub_be.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.company_clean_hub_be.dto.request.VerificationApprovalRequest;
import com.company.company_clean_hub_be.dto.request.VerificationCaptureRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentVerificationResponse;
import com.company.company_clean_hub_be.dto.response.VerificationImageResponse;
import com.company.company_clean_hub_be.service.VerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/verifications")
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping("/pending")
    public ApiResponse<List<AssignmentVerificationResponse>> getPendingVerifications() {
        List<AssignmentVerificationResponse> verifications = verificationService.getPendingVerifications();
        return ApiResponse.success("Lấy danh sách xác minh chờ duyệt thành công", verifications, HttpStatus.OK.value());
    }

    @GetMapping("/assignment/{assignmentId}")
    public ApiResponse<AssignmentVerificationResponse> getVerificationByAssignment(@PathVariable Long assignmentId) {
        log.info("[GET /verifications/assignment/{}] Received request to get verification for assignmentId={}",
                assignmentId, assignmentId);
        var result = verificationService.getVerificationByAssignmentId(assignmentId);
        if (result.isPresent()) {
            AssignmentVerificationResponse v = result.get();
            log.info(
                    "[GET /verifications/assignment/{}] FOUND verification: id={}, status={}, canCapture={}, attempts={}/{}",
                    assignmentId, v.getId(), v.getStatus(), v.getCanCapture(), v.getCurrentAttempts(),
                    v.getMaxAttempts());
            return ApiResponse.success("Lấy thông tin xác minh thành công", v, HttpStatus.OK.value());
        } else {
            log.warn(
                    "[GET /verifications/assignment/{}] NOT FOUND - No verification record exists in assignment_verifications table for this assignmentId",
                    assignmentId);
            return ApiResponse.success("Không tìm thấy yêu cầu xác minh", null, HttpStatus.NOT_FOUND.value());
        }
    }

    @PostMapping("/capture")
    public ApiResponse<VerificationImageResponse> captureImage(@Valid @RequestBody VerificationCaptureRequest request) {
        VerificationImageResponse image = verificationService.captureVerificationImage(request);
        return ApiResponse.success("Chụp ảnh xác minh thành công", image, HttpStatus.CREATED.value());
    }

    @GetMapping("/attendance/{attendanceId}/image")
    public ApiResponse<List<VerificationImageResponse>> getImagesByAttendanceId(@PathVariable Long attendanceId) {
        List<VerificationImageResponse> images = verificationService.getImagesByAttendanceId(attendanceId);
        if (images.isEmpty()) {
            return ApiResponse.success("Không tìm thấy ảnh xác minh cho điểm danh này", images, HttpStatus.NOT_FOUND.value());
        }
        return ApiResponse.success("Lấy ảnh xác minh thành công", images, HttpStatus.OK.value());
    }

    @GetMapping("/{verificationId}/images")
    public ApiResponse<List<VerificationImageResponse>> getVerificationImages(@PathVariable Long verificationId) {
        List<VerificationImageResponse> images = verificationService.getVerificationImages(verificationId);
        return ApiResponse.success("Lấy danh sách ảnh xác minh thành công", images, HttpStatus.OK.value());
    }

    @PutMapping("/approve")
    public ApiResponse<AssignmentVerificationResponse> approveVerification(
            @Valid @RequestBody VerificationApprovalRequest request,
            Principal principal) {
        AssignmentVerificationResponse verification = verificationService.approveVerification(request,
                principal.getName());
        return ApiResponse.success("Duyệt xác minh thành công", verification, HttpStatus.OK.value());
    }

    @PutMapping("/{verificationId}/reject")
    public ApiResponse<AssignmentVerificationResponse> rejectVerification(
            @PathVariable Long verificationId,
            @RequestBody(required = false) String reason,
            Principal principal) {
        AssignmentVerificationResponse verification = verificationService.rejectVerification(verificationId, reason,
                principal.getName());
        return ApiResponse.success("Từ chối xác minh thành công", verification, HttpStatus.OK.value());
    }

    @GetMapping("/{verificationId}/can-capture")
    public ApiResponse<Boolean> canCaptureImage(@PathVariable Long verificationId) {
        boolean canCapture = verificationService.canCaptureImage(verificationId);
        return ApiResponse.success("Kiểm tra quyền chụp ảnh thành công", canCapture, HttpStatus.OK.value());
    }
}