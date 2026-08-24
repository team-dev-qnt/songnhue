# Migration của module `core` — prefix `core`

Đặt tên: `V<yyyyMMddHHmm>__core_<mô_tả>.sql` (conventions.md §1.2).

**Cấm sửa file đã merge** — chỉ thêm file mới. Flyway `validateOnMigrate=true`
sẽ làm migration đỏ nếu checksum của file cũ thay đổi.

Version là timestamp **toàn cục**, nên khi module `app` gộp `locations` của 5
module lại, thứ tự thực thi vẫn đúng theo thời gian tạo.

Không dùng repeatable migration (`R__`) cho danh mục quyền / tham số cấu hình:
lịch sử thay đổi quyền phải truy vết được, không được ghi đè âm thầm.
