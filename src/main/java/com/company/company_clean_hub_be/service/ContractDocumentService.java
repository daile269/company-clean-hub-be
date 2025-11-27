package com.company.company_clean_hub_be.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.entity.ContractDocument;

public interface ContractDocumentService {
    ContractDocument uploadDocument(Long contractId, MultipartFile file) throws IOException;
    
    List<ContractDocument> uploadDocuments(Long contractId, MultipartFile[] files) throws IOException;
    
    void deleteDocument(Long contractId, Long documentId) throws IOException;
    
    List<ContractDocument> getContractDocuments(Long contractId);
}
