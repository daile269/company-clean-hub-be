# Work Schedule Implementation - Phase 1 Complete

## Tổng quan
Đã tách biệt 2 tính năng:
1. **Xác minh nhân viên mới** (NEW_EMPLOYEE_VERIFICATION) - cần duyệt
2. **Chấm công bằng ảnh** (CONTRACT_REQUIREMENT) - không cần duyệt

## Files đã tạo

### Entities & Enums
- ✅ `WorkSchedule.java` - Entity chính cho lịch làm việc
- ✅ `WorkScheduleStatus.java` - SCHEDULED, VERIFIED, MISSED, CANCELLED
- ✅ `WorkScheduleReason.java` - NEW_EMPLOYEE_VERIFICATION, CONTRACT_REQUIREMENT
- ✅ Updated `VerificationStatus.java` - thêm CANCELLED
- ✅ Updated `AssignmentVerification.java` - thêm cancelled_at, cancelled_reason, transition_to_contract_mode

### Repository
- ✅ `WorkScheduleRepository.java` - Queries cho work_schedule

### Service Layer
- ✅ `WorkScheduleService.java` - Interface
- ✅ `WorkScheduleServiceImpl.java` - Implementation
- ✅ `VerificationServiceImpl_NEW.java` - Version mới tích hợp work_schedule

### Controller
- ✅ `WorkScheduleController.java` - REST API endpoints

### DTO
- ✅ `WorkScheduleResponse.java`
- ✅ `WorkScheduleCaptureRequest.java`

### Migration
- ✅ `V009__create_work_schedules.sql` - Database schema

### Scheduler
- ✅ Updated `VerificationScheduler.java` - thêm mark missed job (23:00 daily)

## Endpoints mới

### Work Schedule APIs
```
GET    /api/work-schedules/assignment/{assignmentId}
GET    /api/work-schedules/employee/{employeeId}?startDate=&endDate=
GET    /api/work-schedules/{id}
POST   /api/work-schedules/capture
GET    /api/work-schedules/{id}/can-capture
PUT    /api/work-schedules/{id}/cancel
POST   /api/work-schedules/{id}/create-attendance
```

## Các bước tiếp theo (TODO)

### 1. Backup file cũ và thay thế
```bash
# Backup
mv VerificationServiceImpl.java VerificationServiceImpl_OLD.java

# Rename new file
mv VerificationServiceImpl_NEW.java VerificationServiceImpl.java
```

### 2. Cập nhật AttendanceService
Cần tích hợp work_schedule vào logic tạo attendance:
- `autoGenerateAttendances()` - chấm công thường (không tạo work_schedule)
- `autoGenerateAttendancesWithVerification()` - tạo work_schedule thay vì attendance trực tiếp

### 3. Cập nhật AssignmentService
Khi tạo assignment mới:
```java
if (isNewEmployee) {
    // Tạo verification + work_schedule
    createVerificationWithWorkSchedule(assignment);
} else if (contract.requiresImageVerification) {
    // Chỉ tạo work_schedule
    createWorkScheduleOnly(assignment);
} else {
    // Tạo attendance trực tiếp
    createAttendancesDirectly(assignment);
}
```

### 4. Cập nhật ContractService
Khi toggle `requiresImageVerification`:
```java
contractService.updateRequiresImageVerification(contractId, enabled);
// Sẽ gọi verificationService.syncContractVerificationState()
```

### 5. Thêm API cho Admin
- Xem danh sách work_schedule MISSED
- Xử lý MISSED (tạo attendance hoặc cancel)
- Báo cáo tỷ lệ chấm công

### 6. Testing
- Test nhân viên mới: chụp 5 lần → auto-approve
- Test chấm công hợp đồng: chụp → sinh attendance
- Test toggle verification: bật/tắt giữa chừng
- Test cron job: mark MISSED lúc 23:00
- Test đồng bộ: xóa attendance → sync work_schedule

### 7. Frontend Integration
Cần cập nhật:
- Form chụp ảnh: gọi `/api/work-schedules/capture`
- Hiển thị lịch làm việc: query work_schedules
- Admin panel: xử lý MISSED schedules

## Logic Flow

### A. Nhân viên mới (NEW_EMPLOYEE_VERIFICATION)
```
1. Tạo assignment → phát hiện nhân viên mới
2. Tạo verification (status=PENDING)
3. Tạo work_schedules cho tháng (reason=NEW_EMPLOYEE_VERIFICATION)
4. Nhân viên chụp ảnh:
   - work_schedule.status = VERIFIED
   - Tạo attendance
5. Hết ngày không chụp (23:00):
   - work_schedule.status = MISSED
6. Đủ 5 lần VERIFIED hoặc admin duyệt:
   - Tạo attendance cho TẤT CẢ work_schedule (cả MISSED)
   - Nếu contract bật verification → chuyển sang CONTRACT_REQUIREMENT
   - Nếu không → hoàn thành, sinh attendance trực tiếp
```

### B. Chấm công hợp đồng (CONTRACT_REQUIREMENT)
```
1. Tạo assignment với contract.requiresImageVerification=true
2. Tạo work_schedules (reason=CONTRACT_REQUIREMENT, NO verification)
3. Nhân viên chụp ảnh:
   - work_schedule.status = VERIFIED
   - Tạo attendance
4. Không chụp:
   - work_schedule.status = MISSED (23:00)
   - KHÔNG có attendance → không tính lương
```

### C. Chấm công thường
```
1. Tạo assignment
2. KHÔNG tạo work_schedule
3. Tạo attendance trực tiếp cho tất cả ngày làm việc
```

## Database Schema

### work_schedules
```sql
- id: BIGINT PK
- assignment_id: BIGINT FK
- employee_id: BIGINT FK
- scheduled_date: DATE
- status: ENUM (SCHEDULED, VERIFIED, MISSED, CANCELLED)
- reason: ENUM (NEW_EMPLOYEE_VERIFICATION, CONTRACT_REQUIREMENT)
- assignment_verification_id: BIGINT FK (nullable)
- verification_image_id: BIGINT FK (nullable)
- attendance_id: BIGINT FK (nullable)
- photo_captured_at: DATETIME
- attendance_deleted: BOOLEAN
- sync_note: VARCHAR(1000)
- last_synced_at: DATETIME
- created_at, updated_at: DATETIME
```

### assignment_verifications (updated)
```sql
+ cancelled_at: DATETIME
+ cancelled_reason: VARCHAR(500)
+ transition_to_contract_mode: BOOLEAN
```

## Rules

1. work_schedule CHỈ tồn tại khi: nhân viên mới HOẶC contract bật verification
2. Nhân viên mới: cần duyệt, đủ 5 lần VERIFIED → auto-approve
3. Auto-approve/Admin approve → sinh attendance cho TẤT CẢ (cả MISSED)
4. Chấm công hình ảnh: KHÔNG cần duyệt, chụp là sinh attendance
5. MISSED chỉ là đánh dấu, vẫn sinh attendance khi approve (nhân viên mới)
6. Bật verification → xóa attendance tương lai, tạo work_schedule
7. Tắt verification → sinh attendance cho tất cả work_schedule, hủy verification
8. Nhân viên mới + Contract verification → sau approve chuyển sang CONTRACT_REQUIREMENT
9. work_schedule ↔ attendance phải đồng bộ 100%, có sync_note
10. Cron 23:00 mark MISSED, nhân viên có thể chụp đến 22:59

## Notes

- File `VerificationServiceImpl_NEW.java` cần rename thành `VerificationServiceImpl.java`
- Cần test kỹ trước khi deploy
- Cần migration data nếu có data cũ
- Cần document API cho frontend team
