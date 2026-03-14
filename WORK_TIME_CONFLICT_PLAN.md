# 📋 PLAN: Khung Giờ, Hệ Thống Thông Báo & Phân Quyền

## 📌 Mục Tiêu

1. Thêm **khung giờ làm việc** (`workStartTime` / `workEndTime`) vào Hợp đồng
2. Xây dựng **hệ thống Notification** với đúng **2 loại thông báo**:
   - `WORK_TIME_CONFLICT` — Phân công nhân viên bị trùng khung giờ
   - `NEW_EMPLOYEE_CREATED` — Có nhân viên mới được thêm vào hệ thống
3. Cả 2 loại đều **gửi tự động cho Quản lý tổng (QLC)**
4. Thêm **quyền `NOTIFICATION_VIEW` và `NOTIFICATION_MANAGE`** vào hệ thống phân quyền

---

## 🔔 Hai Loại Thông Báo

| Loại | Trigger | Nội dung | Gửi cho |
|------|---------|----------|---------|
| `WORK_TIME_CONFLICT` | `createAssignment()` hoặc `temporaryReassignment()` phát hiện trùng giờ | "NV X có lịch trùng giờ giữa HĐ A và HĐ B vào Thứ N" | Tất cả QLT |
| `NEW_EMPLOYEE_CREATED` | `createEmployee()` thành công | "Nhân viên mới [Tên] ([Mã]) vừa được thêm bởi [người tạo]" | Tất cả QLT |

> **Phân công vẫn thực hiện thành công** — notification chỉ là cảnh báo sau khi đã lưu, không chặn flow.

---

## 🗂️ Tổng Quan File Cần Thay Đổi

| STT | File | Loại |
|-----|------|------|
| 1 | `entity/Contract.java` | Sửa — thêm `workStartTime`, `workEndTime` |
| 2 | `dto/request/ContractRequest.java` | Sửa |
| 3 | `dto/response/ContractResponse.java` | Sửa |
| 4 | `service/impl/ContractServiceImpl.java` | Sửa — map 2 field mới |
| 5 | `entity/NotificationType.java` | ✨ Tạo mới — enum |
| 6 | `entity/Notification.java` | ✨ Tạo mới — entity |
| 7 | `repository/NotificationRepository.java` | ✨ Tạo mới |
| 8 | `dto/response/NotificationResponse.java` | ✨ Tạo mới |
| 9 | `service/NotificationService.java` | ✨ Tạo mới — interface |
| 10 | `service/impl/NotificationServiceImpl.java` | ✨ Tạo mới |
| 11 | `controller/NotificationController.java` | ✨ Tạo mới |
| 12 | `entity/Permission.java` | Sửa — thêm 2 quyền Notification |
| 13 | `config/SecurityConfig.java` | Sửa — bảo vệ endpoint notification |
| 14 | `repository/AssignmentRepository.java` | Sửa — thêm query detect giờ conflict |
| 15 | `repository/UserRepository.java` | Sửa — thêm query tìm user theo role code |
| 16 | `service/impl/AssignmentServiceImpl.java` | Sửa — thêm `checkAndNotifyTimeConflict()` |
| 17 | `service/impl/EmployeeServiceImpl.java` | Sửa — thêm gửi notification sau `createEmployee()` |

---

## 📝 Chi Tiết Từng Bước

---

### BƯỚC 1–4: Khung Giờ Trong Hợp Đồng

#### `Contract.java`
```java
@Column(name = "work_start_time")
private java.time.LocalTime workStartTime;

@Column(name = "work_end_time")
private java.time.LocalTime workEndTime;
```

#### `ContractRequest.java` & `ContractResponse.java`
```java
private java.time.LocalTime workStartTime;
private java.time.LocalTime workEndTime;
```

#### `ContractServiceImpl.java`
- Map 2 field trong `createContract`, `updateContract`, `mapToResponse`
- Validate: nếu có cả 2 field thì `workStartTime < workEndTime`, ngược lại throw `INVALID_WORK_TIME_RANGE`

---

### BƯỚC 5: `entity/NotificationType.java` ✨ TẠO MỚI

```java
package com.company.company_clean_hub_be.entity;

public enum NotificationType {
    WORK_TIME_CONFLICT("Cảnh báo trùng khung giờ làm việc"),
    NEW_EMPLOYEE_CREATED("Nhân viên mới được thêm vào hệ thống");

    private final String description;

    NotificationType(String description) { this.description = description; }
    public String getDescription() { return description; }
}
```

---

### BƯỚC 6: `entity/Notification.java` ✨ TẠO MỚI

```java
@Entity
@Table(name = "notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người nhận thông báo (Quản lý tổng)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    @JsonIgnore
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Refs để frontend điều hướng (nullable)
    @Column(name = "ref_employee_id")
    private Long refEmployeeId;         // Dùng cho cả 2 loại

    @Column(name = "ref_assignment_id")
    private Long refAssignmentId;       // Chỉ dùng cho WORK_TIME_CONFLICT

    @Column(name = "ref_contract_id")
    private Long refContractId;         // Chỉ dùng cho WORK_TIME_CONFLICT

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

---

### BƯỚC 7: `repository/NotificationRepository.java` ✨ TẠO MỚI

```java
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // Xóa notification cũ (optional - cleanup)
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
```

---

### BƯỚC 8: `dto/response/NotificationResponse.java` ✨ TẠO MỚI

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationResponse {
    private Long id;
    private String type;             // "WORK_TIME_CONFLICT" | "NEW_EMPLOYEE_CREATED"
    private String typeDescription;  // Mô tả tiếng Việt
    private String title;
    private String message;
    private Long refEmployeeId;
    private Long refAssignmentId;
    private Long refContractId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
```

---

### BƯỚC 9: `service/NotificationService.java` ✨ TẠO MỚI

```java
public interface NotificationService {

    // Tạo notification cho 1 người nhận (internal dùng)
    void createNotification(User recipient, NotificationType type,
                            String title, String message,
                            Long refEmployeeId, Long refAssignmentId, Long refContractId);

    // API cho frontend
    List<NotificationResponse> getMyNotifications();
    List<NotificationResponse> getMyUnreadNotifications();
    long countMyUnread();
    NotificationResponse markAsRead(Long id);
    void markAllAsRead();
}
```

---

### BƯỚC 10: `service/impl/NotificationServiceImpl.java` ✨ TẠO MỚI

- Inject: `NotificationRepository`, `UserRepository`
- `createNotification()`: build entity, `createdAt = LocalDateTime.now()`, save
- Các method "my" đều lấy `currentUser` từ `SecurityContextHolder`
- `markAsRead(id)`: kiểm tra `recipient.id == currentUser.id` trước khi cho phép đánh dấu (tránh user đọc notification của người khác)
- `mapToResponse()`: map entity sang DTO, kèm `typeDescription` từ enum

---

### BƯỚC 11: `controller/NotificationController.java` ✨ TẠO MỚI

```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    // GET /api/notifications
    // Quyền: NOTIFICATION_VIEW
    // → Lấy tất cả notification của user đang login

    // GET /api/notifications/unread
    // Quyền: NOTIFICATION_VIEW
    // → Chỉ lấy chưa đọc

    // GET /api/notifications/unread/count
    // Quyền: NOTIFICATION_VIEW
    // → Trả số notification chưa đọc (dùng cho badge)

    // PUT /api/notifications/{id}/read
    // Quyền: NOTIFICATION_VIEW
    // → Đánh dấu 1 notification đã đọc

    // PUT /api/notifications/read-all
    // Quyền: NOTIFICATION_VIEW
    // → Đánh dấu tất cả đã đọc
}
```

---

### BƯỚC 12: `entity/Permission.java` — Thêm 2 Quyền Mới

Thêm vào cuối enum (trước `AUDIT_VIEW`):

```java
// Notification permissions
NOTIFICATION_VIEW("Xem thông báo của bản thân"),
NOTIFICATION_MANAGE("Quản lý thông báo (xem tất cả, xóa)"),
```

> **Phân quyền theo role:**
> - **QLC (Quản lý tổng)**: `NOTIFICATION_VIEW` + `NOTIFICATION_MANAGE`
> - **QLV (Quản lý vùng)**: không có quyền notification (không nhận thông báo)
> - **NV (Nhân viên)**: không có quyền notification

---

### BƯỚC 13: `config/SecurityConfig.java`

Thêm rule bảo vệ endpoint notification:
```java
.requestMatchers("/api/notifications/**").hasAuthority("NOTIFICATION_VIEW")
```

---

### BƯỚC 14: `repository/AssignmentRepository.java`

Thêm query detect conflict khung giờ:

```java
@Query("""
    SELECT a FROM Assignment a
    JOIN a.contract c
    WHERE a.employee.id = :employeeId
    AND (:excludeId IS NULL OR a.id <> :excludeId)
    AND a.status IN (
        com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS,
        com.company.company_clean_hub_be.entity.AssignmentStatus.SCHEDULED
    )
    AND a.startDate <= :checkDate
    AND (a.endDate IS NULL OR a.endDate >= :checkDate)
    AND c.workStartTime IS NOT NULL
    AND c.workEndTime IS NOT NULL
    AND c.workStartTime < :endTime
    AND c.workEndTime > :startTime
""")
List<Assignment> findAssignmentsWithTimeConflict(
    @Param("employeeId") Long employeeId,
    @Param("checkDate") LocalDate checkDate,
    @Param("startTime") LocalTime startTime,
    @Param("endTime") LocalTime endTime,
    @Param("excludeId") Long excludeId
);
```

---

### BƯỚC 15: `repository/UserRepository.java`

Thêm query tìm user theo role code:
```java
@Query("SELECT u FROM User u WHERE u.role.code = :roleCode AND u.status = 'ACTIVE'") // Gọi với roleCode = "QLT"
List<User> findActiveUsersByRoleCode(@Param("roleCode") String roleCode);
```

---

### BƯỚC 16: `service/impl/AssignmentServiceImpl.java`

#### Inject thêm
```java
private final NotificationService notificationService;
```

#### Thêm method `checkAndNotifyTimeConflict()`

```
Input: savedAssignment, newContract

1. newContract.workStartTime == null || workEndTime == null → return (skip)
2. Tính danh sách ngày mẫu = 1 tuần đầu kể từ savedAssignment.startDate
   chỉ lấy ngày thuộc newContract.workingDaysPerWeek
3. Với mỗi ngày:
   a. Query findAssignmentsWithTimeConflict(employeeId, date, startTime, endTime, savedAssignment.id)
   b. Với mỗi kết quả assignment → lấy contract cũ
   c. Kiểm tra ngày có trong workingDays của contract cũ không
   d. Nếu có → ghi nhận conflict info, break ngay
4. Nếu phát hiện conflict:
   a. Tìm tất cả user QLC: userRepository.findActiveUsersByRoleCode("QLC")
   b. Với mỗi QLC → notificationService.createNotification(...)
      - type: WORK_TIME_CONFLICT
      - title: "⚠️ Cảnh báo trùng khung giờ làm việc"
      - message: "Nhân viên [tên] ([mã]) vừa được phân công vào HĐ [B] ([giờB]).
                  Phát hiện trùng giờ với HĐ [A] ([giờA]) vào [Thứ X].
                  Người phân công: [username] | [thời điểm]"
      - refs: savedAssignment.id, employee.id, newContract.id
   c. Log warning
5. Wrap toàn bộ trong try-catch → nếu lỗi chỉ log, không ảnh hưởng flow chính
```

#### Gọi sau `createAssignment()` thành công
```java
// Sau khi savedAssignment đã lưu thành công, ngoài transaction chính
if (contract != null) {
    try { checkAndNotifyTimeConflict(savedAssignment, contract); }
    catch (Exception e) { log.warn("checkAndNotifyTimeConflict failed: {}", e.getMessage()); }
}
```

#### Gọi sau `temporaryReassignment()` thành công
```java
// Sau khi điều động thành công
if (replacementAssignment != null && replacementAssignment.getContract() != null) {
    try { checkAndNotifyTimeConflict(replacementAssignment, replacementAssignment.getContract()); }
    catch (Exception e) { log.warn("checkAndNotifyTimeConflict failed: {}", e.getMessage()); }
}
```

---

### BƯỚC 17: `service/impl/EmployeeServiceImpl.java`

#### Inject thêm
```java
private final NotificationService notificationService;
```

#### Thêm vào cuối `createEmployee()` (sau khi đã save thành công)

```java
// Gửi notification cho Quản lý tổng
try {
    List<User> managers = userRepository.findActiveUsersByRoleCode("QLT");
    String creatorName = username; // người tạo
    for (User manager : managers) {
        notificationService.createNotification(
            manager,
            NotificationType.NEW_EMPLOYEE_CREATED,
            "👤 Nhân viên mới được thêm vào hệ thống",
            String.format("Nhân viên %s (%s) vừa được thêm bởi %s vào lúc %s.",
                savedEmployee.getName(),
                savedEmployee.getEmployeeCode(),
                creatorName,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
            ),
            savedEmployee.getId(), null, null
        );
    }
} catch (Exception e) {
    log.warn("Failed to send NEW_EMPLOYEE_CREATED notification: {}", e.getMessage());
}
```

---

## 🔄 Flow Tổng Quan

```
createEmployee()                    createAssignment() / temporaryReassignment()
│                                   │
├── Validate & lưu nhân viên ✅     ├── Validate & lưu phân công ✅
│                                   │
└── Gửi NEW_EMPLOYEE_CREATED        └── checkAndNotifyTimeConflict()
      ├── Tìm tất cả QLC                  ├── Không có khung giờ? → SKIP
      └── Tạo Notification mỗi QLC        ├── Tìm overlap theo ngày/giờ
                                          ├── Có conflict? → Tìm tất cả QLC
                                          └── Tạo WORK_TIME_CONFLICT mỗi QLC

                                   Frontend QLC:
                                   GET /api/notifications/unread/count → badge số
                                   GET /api/notifications → danh sách
                                   PUT /api/notifications/{id}/read → đánh dấu đã đọc
```

---

## 📱 Sample Notification Messages

### WORK_TIME_CONFLICT
```
Title  : ⚠️ Cảnh báo trùng khung giờ làm việc
Message: Nhân viên Nguyễn Văn A (NV000001) vừa được phân công vào
         Hợp đồng HD-002 (08:00–10:00, làm Thứ 2/4/6).
         Phát hiện trùng giờ với Hợp đồng HD-001 (07:00–09:00, Thứ 2/4/6)
         vào ngày Thứ Hai.
         Người phân công: tranthib | 09:30 09/03/2026
```

### NEW_EMPLOYEE_CREATED
```
Title  : 👤 Nhân viên mới được thêm vào hệ thống
Message: Nhân viên Trần Thị B (NV000002) vừa được thêm bởi admin
         vào lúc 09:45 09/03/2026.
```

---

## 🔑 Phân Quyền Notification

| Role | `NOTIFICATION_VIEW` | `NOTIFICATION_MANAGE` | Nhận thông báo? |
|------|--------------------|-----------------------|----------------|
| QLT (Quản lý tổng) | ✅ | ✅ | ✅ |
| QLV (Quản lý vùng) | ❌ | ❌ | ❌ |
| NV (Nhân viên) | ❌ | ❌ | ❌ |

> **Lưu ý:** `NOTIFICATION_MANAGE` dự phòng cho tương lai (xem notification của người khác, xóa hàng loạt).  
> Hiện tại chỉ cần `NOTIFICATION_VIEW` là đủ cho QLC.

---

## ✅ Checklist Implement

### Phần Khung Giờ
- [ ] B1 — Thêm `workStartTime`, `workEndTime` vào `Contract.java`
- [ ] B2 — Thêm vào `ContractRequest.java`
- [ ] B3 — Thêm vào `ContractResponse.java`
- [ ] B4 — Map + validate range trong `ContractServiceImpl.java`

### Phần Notification (Hệ Thống Mới)
- [ ] B5 — Tạo `entity/NotificationType.java` (enum 2 loại)
- [ ] B6 — Tạo `entity/Notification.java`
- [ ] B7 — Tạo `repository/NotificationRepository.java`
- [ ] B8 — Tạo `dto/response/NotificationResponse.java`
- [ ] B9 — Tạo `service/NotificationService.java`
- [ ] B10 — Tạo `service/impl/NotificationServiceImpl.java`
- [ ] B11 — Tạo `controller/NotificationController.java` (5 endpoints)

### Phần Phân Quyền
- [ ] B12 — Thêm `NOTIFICATION_VIEW`, `NOTIFICATION_MANAGE` vào `Permission.java`
- [ ] B13 — Bảo vệ endpoint trong `SecurityConfig.java`

### Phần Gắn Kết Nghiệp Vụ
- [ ] B14 — Thêm `findAssignmentsWithTimeConflict` vào `AssignmentRepository.java`
- [ ] B15 — Thêm `findActiveUsersByRoleCode` vào `UserRepository.java`
- [ ] B16a — Inject `NotificationService` vào `AssignmentServiceImpl`
- [ ] B16b — Viết `checkAndNotifyTimeConflict()` trong `AssignmentServiceImpl`
- [ ] B16c — Gọi sau `createAssignment()` thành công
- [ ] B16d — Gọi sau `temporaryReassignment()` thành công
- [ ] B17a — Inject `NotificationService` vào `EmployeeServiceImpl`
- [ ] B17b — Gọi sau `createEmployee()` thành công

---

## ⚠️ Lưu Ý Quan Trọng

> 1. **Role code của QLT** (Quản lý tổng) đã xác nhận: `'QLT'`
> 2. **Không dùng WebSocket** — frontend polling hoặc refetch khi load trang
> 3. **Notification không ảnh hưởng flow chính** — toàn bộ logic gửi notification wrap trong `try-catch`
> 4. **`NOTIFICATION_MANAGE`** chưa implement endpoint riêng, chỉ thêm vào Permission để sẵn sàng cho tương lai
