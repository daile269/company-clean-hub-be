# Hệ Thống Phân Quyền - Hướng Dẫn

## Tổng Quan
Hệ thống phân quyền dựa trên **Permission-Based Access Control**. Mỗi Role có một tập hợp Permissions, và các endpoint API được bảo vệ bằng `@PreAuthorize` với permission tương ứng.

## Danh Sách Permissions

### Employee Management
- `EMPLOYEE_VIEW` - Xem danh sách/thông tin nhân viên
- `EMPLOYEE_VIEW_OWN` - Xem thông tin cá nhân (dành cho nhân viên)
- `EMPLOYEE_CREATE` - Tạo nhân viên mới
- `EMPLOYEE_EDIT` - Chỉnh sửa thông tin nhân viên
- `EMPLOYEE_DELETE` - Xóa nhân viên

### Customer Management
- `CUSTOMER_VIEW` - Xem thông tin khách hàng
- `CUSTOMER_CREATE` - Tạo khách hàng mới
- `CUSTOMER_EDIT` - Chỉnh sửa khách hàng
- `CUSTOMER_DELETE` - Xóa khách hàng
- `CUSTOMER_ASSIGN` - Phân công khách hàng cho quản lý

### Assignment Management
- `ASSIGNMENT_VIEW` - Xem phân công
- `ASSIGNMENT_CREATE` - Tạo phân công
- `ASSIGNMENT_UPDATE` - Cập nhật phân công
- `ASSIGNMENT_REASSIGN` - Điều động nhân viên
- `ASSIGNMENT_DELETE` - Xóa phân công

### Attendance Management
- `ATTENDANCE_VIEW` - Xem chấm công
- `ATTENDANCE_CREATE` - Tạo chấm công
- `ATTENDANCE_EDIT` - Chỉnh sửa chấm công
- `ATTENDANCE_DELETE` - Xóa chấm công
- `ATTENDANCE_EXPORT` - Xuất Excel chấm công

### Payroll Management
- `PAYROLL_VIEW` - Xem bảng lương
- `PAYROLL_CREATE` - Tạo bảng lương
- `PAYROLL_EDIT` - Chỉnh sửa bảng lương
- `PAYROLL_MARK_PAID` - Đánh dấu đã trả lương
- `PAYROLL_ADVANCE` - Quản lý ứng lương
- `PAYROLL_EXPORT` - Xuất Excel bảng lương

### Other Permissions
- `COST_MANAGE` - Quản lý chi phí (bonus, penalty, allowance)
- `CONTRACT_VIEW`, `CONTRACT_CREATE`, `CONTRACT_EDIT`, `CONTRACT_DELETE`
- `USER_VIEW`, `USER_CREATE`, `USER_EDIT`, `USER_DELETE`, `USER_MANAGE_ALL`
- `REQUEST_PROFILE_CHANGE` - Yêu cầu thay đổi thông tin cá nhân
- `APPROVE_PROFILE_CHANGE` - Phê duyệt thay đổi thông tin
- `AUDIT_VIEW` - Xem lịch sử thay đổi

## Role Mapping (Gợi Ý)

### Quản Lý Tổng 1 (GENERAL_MANAGER_1)
**Quyền:** Toàn quyền - tất cả permissions
- Quản lý toàn hệ thống
- Tạo/sửa/xóa mọi thông tin
- Xuất báo cáo toàn bộ
- Phê duyệt profile change requests

### Quản Lý Tổng 2 (GENERAL_MANAGER_2)
**Quyền:** Tương tự Quản lý tổng 1, có thể bị giới hạn scope
- Quản lý trong phạm vi được phân công
- Phân công khách hàng cho Quản lý vùng

### Kế Toán (ACCOUNTANT)
**Quyền:**
- VIEW: Employee, Customer, Assignment, Attendance, Payroll, Contract, Audit
- EDIT: Attendance, Payroll
- COST_MANAGE
- PAYROLL_*: tất cả quyền payroll (mark paid, advance, export)
- ATTENDANCE_EXPORT, PAYROLL_EXPORT

### Quản Lý Vùng (REGIONAL_MANAGER)
**Quyền:**
- EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_EDIT
- CUSTOMER_VIEW
- ASSIGNMENT_*: view, create, update, reassign
- ATTENDANCE_VIEW, ATTENDANCE_CREATE, ATTENDANCE_EDIT
- COST_MANAGE
- CONTRACT_VIEW

### Nhân Viên (EMPLOYEE_USER)
**Quyền:**
- EMPLOYEE_VIEW_OWN
- REQUEST_PROFILE_CHANGE

## Cách Sử Dụng

### 1. Secure Endpoint với @PreAuthorize

```java
@GetMapping("/api/employees")
@PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
public ResponseEntity<?> getAllEmployees() {
    // ...
}

@PostMapping("/api/employees")
@PreAuthorize("hasAuthority('EMPLOYEE_CREATE')")
public ResponseEntity<?> createEmployee(@RequestBody EmployeeRequest req) {
    // ...
}
```

### 2. Check Multiple Permissions

```java
@GetMapping("/api/employees/{id}")
@PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW', 'EMPLOYEE_VIEW_OWN')")
public ResponseEntity<?> getEmployeeById(@PathVariable Long id) {
    // Allow both managers and employee viewing own info
}
```

### 3. Programmatic Permission Check

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
boolean hasPermission = auth.getAuthorities().stream()
    .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_EDIT"));
```

### 4. Profile Change Request Flow

**Nhân viên:**
```bash
POST /api/profile-change-requests
{
  "employeeId": 1,
  "changeType": "BANK_INFO",
  "fieldName": "bankAccount",
  "oldValue": "1234567890",
  "newValue": "0987654321",
  "reason": "Đổi tài khoản ngân hàng mới"
}
```

**Quản lý duyệt:**
```bash
PUT /api/profile-change-requests/{id}/approve
# hoặc
PUT /api/profile-change-requests/{id}/reject?rejectionReason=...
```

## Migration Database

**Chạy file:** `migration_add_permissions_system.sql`

**Lưu ý:**
1. Backup database trước khi chạy migration
2. Kiểm tra và điều chỉnh role IDs trong INSERT statements (1, 2, 3, 4) để khớp với roles thực tế
3. Chạy query để xem role IDs hiện tại:
   ```sql
   SELECT id, name, code FROM roles;
   ```
4. Cập nhật permissions trong SQL nếu cần thay đổi mapping

## Testing

### 1. Test Permission Loading
- Login với user có role cụ thể
- Kiểm tra JWT token có chứa permissions không
- Verify trong UserPrincipal.authorities

### 2. Test Endpoint Security
- Gọi endpoint với user không có quyền → 403 Forbidden
- Gọi endpoint với user có quyền → 200 OK

### 3. Test Profile Change Request
- Nhân viên tạo request
- Quản lý approve/reject
- Verify status updates

## Mở Rộng

### Thêm Permission Mới
1. Thêm vào enum `Permission.java`
2. Map vào Role trong migration SQL hoặc qua API
3. Secure endpoint với `@PreAuthorize("hasAuthority('NEW_PERMISSION')")`

### Scope-Based Authorization (Future)
- Thêm trường `managedRegion` hoặc `managedCustomers` vào User
- Implement custom Permission Evaluator
- Check scope trong service layer

## Troubleshooting

**Lỗi 403 Forbidden:**
- Kiểm tra user có role và permissions chưa
- Verify JWT token decode đúng
- Check spelling của permission name

**Permissions không load:**
- Verify migration đã chạy
- Check Role.permissions fetch = EAGER
- Restart application để reload cache

## API Endpoints Đã Secure

### Employee
- GET `/api/employees` - EMPLOYEE_VIEW
- GET `/api/employees/filter` - EMPLOYEE_VIEW
- GET `/api/employees/{id}` - EMPLOYEE_VIEW hoặc EMPLOYEE_VIEW_OWN
- POST `/api/employees` - EMPLOYEE_CREATE
- PUT `/api/employees/{id}` - EMPLOYEE_EDIT
- DELETE `/api/employees/{id}` - EMPLOYEE_DELETE

### Customer
- GET `/api/customers` - CUSTOMER_VIEW
- GET `/api/customers/filter` - CUSTOMER_VIEW
- GET `/api/customers/{id}` - CUSTOMER_VIEW
- POST `/api/customers` - CUSTOMER_CREATE
- PUT `/api/customers/{id}` - CUSTOMER_EDIT
- DELETE `/api/customers/{id}` - CUSTOMER_DELETE

### Profile Change Requests
- POST `/api/profile-change-requests` - REQUEST_PROFILE_CHANGE
- GET `/api/profile-change-requests/{id}` - APPROVE_PROFILE_CHANGE hoặc REQUEST_PROFILE_CHANGE
- GET `/api/profile-change-requests/employee/{id}` - APPROVE_PROFILE_CHANGE hoặc REQUEST_PROFILE_CHANGE
- GET `/api/profile-change-requests` - APPROVE_PROFILE_CHANGE
- PUT `/api/profile-change-requests/{id}/approve` - APPROVE_PROFILE_CHANGE
- PUT `/api/profile-change-requests/{id}/reject` - APPROVE_PROFILE_CHANGE
- PUT `/api/profile-change-requests/{id}/cancel` - REQUEST_PROFILE_CHANGE

**TODO:** Secure thêm các controller còn lại (Assignment, Attendance, Payroll, Contract, User)
