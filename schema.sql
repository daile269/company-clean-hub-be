create database company_clean;
use  company_clean

-- ==============================
-- DATABASE DUMP: TOÀN BỘ
-- ==============================

-- =================================
-- 1. ROLES
-- =================================
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50),
    name VARCHAR(255),
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO roles (id, code, name, description, created_at, updated_at) VALUES
(1, 'ADMIN', 'Admin', 'Quản trị hệ thống', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(2, 'ACCOUNTANT', 'Accountant', 'Kế toán', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(3, 'EMPLOYEE', 'Employee', 'Nhân viên', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(4, 'CUSTOMER', 'Customer', 'Khách hàng', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(5, 'MANAGER_GENERAL_1', 'Quản lý tổng 1', 'Quản lý cấp tổng 1', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(6, 'MANAGER_GENERAL_2', 'Quản lý tổng 2', 'Quản lý cấp tổng 2', '2025-11-20 14:00:00', '2025-11-20 14:00:00'),
(7, 'MANAGER_REGIONAL', 'Quản lý vùng', 'Quản lý các vùng cụ thể', '2025-11-20 14:00:00', '2025-11-20 14:00:00');

-- =================================
-- 2. USERS
-- =================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255),
    password VARCHAR(255),
    phone VARCHAR(255),
    email VARCHAR(255),
    role_id BIGINT,
    status VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

INSERT INTO users (id, username, password, phone, email, role_id, status, created_at, updated_at) VALUES
(1, 'admin', '$2y$10$examplehashadmin', '0901000001', 'admin@example.vn', 1, 'active', '2025-01-01 09:00:00', '2025-11-20 14:00:00'),
(2, 'ketoan_nga', '$2y$10$examplehashacct', '0901000002', 'nga.ketoan@acct.vn', 2, 'active', '2025-02-01 09:00:00', '2025-11-20 14:00:00'),

-- 5 nhân viên
(3, 'nv_trung', '$2y$10$hashnv1', '0912000003', 'trung.nguyen@company.vn', 3, 'active', '2025-03-01 08:30:00', '2025-11-20 14:00:00'),
(4, 'nv_hoa', '$2y$10$hashnv2', '0912000004', 'hoa.tran@company.vn', 3, 'active', '2025-03-05 08:30:00', '2025-11-20 14:00:00'),
(5, 'nv_hung', '$2y$10$hashnv3', '0912000005', 'hung.le@company.vn', 3, 'active', '2025-03-10 08:30:00', '2025-11-20 14:00:00'),
(6, 'nv_mai', '$2y$10$hashnv4', '0912000006', 'mai.pham@company.vn', 3, 'active', '2025-03-12 08:30:00', '2025-11-20 14:00:00'),
(7, 'nv_duy', '$2y$10$hashnv5', '0912000007', 'duy.vu@company.vn', 3, 'active', '2025-03-15 08:30:00', '2025-11-20 14:00:00'),

-- 3 khách hàng
(8, 'kh_viettel', '$2y$10$hashkh1', '02471000008', 'contact@viettel.example', 4, 'active', '2025-04-01 10:00:00', '2025-11-20 14:00:00'),
(9, 'kh_fpt', '$2y$10$hashkh2', '02473000009', 'contact@fpt.example', 4, 'active', '2025-04-03 10:00:00', '2025-11-20 14:00:00'),
(10, 'kh_vnpt', '$2y$10$hashkh3', '02474000010', 'contact@vnpt.example', 4, 'active', '2025-04-05 10:00:00', '2025-11-20 14:00:00');

-- =================================
-- 3. CUSTOMERS
-- =================================
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    customer_code VARCHAR(255),
    name VARCHAR(255),
    address VARCHAR(255),
    contact_info VARCHAR(255),
    tax_code VARCHAR(255),
    description VARCHAR(255),
    company VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (id) REFERENCES users(id)
);

INSERT INTO customers (id, customer_code, name, address, contact_info, tax_code, description, company, created_at, updated_at) VALUES
(8, 'KH0008', 'Nguyễn Văn A', 'Số 1, Đại lộ Thăng Long, Hà Nội', 'Phòng mua hàng: 024-7xxxxxxx', '0100123456', 'Khách hàng lớn, hợp đồng dịch vụ hằng năm', 'Viettel Group', '2025-04-01 10:00:00', '2025-11-20 14:00:00'),
(9, 'KH0009', 'Trần Thị B', 'Khu Công Nghệ Cao, Đà Nẵng', 'Phòng dự án: 0236-xxxxxxx', '0100654321', 'Dự án triển khai phần mềm', 'FPT Corporation', '2025-04-03 10:00:00', '2025-11-20 14:00:00'),
(10, 'KH0010', 'Lê Văn C', 'Số 57 Huỳnh Thúc Kháng, Hà Nội', 'Phòng hợp tác: 024-7xxxxxxx', '0100987654', 'Hợp tác logistics & IT', 'VNPT', '2025-04-05 10:00:00', '2025-11-20 14:00:00');

-- =================================
-- 4. EMPLOYEES
-- =================================
CREATE TABLE employees (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    cccd VARCHAR(255),
    address VARCHAR(255),
    bank_account VARCHAR(255),
    bank_name VARCHAR(255),
    employment_type VARCHAR(255),
    base_salary DOUBLE,
    daily_salary DOUBLE,
    insurance_bhxh DOUBLE,
    insurance_bhyt DOUBLE,
    insurance_bhtn DOUBLE,
    allowance DOUBLE,
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (id) REFERENCES users(id)
);

INSERT INTO employees (id, name, cccd, address, bank_account, bank_name, employment_type, base_salary, daily_salary, insurance_bhxh, insurance_bhyt, insurance_bhtn, allowance, description, created_at, updated_at) VALUES
(3, 'Nguyễn Văn Trung', '012345678901', 'Hà Nội, Ba Đình', '1234567890', 'Vietcombank - CN Hà Nội', 'Chính thức', 8000000, 320000, 8.0, 1.5, 1.0, 500000, 'Nhân viên kinh doanh chính', '2025-03-01 08:30:00', '2025-11-20 14:00:00'),
(4, 'Trần Thị Hoa', '023456789012', 'TP. Hồ Chí Minh, Quận 1', '2345678901', 'BIDV - CN HCM', 'Chính thức', 7500000, 300000, 8.0, 1.5, 1.0, 400000, 'Nhân viên kỹ thuật', '2025-03-05 08:30:00', '2025-11-20 14:00:00'),
(5, 'Lê Văn Hưng', '034567890123', 'Hải Phòng, Ngô Quyền', '3456789012', 'ACB - CN Hải Phòng', 'Tạm thời', 6000000, 240000, 0.0, 0.0, 0.0, 200000, 'Nhân viên hợp đồng ngắn hạn', '2025-03-10 08:30:00', '2025-11-20 14:00:00'),
(6, 'Phạm Thị Mai', '045678901234', 'Đà Nẵng, Thanh Khê', '4567890123', 'VietinBank - CN Đà Nẵng', 'Chính thức', 7000000, 280000, 8.0, 1.5, 1.0, 300000, 'Kỹ sư triển khai', '2025-03-12 08:30:00', '2025-11-20 14:00:00'),
(7, 'Vũ Văn Duy', '056789012345', 'Cần Thơ, Ninh Kiều', '5678901234', 'Sacombank - CN Cần Thơ', 'Tạm thời', 4500000, 180000, 0.0, 0.0, 0.0, 150000, 'Nhân viên vệ sinh & hỗ trợ', '2025-03-15 08:30:00', '2025-11-20 14:00:00');

-- =================================
-- 5. EMPLOYEE IMAGES
-- =================================
CREATE TABLE employee_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT,
    image_path VARCHAR(255),
    uploaded_at TIMESTAMP,
    FOREIGN KEY (employee_id) REFERENCES employees(id)
);

INSERT INTO employee_images (id, employee_id, image_path, uploaded_at) VALUES
(1, 3, '/images/employees/3_trung.jpg', '2025-03-02 09:00:00'),
(2, 4, '/images/employees/4_hoa.jpg', '2025-03-06 09:00:00'),
(3, 5, '/images/employees/5_hung.jpg', '2025-03-11 09:00:00'),
(4, 6, '/images/employees/6_mai.jpg', '2025-03-13 09:00:00'),
(5, 7, '/images/employees/7_duy.jpg', '2025-03-16 09:00:00');

-- =================================
-- 6. SERVICES
-- =================================
CREATE TABLE services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    price_from DOUBLE,
    price_to DOUBLE,
    main_image VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

INSERT INTO services (id, title, description, price_from, price_to, main_image, status, created_at, updated_at) VALUES
(1, 'Tư vấn thuế & kế toán', 'Dịch vụ tư vấn thuế, lập báo cáo tài chính, quyết toán', 2000000, 5000000, '/images/services/tuvantk.jpg', 'active', '2025-01-10 10:00:00', '2025-11-20 14:00:00'),
(2, 'Triển khai hệ thống IT', 'Cài đặt, cấu hình hệ thống, bảo trì, support', 5000000, 25000000, '/images/services/it_setup.jpg', 'active', '2025-01-20 10:00:00', '2025-11-20 14:00:00'),
(3, 'Dịch vụ vệ sinh công nghiệp', 'Vệ sinh văn phòng, vệ sinh theo công trình', 500000, 2000000, '/images/services/cleaning.jpg', 'active', '2025-02-01 10:00:00', '2025-11-20 14:00:00'),
(4, 'Vận chuyển & logistics', 'Dịch vụ vận chuyển nội thành và liên tỉnh', 1000000, 10000000, '/images/services/logistics.jpg', 'active', '2025-02-10 10:00:00', '2025-11-20 14:00:00');


