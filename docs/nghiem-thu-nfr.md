# Sổ số đo nghiệm thu — NFR + DoD Phase 4

> **`DOD4.10` sống ở đây.** Mục ấy đòi: *"Mọi con số nghiệm thu ghi kèm **ngày đo** và **nguồn đo**
> (CI hay máy chủ, ⛔ không phải máy dev)"*. Trước tệp này, câu ấy là **một lời dặn** — và
> `grep -c 'ngày đo\|nguồn đo' .claude/conventions.md` = **0**, tức chưa có cổng nào.
> Nay nó là **một bảng có bộ canh đọc**: `NghiemThuCoNguonDoTest`.

## Vì sao một con số không có nguồn đo là một con số sai đang chờ đến lượt

Dự án này đã trả giá ba lần cho đúng chuyện ấy:

- **§10.75** — dòng nợ `T11.83` khai *"cổng quét soi 110/121 jar"* bằng một phép đếm **tay**. Sai.
  Kế hoạch xử lý trỏ nhầm hướng suốt một ngày.
- **`T51.0`** — một dòng số đo trong `CLAUDE.md` (*"67 migration, đỉnh 1073"*) **hết hạn** rồi đứng
  yên; ngày hôm sau **ba trong bảy** agent đọc nó và cùng dựng sẵn một lượt CI đỏ. Họ ⛔ bịa — họ
  **chép một dòng sổ**.
- **`T63.10`** — mẫu số của một dòng nợ đo lại **lần thứ tư**, và ba lượt trước sai **ba kiểu khác
  nhau**.

⇒ Một con số **⛔ ngày** thì ⛔ ai biết nó còn đúng ⛔. Một con số **⛔ nguồn** thì ⛔ ai kiểm lại
được. Và một con số đo ở **máy dev** ⛔ nói gì về sản phẩm: hai job chỉ sống trên runner, `.env.local`
chỉ có ở máy, còn múi giờ máy dev **đúng bằng** múi giờ sản phẩm nên nó **giấu** cả một lớp lỗi
(§11.27).

## Cách điền

| Ô | Luật |
|---|---|
| **Số đo** | Giá trị thật. Chưa đo thì viết `—`, ⛔ đoán, ⛔ để trống |
| **Ngày đo** | `dd/mm/yyyy`. ⛔ có thì `—` |
| **Nguồn đo** | ĐÚNG MỘT trong: `CI <run-id>` · `VPS-1` · `VPS-2` · `nguồn ngoài` · `CHƯA ĐO` |

⛔⛔ **`máy dev` ⛔ phải một nguồn hợp lệ cho bảng này.** `make ci-local` là cổng để **⛔ đẩy mã
hỏng lên**, ⛔ phải phép đo nghiệm thu. Bộ canh từ chối chuỗi ấy.

---

## A. NFR-01 → NFR-09

| Mã | Ngưỡng phải đạt | Số đo | Ngày đo | Nguồn đo | Ghi chú |
|---|---|---|---|---|---|
| NFR-01 | Uptime ≥ 99%, cảnh báo khi downtime > 15′ | — | — | CHƯA ĐO | Cần Alertmanager bắn thật (`DOD4.12` · `T61.5`) |
| NFR-02a | Trang chủ < 3s | — | — | CHƯA ĐO | Công cụ đo nay CÓ (`T63.22`): `frontend/public-web/playwright.hieu-nang.config.ts` — LCP bằng Chromium thật, đo **cả lượt ISR nguội**. Còn thiếu đúng một lượt chạy **từ máy ở VN** |
| NFR-02b | ≥ 200 người dùng đồng thời | — | — | CHƯA ĐO | Kịch bản đã có: `tools/tai-thu/`, chiều **ĐỎ** đã kiểm chứng 18/09 (4/4 ca, `tu-kiem-tim-kiem-rong.sh`) — chờ chạy trên staging (`DOD4.4`) |
| NFR-03 | Sai lệch cron < 10%, ⛔ bỏ sót khung 10′ | — | — | CHƯA ĐO | Đòi **7 ngày lịch liên tục** = 1008 khung (`T37.1`); đồng hồ bấm được ngay khi `DOD4.2` xanh (production mang bản vá poller từ 18/09 — rào đề bạt đã gỡ) |
| NFR-04 | Báo cáo tháng < 60s | — | — | CHƯA ĐO | `DOD2.22`: bấm giờ job `HYDRO_REPORT_EXPORT` (trạng thái `SUCCEEDED`) ở khối lượng một tháng — bài NFR-04 hiện có ⛔ đo job ấy; production cần ≥ 1 tháng dữ liệu thật sau `DOD4.2` |
| NFR-05 | ⛔ mật khẩu/credential dạng thô; 2FA bắt buộc Admin | — | — | CHƯA ĐO | ⚠ **Sửa 19/09 (WS-68)**: hàng này từng ghi *"31 dòng ASVS L1 đối chiếu · 16/09 · CI"* — đó là một lượt **tự đánh giá tài liệu** (`T61.40`), ⛔ phải phép đo ngưỡng NFR-05 (`function-spec.md`: đăng nhập Admin ⛔ 2FA phải bị từ chối · ⛔ credential dạng thô). Phép đo: bài HTTP 2FA trên một run CI có id + ZAP baseline trên staging (`DOD4.14`, `T61.28`) |
| NFR-06 | Phân quyền dữ liệu — bài kiểm 100% pass | — | — | CHƯA ĐO | Chủ: **`T68.35`** — điền từ một run CI có id (job Backend xanh). ⚠ **Bộ canh bắt chính tôi ở lượt chạy đầu**: bản nháp ghi *1985 testcase BE, 0 đỏ* vào ô Số đo với ngày `—`. Con số ấy đo ở **máy dev** ⇒ theo luật của chính bảng này nó **⛔ phải một phép đo nghiệm thu** |
| NFR-07 | Audit giữ 5 năm, có old/new value | — | — | CHƯA ĐO | Chủ: **`T68.35`** — `AuditArchiveHandler` có **0** bài kiểm hành vi (đo 19/09); cần bài tích hợp trước khi điền |
| NFR-08 | RPO ≤ 24h · RTO ≤ 4h; backup < 26h, khác máy | — | — | CHƯA ĐO | Bằng chứng = **một hàng nhật ký "máy TRẮNG" + RTO bằng phút** trong `docs/runbook/dien-tap-khoi-phuc.md` (`T61.10`). ⚠ Các ô `______` của runbook là **mẫu in ra**, ⛔ phải chỗ điền — *"hết ô trống"* ⛔ chứng minh gì (sửa 19/09) |
| NFR-09 | Chrome/Firefox/Edge/Safari · 360–2560px | — | — | CHƯA ĐO | Playwright 3 engine đã viết (`T61.29`), **chạy tay/theo lịch, ⛔ chặn PR** (chốt 15/09). Vế quản trị cần tài khoản đo **và** `quanTri.spec.ts` đăng nhập MỘT lần mỗi dự án (xô LOGIN 30/15′/IP) — `DOD4.15` |

---

## B. DoD Phase 4 — DOD4.1 → DOD4.15

| Mã | Điều phải chứng minh | Số đo | Ngày đo | Nguồn đo | Ai làm được |
|---|---|---|---|---|---|
| DOD4.1 | `dev → staging → production` đi trọn, đo trên container thật | Đề bạt ĐÃ chạy: #133 → staging 17/09 · #168 → production 18/09 · #171 → staging 19/09; production nay tụt **4** commit (b35d4a2 · #167 · #170 · #169). ⛔ đo container qua SSH | 19/09/2026 | CI 35367590642 · 35436877167 | QuanTran — đo container + Flyway qua SSH (`T61.1` §B3), rồi chặng `staging → production` kế tiếp (`T68.2`) |
| DOD4.2 | Poller production ghi byte thật | Bản vá ĐÃ lên production 18/09 (`origin/production:DiaChiNguon.java:137` = `chuanHoaGoc`); `hydro_readings` trên VPS-1 **chưa ai đo** | 19/09/2026 | CI 35367590642 | QuanTran đo SSH (`T61.2` — SQL dùng `measured_at`, lọc `source='API'`); quy tắc 18 — mỗi ngày chưa đo là một ngày ⛔ biết có đang mất số liệu **vĩnh viễn** ⛔ |
| DOD4.3 | 1008 khung 10′ liên tục, sai lệch < 10% | — | — | CHƯA ĐO | 7 ngày **lịch**; dụng cụ đo đã có (BC-13) |
| DOD4.4 | 200 CCU, P95 dashboard < 3s @ 50 users | — | — | CHƯA ĐO | QuanTran chạy. ⭐ Đuôi mã đã trả (`T63.21`): 4 ngưỡng nay **phân biệt được bốn trạng thái** trên máy chủ giả, và lỗ *tập rỗng* của vế tìm kiếm đã bịt |
| DOD4.5 | Trang chủ < 3s từ máy ở VN, **gồm ISR nguội** | — | — | CHƯA ĐO | ⭐ Đuôi mã ĐÃ TRẢ 18/09 (`T63.22`). Còn lại là một lượt chạy: `HIEU_NANG_URL=… REVALIDATE_SECRET=… npx playwright test -c playwright.hieu-nang.config.ts` — QuanTran |
| DOD4.6 | Một lượt hỏng **SAU** `up -d`, rồi quay lui trả đúng **ID ảnh** đã ghi + nginx healthy + trang chủ 200 (⛔ *"`Created` quay về mốc cũ"* — container tạo lại luôn mang `Created` MỚI, sửa 19/09) | Lượt 17/09 hỏng SAU `up -d` (sự cố nginx §11.27): quay lui chỉ tạo lại 3 container, nginx chỉ *Starting*, site ⛔ hồi ⇒ **quay lui THẤT BẠI** — nó ⛔ trả lại cấu hình `deploy/` đã rsync (`T11.9`) | 17/09/2026 | CI 35236229504 | DEV ✅ WS-71 (19/09): chụp cấu hình trước rsync · quay lui trả cấu hình + `nginx -t` + `--force-recreate` + so ID ảnh + chờ nginx healthy · công tắc diễn tập `DIEN_TAP_QUAY_LUI` (environment `staging`, điều kiện staging nằm trong `if`) · QuanTran: đặt biến → gộp một PR đề bạt vào `staging` → đo theo `deploy-hong.md` mục 0 → xoá biến |
| DOD4.7 | Chuông bắn **thật**, có người **đi hết** runbook | 3/4 phần đã có (`alerts.yml:82` `NguonDuLieuImLang` · registrar · runbook `poller-chet.md` 281 dòng) | 19/09/2026 | CI 35436560640 | QuanTran — chặn bởi 6 giá trị §B6 (`T61.46`) + stack giám sát trên VPS-2 (`T61.5`) |
| DOD4.8 | Lịch gia hạn TLS ở **cả hai** máy + một lượt gia hạn khô | VPS-1 `--dry-run` thoát **0**; VPS-2 `cron` = `not-found` | 07/09 · 10/09/2026 | VPS-1 · VPS-2 | QuanTran (`sudo`). ⚠ **Hạn cứng**: staging **22/11** (3 chứng chỉ, cron phải có TRƯỚC **23/10**), production `.vn` **06/12**. ⛔ Dòng cron đúng là dòng ghi ở `deploy/gia-han-tls.sh:27`, ⛔ bản `docker compose … --profile certbot` (thoát 1 — §10.81, `T11.88`) |
| DOD4.9 | Lịch sao lưu đang chạy + một lượt khôi phục thật đọc được | — | — | CHƯA ĐO | QuanTran |
| DOD4.10 | Mọi con số nghiệm thu có ngày đo + nguồn đo | **Tệp này** + `NghiemThuCoNguonDoTest` | 18/09/2026 | CI | ✅ phía phát triển — xong |
| DOD4.11 | EICAR ra `INFECTED`, ⛔ `SKIPPED` | container `clamav` **healthy** trên VPS-2 | 17/09/2026 | VPS-2 | QuanTran (sau đề bạt production) |
| DOD4.12 | Một cảnh báo Prometheus **tới được người** | cấu hình xong; **6/10** biến §B6 còn thiếu ở VPS-2 | 16/09/2026 | VPS-2 | QuanTran (webhook + bot token) |
| DOD4.13 | Bản dump ⛔ đọc được bởi user khác | `umask 027` + `chmod 640` trong mã, có bộ canh | 14/09/2026 | CI | QuanTran — ⚠ `usermod -aG` phải chạy **TRƯỚC** lượt đo, ⛔ sau: `pre-deploy-dump.sh` **cố ý** trả về `644` nếu user host ⛔ đọc được ở `640` (ưu tiên giữ điểm quay lui) ⇒ đo sớm sẽ ra `644` trong khi script chạy **đúng thiết kế** |
| DOD4.14 | NFR-05: ZAP baseline chạy trên staging, **0 FAIL chưa phân xử**, và đăng nhập Admin ⛔ 2FA bị từ chối — bài HTTP trên một run CI có id | — | — | CHƯA ĐO | QuanTran chạy `tools/zap/zap-baseline.sh` (`T61.28`); DEV phân xử từng FAIL/WARN |
| DOD4.15 | NFR-09: bộ tương thích Playwright 3 engine × 4 bề rộng xanh trên staging **sau #169** (hoặc mỗi bài đỏ có dòng nợ) | — | — | CHƯA ĐO | `T61.29` — DEV sửa `quanTri.spec.ts` đăng nhập một lần; QuanTran cấp tài khoản đo |

---

## C. Thứ bảng này cố ý ⛔ làm

Nó **⛔ tự đo**. Nó chỉ bắt một con số phải khai **nó đến từ đâu và ngày nào** — và từ chối
nguồn `máy dev`. Ô nào `CHƯA ĐO` thì đó là **một câu khẳng định**, ⛔ phải một chỗ trống bỏ quên
(quy tắc 16).
