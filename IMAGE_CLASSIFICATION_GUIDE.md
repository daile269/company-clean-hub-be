# Hướng dẫn phân biệt loại ảnh trong hệ thống

## Tổng quan

Hệ thống có 2 loại ảnh chính được lưu trong bảng `verification_images`:

1. **Ảnh xác minh** (Verification Image)
2. **Ảnh chấm công** (Attendance Photo)

## 1. Ảnh xác minh (Verification Image)

### Mục đích
- Xác minh danh tính nhân viên mới
- Xác minh theo yêu cầu hợp đồng (contract requirement)

### Đặc điểm
- Liên kết với `assignment_verification_id` (NOT NULL)
- `attendance_id` có thể NULL hoặc NOT NULL
- Được chụp qua work schedule system
- Cần approval từ admin

### Khi nào tạo
- Nhân viên hoàn toàn mới (chưa có assignment nào khác)
- Hợp đồng có `requires_image_verification = true`

### Flow xử lý
```
1. Tạo Assignment → requiresVerification() = true
2. Tạo WorkSchedule với reason = NEW_EMPLOYEE_VERIFICATION hoặc CONTRACT_REQUIREMENT
3. Nhân viên chụp ảnh qua /api/work-schedules/capture
4. Tạo VerificationImage với assignment_verification_id
5. Tạo Attendance và link với VerificationImage
6. Admin approve/reject verification
```

## 2. Ảnh chấm công (Attendance Photo)

### Mục đích
- Chấm công hàng ngày
- Tracking vị trí và thời gian làm việc

### Đặc điểm
- Liên kết với `attendance_id` (NOT NULL)
- `assignment_verification_id` có thể NULL (nếu không cần verification)
- Được chụp trực tiếp cho attendance
- Không cần approval

### Khi nào tạo
- Nhân viên cũ chấm công bình thường
- Không cần verification process

### Flow xử lý
```
1. Tạo Assignment → requiresVerification() = false
2. Tạo Attendance trực tiếp
3. Nhân viên chụp ảnh chấm công
4. Tạo VerificationImage với attendance_id only
```

## 3. Phân biệt trong code

### Service Methods
```java
// Kiểm tra loại ảnh
boolean isAttendancePhoto = verificationService.isAttendancePhoto(image);
boolean isVerificationImage = verificationService.isVerificationImage(image);
```

### Database Schema
```sql
-- Ảnh xác minh
SELECT * FROM verification_images 
WHERE assignment_verification_id IS NOT NULL;

-- Ảnh chấm công thuần túy (không cần verification)
SELECT * FROM verification_images 
WHERE assignment_verification_id IS NULL 
AND attendance_id IS NOT NULL;

-- Ảnh vừa là verification vừa là attendance (nhân viên mới)
SELECT * FROM verification_images 
WHERE assignment_verification_id IS NOT NULL 
AND attendance_id IS NOT NULL;
```

## 4. Các trường hợp đặc biệt

### Nhân viên mới
- Ảnh đầu tiên: Vừa là verification image VÀ attendance photo
- `assignment_verification_id` NOT NULL
- `attendance_id` NOT NULL (sau khi chụp ảnh)

### Nhân viên cũ - Hợp đồng yêu cầu verification
- Mỗi ảnh: Vừa là verification image VÀ attendance photo
- `assignment_verification_id` NOT NULL
- `attendance_id` NOT NULL

### Nhân viên cũ - Hợp đồng bình thường
- Chỉ là attendance photo
- `assignment_verification_id` NULL
- `attendance_id` NOT NULL

## 5. Troubleshooting

### Vấn đề: Nhầm lẫn giữa 2 loại ảnh
**Nguyên nhân**: Không phân biệt rõ purpose của ảnh

**Giải pháp**: 
- Sử dụng `verificationService.isAttendancePhoto()` và `verificationService.isVerificationImage()`
- Kiểm tra `assignment_verification_id` và `attendance_id`

### Vấn đề: Nhân viên hỗ trợ 1 ngày (ONE_TIME contract)
**Nguyên nhân**: Logic tạo attendance không đúng với khoảng thời gian assignment

**Giải pháp**:
- Tạo attendance cho tất cả ngày làm việc trong khoảng `startDate` → `endDate` của assignment
- Không chỉ tạo 1 attendance cho ngày đầu tiên