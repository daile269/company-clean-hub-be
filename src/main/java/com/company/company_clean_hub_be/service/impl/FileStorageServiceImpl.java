package com.company.company_clean_hub_be.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.company.company_clean_hub_be.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;
    private static final String FOLDER = "company-clean-hub/employee";

    @Override
    public String storeFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        log.info("Uploading file to Cloudinary: originalName={}", originalFileName);

        File tempFile = null;
        try (InputStream inputStream = file.getInputStream()) {
            tempFile = File.createTempFile("upload-", ".tmp");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                inputStream.transferTo(out);
            }

            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    tempFile,
                    ObjectUtils.asMap(
                            "folder", FOLDER,
                            "resource_type", "auto"
                    )
            );

            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");

            log.info("File uploaded successfully to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return publicId; // hoặc return secureUrl nếu muốn lấy URL trực tiếp
        } catch (IOException ex) {
            log.error("Failed to upload file to Cloudinary: {}", originalFileName, ex);
            throw new IOException("Failed to upload file to Cloudinary", ex);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }


    @Override
    public void deleteFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) return;
        
        log.info("Deleting file from Cloudinary: publicId={}", relativePath);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> deleteResult = cloudinary.uploader().destroy(
                relativePath,
                ObjectUtils.asMap("resource_type", "image")
            );
            
            String result = (String) deleteResult.get("result");
            log.info("File deletion result: {}", result);
            
            if (!"ok".equals(result)) {
                log.warn("Cloudinary delete returned: {}", result);
            }
        } catch (IOException ex) {
            log.error("Failed to delete file from Cloudinary: {}", relativePath, ex);
            throw new IOException("Failed to delete file from Cloudinary", ex);
        }
    }

    @Override
    public Resource loadFileAsResource(String relativePath) throws IOException {
        // For Cloudinary, we don't need to load as Resource
        // Instead, we return the URL directly from the entity
        // This method is not used for Cloudinary
        log.warn("loadFileAsResource is not supported for Cloudinary storage");
        throw new IOException("loadFileAsResource is not supported for Cloudinary storage");
    }
}
