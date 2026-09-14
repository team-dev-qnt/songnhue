# Phase 4 — Hardening · NFR · Go-live

> **Vai trò tệp này**: kế hoạch và **thứ tự**. ⛔ Nó **không** phải sổ task — mọi việc ở đây trỏ bằng
> **ID về `master-tracking.md`** (nguồn DUY NHẤT, `conventions.md` §6). ⛔ Cấm đẻ ID mới ở đây.
>
> Mở ngày **14/09/2026**, ngay sau khi PR #132 gộp vào `dev` (`2c3b8a1`, squash).
> Trạng thái nền đo từ **CI thật** `34829579535` trên `dev`: **1811 testcase BE** · 0 đỏ ·
> **74 migration / 74 vân tay** · mã lỗi **119 = 119** hai phía · **1158 dòng** sổ.

> ⭐⭐ **Đối chiếu lần 2 — 14/09 tối (`WS-61`, `T61.0`).** Bản đầu của tệp này liệt kê **12** việc
> trong khi sổ có **122** dòng mở. Đối chiếu từng dòng với mã thật ⇒ **26 dòng đã xong mà chưa tick**
> (đã lật, kèm bằng chứng) và **9 chỗ hổng ⛔ không dòng nào của kế hoạch nhắc** — ba là bảo mật / dữ
> liệu cá nhân (`T61.4` ClamAV · `T61.8` quyền bản dump · `T61.11` xoay khoá tắt chống trùng CCCD).
> Bốn câu của bản đầu đã **sai** và được sửa ngay tại chỗ bên dưới: *"14 commit"* (nay **15**) ·
> *"`PUBLIC_SITE_URL=songnhue.com`"* (production là **`thuyloisongnhue.vn`** từ 08/09) · *"DOD2.9 cần
> VM-3"* (VM-3 đã gộp vào **VPS-2**) · và *"T37.2 là việc đo"* (kho có **0** kịch bản load test).
>
> ⛔ **Ranh giới đã chốt**: phía phát triển ⛔ không SSH · ⛔ không gộp · ⛔ không đề bạt. Mọi phép đo
> trên máy chủ nằm ở **`phase4-tracking-tmp.md` §B** dưới dạng **lệnh cụ thể cho QuanTran**.

---

## 1. Phase 4 đóng bằng gì

Phase 0→3 trả lời *"hệ thống làm được gì"*. Phase 4 trả lời **ba câu khác hẳn**, và ⛔ không câu nào
đo được từ máy dev:

1. **Nó có chịu được tải đã cam kết không** — NFR-02 (200 CCU · 3s) · NFR-03 (liên tục) · NFR-04.
2. **Hỏng thì có dựng lại được không** — quay lui, sao lưu, khôi phục, gia hạn chứng chỉ.
3. **Hỏng thì có ai biết không** — chuông kêu thật, và người trực đi được runbook.

⛔ Cả ba đều là **phép đo trên hệ đang chạy**. Một bài kiểm xanh ⛔ không trả lời được câu nào trong
ba câu ấy — đó chính là lý do chúng còn treo từ Phase 0.

---

## 2. ⛔⛔⛔ Cửa vào: việc ĐẦU TIÊN ⛔ không phải viết mã

**`T60.13` — chuỗi đề bạt `dev → staging → production`.** Đo 14/09 **ở tầng mã**, ⛔ không suy đoán:

| ref | dòng ghép URL trong `DiaChiNguon.java` |
|---|---|
| `origin/production` | `goc.resolve(duongDan)` |
| `origin/dev` | `goc.resolve(chuanHoaGoc(goc, duongDan)).resolve(duongDan)` |

Đó **đúng** là khuyết tật `T52.0`/`T52.1` cho `hydro_readings = 0` · `consecutive_failures = 3576`.
Production tụt **15 commit** sau `dev` (đo lại sau `git fetch` 14/09 tối; số 14 đo TRƯỚC khi gộp #132)
và bản vá ấy nằm trong số đó. ⭐ **Staging đã mang bản vá từ 11/09** (`origin/staging:DiaChiNguon.java:105`
= `chuanHoaGoc`) và chỉ tụt **1** commit; **PR #133 `dev → staging` đã xanh 12/12, `MERGEABLE`**.

> ⛔⛔ **Phép đo nên làm TRƯỚC khi đề bạt production — `T61.2`**: staging chạy bản vá **ba ngày** mà
> lần đo cuối (`hydro_readings = 0`) là **10/09**, trước bản vá. Hỏi staging trước là cách rẻ nhất biết
> bản vá có chạy trên nguồn thật ⛔ — đỏ ở đó thì đề bạt production ⛔ cứu được gì.

> ⛔ **Quy tắc 18: ⛔ không có API lịch sử ⇒ mỗi ngày production chạy bản cũ là một ngày mất số liệu
> thuỷ văn VĨNH VIỄN.** Đây là mục duy nhất của Phase 4 mà **trì hoãn có giá tính bằng dữ liệu**,
> ⛔ không phải bằng thời gian.

Và nó chặn **cọc dài nhất**: `T37.1` đòi **7 ngày lịch liên tục**; hỏng giữa chừng là **đếm lại từ
đầu**. ⇒ Đồng hồ ấy ⛔ không bấm được trước lượt đề bạt.

### 2.1. Hai phép đo phải làm QUANH lượt đề bạt

- ⛔ **`T60.10` — TRƯỚC**: `uname -m` trên **VPS-1 và VPS-2**. Ảnh MinIO mới (`quay.io`) chỉ có
  manifest **linux/amd64**, và kho ⛔ **không ghi kiến trúc máy chủ ở bất kỳ đâu**. Bản đầu của chính
  dòng sổ ấy khai *"đúng kiến trúc cả hai VPS"* — **một câu chưa đo**, đã sửa. Biết trước rẻ hơn
  nhiều so với biết lúc container ⛔ không lên.
- ⛔ **`T60.3` — SAU**: đo rằng production **kéo được** ảnh mới. Trước bản vá, `compose.prod.yml` ghim
  một ảnh **⛔ không kéo về được nữa** (cả repository `minio/minio` đã rời Docker Hub) ⇒ đường dựng
  lại máy **và** đường quay lui đang gãy mà ⛔ không có triệu chứng, vì production chạy bằng ảnh đã
  nằm trên đĩa. `DOD0.21` ⛔ **không đi thử được** cho tới khi phép đo này xanh.
- ⚠ **Ảnh MinIO đổi ở bước `migrator`, ⛔ không phải bước `up -d`** (`T61.1`): `deploy.yml:441` `$dc pull`
  kéo **mọi** dịch vụ, và `run --rm migrator` phụ thuộc `minio-init → minio` ⇒ compose **tạo lại**
  container MinIO ngay đó (tag đổi `…Z` → `…Z.hotfix.7aa24e772`). Đo `Created` của `minio`, ⛔ chỉ app.

### 2.2. Bẫy của chính lượt đề bạt — đã trả giá, đừng trả lại

- **Gộp `dev → staging` bằng Squash làm gãy gốc chung** (§10.72) ⇒ `Promotion guard` — cổng bắt buộc
  **duy nhất** của `staging` — treo ở *Expected*, ⛔ không một dòng đỏ nào để đọc. Đo 14/09:
  `kiem-goc-chung.sh` thoát **0** (`staging` **0** commit riêng) ⇒ **gốc chung đang còn nguyên**,
  giữ được thì giữ. ⇒ Dùng **merge commit**, ⛔ không squash.
- **`skipped` của một required check được tính là ĐẠT** (luật 24) — sự vắng mặt nguy hiểm hơn màu đỏ.
- **Đo container THẬT qua SSH, ⛔ không đọc lại lời của workflow** (§10.53 · §10.60): `Created` của
  container phải nằm **trong cửa sổ deploy**, và Flyway phải in đúng thứ tự migration.
- ⚠ `gh run list` in **UTC** còn `docker ps` in **+07** — `00:28Z` và `07:30 +07` là **cùng một lượt**
  (T50.0).

---

## 3. Thứ tự phụ thuộc

```
T60.10  (uname -m, cả hai VPS)
   └─► T60.13  đề bạt dev → staging → production
          ├─► T60.3   production kéo được ảnh mới
          │      └─► DOD0.21  quay lui THẬT (cần một lượt hỏng SAU `up -d`)
          ├─► T37.1   NFR-03 — 7 NGÀY LỊCH  ◄── cọc dài nhất, bấm giờ SỚM NHẤT CÓ THỂ
          ├─► T37.3   DOD1.17 — LCP < 3s, đo từ máy ở Việt Nam, cả lượt ISR nguội
          └─► T37.2   Load test 200 CCU + P95 dashboard < 3s @ 50 users

chạy SONG SONG, ⛔ không chờ ai:
   T11.88  lịch gia hạn TLS cho staging   ◄── ⛔ CÓ HẠN: chứng chỉ hết 22/11/2026
   T61.5   Alertmanager + ping ngoài  ──► DOD2.9  bắn chuông THẬT trên VPS-2 (VM-3 đã gộp)
   T61.9   bật lịch sao lưu + kéo về VPS-2 (cần gói cron, cùng lượt T11.88)
              └─► T61.10  khôi phục vào máy TRẮNG, đo RTO (gộp T7.7·T7.13·DOD0.14·T37.8)
   T61.6   viết kịch bản load test ──► T37.2
   T61.4   ClamAV (quyết ngân sách bộ nhớ VPS-2 trước)

mã, làm được NGAY từ kho (phía phát triển):
   T61.8 quyền bản dump · T61.11 runbook xoay khoá · T61.12 vòng khứ hồi biểu mẫu công trình
   T61.14 canh giá trị ip_address · T61.13 bộ canh đối số mã lỗi · T61.15 ba sai lệch nhỏ
   T58.18 · T25.23   nợ kỹ thuật, ⛔ không chặn nghiệm thu
```

⛔ **`T60.8` đã đóng** (WS-61 lật đủ 5 dòng) — gỡ khỏi nhánh song song.

⭐ **`T37.1` bấm giờ càng sớm càng tốt** — nó chiếm **7 ngày lịch**, ⛔ không phải 7 ngày công. Mọi
việc khác của Phase 4 chạy **song song** với đồng hồ ấy.

⛔ **`T11.88` có hạn cứng**: chứng chỉ staging hết **22/11/2026**, và đo 10/09 thì VPS-2 **chưa cài
gói cron** (`is-enabled` ⇒ `not-found`). Thứ đi cảnh báo chính là thứ chưa cài (§10.81).

---

## 4. Danh mục việc — trỏ về sổ, ⛔ không chép lại nội dung

| ID | Việc | Đo bằng gì | Chặn bởi |
|---|---|---|---|
| `T60.10` | `uname -m` hai VPS | đầu ra lệnh | — |
| `T60.13` | Đề bạt 3 chặng | container thật + Flyway + HTTP 200 | `T60.10` |
| `T60.3` | Production kéo được ảnh MinIO mới | `docker compose pull` thoát 0 | `T60.13` |
| `T37.1` | **NFR-03** — 1008 khung 10′ liên tục, sai lệch cron < 10% | cột *"số khung bỏ sót"* của **BC-13** | `T60.13` |
| `T61.2` | Staging (có bản vá từ 11/09) ghi được byte thật chưa | `hydro_readings` + `api_sources` trên staging | — |
| `T61.6` | Viết kịch bản load test — kho có **0** | kịch bản chạy được + tỉ lệ 429 khai riêng | — |
| `T37.2` | **NFR-02** — 200 CCU · P95 < 3s @ 50 users | bộ load test | `T60.13` · `T61.6` |
| `T37.3` | **DOD1.17** — trang chủ < 3s | công cụ đo trang thật, **từ máy ở VN**, cả ISR nguội | `T60.13` |
| `DOD0.21` | Quay lui **dựng lại được một bản đã bị thay** | `Created` của container quay về mốc cũ | `T60.3` |
| `T61.5` | **Alertmanager + ping ngoài** — Prometheus hôm nay ⛔ gửi đi đâu | một cảnh báo tới được người | kênh QuanTran chọn |
| `DOD2.9` | Chuông poller bắn THẬT + runbook đã đi thử | lượt bắn thật trên **VPS-2** (VM-3 đã gộp) | `T61.5` |
| `T11.88` | Lịch gia hạn TLS cho **staging** | `systemctl is-enabled` + một lượt gia hạn khô | — ⛔ **hạn 22/11** |
| `T61.3` | Production là **`thuyloisongnhue.vn`**: chứng chỉ `.vn` hạn **06/12**, `admin.`/`files.` còn treo | `certbot certificates` trên VPS-1 | — |
| `T61.9` | Bật lịch sao lưu + kéo bản dump về VPS-2 | bản < 26h **trên VPS-2** | gói cron (cùng `T11.88`) |
| `T61.10` | Khôi phục vào máy **trắng**, đo RTO < 4h | runbook `dien-tap-khoi-phuc.md` hết ô `______` | `T61.9` |
| `T61.4` | **ClamAV** — mọi tệp tải lên đang `SKIPPED` | một tệp EICAR ra `INFECTED` | ngân sách RAM VPS-2 |
| `T61.8` | Bản dump ghi `644` (T11.96) | bộ canh tĩnh + `stat` trên VPS-1 | — |
| `T61.11` | Runbook xoay khoá tắt chống trùng CCCD (T51.9) | runbook dặn đúng + bài kiểm | — |
| `T61.12` | Vòng khứ hồi biểu mẫu công trình (T42.26·T47.17·T48.10) | bài render so payload | — |
| `T61.14` | Canh GIÁ TRỊ `ip_address` ở 3 bảng | bài HTTP | — |
| `T61.13` | 43 nơi ném đối số vào mã lỗi ⛔ `{n}` (nợ không dòng mở) | bộ canh đếm | — |
| `T61.7` | NFR-05 kiểm thử bảo mật · NFR-09 tương thích — ⛔ task nào | chốt phạm vi | — |
| `T11.89` · `T11.54` | Khoá SSH dùng chung hai môi trường · cổng 5201 mở trên VPS-2 | đo trên máy | — |
| `T58.18` | Chưa có bộ canh hình dạng N+1 | — | — |
| `T25.23` | 44 mã màu ghi cứng ở `admin-app` | trần `NGUONG` trong bộ canh | — |

**Việc của người dùng, ⛔ không phải của mã**: xoay khoá API thuỷ văn với nhà cung cấp · đặt hoặc gỡ
`SMTP_HOST` trên staging.

---

## 5. ⛔ Những gì KHÔNG thuộc Phase 4

**G6 · G8 · G10 ⛔ không phải task mã** (chốt 14/09, `T60.12`):

- **G8 toạ độ** — Công ty **nhập trên màn hình quản trị**. Đường nhập đã sẵn từ `T42.20` (nút *"Nhập
  vị trí từ tệp"* + tệp mẫu **do backend sinh** từ chính hằng bộ đọc dùng) và màn hình sửa từng điểm
  cũng có. ⛔ **Đừng mở lại G8 như một task mã.** Tới khi có toạ độ thì mọi lớp GIS **RỖNG** — đó là
  trạng thái **ĐÚNG**, ⛔ không phải lỗi (quy tắc 16 + lệnh cấm seed dữ liệu *"cho đẹp demo"*).
- **G6 (mẫu 2C-BNV → BCNS-07)** và **G10 (bố cục bản in)** chỉ mở lại khi có **tệp mẫu thật**.
  ⛔ Cấm tự chế bố cục; và ⛔ **đừng chọn thư viện PDF/XLSX (`T42.14`) trước khi thấy mẫu** — mẫu
  quyết định khổ giấy, gộp ô và phông tiếng Việt.

⇒ Ba mục ấy chặn **nghiệm thu**, ⛔ không chặn Phase 4.

---

## 6. Definition of Done — Phase 4

⛔ **Chỉ tick khi đã đọc THÂN bằng chứng.** Mục ⛔ không có phép đo thì **để trống**, ⛔ không tick.

| Mã | Nội dung |
|---|---|
| DOD4.1 | `dev → staging → production` đi trọn, đo **trên container thật qua SSH** (⛔ không đọc lại lời workflow) |
| DOD4.2 | Poller production ghi được **byte thật**: `hydro_readings` tăng, `last_success_at` khác NULL |
| DOD4.3 | `T37.1` xanh — **1008 khung 10′ liên tục**, sai lệch cron < 10%, đọc từ BC-13 |
| DOD4.4 | `T37.2` xanh — 200 CCU, P95 dashboard < 3s @ 50 users |
| DOD4.5 | `DOD1.17` xanh — trang chủ < 3s đo **từ máy ở Việt Nam**, gồm **cả lượt ISR nguội** |
| DOD4.6 | `DOD0.21` xanh — một lượt hỏng **SAU** `up -d` rồi `Created` quay về mốc cũ |
| DOD4.7 | `DOD2.9` xanh — chuông bắn **thật**, và có người **đi hết** runbook |
| DOD4.8 | Lịch gia hạn TLS có ở **cả hai** máy, và đã chứng minh bằng một lượt gia hạn khô |
| DOD4.9 | Sao lưu có **lịch đang chạy** và một lượt **khôi phục thật** đọc được |
| DOD4.10 | Mọi con số nghiệm thu ghi kèm **ngày đo** và **nguồn đo** (CI hay máy chủ, ⛔ không phải máy dev) |
| DOD4.11 | Quét virus chạy **thật** trên production — một tệp EICAR ra `INFECTED`, ⛔ không `SKIPPED` (`T61.4`) |
| DOD4.12 | Một cảnh báo Prometheus **tới được người** qua kênh đã chốt (`T61.5`) — NFR-01 |
| DOD4.13 | Bản dump trên máy chủ ⛔ đọc được bởi user khác (`T61.8`, `stat` = `600`) |

---

## 7. Luật áp riêng cho Phase 4

Phase này sống trên **máy chủ**, nơi bốn cái bẫy dưới đây đã cắn **nhiều lần**:

1. ⛔ **"Xanh ở máy" ⛔ không phải bằng chứng** — và nó có **ba** biến thể đã đo: `.env.local` (§10.38)
   · biến build rỗng · **đệm ảnh Docker** (`T60.1`, ảnh kéo về 12 tháng trước giấu một repository đã
   biến mất). Runner có checkout sạch **và** đệm rỗng; máy chủ ⛔ không có cái nào cả.
2. ⛔ **Tham số chỉ có hiệu lực MỘT LẦN thì tệp cấu hình ⛔ không còn là bằng chứng** (§10.56) — phải
   đo thứ **đã được tạo ra**, ⛔ không đo thứ **sẽ được tạo ra**.
3. ⛔ **Luật 32 — ba cách một lượt kiểm tự nói dối, cả ba in màu xanh.** `$?` sau một ống là mã của
   lệnh **cuối ống**; `-Dtest='A+B'` chạy **0 bài** mà Maven thoát 0; `conclusion: null` ⛔ không
   phải *"kết thúc với null"*. ⇒ In một **con số đếm được** ở mỗi bước.
4. ⛔ **Một chú thích ⛔ không phải một cổng kiểm** — cron TLS (§10.81), `SMTP_HOST` (`T50.12`),
   ảnh MinIO (`T60.2`): cả ba đều có một dòng chữ nói đúng, và ⛔ không dòng nào chặn được gì.
