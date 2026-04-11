# Tóm tắt các sửa lỗi cho deployment

## Vấn đề đã sửa

### 1. Nhầm lẫn giữa chấm công và ảnh xác minh

**Vấn đề**: Hệ thống không phân biệt rõ ràng giữa:
- Ảnh chấm công (attendance photo) - dùng để chấm công hàng ngày
- Ảnh xác minh (verification image) - dùng để xác minh nhân viên mới/theo yêu cầu hợp đồng

**Giải pháp**:
- Thêm methods `isAttendancePhoto()` và `isVerificationImage()` trong `VerificationService`
- Sửa logic trong `WorkScheduleServiceImpl.capturePhoto()` để link ảnh với attendance sau khi tạo attendance
- Tạo documentation `IMAGE_CLASSIFICATION_GUIDE.md` để làm rõ logic

**Files đã sửa**:
- `VerificationService.java` - thêm interface methods
- `VerificationServiceImpl.java` - implement logic phân biệt ảnh
- `WorkScheduleServiceImpl.java` - sửa logic link ảnh với attendance

### 2. Logic nhân viên hỗ trợ 1 ngày (ONE_TIME contract)

**Vấn đề**: 
- Hệ thống chỉ tạo 1 attendance cho ngày đầu tiên của assignment
- Với nhân viên hỗ trợ nhiều ngày, cần tạo attendance cho tất cả ngày làm việc trong khoảng assignment

**Giải pháp**:
- Sửa logic trong `AssignmentServiceImpl.autoGenerateAttendancesForAssignment()`
- Tạo attendance cho tất cả ngày làm việc từ `startDate` đến `endDate` của assignment
- Tính `plannedDays` = số attendance thực tế được tạo (thay vì cố định = 1)

**Files đã sửa**:
- `AssignmentServiceImpl.java` - sửa logic tạo attendance cho ONE_TIME contract

## Chi tiết thay đổi

### VerificationService.java
```java
// Thêm methods mới
boolean isAttendancePhoto(VerificationImage image);
boolean isVerificationImage(VerificationImage image);
```

### VerificationServiceImpl.java
```java
@Override
public boolean isAttendancePhoto(VerificationImage image) {
    return image.getAttendance() != null;
}

@Override
public boolean isVerificationImage(VerificationImage image) {
    return image.getAssignmentVerification() != null;
}
```

### WorkScheduleServiceImpl.java
```java
// Trong capturePhoto method
// Link verification image to attendance for attendance photos
if (image != null && attendance != null) {
    image.setAttendance(attendance);
    imageRepository.save(image);
}
```

### AssignmentServiceImpl.java
```java
// Sửa logic ONE_TIME contract
if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
    // Tạo attendance cho tất cả ngày làm việc trong khoảng startDate -> endDate
    LocalDate assignmentEndDate = freshAssignment.getEndDate() != null ? 
            freshAssignment.getEndDate() : startDate;
    
    // Loop qua tất cả ngày làm việc
    LocalDate currentDate = startDate;
    while (!currentDate.isAfter(assignmentEndDate)) {
        if (workingDays.contains(currentDate.getDayOfWeek())) {
            // Tạo attendance cho ngày này
        }
        currentDate = currentDate.plusDays(1);
    }
}

// Tính plannedDays cho ONE_TIME
if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
    freshAssignment.setPlannedDays(attendances.size()); // Số attendance thực tế
}
```

## Testing

Để test các fix này:

1. **Test ảnh xác minh vs chấm công**:
   - Tạo nhân viên mới → chụp ảnh → kiểm tra `assignment_verification_id` và `attendance_id`
   - Tạo nhân viên cũ → chụp ảnh → kiểm tra chỉ có `attendance_id`

2. **Test ONE_TIME contract**:
   - Tạo assignment với contract ONE_TIME, startDate và endDate khác nhau
   - Kiểm tra số attendance được tạo = số ngày làm việc trong khoảng thời gian
   - Kiểm tra `plannedDays` = số attendance thực tế

## Database Impact

Không có thay đổi schema, chỉ sửa logic business.
Migration V010 đã cho phép `assignment_verification_id` NULL.

## Deployment Notes

- Backup database trước khi deploy
- Test thoroughly với các scenarios:
  - Nhân viên mới
  - Nhân viên cũ  
  - ONE_TIME contract với nhiều ngày
  - Contract yêu cầu verification