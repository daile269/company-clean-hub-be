package com.company.company_clean_hub_be.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.entity.EmployeeImage;

public interface EmployeeImageService {
    List<EmployeeImage> findAll();
    Optional<EmployeeImage> findById(Long id);
    EmployeeImage save(EmployeeImage image);
    void deleteById(Long id);

    // New higher-level operations
    EmployeeImage uploadImage(Long employeeId, MultipartFile file) throws IOException;
    EmployeeImage replaceImage(Long employeeId, Long imageId, MultipartFile file) throws IOException;
    void deleteImage(Long employeeId, Long imageId) throws IOException;
    Resource loadAsResource(String relativePath) throws IOException;
    java.util.List<EmployeeImage> findByEmployeeId(Long employeeId);
}
