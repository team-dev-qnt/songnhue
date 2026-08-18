# Runbook vận hành — songnhue

> Mỗi tệp ở đây trả lời một câu hỏi dạng **"chuông báo vừa kêu, làm gì bây giờ"**.
> Viết cho người bị đánh thức lúc 2 giờ sáng: từng bước gõ được, không phải giải thích kiến trúc.
>
> Luật cảnh báo trỏ thẳng vào các tệp này qua nhãn `runbook:` trong
> [`deploy/observability/alerts.yml`](../../deploy/observability/alerts.yml). Đổi tên tệp thì phải sửa
> cả bên đó — cảnh báo trỏ vào runbook không tồn tại thì lúc cần nhất lại không có gì để đọc.

## Tra nhanh theo cảnh báo

| Cảnh báo | Nghĩa là | Runbook |
|---|---|---|
| `SaoLuuQuaHan` | Quá 26 giờ không có bản sao lưu thành công | [sao-luu-hong.md](sao-luu-hong.md) |
| `SaoLuuChuaRaKhoiMayChu` | Bản dump còn nằm cùng máy với CSDL | [sao-luu-hong.md](sao-luu-hong.md) |
| `NguonDuLieuImLang` | Nguồn ngoài ngừng gửi số — **mất là vĩnh viễn** | [poller-chet.md](poller-chet.md) |
| `WorkerCoVeDaChet` · `HangDoiViecTonDong` | Việc nền không chạy | [job-that-bai.md](job-that-bai.md) |
| `PhatHienDungLaiRefreshToken` | Nghi token bị đánh cắp | [su-kien-bao-mat.md](su-kien-bao-mat.md) |
| `TruyCapNgoaiPhamViDonVi` · `DangNhapSaiDonDap` | Có người đang dò | [su-kien-bao-mat.md](su-kien-bao-mat.md) |
| `KhoiPhucCSDL` | Đang có thao tác ghi đè CSDL | [khoi-phuc-du-lieu.md](khoi-phuc-du-lieu.md) |

## Theo việc phải làm

| Việc | Runbook |
|---|---|
| Khôi phục CSDL từ bản sao lưu | [khoi-phuc-du-lieu.md](khoi-phuc-du-lieu.md) |
| Diễn tập khôi phục (trước go-live, rồi theo quý) | [dien-tap-khoi-phuc.md](dien-tap-khoi-phuc.md) |
| Xoay khoá AES / khoá ký JWT | [xoay-khoa.md](xoay-khoa.md) |

## Bốn điều phải biết trước khi động vào bất cứ thứ gì

1. **Không có PITR, không có replica.** Bản `pg_dump` đêm là đường phục hồi *duy nhất*.
   RPO ≤ 24h · RTO ≤ 4h — chốt ở [`architecture-review.md`](../../.claude/architecture-review.md) §6.5
   cùng 4 rủi ro đã chấp nhận. Đừng đi tìm bản khôi phục điểm-thời-gian, nó không tồn tại.

2. **Dữ liệu thủy văn mất là mất vĩnh viễn.** Nguồn không có API lịch sử (chốt G3). Poller là nơi
   duy nhất bắt được dữ liệu. Vì vậy `NguonDuLieuImLang` có mức ưu tiên ngang cảnh báo sao lưu.

3. **Khoá nằm ngoài CSDL**, ở `/opt/songnhue/keys/`. Khôi phục CSDL **không** khôi phục khoá. Mất
   khoá AES là mất vĩnh viễn phần dữ liệu nhân sự đã mã hoá, dù bản sao lưu còn nguyên.

4. **`audit_logs` là append-only có chuỗi hash.** Đừng sửa tay. Sau mọi thao tác khôi phục, chạy
   `make db-verify-audit` — trả về rỗng nghĩa là chuỗi còn nguyên vẹn.
