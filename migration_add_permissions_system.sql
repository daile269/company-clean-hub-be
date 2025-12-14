-- Migration: Add permissions system and profile change requests
-- Date: 2025-12-12

-- Step 1: Create role_permissions table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 2: Create profile_change_requests table
CREATE TABLE IF NOT EXISTS profile_change_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by_user_id BIGINT,
    approved_at DATETIME,
    rejection_reason TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_employee_id (employee_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Step 3: Assign permissions to roles by `code`
-- This uses INSERT ... SELECT so it will work regardless of numeric role IDs.

-- Quản lý tổng 1 (`QLT1`) - full permissions
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_ASSIGN' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_UPDATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_REASSIGN' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EXPORT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_MARK_PAID' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_ADVANCE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EXPORT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'COST_MANAGE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_CREATE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_EDIT' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_DELETE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'USER_MANAGE_ALL' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'APPROVE_PROFILE_CHANGE' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'AUDIT_VIEW' FROM roles WHERE code = 'QLT1' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Quản lý tổng 2 (`QLT2`) - similar to QLT1 but without USER_MANAGE_ALL
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_CREATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_EDIT' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_DELETE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_CREATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_EDIT' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_DELETE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_ASSIGN' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_CREATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_UPDATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_REASSIGN' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_DELETE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_CREATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EDIT' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_DELETE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_CREATE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EDIT' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_MARK_PAID' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_ADVANCE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EXPORT' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'COST_MANAGE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_VIEW' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'APPROVE_PROFILE_CHANGE' FROM roles WHERE code = 'QLT2' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Quản lý vùng (`QLV`)
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_VIEW' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_CREATE' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_EDIT' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_VIEW' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_VIEW' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_CREATE' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_UPDATE' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_REASSIGN' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_VIEW' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_CREATE' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EDIT' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

INSERT INTO role_permissions (role_id, permission)
SELECT id, 'COST_MANAGE' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_VIEW' FROM roles WHERE code = 'QLV' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Nhân viên (`EMPLOYEE`) - xem thông tin cá nhân và request profile change
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_VIEW_OWN' FROM roles WHERE code = 'EMPLOYEE' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'REQUEST_PROFILE_CHANGE' FROM roles WHERE code = 'EMPLOYEE' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Khách hàng (`CUSTOMER`) - xem thông tin cá nhân của khách
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_VIEW' FROM roles WHERE code = 'CUSTOMER' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Kế toán (`ACCOUNTANT`)
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'EMPLOYEE_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CUSTOMER_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ASSIGNMENT_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EDIT' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'ATTENDANCE_EXPORT' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_CREATE' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EDIT' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_MARK_PAID' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_ADVANCE' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'PAYROLL_EXPORT' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'COST_MANAGE' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'CONTRACT_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);
INSERT INTO role_permissions (role_id, permission)
SELECT id, 'AUDIT_VIEW' FROM roles WHERE code = 'ACCOUNTANT' ON DUPLICATE KEY UPDATE permission=VALUES(permission);

-- Notes:
-- 1. This maps permissions to roles by the `code` column: CUSTOMER, EMPLOYEE, QLT1, QLT2, QLV, ACCOUNTANT
-- 2. If your roles table uses different codes, adjust the WHERE code = '...' clauses accordingly
-- 3. Run this script after backing up the database
