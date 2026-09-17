# ZAP baseline — quét thụ động staging (T61.28 · NFR-05)

Đi kèm bảng tự đánh giá `docs/bao-mat/asvs-l1-tu-danh-gia.md`: bảng ấy **đọc mã**, còn lượt quét
này **đo hệ đang chạy** — header, cookie, lộ phiên bản, lỗi lộ ra ngoài. Hai thứ bổ sung nhau;
⛔ cái nào cũng không thay được pentest độc lập.

| Tệp | Vai trò |
|---|---|
| `zap-baseline.sh` | Kiểm biến → quét cổng công khai → (tuỳ chọn) quét trang quản trị → gộp mã thoát |
| `zap-rules.tsv` | Mức FAIL/WARN/INFO cho từng quy tắc, gắn mã ASVS tương ứng |
| `dem-ma-trang-thai.py` | Hook của ZAP: đếm mã trạng thái HTTP của mọi lượt đã gửi (để đo tỉ lệ 429) |
| `.gitignore` | ⛔ Không commit thư mục `ket-qua/` |

## 1. Chạy

Cần: Docker (đã bật daemon), `curl`. ⛔ Không cài gì lên máy.

```bash
TARGET_URL=https://staging.songnhue.com \
ADMIN_URL=https://admin-staging.songnhue.com \
tools/zap/zap-baseline.sh
```

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `TARGET_URL` | ⛔ **không có — bắt buộc** | Cổng công khai |
| `ADMIN_URL` | rỗng (bỏ qua) | Trang quản trị, quét riêng, báo cáo riêng |
| `XAC_NHAN_PRODUCTION` | rỗng | Phải đúng chữ `co` thì script mới chịu nhắm production |
| `PHUT_SPIDER` | `2` | Số phút spider mỗi mục tiêu |
| `PHUT_TOI_DA` | `15` | Trần thời gian toàn lượt mỗi mục tiêu (phút) |
| `LUONG_SPIDER` | `2` | Số luồng spider — thấp có chủ đích (§3) |
| `AJAX_SPIDER` | `khong` | `co` ⇒ thêm AJAX spider (Firefox headless trong ảnh). Nên bật cho `ADMIN_URL` vì đó là SPA: spider thường chỉ thấy `index.html` |
| `KET_QUA_GOC` | `tools/zap/ket-qua` | Nơi đặt thư mục kết quả |
| `ANH_ZAP` | bản ghim (§5) | Ghi đè ảnh ZAP |

### Hàng rào trước khi gửi lượt nào

Script **dừng với mã 3** trước khi chạm mạng nếu:

- thiếu `TARGET_URL` — ⛔ không có mặc định, vì bắn nhầm môi trường là sự cố;
- host (của `TARGET_URL` **hoặc** `ADMIN_URL`) là production — `thuyloisongnhue.vn` và mọi tên miền con,
  `songnhue.com` / `www.` / `admin.` / `files.` (tên miền cũ vẫn trỏ về VPS-1), hoặc IP `27.71.16.154` —
  mà thiếu `XAC_NHAN_PRODUCTION=co`. Phép so ⛔ phân biệt hoa thường và bỏ dấu chấm cuối
  (`thuyloisongnhue.vn.` là cùng tên miền);
- URL ⛔ phải `http(s)://…`, hoặc trỏ `localhost`/`127.*` (trong container, đó là chính container);
- `docker` vắng hoặc daemon chưa bật.

⚠ Hàng rào so **tên**, ⛔ so **IP đích**: một tên miền lạ CNAME về production vẫn đi qua. Đọc lại URL
trước khi Enter.

## 2. Đọc kết quả

Mỗi lượt tạo `ket-qua/<YYYYMMDD-HHMMSS>/` (giờ +07):

| Tệp | Nội dung |
|---|---|
| `tom-tat.txt` | **Đọc cái này trước**: ảnh ZAP, commit kho, mã thoát từng mục tiêu, dòng đếm FAIL/WARN, **tỉ lệ 429** |
| `cong-khai-bao-cao.html` · `quan-tri-bao-cao.html` | Báo cáo cho người đọc — mỗi cảnh báo kèm URL, bằng chứng, cách sửa |
| `*-bao-cao.json` | Cùng dữ liệu, cho máy đọc / so giữa hai lượt |
| `*-bao-cao.md` | Bản Markdown để dán vào sổ |
| `*-zap.log` | Nguyên văn đầu ra của ZAP |
| `*-ma-trang-thai.txt` | Số lượt theo mã HTTP (`tong`, `200`, `429`, …) do hook ghi |

Đối chiếu từng cảnh báo với cột ASVS trong `zap-rules.tsv` rồi cập nhật dòng tương ứng ở
`docs/bao-mat/asvs-l1-tu-danh-gia.md` (các dòng đang ghi *"chưa đo trên máy thật"*: 3.4.1, 9.1.x,
14.3.3, 14.4.x).

### Lượt quét baseline ⛔ phủ gì

- ⛔ **Không đăng nhập** ⇒ chỉ thấy bề mặt vô danh. Mọi `/api/v1/**` cần quyền trả 401 — ZAP chỉ soi
  được header của phản hồi 401 ấy, ⛔ soi được dữ liệu phía sau.
- ⛔ **Không active scan** ⇒ ⛔ phát hiện SQLi/XSS/SSRF bằng cách thử payload. Các lỗ đã ghi ở bảng ASVS
  §16.1 (vượt 2FA, tự cấp quyền, SVG) **về nguyên tắc ZAP baseline không thấy**. Một báo cáo sạch ⛔ có
  nghĩa là chúng đã được vá.

## 3. ⛔⛔ Hạn mức 429 — một phát hiện riêng, ⛔ nới hạn mức

Xô `PUBLIC` của backend là **300 lượt/phút mỗi IP** (`RateLimitPolicy.java`), nginx biên thêm 30 lượt/giây
cho `/api/` và 20 lượt/phút cho `/api/v1/auth/(login|refresh|2fa)` ở tên miền quản trị. Spider của ZAP
chạy từ **một IP** nên có thể chạm trần.

1. **429 làm độ phủ thủng mà báo cáo vẫn trông sạch**: một URL trả 429 thì ZAP ⛔ thấy trang thật, nên
   ⛔ có cảnh báo nào về nó. Vì vậy hook đếm mọi phản hồi, `tom-tat.txt` in `Tỉ lệ 429: a/b (x%)`, và
   lượt quét **không có FAIL nhưng có 429** thoát mã **4**, ⛔ phải 0/2.
2. **Hook hỏng ≠ 0 lượt 429.** Thiếu tệp đếm thì in *"KHÔNG ĐO ĐƯỢC"* và cũng thoát **4**.
3. ⛔ **Không nới hạn mức ở staging để lượt quét xanh** — nới là tắt một cơ chế bảo mật thật (tiền lệ
   T60.9). Muốn giảm 429: hạ `LUONG_SPIDER=1`, hạ `PHUT_SPIDER`, hoặc chấp nhận và ghi tỉ lệ vào sổ.
4. Tỉ lệ 429 **cao dù spider chậm** là một phát hiện về hạn mức (người dùng thật sau NAT chung một IP
   cũng chạm trần) — ghi thành nợ, ⛔ vá bằng cấu hình bộ quét.
5. Đối chiếu phía máy chủ (QuanTran, qua `docker exec`/`docker logs` — máy chủ ⛔ có công cụ trên host):
   dòng WARN `Chặn theo hạn mức` trong log `app`, và mã `429` trong access log nginx lọc theo IP máy quét.

## 4. Mã thoát

| Mã | Nghĩa | Làm gì |
|---:|---|---|
| 0 | Không FAIL, không WARN, **đo được** 0 lượt 429 | Vẫn đọc báo cáo INFO |
| 1 | Có ít nhất một quy tắc FAIL | Đọc HTML, mở task trong `master-tracking.md` |
| 2 | Không FAIL, có WARN, 0 lượt 429 | Đọc HTML, phân loại từng WARN |
| 3 | Lỗi chạy: thiếu biến, chặn production, docker hỏng, ⛔ kết nối được, ZAP thoát mã lạ | ⛔ Kết quả không dùng được — sửa rồi chạy lại |
| 4 | ZAP không FAIL, **nhưng** có 429 hoặc ⛔ đo được 429 | ⛔ Không đọc là sạch — xem §3 |

Gộp nhiều mục tiêu: **3 thắng tất cả, rồi 1, rồi 4, rồi 2, rồi 0.**

Mã của ZAP được đọc bằng `PIPESTATUS[0]` **ngay dòng sau** ống `docker run … | tee` — ⛔ đọc `$?` (đó là
mã của `tee`, luật 32 của dự án).

## 5. Ảnh ZAP — ghim và cách tra lại

Đang ghim: `ghcr.io/zaproxy/zaproxy:2.17.0@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef`
(đo 15/09/2026: tag `stable` và `2.17.0` cùng trỏ digest này; bản phát hành GitHub mới nhất `v2.17.0`).

Tra bản mới:

```bash
# 1. Phiên bản phát hành mới nhất
curl -s https://api.github.com/repos/zaproxy/zaproxy/releases/latest | grep '"tag_name"'

# 2. Digest của tag trên GHCR (thay 2.17.0 bằng số vừa tra)
TOKEN=$(curl -s "https://ghcr.io/token?scope=repository:zaproxy/zaproxy:pull&service=ghcr.io" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -sI -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: application/vnd.oci.image.index.v1+json' \
  https://ghcr.io/v2/zaproxy/zaproxy/manifests/2.17.0 | grep -i docker-content-digest
```

Rồi sửa `ANH_MAC_DINH` trong `zap-baseline.sh` (cả tag lẫn digest) và ngày đo ở chú thích.
⚠ Tiền lệ T60.0: cả repository `minio/minio` từng biến mất khỏi Docker Hub — ảnh ghim theo digest
**có thể** ngừng kéo về được; khi ấy chạy lại bước tra.

## 6. Quy tắc

- Quy tắc ⛔ khai trong `zap-rules.tsv` giữ mức mặc định **WARN**.
- ⛔ **Không hạ một quy tắc xuống `IGNORE` để lượt quét xanh.** Hạ mức phải có lý do ở cột 3 của tệp
  TSV và một dòng ở đây.
- Hạ mức có chủ đích hiện có:
  - `10202` (thiếu token chống CSRF trong form) → WARN: API chống CSRF bằng header `X-CSRF-Token` +
    cookie `SameSite=Strict`, ZAP chỉ soi thẻ `<form>` HTML.
  - `10096` (lộ dấu thời gian) → INFO: JSON mang mốc thời gian nghiệp vụ có chủ đích.
- ⚠ `10010` (cookie thiếu HttpOnly) để FAIL: baseline ⛔ đăng nhập nên hôm nay ⛔ gặp cookie nào. Nếu về
  sau quét có đăng nhập, cookie `XSRF-TOKEN` **cố ý** đọc được bằng JS (double-submit) — khi đó thêm
  ngoại lệ theo URL, ⛔ hạ cả quy tắc.

## 7. Đã kiểm chứng gì — và CHƯA gì

- ✅ `bash -n zap-baseline.sh` sạch; `shellcheck` (`koalaman/shellcheck:v0.10.0`, chạy bằng Docker) mức
  mặc định **0 phát hiện**, thoát 0 — đối chứng: cùng lệnh trên một script cố ý sai thoát **1** và in
  SC2086/SC2164. `-o all` chỉ còn ghi chú kiểu dáng (SC2250/SC2249/SC2310/SC2312).
- ✅ Hàng rào (15/09/2026): thiếu `TARGET_URL` · `thuyloisongnhue.vn` · `ADMIN.thuyloisongnhue.vn` ·
  `XAC_NHAN_PRODUCTION=yes` (sai chữ) · `localhost` · `ftp://` · `PHUT_SPIDER=0` ⇒ cả bảy thoát **3**,
  ⛔ container nào được chạy.
- ✅ Chạy thật hết đường (15/09/2026) với ảnh đã ghim, vào một **máy chủ giả** trên mạng LAN trả `429`
  cho mỗi lượt thứ ba: cả hai mục tiêu ra đủ HTML/JSON/MD, hook ghi `tong 25 · 200 16 · 429 8`,
  `tom-tat.txt` in `8/25 (32.0%)`, ZAP thoát 1 (máy giả ⛔ có CSP/header) ⇒ mã chung **1**.
- ✅ Gộp mã thoát (15/09/2026) bằng một `docker` giả đặt đầu `PATH`: WARN+429 ⇒ **4** · WARN+thiếu tệp
  đếm ⇒ **4** · sạch+0 lượt 429 ⇒ **0** · WARN+0 lượt 429 ⇒ **2** · FAIL+429 ⇒ **1** · docker thoát 137 ⇒
  **3** · ZAP thoát 3 ⇒ **3** · cổng ⛔ kết nối được ⇒ **3**. Hàng rào thêm: `thuyloisongnhue.vn.`
  (dấu chấm cuối), `27.71.16.154`, `www.songnhue.com` ⇒ **3**.
- ⬜ **Chưa chạy vào staging** — việc của QuanTran.
- ⬜ Chưa thử `AJAX_SPIDER=co`.
- ⚠ Trên Linux, tệp kết quả do người dùng `zap` (uid 1000) trong container tạo ⇒ có thể phải `sudo rm`
  thư mục kết quả cũ.
