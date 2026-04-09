-- Migration: Create work_schedules table and update assignment_verifications
-- Purpose: Separate work schedule (plan) from attendance (actual) for photo verification

-- 1. Create work_schedules table
CREATE TABLE work_schedules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    
    -- Status tracking
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    
    -- Reason for work schedule
    reason VARCHAR(30) NOT NULL,
    
    -- Relationships
    assignment_verification_id BIGINT,
    verification_image_id BIGINT,
    attendance_id BIGINT,
    
    -- Photo tracking
    photo_captured_at DATETIME,
    
    -- Sync with attendance
    attendance_deleted BOOLEAN DEFAULT FALSE,
    sync_note VARCHAR(1000),
    last_synced_at DATETIME,
    
    -- Audit
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    
    -- Constraints
    UNIQUE KEY uk_assignment_date (assignment_id, scheduled_date),
    INDEX idx_employee_date (employee_id, scheduled_date),
    INDEX idx_status (status),
    INDEX idx_reason (reason),
    INDEX idx_verification (assignment_verification_id),
    INDEX idx_scheduled_date (scheduled_date),
    
    FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (assignment_verification_id) REFERENCES assignment_verifications(id) ON DELETE SET NULL,
    FOREIGN KEY (verification_image_id) REFERENCES verification_images(id) ON DELETE SET NULL,
    FOREIGN KEY (attendance_id) REFERENCES attendance(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Update assignment_verifications table
ALTER TABLE assignment_verifications 
ADD COLUMN cancelled_at DATETIME AFTER auto_approved_at,
ADD COLUMN cancelled_reason VARCHAR(500) AFTER cancelled_at,
ADD COLUMN transition_to_contract_mode BOOLEAN DEFAULT FALSE AFTER cancelled_reason;

-- 3. Add comment
ALTER TABLE work_schedules COMMENT = 'Work schedules for photo verification - source of truth for attendance generation';
ALTER TABLE assignment_verifications MODIFY COLUMN transition_to_contract_mode BOOLEAN DEFAULT FALSE COMMENT 'Chuyển sang chế độ CONTRACT_REQUIREMENT sau khi duyệt nhân viên mới';
