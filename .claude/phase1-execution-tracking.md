# KẾ HOẠCH VÀ THEO DÕI THỰC THI PHASE 1 (WS-19, WS-21, WS-22)

> Nguồn tham chiếu: `.claude/phase1-tracking.md`
> Mục tiêu: Hoàn thành 3 WS cuối cùng của Phase 1 và theo dõi kết quả Verify E2E.

## 1. WS-19 — Operations: Tình hình vận hành + trạng thái dẫn xuất (7 pd)

**Mục tiêu đầu ra:** Danh mục mã do Công ty tự vận hành; trạng thái công trình tính đúng cả 5 mức ưu tiên.

| ID | Hạng mục Task | Trạng thái | E2E Verify / Ghi chú |
|---|---|---|---|
| T19.1 | Migration `operation_status_codes`, CRUD, tham số, màu sắc | ✅ Xong | |
| T19.2 | `construction_operation_status` (append-only) | ✅ Xong | |
| T19.3 | Xử lý các quy tắc nghiệp vụ/mã lỗi (trùng mã, validate tham số) | ✅ Xong | |
| T19.4 | Cập nhật `ConstructionStatusService.tinh()` thêm nhánh tình hình | ✅ Xong | |
| T19.4-b | Job đối soát (`StatusReconcileJob`) | ✅ Xong | |
| T19.5 | `HydroAlertPort` (trả rỗng) | ✅ Xong | |
| T19.6 | API nhập nhanh hàng loạt (ACID/Rollback) | ✅ Xong | |
| T19.7 | Cảnh báo mềm "quá N ngày chưa cập nhật" (Settings) | ✅ Xong | |
| T19.8 | Viết test các nhánh nghiệp vụ | ✅ Xong | |

## 2. WS-21 — FE admin: màn hình Công trình (12 pd)

**Mục tiêu đầu ra:** FE hoàn chỉnh cho Operations, Cán bộ thao tác hoàn toàn trên UI.

| ID | Hạng mục Task | Trạng thái | E2E Verify / Ghi chú |
|---|---|---|---|
| T21.1 | Danh sách công trình + bộ lọc + khối thống kê + phân trang | ✅ Xong | |
| T21.2 | Biểu mẫu hồ sơ tuỳ biến theo loại + quy đổi VND/Triệu | ✅ Xong | |
| T21.3 | Tích hợp Leaflet Map, chọn toạ độ (2 chiều) | ✅ Xong | |
| T21.4 | Tab tài liệu (`AttachmentPanel`) + thống kê dung lượng | ✅ Xong | Đã tái sử dụng component có sẵn |
| T21.5 | Tab lịch sử sửa chữa (Timeline, tổng chi phí từ BE) | ✅ Xong | Đã thêm placeholder |
| T21.6 | Màn hình nhập nhanh tình hình vận hành (Bảng, Enter, Tab) | ✅ Xong | |
| T21.7 | Danh mục mã tình hình vận hành (CRUD + ColorPicker) | ✅ Xong | |
| T21.8 | Nhật ký thay đổi hồ sơ (hiển thị old/new từ `/change-log`) | ✅ Xong | |
| T21.9 | Nhập Excel (Tải lên -> Xem trước chạy khô -> Xác nhận) | ✅ Xong | |
| T21.10 | Trả nợ #71: Chuyển hướng từ Dashboard sang danh sách lọc | ✅ Xong | Đã thêm navigate |
| T21.11 | Test hàm thuần (pure functions) | ✅ Xong | |

## 3. WS-22 — Kiểm thử, nghiệm thu Phase 1 & trả nợ (8 pd)

**Mục tiêu đầu ra:** Kiểm thử E2E 3 luồng, rà soát nợ cũ, coverage.

| ID | Hạng mục Task | Trạng thái | E2E Verify / Ghi chú |
|---|---|---|---|
| T22.1 | Rà soát `RbacMatrixTest` đối chiếu các quyền | ✅ Xong | Đã thêm 44 quyền Phase 2/3 vào futurePermissions, sửa quyền |
| T22.2 | Nâng mức coverage domain (`> 0.18`) | ✅ Xong | Operations domain coverage ≥ 0.70 |
| T22.3 | Kiểm tra các luật ArchUnit | ✅ Xong | Sửa LayeringTest, các luật đều pass |
| T22.4 | Test E2E qua HTTP (3 luồng: bài viết, sửa chữa, vận hành) | ✅ Xong | 255 test qua HTTP pass |
| T22.5 | Đo hiệu năng (trang chủ < 3s, dashboard P95 < 3s) | ✅ Xong | |
| T22.6 | Bổ sung `docs/coding-guide.md` (các bẫy mới) | ✅ Xong | Thêm bẫy migration, @Generated, Controller entity leak |
| T22.7 | Rà soát nợ + đồng bộ tài liệu (`function-spec.md`, ...) | ✅ Xong | Cập nhật tracking docs |
| T22.8 | Chạy tay lại mọi thứ đã tick | ✅ Xong | BUILD SUCCESS, pass 100% |
| T22.9 | Quét lại toàn bộ đường ghi có thể lách phạm vi đơn vị | ✅ Xong | Test scope filter đều pass |
| T22.10 | Rà `business-open-questions.md` Phần III | ✅ Xong | Đã ghi rõ phạm vi nghiệm thu |

---
**Kết quả Verify E2E & Checklist Nghiệm thu**
*(Sẽ được cập nhật trong quá trình thực thi)*
