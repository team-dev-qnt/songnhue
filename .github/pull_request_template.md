## Nội dung thay đổi

<!-- Mô tả ngắn: làm gì, vì sao. Nếu sửa lỗi thì nêu cả nguyên nhân gốc. -->

**Hạng mục / task**: <!-- VD: WS-4 / T4.2  ·  hoặc mã chức năng CN-05.3 -->

---

## Definition of Done (conventions.md §5)

- [ ] Dùng đúng **envelope / exception / error-code** của Core — không tự chế bản riêng
- [ ] Endpoint mới có **`@RequirePermission`**, và field sort/filter nằm trong **whitelist**
- [ ] Entity thuộc phạm vi đơn vị kế thừa **`ScopedEntity`**; có **migration Flyway** đúng naming
- [ ] **Unit test** rule nghiệp vụ ở tầng domain; **integration test quyền** nếu thêm endpoint
- [ ] Message lỗi mới đã vào **catalog** (BE `error-messages_vi.properties` + FE `error-map.ts`)
- [ ] Không vi phạm **ArchUnit**; **lint + CVE scan** xanh

## Kiểm tra bắt buộc theo dự án

- [ ] Số đo và tiền dùng **`BigDecimal`** — không `float/double`
- [ ] Timestamp lưu **`timestamptz` UTC**, chỉ convert UTC+7 ở tầng hiển thị
- [ ] Tham số nghiệp vụ để trong bảng **`settings`**, không hard-code, không nằm ở `application.yml`
- [ ] Truy vấn dữ liệu thủy văn có lọc **`quality = HOP_LE`** *(bẫy sai số liệu dễ mắc nhất)*
- [ ] Không commit **secret / `.env` / credential**; credential bên thứ 3 không lọt vào log, response, bản export
- [ ] Đổi trạng thái entity đi qua **Workflow engine**, không `UPDATE` trực tiếp

## Nếu có migration đổi schema

- [ ] Đã ghi **cách rollback** trong phần mô tả PR *(production không có PITR — chỉ có bản dump pre-deploy)*
- [ ] Migration **chỉ thêm mới**, không sửa file đã merge

## Kiểm chứng

<!-- Đã chạy gì để tin là nó hoạt động: lệnh, kết quả, ảnh chụp màn hình. -->

```
make lint && make test-be
```
