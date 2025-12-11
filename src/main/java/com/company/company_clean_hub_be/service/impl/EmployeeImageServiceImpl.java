package com.company.company_clean_hub_be.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.EmployeeImage;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.EmployeeImageRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.service.EmployeeImageService;
import com.company.company_clean_hub_be.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EmployeeImageServiceImpl implements EmployeeImageService {
    private final EmployeeImageRepository repository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;

    @Override
    public List<EmployeeImage> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<EmployeeImage> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public EmployeeImage save(EmployeeImage image) {
        return repository.save(image);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public EmployeeImage uploadImage(Long employeeId, MultipartFile file) throws IOException {
        log.info("EmployeeImageService.uploadImage - start: employeeId={}, originalFilename={}, size={}, contentType={}",
            employeeId, file.getOriginalFilename(), file.getSize(), file.getContentType());

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        log.info("Employee found: id={}, username={}", employee.getId(), employee.getUsername());

        // Upload to Cloudinary
        String publicId = fileStorageService.storeFile(file);
        
        log.info("File stored to Cloudinary: publicId={}", publicId);

        EmployeeImage image = EmployeeImage.builder()
            .employee(employee)
            .cloudinaryPublicId(publicId)
            .uploadedAt(LocalDateTime.now())
            .build();

        EmployeeImage saved = repository.save(image);
        log.info("EmployeeImage saved: imageId={}, employeeId={}, publicId={}", 
            saved.getId(), employeeId, publicId);
        return saved;
    }

    @Override
    public EmployeeImage replaceImage(Long employeeId, Long imageId, MultipartFile file) throws IOException {
        log.info("EmployeeImageService.replaceImage - start: employeeId={}, imageId={}, originalFilename={}, size={}",
                employeeId, imageId, file.getOriginalFilename(), file.getSize());

        EmployeeImage existing = repository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.IMAGE_NOT_FOUND));

        if (existing.getEmployee() == null || !existing.getEmployee().getId().equals(employeeId)) {
            throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        // Delete old image from Cloudinary
        if (existing.getCloudinaryPublicId() != null && !existing.getCloudinaryPublicId().isEmpty()) {
            log.info("Deleting existing image from Cloudinary: publicId={}", existing.getCloudinaryPublicId());
            fileStorageService.deleteFile(existing.getCloudinaryPublicId());
        }

        // Upload new image to Cloudinary
        String publicId = fileStorageService.storeFile(file);
        
        existing.setCloudinaryPublicId(publicId);
        existing.setUploadedAt(LocalDateTime.now());
        
        EmployeeImage saved = repository.save(existing);
        log.info("Replaced image saved: imageId={}, newPublicId={}", 
            saved.getId(), publicId);
        return saved;
    }

    @Override
    public void deleteImage(Long employeeId, Long imageId) throws IOException {
        log.info("EmployeeImageService.deleteImage - start: employeeId={}, imageId={}", employeeId, imageId);

        EmployeeImage existing = repository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.IMAGE_NOT_FOUND));

        if (existing.getEmployee() == null || !existing.getEmployee().getId().equals(employeeId)) {
            throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        // Delete from Cloudinary
        if (existing.getCloudinaryPublicId() != null && !existing.getCloudinaryPublicId().isEmpty()) {
            log.info("Deleting image from Cloudinary: publicId={}", existing.getCloudinaryPublicId());
            fileStorageService.deleteFile(existing.getCloudinaryPublicId());
        }
        
        repository.deleteById(imageId);
        log.info("EmployeeImage deleted: imageId={}", imageId);
    }

    @Override
    public Resource loadAsResource(String relativePath) throws IOException {
        log.info("EmployeeImageService.loadAsResource - relativePath={}", relativePath);
        return fileStorageService.loadFileAsResource(relativePath);
    }

    @Override
    public java.util.List<EmployeeImage> findByEmployeeId(Long employeeId) {
        log.info("EmployeeImageService.findByEmployeeId - employeeId={}", employeeId);
        return repository.findByEmployeeId(employeeId);
    }
}
