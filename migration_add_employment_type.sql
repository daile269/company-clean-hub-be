-- Migration: Thêm employmentType và các trường lương cho Employee

-- Thêm cột employment_type với giá trị mặc định CONTRACT_STAFF
ALTER TABLE employees ADD COLUMN employment_type VARCHAR(20) DEFAULT 'CONTRACT_STAFF';

-- Thêm các cột lương cho COMPANY_STAFF
ALTER TABLE employees ADD COLUMN monthly_salary DECIMAL(18,2) NULL COMMENT 'Lương tháng cho nhân viên văn phòng';
ALTER TABLE employees ADD COLUMN allowance DECIMAL(18,2) NULL COMMENT 'Phụ cấp';
ALTER TABLE employees ADD COLUMN insurance_salary DECIMAL(18,2) NULL COMMENT 'Bảo hiểm xã hội + Y tế';

-- Thêm cột lương cho CONTRACT_STAFF
-- Cập nhật tất cả nhân viên hiện tại thành CONTRACT_STAFF nếu NULL
UPDATE employees SET employment_type = 'CONTRACT_STAFF' WHERE employment_type IS NULL;

-- Đặt cột employment_type thành NOT NULL sau khi đã cập nhật
ALTER TABLE employees MODIFY employment_type VARCHAR(20) NOT NULL;

-- Tạo index cho employment_type để tăng tốc độ truy vấn
CREATE INDEX idx_employees_employment_type ON employees(employment_type);
