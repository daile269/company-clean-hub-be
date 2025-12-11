package com.company.company_clean_hub_be.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.ContractDocument;
import com.company.company_clean_hub_be.entity.ContractDocument.DocumentType;
import com.company.company_clean_hub_be.repository.ContractDocumentRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.service.ContractDocumentService;
import com.company.company_clean_hub_be.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractDocumentServiceImpl implements ContractDocumentService {
    
    private final ContractDocumentRepository contractDocumentRepository;
    private final ContractRepository contractRepository;
    private final FileStorageService fileStorageService;

    @Override
    public ContractDocument uploadDocument(Long contractId, MultipartFile file) throws IOException {
        log.debug("Start uploadDocument: contractId={}, fileName={}", contractId, file.getOriginalFilename());
        
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found with id: " + contractId));
        
        String fileName = file.getOriginalFilename();
        DocumentType documentType = getDocumentType(fileName, file.getContentType());
        
        String publicId = fileStorageService.storeFile(file);
        
        ContractDocument document = ContractDocument.builder()
                .contract(contract)
                .cloudinaryPublicId(publicId)
                .documentType(documentType)
                .fileName(fileName)
                .uploadedAt(LocalDateTime.now())
                .build();
        
        ContractDocument saved = contractDocumentRepository.save(document);
        log.debug("Upload successful: contractId={}, documentId={}, publicId={}, type={}", 
                contractId, saved.getId(), publicId, documentType);
        
        return saved;
    }

    @Override
    public List<ContractDocument> uploadDocuments(Long contractId, MultipartFile[] files) throws IOException {
        log.debug("Start uploadDocuments: contractId={}, fileCount={}", contractId, files.length);
        
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found with id: " + contractId));
        
        List<ContractDocument> uploadedDocuments = new ArrayList<>();
        
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            log.debug("Uploading file {}/{}: fileName={}, size={}, contentType={}",
                    i + 1, files.length, file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            try {
                String fileName = file.getOriginalFilename();
                DocumentType documentType = getDocumentType(fileName, file.getContentType());
                
                String publicId = fileStorageService.storeFile(file);
                
                ContractDocument document = ContractDocument.builder()
                        .contract(contract)
                        .cloudinaryPublicId(publicId)
                        .documentType(documentType)
                        .fileName(fileName)
                        .uploadedAt(LocalDateTime.now())
                        .build();
                
                ContractDocument saved = contractDocumentRepository.save(document);
                uploadedDocuments.add(saved);
                log.debug("Upload successful: contractId={}, documentId={}, publicId={}, type={}", 
                        contractId, saved.getId(), publicId, documentType);
            } catch (IOException ex) {
                log.error("Failed to upload file {}/{} for contractId={}: {}", 
                        i + 1, files.length, contractId, ex.getMessage(), ex);
                throw ex;
            }
        }
        
        return uploadedDocuments;
    }

    @Override
    public void deleteDocument(Long contractId, Long documentId) throws IOException {
        log.debug("Start deleteDocument: contractId={}, documentId={}", contractId, documentId);
        
        ContractDocument document = contractDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));
        
        if (!document.getContract().getId().equals(contractId)) {
            throw new RuntimeException("Document does not belong to contract: " + contractId);
        }
        
        try {
            fileStorageService.deleteFile(document.getCloudinaryPublicId());
            contractDocumentRepository.delete(document);
            log.debug("Delete successful: contractId={}, documentId={}, publicId={}", 
                    contractId, documentId, document.getCloudinaryPublicId());
        } catch (IOException ex) {
            log.error("Failed to delete document {}: {}", documentId, ex.getMessage(), ex);
            throw ex;
        }
    }

    @Override
    public List<ContractDocument> getContractDocuments(Long contractId) {
        log.debug("Fetching contract documents: contractId={}", contractId);
        
        List<ContractDocument> documents = contractDocumentRepository.findByContractId(contractId);
        log.debug("Found {} documents for contractId={}", documents.size(), contractId);
        
        return documents;
    }

    private DocumentType getDocumentType(String fileName, String contentType) {
        if (fileName == null) {
            return DocumentType.IMAGE;
        }
        
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".pdf") || (contentType != null && contentType.contains("pdf"))) {
            return DocumentType.PDF;
        }
        
        return DocumentType.IMAGE;
    }
}
