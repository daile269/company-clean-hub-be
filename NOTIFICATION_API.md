# 🔔 Notification API — Tài Liệu Tích Hợp FE

## Tổng Quan

| Loại | `type` | Trigger |
|------|--------|---------|
| Cảnh báo trùng giờ | `WORK_TIME_CONFLICT` | Sau khi phân công / điều động |
| Nhân viên mới | `NEW_EMPLOYEE_CREATED` | Sau khi thêm nhân viên |

> **Chỉ role QLT1** mới có quyền `NOTIFICATION_VIEW` và nhận thông báo.

---

## Response Object — `NotificationResponse`

```json
{
  "id": 1,
  "type": "NEW_EMPLOYEE_CREATED",
  "typeDescription": "Nhân viên mới được thêm vào hệ thống",
  "title": "[NV MOI] Nhân viên mới được thêm vào hệ thống",
  "message": "Nhân viên Trần Văn A (NV000053) vừa được thêm bởi admin vào lúc 10:30 10/03/2026.",
  "refEmployeeId": 176,
  "refAssignmentId": null,
  "refContractId": null,
  "isRead": false,
  "createdAt": "2026-03-10T10:30:00"
}
```

| Field | Type | Mô tả |
|-------|------|-------|
| `id` | Long | ID notification |
| `type` | String | `WORK_TIME_CONFLICT` hoặc `NEW_EMPLOYEE_CREATED` |
| `typeDescription` | String | Mô tả tiếng Việt |
| `title` | String | Tiêu đề ngắn |
| `message` | String | Nội dung chi tiết |
| `refEmployeeId` | Long \| null | ID nhân viên liên quan |
| `refAssignmentId` | Long \| null | ID phân công (chỉ ở WORK_TIME_CONFLICT) |
| `refContractId` | Long \| null | ID hợp đồng (chỉ ở WORK_TIME_CONFLICT) |
| `isRead` | Boolean | `false` = chưa đọc |
| `createdAt` | String (ISO) | Thời điểm tạo |

---

## Base URL & Header

```
BASE: http://localhost:8080
Header: Authorization: Bearer {JWT_TOKEN}
```

---

## 1. Kết Nối Real-time (SSE)

> Gọi **1 lần duy nhất** khi QLT login. Server tự đẩy notification về khi có sự kiện mới.

```
GET /api/notifications/subscribe?token={JWT_TOKEN}
Accept: text/event-stream
```

```bash
curl -N "http://localhost:8080/api/notifications/subscribe?token=YOUR_TOKEN" \
  -H "Accept: text/event-stream"
```

**Server đẩy 2 loại event:**
```
# Khi kết nối thành công
event: connected
data: ok

# Khi có notification mới
event: notification
data: {"id":5,"type":"NEW_EMPLOYEE_CREATED","typeDescription":"Nhân viên mới được thêm vào hệ thống","title":"[NV MOI] Nhân viên mới được thêm vào hệ thống","message":"Nhân viên Trần Văn A (NV000053) vừa được thêm bởi admin vào lúc 10:30 10/03/2026.","refEmployeeId":176,"refAssignmentId":null,"refContractId":null,"isRead":false,"createdAt":"2026-03-10T10:30:00"}
```

---

## 2. Lấy Danh Sách Notification (Có Filter + Phân Trang)

```
GET /api/notifications?type={type}&isRead={isRead}&page={page}&pageSize={pageSize}
```

| Param | Bắt buộc | Mặc định | Giá trị |
|-------|---------|---------|---------|
| `type` | Không | `ALL` | `ALL` \| `WORK_TIME_CONFLICT` \| `NEW_EMPLOYEE_CREATED` |
| `isRead` | Không | *(không lọc)* | `true` \| `false` |
| `page` | Không | `0` | Số trang, bắt đầu từ 0 |
| `pageSize` | Không | `20` | Số item mỗi trang |

**CURL — Lấy tất cả (trang đầu):**
```bash
curl -X GET "http://localhost:8080/api/notifications" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**CURL — Chỉ chưa đọc:**
```bash
curl -X GET "http://localhost:8080/api/notifications?isRead=false" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**CURL — Chỉ loại trùng giờ, chưa đọc, trang 2:**
```bash
curl -X GET "http://localhost:8080/api/notifications?type=WORK_TIME_CONFLICT&isRead=false&page=1&pageSize=10" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**CURL — Chỉ nhân viên mới:**
```bash
curl -X GET "http://localhost:8080/api/notifications?type=NEW_EMPLOYEE_CREATED" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 5,
      "type": "NEW_EMPLOYEE_CREATED",
      "typeDescription": "Nhân viên mới được thêm vào hệ thống",
      "title": "[NV MOI] Nhân viên mới được thêm vào hệ thống",
      "message": "Nhân viên Trần Văn A (NV000053) vừa được thêm bởi NVVP000003 vào lúc 10:30 10/03/2026.",
      "refEmployeeId": 176,
      "refAssignmentId": null,
      "refContractId": null,
      "isRead": false,
      "createdAt": "2026-03-10T10:30:00"
    },
    {
      "id": 4,
      "type": "WORK_TIME_CONFLICT",
      "typeDescription": "Cảnh báo trùng khung giờ làm việc",
      "title": "[TRUNG GIO] Cảnh báo trùng khung giờ làm việc",
      "message": "Nhân viên Nguyễn Văn B (NV000012) vừa được phân công vào Hợp đồng ID=5 (08:00–10:00). Phát hiện trùng giờ với Hợp đồng ID=2 (07:00–09:00) vào ngày Thứ Hai.",
      "refEmployeeId": 12,
      "refAssignmentId": 34,
      "refContractId": 5,
      "isRead": true,
      "createdAt": "2026-03-10T09:15:00"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

## 3. Xem Chi Tiết 1 Notification

> Tự động đánh dấu **đã đọc** khi gọi endpoint này.

```
GET /api/notifications/{id}
```

```bash
curl -X GET "http://localhost:8080/api/notifications/5" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):**
```json
{
  "id": 5,
  "type": "NEW_EMPLOYEE_CREATED",
  "typeDescription": "Nhân viên mới được thêm vào hệ thống",
  "title": "[NV MOI] Nhân viên mới được thêm vào hệ thống",
  "message": "Nhân viên Trần Văn A (NV000053) vừa được thêm bởi NVVP000003 vào lúc 10:30 10/03/2026.",
  "refEmployeeId": 176,
  "refAssignmentId": null,
  "refContractId": null,
  "isRead": true,
  "createdAt": "2026-03-10T10:30:00"
}
```

---

## 4. Đếm Số Chưa Đọc (Badge)

```
GET /api/notifications/unread/count
```

```bash
curl -X GET "http://localhost:8080/api/notifications/unread/count" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):**
```json
{
  "count": 3
}
```

---

## 5. Lấy Danh Sách Chưa Đọc

```
GET /api/notifications/unread
```

```bash
curl -X GET "http://localhost:8080/api/notifications/unread" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):** Mảng `NotificationResponse[]` (không phân trang).

```json
[
  {
    "id": 5,
    "type": "NEW_EMPLOYEE_CREATED",
    "title": "[NV MOI] Nhân viên mới được thêm vào hệ thống",
    "message": "Nhân viên Trần Văn A (NV000053) vừa được thêm bởi NVVP000003 vào lúc 10:30 10/03/2026.",
    "refEmployeeId": 176,
    "refAssignmentId": null,
    "refContractId": null,
    "isRead": false,
    "createdAt": "2026-03-10T10:30:00"
  }
]
```

---

## 6. Đánh Dấu 1 Notification Đã Đọc

> Dùng khi FE muốn mark-as-read mà **không** navigate vào chi tiết.

```
PUT /api/notifications/{id}/read
```

```bash
curl -X PUT "http://localhost:8080/api/notifications/5/read" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):** `NotificationResponse` với `isRead: true`.

---

## 7. Đánh Dấu Tất Cả Đã Đọc

```
PUT /api/notifications/read-all
```

```bash
curl -X PUT "http://localhost:8080/api/notifications/read-all" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Response (200 OK):** Không có body.

---

## Error Responses

| HTTP | Code | Trường hợp |
|------|------|-----------|
| 401 | UNAUTHORIZED | Token hết hạn / thiếu |
| 403 | FORBIDDEN | Không có quyền `NOTIFICATION_VIEW` hoặc xem notification của người khác |
| 404 | NOTIFICATION_NOT_FOUND | ID không tồn tại |
| 400 | INVALID_REQUEST | `type` không hợp lệ (không phải ALL/WORK_TIME_CONFLICT/NEW_EMPLOYEE_CREATED) |

---

## Gợi Ý Tích Hợp FE

### Khởi tạo khi QLT login
```javascript
// 1. Lấy badge count
GET /api/notifications/unread/count → { count: N }

// 2. Kết nối SSE để nhận real-time
GET /api/notifications/subscribe?token=JWT
```

### Khi mở panel chuông
```javascript
// Lấy trang đầu, tất cả loại
GET /api/notifications?page=0&pageSize=20

// FE render tab filter: All / Chưa đọc / Trùng giờ / NV mới
// Khi đổi tab → gọi lại với params tương ứng
```

### Khi click 1 thông báo
```javascript
// Gọi detail → tự động mark as read
GET /api/notifications/{id}

// Sau đó navigate tới trang liên quan
if (type === 'WORK_TIME_CONFLICT')   → /assignments/{refAssignmentId}
if (type === 'NEW_EMPLOYEE_CREATED') → /employees/{refEmployeeId}
```

### Khi nhận SSE event
```javascript
eventSource.addEventListener('notification', (e) => {
  const notif = JSON.parse(e.data);
  addToList(notif);      // thêm vào đầu danh sách
  badgeCount++;          // tăng badge
  showToast(notif.title, notif.message);
});
```
