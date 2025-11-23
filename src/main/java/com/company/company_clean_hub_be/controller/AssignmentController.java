package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/assignments")
public class AssignmentController {
    private final AssignmentService assignmentService;

    @GetMapping
    public ApiResponse<List<AssignmentResponse>> getAllAssignments() {
        List<AssignmentResponse> assignments = assignmentService.getAllAssignments();
        return ApiResponse.success("Lấy danh sách phân công thành công", assignments, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<AssignmentResponse> getAssignmentById(@PathVariable Long id) {
        AssignmentResponse assignment = assignmentService.getAssignmentById(id);
        return ApiResponse.success("Lấy thông tin phân công thành công", assignment, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<AssignmentResponse> createAssignment(@Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse assignment = assignmentService.createAssignment(request);
        return ApiResponse.success("Tạo phân công thành công", assignment, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<AssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse assignment = assignmentService.updateAssignment(id, request);
        return ApiResponse.success("Cập nhật phân công thành công", assignment, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ApiResponse.success("Xóa phân công thành công", null, HttpStatus.OK.value());
    }
}
