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
| NFR-03 | Sai lệch cron < 10%, ⛔ bỏ sót khung 10′ | — | — | CHƯA ĐO | Đòi **7 ngày lịch liên tục** = 1008 khung (`T37.1`); đồng hồ ⛔ bấm được trước `DOD4.1` |
| NFR-04 | Báo cáo tháng < 60s | — | — | CHƯA ĐO | — |
| NFR-05 | ⛔ mật khẩu/credential dạng thô; 2FA bắt buộc Admin | 31 dòng ASVS L1 đối chiếu | 16/09/2026 | CI | `T61.40`; ⚠ sổ từng ghi **23**, đo lại là **31** |
| NFR-06 | Phân quyền dữ liệu — bài kiểm 100% pass | — | — | CHƯA ĐO | ⚠ **Bộ canh bắt chính tôi ở lượt chạy đầu**: bản nháp ghi *1985 testcase BE, 0 đỏ* vào ô Số đo với ngày `—`. Con số ấy đo ở **máy dev** ⇒ theo luật của chính bảng này nó **⛔ phải một phép đo nghiệm thu**. Điền sau một lượt CI trên `dev` |
| NFR-07 | Audit giữ 5 năm, có old/new value | — | — | CHƯA ĐO | — |
| NFR-08 | RPO ≤ 24h · RTO ≤ 4h; backup < 26h, khác máy | — | — | CHƯA ĐO | `docs/runbook/dien-tap-khoi-phuc.md` còn **8 ô trống** — chúng là ô điền **sau khi đo** (`DOD4.9`) |
| NFR-09 | Chrome/Firefox/Edge/Safari · 360–2560px | — | — | CHƯA ĐO | Playwright 3 engine đã viết (`T61.29`), chưa vào CI (`T38.10`) |

---

## B. DoD Phase 4 — DOD4.1 → DOD4.13

| Mã | Điều phải chứng minh | Số đo | Ngày đo | Nguồn đo | Ai làm được |
|---|---|---|---|---|---|
| DOD4.1 | `dev → staging → production` đi trọn, đo trên container thật | production tụt **22** commit | 18/09/2026 | CI | QuanTran — ⛔ phải việc mã |
| DOD4.2 | Poller production ghi byte thật | `DiaChiNguon:98` vẫn `goc.resolve(duongDan)` ⇒ **chưa có bản vá** | 18/09/2026 | CI | QuanTran (quy tắc 18 — mỗi ngày chậm là một ngày mất số liệu **vĩnh viễn**) |
| DOD4.3 | 1008 khung 10′ liên tục, sai lệch < 10% | — | — | CHƯA ĐO | 7 ngày **lịch**; dụng cụ đo đã có (BC-13) |
| DOD4.4 | 200 CCU, P95 dashboard < 3s @ 50 users | — | — | CHƯA ĐO | QuanTran chạy. ⭐ Đuôi mã đã trả (`T63.21`): 4 ngưỡng nay **phân biệt được bốn trạng thái** trên máy chủ giả, và lỗ *tập rỗng* của vế tìm kiếm đã bịt |
| DOD4.5 | Trang chủ < 3s từ máy ở VN, **gồm ISR nguội** | — | — | CHƯA ĐO | ⭐ Đuôi mã ĐÃ TRẢ 18/09 (`T63.22`). Còn lại là một lượt chạy: `HIEU_NANG_URL=… REVALIDATE_SECRET=… npx playwright test -c playwright.hieu-nang.config.ts` — QuanTran |
| DOD4.6 | Một lượt hỏng **SAU** `up -d` rồi `Created` quay về mốc cũ | 2 lượt quay lui đã chạy đều dừng ở `migrator` ⇒ **⛔ có gì bị thay** | 14/09/2026 | CI | ⬜ cần một công tắc diễn tập — **hỏi QuanTran trước**, đó là đường cố tình hạ site |
| DOD4.7 | Chuông bắn **thật**, có người **đi hết** runbook | 3/4 phần đã có (`alerts.yml` · registrar · runbook 214 dòng) | 14/09/2026 | CI | QuanTran |
| DOD4.8 | Lịch gia hạn TLS ở **cả hai** máy + một lượt gia hạn khô | VPS-1 `--dry-run` thoát **0**; VPS-2 `cron` = `not-found` | 07/09 · 10/09/2026 | VPS-1 · VPS-2 | QuanTran (`sudo`). ⚠ **Hạn cứng**: staging **22/11**, production `.vn` **06/12** |
| DOD4.9 | Lịch sao lưu đang chạy + một lượt khôi phục thật đọc được | — | — | CHƯA ĐO | QuanTran |
| DOD4.10 | Mọi con số nghiệm thu có ngày đo + nguồn đo | **Tệp này** + `NghiemThuCoNguonDoTest` | 18/09/2026 | CI | ✅ phía phát triển — xong |
| DOD4.11 | EICAR ra `INFECTED`, ⛔ `SKIPPED` | container `clamav` **healthy** trên VPS-2 | 17/09/2026 | VPS-2 | QuanTran (sau đề bạt production) |
| DOD4.12 | Một cảnh báo Prometheus **tới được người** | cấu hình xong; **6/10** biến §B6 còn thiếu ở VPS-2 | 16/09/2026 | VPS-2 | QuanTran (webhook + bot token) |
| DOD4.13 | Bản dump ⛔ đọc được bởi user khác | `umask 027` + `chmod 640` trong mã, có bộ canh | 14/09/2026 | CI | QuanTran — ⚠ `usermod -aG` phải chạy **TRƯỚC** lượt đo, ⛔ sau: `pre-deploy-dump.sh` **cố ý** trả về `644` nếu user host ⛔ đọc được ở `640` (ưu tiên giữ điểm quay lui) ⇒ đo sớm sẽ ra `644` trong khi script chạy **đúng thiết kế** |

---

## C. Thứ bảng này cố ý ⛔ làm

Nó **⛔ tự đo**. Nó chỉ bắt một con số phải khai **nó đến từ đâu và ngày nào** — và từ chối
nguồn `máy dev`. Ô nào `CHƯA ĐO` thì đó là **một câu khẳng định**, ⛔ phải một chỗ trống bỏ quên
(quy tắc 16).
