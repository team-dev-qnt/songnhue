# Bộ đo tải — NFR-02 (T37.2 · T61.6)

Hai kịch bản [k6](https://grafana.com/docs/k6/), chạy bằng Docker — ⛔ không cài gì lên máy:

| Tệp | Đo gì | Ngưỡng (đỏ khi vượt) |
|---|---|---|
| `cong-cong-khai.js` | 200 người xem cổng: trang chủ · mực nước · tìm kiếm | P95 trang chủ < 3s · **0** lượt 429 · **0** tìm kiếm rỗng dưới tải · **mốc tìm kiếm ⛔ được rỗng** · lỗi < 1% |
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
# Tự kiểm: 4 ca trên máy chủ giả, ⛔ chạm staging (cần docker + node)
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

## 2. ⛔⛔ Hạn mức khoá theo IP — đọc trước khi tin một con số xanh

Mọi xô hạn mức của hệ khoá theo **IP** — cả `RateLimitFilter` (LOGIN 30/15' · API 100/1' · PUBLIC 300/1'
· EXPORT 10/1h) lẫn nginx biên (`/api/` 30 lượt/giây · đăng nhập 20 lượt/phút, burst 10). Bắn từ **một
máy** là mọi "người dùng ảo" chung **một IP**. Vì vậy:

1. **Mỗi kịch bản đếm 429 thành chỉ số riêng (`bi_chan_429`) và ĐỎ khi có bất kỳ lượt nào.** Một P95
   nhanh vì hệ trả 429 sớm là một P95 nói dối.
2. ⛔ **Không nới hạn mức ở môi trường đo** — nới là tắt một cơ chế bảo mật thật (tiền lệ T60.9).
3. ⚠ **Với dashboard, 429 KHÔNG phải lỗi của bài đo mà là KẾT QUẢ.** Nếu Công ty ra Internet qua một
   IP NAT (chú thích `RateLimitFilter.policyFor` khẳng định vậy — **chưa đo**), thì 50 cán bộ thật cũng
   chung một xô API 100 lượt/phút, trong khi một tab dashboard tự làm mới ~2–3 lượt/phút lúc ⛔ ai bấm
   gì. Đó là nợ **T61.17**, ⛔ phải một giới hạn của bộ đo.
4. ⚠ **Tìm kiếm trên cổng**: `public-web` gọi backend lúc dựng trang từ **IP của container** ⇒ mọi khách
   chung một xô PUBLIC 300/phút; 429 bị `lib/api.ts` đổi thành `null` ⇒ trang in *"Không tìm thấy…"*
   **trong im lặng**. `tim_kiem_rong` đo đúng triệu chứng ấy — nhưng chỉ khi từ khoá **đa dạng**: đệm
   dữ liệu 5 phút của Next làm 8 từ khoá mặc định chỉ sinh ≤ 8 lượt gọi backend. Muốn tái lập vế này
   thì truyền `TU_KHOA` dài (vài trăm từ khoá thật); `setup()` tự loại từ khoá ⛔ có kết quả lúc chưa tải.

## 3. Đã kiểm chứng gì — và CHƯA gì

- ✅ `k6 inspect` parse được cả hai tệp (14/09/2026, `grafana/k6:0.57.0`); thiếu `BASE_URL`/`API_URL`
  thì dừng ngay (⛔ có giá trị mặc định — bắn nhầm production là sự cố).
- ✅ Cả hai kịch bản chạy thật trên **máy chủ giả** (14/09/2026): có 429 ⇒ thoát **99**; ⛔ 429 ⇒ thoát
  **0**. `dashboard-quan-tri.js` đi hết `setup()` (đăng nhập 2 tài khoản) + `http.batch` 4 lượt.
  ⚠ Lượt ấy lộ ra câu kiểm envelope bản đầu so **chuỗi** `"success":true` — máy chủ giả trả JSON có dấu
  cách ⇒ `checks 50%` ở CẢ lượt ⛔ có 429. Nay đọc `r.json('success')` ⇒ 100%.
- ✅ **Chiều ĐỎ đã thử — 18/09/2026** (`T63.21`, `tu-kiem/tu-kiem-tim-kiem-rong.sh`, 4/4 ca):
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
