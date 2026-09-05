# Migration của module `core` — prefix `core`

Đặt tên: `V<yyyyMMdd><nnnn>__core_<mô_tả>.sql` (conventions.md §1.2).

⛔⛔ **`<nnnn>` là SỐ THỨ TỰ CHẠY TIẾP TOÀN KHO, KHÔNG PHẢI GIỜ-PHÚT.** Số hiệu mới phải
**lớn hơn mọi số đã có** trong cả kho. Đánh số bằng giờ-phút đã làm **hai lượt CD đỏ liên
tiếp** ngày 27/08/2026 (§10.66) — hai cách viết chỉ khác nhau ở đúng chỗ không ai nhìn là
thứ tự sắp xếp. Chạy `make migration-order` trước mỗi PR có migration.

**Cấm sửa file đã merge** — chỉ thêm file mới. Flyway `validateOnMigrate=true`
sẽ làm migration đỏ nếu checksum của file cũ thay đổi.

Version là timestamp **toàn cục**, nên khi module `app` gộp `locations` của 5
module lại, thứ tự thực thi vẫn đúng theo thời gian tạo.

Không dùng repeatable migration (`R__`) cho danh mục quyền / tham số cấu hình:
lịch sử thay đổi quyền phải truy vết được, không được ghi đè âm thầm.
