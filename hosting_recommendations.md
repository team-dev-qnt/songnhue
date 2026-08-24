# Đề xuất hạ tầng — Hệ thống Quản lý Thuỷ lợi Sông Nhuệ

> Chốt **23/8/2026**. Cách làm từng bước: `docs/deploy-guideline.md`. Luồng CI/CD: `docs/cicd.md`.

## 1. Chốt: 2 VPS đặt tại Việt Nam, chạy docker-compose

| | Máy | Cấu hình | Chạy gì |
|---|---|---|---|
| **VPS-1** | Production | 4 vCPU · 8 GB · 160 GB SSD · Ubuntu 24.04 | nginx · app · postgres · minio · admin-app · public-web |
| **VPS-2** | Staging | 2 vCPU · 4 GB · 80 GB | cùng stack (nhỏ hơn) **+ kho sao lưu + Prometheus/Grafana** |
| — | Kho ngoài | B2 / R2, **nhà cung cấp khác** | bản sao lưu đã mã hoá |

Nhà cung cấp: lấy báo giá Viettel IDC · VNPT · FPT · BizFly · VNG. Chênh nhau nhiều và hay có giá
trả trước theo năm — hỏi thẳng, đừng lấy giá niêm yết.

**Ba máy rút còn hai.** Kế hoạch cũ (`.claude/phase0-tracking.md` T11.2) có VM-3 riêng cho sao lưu
và giám sát. VM-3 gộp vào VPS-2 vì tính chất cần giữ là *"nằm ngoài máy production"*, không phải
*"là một máy thứ ba"*. Gộp lại vẫn giữ đủ: chuỗi giám sát sống sót khi VPS-1 chết, và kho sao lưu
không nằm cùng đĩa với CSDL.

## 2. Vì sao không PaaS — năm bảo đảm phải tháo

Đã có một bản kế hoạch chuyển sang Vercel + Railway. Bản đó bị bác, và lý do không phải "PaaS tệ"
mà là: với repo **này**, giữ PaaS đòi tháo năm thứ đã dựng có chủ đích.

| # | PaaS bắt phải làm gì | Đang bảo vệ điều gì |
|---|---|---|
| 1 | Thêm cấu hình CORS vào backend | Hiện backend **không có một dòng CORS nào**, vì giao diện và API luôn cùng origin |
| 2 | Hạ cookie refresh `SameSite=Strict` → `None` | Chống CSRF ở tầng trình duyệt (`CsrfTokens.java:87`) |
| 3 | Bỏ service `migrator` riêng | Migration hỏng thì app **không lên nửa vời** |
| 4 | Bỏ 4 vai trò CSDL tách quyền | `audit_logs` chỉ-ghi-thêm **ở tầng CSDL**, không chỉ ở mã |
| 5 | Chuyển header bảo mật vào Spring | `NginxSecurityHeadersTest` đang canh chúng ở đúng nơi chúng sống |

Cộng thêm: **hai phần ba công việc đã làm xong cho đường VPS** — Dockerfile, compose, init CSDL,
4 script sao lưu, cấu hình nginx, hai workflow triển khai. Đường PaaS vứt hết chỗ đó rồi dựng lại
một bản yếu hơn.

Và một lý do không thuộc kỹ thuật, nhưng là lý do lớn nhất: hệ này quản lý công trình thuỷ lợi của
một công ty nhà nước. Câu *"dữ liệu nhân sự đang nằm trên máy chủ nước ngoài do cá nhân nhà thầu
đứng tên"* rất khó trả lời khi có người hỏi.

## 3. Đặt máy trong nước gỡ được gì, và không gỡ được gì

| Nghĩa vụ | Đặt trong nước | Đặt nước ngoài |
|---|---|---|
| NĐ 13/2023 **Điều 25** — hồ sơ đánh giá tác động xử lý DLCN | **Vẫn phải làm** | Vẫn phải làm |
| NĐ 13/2023 **Điều 26** — hồ sơ chuyển DLCN ra nước ngoài | Không phát sinh | Phải làm |

Còn một hồ sơ thay vì hai, và hồ sơ còn lại thuộc loại tự lập tự lưu.

**Ba việc rẻ, giá gần bằng 0, làm ngay:**

1. **Ký phụ lục xử lý dữ liệu cá nhân với Công ty** — Công ty là Bên Kiểm soát, người vận hành là
   Bên Xử lý. Hiện chưa có văn bản nào định danh tư cách, mà dữ liệu cá nhân **đã tồn tại từ Phase
   0** (`users.full_name`, email, nhật ký audit), không phải đợi tới `employee_sensitive` ở Phase 2.
2. **Tên miền `.vn` đăng ký chủ thể là Công ty**, không phải cá nhân.
3. **Tài khoản hosting mở bằng email trung tính**, nhà cung cấp xuất hoá đơn VAT cho Công ty.

> ⚠ Đây là phần cần **pháp chế của Công ty xác nhận**, không phải kết luận kỹ thuật. Mẫu biểu và
> cách nộp hồ sơ Điều 25 tôi không khẳng định — nhưng phải có mục (1) trước, vì thiếu nó thì không
> rõ ai là bên phải nộp.

> ⚠ **Bàn giao** — chỗ chưa ai tính. Hệ này rồi sẽ chuyển cho Công ty hoặc đơn vị bảo trì kế tiếp.
> Tài khoản PaaS cá nhân gắn thẻ cá nhân thì không bàn giao được, phải dựng lại từ đầu. VPS + tên
> miền đứng tên Công ty thì bàn giao là đổi mật khẩu.

## 4. Cắt gánh nặng quản trị xuống gần 0

Lý do chính chọn PaaS là **không muốn trông máy chủ**. Lấy lại phần lớn điều đó bằng 5 việc làm
một lần:

| Việc | Cách | Sau đó |
|---|---|---|
| Vá bảo mật hệ điều hành | `unattended-upgrades` | tự động, vĩnh viễn |
| Chứng chỉ TLS | certbot webroot + cron hằng tuần, đã kiểm bằng `--dry-run` | hết hạn lúc 3h sáng không còn là một loại sự cố |
| Container chết | `restart: unless-stopped` | tự dậy |
| Biết khi sập | UptimeRobot bản miễn phí, ping `/healthz` từ ngoài | không cần máy thứ ba cho giám sát |
| Biết khi sao lưu chết | cảnh báo `backup_last_success > 26h` | đã có sẵn `deploy/observability/alerts.yml` |

Cloudflare: **cổng công khai bật proxy** (cache + chống ngập cho phần dân truy cập); **`admin` và
`files` để DNS-only** — không cho phiên quản trị và tệp nhân sự đi vòng qua hạ tầng nước ngoài,
vốn là điều đang cố tránh.

## 5. Sao lưu — sửa hai điểm sai của bản PaaS

Bản trước đề xuất `pg_dump` **hàng tuần**, lưu **vào MinIO cùng nền tảng**. Cả hai đều sai theo
hướng nguy hiểm:

| Bản PaaS | Vấn đề | Chốt |
|---|---|---|
| hàng tuần | RPO thành 7 ngày; đã chốt **≤ 24h** (NFR-08) | **hàng đêm 02:00** — `backup.sh` đã viết sẵn |
| lưu vào MinIO | MinIO **cùng nền tảng** với CSDL → mất tài khoản là mất cả hai | kéo về VPS-2 **+** đẩy ra nhà cung cấp khác |

Thành ba bản, mỗi bản trả lời một câu hỏi khác nhau:

| Bản | Ở đâu | Trả lời |
|---|---|---|
| Đêm 02:00 | VPS-1 | "xoá nhầm bảng lúc chiều" |
| Kéo về 03:00 | VPS-2 | "VPS-1 chết / đĩa hỏng" |
| Đẩy ra 03:30, **đã mã hoá** | B2/R2 | "mất tài khoản nhà cung cấp" |

Cộng bản `predeploy-*` tự động trước mỗi lượt deploy production — điểm quay lui **duy nhất** khi
migration làm hỏng dữ liệu, vì hệ này không có PITR.

Ba điều bổ sung, mỗi điều bịt một lỗ:

1. **Mã hoá trước khi rời máy** (`age`). Bản dump chứa dữ liệu nhân sự; mã hoá xong thì thứ rời
   khỏi máy chỉ là byte ngẫu nhiên, và kho ở đâu không còn là câu hỏi về dữ liệu cá nhân.
2. **Sao lưu cả tệp đính kèm.** Kế hoạch cũ chỉ sao lưu CSDL. Hồ sơ công trình nằm trong MinIO và
   **chưa có bản sao nào** — khôi phục xong sẽ được một hệ thống đầy đủ bản ghi mà mọi đường tải
   về đều 404.
3. **Diễn tập khôi phục một lần trước go-live**, ghi con số RTO thật vào runbook. Dự án đã có 4
   ngày sao lưu chạy mà không sinh ra tệp nào, trong khi bài kiểm vẫn xanh. **Chưa khôi phục thật
   thì chưa có sao lưu, chỉ có tệp.**

## 6. Rủi ro chấp nhận — ghi rõ để về sau có căn cứ

| # | Rủi ro | Hệ quả xấu nhất | Vì sao chấp nhận |
|---|---|---|---|
| 1 | Mất tối đa 1 ngày dữ liệu | Nhập lại số liệu trong ngày | Giờ hành chính, khối lượng nhập nhỏ |
| 2 | Không HA — CSDL chết là dừng dịch vụ | Ngừng 30–60 phút | NFR-01 (99% ≈ 7,2h/tháng) thừa biên |
| 3 | Không PITR | Migration hỏng giữa ngày | Bù bằng bản chụp `predeploy-*` |
| 4 | Một người vận hành duy nhất | Người đó bận thì không ai xử lý | ⚠ **Chưa có phương án.** Tối thiểu: đưa `docs/runbook/` cho một người thứ hai và cất bản sao khoá riêng ở nơi thứ hai |

Rủi ro #4 là rủi ro thật nhất trong bảng này và là rủi ro duy nhất chưa được xử lý bằng kỹ thuật.

## 7. Đường nâng cấp — khi nào cần, không phải bây giờ

Bật PITR về sau là **thêm cấu hình, không sửa mã**: `archive_mode=on` + `archive_timeout` +
`pg_basebackup` hàng tuần. Cân nhắc khi dữ liệu thuỷ văn đã tích luỹ nhiều năm, hoặc Công ty nâng
cam kết uptime, hoặc lên ≥2 node.

> 📌 Lưu ý kỹ thuật cho lần đó: **không thể replay WAL lên bản `pg_dump`**. PITR bắt buộc phải có
> `pg_basebackup` (bản vật lý). Bật WAL archiving mà chỉ có dump logic là vô nghĩa.
