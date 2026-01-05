package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.CreateRatingRequest;
import com.company.company_clean_hub_be.dto.request.UpdateRatingRequest;
import com.company.company_clean_hub_be.dto.response.RatingResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_CREATE') or @securityCheck.isAssignmentOwnedByCurrentUser(#req.assignmentId) or @securityCheck.isEmployeeAssignedToAssignment(#req.assignmentId)")
    public ApiResponse<RatingResponse> create(@RequestBody CreateRatingRequest req) {
        RatingResponse resp = ratingService.createRating(req);
        return ApiResponse.success("Tạo đánh giá thành công", resp, HttpStatus.CREATED.value());
    }

    @GetMapping("/contract/{contractId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_CONTRACT')")
    public ApiResponse<List<RatingResponse>> getByContract(@PathVariable Long contractId) {
        List<RatingResponse> list = ratingService.getRatingsByContract(contractId);
        return ApiResponse.success("Lấy đánh giá theo hợp đồng thành công", list, HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_ALL') or @securityCheck.isEmployeeSelf(#employeeId)")
    public ApiResponse<List<RatingResponse>> getByEmployee(@PathVariable Long employeeId) {
        List<RatingResponse> list = ratingService.getRatingsByEmployee(employeeId);
        return ApiResponse.success("Lấy đánh giá theo nhân viên thành công", list, HttpStatus.OK.value());
    }

    @GetMapping("/customer/{customerId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_ALL') or @securityCheck.isEmployeeAssignedToCustomer(#customerId) or @securityCheck.isCustomerSelf(#customerId)")
    public ApiResponse<List<RatingResponse>> getByCustomer(@PathVariable Long customerId) {
        List<RatingResponse> list = ratingService.getRatingsByCustomer(customerId);
        return ApiResponse.success("Lấy đánh giá theo khách hàng thành công", list, HttpStatus.OK.value());
    }

    @GetMapping("/reviewer/{reviewerId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_ALL') or @securityCheck.isEmployeeSelf(#reviewerId)")
    public ApiResponse<List<RatingResponse>> getByReviewer(@PathVariable Long reviewerId) {
        List<RatingResponse> list = ratingService.getRatingsByReviewer(reviewerId);
        return ApiResponse.success("Lấy đánh giá theo người tạo (reviewer) thành công", list, HttpStatus.OK.value());
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_ALL')")
    public ApiResponse<PageResponse<RatingResponse>> getAllWithFilter(
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResponse<RatingResponse> resp = ratingService.getRatingsWithFilter(contractId, assignmentId, customerId, employeeId, page, pageSize);
        return ApiResponse.success("Lấy danh sách đánh giá thành công", resp, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<RatingResponse> get(@PathVariable Long id) {
        RatingResponse resp = ratingService.getRating(id);
        return ApiResponse.success("Lấy đánh giá thành công", resp, HttpStatus.OK.value());
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_UPDATE') or @securityCheck.isRatingCreatedByCurrentUser(#id)")
    public ApiResponse<RatingResponse> update(@PathVariable Long id, @RequestBody UpdateRatingRequest req) {
        RatingResponse resp = ratingService.updateRating(id, req);
        return ApiResponse.success("Cập nhật đánh giá thành công", resp, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_DELETE') or @securityCheck.isRatingCreatedByCurrentUser(#id) or @securityCheck.isRatingOwnedByCustomer(#id)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ratingService.deleteRating(id);
        return ApiResponse.success("Xóa đánh giá thành công", null, HttpStatus.NO_CONTENT.value());
    }
}
