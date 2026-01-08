-- Add end_date column to assignments table
ALTER TABLE assignments ADD COLUMN end_date DATE;

-- Create backup table for deleted attendances
CREATE TABLE deleted_attendance_backup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_attendance_id BIGINT,
    assignment_id BIGINT,
    employee_id BIGINT,
    date DATE,
    work_hours DECIMAL(10,2),
    bonus DECIMAL(18,2),
    penalty DECIMAL(18,2),
    support_cost DECIMAL(18,2),
    is_overtime BOOLEAN,
    overtime_amount DECIMAL(18,2),
    description VARCHAR(1000),
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP,
    payload LONGTEXT,
    INDEX idx_assignment_id (assignment_id),
    INDEX idx_employee_id (employee_id)
);
