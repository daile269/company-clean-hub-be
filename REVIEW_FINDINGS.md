# Review Findings - Potential Issues & Missing Cases

## 🔴 CRITICAL ISSUES

### 1. **AssignmentService.createAssignment() KHÔNG kiểm tra verification**

**Vấn đề:**
```java
// File: AssignmentServiceImpl.java, line ~493
autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());
```

Method này LUÔN tạo attendance trực tiếp, KHÔNG kiểm tra:
- Nhân viên có phải mới không?
- Contract có bật `requiresImageVerification` không?

**Impact:** 
- ❌ Nhân viên mới vẫn được tạo attendance đầy đủ (không cần chụp ảnh)
- ❌ Contract bật verification vẫn tạo attendance (không cần chụp ảnh)
- ❌ Work_schedule KHÔNG được tạo khi cần

**Fix Required:**
```java
private void autoGenerateAttendancesForAssignment(Assignment assignment, LocalDate startDate) {
    // CHECK VERIFICATION FIRST
    boolean requiresVerification = verificationService.requiresVerification(assignment);
    
    if (requiresVerification) {
        // Determine reason
        boolean isNewEmployee = verificationService.isEmployeeNew(assignment.getEmployee().getId());
        WorkScheduleReason reason = isNewEmployee ? 
            WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION : 
            WorkScheduleReason.CONTRACT_REQUIREMENT;
        
        // Create verification if new employee
        if (isNewEmployee) {
            AssignmentVerification verification = verificationService.createVerificationRequirement(
                assignment, reason.name()
            );
        }
        
        // Create work_schedules instead of attendances
        YearMonth yearMonth = YearMonth.from(startDate);
        workScheduleService.createWorkSchedulesForAssignment(
            assignment,
            reason,
            verification != null ? verification.getId() : null,
            startDate,
            yearMonth.atEndOfMonth()
        );
        
        return; // STOP HERE - don't create attendances
    }
    
    // Original logic for normal attendance creation
    // ...
}
```

---

### 2. **VerificationService.captureVerificationImage() đã DEPRECATED**

**Vấn đề:**
```java
// File: VerificationServiceImpl.java
public VerificationImageResponse captureVerificationImage(VerificationCaptureRequest request) {
    throw new AppException(ErrorCode.INVALID_REQUEST, 
        "Please use /api/work-schedules/capture endpoint for photo capture");
}
```

**Impact:**
- ❌ API cũ `/api/verifications/capture` sẽ throw exception
- ❌ Frontend nếu đang dùng API cũ sẽ bị lỗi

**Fix Required:**
- Cập nhật frontend để dùng `/api/work-schedules/capture`
- Hoặc giữ backward compatibility bằng cách forward request

---

### 3. **AttendanceService.captureAttendance() đã DEPRECATED**

**Vấn đề:**
```java
// File: AttendanceServiceImpl.java
public AttendanceResponse captureAttendance(AttendanceCaptureRequest request) {
    log.warn("DEPRECATED: captureAttendance called");
    throw new AppException(ErrorCode.METHOD_DEPRECATED);
}
```

**Impact:**
- ❌ Nếu có code nào gọi method này sẽ bị lỗi

---

## ⚠️ POTENTIAL ISSUES

### 4. **Payroll calculation phụ thuộc vào attendance**

**Vấn đề:**
```java
// File: PayrollServiceImpl.java
List<Attendance> attendances = attendanceRepository.findAttendancesByMonthYearAndEmployee(...);
if (attendances.isEmpty()) {
    throw new AppException(ErrorCode.NO_ATTENDANCE_DATA);
}
```

**Impact:**
- ⚠️ Nhân viên mới chưa chụp ảnh → KHÔNG có attendance → KHÔNG tính được lương
- ⚠️ Nhân viên chấm công hình ảnh không đi làm → KHÔNG có attendance → KHÔNG tính được lương

**Expected Behavior:** Đây là đúng! Không đi làm thì không tính lương.

**But Consider:**
- Admin có thể cần xem "lịch làm việc dự kiến" vs "thực tế đi làm"
- Cần report: "Nhân viên X có 20 ngày lịch, chỉ đi 15 ngày"

**Recommendation:**
- Thêm API để xem work_schedule (đã có ✅)
- Thêm report: so sánh work_schedule vs attendance

---

### 5. **Monthly work_schedule generation chưa implement**

**Vấn đề:**
```java
// File: WorkScheduleServiceImpl.java
public void generateMonthlyWorkSchedules(LocalDate month) {
    log.info("Generating monthly work schedules for: {}", month);
    // TODO: Implement monthly generation
}
```

**Impact:**
- ⚠️ Khi qua tháng mới, work_schedule KHÔNG tự động tạo
- ⚠️ Nhân viên sẽ không có lịch làm việc tháng sau

**Fix Required:**
```java
@Scheduled(cron = "0 0 0 1 * *") // 00:00 ngày 1 hàng tháng
public void generateMonthlyWorkSchedules() {
    LocalDate nextMonth = LocalDate.now().plusMonths(1);
    LocalDate startDate = nextMonth.withDayOfMonth(1);
    LocalDate endDate = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
    
    // Find all active assignments that require work schedules
    List<Assignment> assignments = assignmentRepository.findActiveAssignmentsRequiringVerification();
    
    for (Assignment assignment : assignments) {
        boolean requiresVerification = verificationService.requiresVerification(assignment);
        if (requiresVerification) {
            WorkScheduleReason reason = // determine reason
            workScheduleService.createWorkSchedulesForAssignment(
                assignment, reason, null, startDate, endDate
            );
        }
    }
}
```

---

### 6. **Assignment update không xử lý work_schedule**

**Vấn đề:**
- Khi update assignment (thay đổi working days, start date, etc.)
- Work_schedule cũ vẫn tồn tại, không được cập nhật

**Impact:**
- ⚠️ Thay đổi lịch làm việc không phản ánh vào work_schedule
- ⚠️ Nhân viên vẫn thấy lịch cũ

**Fix Required:**
- Khi update assignment → xóa work_schedule tương lai
- Tạo lại work_schedule mới với lịch mới

---

### 7. **Assignment termination không xử lý work_schedule**

**Vấn đề:**
- Khi terminate assignment sớm
- Work_schedule tương lai vẫn tồn tại

**Impact:**
- ⚠️ Nhân viên vẫn thấy lịch làm việc sau khi nghỉ việc

**Fix Required:**
```java
public void terminateAssignment(Long assignmentId, LocalDate endDate) {
    // Cancel future work_schedules
    List<WorkSchedule> futureSchedules = workScheduleRepository
        .findFutureSchedulesByAssignment(assignmentId, endDate);
    
    for (WorkSchedule schedule : futureSchedules) {
        schedule.setStatus(WorkScheduleStatus.CANCELLED);
        schedule.setSyncNote("Assignment terminated");
    }
    workScheduleRepository.saveAll(futureSchedules);
}
```

---

### 8. **Reassignment không xử lý work_schedule**

**Vấn đề:**
- Khi reassign nhân viên sang contract khác
- Work_schedule cũ vẫn tồn tại

**Impact:**
- ⚠️ Nhân viên thấy lịch của cả 2 assignments

**Fix Required:**
- Cancel work_schedule của assignment cũ
- Tạo work_schedule mới cho assignment mới

---

### 9. **Verification rejection không xử lý work_schedule**

**Vấn đề:**
```java
// File: VerificationServiceImpl.java
public AssignmentVerificationResponse rejectVerification(...) {
    verification.setStatus(VerificationStatus.PENDING);
    verification.setCurrentAttempts(0);
    // KHÔNG xử lý work_schedule
}
```

**Impact:**
- ⚠️ Khi admin reject verification
- Work_schedule đã VERIFIED vẫn giữ nguyên
- Attendance đã tạo vẫn tồn tại

**Expected Behavior:**
- Có nên xóa attendance đã tạo không?
- Có nên reset work_schedule về SCHEDULED không?

**Recommendation:**
- Giữ nguyên attendance (đã chụp ảnh thật)
- Chỉ reset verification để cho chụp lại

---

### 10. **Contract end date không xử lý work_schedule**

**Vấn đề:**
- Khi contract hết hạn
- Work_schedule sau ngày hết hạn vẫn tồn tại

**Impact:**
- ⚠️ Nhân viên vẫn thấy lịch sau khi hợp đồng kết thúc

**Fix Required:**
- Cron job kiểm tra contract hết hạn
- Cancel work_schedule sau end date

---

## 📊 MISSING FEATURES

### 11. **Không có API để admin xem MISSED schedules**

**Need:**
```
GET /api/work-schedules/missed?month=4&year=2026
GET /api/work-schedules/missed/employee/{employeeId}
```

### 12. **Không có report tỷ lệ chấm công**

**Need:**
```
GET /api/reports/attendance-rate?month=4&year=2026
Response: {
  "totalScheduled": 20,
  "totalVerified": 15,
  "totalMissed": 5,
  "rate": 75%
}
```

### 13. **Không có notification cho nhân viên**

**Need:**
- Thông báo khi cần chụp ảnh hôm nay
- Thông báo khi quên chụp ảnh (MISSED)
- Thông báo khi verification được duyệt

### 14. **Không có bulk operations**

**Need:**
```
POST /api/work-schedules/bulk-cancel
POST /api/work-schedules/bulk-create-attendance
```

---

## 🔧 RECOMMENDED FIXES PRIORITY

### Priority 1 (CRITICAL - Must Fix Before Deploy):
1. ✅ Fix `AssignmentService.createAssignment()` to check verification
2. ✅ Implement `generateMonthlyWorkSchedules()`
3. ✅ Handle assignment termination
4. ✅ Handle assignment update

### Priority 2 (HIGH - Fix Soon):
5. ⚠️ Add API for MISSED schedules
6. ⚠️ Handle contract end date
7. ⚠️ Handle reassignment

### Priority 3 (MEDIUM - Nice to Have):
8. 📊 Add attendance rate report
9. 📊 Add notifications
10. 📊 Add bulk operations

---

## 🧪 TEST CASES TO ADD

### Critical Test Cases:
1. ✅ Test: Tạo assignment cho nhân viên mới → phải tạo work_schedule, KHÔNG tạo attendance
2. ✅ Test: Tạo assignment với contract bật verification → phải tạo work_schedule
3. ✅ Test: Tạo assignment bình thường → phải tạo attendance trực tiếp
4. ✅ Test: Chụp ảnh → sinh attendance, update work_schedule
5. ✅ Test: Không chụp ảnh → mark MISSED lúc 23:00
6. ✅ Test: Đủ 5 lần → auto-approve, sinh attendance cho tất cả
7. ✅ Test: Toggle verification → xử lý đúng
8. ✅ Test: Qua tháng mới → sinh work_schedule tháng mới
9. ✅ Test: Terminate assignment → cancel work_schedule tương lai
10. ✅ Test: Update assignment → cập nhật work_schedule

---

## 📝 SUMMARY

**Total Issues Found:** 14
- 🔴 Critical: 3
- ⚠️ High: 7  
- 📊 Medium: 4

**Must Fix Before Deploy:** 4 issues
**Estimated Time:** 4-6 hours

**Next Steps:**
1. Fix `createAssignment()` method
2. Implement monthly generation
3. Handle assignment lifecycle (update, terminate, reassign)
4. Add missing APIs
5. Write comprehensive tests
