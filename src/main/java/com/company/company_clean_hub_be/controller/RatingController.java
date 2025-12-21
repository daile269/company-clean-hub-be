package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.CreateRatingRequest;
import com.company.company_clean_hub_be.dto.request.UpdateRatingRequest;
import com.company.company_clean_hub_be.dto.response.RatingResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.service.RatingService;
import com.company.company_clean_hub_be.service.UserService;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_CREATE')")
    public ApiResponse<RatingResponse> create(@RequestBody CreateRatingRequest req) {
        RatingResponse resp = ratingService.createRating(req);
        return ApiResponse.success("Tạo đánh giá thành công", resp, HttpStatus.CREATED.value());
    }

    @GetMapping("/contract/{contractId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_VIEW_CONTRACT')")
    public ApiResponse<List<RatingResponse>> getByContract(@PathVariable Long contractId) {
        String username = userService.getCurrentUsername();
        if (username == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        User currentUser = userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        String roleCode = currentUser.getRole() != null ? currentUser.getRole().getCode() : null;
        boolean isCustomerRole = "CUSTOMER".equalsIgnoreCase(roleCode);

        if (isCustomerRole) {
            Contract contract = contractRepository.findById(contractId).orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
            if (contract.getCustomer() == null || !contract.getCustomer().getId().equals(currentUser.getId())) {
                return ApiResponse.success("Lấy đánh giá theo hợp đồng thành công", List.of(), HttpStatus.OK.value());
            }
        }

        List<RatingResponse> list = ratingService.getRatingsByContract(contractId);
        return ApiResponse.success("Lấy đánh giá theo hợp đồng thành công", list, HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<RatingResponse>> getByEmployee(@PathVariable Long employeeId) {
        List<RatingResponse> list = ratingService.getRatingsByEmployee(employeeId);
        return ApiResponse.success("Lấy đánh giá theo nhân viên thành công", list, HttpStatus.OK.value());
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
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_UPDATE')")
    public ApiResponse<RatingResponse> update(@PathVariable Long id, @RequestBody UpdateRatingRequest req) {
        RatingResponse resp = ratingService.updateRating(id, req);
        return ApiResponse.success("Cập nhật đánh giá thành công", resp, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('REVIEW_DELETE')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ratingService.deleteRating(id);
        return ApiResponse.success("Xóa đánh giá thành công", null, HttpStatus.NO_CONTENT.value());
    }
}
