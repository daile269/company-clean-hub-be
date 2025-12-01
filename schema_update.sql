-- Thêm cột additional_allowance vào bảng assignments
ALTER TABLE assignments 
ADD COLUMN additional_allowance DECIMAL(38,2) DEFAULT 0.00 COMMENT 'Phụ cấp thêm';

-- Tạo bảng lưu trữ các ngày làm việc trong tuần cho mỗi assignment
CREATE TABLE IF NOT EXISTS assignment_working_days (
    assignment_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    CONSTRAINT fk_assignment_working_days 
        FOREIGN KEY (assignment_id) 
        REFERENCES assignments(id) 
        ON DELETE CASCADE,
    INDEX idx_assignment_id (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Ví dụ insert dữ liệu mẫu
-- INSERT INTO assignment_working_days (assignment_id, day_of_week) VALUES 
-- (1, 'MONDAY'),
-- (1, 'TUESDAY'),
-- (1, 'WEDNESDAY'),
-- (1, 'THURSDAY'),
-- (1, 'FRIDAY');
