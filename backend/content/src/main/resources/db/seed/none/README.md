# `db/seed/none` — thư mục cố ý KHÔNG có migration nào

Đây là giá trị mặc định của `SEED_LOCATION` (xem `application.yml`).

Flyway phải giải được **một** location có thật; trỏ vào một đường dẫn không tồn tại thì
tuỳ phiên bản mà nó cảnh báo hoặc dừng — và một cổng chặn chỉ đúng "tuỳ phiên bản" thì
không phải cổng chặn. Thư mục này tồn tại để mặc định luôn là một câu trả lời hợp lệ:
*không có seed nào cả*.

⛔ Đừng đặt migration vào đây. Muốn thêm bộ seed cho một môi trường thì tạo thư mục
`db/seed/<tên>` riêng và trỏ `SEED_LOCATION` vào đó ở đúng môi trường ấy.
