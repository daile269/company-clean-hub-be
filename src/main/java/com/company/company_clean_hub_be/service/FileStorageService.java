package com.company.company_clean_hub_be.service;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeFile(MultipartFile file) throws IOException;
    void deleteFile(String relativePath) throws IOException;
    Resource loadFileAsResource(String relativePath) throws IOException;
}
