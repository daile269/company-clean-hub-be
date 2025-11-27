-- Migration: Add contract_documents table for storing contract files (images/PDFs)
-- Version: 002
-- Date: 2025-11-27

CREATE TABLE IF NOT EXISTS contract_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    cloudinary_public_id VARCHAR(512) NOT NULL,
    document_type VARCHAR(50) NOT NULL COMMENT 'IMAGE or PDF',
    file_name VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key
    CONSTRAINT fk_contract_documents_contract_id 
        FOREIGN KEY (contract_id) 
        REFERENCES contracts(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_contract_id (contract_id),
    INDEX idx_uploaded_at (uploaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
