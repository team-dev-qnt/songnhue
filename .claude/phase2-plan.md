# PHASE 2 — KẾ HOẠCH CODING CHI TIẾT

> **Đây là kế hoạch, KHÔNG phải sổ tracking.** Task và nợ vẫn ở `master-tracking.md` (`conventions.md` §6)
> — file này chỉ giữ phần đặc tả mỗi hạng mục: điều kiện tiên quyết · đầu ra · cách kiểm chứng · bẫy
> đã trả giá. Mâu thuẫn thì `architecture-review.md` > file này > `implement.md`.
>
> Lập ngày **29/08/2026**, sau lượt rà CI/CD §10.68. Nguồn: `function-spec.md` v2.2 · `implement.md`
> §2 nhóm C2 + §3 · `architecture-review.md` §8.1–§8.3 + §9 + §10 · `business-open-questions.md`
> Phần II–III · khảo sát mã nguồn thật ngày 29/8.
>
> ⛔ **Mọi con số trong file này là số đo có hạn dùng** (§10.68-A). Đọc lại thì đo lại, đừng trích.

---

## 0. Đọc file này thế nào

| Cần gì | Đọc mục |
|---|---|
| Phase 2 gồm và **không** gồm những gì | §1 |
| Cái gì đã có sẵn, cái gì còn thiếu **đường nối** | §2 |
| Phải quyết định gì **trước khi gõ dòng mã đầu tiên** | §3 ⛔ đọc trước tiên |
| Thứ tự làm, cái gì chặn cái gì | §4 |
| Đặc tả từng hạng mục WS-27 → WS-37 | §5 |
| Definition of Done | §6 |
| Checklist luật đã trả giá — dán vào mỗi PR | §7 |
| Rủi ro + mục chờ Công ty | §8 |
| Ước lượng và thứ tự khởi động | §9 |

Ký hiệu: ⛔ cấm/chặn · ⚠ dễ sai · ⭐ bằng chứng đo được · 📌 bài học rộng hơn · ⬜ còn treo.

---

## 1. Phạm vi Phase 2

### 1.1. Trong phạm vi

| Nhóm | Nội dung | Nguồn chốt |
|---|---|---|
| **C2 — MOD-03 `hydro` trọn vẹn** | danh mục điểm đo + loại chỉ số + nguồn API · adapter `bhh40` + parser · poller + rate-limit · lưu trữ time-series (raw/readings/latest/sync_logs) · validate 2 mức + duyệt dữ liệu nghi ngờ · alert engine + ngưỡng · biểu tổng hợp theo tuyến sông + wall mode · báo cáo BC-05/11/12/13 · lớp GIS điểm đo | `implement.md` §3, §7.2 |
| **B còn lại — MOD-01** | CN-01.4 Liên hệ (hoàn thiện) · CN-01.6 Phản hồi/khảo sát · CN-01.8 Tìm kiếm · slider config banner | `implement.md` §7.6 (đã hoãn từ Phase 1) |
| **Điểm giao B×C** | Widget thủy văn công khai + khối *Mực nước* trang chủ + 2 ô KPI dashboard + `optionDuong` | `implement.md` §3 "điểm khóa dependency" |
| **NFR do Phase 2 sinh ra** | NFR-03 (không bỏ sót khung 10' nào trong 7 ngày) · load test 200 CCU (NFR-02) · NFR-04 báo cáo tháng < 60s | `architecture-review.md` §8.1 mục 12 (G12); `implement.md` §3 rủi ro #2 |

### 1.2. Ngoài phạm vi — và vì sao

| Không làm | Lý do |
|---|---|
| **CN-01.7 lưu mã số hệ thống văn bản + auto-login** | 🟥 **G5 chặn cứng.** Chưa biết mã số riêng từng người hay chung một mã ⇒ hai hướng khác nhau cả bảng, UI lẫn phân quyền. Nếu xin được token/SSO thì bỏ hẳn việc lưu credential. ⛔ Không code trước |
| Nhập tay lượng mưa (PA B của G3-a) | Chưa chốt. Giữ loại chỉ số "Lượng mưa" trong danh mục + chỗ cắm adapter thứ 2; cột hiển thị `-` |
| Nợ hạ tầng T11.2 (VPS-1) · T11.7 · DOD0.21 quay lui · T7.13 diễn tập restore · DOD0.14 RTO | Không phải mã Phase 2 — vẫn ở WS-11/WS-7. ⚠ Nhưng **chặn nghiệm thu**: xem §8.2 |
| HRM (MOD-04), GIS đo/xuất bản đồ (M2.12/M2.13), báo cáo BC-06/09/10 | Phase 3 |
| Thumbnail/biến thể ảnh (T12.7) | Hoãn có chủ đích từ WS-12 |

### 1.3. Ba thay đổi phạm vi so với `implement.md` §3

| Thay đổi | Lý do |
|---|---|
| **BC-13 (Nhật ký đồng bộ & chất lượng dữ liệu) kéo lên làm SỚM**, không xếp cuối nhóm báo cáo | Cột *"Số khung 10' bị bỏ sót"* của BC-13 là **phép đo duy nhất** của NFR-03. Không có nó thì cam kết nghiệm thu nặng nhất của Phase 2 không đo được, và 7 ngày quan sát phải bắt đầu lại từ đầu |
| **Trả nợ T25.22 (SPI cache cổng) đưa vào WS-27, thành việc chặn** | §10.62 đã đo được: `PortalCache` ở `content`, `hydro` sẽ không có đường **hợp lệ** nào để đẩy cache cổng khi có số liệu mới. Không có nó thì mực nước trên cổng trễ tới 5 phút và trông như lưu hỏng |
| **Load test 200 CCU nằm trong Phase 2**, không dồn Phase 4 | `implement.md` §3 rủi ro #2. Và Phase 2 là phase đầu tiên có **dữ liệu ghi liên tục 720 lượt/ngày** — hình dạng tải khác hẳn Phase 1 |

---

## 2. Nền — đo ngày 29/08/2026

### 2.1. Đã có sẵn, KHÔNG dựng lại (12 thứ)

| # | Thứ đã có | Ở đâu |
|---|---|---|
| 1 | Module Maven `hydro` + 5 tầng package + `pom.xml` | `backend/hydro/` (7 tệp Java, 62 dòng) |
| 2 | `classpath:db/migration/hyd` đã khai trong Flyway locations | `backend/app/src/main/resources/application.yml:44` |
| 3 | **10 mã quyền `hyd:`** đã seed + gán 6 vai trò | `V202608131007__core_seed_rbac.sql:211-274` |
| 4 | **5 mã lỗi `HYD-*`** (BE enum + `error-messages.properties` + FE `error-map.ts`) | `ErrorCode.java:130-134` |
| 5 | **8 khoá `settings` nhóm `HYDRO`** | `V202608131009__core_seed_settings.sql:54-81` |
| 6 | `HydroAlertPort` + người tiêu thụ duy nhất (mắt xích 3) | `core/spi/HydroAlertPort.java` · `ConstructionStatusService.java:153` |
| 7 | `DataFreshnessRegistry` + `TelemetryHealthIndicator` (ngưỡng im lặng 30') | `core/common/observability/` |
| 8 | Role DB `songnhue_archiver`; `songnhue_app` **không DELETE** trên `hydro_raw_logs` | `deploy/postgres/init/10-bootstrap.sh:41-45` |
| 9 | Biến env `HYDRO_API_BASE_URL` / `HYDRO_API_KEY` ở 5 tệp env | `deploy/env/*.env*` |
| 10 | Runbook **`poller-chet.md`** viết sẵn (cảnh báo `NguonDuLieuImLang`, `job_type LIKE 'HYDRO%'`) | `docs/runbook/poller-chet.md` |
| 11 | Khung FE: `WaterLevelBlock` (tên cột, ⛔ không dòng) · `COT_MUC_NUOC` 8 cột · `RealtimeFrame` · `KhoaDangNhap` | `frontend/public-web/src/` |
| 12 | `echartsTheme` + `echartsWallTheme` + `statusColors` + `severityColorKey` | `frontend/design-tokens/src/index.ts` |

### 2.2. Tám đường nối CÒN THIẾU — đây chính là hình dạng lỗi "nửa cặp đọc–ghi" (luật 27)

⚠ Bảy trong tám mục dưới đây là **vế ghi đã có, vế đọc chưa có**. Đó đúng là thứ lượt rà 28/8 tìm ra
sáu lần và §10.62 gọi tên: *"màn hình báo lưu thành công, cổng không đổi gì"*.

| # | Đường nối thiếu | Đo được |
|---|---|---|
| 1 | Migration đầu tiên của `hydro` | `db/migration/hyd/` có **0 tệp `.sql`** |
| 2 | `@ConfigurationProperties` đọc `HYDRO_API_*` | env khai ở 5 tệp, **không bean nào nạp** |
| 3 | Bean đọc 8 khoá `settings` nhóm `HYDRO` | seed từ 13/8, **chưa dòng mã nào đọc** — vi phạm luật 15 đang treo |
| 4 | `JobTypes.HYDRO_POLL` + handler + scheduler | `JobTypes` hiện có 8 mã, không mã nào là `HYDRO*` |
| 5 | Cài đặt thật của `HydroAlertPort` | `DummyHydroAlertService` trả `false` cứng |
| 6 | `DataFreshnessRegistry.register("hydro-water-level", …)` | `TelemetryHealthIndicator` báo *"Chưa có nguồn dữ liệu ngoài nào đăng ký"* |
| 7 | Gỡ 10 mã `hyd:*` khỏi `QUYEN_PHASE_SAU` khi bắt đầu dùng | `RbacMatrixTest.java:85-101` — ⚠ chiều ngược lại đang được canh: quyền đã có người dùng mà còn nằm đây thì **CI đỏ** |
| 8 | Menu + route admin `features/hydro/` | `frontend/admin-app/src/features/hydro/` **không tồn tại** |

⚠ Hai mã `hyd:alert-group:manage` và `hyd:api-source:manage` **có trong danh sách miễn kiểm nhưng
chưa thấy trong migration seed** — kiểm lại ở T27.6, đây là nửa cặp ngược chiều.

### 2.3. Trạng thái kho lúc lập kế hoạch

⛔ **WS-26 đang làm dở** — 26 tệp `M`, và `V202608291047__cms_logo_lien_ket_va_anh_ban_do.sql` là
**untracked, chưa có trong `db-migration-checksums.txt`**. Cắt nhánh Phase 2 từ đây thì
`kiem-thu-tu-migration.sh` so với **nền sai**. ⇒ **Điều kiện tiên quyết của WS-27: WS-26 đã merge
vào `dev` và `make migration-manifest` đã chạy.**

⭐ Đỉnh số hiệu migration hiện tại: **`202608291047`** ⇒ migration đầu tiên của `hydro` phải là
`V20260830<nnnn>` với `nnnn ≥ 1048`.

⚠ **Đừng hoảng khi thấy `V209912310001`.** Tệp đó là `V209912310001__test_scoped_records.sql` nằm ở
`backend/app/src/test/resources/db/migration/test/` — **ngoài** 5 location Flyway của production và
**ngoài** phạm vi hai công cụ canh (`kiem-thu-tu-migration.sh` và `sinh-vantay-migration.sh` đều
`find backend -path '*/src/main/resources/db/*'`). Nó cố ý mang số hiệu năm 2099 để luôn chạy sau cùng
trong bộ kiểm. Một lượt `find` ngây thơ sẽ thấy nó và kết luận sai về đỉnh số hiệu.

Số đo nền (29/8, để so sau Phase 2): **723 test BE** (core 249 · content 37 · operations 33 · app 404 ·
**hydro 0** · hr 0) · **300 test FE** · **74 mã lỗi** · **88 quyền / 12 vai trò** · **32 bài ArchUnit /
7 lớp** · **45 migration**.

---

## 3. ⛔ 16 điểm phải chốt TRƯỚC khi gõ dòng mã đầu tiên

Phase 1 có mục *"18 điểm nghiệp vụ đã làm rõ trước khi code"* và nó là lý do WS-13→WS-21 không phải
quay lại sửa lược đồ. Đây là bản tương ứng của Phase 2. **Cột "ai quyết" quan trọng ngang cột "đề xuất"**
— mục nào ghi *Công ty* thì không được tự quyết thay.

| # | Điểm | Vì sao phải chốt trước | Ai quyết | Đề xuất |
|---|---|---|---|---|
| **1** | **Enum `stations.status`** — CN-03.1 ghi 3 giá trị (*Hoạt động / Offline / Ngừng*), CN-03.2 + `implement.md` dùng giá trị thứ tư **`MAT_TIN_HIEU`** | Sai thì job phát hiện mất tín hiệu **không có chỗ ghi**, và CHECK của CSDL chặn lúc chạy chứ không lúc biên dịch | Nội bộ | **4 giá trị**: `HOAT_DONG` · `MAT_TIN_HIEU` · `OFFLINE` (Admin tắt tạm) · `NGUNG` (ngừng vĩnh viễn). `MAT_TIN_HIEU` do hệ thống đặt, hai giá trị kia do người đặt — ghi rõ trong comment cột |
| **2** | **Quy ước số hiệu migration của `hydro`** — `hyd/README.md` đang ghi `V<yyyyMMddHHmm>`, quy ước đúng (đã trả giá §10.66) là `V<yyyyMMdd><nnnn>` với `nnnn` là **số thứ tự chạy tiếp toàn kho** | Hai cách viết *chỉ khác nhau ở đúng chỗ không ai nhìn* — thứ tự sắp xếp. Đã làm đỏ 2 lượt CD liên tiếp ngày 27/8 | Nội bộ | Sửa `README.md` **trước** khi viết migration đầu tiên (T27.2) + thêm bài kiểm khẳng định mọi `V*.sql` khớp `^V\d{8}\d{4}__` |
| **3** | **`stations.org_unit_id` — bắt buộc hay nullable ở v1** | Spec ghi bắt buộc, nhưng **OI-05 chưa chốt 7 hay 8 Xí nghiệp** ⇒ không seed được đơn vị phụ trách cho 19 điểm đo. `NOT NULL` thì migration seed **không chạy nổi** | Nội bộ (dữ liệu: Công ty) | **Nullable ở v1** + màn hình *"Điểm đo chưa gán đơn vị"* (cùng khuôn *"chưa cấu hình ngưỡng"*). ⚠ Hệ quả: resolver người nhận cảnh báo (G11 tập 2) rỗng cho tới khi gán — phải hiện rõ, không im lặng |
| **4** | **Chu kỳ poller đọc từ `settings` hay ghi cứng** | `@Scheduled(cron=…)` **chốt chu kỳ lúc dựng bean**, không đọc lại `settings`. §10.31 lỗi 4 đã ghi tiền lệ: chọn ghi cứng thì **phải gỡ dòng dữ liệu tương ứng**, nếu không lại là một núm không điều khiển gì | Nội bộ | **Trigger động** (`SchedulingConfigurer` + `Trigger` đọc `hydro.polling.cron`, nạp lại khi `SettingChangedEvent`). Khoá đã seed từ 13/8 nên đây là vế đọc còn thiếu, không phải khoá mới |
| **5** | **Khoá ngoại `station_constructions.construction_id`** | `constructions` thuộc module `operations`. §10.4: *"mỗi module chỉ đặt khoá ngoại tới bảng của chính nó và của `core`"* | Nội bộ | **Không `REFERENCES`.** Lưu `construction_id BIGINT` + `construction_public_id UUID`; toàn vẹn kiểm ở service qua `spi` của `operations`; job đối soát phát hiện tham chiếu chết |
| **6** | **Bộ mức ngưỡng** — 3 mức (Cảnh báo thấp/cao, Nguy hiểm) hay báo động cấp I/II/III | **G9-a chưa chốt** | Công ty | ⛔ **Danh mục có CRUD, cấm enum** (quy tắc 16). Bảng `alert_levels` dựng đủ, **KHÔNG seed giá trị** — đổi số mức về sau chỉ là dữ liệu |
| **7** | **Hai khoá `settings` đang rỗng**: `hydro.threshold.default-set`, `hydro.quality.suspect-rule` | Đã seed từ 13/8, chưa ai đọc. Luật 15: *"công tắc chưa ai đọc là một lỗi, không phải việc để dành"* | Nội bộ | Nối vế đọc ở WS-32 (`suspect-rule`) và WS-33 (`threshold.default-set`). Mục nào Phase 2 **không** dùng thì **gỡ khỏi seed bằng migration**, không để đó |
| **8** | **Retention: readings vs raw logs** | Nhịp 2' ⇒ ~720 response/ngày. Raw giữ cùng 5 năm là vô nghĩa về dung lượng | Nội bộ | `hydro_readings` **5 năm** (`hydro.retention-years`, đã seed). `hydro_raw_logs` **retention riêng ngắn hơn** — khoá mới `hydro.raw-retention-days` (đề xuất 90), ⛔ **seed cùng lúc với mã đọc**, không sớm hơn |
| **9** | **Raw logs có kết xuất lưu trữ trước khi xoá không** | `audit_logs` có (G7, checksum SHA-256 lên MinIO). Raw log không phải chứng cứ pháp lý | Nội bộ | **Xoá thẳng, không kết xuất** ở v1 — ghi lý do vào migration. Nếu đổi ý thì chép khuôn `AUDIT_ARCHIVE` |
| **10** | **`hydro_agg_daily` có làm ở Phase 2 không** | BC-05/BC-13 cần tổng hợp; §2.3 chốt *"báo cáo đọc bảng agg, không bao giờ scan raw"* | Nội bộ | **Có** — bảng agg tối thiểu (ngày × điểm đo × loại chỉ số: max/min/avg/count/count_nghi_ngo), ⛔ **idempotent UPSERT theo khoá kỳ + `computed_at`**, dirty-flag khi bản ghi được duyệt/xoá |
| **11** | **Widget cổng hiển thị điểm đo nào** | **OI-03 chưa chốt** danh sách 10 cống trục chính. §10.61 mục 6 đã cố ý **không dựng sẵn lưới 10 cống** | Công ty | Danh sách **cấu hình được từ admin** (chọn điểm đo). Rỗng → khối hiện `EmptyBlock` kèm lý do, ⛔ không bịa, ⛔ không dựng lưới rỗng có dấu gạch |
| **12** | **Hai chu kỳ auto-refresh khác nhau** — bảng realtime nội bộ **2'** (CN-03.4) và widget công khai **5/10/15/30'** (CN-01.5, OI-09) | *"Hai con số khác nhau và cùng đúng — đừng gộp thành một tham số"* | Nội bộ | **Hai khoá riêng.** ⛔ Không tạo một khoá dùng chung — §10.15: *"hai công tắc cho một bóng đèn"* là lỗi, nhưng **một công tắc cho hai bóng đèn cũng là lỗi** |
| **13** | **Đặt `TelemetryAdapter` ở tầng nào** | Nó là hợp đồng **nội bộ module**, không phải hợp đồng liên module | Nội bộ | `hydro.domain` (interface) + `hydro.infra` (`Bhh40Adapter`, `MockAdapter`). ⛔ **Không** để ở `hydro.spi` — `spi` chỉ khai những gì module khác gọi (luật *"SPI mỏng là cố ý"*) |
| **14** | **`hydro.spi` xuất những gì cho `content` và `operations`** | Nếu khai thừa thì phải giữ mãi; nếu khai thiếu thì có người sẽ nới ArchUnit | Nội bộ | Đúng ba thứ: `HydroLatestQueryPort` (đọc `hydro_latest` cho widget/GIS/dashboard) · `HydroAlertQueryPort` (đếm cảnh báo đang mở cho KPI) · giữ nguyên `core.spi.HydroAlertPort` cho mắt xích 3. ⛔ Không xuất repository, không xuất entity |
| **15** | **Đặt cổng xoá cache cổng ở đâu** (nợ T25.22) | `PortalCache` ở `content`; `core`/`operations`/`hydro` không gọi được (quy tắc 6) | Nội bộ | **Chép đúng khuôn `HydroAlertPort`**: interface `core.spi.PortalCachePort`, cài đặt ở `content`. Hướng phụ thuộc giữ nguyên (nghiệp vụ → core), và mọi module gọi được |
| **16** | **Có thêm thư viện mới không** (HTTP client, CSV/Excel, WireMock) | §10.22 license (TipTap/CKEditor) + §10.68-A bề mặt CVE | Nội bộ | JDK `HttpClient` (không thêm) · CSV viết tay (không thêm) · ⛔ **không thêm Apache POI** ở phase này (§10.32) · WireMock **chỉ ở `test` scope** — hoặc dùng `com.sun.net.httpserver` của JDK để khỏi thêm gì |

---

## 4. Thứ tự và phụ thuộc

```
WS-27  Nền Phase 2 (mở đường + trả nợ chặn)  ─── CHẶN MỌI THỨ
   │
   ├─► WS-28  Lược đồ danh mục: measurement_types · api_sources · stations · station_constructions
   │      │
   │      ├─► WS-29  Lưu trữ time-series: raw_logs · readings · latest · sync_logs · partition · retention
   │      │      │
   │      │      ├─► WS-30  Adapter bhh40 + parser 10 quy tắc  ──┐
   │      │      │                                              │
   │      │      └─────────────────────────────────────────────►├─► WS-31  Poller: đặt việc · rate-limit · ingest · mất tín hiệu
   │      │                                                     │         └─► WS-32  Validate + dữ liệu nghi ngờ
   │      │                                                     │                └─► WS-33  Alert engine + ngưỡng
   │      │                                                     │                       │
   │      └─────────────────────────────────────────────────────┘                       │
   │                                                                                    │
   ├─► WS-36  MOD-01 phần còn lại (Liên hệ · Phản hồi · Tìm kiếm · slider)  ← song song, chỉ cần WS-27
   │                                                                                    │
   └────────────────────────────────────────────────────────────────────────────────────┤
                                                                                        ▼
                                                          WS-34  Tổng hợp + báo cáo (BC-13 → BC-11 → BC-05 → BC-12)
                                                                                        │
                                                                                        ▼
                                                          WS-35  GIS + Dashboard + Widget cổng (điểm giao B×C)
                                                                                        │
                                                                                        ▼
                                                          WS-37  Nghiệm thu Phase 2 + NFR + hardening
```

**Bốn ràng buộc thứ tự thật sự** (những cái còn lại chỉ là thuận tiện):

1. **WS-27 chặn cứng.** Không có `HydroProperties`, không có bean đọc `settings`, không có `PortalCachePort` thì mọi việc sau đều phải quay lại sửa.
2. **WS-30 (adapter) và WS-29 (lưu trữ) làm được song song** — chỉ cần thống nhất kiểu `TelemetryReading(apiCode, measuredAt, valueCm)` trước.
3. **WS-31 phải xong trước khi bắt đầu đếm 7 ngày của NFR-03.** Mỗi ngày trễ là một ngày dữ liệu **không lấy lại được** — nguồn không có API lịch sử.
4. **BC-13 (trong WS-34) phải xong trước lượt quan sát NFR-03**, vì nó là dụng cụ đo. Làm BC-13 sau khi quan sát xong là đo bằng thước chưa tồn tại lúc đo.

📌 **WS-36 (MOD-01) chạy song song được với toàn bộ nhánh `hydro`** — nó chỉ cần WS-27. Nếu có hai
người thì đây là đường tách việc rẻ nhất.

---

## 5. Đặc tả từng hạng mục

> Mỗi WS tự chứa **điều kiện tiên quyết / đầu ra / task / cách kiểm chứng / bẫy** để làm độc lập.
> Task ID trong `master-tracking.md` — ở đây chỉ nêu ID và một dòng, chi tiết ở cột "cách kiểm chứng".

---

### WS-27 — Nền Phase 2: mở đường cho `hydro` + trả nợ chặn

**Điều kiện tiên quyết**: WS-26 đã merge vào `dev`; `make migration-manifest` đã chạy cho
`V202608291047`; `make hooks` đã bật trên bản clone đang dùng.

**Đầu ra**: một nhánh mà dòng mã `hydro` đầu tiên **biên dịch, chạy, và không làm CI đỏ vì lý do kiến
trúc**. Không có chức năng nghiệp vụ nào trong WS này — đó là chủ ý.

| ID | Việc |
|---|---|
| T27.1 | Chốt enum `stations.status` 4 giá trị (§3 mục 1), ghi quyết định vào `architecture-review.md` §11.1 |
| T27.2 | Sửa `hyd/README.md`: số hiệu `V<yyyyMMdd><nnnn>` (đang ghi sai `V<yyyyMMddHHmm>`) + bài kiểm khẳng định mọi `V*.sql` toàn kho khớp mẫu |
| T27.3 | `HydroProperties` `@ConfigurationProperties("app.hydro")` đọc `HYDRO_API_BASE_URL` / `HYDRO_API_KEY`, fail-fast lúc khởi động |
| T27.4 | Mở rộng bộ canh biến môi trường cho `HYDRO_*` — so **tập hợp hai chiều** giữa 5 tệp `.env*`, compose, `@Value`/properties và cổng kiểm |
| T27.5 | `HydroSettings` — bean đọc 8 khoá `settings` nhóm `HYDRO` qua `SettingPort`, cache + nạp lại theo `SettingChangedEvent` |
| T27.6 | Rà 12 mã quyền `hyd:*`: seed đủ (bổ sung `hyd:alert-group:manage`, `hyd:api-source:manage` nếu thiếu) + gỡ khỏi `QUYEN_PHASE_SAU` **theo tiến độ dùng**, không gỡ trước |
| T27.7 | ⭐ **Trả nợ T25.22** — `core.spi.PortalCachePort` (interface ở `core`, cài đặt ở `content`), đi qua hàng đợi job, dọn bằng `@TransactionalEventListener(AFTER_COMMIT)` |
| T27.8 | Bài kiểm chứng minh `PortalCachePort` **bắt được vi phạm**: sửa `org_units` → đo cache cổng bị xoá |
| T27.9 | Job CI mới (nếu có) vào `needs` của **`Cổng kiểm CI`** + nâng ngưỡng `so_job` của `CiGateCoverageTest` |
| T27.10 | Bài kiểm đầu tiên của module `hydro` (dù chỉ một) để cổng bao phủ JaCoCo không bị bỏ qua trong im lặng |
| T27.11 | Mở `architecture-review.md` **§11 (Phase 2)** — nơi ghi quyết định + nguyên nhân gốc của phase này, trước khi viết mã |
| T27.12 | Thêm 2 dòng vào bảng bẫy `docs/coding-guide.md`: *"adapter gọi nguồn ngoài chỉ có test mock"* và *"cron trong `settings` không tự vào `@Scheduled`"* |

**Cách kiểm chứng**
- T27.3: **xoá** `HYDRO_API_KEY` khỏi env → app **phải không khởi động được**, và thông báo nói đúng tên biến. ⚠ Điều kiện là *"khác rỗng"*, **không** phải *"đã khai"* (§9.9.2) — Docker `env_file` biến `RONG=  # chú thích` thành giá trị `# chú thích` (§10.27).
- T27.4: bài kiểm phải có **vế chống xanh-trên-tập-rỗng** (§10.68-D): nếu không tìm thấy biến nào để so thì **đỏ**, không phải "không thấy vi phạm".
- T27.5: bài kiểm đối chiếu **danh sách khoá seed ↔ danh sách khoá được đọc**, hai chiều; khoá không ai đọc → đỏ.
- T27.7/T27.8: đo bằng **số**, không bằng lời: đếm mục cache trước/sau, hoặc thời điểm `revalidate` gần nhất.

**Bẫy**
- ⛔ Cách xử lý **sai** khi vướng ArchUnit là nới luật cho phép import `core.application.*` (`conventions.md` §1.1). Nếu thấy cần, dừng lại — đó là dấu hiệu SPI khai thiếu.
- ⛔ Chú thích trong `.env` phải **lên dòng riêng** (§10.27).
- ⚠ `skipped` của một required check được GitHub tính là **ĐẠT** — job mới phải nằm dưới `Cổng kiểm CI` (§10.63).

---

### WS-28 — Lược đồ danh mục: loại chỉ số · nguồn API · điểm đo · liên kết công trình

**Điều kiện tiên quyết**: WS-27.
**Đầu ra**: CRUD đầy đủ ba danh mục + **19 điểm đo đã seed bằng mã API**, chưa có dữ liệu đo nào.

| ID | Việc |
|---|---|
| T28.1 | Migration `measurement_types` + seed 3 loại: Mực nước (m, scale 3) · **Lượng mưa (mm, scale 1)** · Lưu lượng (m³/s, scale 3). ⛔ Giữ "Lượng mưa" dù v1 chưa có nguồn (G3-a) |
| T28.2 | `api_sources` — `credential` AES-256-GCM qua `CryptoService` + `key_id`, `timeout_seconds`, `max_retry`, `cron`, `frame_minutes`, `status`, `consecutive_failures` |
| T28.3 | `stations` kế thừa `ScopedEntity` (8 cột chuẩn) — `api_code` UNIQUE · `position_role` VARCHAR+CHECK gồm **`MN_SONG`** · `river_name` · `chainage` + `chainage_m` (cột sinh) · toạ độ NUMERIC(9,6) + `geom` cột sinh · `is_interpolated` · `status` 4 giá trị · `org_unit_id` **nullable v1** |
| T28.4 | `station_measurement_types` (n–n: một điểm đo nhiều loại chỉ số) |
| T28.5 | `station_constructions` — ⛔ **không `REFERENCES` sang `constructions`**; `role` + `is_primary`; ràng buộc `is_primary.role == stations.position_role` ép ở service |
| T28.6 | ⭐ Seed **19 điểm đo** theo bảng G8b — **dùng mã API `F#####`, cấm dùng tên** |
| T28.7 | Entity + repository + `@Audited(module="hyd", …)` + `@Filter` `ScopedEntity.ORG_UNIT_FILTER_CONDITION` |
| T28.8 | CRUD ba danh mục qua `public_id` + `ScopeGuard.require` |
| T28.9 | Màn hình *"Điểm đo chưa gán đơn vị"* (hệ quả của §3 mục 3) |
| T28.10 | Mã lỗi mới nếu phát sinh → **ba tệp** (`ErrorCode.java` + `error-messages.properties` + `error-map.ts`) |
| T28.11 | Test lược đồ + seed |
| T28.12 | `make migration-order` + `make migration-manifest` |

**Cách kiểm chứng**
- Seed: khẳng định **đúng 19 dòng**, 19 mã API duy nhất, và `hasSizeGreaterThanOrEqualTo(19)` — một khẳng định **về số lượng** không chia sẻ giả định nào với mẫu regex (§10.62).
- ⛔ Nhánh bắt buộc phải có bài kiểm: điểm `MN_SONG` **không có dòng `station_constructions` nào** vẫn đọc/hiển thị được. Đừng để `NOT NULL` hay inner join làm rớt 4/19 điểm.
- Hai công trình cùng tên *"Yên Nghĩa"* (`TB Yên Nghĩa` và `Cống tiêu tự chảy Yên Nghĩa`) + cụm Liên Mạc (`Cống Liên Mạc`, `Liên Mạc 2`): bài kiểm phải chứng minh join **theo mã**, và UI hiện tên **kèm mã hoặc lý trình**.
- Migration seed: ⛔ `INSERT ... ON CONFLICT DO NOTHING` **và** khẳng định số hàng chạm (`IF NOT FOUND THEN RAISE`) — §10.66: một `UPDATE` chạm 0 hàng là hỏng câm, *không lỗi, không log*.

**Bẫy**
- ⚠ Cột chuẩn: `BaseEntity` **7 cột**, `ScopedEntity` **8 cột** — thiếu một cột thì `ddl-auto: validate` chặn **toàn bộ** context test tích hợp.
- ⚠ `VARCHAR`, **không bao giờ `CHAR(n)`**. `NUMERIC` cho mọi số đo — ArchUnit chặn `float/double` ở tầng Java.
- ⚠ `@Generated` (cột sinh `geom`, `chainage_m`) thiếu `insertable=false, updatable=false` → bản ghi trả về sau khi tạo **rỗng** ở cột đó.
- ⛔ `river_name` / `chainage` / toạ độ để **NULL** — G8 chưa có dữ liệu. ⛔ Không bịa, không suy từ tên.
- ⛔ Không endpoint nào trả `api_sources.credential`, kể cả cho Admin. UI mask; loại khỏi export cấu hình M5.17 (`conventions.md` §4.7).

---

### WS-29 — Lưu trữ time-series: raw · readings · latest · sync_logs

**Điều kiện tiên quyết**: WS-28.
**Đầu ra**: bốn bảng + partition + retention + chỉ số độ tươi dữ liệu. Chưa có ai ghi vào — poller ở WS-31.

| ID | Việc |
|---|---|
| T29.1 | `hydro_raw_logs` partition **theo tháng** + runway 12 tháng + partition **`DEFAULT`**; cột `body` TEXT **nguyên văn**, `http_status`, `duration_ms`, `outcome` |
| T29.2 | ⛔ `REVOKE UPDATE, DELETE, TRUNCATE ON hydro_raw_logs FROM songnhue_app` + `GRANT SELECT, DELETE … songnhue_archiver` **trong chính migration tạo bảng** |
| T29.3 | `hydro_readings` partition tháng — unique `(station_id, measurement_type_id, measured_at)`, index `(station_id, measured_at DESC)`, **index theo `quality`**, cột `source` (`API`/`MANUAL`), `raw_log_id` |
| T29.4 | `hydro_latest` — một dòng/(điểm đo × loại chỉ số), UPSERT; **thay cache Redis** |
| T29.5 | `sync_logs` — `status` (`SUCCESS`/`PARTIAL`/`FAILED`/`SKIPPED_UP_TO_DATE`) + ⭐ `failure_kind` **4 giá trị phân biệt được** (`NOT_WORKING`/`TIMEOUT`/`HTTP_ERROR`/`EMPTY_BODY`) + `frame_start` + bộ đếm nhận/ghi/bỏ qua/mã lạ |
| T29.6 | Job tạo partition hằng tháng (chép khuôn `AUDIT_PARTITION`) |
| T29.7 | Job retention: `hydro.retention-years` (readings, 5 năm) và **khoá mới** `hydro.raw-retention-days` cho raw — ⛔ seed **cùng lúc** với mã đọc |
| T29.8 | `DataFreshnessRegistry.register("hydro-water-level", …)` — làm mới **theo lịch**, ⛔ không đọc CSDL trong hàm gauge |
| T29.9 | Test partition + `DEFAULT` |
| T29.10 | Test quyền CSDL |

**Cách kiểm chứng**
- T29.2 ⭐ **Bài kiểm chạy bằng chính vai trò `songnhue_app`** khẳng định `UPDATE hydro_raw_logs` **bị từ chối**, và `songnhue_archiver` DELETE được. Khai `REVOKE` trong tệp SQL **chưa phải là bằng chứng nó có hiệu lực** — §10.68-D: *"khai một thứ và dùng một thứ chưa phải là nối nó"*.
- T29.9: ghi bản ghi của tháng nằm **ngoài runway** → phải rơi vào partition `DEFAULT` **không lỗi**. ⛔ Thà ghi chậm còn hơn `INSERT` lỗi làm hỏng cả giao dịch ingest — và một giao dịch ingest hỏng là **mất dữ liệu vĩnh viễn**.
- T29.8: gauge phải phân biệt được *"chưa từng có bản ghi nào"* (`-1`) với *"có 0 giây trước"* (§9.9.3).
- GRANT: kiểm bằng một lượt **khôi phục vào cluster MỚI** (§10.58) — đường "khôi phục đè lên CSDL đang chạy" không bắt được lỗi này.

**Bẫy**
- ⛔ `hydro_raw_logs` là **bản sao duy nhất tồn tại** của dữ liệu — nguồn không có API lịch sử. Tính bất biến ép ở **tầng quyền CSDL**, không chỉ ở mã.
- ⚠ Default privileges của `V202608131006` cấp sẵn `UPDATE, DELETE` cho **mọi bảng tạo sau** — chính tệp đó đã ghi lời cảnh báo đích danh `hydro_raw_logs`. Quên `REVOKE` là mất tính append-only mà **không lệnh nào báo sai**.
- ⚠ `now()` của PostgreSQL là **thời điểm bắt đầu giao dịch** (§10.32) — truy vấn time-series có cận dưới theo `createdAt` của entity khác phải lùi mốc về **đầu partition**.
- ⚠ `SUM`/`AVG` trả `null` **biến mất khỏi JSON** vì `NON_NULL` chung → `@JsonInclude(ALWAYS)`.

---

### WS-30 — Adapter `bhh40` + parser 10 quy tắc

**Điều kiện tiên quyết**: WS-28 (biết `api_code`), kiểu `TelemetryReading` đã thống nhất. Làm **song song** với WS-29.
**Đầu ra**: một hàm gọi được nguồn thật và trả về danh sách bản ghi đã chuẩn hoá — chưa ghi CSDL.

| ID | Việc |
|---|---|
| T30.1 | Interface `TelemetryAdapter` ở `hydro.domain`; ⛔ **không** đặt ở `hydro.spi` |
| T30.2 | `Bhh40Adapter` ở `hydro.infra` — HTTP client **gói vào kiểu riêng của dự án**, ⛔ không khai bean `RestClient`/`HttpClient` trần; chọn cài đặt bằng `@ConditionalOnProperty` |
| T30.3 | ⛔ **Ép HTTP/1.1 tường minh** (nguồn là ASP.NET/IIS 8.5) và **đo** bằng header `Upgrade`/`HTTP2-Settings` |
| T30.4 | Parser: **10 quy tắc, mỗi quy tắc một bài kiểm**, gồm cả response lỗi và dòng rác |
| T30.5 | Đơn vị: `BigDecimal(cm).divide(100, 3, HALF_UP)` → m |
| T30.6 | `not.working` → `UpstreamException` (`SYS-0006`) + `sync_logs.failure_kind = NOT_WORKING`; 3 lần liên tiếp → `api_sources.status = OFFLINE` + cảnh báo Admin |
| T30.7 | ⛔ Mã không có trong `stations.api_code` → bỏ qua + cảnh báo Admin, **tuyệt đối không tự tạo điểm đo** |
| T30.8 | `MockAdapter` cho CI không phụ thuộc mạng |
| T30.9 | ⭐ **Một bài chạy thật qua HTTP** với nguyên văn response mẫu |
| T30.10 | Chừa **một điểm cắm nguồn thứ hai** (lượng mưa, G3-a) — ⛔ không hard-code "1 nguồn = 1 endpoint mực nước" |
| T30.11 | Credential: giải mã **chỉ tại thời điểm gọi**; ⛔ không log, ⛔ **không đưa vào payload job**; ghi security event mỗi lượt dùng |
| T30.12 | SSRF: validate scheme/host lấy từ cấu hình Admin, ⛔ không nhận URL từ người dùng |

**10 quy tắc parse** (nguyên văn `function-spec.md` CN-03.2, mỗi dòng = một bài kiểm):

1. Ghi nguyên văn response vào `hydro_raw_logs` **trước khi parse**.
2. Body chứa `not.working` → `UpstreamException`, `sync_logs` FAILED, **không ghi reading nào**.
3. Cắt từ `<!DOCTYPE` trở đi → split theo `<br>` → bỏ dòng rỗng.
4. Mỗi dòng khớp `^([A-Z]\d+);(\d{2}/\d{2}/\d{4});(\d{2}:\d{2});value=(-?\d+(?:[.,]\d+)?);$` — dòng không khớp thì **bỏ qua + ghi log**, không làm hỏng cả mẻ. ⚠ Giữ đúng `[A-Z]\d+`, **đừng thắt lại thành `F\d{5}`**.
5. Mã lạ → bỏ qua + cảnh báo, **không tự tạo điểm đo**.
6. `dd/MM/yyyy HH:mm` diễn giải theo `Asia/Ho_Chi_Minh` → `Instant` UTC.
7. `BigDecimal(cm).divide(100, 3, HALF_UP)` — **cấm double**.
8. Chống trùng: unique `(station_id, measured_at)` + `ON CONFLICT DO NOTHING` — poll 2' trên nguồn 10' **sẽ trả trùng ở phần lớn các lần gọi**, đó là bình thường.
9. Số bản ghi hợp lệ < 50% số điểm đo đang hoạt động → ghi cảnh báo *"nguồn trả thiếu dữ liệu"*.
10. Toàn bộ một response xử lý trong **một transaction** + một dòng `sync_logs`.

**Cách kiểm chứng**
- ⛔ **Bài kiểm mock `TelemetryAdapter` không kiểm được gì cả** (luật 4 — `BackupServiceTest` mock `PostgresToolRunner` và `pg_dump` chưa từng chạy suốt 4 ngày). Bắt buộc có **một** bài dựng máy chủ HTTP thật (JDK `com.sun.net.httpserver`) trả **nguyên văn** response mẫu, gồm cả trang HTML rỗng ở đuôi và chuỗi `not.working`.
- T30.3: ⛔ **Đừng khẳng định `getProtocol()`** — §10.36 đã có một bài kiểm xanh giả đúng kiểu này: máy chủ JDK chỉ nói HTTP/1.1 nên client tự hạ cấp, khẳng định xanh cả khi đã gỡ `.version(HTTP_1_1)`. Phải đo **cái thật sự khác** giữa hai cấu hình.
- T30.5: bài kiểm dùng **giá trị thật của 19 mã** (4.47 · 2.94 · 1.90 · 2.03 · 3.51 …). Bộ canh theo hình dạng phải thử với **dữ liệu đang chạy** (§10.54, luật 25).
- T30.6: ⭐ Bốn nguyên nhân hỏng phải cho ra **bốn vân tay khác nhau** trong `sync_logs`. §10.68-B: bản cũ của bước SSH cho *cùng một vân tay `5ed3fce68b39`* cho cả ba nguyên nhân, và ba nguyên nhân ấy cần ba cách xử lý **ngược nhau**.

**Bẫy**
- ⚠ Dấu `;` **cuối `key` là một phần của giá trị env** — thiếu thì API trả `not.working`, **trông y hệt lỗi sai key**. Đừng trim.
- ⚠ Dấu chấm/phẩy thập phân: §10.32 — *"bỏ hết dấu chấm"* biến vĩ độ 21,023456 thành 21023456; CHECK bắt được khi vượt biên, **không bắt được sai số nhỏ hơn**.
- ⚠ Nguồn có thể có rate-limit/chặn IP. §10.68-C: *"một cơ chế bảo vệ và một cơ chế tự động hoá đặt cạnh nhau mà không ai đối chiếu thì chúng sẽ ăn thịt nhau"* — lượt deploy đã **tự cấm chính nó** bằng `ssh-keyscan`.
- ⛔ Trình duyệt **không bao giờ** gọi thẳng `songnhue.bhh40.net` (nguồn chạy HTTP, không TLS) — ghi rủi ro tồn dư vào hồ sơ bàn giao.

---

### WS-31 — Poller: đặt việc · rate-limit · ingest · mất tín hiệu

**Điều kiện tiên quyết**: WS-29 + WS-30.
**Đầu ra**: hệ thống bắt đầu **giữ lịch sử**. Từ giờ mỗi phút poller chết là dữ liệu mất vĩnh viễn.

| ID | Việc |
|---|---|
| T31.1 | `JobTypes.HYDRO_POLL` + `HydroPollJobHandler`; ⭐ `@Scheduled` **chỉ đặt việc vào hàng đợi**, handler mới gọi HTTP |
| T31.2 | Chu kỳ đọc từ `settings` bằng **trigger động**, nạp lại khi `SettingChangedEvent` |
| T31.3 | Scheduler nằm trong `SchedulingConfig` `@Profile("!migrate")`; ⛔ cấm `@EnableScheduling` ở lớp khác |
| T31.4 | ⭐ **Rate-limit TRƯỚC khi mở HTTP** — `frame = floor(now / 10')`; skip khi **TOÀN BỘ** điểm đo hoạt động đã có bản ghi thuộc khung |
| T31.5 | Ingest: raw ghi bằng `TransactionTemplate` `REQUIRES_NEW` để sống sót khi parse rollback |
| T31.6 | `ON CONFLICT DO NOTHING` + đếm `written`/`skipped` riêng |
| T31.7 | Quy tắc 9 → `sync_logs.status = PARTIAL` + cảnh báo |
| T31.8 | Job phát hiện **mất tín hiệu** (`hydro.station.signal-loss-frames`, mặc định 3 ≈ 30') + tự phục hồi + ghi log |
| T31.9 | Chỉ số + cảnh báo Prometheus `NguonDuLieuImLang` + email Admin — **ưu tiên ngang backup CSDL** |
| T31.10 | `TelemetryHealthIndicator` thuộc **bản tổng / M5.12**, ⛔ **KHÔNG** thuộc `readiness` |
| T31.11 | ⭐ Poller log **vân tay build + commit SHA** lúc khởi động |
| T31.12 | Backoff + trần số lượt gọi; ghi `sync_logs` **kể cả khi bị nguồn từ chối** |
| T31.13 | Màn hình *"Nhật ký đồng bộ"* (M3.16) |
| T31.14 | Cập nhật `docs/runbook/poller-chet.md` cho khớp cài đặt thật |

**Cách kiểm chứng**
- T31.4 ⛔ **Bài kiểm phải phân biệt ba nhánh**: (a) toàn bộ trạm đã có bản ghi khung hiện tại → **skip**; (b) **mới có bản ghi đầu tiên** của khung → **vẫn phải gọi**; (c) chưa có gì → gọi. Nhánh (b) là chỗ dễ sai nhất: nguồn trả rải rác trong cửa sổ `x1:30 → x8:30`, dừng sớm là **mất trạm lên muộn**.
- T31.5: làm hỏng có chủ đích — ném exception ở bước parse, khẳng định `hydro_raw_logs` **vẫn có dòng**. ⚠ Và xác nhận **bản hỏng đã thực sự được nạp** trước khi đọc kết quả (§10.35, §10.56): in một con số đo được (`sha256`, `stat`) ở mỗi bước, `touch` tệp sau khi khôi phục.
- T31.10: cố ý cho một trạm mất tín hiệu → khẳng định container **không** restart. §9.12.5: `/actuator/health` DOWN vì chuyện bình thường đã làm đỏ deploy suốt 5 phút một lần rồi.
- Bài kiểm "poller đã ghi dữ liệu" phải phân biệt **ba trạng thái**: *ghi mới* / *bỏ qua vì đã đủ* / *gọi hỏng*. Một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì (§10.36).

**Bẫy**
- ⭐ **Việc theo lịch chỉ ĐẶT việc, không tự làm** (§9.6). Chạy thẳng trong `@Scheduled` thì *"hỏng là im lặng — không trạng thái, không thử lại, không ai nhìn thấy"*. Đi qua hàng đợi thì có sẵn: trạng thái, số lần thử, backoff, màn hình theo dõi, thu hồi job treo.
- ⛔ **Không bọc ShedLock quanh worker hàng đợi.** Khoá chống trùng theo khung (`HYDRO_POLL:<frameStart>`) va chỉ mục `uq_jobs_dedup_active` là đủ — CSDL đã là điểm đồng bộ.
- ⛔ **Không tự gọi hàm `@Transactional` trong cùng lớp** (§10.20 — đã sập 2 lần, `ViewCountService` chưa từng ghi được một lượt xem nào). Dùng `TransactionTemplate`.
- ⚠ `@EnableScheduling` dựng luồng **không phải daemon** → migrator không thoát được, container đứng `Up` vô hạn, **không một dòng lỗi** (§9.11.5).
- ⛔ ⚠ **API key không được đi qua `payload` của job** — payload nằm nguyên văn trong bảng `jobs` và lọt vào bản sao lưu (javadoc `JobHandler`).
- 📌 §10.67: *"mọi công cụ chạy nền — MCP server, worker, daemon — cần một cách tự trả lời câu 'tôi có đang chạy đúng thứ trong kho không'"*. Poller là daemon. Ba lần cùng câu hỏi: container giữ image cũ (§10.53), cluster giữ collation cũ (§10.56), tiến trình giữ module cũ (§10.67).

---

### WS-32 — Validate + dữ liệu nghi ngờ (`quality` 2 mức)

**Điều kiện tiên quyết**: WS-31.
**Đầu ra**: bản ghi được phân loại `HOP_LE`/`NGHI_NGO`, có màn hình duyệt, và **một bộ canh đảm bảo mọi truy vấn tổng hợp đều lọc**.

| ID | Việc |
|---|---|
| T32.1 | Quy tắc nghi ngờ đọc `hydro.quality.suspect-rule` (khoảng vật lý + delta/giờ theo loại chỉ số) — nối vế đọc cho khoá đã seed |
| T32.2 | ⛔ **CẤM validate liên điểm đo kiểu "TL > HL"** ở mọi tầng |
| T32.3 | Bản ghi `NGHI_NGO` **vẫn ghi vào bảng chính** + thông báo Quản trị |
| T32.4 | ⭐ Bộ canh *"mọi truy vấn báo cáo/alert/agg lọc `quality = HOP_LE`"* |
| T32.5 | Workflow `HYDRO_READING` seed bằng migration: `NGHI_NGO → HOP_LE` (`hyd:measurement:review`) và `NGHI_NGO → XOA` (soft delete, `requires_reason = TRUE`) |
| T32.6 | Kiểm quy tắc **TRƯỚC** `workflow.execute`, tra đích đến bằng `WorkflowPort.allowedActions()` |
| T32.7 | Màn hình *"Dữ liệu nghi ngờ"* + đường nhập tay (`source = MANUAL`) khi API gián đoạn |
| T32.8 | Nối `HYD-2001` / `HYD-2002` vào đường chạy thật |

**Cách kiểm chứng**
- T32.2 ⭐ **Bài kiểm dùng số liệu thật bị đảo**: Vân Đình TL 1.82 < HL 2.18 (−0.36 m) và Cống tiêu tự chảy Yên Nghĩa TL 2.03 < HL 3.51 (−1.48 m) — cả hai **phải ra `HOP_LE`**. Đây là trạng thái vận hành hợp lệ (cống tiêu tự chảy khi sông ngoài đang cao), không phải lỗi dữ liệu.
- T32.4 ⛔ Bộ canh phải **parse**, không `contains` — §10.62: một bài kiểm chứng ngược đặt `--` trước câu `DELETE` và bộ canh **không đỏ**, vì regex không biết SQL có chú thích. **Bỏ chú thích trước khi khớp.** Kèm một khẳng định **về số lượng** (`hasSizeGreaterThanOrEqualTo(n)` số truy vấn được soi) để nếu bộ canh chạy trên tập rỗng thì đỏ.
- T32.4 phải có **bài tự-kiểm-chứng**: cố ý viết một truy vấn thiếu bộ lọc → bộ canh **phải đỏ**; gỡ ra → **phải xanh** (`conventions.md` §1.5).
- T32.5: ⛔ **Cấm lách bằng transition giả** (§10.7) — *"nhật ký kiểm toán của hệ này có hash chain, tức là chúng ta đang ký tên vào một lịch sử bịa"*.

**Bẫy**
- ⛔ Đây là **bẫy sai số liệu dễ mắc nhất của dự án** (quy tắc 14 `CLAUDE.md`, §8.1 mục 4). Bản ghi nghi ngờ nằm **chung bảng chính** — quên lọc một chỗ là sai số liệu ở một báo cáo mà không ai biết.
- ⚠ **Ngoại lệ hợp lệ duy nhất: BC-12** (có cột "Chất lượng"). Bộ canh phải khai ngoại lệ đó **có tên**, không khai bằng cách nới luật.
- ⚠ Kiểm quy tắc **sau** `workflowEngine.execute(...)` không bao giờ chạy tới — engine ghi thông báo → flush entity bẩn → CHECK của CSDL bắn trước (§10.34).
- ⚠ Bộ canh phải **nói ra phạm vi của chính nó** (luật 28): javadoc ghi rõ nó soi những tệp nào; không phủ hết thì mở một dòng nợ **có số đo**.

---

### WS-33 — Alert engine + cấu hình ngưỡng

**Điều kiện tiên quyết**: WS-32.
**Đầu ra**: cảnh báo ngưỡng chạy thật; **mắt xích 3** của `ConstructionStatusService.tinh()` có nguồn; màn hình cấu hình ngưỡng là **hạng mục nghiệm thu** (G9).

| ID | Việc |
|---|---|
| T33.1 | `alert_levels` — ⛔ **danh mục có CRUD, cấm enum** (code, name, color, severity_rank, is_active). **Không seed giá trị** (G9-a chưa chốt); màu vào `design-tokens` |
| T33.2 | `alert_rules` — điểm đo × loại chỉ số × mức; `condition_type` (`GT`/`LT`/`OUT_OF_RANGE`/`RATE_OF_CHANGE`); `threshold_value`, `threshold_value_high`, `delay_minutes`, `is_active` |
| T33.3 | `alert_events` — ⛔ unique `(rule_id, started_at)` chống bắn trùng; `status` `DANG_XAY_RA`/`DA_XU_LY`/`FALSE_ALARM`; ⛔ **hysteresis lưu CSDL, không lưu bộ nhớ** |
| T33.4 | ⛔ **Không FK xuyên module**: `alert_events` không `REFERENCES constructions`; `maintenance_logs` trỏ ngược bằng `alert_event_public_id UUID` |
| T33.5 | Đánh giá **trong cùng transaction ghi reading**; ⛔ chỉ trên `quality = HOP_LE`; ⛔ loại điểm `MAT_TIN_HIEU` (`HYD-2004`) |
| T33.6 | Điểm chưa cấu hình ngưỡng → nhãn *"chưa cấu hình ngưỡng"*, ⛔ **không phát cảnh báo** (`HYD-2003`) + danh sách *"Điểm đo chưa cấu hình ngưỡng"* |
| T33.7 | Resolver người nhận (G11): nhóm *"Ban điều hành"* từ `settings` ∪ người phụ trách `org_units` của công trình liên kết; khử trùng lặp; loại tài khoản khoá |
| T33.8 | Điểm `MN_SONG` không liên kết công trình → **chỉ nhóm cố định nhận** + ghi log để Admin bổ sung liên kết |
| T33.9 | ⭐ `HydroAlertPort` thật thay `DummyHydroAlertService` + nối vào `StatusReconcileJob` |
| T33.10 | Nút *"Tạo bản ghi khắc phục"* trên màn hình cảnh báo (prefill `alert_event_public_id`); ⛔ cảnh báo **không tự sinh** `maintenance_logs` |
| T33.11 | Màn hình cấu hình ngưỡng đầy đủ + lịch sử cảnh báo (**hạng mục nghiệm thu G9**) |
| T33.12 | `hydro.threshold.default-set`: nối vế đọc (ngưỡng mặc định khi tạo điểm đo mới, M5.6) **hoặc gỡ khỏi seed** |

**Cách kiểm chứng**
- T33.7 ⭐ **Dùng `NotifyRequest.alert(...)`, KHÔNG dùng `targeted(...)`.** §10.10 phân biệt hai bài toán ngược nhau: *cảnh báo vận hành* = hệ thống **đoán** ai nên biết (giữ nguyên hành vi cộng nhóm Ban điều hành — *"không ai sở hữu một mực nước vượt ngưỡng"*); *quy trình duyệt* = nơi gọi **biết chính xác**. Nhầm nhánh thì mỗi lần biên tập viên bấm "Gửi duyệt" cả ban lãnh đạo nhận email, và *"vài tuần sau không ai đọc thông báo nữa — lúc đó cảnh báo sự cố thật chết theo"*.
- T33.9 ⛔ **Mắt xích mới phải cùng chiều lọc phạm vi với 4 mắt xích còn lại** (câu native, không lọc). §10.35 lỗi 2 + luật 13: một cột dẫn xuất trộn hai nguồn khác chiều lọc thì *"kết quả phụ thuộc ai bấm F5 sau cùng"* — và giá trị sai **được ghi xuống CSDL** cho tất cả mọi người.
- T33.9: `StatusReconcileJob` phải **bao mắt xích mới**. §10.3: *"job đối soát là phần bắt buộc, không phải phần thêm"* — cột vật chất hoá luôn có nguy cơ lệch, đối soát biến sai lệch âm thầm thành sai lệch **đo được**.
- T33.3: bài kiểm restart ứng dụng giữa chừng → hysteresis **không mất**; hai lượt đánh giá liên tiếp cùng một rule không sinh hai `alert_events`.
- T33.8: bài kiểm cho điểm `MN_SONG` (TV Hà Nội, TV Ba Thá, An Cảnh, TB Hồng Vân) — 4/19 điểm, **không được coi là dữ liệu thiếu**.

**Bẫy**
- ⛔ Ràng buộc CHECK ở CSDL chặn khai `notify_permission`/`notify_owner` mà quên `notify_event` — engine kiểm `notify_event` trước tiên, thiếu nó là **thông báo không bao giờ được sinh ra, im lặng** (§10.10).
- ⛔ `ownerUserId()` **không được lấy từ `createdBy`**.
- ⚠ **Alert vận hành ≠ alert nghiệp vụ** (§2.4): Prometheus alert (hạ tầng, WS-31) và cảnh báo thuỷ văn (nghiệp vụ, WS-33) là **2 kênh, 2 đối tượng nhận**. Đừng gộp.
- ⚠ Quyền ở FE **chỉ để ẩn/hiện** — nút "Duyệt/Loại bỏ" phải có `@RequirePermission` thật (§9.10.4).
- ⚠ Quyền mới phải **vào ma trận bằng migration** — quyền ngoài ma trận = ô không vai trò nào được gán = chức năng không ai dùng được (§9.10.5, §10.32).
- ⚠ §10.36: `TECHNICIAN` là vai trò **duy nhất** có `ops:construction:create` nhưng ô chọn đơn vị đứng sau một quyền họ không có → **biểu mẫu chưa từng dùng được bởi đúng vai trò sở hữu nó**. Mọi vai trò có quyền cấu hình ngưỡng phải **đi thử hết biểu mẫu**, kể cả các ô chọn phụ trợ.

---

### WS-34 — Tổng hợp + báo cáo: BC-13 → BC-11 → BC-05 → BC-12

**Điều kiện tiên quyết**: WS-31 (cho BC-13), WS-33 (cho BC-05).
**Đầu ra**: bốn báo cáo có **khung + trường dữ liệu**; layout in ấn chờ G10.

| ID | Việc |
|---|---|
| T34.1 | `hydro_agg_daily` — ⛔ **idempotent UPSERT theo khoá kỳ + `computed_at`**; dirty-flag khi bản ghi được duyệt/xoá → tính lại |
| T34.2 | ⛔ Mọi báo cáo/dashboard **đọc agg, không scan `hydro_readings` raw** |
| T34.3 | ⭐ **BC-13 làm TRƯỚC** — cột *"Số khung 10' bị bỏ sót"* là **phép đo duy nhất của NFR-03** |
| T34.4 | **BC-11 Biểu tổng hợp mực nước theo tuyến sông** — ⭐ dùng chung layout với **wall mode 4K**, làm 1 lần dùng 2 nơi |
| T34.5 | BC-05 tháng — max/min/TB **chỉ trên `HOP_LE`**, kèm thời điểm đạt max/min |
| T34.6 | BC-12 theo yêu cầu — **ngoại lệ hợp lệ duy nhất** hiển thị cả `NGHI_NGO` (có cột "Chất lượng" + cột "Nguồn") |
| T34.7 | Xuất qua **Async Job Queue** (202 + `jobId` → worker → link tải TTL 24h) |
| T34.8 | ⛔ Không thêm Apache POI ở phase này — CSV/XLSX tối giản; quyết định ghi rõ vào §11 |
| T34.9 | Đo NFR-04: báo cáo tháng **< 60s** |
| T34.10 | ⛔ Layout in ấn chờ **G10** — làm khung + trường trước |

**Cách kiểm chứng**
- T34.7 ⛔ Endpoint tải trả `byte[]`/`Resource`/`StreamingResponseBody` — đi đúng đường danh sách cho phép theo converter (§10.52). **Bài kiểm phải đi nhánh CÓ dữ liệu**: §10.52 là lỗi ảnh cổng chưa từng trả về một byte nào suốt nhiều WS, vì bài kiểm dùng **UUID không tồn tại** nên chỉ đi nhánh 404.
- T34.4: `river_name` NULL (G8 chưa có) → nhóm **"Chưa phân tuyến"**, ⛔ **không crash**, ⛔ không bịa tuyến. Điểm mất tín hiệu → `-` ô xám. Cột lượng mưa → `-` (G3-a).
- T34.5: ô chưa có nguồn trả **rỗng kèm lý do**, ⛔ không trả `0` — *"số 0 là một câu khẳng định"*. Ràng buộc ép ở **hàm dựng của record**, không ở lời dặn (§10.33 mục 1).
- ⚠ `SUM(cost)`/`AVG` trả `null` **biến mất khỏi JSON** → `@JsonInclude(ALWAYS)`. *"Chưa ai điền"* và *"đã đo mà bằng không"* là hai câu khác nhau (§10.34 mục 4).
- T34.1: chạy cron tổng hợp **hai lần liên tiếp** → khẳng định số liệu **không nhân đôi**.

**Bẫy**
- ⚠ 9/19 điểm đo **không thành cặp TL–HL** (Lương Cổ, Hòa Mỹ, Hà Đông, Liên Mạc 2 chỉ có một vế; 4 điểm MN sông; 1 bể hút) → biểu tổng hợp và báo cáo **phải chịu được ô trống**, ⛔ không giả định mọi công trình có đủ hai vế.
- ⚠ Báo cáo nặng đi qua **hàng đợi job**, ⛔ không render đồng bộ trong request.
- ⚠ Tệp kết xuất đi qua `attachments` của Core, ⛔ không dựng bảng tệp thứ hai (§10.6).
- ⛔ **Không dùng presigned URL cho đường tải lên**, và không cho đường đọc của trang ISR (§10.25, §10.1).

---

### WS-35 — GIS + Dashboard + Widget cổng (điểm giao B×C)

**Điều kiện tiên quyết**: WS-33 (màu marker theo mức) + WS-27 (T27.7 `PortalCachePort`).
**Đầu ra**: số liệu thủy văn **thật** xuất hiện trên cổng công khai lần đầu tiên.

| ID | Việc |
|---|---|
| T35.1 | Lớp *"Điểm đo thuỷ văn"* (CN-03.7) — marker Xanh/Vàng/Đỏ/**Xám (mất tín hiệu)**; viền nét đứt khi bản ghi mới nhất `NGHI_NGO`; popup điểm xám hiện giá trị cuối + *"Dữ liệu chưa cập nhật"* |
| T35.2 | Điểm chưa có toạ độ (G8) → danh sách *"chưa số hoá vị trí"* (cơ chế đã có ở CN-02.4, ⛔ không dựng mới) |
| T35.3 | ⭐ Nối **2 ô KPI thuỷ văn** của dashboard (cảnh báo · điểm đo mất tín hiệu) — đang trả `null` kèm lý do |
| T35.4 | ⭐ `optionDuong` — chuỗi thời gian đầu tiên của hệ thống (mực nước 24h). ⛔ **Phase 2 đến mà vẫn không ai gọi thì XOÁ** |
| T35.5 | API công khai `/api/v1/public/hydro/**` với `@PublicEndpoint(reason=…)` |
| T35.6 | `content`/`operations` gọi `hydro` **chỉ qua `hydro/spi/`** — ⛔ không đụng bảng `hydro_*` |
| T35.7 | `WaterLevelBlock` trang chủ — đổ dữ liệu thật vào **hàng tiêu đề cột đã dựng sẵn** |
| T35.8 | Danh sách điểm đo hiển thị trên cổng **cấu hình được từ admin** (⚠ chặn nội dung bởi **OI-03**) |
| T35.9 | Làm mới cổng khi có số liệu/cảnh báo mới — `revalidatePath` **gửi ĐƯỜNG DẪN**, đi **qua hàng đợi**, qua `PortalCachePort` |
| T35.10 | Widget hỏng ⛔ **không được làm sập trang chủ**; API lỗi → *"Không có dữ liệu"*, ⛔ không lộ lỗi kỹ thuật |
| T35.11 | SSR đọc bằng `API_INTERNAL_BASE_URL`, ⛔ không dùng biến `NEXT_PUBLIC_*` cho lượt gọi phía máy chủ |
| T35.12 | Hai chu kỳ auto-refresh **riêng biệt**: bảng realtime nội bộ 2' · widget công khai 5' (OI-09) |
| T35.13 | Menu + route admin `features/hydro/` — sửa **đúng hai tệp** `menu.tsx` + `router.tsx`, đường dẫn `/thuy-van/...` |
| T35.14 | Màu ngưỡng vào `design-tokens`, ⛔ không ghi cứng mã màu (admin-app còn nợ T25.23 — đừng thêm) |

**Cách kiểm chứng**
- T35.7 ⛔⛔ **Đây là chỗ bài học nặng nhất của dự án nằm.** §10.54: `HydrologyQuickWidget` cũ có **5 trạm + mực nước + mức "Cảnh báo BĐ I"** gắn tên cống có thật, kèm chấm "live" nhấp nháy — tất cả đều bịa, và đã lên staging. ⛔ **Không mảng `DEFAULT_*`, không `x.length >= n ? x : [...x, ...BIA]`.** `noFabricatedContent.test.ts` đã có tên trường `waterLevel` trong danh sách bắt và soi **toàn cây** — bộ canh này phải **vẫn xanh** sau khi nối dữ liệu thật, nghĩa là dữ liệu đến từ API chứ không từ hằng số.
- T35.5: ⛔ CSRF **không áp** cho `/api/v1/public/**`; bài kiểm giữ **cả hai vế** (§10.19). Và bài kiểm phải mang **`Origin`** — `curl` không có origin, không preflight, *"đi lọt qua đúng bức tường chặn người dùng thật"* (§10.29).
- T35.9: ⛔ **Gửi đường dẫn, không gửi nhãn** (§10.17): nếu lượt `fetch` hỏng thì **không mục cache nào mang nhãn được tạo ra** — `revalidateTag` không có gì để lần ngược; đo được là gửi nhãn 2 lần đều trả `{"revalidated":true}` mà trang **vẫn rỗng**.
- T35.1: nguồn tile/ảnh mới phải mở trong **CSP** — CSP không tự đi theo cấu hình (§10.33 mục 5, §10.61). ⛔ Không nới `img-src` thành `https:` trần.
- T35.3: `DashboardHttpTest` phải **đổi khẳng định** cho hai ô này từ *"rỗng kèm lý do"* sang **số** (§10.34 mục 6 đã làm đúng việc ấy cho hai ô của WS-18).

**Bẫy**
- ⚠ §10.61 mục 6: khối *Mực nước* trên cổng cố ý **chưa dựng lưới 10 cống** — *"một lưới mà không lượt chạy nào từng đổ dữ liệu thật vào là mã chưa được kiểm, đội lốt mã đã xong"*.
- ⛔ Chỉ tới **Phase 2 khoá bật/tắt widget thuỷ văn mới được sinh ra** — cùng lúc với mã đọc nó, không sớm hơn (§10.15).
- ⚠ Widget gọi API dày đặc dễ tạo nhiều lượt 401 song song → phải đi qua `apiClient` chung (refresh đúng một lượt, §9.10.2).
- ⚠ `menu.tsx` là **một nơi duy nhất** ghép *đường dẫn ↔ nhãn ↔ quyền cần có* — có `menu.test.ts` đi kèm.

---

### WS-36 — MOD-01 phần còn lại (chạy song song, chỉ cần WS-27)

**Điều kiện tiên quyết**: WS-27.
**Đầu ra**: đóng CN-01.4, CN-01.6, CN-01.8 và slider banner. ⛔ CN-01.7 **không làm** (G5).

| ID | Việc |
|---|---|
| T36.1 | CN-01.4 — 4 transition còn lại qua **Workflow engine**; ⛔ không thêm phương thức `chuyen()` vào `ContactStatus` |
| T36.2 | Phân loại + chuyển phòng ban + ghi chú nội bộ; ⛔ **chặn xoá khi `DANG_XU_LY`** |
| T36.3 | Email báo có liên hệ mới (nhiều người nhận) + email xác nhận cho người gửi |
| T36.4 | **SLA nhắc nhở** — job quét theo lịch, ⭐ **đặt việc vào hàng đợi**, dedup theo ngày |
| T36.5 | Export Excel danh sách liên hệ |
| T36.6 | reCAPTCHA v3 — ⛔ chặn bởi **G13**; dựng chỗ cắm + **mặc định tắt**, ⛔ **không seed khoá `settings` khi chưa có mã đọc** |
| T36.7 | Bật/tắt từng trường của form liên hệ + đặt bắt buộc |
| T36.8 | CN-01.6 `feedbacks` — kiểm duyệt **100%** (D1), workflow duyệt; ⛔ không dựng `comments` |
| T36.9 | Gom Contact + Feedback về một service `inbound_submission` — ⭐ đặt bảo đảm ở **chỗ dữ liệu đi qua**, không ở nơi gọi |
| T36.10 | CN-01.8 Tìm kiếm — dùng lại `sn_khong_dau`/`unaccent` đã có, ⛔ không viết hàm thứ hai; highlight + phân trang + lọc phạm vi |
| T36.11 | Slider config banner (thời gian dừng, Fade/Slide, autoplay, arrows/dots) vào `settings` — ⛔ **phải có nơi đọc thật trong cùng lượt** |
| T36.12 | Trả nợ **T26.63** — `articles.source` không ra tới cổng, chân bài viết đang ghi cứng một câu **sai sự thật** |
| T36.13 | ⛔ CN-01.7 **KHÔNG code** (G5) |

**Cách kiểm chứng**
- T36.9 ⭐ **Đếm đủ các đường vào.** §10.31 lỗi 1: XSS lưu trữ lọt qua **hai trong ba** đường ghi `settings` vì khử trùng đặt ở *nơi gọi*. Bài kiểm phải khẳng định **số lượng đường vào** được bảo vệ, không chỉ khẳng định một đường.
- T36.11 ⛔ Đây đúng loại lỗi lượt 28/8 tìm ra **sáu lần**: khoá `settings` có ô nhập ở admin mà không dòng mã nào đọc. Checklist 6 câu ở §7.3 áp cho **từng** tham số slider.
- T36.1: bước chuyển khai bằng **dữ liệu** (`workflow_transitions`), `requires_reason` là một cột — *"thêm một bước đòi lý do về sau là một dòng `UPDATE`, không phải một lượt deploy"* (§10.37).
- T36.10: bài kiểm tìm kiếm không dấu với dữ liệu thật có dấu tổ hợp (NFD) — §10.24: `Array.from` đếm điểm mã, *"Đề"* dán từ Word ra 4 thay vì 2.

**Bẫy**
- ⛔ **Không lưu IP người gửi** liên hệ (NĐ 13/2023) — chống lạm dụng do `RateLimitFilter` lo trong bộ nhớ.
- ⚠ `Contact` đã có ràng buộc `ck_contacts_lien_lac` (phải có ít nhất một đường liên lạc ngược) ép ở **cả hàm dựng và CSDL** — giữ cả hai vế.
- ⚠ Từ vựng trình soạn thảo ↔ danh sách cho phép của bộ lọc ↔ CSS cổng: ba nơi phải nhớ, cần **một phép kiểm nhớ hộ** (luật 14, §10.23).

---

### WS-37 — Nghiệm thu Phase 2 + NFR + hardening

**Điều kiện tiên quyết**: WS-34 (BC-13 đã chạy) + WS-35 + WS-36.
**Đầu ra**: DoD Phase 2 có phép kiểm đứng sau **từng mục**; mục nào không có phép kiểm thì **để trống, không tick**.

| ID | Việc |
|---|---|
| T37.1 | ⭐ **NFR-03** — quan sát **7 ngày liên tục / 1008 khung 10'**, đo bằng cột *"số khung bỏ sót"* của BC-13; sai lệch cron < 10% |
| T37.2 | **Load test 200 CCU** (NFR-02) + P95 dashboard < 3s @ 50 users |
| T37.3 | Đóng **DOD1.17** — LCP đo bằng công cụ đo trang thật **từ máy ở Việt Nam**, và đo **cả lượt ISR nguội** |
| T37.4 | 📋 **Theo dõi 3 cặp mã trùng giá trị ≥ 3 ngày** (`F02030`/`F02031`, `F01707`/`F01820`, `F01672`/`F01965`) → hỏi Công ty trước khi gắn 2 bộ ngưỡng độc lập |
| T37.5 | Quét CVE **thật** cho mọi thư viện mới; **nâng cấp trước, suppress sau**; tra phiên bản bằng `maven-metadata.xml` |
| T37.6 | `make ci-image` — `docker build` đúng đối số của `ci.yml` (biến rỗng) |
| T37.7 | Nghiệm thu trên staging bằng **đường người dùng thật đi**, đo **độc lập qua SSH**, ⛔ không đọc lại lời workflow |
| T37.8 | Lượt **khôi phục vào cluster MỚI** để kiểm GRANT của các bảng `hydro_*` |
| T37.9 | Đối chiếu **cặp đọc–ghi** cho mọi cột/khoá/tham số Phase 2 (checklist §7.3) |
| T37.10 | Rà bộ canh: mỗi cơ chế mới có bài **tự-kiểm-chứng**; bài kiểm chứng ngược có khẳng định **về số lượng** |
| T37.11 | Cập nhật `CLAUDE.md` (số đo **kèm ngày đo**), `architecture-review.md` §11, bảng bẫy `coding-guide.md` |
| T37.12 | Ghi DoD Phase 2 — mỗi mục kèm **tên phép kiểm** |

**Cách kiểm chứng**
- T37.1: ⚠ Đây là NFR khó nhất và **không rút ngắn được** — 7 ngày là 7 ngày. Bắt đầu đếm ngay khi WS-31 ổn định, ⛔ đừng để tới tuần cuối.
- T37.2: ⛔ **Hạn mức đăng nhập là ngân sách dùng chung** giữa mọi lớp kiểm thử HTTP (§10.33 mục 7) — đăng nhập ở `@BeforeAll` + `@TestInstance(PER_CLASS)` + `X-Forwarded-For` riêng. ⛔ **Không nới hạn mức ở hồ sơ kiểm thử.**
- T37.7: ⭐ Khuôn đã dùng ngày 28/8 — đo **ba container chạy đúng ID ảnh đã triển khai**, Flyway đúng thứ tự, và **đếm byte thật** của thứ đang phục vụ, không hỏi trạng thái `Running`.
- T37.10: §10.62 — **cả hai lượt kiểm chứng ngược của một phiên đều sai**; thứ cứu được là một khẳng định **về số lượng** không chia sẻ giả định nào với mẫu.

**Bẫy**
- ⛔ *"Đã tick không phải bằng chứng"* — 25 lượt liên tiếp một bản ghi "đã xong" bị lượt rà sau bác bỏ. Nghiệm thu đối chiếu với **mã thật**, không đối chiếu với bản ghi tiến độ.
- ⛔ *"Xanh ở máy cũng không phải bằng chứng"* — mọi lượt build ở máy nạp `.env.local`, runner thì không.
- ⚠ Số đo ghi vào sổ phải **kèm ngày đo** (§10.68-A: *"mã không đổi, thế giới đổi"* — 9 CVE nổ ra mà không ai đụng vào mã).

---

## 6. Definition of Done — Phase 2 (DOD2.1 → DOD2.22)

> Khuôn giống DoD Phase 1: **mỗi mục kèm tên phép kiểm đứng sau nó. Mục nào không có phép kiểm thì
> để trống, không tick.** Ghi trạng thái ở `master-tracking.md`, mục `## DoD Phase 2`.

| ID | Cam kết | Phép kiểm phải có |
|---|---|---|
| DOD2.1 | Ranh giới module `hydro` chạy thật — `content`/`operations` chỉ gọi qua `spi` | `ModuleBoundaryTest` có ít nhất một lượt đi qua đường `content → hydro.spi` (không phải tập rỗng) |
| DOD2.2 | 19 điểm đo seed đúng bằng **mã API**, không bằng tên | Bài kiểm khẳng định **số lượng** = 19 + 19 `api_code` duy nhất |
| DOD2.3 | Điểm `MN_SONG` không liên kết công trình vẫn đọc/hiển thị/cảnh báo được | Bài kiểm riêng cho 4 điểm MN sông |
| DOD2.4 | `hydro_raw_logs` **thật sự** append-only | Bài kiểm chạy bằng vai trò `songnhue_app`: `UPDATE`/`DELETE` bị từ chối |
| DOD2.5 | Parser đúng cả 10 quy tắc, gồm response lỗi và dòng rác | 10 bài kiểm, một bài/quy tắc |
| DOD2.6 | Adapter có **một** lượt chạy thật qua HTTP với nguyên văn response mẫu | Bài kiểm dựng máy chủ HTTP thật (không mock `TelemetryAdapter`) |
| DOD2.7 | Đơn vị cm → m scale 3 đúng ở mọi đường | Bài kiểm với giá trị thật của 19 mã |
| DOD2.8 | Rate-limit dừng đúng điều kiện *"đủ TOÀN BỘ trạm"* | Bài kiểm ba nhánh (đủ / mới có bản ghi đầu / chưa có) |
| DOD2.9 | Poller hỏng thì **nhìn thấy được** | Cảnh báo Prometheus + email Admin có lượt bắn thật; runbook đã đi thử |
| DOD2.10 | `sync_logs` phân biệt được **4 nguyên nhân hỏng** | Bốn bài kiểm cho bốn `failure_kind` khác nhau |
| DOD2.11 | ⛔ Không có validate liên điểm đo kiểu *"TL > HL"* ở bất kỳ tầng nào | Bài kiểm với cặp đảo thật (Vân Đình, CTTC Yên Nghĩa) ra `HOP_LE` |
| DOD2.12 | Mọi truy vấn báo cáo/alert/agg lọc `quality = HOP_LE` | Bộ canh **parse** (bỏ chú thích trước khi khớp) + bài tự-kiểm-chứng + khẳng định số lượng |
| DOD2.13 | Đổi `quality` chỉ qua Workflow engine | Bài kiểm khẳng định không có đường `UPDATE` trực tiếp |
| DOD2.14 | Mức ngưỡng là **danh mục CRUD**, thêm mức không cần deploy | Bài kiểm thêm một mức mới bằng dữ liệu rồi phát cảnh báo mức đó |
| DOD2.15 | Điểm chưa cấu hình ngưỡng **không phát cảnh báo** và hiện nhãn | Bài kiểm `HYD-2003` + danh sách "chưa cấu hình ngưỡng" |
| DOD2.16 | Alert dedup + hysteresis **sống sót qua restart** | Bài kiểm restart giữa chừng, unique `(rule_id, started_at)` |
| DOD2.17 | Mắt xích 3 của `ConstructionStatusService` **cùng chiều lọc** với 4 mắt xích còn lại | Bài kiểm kiểu `MaintenanceScopeTest.statusSurvivesAConstructionHandover` cho cảnh báo |
| DOD2.18 | ⛔ Không dữ liệu bịa nào trên cổng | `noFabricatedContent.test.ts` **vẫn xanh** sau khi nối dữ liệu thật (chứng minh dữ liệu đến từ API) |
| DOD2.19 | Ô chưa có nguồn trả **rỗng kèm lý do**, không trả `0` | Ràng buộc ép ở hàm dựng record + bài kiểm |
| DOD2.20 | Mọi cột/khoá/tham số mới đủ **cặp đọc–ghi** | Bảng đối chiếu 6 câu (§7.3) chạy cho toàn bộ Phase 2 |
| DOD2.21 | **NFR-03** — không bỏ sót khung 10' nào trong 7 ngày (1008 khung) | Cột *"số khung bỏ sót"* của BC-13 trên môi trường chạy thật |
| DOD2.22 | **NFR-02** — 200 CCU không suy giảm đáng kể; **NFR-04** — báo cáo tháng < 60s | Kết quả load test có số; job report `completed` < 60s |

---

## 7. Checklist luật đã trả giá — dán vào mỗi PR của Phase 2

### 7.1. Trước khi viết migration

- [ ] Số hiệu `V<yyyyMMdd><nnnn>`, `nnnn` **lớn hơn mọi số đã có toàn kho** — kể cả tệp mang ngày lớn hơn hôm nay. Chạy `make migration-order`.
- [ ] Ghi **ràng buộc thứ tự phụ thuộc vào chính đầu tệp** (migration tạo khoá phải chạy trước migration ghi giá trị).
- [ ] Mọi `UPDATE`/seed **khẳng định số hàng bị chạm** hoặc dùng `INSERT ... ON CONFLICT`.
- [ ] Bảng mới → cập nhật `ALTER DEFAULT PRIVILEGES` (cả `TABLES` **và `SEQUENCES`**); bảng append-only → `REVOKE` **trong chính migration đó**.
- [ ] Cột chuẩn đủ (`BaseEntity` 7 / `ScopedEntity` 8); `VARCHAR` không `CHAR(n)`; `NUMERIC` cho số đo; `timestamptz` UTC.
- [ ] Chạy `make migration-manifest` sau khi thêm/đổi tên tệp.
- [ ] ⛔ Migration **đã phát hành là bất biến — kể cả chú thích**. Flyway băm toàn bộ tệp.

### 7.2. Trước khi viết bài kiểm

- [ ] Cơ chế canh gác mới có bài **chứng minh nó bắt được vi phạm**?
- [ ] Bài kiểm có đi **nhánh CÓ dữ liệu**, không chỉ nhánh 404/rỗng?
- [ ] Khẳng định có **phân biệt được hai trạng thái** không? (không thì nó không khẳng định gì)
- [ ] Có chỗ nào mock đúng ranh giới mã chạm ra ngoài mà **không** có một bài chạy thật kèm theo?
- [ ] Endpoint trình duyệt gọi → bài kiểm có mang `Origin` không?
- [ ] Bộ canh **parse** hay chỉ `contains`? Có bỏ chú thích trước khi khớp không?
- [ ] Có một khẳng định **về SỐ LƯỢNG** để chống xanh-trên-tập-rỗng không?
- [ ] Lớp `*HttpTest` đăng nhập ở `@BeforeAll` + `X-Forwarded-For` riêng?
- [ ] Bộ canh **nói ra phạm vi của chính nó** trong javadoc?

### 7.3. ⭐ Sáu câu cho MỖI cột / khoá / tham số mới (luật 27)

Lượt rà 28/8 tìm ra **sáu** chỗ thiếu đúng một nửa cặp đọc–ghi, **bốn** trong đó ra đời **một ngày
trước** từ một đợt cẩn thận, có bài kiểm, có nghiệm thu. Triệu chứng luôn giống nhau và luôn im lặng:
*màn hình báo lưu thành công, cổng không đổi gì.*

1. Có migration tạo nó không?
2. Có setter **và** có lời gọi setter **ngoài bài kiểm** không?
3. Có endpoint ghi không?
4. Có **màn hình nào gọi** endpoint đó không?
5. Có đường đọc không?
6. Có **nơi hiển thị** không?

Thiếu bất kỳ mục nào ⇒ **chưa xong**. Và nếu là khoá `settings` cho tính năng chưa dựng ⇒ **không seed**.

### 7.4. Trước khi mở PR

```bash
cd backend && ./mvnw verify          # test + ArchUnit + Spotless + Checkstyle + cổng bao phủ
cd frontend && npm run lint && npm test
make ci-local                        # 10 bước, gồm bộ đọc tracking + thứ tự migration
make ci-image                        # "xanh ở máy" không phải bằng chứng
make branch-check
```

- [ ] Mã lỗi mới → sửa **ba tệp** (`ErrorCode.java` · `error-messages.properties` · `error-map.ts`).
- [ ] Quyền mới → seed `permissions` **và** `role_permissions`, gỡ khỏi `QUYEN_PHASE_SAU`.
- [ ] Job CI mới → vào `needs` của **`Cổng kiểm CI`** + nâng ngưỡng `so_job`.
- [ ] Biến env mới → **bốn nơi** (`.env*`, compose, properties, cổng kiểm), so **tập hợp hai chiều**.
- [ ] Thư viện mới → kiểm license + chạy quét CVE thật.
- [ ] ⛔ Có sửa tệp kiểm kiến trúc để mã của mình chạy được không? Nếu có — **dừng lại**.

---

## 8. Rủi ro và mục chờ Công ty

### 8.1. Rủi ro riêng của Phase 2

| # | Rủi ro | Vì sao nặng | Giảm nhẹ |
|---|---|---|---|
| **R1** | ⛔ **Mất dữ liệu do poller chết** | Nguồn **không có API lịch sử** — mọi tham số `date`/`from`/`to` bị bỏ qua. Hệ thống mới là **nơi lưu lịch sử duy nhất**; mất là mất **vĩnh viễn**, không có đường backfill | Giám sát poller **ưu tiên ngang backup CSDL** (T31.9) · `hydro_raw_logs` ghi **nguyên văn trước khi parse** · cửa sổ bảo trì ngắn · giữ profile `worker` để poller chạy độc lập khi app bảo trì |
| **R2** | Nguồn chặn IP / rate-limit phía họ | §10.68-C: lượt deploy đã **tự cấm chính nó**. Nhịp 2'/lần × 720 lượt/ngày là hình dạng dễ bị coi là quét | Rate-limit ở tầng ứng dụng **trước khi mở HTTP** (kỳ vọng 1–3 lượt thật/khung thay vì 5) · backoff · ghi `sync_logs` kể cả khi bị từ chối |
| **R3** | Sai đơn vị cm↔m hoặc sai múi giờ | Sai chỗ này là **sai toàn bộ ngưỡng cảnh báo**, và lộ ra rất xa chỗ gây ra (§10.8 là cùng họ) | Unit test bắt buộc cho adapter, với giá trị thật của 19 mã |
| **R4** | Quên lọc `quality = HOP_LE` ở một truy vấn | *"Bẫy sai số liệu dễ mắc nhất"* — bản ghi nghi ngờ nằm chung bảng chính | Bộ canh parse + tự-kiểm-chứng (T32.4); ngoại lệ duy nhất BC-12 phải khai **có tên** |
| **R5** | Phase 2 là phase **nhiều migration nhất từ trước tới nay** | Hai lớp lỗi mà bộ test **về nguyên tắc không thấy** đều nằm ở migration (§10.65 checksum · §10.66 out-of-order) — cả hai chỉ hiện ra lúc deploy | Bắt buộc `kiem-thu-tu-migration.sh` + `make migration-manifest` cho **từng PR** |
| **R6** | Dữ liệu bịa quay lại trên cổng | §10.54: 19 bài viết, 4 văn bản có số hiệu, **5 trạm thuỷ văn có mực nước** đã lên staging. `HydrologyQuickWidget` là **đúng component Phase 2 dựng lại** | `noFabricatedContent.test.ts` soi toàn cây; ô chưa có nguồn ép ở **hàm dựng**, không ép bằng lời dặn |
| **R7** | NFR-03 không rút ngắn được | 7 ngày là 7 ngày; hỏng giữa chừng là đếm lại từ đầu | Bắt đầu quan sát **ngay khi WS-31 ổn định**, và BC-13 phải xong trước |

### 8.2. Mục chờ Công ty — ⛔ đi xin NGAY, không để tới tuần cuối

| Mục | Chặn cụ thể trong Phase 2 | Mức |
|---|---|---|
| **G5** — mã số hệ thống văn bản riêng hay chung + có xin được token/SSO không | Toàn bộ CN-01.7 | 🟥 **chặn code** (đã loại khỏi phạm vi) |
| **G8** — tuyến sông / lý trình / **toạ độ GPS** của 19 điểm đo + danh mục công trình tổng thể | Toạ độ NULL ⇒ **lớp GIS điểm đo rỗng**; `river_name` NULL ⇒ BC-11 nhóm *"Chưa phân tuyến"*; `station_constructions` không seed đủ ⇒ **resolver người nhận cảnh báo tập 2 rỗng** | 🟩 chặn dữ liệu + nghiệm thu |
| **OI-03** — danh sách **10 cống trục chính** hiện công khai | Widget trang chủ **không biết hiển thị điểm đo nào** (T35.8) | ⛔ chặn nội dung |
| **OI-05** — **7 hay 8 Xí nghiệp** | `stations.org_unit_id` không seed được ⇒ scope dữ liệu + người nhận cảnh báo | ⛔ chặn dữ liệu |
| **G9-a** — bộ mức ngưỡng (3 mức hay báo động cấp I/II/III) | Không chặn code (danh mục CRUD) — chặn **dữ liệu khởi tạo** bảng mức | 🟨 |
| **G3-a** — lượng mưa (chờ endpoint / nhập tay / bỏ hẳn) | Cột lượng mưa của BC-11 và BC-05 hiển thị `-`; widget thiếu một thông số | 🟨 |
| **G10** — duyệt format báo cáo + **file mẫu thật** BC-11/BC-05 | Layout in ấn BC-05/11/12/13 | 🟨 làm khung + trường trước |
| **G13** — khoá reCAPTCHA | T36.6 | 🟨 dựng chỗ cắm, mặc định tắt |
| **G8 mục 3** — 3 cặp mã trùng giá trị: 1 cảm biến 2 mã hay 2 cảm biến? | Gắn 1 hay 2 bộ ngưỡng độc lập | 🟨 T37.4 theo dõi ≥3 ngày rồi hỏi |

⚠ **Đọc bảng này cho đúng**: 🟩 nghĩa là *viết mã được trọn vẹn*, **không** nghĩa là *bàn giao được*.

### 8.3. Nợ ngoài phạm vi nhưng chặn nghiệm thu Phase 2

`T11.2` VPS-1 · `T11.7` secret environment `production` · `T11.35`/`T11.36` chuẩn bị host + `docker login` ·
`DOD0.21` quay lui (chưa lượt deploy nào đi qua đường quay lui thành công) · `T7.13`/`DOD0.14` diễn tập
khôi phục và **đo RTO thật** · `T11.7-a` biến kho `PUBLIC_SITE_URL` (sitemap/canonical staging còn trỏ
`localhost`). ⚠ `T7.13` đặc biệt liên quan: bảng `hydro_*` sẽ làm bản dump **lớn hơn nhiều** — thời gian
restore và RTO phải đo **lại** sau khi có dữ liệu thật.

---

## 9. Ước lượng và thứ tự khởi động

### 9.1. Ước lượng (người·ngày, một người làm tuần tự)

| WS | Nội dung | Ước lượng |
|---|---|:-:|
| WS-27 | Nền + trả nợ SPI cache cổng | 4–5 |
| WS-28 | Lược đồ danh mục + seed 19 điểm đo | 5–6 |
| WS-29 | Lưu trữ time-series + partition + retention | 4–5 |
| WS-30 | Adapter + parser 10 quy tắc | 4–5 |
| WS-31 | Poller + rate-limit + mất tín hiệu + giám sát | 6–7 |
| WS-32 | Validate + dữ liệu nghi ngờ + bộ canh `quality` | 4–5 |
| WS-33 | Alert engine + ngưỡng + resolver người nhận | 7–8 |
| WS-34 | Agg + BC-13/11/05/12 | 8–10 |
| WS-35 | GIS + dashboard + widget cổng | 7–8 |
| WS-36 | MOD-01 phần còn lại | 8–10 |
| WS-37 | Nghiệm thu + NFR (⚠ **T37.1 chiếm 7 ngày lịch**, không phải 7 ngày công) | 5–6 |
| | **Tổng** | **62–75 pd** |

⚠ Đây là **ước lượng khối lượng, không phải cam kết lịch**. Hai chỗ dài hơn nó trông: WS-33 (resolver
người nhận đụng `org_units` chưa seed) và WS-34 (bốn báo cáo + layout chờ G10).

### 9.2. Thứ tự khởi động — tuần 1

1. **Chờ WS-26 merge vào `dev`** rồi mới cắt nhánh (nếu không `kiem-thu-tu-migration.sh` so với nền sai).
2. Chốt **16 điểm ở §3** — 13 mục quyết nội bộ, quyết luôn; 3 mục còn lại gửi Công ty cùng lượt với §8.2.
3. Gửi Công ty **một văn bản duy nhất** xin: G8 (toạ độ/tuyến sông/lý trình 19 điểm + danh mục công trình) · OI-03 (10 cống trục chính) · OI-05 (7 hay 8 XN) · G9-a (bộ mức ngưỡng) · G10 (file mẫu BC-11, BC-05) · G3-a · G13. ⛔ Đi xin **ngay tuần 1** — chúng chặn **dữ liệu và nghiệm thu**, không chặn code, nên rất dễ quên tới lúc không kịp.
4. Làm **WS-27** trọn vẹn trước khi mở bất kỳ WS nào khác.
5. Mở `architecture-review.md` **§11** ngay từ commit đầu — ghi quyết định **lúc quyết**, không ghi lại lúc nghiệm thu.

### 9.3. Nếu có hai người

| Người | Tuần 1–2 | Tuần 3–5 | Tuần 6+ |
|---|---|---|---|
| BE 1 | WS-27 → WS-28 | WS-29 → WS-30 → WS-31 | WS-32 → WS-33 |
| BE 2 / FE | WS-36 (MOD-01, chỉ cần WS-27) | WS-36 tiếp + khung màn hình `hydro` | WS-34 → WS-35 |
| Cả hai | | | WS-37 |

📌 **Điểm hẹn bắt buộc**: kiểu `TelemetryReading(apiCode, measuredAt, valueCm)` phải thống nhất trước
khi WS-29 và WS-30 tách nhánh — đây là chỗ duy nhất hai nhánh gặp nhau, và §10.61 đã cho thấy
*"lỗi nằm ở chỗ tiếp giáp"*.

---

## 10. Ba điều dễ bị bỏ qua nhất khi mở Phase 2

1. **Nợ T25.22 (SPI cache cổng) là điều kiện tiên quyết của widget thủy văn**, không phải việc để dành — §10.62 đã đo được: `hydro` sẽ không có đường **hợp lệ** nào để xoá cache cổng khi có số liệu mới.
2. **Hai ô KPI thủy văn + `optionDuong` + khối *Mực nước* trên cổng là những "chỗ giữ" đã ghi rõ mốc Phase 2.** Chúng phải được **nối nguồn thật hoặc xoá** — giữ nguyên là vi phạm chính luật đã đặt ra khi tạo chúng (§10.33, §10.61 mục 6).
3. **Phase 2 có nhiều migration hơn mọi phase trước cộng lại**, và hai lớp lỗi mà bộ test *về nguyên tắc không thấy* đều nằm ở migration. `kiem-thu-tu-migration.sh` + `make migration-manifest` cho **từng PR**, không phải cho từng đợt.
