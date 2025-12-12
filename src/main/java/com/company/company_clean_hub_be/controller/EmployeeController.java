package com.company.company_clean_hub_be.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.dto.request.EmployeeRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.EmployeeExportDto;
import com.company.company_clean_hub_be.dto.response.EmployeeResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.EmployeeImage;
import com.company.company_clean_hub_be.service.EmployeeImageService;
import com.company.company_clean_hub_be.service.EmployeeService;
import com.company.company_clean_hub_be.service.ExcelExportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(value = "/api/employees")
public class EmployeeController {
    private final EmployeeService employeeService;
    private final EmployeeImageService employeeImageService;
    private final ExcelExportService excelExportService;

    @GetMapping
    public ApiResponse<List<EmployeeResponse>> getAllEmployees() {
        List<EmployeeResponse> employees = employeeService.getAllEmployees();
        return ApiResponse.success("Lấy danh sách nhân viên thành công", employees, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<EmployeeResponse>> getEmployeesWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.EmploymentType employmentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<EmployeeResponse> employees = employeeService.getEmployeesWithFilter(keyword, employmentType, page, pageSize);
        return ApiResponse.success("Lấy danh sách nhân viên thành công", employees, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<EmployeeResponse> getEmployeeById(@PathVariable Long id) {
        EmployeeResponse employee = employeeService.getEmployeeById(id);
        return ApiResponse.success("Lấy thông tin nhân viên thành công", employee, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.createEmployee(request);
        return ApiResponse.success("Tạo nhân viên thành công", employee, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<EmployeeResponse> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.updateEmployee(id, request);
        return ApiResponse.success("Cập nhật nhân viên thành công", employee, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ApiResponse.success("Xóa nhân viên thành công", null, HttpStatus.OK.value());
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<EmployeeImage>> uploadEmployeeImages(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile[] files) throws IOException {

        log.info("Start uploadEmployeeImages: employeeId={}, fileCount={}", id, files.length);
        
        List<EmployeeImage> uploadedImages = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            log.info("Uploading file {}/{}: originalFilename={}, size={}, contentType={}",
                    i + 1, files.length, file.getOriginalFilename(), file.getSize(), file.getContentType());
            try {
                EmployeeImage saved = employeeImageService.uploadImage(id, file);
                uploadedImages.add(saved);
                log.info("Upload successful: employeeId={}, imageId={}, url={}", id, saved.getId(), saved.getCloudinaryPublicId());
            } catch (IOException ex) {
                log.error("Failed to upload image {}/{} for employeeId={}: {}", i + 1, files.length, id, ex.getMessage(), ex);
                throw ex;
            }
        }
        
        return ApiResponse.success("Upload " + uploadedImages.size() + " ảnh nhân viên thành công", uploadedImages, HttpStatus.CREATED.value());
    }

    @PostMapping(value = "/{id}/images/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmployeeImage> uploadEmployeeImage(@PathVariable Long id,
                                                          @RequestParam("file") MultipartFile file) throws IOException {

        log.info("Start uploadEmployeeImage: employeeId={}, originalFilename={}, size={}, contentType={}",
                id, file.getOriginalFilename(), file.getSize(), file.getContentType());
        try {
            EmployeeImage saved = employeeImageService.uploadImage(id, file);
            log.info("Upload successful: employeeId={}, imageId={}, url={}", id, saved.getId(), saved.getCloudinaryPublicId());
            return ApiResponse.success("Upload ảnh nhân viên thành công", saved, HttpStatus.CREATED.value());
        } catch (IOException ex) {
            log.error("Failed to upload image for employeeId={}: {}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @PutMapping(value = "/{id}/images/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmployeeImage> replaceEmployeeImage(@PathVariable Long id,
                                                           @PathVariable Long imageId,
                                                           @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Start replaceEmployeeImage: employeeId={}, imageId={}, originalFilename={}, size={}",
                id, imageId, file.getOriginalFilename(), file.getSize());
        
        try {
            EmployeeImage saved = employeeImageService.replaceImage(id, imageId, file);
            log.info("Replace successful: employeeId={}, imageId={}, url={}", id, saved.getId(), saved.getCloudinaryPublicId());
            return ApiResponse.success("Cập nhật ảnh nhân viên thành công", saved, HttpStatus.OK.value());
        } catch (IOException ex) {
            log.error("Failed to replace image for employeeId={}: {}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<Void> deleteEmployeeImage(@PathVariable Long id,
                                                 @PathVariable Long imageId) throws IOException {
        log.info("Start deleteEmployeeImage: employeeId={}, imageId={}", id, imageId);
        
        try {
            employeeImageService.deleteImage(id, imageId);
            log.info("Delete successful: employeeId={}, imageId={}", id, imageId);
            return ApiResponse.success("Xóa ảnh nhân viên thành công", null, HttpStatus.OK.value());
        } catch (IOException ex) {
            log.error("Failed to delete image for employeeId={}: {}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @DeleteMapping("/{id}/images")
    public ApiResponse<Void> deleteAllEmployeeImages(@PathVariable Long id) throws IOException {
        log.info("Start deleteAllEmployeeImages: employeeId={}", id);
        
        try {
            List<EmployeeImage> images = employeeImageService.findByEmployeeId(id);
            for (EmployeeImage image : images) {
                employeeImageService.deleteImage(id, image.getId());
            }
            log.info("Delete all successful: employeeId={}, deletedCount={}", id, images.size());
            return ApiResponse.success("Xóa tất cả ảnh nhân viên thành công", null, HttpStatus.OK.value());
        } catch (IOException ex) {
            log.error("Failed to delete all images for employeeId={}: {}", id, ex.getMessage(), ex);
            throw ex;
        }
    }

    @GetMapping("/{id}/images")
    public ApiResponse<List<EmployeeImage>> getImagesByEmployeeId(@PathVariable Long id) {
        log.info("GET /api/employees/{}/images - fetch images for employee", id);
        
        try {
            List<EmployeeImage> images = employeeImageService.findByEmployeeId(id);
            log.info("Fetched {} images for employee {}", images.size(), id);
            return ApiResponse.success("Lấy danh sách ảnh nhân viên thành công", images, HttpStatus.OK.value());
        } catch (Exception ex) {
            log.error("Failed to fetch images for employeeId={}: {}", id, ex.getMessage(), ex);
            return ApiResponse.success("Lấy danh sách ảnh nhân viên thành công", new ArrayList<>(), HttpStatus.OK.value());
        }
    }

    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> exportEmployeesToExcel(
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.EmploymentType employmentType) {
        log.info("Export employees requested: employmentType={}", employmentType);
        try {
            List<EmployeeExportDto> employees;
            if (employmentType != null) {
                employees = employeeService.getEmployeesForExportByType(employmentType);
            } else {
                employees = employeeService.getAllEmployeesForExport();
            }
            ByteArrayResource resource = excelExportService.exportEmployeesToExcel(employees);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=Danh_sach_nhan_vien.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            log.error("Error exporting employees", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
