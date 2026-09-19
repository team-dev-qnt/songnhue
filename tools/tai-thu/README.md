# Bộ đo tải — NFR-02 (T37.2 · T61.6)

Hai kịch bản [k6](https://grafana.com/docs/k6/), chạy bằng Docker — ⛔ không cài gì lên máy:

| Tệp | Đo gì | Ngưỡng (đỏ khi vượt) |
|---|---|---|
| `cong-cong-khai.js` | 200 người xem cổng: trang chủ · mực nước · tìm kiếm | P95 trang chủ < 3s · **0** lượt 429 · **0** tìm kiếm rỗng dưới tải · **0** tìm kiếm *"chưa tra cứu được"* · **mốc tìm kiếm ⛔ được rỗng** · lỗi < 1% |
| `dashboard-quan-tri.js` | 50 người mở dashboard điều hành rồi ở lại màn hình | P95 mở màn hình < 3s · **0** lượt 429 · lỗi < 1% |

```bash
docker run --rm -i -e BASE_URL=https://staging.songnhue.com \
  grafana/k6:0.57.0 run - < tools/tai-thu/cong-cong-khai.js

docker run --rm -i -e API_URL=https://admin-staging.songnhue.com/api/v1 \
  -e TAI_KHOAN='taithu01:<mk>,taithu02:<mk>' \
  grafana/k6:0.57.0 run - < tools/tai-thu/dashboard-quan-tri.js
```

Biến tuỳ chọn: `SO_NGUOI` (mặc định 200 / 50) · `LEN` (thời gian tăng tải) · `GIU` (thời gian giữ
đỉnh) · `TU_KHOA="a,b,c"` (ứng viên từ khoá tìm kiếm). Mã thoát **99** = vượt ngưỡng.

⚠ Ba núm `TI_LE_TIM_KIEM` · `NGHI_MIN` · `NGHI_KHOANG` đổi **hình dạng phiên** của người xem giả. Chúng
chỉ dành cho lượt tự kiểm; đặt khác mặc định thì `setup()` in ra một dòng cảnh báo và lượt ấy **⛔ phải
một số đo nghiệm thu**.

```bash
# Tự kiểm: 5 ca trên máy chủ giả, ⛔ chạm staging (cần docker + node)
tools/tai-thu/tu-kiem/tu-kiem-tim-kiem-rong.sh
```

---

## 1. ⛔ Đo ở đâu, và đo từ đâu

- **Staging, ⛔ production** — trừ khi QuanTran chốt khác. 200 CCU vào production giờ hành chính là tự
  gây sự cố cho người dùng thật.
- ⚠ Staging (VPS-2: 2 vCPU · 8 GB) **nhỏ hơn** production (VPS-1: 8 vCPU · 15 GB). Xanh trên staging là
  bằng chứng **mạnh hơn** cho production; đỏ trên staging thì **chưa** kết luận được gì về production.
- Ghi kèm **ngày đo · môi trường · máy bắn đặt ở đâu** (DOD4.10). Máy bắn ở nước ngoài đo cả độ trễ
  quốc tế, ⛔ phải NFR.

## 2. ⛔⛔ Hạn mức — đọc trước khi tin một con số xanh

Hạn mức của hệ, đo lại trên mã ngày 19/09/2026 (sau T61.17 và WS-72):

| Tầng | Xô | Khoá theo | Trần |
|---|---|---|---|
| backend `RateLimitFilter` | `LOGIN` | IP | 30 lượt **⛔ đúng mật khẩu** / 15' (WS-72: lượt đúng được trả lại chỗ) |
| | `PUBLIC` (cổng) | IP | 300 / phút |
| | `BIEU_MAU_CONG_KHAI` | IP | 10 / giờ |
| backend `HanMucNguoiDungFilter` | `API` | người dùng@IP (chưa đăng nhập ⇒ IP) | 100 / phút |
| | `EXPORT` | người dùng@IP | `limits.rate.export-per-hour` / giờ (mặc định 30, trần 100) |
| nginx biên | `/api/` (`api_general`) | IP | 30 / giây, burst 60 |
| | đăng nhập · làm mới · 2FA trên tên miền quản trị (`api_auth`) | IP | 20 / phút, burst 10 |
| | kết nối đồng thời (`limit_conn per_ip`) | IP | 50 (cổng · quản trị) · 30 (tệp) |

Bắn từ **một máy** là mọi "người dùng ảo" chung **một IP**. Vì vậy:

1. **Mỗi kịch bản đếm 429 thành chỉ số riêng (`bi_chan_429`) và ĐỎ khi có bất kỳ lượt nào.** Một P95
   nhanh vì hệ trả 429 sớm là một P95 nói dối.
2. ⛔ **Không nới hạn mức ở môi trường đo** — nới là tắt một cơ chế bảo mật thật (tiền lệ T60.9).
3. ⚠ **Dashboard: 429 trước hết là giới hạn của BÀI ĐO, ⛔ phải kết luận về NAT.** Từ T61.17 xô `API`
   khoá theo người dùng@IP. Bài đo chia ≤ 10 tài khoản cho 50 người ảo, nên mỗi xô gánh ~5 người, chặt
   hơn người thật (mỗi người một tài khoản). Câu hỏi NAT (T61.17) nay chỉ còn ở nginx `api_auth`, và
   phép đo NAT là việc của QuanTran, ⛔ phải của bộ đo này.
4. ⚠ **Tìm kiếm trên cổng**: `public-web` gọi backend lúc dựng trang từ **IP của container** ⇒ mọi khách
   chung một xô `PUBLIC` 300/phút. Trước WS-72, 429 bị `lib/api.ts` đổi thành `null` và trang in
   *"Không tìm thấy…"* **trong im lặng**. Nay trang in khối *"Chưa tra cứu được"* mang dấu hiệu
   `data-tra-cuu="khong-tra-loi"`, và kịch bản đếm nó thành **`tim_kiem_khong_tra_loi`**. `tim_kiem_rong`
   giữ vai khác: từ khoá có kết quả lúc không tải mà dưới tải backend trả lời RỖNG. Cả hai chỉ có mẫu
   khi từ khoá **đa dạng**: đệm dữ liệu 5 phút của Next làm 8 từ khoá mặc định chỉ sinh ≤ 8 lượt gọi
   backend. Muốn tái lập vế này thì truyền `TU_KHOA` dài (vài trăm từ khoá thật); `setup()` tự loại từ
   khoá ⛔ có kết quả lúc chưa tải.
5. ⛔ **Bẫy 503 của nginx.** Mặc định nginx trả **503** khi chặn theo `limit_req`/`limit_conn`, nên lượt
   đo đếm nó vào `http_req_failed`/`loi_5xx` như một lỗi máy chủ trong khi máy chủ khoẻ. Từ WS-72 cả hai
   chốt trả **429** (⇒ `bi_chan_429`) — đo 19/09/2026 trên `nginx:1.30-alpine`. Đo trên một máy chủ
   **chưa** đề bạt WS-72 thì phải đọc THÂN phản hồi: trang HTML *"503 Service Temporarily Unavailable"*
   là hạn mức của nginx, envelope JSON của backend mới là lỗi thật. 200 người ảo mở kết nối đồng thời
   từ một máy chạm `limit_conn per_ip 50` trước mọi thứ khác.

## 3. Đã kiểm chứng gì — và CHƯA gì

- ✅ `k6 inspect` parse được cả hai tệp (14/09/2026, `grafana/k6:0.57.0`); thiếu `BASE_URL`/`API_URL`
  thì dừng ngay (⛔ có giá trị mặc định — bắn nhầm production là sự cố).
- ✅ Cả hai kịch bản chạy thật trên **máy chủ giả** (14/09/2026): có 429 ⇒ thoát **99**; ⛔ 429 ⇒ thoát
  **0**. `dashboard-quan-tri.js` đi hết `setup()` (đăng nhập 2 tài khoản) + `http.batch` 4 lượt.
  ⚠ Lượt ấy lộ ra câu kiểm envelope bản đầu so **chuỗi** `"success":true` — máy chủ giả trả JSON có dấu
  cách ⇒ `checks 50%` ở CẢ lượt ⛔ có 429. Nay đọc `r.json('success')` ⇒ 100%.
- ✅ **Ca thứ năm — 19/09/2026** (`T61.17`, WS-72): `khong-tra-loi-duoi-tai` ⇒ **99** kèm
  `tim_kiem_khong_tra_loi`, 5/5 ca đúng trên `grafana/k6:0.57.0`. Kiểm chứng ngược: chạy bộ tự kiểm trên
  kịch bản TRƯỚC bản vá (`KICH_BAN=<bản cũ>`) thì ca ấy thoát **0**, `tim_kiem_rong` 0/118 — tức đổi
  trang mà ⛔ đổi kịch bản là 429 phía SSR biến mất khỏi bộ đo. `KichBanTaiThuTest` nay đòi trang ·
  kịch bản · máy chủ giả mang CÙNG một dấu hiệu.
- ✅ **Chiều ĐỎ đã thử — 18/09/2026** (`T63.21`, `tu-kiem/tu-kiem-tim-kiem-rong.sh`, 4/4 ca lúc ấy):
  `binh-thuong` ⇒ thoát **0** · `rong-duoi-tai` ⇒ **99** kèm `tim_kiem_rong` · `moc-rong` ⇒ **99** kèm
  `thieu_moc_tim_kiem` · `chan-429` ⇒ **99** kèm `bi_chan_429`. Ca đầu là **vế đối chứng**: ⛔ có nó thì
  một kịch bản đỏ-với-mọi-thứ cũng "đạt" cả ba ca sau (luật 9). Bộ tự kiểm đọc **tên ngưỡng nào đỏ**, ⛔
  chỉ đọc mã thoát — một bài đỏ đúng lúc mà sai chỗ vẫn dẫn người đọc đi lạc (§11.19).
- ⛔⛔ **Và lượt ấy tìm ra một lỗ đang sống**: `tim_kiem_rong` chỉ nhận mẫu khi `setup()` tìm được ít nhất
  một từ khoá CÓ kết quả, nên trên một môi trường ⛔ nội dung nó đi qua một **tập rỗng** và xanh trọn vẹn
  — xanh trong đúng tình huống *"vế tìm kiếm ⛔ được đo"* (luật 7). Đo được: bản trước lượt vá thoát **0**
  ở ca `moc-rong`. Nay có `thieu_moc_tim_kiem`, nhận mẫu ở **mọi** lượt lặp ⇒ trạng thái ấy thành một dòng
  đỏ gọi đúng tên nó.
- ✅ Bánh cóc: **`KichBanTaiThuTest`** (backend) đòi mọi chỉ số khai trong `tools/tai-thu/*.js` phải có một
  **ngưỡng**, hoặc một dòng miễn trừ kèm lý do ≥ 40 ký tự — vì k6 chỉ thoát 99 khi một *ngưỡng* bị vượt,
  nên một chỉ số ⛔ ngưỡng ⛔ bao giờ làm lượt đo đỏ trong khi đọc lên thì y hệt một cam kết đang được canh.
  ⚠ Cùng lượt, `tools/` được đưa vào **bộ lọc đường dẫn của CI** — thiếu nó thì bộ canh ⛔ chạy đúng lúc
  kịch bản tải thay đổi, và `skipped` được GitHub tính là ĐẠT (luật 24).
- ⬜ **Chưa chạy trên staging lần nào.** Đường đăng nhập đọc `data.accessToken`/`data.stage` theo đúng
  thứ `PhienHttp` đọc — trên máy chủ thật đó vẫn là suy luận từ mã tới lượt chạy đầu.
