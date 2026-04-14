# Bug Report: Xác thực nhân viên mới & Chấm công hình ảnh

**Ngày:** 2026-04-14  
**Phạm vi:** `WorkScheduleServiceImpl`, `VerificationServiceImpl`, `AssignmentServiceImpl`

---

## Tổng quan nghiệp vụ

Hệ thống có 2 tính năng liên quan nhưng tách biệt:

| Tính năng | Điều kiện | Xử lý |
|---|---|---|
| **NEW_EMPLOYEE_VERIFICATION** | Nhân viên chưa có bất kỳ assignment nào (ngoại trừ assignment vừa tạo) | Yêu cầu chụp ảnh 5 ngày → auto-approve → chuyển sang CONTRACT_REQUIREMENT nếu hợp đồng bật |
| **CONTRACT_REQUIREMENT** | Nhân viên cũ, hợp đồng bật `requiresImageVerification = true` | Yêu cầu chụp ảnh hàng ngày, không auto-approve |

Điểm chung: cả 2 đều dùng `WorkSchedule` làm source of truth. Không chụp ảnh = không có attendance = không có lương. Admin có thể tạo chấm công thay cho ngày MISSED.

---

## Bug 1 — `isEmployeeNew` có 2 phiên bản logic khác nhau

**Mức độ:** 🔴 Critical  
**File:** `WorkScheduleServiceImpl.java` (line ~535)

### Mô tả

Có 2 nơi kiểm tra nhân viên mới với logic khác nhau:

- `VerificationServiceImpl.isEmployeeCompletelyNew()` → dùng `countAssignmentsByEmployeeExcluding(employeeId, excludeAssignmentId)` — **đúng**, loại trừ assignment hiện tại
- `WorkScheduleServiceImpl.isEmployeeNew()` → dùng `countAssignmentsByEmployee(employeeId)` — **sai**, đếm luôn cả assignment hiện tại

### Hậu quả

Khi `generateMonthlyWorkSchedules()` và `handleAssignmentUpdate()` gọi `isEmployeeNew()` nội bộ của `WorkScheduleServiceImpl`, nhân viên mới vừa được tạo assignment đầu tiên sẽ có `totalAssignments = 1` → bị coi là nhân viên cũ → tạo `CONTRACT_REQUIREMENT` thay vì `NEW_EMPLOYEE_VERIFICATION`, hoặc không tạo work schedule nào nếu hợp đồng không bật verification.

### Code lỗi

```java
// WorkScheduleServiceImpl.java
private boolean isEmployeeNew(Long employeeId) {
    Long totalAssignments = assignmentRepository.countAssignmentsByEmployee(employeeId);
    return totalAssignments == 0; // ← Luôn false vì assignment vừa tạo đã được đếm
}
```

### Fix

```java
private boolean isEmployeeNew(Long employeeId, Long excludeAssignmentId) {
    Long totalAssignments = excludeAssignmentId != null
            ? assignmentRepository.countAssignmentsByEmployeeExcluding(employeeId, excludeAssignmentId)
            : assignmentRepository.countAssignmentsByEmployee(employeeId);
    return totalAssignments == 0;
}
```

Tất cả các chỗ gọi `isEmployeeNew(employeeId)` trong `WorkScheduleServiceImpl` cần truyền thêm `assignment.getId()`.

---

## Bug 2 — SUPPORT assignment 1 ngày sinh ra work schedules nhiều ngày

**Mức độ:** 🔴 Critical  
**File:** `AssignmentServiceImpl.java` (phần tạo work schedules khi `requiresVerification = true`)

### Mô tả

SUPPORT assignment được tạo với danh sách ngày cụ thể (`request.getDates()`), ví dụ chỉ 1 ngày. Nhưng khi nhân viên đó là nhân viên mới, code đi vào nhánh `requiresVerification = true` và tính `endDate` như sau:

```java
LocalDate endDate;
if (savedAssignment.getEndDate() != null) {
    endDate = savedAssignment.getEndDate();
} else {
    YearMonth yearMonth = YearMonth.from(request.getStartDate());
    endDate = yearMonth.atEndOfMonth(); // ← Lấy cuối tháng
    ...
}

workScheduleService.createWorkSchedulesForAssignment(
    savedAssignment, wsReason, ...,
    request.getStartDate(),
    endDate  // ← Từ ngày bắt đầu đến cuối tháng, không phải chỉ ngày được chọn
);
```

SUPPORT assignment thường có `endDate = null` hoặc `endDate = startDate`. Kết quả: thay vì 1 ngày, hệ thống tạo work schedules cho toàn bộ ngày làm việc từ `startDate` đến cuối tháng.

### Hậu quả

- Nhân viên hỗ trợ 1 ngày bị yêu cầu chụp ảnh nhiều ngày
- Nếu không chụp, tất cả các ngày đó bị MISSED → không có lương cho những ngày không làm

### Fix

Với SUPPORT assignment, `endDate` phải là ngày cuối cùng trong `request.getDates()`, không phải cuối tháng:

```java
if (assignmentTypeParsed == AssignmentType.SUPPORT && request.getDates() != null && !request.getDates().isEmpty()) {
    // Chỉ tạo work schedules cho đúng các ngày được chỉ định
    LocalDate maxDate = request.getDates().stream().max(LocalDate::compareTo).orElse(request.getStartDate());
    endDate = maxDate;
    // Và fromDate phải là ngày nhỏ nhất trong danh sách
}
```

Hoặc tốt hơn: với SUPPORT, gọi `createWorkSchedulesForAssignment` theo từng ngày trong `request.getDates()` thay vì dùng date range.

---

## Bug 3 — Admin tạo chấm công thay không tăng `currentAttempts` cho nhân viên mới

**Mức độ:** 🔴 Critical  
**File:** `WorkScheduleServiceImpl.java` — method `createAttendanceForMissed()` (line ~557)

### Mô tả

Khi admin tạo chấm công thay cho ngày MISSED của nhân viên mới (`NEW_EMPLOYEE_VERIFICATION`), method `createAttendanceForMissed()` tạo attendance và chuyển status sang VERIFIED, nhưng **không gọi `checkAndAutoApprove()`**.

```java
public WorkScheduleResponse createAttendanceForMissed(Long id, String reason) {
    // ...
    Attendance attendance = createAttendanceFromSchedule(schedule);
    schedule.setAttendance(attendance);
    schedule.setStatus(WorkScheduleStatus.VERIFIED);
    // ← Thiếu: checkAndAutoApprove() nếu reason == NEW_EMPLOYEE_VERIFICATION
    workScheduleRepository.save(schedule);
    return mapToResponse(schedule);
}
```

### Hậu quả

- `AssignmentVerification.currentAttempts` không tăng
- Nhân viên mới không bao giờ đạt đủ 5 lần dù admin đã tạo đủ chấm công
- Verification mãi ở trạng thái PENDING/IN_PROGRESS, không auto-approve
- Nhân viên mới không chuyển sang chế độ bình thường

### Fix

```java
public WorkScheduleResponse createAttendanceForMissed(Long id, String reason) {
    // ... tạo attendance như cũ ...
    workScheduleRepository.save(schedule);

    // Tăng attempts và kiểm tra auto-approve nếu là nhân viên mới
    if (schedule.getReason() == WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION
            && schedule.getAssignmentVerification() != null) {
        checkAndAutoApprove(schedule.getAssignmentVerification().getId());
    }

    return mapToResponse(schedule);
}
```

---

## Bug 4 — NullPointerException khi xem ảnh chấm công của nhân viên cũ

**Mức độ:** 🔴 Critical  
**File:** `VerificationServiceImpl.java` — method `mapToImageResponse()` (cuối file)

### Mô tả

Sau migration V010, `assignment_verification_id` trong bảng `verification_images` có thể là NULL (cho ảnh CONTRACT_REQUIREMENT). Nhưng `mapToImageResponse()` gọi trực tiếp:

```java
.verificationId(image.getAssignmentVerification().getId()) // ← NPE nếu null
```

### Hậu quả

Crash khi admin xem ảnh chấm công của nhân viên cũ (CONTRACT_REQUIREMENT). Toàn bộ trang work-schedules có thể bị lỗi 500.

### Fix

```java
.verificationId(image.getAssignmentVerification() != null 
    ? image.getAssignmentVerification().getId() 
    : null)
```

---

## Bug 5 — Sau khi duyệt nhân viên mới, bỏ qua phần còn lại của tháng hiện tại

**Mức độ:** 🟡 High  
**File:** `VerificationServiceImpl.java` — method `handleVerificationApproval()` (khi `transitionToContractMode = true`)

### Mô tả

Khi nhân viên mới được duyệt (đủ 5 ảnh) và hợp đồng bật `requiresImageVerification`, code tạo work schedules CONTRACT_REQUIREMENT cho **tháng tiếp theo**:

```java
LocalDate nextMonth = LocalDate.now().plusMonths(1);
LocalDate endOfNextMonth = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
workScheduleService.createWorkSchedulesForAssignment(
    ...,
    nextMonth.withDayOfMonth(1), // ← Bắt đầu từ đầu tháng sau
    endOfNextMonth
);
```

Nếu nhân viên được duyệt vào ngày 14/4, từ ngày 15/4 đến hết tháng 4 sẽ không có work schedule nào → không có attendance → mất lương những ngày đó.

### Fix

Tạo work schedules CONTRACT_REQUIREMENT từ **ngày hôm sau** đến cuối tháng hiện tại, rồi tiếp tục tháng sau:

```java
LocalDate tomorrow = LocalDate.now().plusDays(1);
LocalDate endOfCurrentMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

// Phần còn lại của tháng hiện tại (nếu còn ngày)
if (!tomorrow.isAfter(endOfCurrentMonth)) {
    workScheduleService.createWorkSchedulesForAssignment(
        ..., WorkScheduleReason.CONTRACT_REQUIREMENT, null,
        tomorrow, endOfCurrentMonth
    );
}
```

---

## Tóm tắt

| # | Bug | File | Mức độ | Trạng thái |
|---|---|---|---|---|
| 1 | `isEmployeeNew` không loại trừ assignment hiện tại | `WorkScheduleServiceImpl` | 🔴 Critical | Đã sửa một phần |
| 2 | SUPPORT 1 ngày sinh work schedules cả tháng | `AssignmentServiceImpl` | 🔴 Critical | Chưa sửa |
| 3 | Admin tạo chấm công thay không tăng attempts | `WorkScheduleServiceImpl` | 🔴 Critical | Chưa sửa |
| 4 | NPE khi `assignmentVerification` là null | `VerificationServiceImpl` | 🔴 Critical | Chưa sửa |
| 5 | Bỏ qua phần còn lại tháng hiện tại sau khi duyệt | `VerificationServiceImpl` | 🟡 High | Chưa sửa |
