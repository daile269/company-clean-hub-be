-- Migration: Complete Image Verification System for Attendance
-- This migration creates the complete verification system from scratch
-- WARNING: This will recreate verification tables if they exist

-- 1. Add image verification setting to contracts table (check first)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contracts' 
    AND COLUMN_NAME = 'requires_image_verification');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE contracts ADD COLUMN requires_image_verification BOOLEAN DEFAULT FALSE',
    'SELECT "Column requires_image_verification already exists" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Create assignment_verifications table (recreate if exists)
DROP TABLE IF EXISTS verification_images;
DROP TABLE IF EXISTS assignment_verifications;

CREATE TABLE assignment_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL UNIQUE,
    reason ENUM('NEW_EMPLOYEE', 'CONTRACT_SETTING') NOT NULL,
    status ENUM('PENDING', 'IN_PROGRESS', 'APPROVED', 'AUTO_APPROVED') DEFAULT 'PENDING',
    max_attempts INT DEFAULT 5,
    current_attempts INT DEFAULT 0,
    approved_by BIGINT NULL,
    approved_at DATETIME(6) NULL,
    auto_approved_at DATETIME(6) NULL,
        created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    
    INDEX idx_assignment_verification_assignment (assignment_id),
    INDEX idx_assignment_verification_status (status),
    INDEX idx_assignment_verification_reason (reason),
    INDEX idx_assignment_verification_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Create verification_images table
CREATE TABLE verification_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_verification_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    attendance_id BIGINT NULL,
    cloudinary_public_id VARCHAR(512) NOT NULL,
    cloudinary_url VARCHAR(1024) NOT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    address VARCHAR(512) NULL,
    captured_at DATETIME(6) NOT NULL,
    face_confidence DECIMAL(5,4) NULL,
    image_quality_score DECIMAL(5,4) NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    FOREIGN KEY (assignment_verification_id) REFERENCES assignment_verifications(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (attendance_id) REFERENCES attendance(id) ON DELETE SET NULL,
    
    INDEX idx_verification_image_assignment_verification (assignment_verification_id),
    INDEX idx_verification_image_employee (employee_id),
    INDEX idx_verification_image_attendance (attendance_id),
    INDEX idx_verification_image_cloudinary (cloudinary_public_id),
    INDEX idx_verification_image_captured_at (captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Add optional reference from attendance to verification
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance' 
    AND COLUMN_NAME = 'assignment_verification_id');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE attendance ADD COLUMN assignment_verification_id BIGINT NULL',
    'SELECT "Column assignment_verification_id already exists" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add foreign key if not exists
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance' 
    AND COLUMN_NAME = 'assignment_verification_id' AND REFERENCED_TABLE_NAME = 'assignment_verifications');

SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE attendance ADD CONSTRAINT fk_attendance_verification FOREIGN KEY (assignment_verification_id) REFERENCES assignment_verifications(id) ON DELETE SET NULL',
    'SELECT "Foreign key already exists" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index if not exists  
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance' 
    AND INDEX_NAME = 'idx_attendance_verification');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE attendance ADD INDEX idx_attendance_verification (assignment_verification_id)',
    'SELECT "Index already exists" as message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Migration completed successfully