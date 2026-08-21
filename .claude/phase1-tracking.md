# PHASE 1 — CMS & MASTER DATA CÔNG TRÌNH · BẢNG THEO DÕI TIẾN ĐỘ

> **Cập nhật lần cuối**: 2026-08-21 · **Tiến độ: 63/112 task (56%)** · 1 task **hoãn có chủ đích** (T12.7 → nợ #62) · **DoD: 0/17** · Trạng thái: 🟡 Đang làm — WS-12 ✅, WS-13 ✅, WS-14 ✅, WS-15 ✅, WS-16 ✅, WS-20 ✅, **WS-17 ✅**
> Nguồn ràng buộc: `function-spec.md` CN-01.1→01.5, CN-01.8 · CN-02.1, 02.2, 02.3, 02.6, 02.7, 02.11 · `conventions.md` (luật) · `docs/coding-guide.md` (đường đi) · `architecture-review.md` §10 (quyết định Phase 1)
> **Cách dùng**: giống Phase 0 — làm xong task nào tick `[x]`; xong 1 WS thì chạy mục "Kiểm chứng" rồi cập nhật bảng tổng + dòng trên cùng.
> ⚠ **Luật 3 bước khi đóng WS** (thừa kế từ Phase 0): tick task → tick dòng nợ → **quay lại sửa mô tả đã lỗi thời ở WS đã giao nợ**. Bước 3 hay bị bỏ nhất.
> ⚠⚠ **Và tick không có nghĩa là chạy được.** Phase 0 kết thúc với 4 cơ chế "xanh mà không chạy" và một lưới an toàn chưa từng sinh ra tệp nào. Mỗi WS ở đây phải có ít nhất một phép kiểm **đi qua thật**, không phải chỉ mock.

---

## Vì sao Phase 1 khác Phase 0 về bản chất

Phase 0 dựng **cơ chế**; Phase 1 là lần đầu có **người dùng thật đi qua cơ chế đó**. Ba thứ đến giờ chưa ai đi qua:

| Cơ chế | Dựng ở | Đã bao giờ chạy thật chưa |
|---|---|---|
| Ranh giới module (ArchUnit) | WS-10 | ❌ Chưa — 4 module nghiệp vụ còn rỗng, luật chạy qua **tập rỗng** |
| Phân quyền tầng 3 (lọc theo đơn vị) | WS-5, WS-10 | ❌ Chỉ trên `ScopedRecord` **dựng riêng cho test**; app thật in *"Chưa có entity nào thuộc phạm vi đơn vị"* |
| `POST /api/revalidate` (ISR) | WS-9 | ✅ **Đã đi qua 20/8 (WS-16)** — đo thật: cổng đổi nội dung sau **114 ms** kể từ lúc gọi. Lộ ra một tính chất không hiển nhiên của Next: tuyến đường có lượt `fetch` hỏng lúc build thì **không mang nhãn nào**, nên `revalidateTag` vĩnh viễn không chạm tới được — chỉ `revalidatePath` chữa được (`architecture-review.md` §10.17) |

Đây không phải ghi chú lịch sử. Nó quyết định thứ tự làm: **WS-12 phải xong trước mọi thứ**, và ba mục trên nằm trong Definition of Done chứ không phải "kiểm sau".

---

## Bảng tổng

| WS | Hạng mục | Task | Xong | Trạng thái | Phụ thuộc | Ước tính |
|---|---|:-:|:-:|---|---|:-:|
| **WS-12** | Mở SPI Core + nền cho module nghiệp vụ | 8 | **7** | ✅ **Xong 19/8** — T12.7 hoãn có chủ đích (nợ #62) | Phase 0 | 6 pd |
| **WS-13** | CMS — Danh mục nội dung & Bài viết | 13 | **13** | ✅ **Xong 20/8** — T13.7/T13.10 đóng cùng WS-16 (nợ #63, #64) | WS-12 | 12 pd |
| **WS-14** | CMS — Thư viện Media | 6 | **6** | ✅ **Xong 19/8** — đóng DoD #11 của Phase 0 | WS-12 | 6 pd |
| **WS-15** | CMS — Cấu hình giao diện, Menu, Banner | 7 | **7** | ✅ **Xong 19/8** — SVG lần đầu đi qua đường thật | WS-13 | 6 pd |
| **WS-16** | Public-web — hiển thị + ISR | 8 | **8** | ✅ **Xong 20/8** — ISR lần đầu có người đi qua | WS-13, WS-15 | 8 pd |
| **WS-17** | Operations — Danh mục công trình | 12 | **12** | ✅ **Xong 21/8** — tầng 3 lần đầu chạy trên entity thật (trả nợ #57) | WS-12 | 14 pd |
| **WS-18** | Operations — Lịch sử sửa chữa & sự cố | 11 | 0 | ⬜ Chưa bắt đầu | WS-17 | 10 pd |
| **WS-19** | Operations — Tình hình vận hành + trạng thái dẫn xuất | 8 | 0 | ⬜ Chưa bắt đầu | WS-17, WS-18 | 7 pd |
| **WS-20** | FE admin — màn hình CMS | 13 | **13** | ✅ **Xong 20/8** — kéo lên trước WS-17 | WS-13→15 (API) | 12 pd |
| **WS-21** | FE admin — màn hình Công trình | 10 | 0 | ⬜ Chưa bắt đầu | WS-17→19 (API) | 12 pd |
| **WS-23** | ⭐ Nền biểu đồ + Dashboard điều hành (CN-02.5, CN-02.6) | 11 | 0 | ⬜ Chưa bắt đầu | WS-17 | 11 pd |
| **WS-22** | Kiểm thử, nghiệm thu Phase 1 & trả nợ | 8 | 0 | ⬜ Chưa bắt đầu | tất cả | 8 pd |
| | **TỔNG** | **112** | **63** | | | **112 pd** |

*(112 task triển khai + 17 mục Definition of Done ở cuối file.)*

**Trạng thái**: ⬜ Chưa bắt đầu · 🟡 Đang làm · ✅ Xong · ⏸ Tạm dừng · ❌ Bỏ

---

## ⭐ Đổi thứ tự thực hiện — chốt 20/8/2026

**Thứ tự mới**: `WS-20` (màn hình CMS) → `WS-17` (danh mục công trình) → `WS-23` (nền biểu đồ + dashboard) → `WS-18`, `WS-19`, `WS-21` → `WS-22`.

| Vì sao | |
|---|---|
| **WS-20 lên trước WS-17→19** | Đóng nợ **#65** (ba bài kiểm CMS chưa đi qua HTTP) và nợ **#66** (`created_by` chưa từng được kiểm chứng) lúc API CMS còn ổn định. Sửa API sau khi đã có màn hình dùng nó thì mỗi lần sửa là hai chỗ. Và Công ty có màn hình để nghiệm thu **G13/G14** — hai mục đang chặn nghiệm thu chứ không chặn code |
| **WS-17 lên trước dashboard** | Dashboard CN-02.5 lấy số liệu từ công trình. Dựng dashboard trước rồi cắm dữ liệu mẫu vào là **demo nói dối**, và toàn bộ phần đấu nối phải viết lại khi WS-17 về. Kéo WS-17 lên thì KPI công trình, biểu đồ thống kê và marker GIS đều là **số thật** ngay từ lần demo đầu |
| **WS-23 là hạng mục mới** | `implement.md` xếp dashboard vào Phase 3. Nay tách thành WS riêng và làm trong Phase 1 để có màn hình demo. Phần thuỷ văn của CN-02.5 **vẫn chờ Phase 2** — chỗ đó hiện khối *"Dữ liệu chưa cập nhật"*, đúng như spec yêu cầu khi mất kết nối, chứ không phải số giả |

⛔ **Cấm seed dữ liệu công trình/thuỷ văn "để cho đẹp demo".** Số liệu giả trong hệ thật là thứ không ai nhớ xoá, và một lần Công ty nhìn thấy con số sai trên màn hình điều hành là mất niềm tin vào mọi con số còn lại. Ô nào chưa có nguồn thì nói thẳng là chưa có.

### Wall mode: base 4K nhưng **phải co giãn**, không phải hai bản layout

Chốt 20/8 (mở rộng so với B8): route `?mode=wall` thiết kế ở base 4K cho TV 85", nhưng **cùng một layout phải chạy được trên laptop 13" và monitor rời 1080p/1440p** — người vận hành thử bố cục ở máy mình trước khi đẩy lên TV, và lúc demo thì màn hình sẵn có là cái laptop.

Hệ quả kỹ thuật, ghi trước để không ai dựng nhầm:
- Cỡ chữ và khoảng cách theo **đơn vị tương đối bám viewport** (`clamp()` + `vw`), không phải hai bộ CSS theo `@media`. Hai bộ thì bộ ít dùng hơn sẽ hỏng mà không ai biết.
- Số lượng ô hiển thị **tự rút gọn theo bề rộng** — không cắt chữ, không thanh cuộn ngang.
- Có bài kiểm ở **ba bề rộng**: 3840, 1920, 1366. Bài kiểm phải khẳng định *cả hai vế*: không tràn ngang, và không mất khối nào.

### Sơ đồ phụ thuộc

```
WS-12 ─────────────────────────────────────► [nền — CHẶN mọi thứ, làm trước]
   ├─► WS-13 ─► WS-15 ─► WS-16 ─► **WS-20**   [nhánh CMS + cổng công khai + màn hình CMS]
   │      └────────────► WS-20               [FE bám API vừa xong]
   ├─► WS-14 ──────────► WS-20
   └─► WS-17 ─► WS-18 ─► WS-19               [nhánh Công trình — tuần tự, không đảo được]
          └───────────────► WS-21
                              └─► WS-22      [cần tất cả]
```

Hai nhánh **CMS** và **Công trình** độc lập nhau hoàn toàn sau WS-12 — chạy song song được, hoặc làm dứt điểm từng nhánh nếu một người.

### Phạm vi Phase 1 — đã chốt 19/8/2026

| Có trong Phase 1 | Không có trong Phase 1 |
|---|---|
| CN-01.1 Bài viết · CN-01.2 Danh mục · CN-01.3 Media · CN-01.5 Cấu hình giao diện/Menu/Banner · CN-01.8 Tìm kiếm (bài viết) | CN-01.4 Liên hệ · CN-01.6 Phản hồi/khảo sát → **Phase 2** |
| **Hiển thị công khai bài viết trên cổng** (bổ sung so với `implement.md` §3 — lý do ở §10.1) | CN-01.7 Liên kết hệ thống văn bản → **chặn bởi G5** |
| CN-02.1 Danh mục công trình · CN-02.2 Lịch sử sửa chữa/sự cố · CN-02.3 Tài liệu · CN-02.6 Thống kê & tìm kiếm · CN-02.7 Nhật ký thay đổi · CN-02.11 Tình hình vận hành | Widget thủy văn ở CN-01.5 → cần MOD-03, **Phase 2** |
| Bản đồ **tối thiểu**: chọn toạ độ công trình | CN-02.4 GIS đầy đủ · CN-02.5 Dashboard/wall · CN-02.10 Báo cáo → **Phase 3** |

### Ba ràng buộc thiết kế giữ xuyên suốt Phase 1

Kế thừa Phase 0 và thêm một điều kiện mới:

1. **`settings` key-value có type** — tham số mới (giới hạn dung lượng, cửa sổ sửa bản ghi, số ngày nhắc cập nhật) đổ vào đây, không migration.
2. **Danh mục hoá thay vì enum cứng** — mã tình hình vận hành là bảng có CRUD (quy tắc 16); loại công trình và loại công việc thì **là enum**, vì chúng gắn với cấu trúc hồ sơ kỹ thuật, không phải thứ Admin thêm bớt.
3. ⭐ **Module nghiệp vụ không được chạm vào `core.application`** — mọi lời gọi đi qua `core.spi`. Đây là ràng buộc *mới có hiệu lực* từ Phase 1, vì trước đó chưa ai đi qua ranh giới.

---

## NGHIỆP VỤ — 18 ĐIỂM ĐÃ LÀM RÕ TRƯỚC KHI CODE

> Đây là các điểm **spec không nói, hoặc nói mà đọc ra hai nghĩa**. Rà ngày 19/8/2026 trên toàn bộ CN thuộc Phase 1.
> Cột "Ai quyết": 🔧 = quyết định kỹ thuật nội bộ (không hỏi khách) · 📣 = quyết định nội bộ **nhưng phải thông báo** Công ty vì đổi thói quen làm việc · ❓ = **phải hỏi Công ty**, đã đưa vào `business-open-questions.md`.

### Nhóm CMS

| # | Điểm chưa rõ trong spec | Chốt | Ai quyết |
|:-:|---|---|:-:|
| 1 | **Sửa bài đã xuất bản có phải duyệt lại không?** Spec có `article_versions` + diff + rollback nhưng không nói | **Copy-on-write**: bản đang xuất bản **giữ nguyên trên cổng**, bản sửa đi vào `CHỜ DUYỆT` như một phiên bản mới. Duyệt xong mới thay thế. Người có `cms:article:publish` sửa thì đăng thẳng | 📣 |
| 2 | **"Yêu cầu chỉnh sửa" là trạng thái riêng hay quay về Nháp?** | **Trạng thái riêng** `YEU_CAU_CHINH_SUA` — giữ được lý do từ chối và lọc được danh sách "bài bị trả về". Quay về Nháp là mất dấu vết | 🔧 |
| 3 | **Quản trị nội dung có được duyệt bài của chính mình?** SRS §3.1.3 chỉ cấm Biên tập viên | **Được** — cấm nốt thì đội nội dung 1–2 người không đăng được bài nào. Audit ghi rõ "tự duyệt" để soi lại khi cần | 📣 |
| 4 | **Slug trùng: cảnh báo hay chặn?** Spec ghi "cảnh báo trùng" | **Chặn cứng** (`CMS-2001`, đã có sẵn trong catalog) — slug là URL công khai, trùng là hai bài tranh nhau một địa chỉ. Đổi slug bài đã xuất bản: v1 **không** làm chuyển hướng 301, vì cổng dựng mới và D4 đã chốt không migrate nội dung cũ | 🔧 |
| 5 | **Hẹn giờ đăng: bài phải duyệt trước chưa?** | Bài **đã duyệt** + `published_at` ở tương lai = "Đã lên lịch". Truy vấn công khai lọc `published_at <= now()`, nên không cần trạng thái thứ bảy. Cron 5' chỉ để **bắn revalidate ISR** đúng lúc tới hạn | 🔧 |
| 6 | **Lượt xem đếm thế nào khi trang được cache tĩnh?** Spec ghi "auto đếm" | Endpoint công khai riêng, gọi từ trình duyệt, có giới hạn tần suất; cộng dồn trong bộ nhớ, job đẩy xuống DB theo lô. **Ghi rõ trong tài liệu bàn giao: đây là số xấp xỉ**, không phải số kiểm toán được | 📣 |
| 7 | **SVG có được tải lên không?** CN-01.3 xếp SVG vào nhóm ảnh 10MB | SVG **chứa được JavaScript** → chỉ nhận ở màn hình cấu hình (logo/favicon, người tải là Admin) và **khử trùng** (`<script>`, `on*`, `<foreignObject>`); ảnh trong bài viết **không nhận SVG**. Spec không lường trước điều này | 🔧 |
| 8 | **Thư viện media lưu ở bảng nào?** `attachments` của Core không có cột thư mục | Tệp media **là `attachments`** với `owner_type='MEDIA_FOLDER'`, `owner_id` trỏ bảng `media_folders` của `content`. Không dựng bảng tệp thứ hai — P3 đã có | 🔧 |
| 9 | **Bài viết có thuộc phạm vi Xí nghiệp không?** | **Không.** `articles` kế thừa `BaseEntity`, **không** `ScopedEntity`: nội dung cổng là của toàn Công ty. Gắn nhầm phạm vi thì Xí nghiệp A không đọc được tin của Xí nghiệp B trên chính cổng công khai | 🔧 |
| 10 | **Danh mục nội dung + menu ban đầu lấy đâu ra?** D4 chốt không migrate, Công ty tự nhập | Cần **sơ đồ danh mục/menu** và nội dung trang tĩnh từ Công ty để bàn giao cổng có ruột. Không có thì Phase 1 nghiệm thu trên cổng rỗng | ❓ **G14** |
| 11 | **Logo, favicon, mã GA/GTM, link mạng xã hội, thông tin footer lấy đâu?** CN-01.5 liệt kê nhưng không ai cấp | Cần bộ nhận diện + tài khoản dịch vụ ngoài từ Công ty. Không chặn code (đọc từ `settings`), chặn **nghiệm thu** | ❓ **G13** |

### Nhóm Công trình

| # | Điểm chưa rõ trong spec | Chốt | Ai quyết |
|:-:|---|---|:-:|
| 12 | **"Cụm công trình" là đơn vị tổ chức hay chỉ là cách nhóm?** `implement.md` nêu bảng `construction_clusters`, còn CN-02.1 lại xếp Cụm vào *cấp quản lý* cạnh Công ty/Xí nghiệp | ✅ **G15 đóng 19/8: chỉ là cách nhóm.** Bảng `construction_clusters` riêng + `constructions.cluster_id` **nullable**. ⛔ Không thêm loại nút vào `org_units`; cụm **không mang ý nghĩa phân quyền** | ✅ đã đóng |
| 13 | **Mã công trình tự sinh hay nhập tay?** | **Nhập tay, có gợi ý tự sinh** `<LOẠI>-<XN>-<số>`. Công ty đã có mã riêng (G8 đang xin file Excel) — ép tự sinh là buộc họ đổi mã đang dùng trên giấy tờ | 🔧 |
| 14 | **Trạng thái công trình là giá trị dẫn xuất — tính lúc đọc hay lưu sẵn?** Spec chỉ nói "tính ở BE" | **Lưu sẵn một cột + tính lại theo sự kiện + job đối soát định kỳ.** Tính lúc đọc thì mỗi lần mở bản đồ là vài trăm truy vấn con. Cột đó **không có API sửa** — sửa thẳng trả `OPS-3001` | 🔧 |
| 15 | **Bản ghi sửa chữa nhập sau khi xong thì bắt đầu ở trạng thái nào?** Spec: "mặc định Đã xử lý với công việc nhập sau khi hoàn thành", nhưng workflow engine chỉ có **một** `initial_state` | Workflow phải nhận **nhiều trạng thái khởi đầu** (T12.5). ⛔ Cấm lách bằng cách tạo ở `MOI` rồi chạy transition giả — lịch sử sẽ ghi một sự việc chưa từng xảy ra | 🔧 |
| 16 | **`alert_event_id` trỏ sang bảng của `hydro` — có đặt FK không?** | **Không FK.** Lưu `alert_event_public_id UUID`, tra qua `hydro.spi`. FK xuyên module trói hai module lại ở tầng CSDL, đúng thứ ranh giới module sinh ra để tránh | 🔧 |
| 17 | **Đơn vị thực hiện: nội bộ hay nhà thầu ngoài?** Spec ghi "Text / FK" | **Hai cột** `performer_org_unit_id` (FK) và `performer_name` (text) + CHECK **đúng một** cột có giá trị. Một cột lưu cả hai kiểu là bảo đảm sẽ có dữ liệu bẩn | 🔧 |
| 18 | **Tiền lưu đơn vị nào?** CN-02.2 ghi chi phí "VND", CN-02.1 ghi tổng vốn "triệu VND" | **Mọi cột tiền lưu VND, `NUMERIC(18,2)`.** Form nào hiển thị triệu thì quy đổi ở FE. Hai đơn vị trong cùng một CSDL là lỗi cộng dồn chờ sẵn (quy tắc 2) | 🔧 |

### Ba mục mới mở cho Công ty — **G15 đã đóng trong ngày**, còn G13/G14

| Mã | Cần gì | Chặn cái gì |
|---|---|---|
| **G13** | Bộ nhận diện cổng: logo, favicon, màu chủ đạo, thông tin footer, link mạng xã hội, GA/GTM, (và reCAPTCHA key cho Phase 2) | **Nghiệm thu** WS-15/WS-16 — không chặn code |
| **G14** | Sơ đồ danh mục nội dung + menu cổng + nội dung trang tĩnh (Giới thiệu, Liên hệ…) | **Nghiệm thu** WS-16 — cổng rỗng thì không có gì để nghiệm thu |
| ~~**G15**~~ | ~~"Cụm công trình" là đơn vị tổ chức hay cách nhóm?~~ | ✅ **Đóng 19/8 ngay trong ngày mở**: chỉ là cách nhóm → bảng riêng + khoá ngoại nullable, không đụng cây tổ chức |

---

## WS-12 — Mở SPI Core + nền cho module nghiệp vụ · 6 pd

**Tiên quyết**: Phase 0 xong. **Đầu ra**: module nghiệp vụ gọi được cả 6 dịch vụ dùng chung của Core mà ArchUnit vẫn xanh; workflow nhận nhiều trạng thái khởi đầu; đính kèm có hạn mức. *(Ảnh phái sinh đã tách khỏi đầu ra của WS-12 — xem T12.7.)*

> ⚠⚠ **Đây là nợ #56 và là việc CHẶN.** `core/spi/` hiện chỉ có `package-info.java`, trong khi cả sáu dịch vụ nằm ở `core.application.*`. Dòng mã Phase 1 đầu tiên gọi `WorkflowEngine` sẽ làm CI đỏ. Quyết định đã ghi ở `architecture-review.md` §9.14: **mở SPI, giữ nguyên luật ArchUnit**.
>
> ⚠ **Và việc này lớn hơn "thêm sáu interface".** Chữ ký hiện tại trả về **entity domain**: `AttachmentService.upload()` → `core.domain.attachment.Attachment`, `JobService.enqueue()` → `core.domain.job.Job`, `OrgUnitService.get()` → `core.domain.org.OrgUnit`. Module nghiệp vụ import những lớp đó là **vi phạm y hệt** như import `core.application`. Nên SPI phải có bộ record riêng, không phải bọc mỏng.

- [x] **T12.1** Sáu interface ở `core/spi/`: `WorkflowPort`, `NotificationPort`, `AttachmentPort`, `JobPort`, `SettingPort`, `OrgUnitPort` — *`architecture-review.md` §9.14* ✅ *19/8*
- [x] **T12.2** Bộ record truyền dữ liệu ở `core.spi`: `AllowedAction`, `AttachmentRef`, `AttachmentUploadCommand`, `JobRef`, `JobRequest`, `NotifyRequest`, `OrgUnitRef` + 2 enum `NotifySeverity`/`NotifyChannel` ✅ *19/8*
- [x] **T12.3** ⚠ Chuyển `WorkflowAware` từ `core.domain.workflow` sang `core.common.persistence` — entity của `content`/`operations` **phải implement** nó. Cùng lý do `BaseEntity`/`ScopedEntity` đã nằm ở `core.common`: đây là hợp đồng hạ tầng, không phải mô hình nghiệp vụ. Luật ArchUnit "`applyState` chỉ được gọi từ `WorkflowEngine`" (nợ #19) vẫn nguyên vẹn ✅ *19/8*
- [x] **T12.4** Service ở `core.application` cài interface tương ứng; bean công khai cho module khác **là interface** ✅ *19/8*
- [x] **T12.5** ⚠ Workflow nhiều trạng thái khởi đầu — điểm nghiệp vụ **15**. ~~Thêm `workflow_initial_states` (hoặc cột `is_initial`) + `WorkflowPort.initialStates(entityType)`~~ → **làm khác kế hoạch**: dùng lại `workflow_transitions` với trạng thái-giả `WorkflowPort.CREATION_STATE = '__NEW__'` ở vế `from_state`; 3 phương thức `initialState` / `initialActions` / `resolveInitialState`; 2 ràng buộc CHECK ở CSDL ✅ *19/8*
- [x] **T12.6** `AttachmentPort`: hạn mức theo chủ sở hữu (CN-02.3 — 500MB/công trình) + `usedBytes(ownerType, ownerId)`; ngưỡng đọc từ `settings`. Kèm **sửa lỗi im lặng `limits.upload.max-mb.*` có từ WS-6** (xem bảng dưới) + mã lỗi `SYS-0010` ✅ *19/8*
- [ ] **T12.7** ⏸ **HOÃN CÓ CHỦ ĐÍCH — chốt 19/8, thành nợ #62.** Ảnh phái sinh (WebP + thumbnail 150/400/800, CN-01.3). Phase 1 dùng **thẳng ảnh gốc**; dựng phái sinh khi có nhu cầu thật. Lý do đầy đủ: `architecture-review.md` **§10.9**
- [x] **T12.8** ⭐ **Bài kiểm chứng minh ranh giới bắt được vi phạm** — `conventions.md` §1.5. `ModuleBoundarySelfCheckTest` + `BoundaryFixtures` (gói `com.songnhue.content.boundaryfixture` trong `src/test`) ✅ *19/8*

**Kết quả phần đã làm (19/8)**: 16 tệp ở `core/spi/` · 6 service cài port · 2 migration · **284 test BE xanh** (tăng 21 so với Phase 0).

**Kiểm chứng — đã chạy**:
- ✅ `./mvnw verify` → **BUILD SUCCESS**, 7/7 module
- ✅ **Kiểm chứng ngược trên mã production, không chỉ fixture**: đặt một lớp thật ở `content.application` nhận `SettingService` → `ModuleBoundaryTest` **đỏ**, chỉ đích danh cả 3 cạnh phụ thuộc (tham số hàm dựng · kiểu trường · lời gọi phương thức). Đổi đúng lớp đó sang `SettingPort` → **xanh**
- ✅ `ModuleBoundarySelfCheckTest` 4 bài: đường qua `core.spi` **được cho qua** · gọi thẳng `core.application` **bị chặn** · **chỉ nhận về** một entity `core.domain` cũng **bị chặn** · fixture nằm ngoài tập lớp production
- ✅ `NotificationEnumParityTest` — và nó **bắt lỗi ngay lượt chạy đầu**, xem bên dưới
- ✅ `WorkflowInitialStateTest` 8 bài trên CSDL thật: đường vào mặc định · đường vào thứ hai khi có quyền · **thiếu quyền → `AUTH-3001` chứ không phải "trạng thái không hợp lệ"** · trạng thái có thật nhưng không phải đường vào → `SYS-0008` · ô chọn lọc theo quyền · **`__NEW__` không lọt vào danh sách nút của bản ghi đang sống** · 2 ràng buộc CHECK ở CSDL
- ✅ `AttachmentQuotaTest` 7 bài — gồm bài chứng minh **đổi trần dung lượng thì lượt tải đổi kết quả theo**, và bài chứng minh ảnh/tài liệu tra hai tham số khác nhau
- ⬜ Chưa chạy: lượt tải đi tới kho **thật** — MinIO ở môi trường kiểm thử là địa chỉ giả (Definition of Done mục 7, cần nợ #20)

**Quyết định phát sinh khi làm** (khác/bổ sung so với kế hoạch):
| Việc | Xử lý |
|---|---|
| Service cài port trực tiếp hay thêm lớp adapter? | **Cài trực tiếp.** Tên phương thức của port khác tên nội bộ ở chỗ kiểu trả về khác (`refsOf` vs `listOf`, `findJob` vs `getOwn`) — vừa tránh đụng độ chữ ký sau xoá kiểu, vừa là dấu hiệu đọc được: tên nào thuộc hợp đồng, tên nào là nội bộ |
| `NotificationRequest` tham chiếu enum của `domain` | Nhân bản thành `NotifySeverity`/`NotifyChannel` ở `core.spi` + ánh xạ bằng `valueOf(name())`. Hợp đồng SPI **không được trói vào mô hình lưu trữ** của core |
| ⚠⚠ **Và bản sao đó trôi lệch ngay lập tức** | Tôi chép thiếu `DANGER` và `WEB_PUSH`. `valueOf(name())` nghĩa là lỗi này **biên dịch trót lọt, test đơn vị vẫn xanh, rồi ném lỗi lúc chạy** đúng lúc có người bấm gửi thông báo. `NotificationEnumParityTest` bắt được ở lượt chạy đầu tiên — bài canh viết ra 10 phút thì thu hồi vốn ngay trong 10 phút |
| `JobService.getOwn` và `findJob` cùng mang một luật bảo mật | Rút luật vào private `findOwn(...)`; hai phương thức công khai chỉ là hai hình dạng trả về. Viết luật hai lần là để hai bản lệch nhau |
| Fixture đặt gói nào? | `com.songnhue.content.boundaryfixture` — luật phân loại module **theo tên gói**, đặt ở `com.songnhue.app..` thì nó bỏ qua sạch và bài tự kiểm thành trang trí |
| Nhiều trạng thái khởi đầu: bảng mới hay dùng lại `workflow_transitions`? | **Dùng lại**, với trạng thái-giả `__NEW__`. Được luôn `required_permission`/`label`/`sort_order`, và chỉ có một cơ chế để đọc hiểu thay vì hai bảng gần giống nhau. Chặn `__NEW__` ở vế `to_state` bằng **CHECK ở CSDL** vì đây là dữ liệu seed bằng migration — sai thì sai lúc triển khai, không phải lúc chạy test |
| ⚠⚠ **Lỗi im lặng có từ WS-6, phát hiện khi seed hạn mức** | `AttachmentService` đọc khoá `limit.upload.max-file-mb` — **khoá này chưa từng được seed**. Mọi lượt tải rơi về **20MB cứng trong mã**, trong khi màn hình cấu hình bày ra ba tham số `limits.upload.max-mb.*` mà **không dòng mã nào đọc**. Triệu chứng: quản trị viên sửa "tối đa mỗi tài liệu = 50MB", tải hồ sơ hoàn công 30MB, **vẫn bị từ chối, không lời giải thích**. Đã nối đúng khoá + tra theo nhóm định dạng (ảnh/tài liệu/GIS) |
| Bài kiểm hạn mức hỏi câu gì | **Không** hỏi "mã có đọc được tham số không" — mã nào chẳng đọc được một con số. Hỏi "**đổi tham số thì hành vi có đổi theo không**". Một bài kiểm kiểu cũ sẽ xanh trọn vẹn suốt thời gian lỗi trên tồn tại |
| Kiểm tra hạn mức là "đọc rồi ghi" | Hai lượt tải song song vẫn có thể cùng lọt và vượt trần một chút. **Chấp nhận có ý thức**: hạn mức là *chính sách vận hành*, không phải bất biến dữ liệu. Vượt vài MB không hỏng gì; khoá nhầm một lượt tải hợp lệ thì có |
| ⚠ Bài kiểm đỏ ngắt quãng | `AttachmentQuotaTest` chạy riêng thì xanh, chạy cả lớp thì đỏ: `@AfterEach` xoá dòng tham số nhưng **quên dọn bộ nhớ đệm Caffeine**, nên bài sau thấy hạn mức của bài trước. Cùng loại bẫy mà `SettingService.invalidate` sinh ra để tránh |

**Nợ giao cho WS sau**: ⬜ T12.9 → WS-17/T17.2 (kiểm chứng tầng 3 trên entity nghiệp vụ thật) · ⬜ ClamAV chạy thật vẫn treo ở nợ #20

---

## WS-13 — CMS: Danh mục nội dung & Bài viết · 12 pd

**Tiên quyết**: WS-12. **Đầu ra**: soạn → gửi duyệt → duyệt → xuất bản chạy hết bằng API, có phiên bản và nhật ký.

- [x] **T13.1** Migration `db/migration/**cms**/`: `categories` (cây 3 cấp, materialized path), `articles`, `article_categories`, `article_versions`, `tags`, `article_tags` — ⚠ tiền tố thư mục là **`cms`**, không phải `content` (`docs/coding-guide.md` §3.1)
- [x] **T13.2** Entity + `@Audited(module="cms")`; `Article implements WorkflowAware`; **kế thừa `BaseEntity`, KHÔNG `ScopedEntity`** — điểm nghiệp vụ **9**
- [x] **T13.3** Seed workflow `ARTICLE` bằng migration: `NHAP · CHO_DUYET · YEU_CAU_CHINH_SUA · XUAT_BAN · GO_BAI · LUU_TRU` + transition kèm `required_permission` và `notify_event`. **Quy tắc tách vai trò**: `SUBMIT` cần `cms:article:submit`, `APPROVE` cần `cms:article:approve` — Biên tập viên không có mã thứ hai nên không tự xuất bản được, ràng buộc nằm ở **dữ liệu**, không ở `if` trong service
- [x] **T13.4** Slug: `SlugUtils` bỏ dấu tiếng Việt, cho sửa tay, duy nhất → trùng trả `CMS-2001` — điểm nghiệp vụ **4**
- [x] **T13.5** `article_versions`: mỗi lần lưu nội dung ghi một bản; API so sánh (diff) + phục hồi bản cũ
- [x] **T13.6** Sửa bài đã xuất bản theo cơ chế **copy-on-write** — điểm nghiệp vụ **1**
- [x] **T13.7** Hẹn giờ đăng: `published_at` tương lai; job 5' quét bài tới hạn → gọi revalidate (đấu nối thật ở WS-16) — điểm nghiệp vụ **5**. ✅ Đóng ở WS-16 (`ScheduledPublishScanner`). ⚠ Quét theo **cửa sổ hai đầu** `(tu, den]`, không phải `published_at <= now()`: vế sau quét lại toàn bộ bài đã đăng từ trước tới nay và bắn revalidate cho tất cả, mỗi 5 phút
- [x] **T13.8** Tìm kiếm quản trị (CN-01.8 phần bài viết): `unaccent` + `pg_trgm`, lọc theo danh mục/trạng thái/tác giả/khoảng thời gian, phân trang 20/50/100, sắp xếp qua `PageUtils` (danh sách cột cho phép)
- [x] **T13.9** Xoá danh mục còn bài viết → chặn, yêu cầu chuyển bài trước (mã lỗi mới)
- [x] **T13.10** Đếm lượt xem theo lô — điểm nghiệp vụ **6**. ✅ Đóng ở WS-16 (`ViewCountService`, gom `LongAdder` → đẩy mỗi phút). ⚠ Bản đầu **chưa từng ghi được gì** vì tự gọi hàm `@Transactional` — xem phần WS-16
- [x] **T13.11** Mã lỗi mới → `ErrorCode` (BE) **và** `frontend/admin-app/src/shared/error-map.ts` — có bài kiểm canh sự đồng bộ, đừng để nó đỏ ở CI
- [x] **T13.12** Test: Biên tập viên gọi `APPROVE` → 403 · workflow đủ nhánh · slug trùng · phiên bản + phục hồi · hẹn giờ
- [x] **T13.13** ⭐ Seed **khung danh mục đề xuất** (Tin tức + 2 danh mục con · Thông báo · Giới thiệu) — G14 ✅ *19/8, làm cùng T15.7 vì menu phải trỏ vào danh mục đã có*

**Kết quả (19/8)**: 11/13 task. Còn **T13.7** (job hẹn giờ đăng) và **T13.10** (đếm lượt xem theo lô) — tham số `settings` của cả hai đã seed, phần job chưa dựng. **298 test BE xanh** (211 core + 87 app), trong đó `ArticleLifecycleTest` **14 bài trên CSDL thật**.

**Cập nhật 20/8**: T13.7 và T13.10 đã đóng cùng WS-16 — cả hai cần cổng công khai thật để đấu nối, nên tách ra làm sau là đúng chứ không phải nợ kỹ thuật. **WS-13 xong 13/13.**

**Kiểm chứng — đã chạy**:
- ✅ **Lần đầu tiên một entity nghiệp vụ đi qua workflow engine.** Suốt Phase 0 engine chỉ chạy trên bản ghi dựng riêng cho test
- ✅ Biên tập viên gọi `APPROVE` → **`AUTH-3001`**, và ràng buộc nằm ở `workflow_transitions.required_permission` chứ không ở câu `if` nào
- ✅ `allowedActions` của biên tập viên **không chứa** `APPROVE`/`REQUEST_CHANGES` — nút không hiện thì không có chuyện bấm vào bị 403
- ✅ **Copy-on-write đi qua thật**: sửa bài đang xuất bản → `status = CHO_DUYET` mà `published_version_id` **giữ nguyên**, `isPubliclyVisible` vẫn `true`; duyệt xong con trỏ mới đổi
- ✅ Hẹn giờ: đã duyệt + `published_at` tương lai → chưa hiện; tới giờ thì hiện, **không cần job**
- ✅ Gỡ bài → đăng lại giữ nguyên `published_version_id` (không duyệt lại, đúng spec)
- ✅ Slug trùng `CMS-2001` · bài không danh mục `CMS-2006` · xoá danh mục còn bài `CMS-2003` · sửa bài chờ duyệt `CMS-2007`
- ✅ Phục hồi bản cũ **ghi thêm** phiên bản thứ 3 chứ không xoá lịch sử
- ✅ **ArchUnit xanh với mã thật** — `content` chỉ chạm `core.spi` và `core.common`
- ⬜ Chưa chạy: gọi qua HTTP thật (bài kiểm gọi thẳng service); `audit_logs` chưa đối chiếu old/new cho `articles`

---

## WS-14 — CMS: Thư viện Media · 6 pd

**Tiên quyết**: WS-12 (T12.6). **Đầu ra**: tải ảnh/tài liệu lên, có thư mục, chèn được vào bài viết.

- [x] **T14.1** Migration `media_folders` — cây **tối đa 3 cấp**, chặn ở tầng service chứ không chỉ ở UI
- [x] **T14.2** Tệp media = `attachments` với `owner_type='MEDIA_FOLDER'` — điểm nghiệp vụ **8**, không bảng tệp thứ hai
- [x] **T14.3** Tải nhiều tệp; giới hạn theo loại đọc từ `settings`: ảnh 10MB · video 500MB · tài liệu 50MB · nén 100MB
- [x] **T14.4** Danh sách Grid/List, lọc theo loại/thư mục/ngày, sao chép URL 1 lần bấm. ⚠ Ảnh hiển thị là **ảnh gốc** (T12.7 hoãn) → lưới ảnh **bắt buộc** `loading="lazy"` + khung CSS cố định, nếu không thì mở một thư mục 200 ảnh là tải về vài trăm MB
- [x] **T14.5** Xoá tệp đang được bài viết tham chiếu → cảnh báo có danh sách bài đang dùng; xoá thư mục **chỉ khi rỗng**
- [x] **T14.6** ⚠ SVG — điểm nghiệp vụ **7**: chỉ nhận ở màn hình cấu hình, khử trùng trước khi lưu; test bằng SVG có `onload` và có `<script>`

**Kết quả (19/8)**: 6/6 task. **319 test BE xanh** (220 core + 99 app) — `MediaLibraryTest` 12 bài + `SvgSanitizerTest` 9 bài.

**Kiểm chứng — đã chạy**:
- ⭐⭐ **MinIO THẬT trong test tích hợp — `SongnhueMinio`.** Từ WS-6 tới hết Phase 0, `app.storage.endpoint` trỏ vào `http://minio.invalid:9000`; `MinioClient` không mở kết nối lúc dựng bean nên context vẫn lên, test vẫn xanh, và **chưa một lượt tải tệp nào đi tới kho**. Nay mọi test tích hợp chạy trên MinIO thật → **đóng DoD #11 của Phase 0**
- ✅ Tải ảnh lên đi tới kho thật, đọc lại được, presigned URL trỏ đúng bucket
- ✅ **Tên lưu xuống kho là chuỗi ngẫu nhiên**: `bao-cao.jpg.exe` (nội dung PNG) → lưu thành `<uuid>.png`, đuôi suy từ MIME đã xác thực chứ không từ tên người dùng đặt
- ✅ Văn bản thuần đổi đuôi `.png` bị `FileValidator` loại bằng magic bytes
- ✅ **Thư viện media từ chối SVG** — đúng điểm nghiệp vụ 7
- ✅ `SvgSanitizerTest` 9 bài: `<script>` (kể cả tràn nhiều dòng) · `onload`/`onerror` · `<foreignObject>` · `href="javascript:"` · `DOCTYPE`/`ENTITY`; mỗi bài khẳng định **cả hai vế** — đoạn nguy hiểm mất và hình vẽ còn. Kèm bài tự kiểm chứng minh bộ dò bắt được thật
- ✅ Xoá thư mục còn tệp `CMS-2008` · còn thư mục con `CMS-2004` · cây quá 3 cấp `CMS-2005`
- ✅ Xoá tệp đang được bài viết dùng `CMS-2009`, **kèm tên bài**; xét cả ảnh bìa lẫn ảnh chèn trong HTML
- ⬜ Chưa chạy: gọi qua HTTP multipart thật (bài kiểm gọi thẳng service) — nợ #65 cùng WS-20

---

## WS-15 — CMS: Cấu hình giao diện, Menu, Banner · 6 pd

**Tiên quyết**: WS-13. **Đầu ra**: cổng công khai lấy được toàn bộ cấu hình hiển thị từ API.

- [x] **T15.1** Migration `banners`, `menu_items` (cây lồng nhau, hai vị trí header/footer độc lập) ✅ *19/8*
- [x] **T15.2** Cấu hình chung website → **nhóm `SITE` trong `settings`**, không bảng mới: tên site, slogan, logo, favicon, màu chủ đạo/phụ, GA Tracking ID, GTM Container ID ✅ *19/8* — ⛔ **Maintenance Mode KHÔNG thêm khoá mới**: `system.maintenance-mode` đã có từ WS-7 và đang được `MaintenanceFilter` đọc thật; hai công tắc cho một bóng đèn thì người vận hành gạt cái đang nhìn, hệ thống nghe cái kia
- [x] **T15.3** Footer: khối thông tin công ty, bản đồ nhúng, mạng xã hội, copyright ✅ *19/8* — "Liên kết nhanh" **chính là menu vị trí FOOTER**, không phải tham số riêng: hai nơi khai cùng một danh sách thì chúng lệch nhau
- [x] **T15.4** Trang đặc biệt: `site.home.blocks` (JSON — thứ tự phần tử là thứ tự khối) + trang 404 tuỳ biến ✅ *19/8*
- [x] **T15.5** ⛔ **Widget thuỷ văn: KHÔNG seed tham số nào** ✅ *19/8* — T15.5 ghi "giữ chỗ cấu hình", nhưng widget cần MOD-03 (Phase 2) nên không dòng mã nào đọc được khoá đó. Bày ra một tham số như vậy là lặp lại đúng lỗi vừa sửa ở WS-12. **Chỗ giữ là một khối bị khoá trên giao diện (WS-20)**, không phải một dòng trong CSDL. Có bài kiểm canh việc này
- [x] **T15.6** Cache Caffeine + **dọn bằng sự kiện `SettingChangedEvent`** ✅ *19/8* — không phải tự dọn: cùng một dòng `settings` sửa được từ **hai** màn hình (`architecture-review.md` §10.13)
- [x] **T15.7** ⭐ Seed **menu header/footer đề xuất** + 4 trang tĩnh (Giới thiệu chung · Chức năng nhiệm vụ · Cơ cấu tổ chức · Liên hệ) — G14 ✅ *19/8*, đặt thẳng ở **Xuất bản** để menu không trỏ vào 404

**Kết quả (19/8)**: 7/7 task. **5 mã lỗi mới** (CMS-2010→2014, tổng **62**, BE=FE). **340 test BE xanh** (220 core + 120 app) + 24 FE, trong đó `SiteLayoutTest` **21 bài trên CSDL + MinIO thật**.

**Kiểm chứng — đã chạy**:
- ✅ **`make dev-docker`, migration chạy thật trong jar**: `flyway_schema_history` có đủ V…1019/1020/1021, `menu_items` ra đúng 6+3 mục Header và 4 mục Footer, 4 trang tĩnh `XUAT_BAN` **và có `published_version_id`**
- ✅ 12 endpoint mới có mặt trong OpenAPI và trả **401** (không phải 404) khi chưa đăng nhập — nghĩa là image đang chạy là bản vừa dựng, không phải bản cũ
- ✅ **Sửa tham số ở màn hình cấu hình HỆ THỐNG → cổng thấy ngay**, không chờ hết TTL. Đây là câu hỏi mà bài kiểm phải hỏi; hỏi "sửa ở màn hình CMS có thấy không" thì một bản cài sai vẫn xanh
- ✅ ⭐⭐ **SVG lần đầu đi qua đường tải lên thật**: logo có `<script>` + `onload` → đọc lại **từ MinIO** thấy phần chạy được đã mất, hình vẽ còn nguyên
- ✅ Đường CMS gọi `security.login.max-failed-attempts` → **SYS-0004**, giá trị không đổi — chốt chặn nằm dưới annotation phân quyền
- ✅ **CSDL từ chối** mục con khác vị trí với cha (chèn thẳng bằng SQL) — không chỉ tầng service chặn
- ✅ Menu quá 3 cấp `CMS-2010` · xoá mục còn con `CMS-2011` · đích đã xoá mềm `CMS-2012` · lệch vị trí `CMS-2013` · lịch banner ngược `CMS-2014`
- ✅ Banner: chưa tới / đang chạy / đã hết → chỉ mục đang trong khung lên cổng; tắt thì rời cổng ngay mà không mất dữ liệu
- ⬜ Chưa chạy: gọi qua HTTP thật với token (bài kiểm gọi thẳng service) — nợ #65 cùng WS-20

---

## WS-16 — Public-web: hiển thị + ISR · 8 pd

**Tiên quyết**: WS-13, WS-15. **Đầu ra**: bài viết duyệt xong hiện trên cổng thật, không phải bấm build lại.

> ⭐ WS này bổ sung so với `implement.md` §3 (vốn xếp phần public vào Phase 2). Lý do ở `architecture-review.md` §10.1: `POST /api/revalidate` đã được dựng ở WS-9 **cho đúng luồng này** và tới giờ chưa ai đi qua.

- [x] **T16.1** Nhóm API công khai `@PublicEndpoint`: danh sách bài, chi tiết theo slug, theo danh mục, menu, banner, cấu hình site. ⛔ **Chỉ trả bài `XUAT_BAN` và `published_at <= now()`** — có bài kiểm cố tình hỏi bài Nháp bằng slug đúng và phải nhận 404
- [x] **T16.2** Giới hạn tần suất riêng cho nhóm công khai + cache; không đụng bucket của API quản trị — `RateLimitPolicy.PUBLIC` 300/phút theo IP. ⚠ Rộng hơn nhóm quản trị là **có chủ ý**: cả Công ty ra Internet qua **một IP NAT**, siết chặt ở đây là cả cơ quan không đọc được cổng của chính mình (cùng lý do đã nâng hạn mức đăng nhập 5 → 30 ở WS-5)
- [x] **T16.3** Trang Next: danh sách, chi tiết, theo danh mục, tìm kiếm; dùng ISR
- [x] **T16.4** SEO: metadata + Open Graph theo từng bài; `sitemap.ts` **đọc từ DB** thay vì danh sách tĩnh; giữ nguyên cơ chế tự chặn lập chỉ mục ở staging/local
- [x] **T16.5** ⭐ `POST /api/revalidate` đấu nối thật vào bước xuất bản và bước hẹn giờ tới hạn; có bí mật chia sẻ, có ghi log lượt gọi. Đi qua **hàng đợi job** (`CMS_PORTAL_REVALIDATE`) chứ không gọi thẳng — cổng khởi động sau backend nên lượt gọi thẳng đầu tiên chắc chắn hỏng, và lượt thử lại đúng là việc hàng đợi sinh ra để làm
- [x] **T16.6** Ảnh trong bài: quyết định đường phục vụ tệp công khai từ MinIO (bucket công khai riêng hay proxy qua BE) — **không** dùng presigned URL cho ảnh trang công khai, vì URL hết hạn thì trang tĩnh đã cache sẽ hỏng ảnh. Chốt: **proxy qua BE** `GET /api/v1/public/files/{publicId}`, chặn theo **danh sách loại chủ sở hữu công khai** ở tầng đính kèm (không ở controller) + bắt buộc `isDownloadable()` — tệp chưa quét virus không ra khỏi hệ thống bằng đường này
- [x] **T16.7** Trang 404/500; bài `GO_BAI` trả 404 nhưng **giữ nguyên dữ liệu**; bài `LUU_TRU` không lên danh sách nhưng vẫn vào được bằng URL trực tiếp (kèm `noindex`)
- [x] **T16.8** Kiểm chứng đầu-cuối: soạn → gửi duyệt → duyệt → xuất bản → cổng hiện bài, **đo thời gian thật** từ lúc bấm tới lúc trang đổi

**Kiểm chứng**: chạy `make dev-docker`, đi trọn luồng trên trình duyệt. Trang chủ đo được **< 3s** (NFR-02).

### Đo thật trên `make dev-docker` (20/8/2026)

| Phép đo | Kết quả |
|---|---|
| Trang chủ | HTTP 200 · **0,265 s** (NFR-02 cho phép 3 s) |
| Chi tiết bài · danh mục · tìm kiếm | HTTP 200 · 0,046 / 0,023 / 0,026 s |
| Slug không tồn tại | HTTP 404 |
| `sitemap.xml` | **10** `<url>` (trang chủ + 5 danh mục + 4 trang tĩnh) |
| T16.8 — sửa nội dung rồi gọi revalidate | cổng đổi nội dung sau **114 ms** |
| Việc hâm nóng cổng | 7/7 `SUCCEEDED` |
| Đếm lượt xem | `POST …/views` → **204**, `view_count` lên đúng **7/7** lượt gọi sau ~15 s |
| `POST` đường quản trị thiếu token CSRF | vẫn **403** |

### Bốn lỗi chỉ lộ ra khi chạy thật

Cả bốn đều **xanh trọn vẹn trong bộ kiểm thử**. Chi tiết ở `architecture-review.md` §10.16–§10.19.

1. ⚠⚠ **Cổng dựng ra trang trắng trong Docker** — `NEXT_PUBLIC_API_BASE_URL` là địa chỉ của *trình duyệt*, còn lượt gọi phía máy chủ nằm trong container. Thêm `API_INTERNAL_BASE_URL` (§10.16).
2. ⚠⚠ **`revalidateTag` không chữa được trang dựng hỏng lúc build** — không có lượt `fetch` thành công thì không có mục cache mang nhãn. Phải `revalidatePath`, và phải hâm nóng sau khi khởi động (§10.17).
3. ⚠ **Việc hâm nóng hỏng 3 lượt** vì `HttpClient` của JDK mặc định HTTP/2 và gửi kèm yêu cầu nâng cấp h2c; máy chủ Node đóng kết nối. `curl` chạy được nên suýt truy sai hướng (§10.18).
4. ⚠⚠ **Bộ đếm lượt xem trả 403** — `CsrfFilter` chặn đường công khai, tức là nó **không bao giờ chạy được** ở production. CSRF bảo vệ phiên, mà khách vãng lai không có phiên nào để mượn (§10.19).

### Lỗi thứ năm, và một luật ArchUnit mới

⚠⚠ **`ViewCountService` chưa từng ghi được một lượt xem nào.** `dayXuongDinhKy()` (bộ hẹn giờ gọi) tự gọi `day()` bằng `this` → không qua proxy → `@Transactional` vô hiệu → `TransactionRequiredException` **mỗi phút một lần**, trong log của bộ hẹn giờ chứ không của request nào. Bài kiểm xanh vì nó gọi thẳng `day()` — **đi một đường khác với đường production đi**.

Đây là **lần thứ hai** (`BackupService`, WS-7), nên nó thành luật: `SilentFailureRuleTest.KHONG_TU_GOI_HAM_TRANSACTIONAL`. Luật chạy lần đầu tìm ra **8 vi phạm trong mã production**, trong đó:

- ⚠⚠ **`NotificationService.notify(NotifyRequest)` — cửa vào SPI của mọi module nghiệp vụ — đang chạy trong giao dịch `readOnly`.** Khối SPI thêm ở WS-12 được chèn vào **giữa** một `@Transactional(readOnly = true)` và hàm nó thuộc về (`inbox()`), nên chú thích rơi nhầm sang `notify`. Kiểm chứng ngược (cắm lại lỗi, chạy bài kiểm mới): PostgreSQL từ chối thẳng — `cannot execute INSERT in a read-only transaction`. Tức là **module nghiệp vụ đầu tiên gọi `NotificationPort.notify(...)` sẽ nhận 500 ngay lần đầu**; cái im lặng nằm ở *thời điểm phát hiện*, vì lỗi đi qua 4 WS và hơn 370 bài kiểm mà không ai chạm tới cửa SPI — `WorkflowEngine` ở trong `core` nên nó gọi thẳng bản kia. Người đi đầu tiên sẽ là **WS-17** (`architecture-review.md` §10.21).
- ⚠ **`CodeGenerator`**: nạp chồng tiện dụng làm mất `REQUIRES_NEW` → bộ đếm mã lùi theo lượt ghi hỏng và bản ghi kế tiếp **mang lại đúng mã đó**, đúng thứ cả lớp sinh ra để chống.
- 3 chỗ còn lại là chú thích ghi một bảo đảm không tồn tại (`BackupService.pruneExpired`, `JobService.findActiveByDedupKey`, `SettingService.getString`) — gỡ, và ghi rõ vì sao cố ý không có.

⭐ **Và bản sửa phải có người đi qua, ngay chứ không phải ở WS-17.** Một bản sửa đúng theo suy luận nhưng không ai chạm tới thì đứng đúng chỗ bản lỗi vừa đứng — đó là bài học lặp lại của cả Phase 0. `NotificationPortTest` tiêm **interface** `NotificationPort` (tiêm lớp cài đặt là gọi đúng hàm mà production *không* gọi) và kiểm chứng ngược đã chạy: cắm lại lỗi cũ → đỏ đúng một bài, đúng bài nhắm vào `notify`. Đây cũng là **bài kiểm tích hợp đầu tiên** của cơ chế thông báo — dựng từ WS-6, tới nay chưa có bài nào.

⚠ Một cái bẫy của chính lượt kiểm chứng đó, ghi lại để khỏi mắc lần nữa: `./mvnw -pl app test` **không** dựng lại `core`, nên lượt chạy đầu đo nhầm jar cũ trong `~/.m2`. Phải có `-am`. CI luôn dựng cả reactor nên không dính, nhưng ở máy dev thì kết quả "đỏ" và "xanh" đều có thể là của mã khác.

---

## WS-17 — Operations: Danh mục công trình · 13 pd

**Tiên quyết**: WS-12. **Đầu ra**: hồ sơ công trình đủ 4 loại, có toạ độ, có tài liệu, có nhật ký thay đổi, và **phân quyền tầng 3 chạy trên entity thật**.

- [x] **T17.1** Migration `db/migration/**ops**/`: `constructions` + `pump_station_specs` + `sluice_specs` + hồ sơ tối thiểu cho đê/kênh. Mọi số đo `NUMERIC`, tiền `NUMERIC(18,2)` VND — điểm nghiệp vụ **18**
- [x] **T17.2** ⭐ `Construction extends ScopedEntity` — **entity nghiệp vụ đầu tiên thuộc phạm vi đơn vị**. Trả nợ T12.9: chạy lại kiểm chứng tầng 3 trên bảng thật, thay cho `ScopedRecord` dựng riêng cho test ở Phase 0. Dòng log *"Chưa có entity nào thuộc phạm vi đơn vị"* phải **biến mất**
- [x] **T17.3** Mã công trình duy nhất toàn hệ thống; gợi ý tự sinh nhưng cho sửa — điểm nghiệp vụ **13**
- [x] **T17.4** Toạ độ `Decimal(9,6)` + cột PostGIS; `river_name`, `chainage` (`K<km>+<m>`); danh sách "Công trình chưa có vị trí GIS"
- [x] **T17.5** Lưu vực / khu tưới tiêu = **trường văn bản** (chốt F3) — ⛔ không bảng danh mục, không polygon riêng
- [x] **T17.6** Trạng thái vận hành là **cột dẫn xuất** (tính ở WS-19); API nhận `status` từ client → `OPS-3001`
- [x] **T17.7** Tài liệu công trình (CN-02.3) qua `AttachmentPort`: hạn mức 500MB/công trình, nhãn loại tài liệu, ngày lập + ngày hết hiệu lực, phiên bản
- [x] **T17.8** Nhật ký thay đổi hồ sơ (CN-02.7) = **API đọc `audit_logs`** lọc theo `entity_type='CONSTRUCTION'` — ⛔ không dựng bảng lịch sử thứ hai; `@Audited` đã ghi đủ old/new
- [x] **T17.9** Nhập từ Excel/CSV: **chạy khô trước** (xem trước + báo lỗi từng dòng + đếm sẽ thêm/sửa bao nhiêu), có lỗi chặn thì **không nhập dòng nào**. Đây cũng là đường seed dữ liệu thật khi G8 về
- [x] **T17.10** Thống kê & tìm kiếm (CN-02.6): đếm theo loại / đơn vị / trạng thái / cấp quản lý; lọc trên danh sách. Biểu đồ để Phase 3
- [x] **T17.11** `construction_clusters` (mã, tên, đơn vị quản lý, thứ tự) + `constructions.cluster_id` **nullable** + CRUD danh mục cụm — điểm nghiệp vụ **12**, G15 đã đóng. ⚠ Cụm **chỉ để nhóm hiển thị và lọc**: cấm dùng `cluster_id` trong bất kỳ truy vấn phân quyền nào, phạm vi vẫn đi bằng `org_unit_id`
- [x] **T17.12** Test: tầng 3 đủ 3 nhánh (đơn vị mình · cấp trên thấy cấp dưới · đơn vị khác → `AUTH-3002` + `security_events`) + mã lỗi mới

**Kiểm chứng**: hai tài khoản thuộc hai Xí nghiệp khác nhau — mỗi người chỉ thấy công trình đơn vị mình; tài khoản cấp Công ty thấy cả hai. **Đây là lần đầu tiên điều đó được chứng minh trên dữ liệu nghiệp vụ thật.**

✅ **Đã chạy (21/8)** — quyết định và bài học ghi ở `architecture-review.md` **§10.32**:

- **Tầng 3 sống thật**: log khởi động đổi từ *"Chưa có entity nào thuộc phạm vi đơn vị"* sang *"Bộ lọc phạm vi đơn vị đã sẵn sàng"* (đo trên `make dev-docker`). `ConstructionScopeTest` phủ 3 nhánh bắt buộc **cộng** nhánh mà `ScopedRecord` không có: **sửa / đổi vòng đời / xoá** hồ sơ ngoài phạm vi cũng bị chặn — bộ lọc và `ScopeGuard` là hai cơ chế khác nhau, quên `ScopeGuard` ở đường ghi là sửa được hồ sơ của Xí nghiệp khác.
- ⭐ **Kiểm chứng ngược**: gỡ `@Filter` khỏi `Construction` → **6/8 bài đỏ** + luật ArchUnit `everyScopedEntityCarriesTheFilter` đỏ, chỉ đích danh lớp thiếu annotation.
- ⭐ **Cửa SPI thông báo lần đầu có người đi qua** — `NotificationPort.notify(...)` được gọi ở lượt bàn giao đơn vị và lượt thanh lý. Đây là cửa mà lỗi giao dịch `readOnly` từng nằm im sau 4 WS (§10.21).
- ⚠⚠ **Lỗi bài kiểm qua HTTP bắt được ngay lượt chạy đầu**: nhật ký thay đổi trả **rỗng** cho hồ sơ vừa tạo. `audit_logs.occurred_at` mặc định `now()` = **thời điểm bắt đầu giao dịch**, còn `createdAt` do Spring gán lúc flush → dòng nhật ký của chính lượt tạo nằm dưới mốc và bị loại. Chữa bằng cách lùi mốc về **đầu tháng** (không quét thêm partition nào).
- ⚠ **Hai cột `valid_from` / `valid_until` của `attachments` đã chết từ WS-2** — có trong lược đồ, entity có setter, không dòng mã nào gọi. CN-02.3 đòi *ngày lập + ngày hết hiệu lực*, nên WS-17 mở `AttachmentPort.setValidity(...)`.
- ⛔ **Nhập tệp không thêm Apache POI** — XLSX đọc bằng `java.util.zip` + StAX của JDK. Giới hạn ghi thẳng trong mã (chỉ sheet 1 · không tính lại công thức · không đổi số sê-ri ngày). ⚠⚠ Bẫy nặng nhất: **dấu chấm** — `21.023456` là toạ độ thập phân còn `1.500.000` là hàng nghìn; "bỏ hết dấu chấm" biến vĩ độ thành một điểm giữa đại dương mà CHECK của CSDL không bắt được nếu sai số nhỏ.
- **3 cột sinh ở CSDL**, không có đường ghi nên không thể lệch: `geom` (PostGIS, đo thật `POINT(105.78 20.98)` SRID 4326) · `chainage_m` (`K18+100` → 18100) · `total_flow_m3s` (3 × 1,5 = 4.500, đo qua HTTP).
- **9 mã lỗi mới** (OPS-2008→2016) → **71 mã**, BE = FE. **451 test BE** (239 core + 212 app) + **128 FE**.

**Nợ giao cho WS sau**: ⬜ chuỗi suy ra trạng thái mới có mắt xích cuối (vòng đời) — 4 mắt xích trên thuộc **WS-18** (sự cố, bảo trì), **WS-19** (mã tình hình vận hành) và **Phase 2** (cảnh báo ngưỡng); tất cả thêm vào **đúng `ConstructionStatusService`**, không mở đường ghi mới.

---

## WS-18 — Operations: Lịch sử sửa chữa & sự cố · 10 pd

**Tiên quyết**: WS-17. **Đầu ra**: chức năng ghi nhận hoạt động **duy nhất** của MOD-02 chạy đủ, gồm cả nhánh sự cố.

> Nhắc lại quy tắc 15: **sự cố không phải entity riêng.** Không bảng `incidents`, không mã `SC-`, không vòng đời 7 trạng thái.

- [ ] **T18.1** Migration `maintenance_logs`: `NUMERIC(18,2)` VND; `performer_org_unit_id` **hoặc** `performer_name` + CHECK đúng một — điểm nghiệp vụ **17**
- [ ] **T18.2** Phạm vi đơn vị: sao chép `org_unit_id` từ công trình lúc tạo. ⚠ Ghi rõ hệ quả: công trình đổi đơn vị phụ trách thì bản ghi cũ **giữ nguyên** đơn vị lúc phát sinh — đó là điều đúng cho hồ sơ lịch sử, nhưng phải nói ra để không bị coi là lỗi
- [ ] **T18.3** Seed workflow `MAINTENANCE_LOG`: `MOI → DANG_XU_LY → DA_XU_LY`, **hai trạng thái khởi đầu** (T12.5) — điểm nghiệp vụ **15**
- [ ] **T18.4** Quy tắc nghiệp vụ, mã lỗi **đã có sẵn trong catalog**: sự cố thiếu mức độ → `OPS-2003` · ngày hoàn thành < ngày bắt đầu → `OPS-2001` · chuyển `DA_XU_LY` khi chưa có ngày hoàn thành → `OPS-2004` · công trình đã thanh lý → `OPS-2002`
- [ ] **T18.5** Mã bản ghi `BT-<năm>-xxxx` qua `code_sequences` của Core
- [ ] **T18.6** Tệp đính kèm: biên bản nghiệm thu, ảnh trước/sau
- [ ] **T18.7** Timeline theo công trình + bộ lọc; **tổng chi phí theo kỳ tính ở BE** (quy tắc 3) — FE không cộng
- [ ] **T18.8** Danh sách "Sự cố chưa xử lý" (phục vụ dashboard Phase 3 và trạng thái dẫn xuất WS-19)
- [ ] **T18.9** Quyền sửa/xoá sau khi lưu theo `function-spec.md` §6 + **cửa sổ tác giả tự sửa** đọc từ `settings`, **mặc định tắt** (đúng ma trận hiện tại, bật được nếu Công ty đổi ý)
- [ ] **T18.10** `alert_event_public_id UUID` **không FK** — điểm nghiệp vụ **16**; nút "Tạo bản ghi khắc phục" từ màn hình cảnh báo là việc của Phase 2, ở đây chỉ chừa cột và đường điền sẵn
- [ ] **T18.11** Test: đủ 3 trạng thái qua engine · chi phí `BigDecimal` không sai số qua Σ · phạm vi đơn vị · đóng bản ghi sự cố cuối cùng → trạng thái công trình đổi (đấu nối ở WS-19)

**Kiểm chứng**: ghi một sự cố → công trình chuyển **Sự cố (đỏ)**; đóng bản ghi → tự trả về trạng thái trước đó. Không có dòng nào `UPDATE` thẳng cột trạng thái.

---

## WS-19 — Operations: Tình hình vận hành + trạng thái dẫn xuất · 7 pd

**Tiên quyết**: WS-17, WS-18. **Đầu ra**: danh mục mã do Công ty tự vận hành; trạng thái công trình tính đúng theo 5 mức ưu tiên.

- [ ] **T19.1** Migration `operation_status_codes` — CRUD đầy đủ: mã, tên, có tham số kèm + đơn vị, **màu hex**, **trạng thái công trình ánh xạ** (để trống = không tác động), thứ tự, hiện/ẩn. **Seed 4 mã** `MT` / `ĐK` / `ĐTTL` / `ĐTHL` — quy tắc 16: cấm hard-code enum
- [ ] **T19.2** `construction_operation_status` **append, không ghi đè**; "tình hình hiện hành" = bản ghi mới nhất theo `effective_at`
- [ ] **T19.3** Quy tắc, mã lỗi **đã có sẵn**: trùng mã → `OPS-2005` · mã có tham số mà không nhập giá trị → `OPS-2006` · xoá mã đã dùng → `OPS-2007` (chỉ được ẩn)
- [ ] **T19.4** ⭐ Dịch vụ tính trạng thái dẫn xuất đúng **thứ tự ưu tiên CN-02.1**: (1) sự cố đang mở → (2) bảo trì đang thực hiện → (3) cảnh báo ngưỡng → (4) ánh xạ từ mã tình hình vận hành → (5) Bình thường. **Lưu sẵn + tính lại theo sự kiện + job đối soát** — điểm nghiệp vụ **14**
- [ ] **T19.5** `HydroAlertPort` ở `hydro/spi/` **trả rỗng ở Phase 1** — mức ưu tiên (3) có chỗ cắm sẵn, Phase 2 chỉ điền phần cài đặt. ⚠ Có bài kiểm cho nhánh "không có cảnh báo nào" để Phase 2 không phá vỡ nó
- [ ] **T19.6** Nhập nhanh hàng loạt: một bảng liệt kê toàn bộ cống/trạm bơm, chọn mã + nhập giá trị + lưu một lần (màn hình trực ban đầu ca)
- [ ] **T19.7** Cảnh báo mềm "quá N ngày chưa cập nhật" — N đọc từ `settings`
- [ ] **T19.8** Test: **đủ 5 nhánh ưu tiên**, kể cả trường hợp hai nguồn cùng đòi đổi trạng thái · append giữ đúng lịch sử · thêm mã mới không cần deploy

**Kiểm chứng**: thêm một mã tình hình vận hành mới qua UI, gán cho một công trình → badge màu mới hiện ra, **không build lại gì**. Đây chính là điều G4 yêu cầu.

---

## WS-20 — FE admin: màn hình CMS · 12 pd

**Tiên quyết**: WS-13→15 có API. **Đầu ra**: đội nội dung làm việc được hoàn toàn trên giao diện.

- [x] **T20.1** Trình soạn thảo — ⛔ **bản tự host, không CDN**: CSP của trang chặn mọi tài nguyên ngoài. ⚠⚠ **Đổi lựa chọn: TipTap (MIT) thay cho CKEditor 5 / TinyMCE.** Tới 8/2026 **cả hai đều đã chuyển sang GPL** (CKEditor từ v44, TinyMCE từ v7) — dùng chúng là admin-app trở thành tác phẩm phái sinh của thư viện GPL và phải phát hành theo GPL khi bàn giao. Đó là quyết định pháp lý của chủ đầu tư, không phải của người viết mã
- [x] **T20.2** Danh sách bài + bộ lọc + thao tác hàng loạt + phân trang phía máy chủ. ⚠ Xoá hàng loạt chạy **tuần tự**: hỏng giữa chừng thì biết chính xác đã xong tới đâu, và không bắn hai chục giao dịch song song vào hệ 200 người dùng
- [x] **T20.3** Biểu mẫu bài viết: SEO đếm ký tự có cảnh báo vượt ngưỡng, ảnh đại diện, hẹn giờ đăng
- [x] **T20.4** ⭐ Nút duyệt render từ `allowedActions` của API — ⛔ **không tự suy từ trạng thái** (`conventions.md` §3)
- [x] **T20.5** So sánh phiên bản (diff) + phục hồi bản cũ. So theo **khối văn bản** chứ không theo từ trên chuỗi HTML — diff trên HTML thô cho ra một biển thay đổi mà người biên tập không đọc được. ⛔ Không kéo thư viện diff: LCS ~40 dòng, một phụ thuộc nữa là một dòng nữa phải theo dõi CVE
- [x] **T20.6** Cây danh mục kéo thả, chặn kéo vào chính nhánh con **trước khi gửi lên** — backend cũng chặn, nhưng để người dùng kéo tới nơi rồi mới báo lỗi là bắt họ làm lại
- [x] **T20.7** Thư viện media: tải nhiều tệp có thanh tiến trình từng tệp, hộp chọn ảnh cho bài viết. ⚠ Xoá tệp thì **hỏi backend xem bài nào đang dùng** trước — không hỏi thì ảnh trong bài đã xuất bản vỡ và người xoá không biết
- [x] **T20.8** Cấu hình giao diện: banner (đổi thứ tự bằng nút, không kéo thả — dòng ảnh cao thì kéo phải vừa cuộn vừa giữ chuột), menu lồng nhau hai cây tách biệt, nhận diện + thông tin cổng
- [x] **T20.9** Không phát sinh mã lỗi mới ở WS-20 — `error-map.ts` giữ nguyên **62 mã**, bài kiểm đồng bộ vẫn xanh
- [x] **T20.10** Test FE cho các hàm thuần: **43 bài mới** (SEO 11 · diff 13 · cây 10 · chuẩn hoá URL video 9)
- [x] **T20.11** ⭐⭐ **Chèn ảnh đúng vị trí — rà soát theo yêu cầu của anh Quân (20/8)**: kéo-thả từ máy (chèn tại **chỗ thả**, lấy bằng `posAtCoords`) · dán ảnh chụp màn hình · ô giữ chỗ lạc quan hiện ngay bằng `blob:` rồi thay `src` thật · chú thích + `alt` + bề ngang ảnh + căn lề trên thanh công cụ theo ngữ cảnh. **Bốn lỗi im lặng tìm được — xem khối bên dưới**
- [x] **T20.12** Bộ từ vựng chuyển sang **`design-tokens/src/editor-schema.ts`** — nó là hợp đồng của **ba** bên (soạn thảo · khử trùng · **hiển thị**), không phải tài sản riêng của admin-app. Cạnh thứ ba chưa từng có phép canh nào
- [x] **T20.13** Gỡ `@tiptap/extension-image` và `@tiptap/extension-text-align` — hai gói khai trong `package.json` mà **chưa dòng mã nào import** (đã tự viết `FigureImage`/`AlignClass` thay). Mỗi phụ thuộc là một dòng nữa phải theo dõi CVE

### ⭐⭐ Bộ từ vựng của trình soạn thảo phải khớp danh sách cho phép của bộ lọc

Đây là phần đáng kể nhất của WS-20, và nó **không nằm trong kế hoạch ban đầu**.

`HtmlSanitizer` chạy lúc **ghi**: thẻ ngoài danh sách bị gỡ, im lặng, bài vẫn lưu thành công. Nên một nút trên thanh công cụ tạo ra thẻ ngoài danh sách cho ra đúng kịch bản: biên tập viên chèn bảng, bấm Lưu, hệ thống báo *"Đã lưu"*, mở lại thì bảng biến mất — không lỗi, không cảnh báo, và người dùng nghĩ mình thao tác sai.

Hai danh sách này nằm ở hai ngôn ngữ và hai thư mục, trình biên dịch không bắt lệch được. Nên có **`EditorVocabularyTest`** (Java đọc bản khai của FE, chạy mẫu HTML qua `HtmlSanitizer` thật, đòi mọi thẻ sống sót). *(20/8: bản khai đã chuyển sang `design-tokens/src/editor-schema.ts` — xem T20.11.)* Cùng cách mà `error-map.test.ts` đang canh danh mục mã lỗi, chỉ ngược chiều.

**Bốn phát hiện, cả bốn đều là lỗi im lặng:**

1. ⚠⚠ **`<s>` bị gỡ** — `Safelist.relaxed()` của jsoup chỉ có `strike`, thẻ đã bị HTML5 loại bỏ; mọi trình soạn thảo hiện đại phát ra `<s>`. Nút "gạch ngang" bấm được, lưu xong định dạng biến mất. Bài kiểm bắt được ở **lượt chạy đầu tiên**.
2. ⚠⚠ **Nhúng video YouTube/Vimeo bị gỡ sạch** — CN-01.1 yêu cầu chức năng này, mà `clean()` gỡ mọi `<iframe>`. Thêm danh sách tên miền video, tách hẳn khỏi danh sách tên miền bản đồ (có bài kiểm chứng minh **hai danh sách không lẫn vào nhau**). Chuẩn hoá sang `youtube-nocookie.com` ngay lúc dán: bản thường đặt cookie theo dõi ngay khi trang tải, kể cả khi người đọc không bấm phát.
3. ⚠ **Căn lề phải đi bằng `class`, không bằng `style`** — `HtmlSanitizer` cấm `style` (đúng: `style` mở đường cho `position:fixed` phủ kín trang, hoặc chữ trắng trên nền trắng để giấu nội dung trong một bài đã duyệt). Bản gốc của `@tiptap/extension-text-align` phát ra `style`, nên phải viết extension riêng.
4. ⚠ **Bài kiểm bản đầu gộp hai nguyên nhân làm một** — "thẻ không có trong kết quả" có thể vì bộ lọc gỡ nó (lỗi thật) **hoặc** vì mẫu HTML chưa từng có nó (lỗi của chính bài kiểm). Lượt đỏ đầu tiên chỉ đường sai và tôi suýt thêm bốn thẻ vào safelist trong khi chúng chưa bao giờ bị gỡ. Nay là **hai phép khẳng định riêng**.

### Hai lỗi khác, do lint và do bài kiểm bắt

- ⚠ **`Array.from` không gộp dấu tổ hợp.** Bộ đếm ký tự SEO bản đầu dùng `Array.from` kèm một dòng tài liệu khẳng định như vậy là đếm được ký tự hiển thị. Không đúng: `Array.from` tách theo *điểm mã*, mà chữ dán từ Word thường ở dạng NFD nên `Đề` đếm ra **4**. Người soạn sẽ cắt bớt một tiêu đề hoàn toàn hợp lệ. Chuyển sang `Intl.Segmenter`.
- ⚠ **`useEffect` đổ dữ liệu vào biểu mẫu** (ESLint `react-hooks/set-state-in-effect`). Lý do sâu hơn tên luật: màn hình vẽ một lượt với ô trống rồi vẽ lại — người dùng thấy nhấp nháy, và nếu kịp gõ vào khoảng giữa thì cú gõ đó bị ghi đè. Tách hai lớp: vỏ ngoài nạp dữ liệu, lớp trong dựng biểu mẫu với `initialValues` + `key`.

### ⭐⭐ Rà soát đường chèn ảnh (T20.11) — bốn lỗi im lặng, lỗi nặng nhất không nằm ở trình soạn thảo

Câu hỏi khởi đầu rất hẹp: *"chèn ảnh vào đúng vị trí có chạy không?"*. Trả lời: **chèn tại con trỏ thì chạy** (`insertContent` giữ đúng vùng chọn từ đầu), nhưng quanh nó là bốn lỗi, và **cả bốn đều tìm ra bằng cách chạy máy chứ không bằng đọc mã**.

1. ⚠⚠ **Căn lề ảnh chưa bao giờ hoạt động.** `AlignClass` khai áp dụng cho `'image'` và `'figure'` — **không tên nào tồn tại**, nút ảnh đăng ký tên `figureImage`. Đo bằng `getSchema`: `figureImage attrs: ['src','alt','caption']`, **không có `align`**. Hỏng hai tầng, cả hai im: TipTap bỏ qua lặng lẽ `addGlobalAttributes` trỏ vào type không có thật; và lệnh trả `NHOM_AP_DUNG.some(...)` mà `.some` **dừng ở phần tử đầu tiên trả `true`** — `'paragraph'` luôn trả `true`, nên nút sáng lên như đã làm xong việc trong khi ảnh đứng yên.
2. ⚠⚠ **Cổng công khai không có CSS nào cho nội dung bài.** Thân bài mang class `prose`, mà **`@tailwindcss/typography` chưa từng được cài** → `prose` là class rỗng. Cộng với preflight của Tailwind xoá hình dạng mặc định: danh sách mất dấu đầu dòng, `h3`/`h4` bằng cỡ chữ đoạn văn, bảng không viền, `figcaption` không phân biệt được với một câu trong bài, `sn-align-*` không định nghĩa ở đâu. **Màn hình xem trước trong admin-app vẫn đúng** (nó dùng CSS của trình soạn thảo) — nên biên tập viên không có cách nào biết. Tài liệu của `AlignClass` đã viết sẵn điều kiện *"với điều kiện cổng công khai có định nghĩa ba class đó"* — điều kiện được ghi ra và không ai thực hiện.
3. ⚠ **Chú thích ảnh không có đường nào tạo ra được.** `caption` khai trong node, `figcaption` trong safelist, có trong mẫu kiểm, `EditorVocabularyTest` xanh — mà `RichTextEditor` truyền cứng `caption: null` và không có ô nhập nào. CN-01.1 yêu cầu *"ảnh inline (căn lề, caption)"*: hai vế, cả hai hỏng.
4. ⚠ **Kéo một tệp ảnh vào bài làm mất bài đang soạn.** Không chặn `drop` thì trình duyệt **điều hướng cả tab sang tệp vừa thả**. Nặng hơn "thiếu tính năng" — nó phá công việc đang dở. Nay `handleDrop` trả `true` **kể cả khi không chèn được gì**.

**⚠⚠ Và bài canh cho lỗi (2) ban đầu XANH trong khi không kiểm được gì.** Bản đầu hỏi `CSS.includes('.sn-align-center')`; kiểm chứng ngược bằng cách xoá hẳn `text-align: center` → **vẫn xanh**, vì chuỗi đó còn nằm trong một quy tắc khác cùng tệp (`figure.sn-align-center`). Bài canh chống lỗi im lặng lại chính là một lỗi im lặng. Nay nó tách tệp thành từng quy tắc và hỏi **thuộc tính có được khai không**; kiểm chứng ngược ở mức thuộc tính bắt đủ ba lượt phá hoại. ⛔ **Luật: canh cấu trúc, đừng canh văn bản** — cùng một chuỗi thường xuất hiện nhiều chỗ với ý nghĩa khác nhau.

**Ba cạnh, ba phép canh** (`architecture-review.md` §10.25):

| Cạnh | Phép canh | Nơi |
|---|---|---|
| soạn thảo → khử trùng | `EditorVocabularyTest` (Java đọc mã TS) | `core` |
| khử trùng → soạn thảo | `editorRoundTrip.test.ts` | admin-app |
| khử trùng → **hiển thị** | `articleContentCss.test.ts` | public-web |

**Về đề xuất lưu `jsonb` thay HTML — đã cân nhắc và giữ HTML** (§10.25). Nỗi lo "parse ra DOM bị lỗi" đã **đo thật** trên jsoup 1.23.1 với 7 mẫu: thẻ inline sát chữ, câu dài có inline ở giữa, và khối mã có thụt lề — **cả ba giữ nguyên**; jsoup chỉ thêm thụt lề *giữa các thẻ khối*, và `editorRoundTrip.test.ts` chứng minh việc đó không làm đổi cây nút. Đổi sang `jsonb` thì mất `HtmlSanitizer`, cổng hết dựng được HTML nếu không mang schema TipTap sang máy chủ, và vỡ ba thứ đang chạy (so sánh phiên bản · tìm kiếm toàn văn · chế độ soạn HTML). ⛔ **Không presign đường tải lên**: bỏ qua `FileValidator` · `ImageSanitizer` (EXIF mang **toạ độ GPS** của công trình) · `SvgSanitizer` · ClamAV · hạn mức.

**Kiểm chứng (`make dev-docker`, 20/8)**: 4 route CMS trả 200 · bó mã `ArticleEditorPage` tách riêng, chỉ tải khi mở trang soạn (493 kB / 157 kB nén) · API CMS chưa đăng nhập trả **401** (không phải 404 — nghĩa là endpoint có thật và tầng xác thực đang chạy). **391 test BE** (239 core + 152 app) + **112 FE** (79 admin + 33 public).

---

## WS-21 — FE admin: màn hình Công trình · 12 pd

**Tiên quyết**: WS-17→19 có API.

- [ ] **T21.1** Danh sách công trình + bộ lọc + khối thống kê (CN-02.6)
- [ ] **T21.2** Biểu mẫu hồ sơ **đổi theo loại công trình** (trạm bơm / cống / kênh / đê) — trường kỹ thuật khác nhau, không nhồi chung một form
- [ ] **T21.3** Chọn toạ độ trên bản đồ (Leaflet + OSM) — **phần bản đồ tối thiểu**; GIS nhiều lớp là Phase 3
- [ ] **T21.4** Tab tài liệu — dùng lại `AttachmentPanel`, thêm hiển thị hạn mức đã dùng
- [ ] **T21.5** Timeline sửa chữa + biểu mẫu ghi nhận + nút chuyển trạng thái xử lý (render từ `allowedActions`)
- [ ] **T21.6** Màn hình nhập nhanh tình hình vận hành dạng bảng — tối ưu cho thao tác bàn phím của trực ban
- [ ] **T21.7** Danh mục mã tình hình vận hành: CRUD + chọn màu + xem trước badge
- [ ] **T21.8** Nhật ký thay đổi hồ sơ (đọc `audit_logs`, hiển thị old/new)
- [ ] **T21.9** Nhập Excel: tải lên → **xem trước kết quả chạy khô** → xác nhận
- [ ] **T21.10** Test FE cho các hàm thuần: chọn biểu mẫu theo loại, kiểm tra định dạng lý trình `K..+..`, quy đổi hiển thị tiền

---

## WS-23 — ⭐ Nền biểu đồ + Dashboard điều hành · 11 pd

**Tiên quyết**: WS-17 (dashboard lấy số liệu từ công trình). **Đầu ra**: một màn hình điều hành **demo được cho Công ty**, mọi con số trên đó là số thật.

> ⭐ Hạng mục **thêm ở Phase 1** (20/8), `implement.md` vốn xếp dashboard vào Phase 3. Lý do ở khối "Đổi thứ tự thực hiện" đầu file. Nguồn ràng buộc: `function-spec.md` **CN-02.5** (dashboard + wall mode) và **CN-02.6** (thống kê công trình).

### Nền dùng chung (cần cho mọi biểu đồ về sau, kể cả thuỷ văn Phase 2)

- [ ] **T23.1** Theme ECharts sinh **từ `design-tokens`** — cùng một nguồn màu với AntD. ⛔ Không khai bảng màu thứ hai trong mã biểu đồ: hai bảng màu thì badge trạng thái trên bảng và cột trên biểu đồ sẽ lệch nhau, và không ai coi đó là lỗi để đi sửa
- [ ] **T23.2** Bộ component biểu đồ dùng chung (`LineChart`, `BarChart`, `PieChart`, `GaugeChart`) — tự co theo khung chứa, có trạng thái **rỗng** và **đang tải** riêng. ⛔ Không dữ liệu thì hiện "Không có dữ liệu", **không vẽ biểu đồ trống** (CN-03.4 nói rõ điều này cho thuỷ văn; áp cho tất cả)
- [ ] **T23.3** Nạp ECharts theo **kiểu chọn lọc** (chỉ import loại biểu đồ dùng tới) — nạp trọn gói làm bundle admin phình gấp nhiều lần vì một màn hình
- [ ] **T23.4** `KpiCard` + `ChartCard` + khung lưới dashboard tự xếp lại theo bề rộng
- [ ] **T23.5** Móc tự làm mới theo chu kỳ đọc từ `settings` (M2.15, mặc định 5') — ⚠ đọc lúc chạy, không chốt lúc dựng component, nếu không thì tham số sửa trên giao diện là công tắc chết (bài học WS-12)

### Dashboard điều hành (CN-02.5)

- [ ] **T23.6** API tổng hợp `GET /api/v1/ops/dashboard` — **tính ở BE**, FE chỉ hiển thị (quy tắc 3). Một lượt gọi trả đủ KPI, không để FE gọi bảy endpoint rồi tự cộng
- [ ] **T23.7** KPI card: tổng công trình đang hoạt động/tổng · số công trình theo trạng thái. ⛔ Ô nào chưa có nguồn (cảnh báo thuỷ văn → Phase 2; sự cố chưa xử lý → WS-18) hiện **"Chưa có dữ liệu" kèm lý do**, không hiện số 0 — số 0 nghĩa là "đã đo và bằng không", khác hẳn "chưa đo"
- [ ] **T23.8** Biểu đồ thống kê công trình (CN-02.6): theo loại · theo đơn vị · theo cấp quản lý; bấm vào một cột thì mở danh sách đã lọc sẵn
- [ ] **T23.9** Bản đồ GIS tổng quan: marker theo toạ độ thật, màu theo trạng thái, popup theo M2.10. Công trình **chưa có toạ độ** đưa vào một danh sách riêng thay vì bỏ im
- [ ] **T23.10** ⭐ **Wall mode `?mode=wall`** — base 4K, dark theme, auto-rotate; **co giãn thật** xuống 1440p/1080p/laptop bằng `clamp()` + `vw`, không phải hai bộ layout. Mất kết nối → "Dữ liệu chưa cập nhật" + thời điểm gần nhất
- [ ] **T23.11** Test: hàm gom số liệu ở BE (đủ nhánh "chưa có nguồn") · bố cục wall ở **ba bề rộng 3840/1920/1366**, khẳng định cả hai vế: không tràn ngang **và** không mất khối

**Kiểm chứng**: mở dashboard trên laptop rồi trên màn hình 4K — cùng một route, cùng một layout, không vỡ. Mọi con số đối chiếu được với danh sách công trình. P95 < 3s (NFR-02).

---

## WS-22 — Kiểm thử, nghiệm thu Phase 1 & trả nợ · 8 pd

**Tiên quyết**: tất cả. **Đầu ra**: Phase 1 đóng được, tài liệu khớp thực tế, nợ đã trả hoặc đã ghi rõ ai nhận.

- [ ] **T22.1** Cập nhật `RbacMatrixTest` nếu Phase 1 thêm mã quyền (đối chiếu trên DB thật với `function-spec.md` §6)
- [ ] **T22.2** ⭐ **Nâng cổng bao phủ tầng domain** — trả nợ #22. Ngưỡng `0.18` của Phase 0 là "mức đo được khi domain gần như rỗng"; nay có quy tắc nghiệp vụ thật thì phải nâng
- [ ] **T22.3** Luật ArchUnit áp cho module nghiệp vụ: entity không ra khỏi `application` · `@Transactional` chỉ ở `application` · controller không gọi repository. **Kèm bài chứng minh từng luật bắt được vi phạm**
- [ ] **T22.4** Test tích hợp đầu-cuối hai luồng: bài viết (soạn→cổng) và sửa chữa (ghi sự cố→trạng thái công trình đổi→đóng)
- [ ] **T22.5** Đo hiệu năng: danh sách công trình có phân trang, trang chủ cổng (**< 3s**, NFR-02)
- [ ] **T22.6** Bổ sung `docs/coding-guide.md` bằng bẫy mới gặp trong Phase 1
- [ ] **T22.7** Rà soát nợ + đồng bộ tài liệu (`function-spec.md`, `implement.md`, `conventions.md`, `CLAUDE.md`)
- [ ] **T22.8** ⭐ **Chạy tay lại mọi thứ đã tick.** Bài học đắt nhất Phase 0: rà soát kiểu này tìm ra 4 lỗi thật, cả 4 đều im lặng, trong đó có cơ chế sao lưu chưa từng sinh ra một tệp nào

---

## DEFINITION OF DONE — PHASE 1

Chạy tuần tự, tất cả phải xanh mới coi là Phase 1 hoàn thành:

- [ ] **1. Ranh giới module chạy thật** — `content` và `operations` có mã nghiệp vụ, gọi Core qua `core.spi`, ArchUnit xanh; cố tình import `core.application` → **CI đỏ**
- [ ] **2. Phân quyền tầng 3 trên entity nghiệp vụ thật** — hai Xí nghiệp không đọc được dữ liệu của nhau trên bảng `constructions`; cấp trên thấy cấp dưới; ngoài phạm vi trả `AUTH-3002` + `security_events`. Dòng log *"Chưa có entity nào thuộc phạm vi đơn vị"* đã biến mất
- [ ] **3. Vòng đời bài viết đầu-cuối** — Biên tập viên soạn → gửi duyệt → Quản trị nội dung duyệt → xuất bản → **hiện trên cổng công khai**, đo được thời gian
- [ ] **4. Biên tập viên không tự xuất bản được** — quy tắc nằm ở dữ liệu workflow, không ở `if` trong service; có test
- [ ] **5. ISR revalidate chạy thật** — cơ chế WS-9 dựng từ 17/8 lần đầu có người đi qua
- [ ] **6. API công khai không lộ bài chưa xuất bản** — hỏi đúng slug của bài Nháp/Chờ duyệt → 404
- [ ] **7. Đính kèm đầu-cuối qua HTTP** — tải multipart thật, sai magic bytes bị loại, hạn mức 500MB/công trình chặn được, SVG độc hại bị khử trùng *(đóng nốt DoD #11 của Phase 0)*
- [ ] **8. Trạng thái công trình không sửa trực tiếp được** — API nhận `status` từ client → `OPS-3001`; giá trị luôn khớp 5 mức ưu tiên
- [ ] **9. Sự cố đổi trạng thái công trình** — ghi sự cố → đỏ; đóng bản ghi cuối → trả về trạng thái trước đó
- [ ] **10. Mọi đổi trạng thái đi qua Workflow engine** — không một `UPDATE status` trực tiếp nào trong mã (có luật ArchUnit canh)
- [ ] **11. Thêm mã tình hình vận hành mới không cần deploy** — thêm qua UI, badge màu mới hiện ngay (yêu cầu G4)
- [ ] **12. Tiền và số đo là `BigDecimal`** — không `float/double` nào lọt qua ArchUnit; tổng chi phí tính ở BE
- [ ] **13. Nhật ký kiểm toán đủ old/new** cho entity mới; hash chain vẫn verify pass sau khi Phase 1 ghi hàng nghìn bản ghi
- [ ] **14. Nhập Excel chạy khô đúng** — file có lỗi → **không dòng nào được nhập**, báo lỗi theo từng dòng
- [ ] **15. Mã lỗi BE = FE** — bài kiểm đồng bộ xanh
- [ ] **16. Cổng bao phủ tầng domain đã nâng** khỏi mức `0.18` (nợ #22)
- [ ] **17. Trang chủ cổng < 3s** (NFR-02) đo trên môi trường gần thật

---

## SỔ NỢ PHASE 1

> Cùng luật 3 bước như Phase 0. Đánh số tiếp từ Phase 0 để không trùng khi tra cứu chéo.

### Nợ thừa kế từ Phase 0

| # | Nợ | Phát sinh ở | Task nhận | Trạng thái |
|:-:|---|---|---|:-:|
| 22 | Nâng ngưỡng bao phủ tầng domain (nay `0.18`) | WS-10/T10.5 | **WS-22/T22.2** | ⬜ Chờ |
| 35 | Dựng luồng quên mật khẩu + vòng đời mật khẩu M5.15-a (đã chốt cách làm 18/8, **chưa có mã**) | WS-8/T8.6 | **Phase 2** — chốt 19/8, ~4 pd | ⬜ Chờ |
| 56 | `core/spi/` rỗng — chặn dòng mã Phase 1 đầu tiên | Rà soát 19/8 | **WS-12/T12.1→T12.4** | ✅ **Trả 19/8** — 6 port + 7 record + 2 enum; kiểm chứng ngược trên mã production |
| ~~11 (DoD P0)~~ | ~~Đính kèm chưa kiểm chứng đầu-cuối qua HTTP~~ | WS-6 | WS-14 | ✅ **Trả 19/8** — `SongnhueMinio` đưa MinIO thật vào mọi test tích hợp; tệp đi tới kho, đọc lại được. *(Phần qua HTTP multipart còn ở nợ #65)* |
| 20 | Dựng ClamAV trong compose để quét virus chạy thật | WS-6/T6.4 | WS-11/T11.3 *(vẫn ở Phase 0)* | ⬜ Chờ |

✅ **Nợ #35 đã có chỗ đứng — chốt 19/8: Phase 2, ~4 pd.** Hai lý do cho phép hoãn mà không gây hại:
- **Hạn 90 ngày chỉ cắn sau go-live 90 ngày** — không nằm trong bất kỳ kịch bản nghiệm thu Phase 1 nào.
- **Quên mật khẩu đã có đường đi từ ngày đầu**: quản trị viên cấp mật khẩu tạm (M5.15-a). Luồng tự phục vụ là tiện lợi, không phải điều kiện vận hành.

⛔ **Không được suy ra rằng chính sách mật khẩu đang bị tắt.** BCrypt cost ≥ 12, độ dài tối thiểu, bắt đổi lần đầu, khoá sau 5 lần sai — tất cả đã chạy từ WS-5. Phần hoãn **chỉ là**: hết hạn 90 ngày, nhắc trước 14/7/1 ngày, và luồng tự phục vụ gửi liên kết một lần.

### Nợ phát sinh trong Phase 1

| # | Nợ | Phát sinh ở | Task nhận | Trạng thái |
|:-:|---|---|---|:-:|
| ~~57~~ | ~~Kiểm chứng tầng 3 trên entity nghiệp vụ thật~~ | WS-12 | WS-17/T17.2 | ✅ **Đóng 21/8** — `ConstructionScopeTest` 8 bài trên bảng `constructions` thật; log khởi động đổi sang *"Bộ lọc phạm vi đơn vị đã sẵn sàng"*. Kiểm chứng ngược: gỡ `@Filter` → 6/8 bài đỏ + luật ArchUnit đỏ |
| 58 | `HydroAlertPort` mới có phần khai, chưa có phần cài | WS-19/T19.5 | Phase 2 (`hydro`) | ⬜ Chờ |
| 59 | Nút "Tạo bản ghi khắc phục" từ màn hình cảnh báo | WS-18/T18.10 | Phase 2 | ⬜ Chờ |
| 60 | Widget thuỷ văn ở cấu hình giao diện. ⚠ **Chốt 19/8: KHÔNG seed tham số nào bây giờ** — công tắc chưa ai đọc là lỗi vừa sửa ở WS-12. Phase 2 dựng cả tham số lẫn phần đọc **cùng lúc** | WS-15/T15.5 | Phase 2 | ⬜ Chờ |
| ~~61~~ | ~~`construction_clusters` — chờ **G15**~~ | WS-17/T17.11 | — | ✅ **Đóng 19/8** — G15 trả lời trong ngày, việc dựng bảng nay nằm thẳng trong T17.11 |
| **62** | **Ảnh phái sinh (WebP + thumbnail 150/400/800)** — CN-01.3 yêu cầu, Phase 1 dùng ảnh gốc | WS-12/T12.7 | **Phase 2** *(hoặc sớm hơn nếu trúng điều kiện kích hoạt bên dưới)* | ⏸ **Hoãn có chủ đích 19/8** |
| **63** | Job hẹn giờ đăng bắn revalidate ISR (T13.7) — tham số `settings` đã seed, job chưa dựng. ⚠ **Không chặn nghiệp vụ**: bài tới hạn vẫn tự hiện vì truy vấn công khai lọc `published_at <= now()`; job chỉ để cổng tĩnh cập nhật đúng lúc | WS-13/T13.7 | **WS-16** (cùng chỗ đấu nối ISR thật) | ✅ **Trả 20/8** — `ScheduledPublishScanner`, quét cửa sổ hai đầu |
| **64** | Đếm lượt xem theo lô (T13.10) — `ArticleRepository.addViews` đã có, thiếu endpoint công khai + job đẩy. Cần cùng lúc với trang chi tiết bài ở cổng | WS-13/T13.10 | **WS-16** | ✅ **Trả 20/8** — và lộ ra 2 lỗi: CSRF chặn đường công khai, tự gọi hàm `@Transactional` |
| **65** | `ArticleLifecycleTest` / `MediaLibraryTest` / `SiteLayoutTest` gọi thẳng service, **chưa đi qua HTTP** — chưa kiểm envelope, `@RequirePermission` tầng 2, và ràng buộc "phải nêu lý do khi trả bài" nằm ở controller | WS-13/T13.12 | **WS-20** (cùng lúc dựng màn hình) | 🟡 **Trả phần bài viết 21/8** — `ArticleHttpTest` (5 bài) + bộ trợ giúp `PhienHttp` dùng chung. ⚠⚠ **Dòng nợ này đang che một sự cố toàn phần**: `GET /cms/articles` và `/cms/articles/{id}` trả **500 cho mọi lượt gọi** từ WS-13 tới nay, mà 391 bài kiểm vẫn xanh vì chúng khẳng định *bên trong* giao dịch. Còn `MediaLibraryTest` / `SiteLayoutTest` |

| ~~66~~ | ~~⚠ **`AuditorAwareImpl` đọc `AuditContext` (do filter đặt), còn test tích hợp chỉ đặt `AuthContext`** → cột `created_by`/`updated_by` chưa từng được kiểm chứng~~ | WS-15 (lộ ra khi dựng `CmsFixtures`) | — | ✅ **Đóng 21/8** — không cần đặt `AuditContext` bằng tay: đi **qua HTTP** thì chính filter đặt nó, đúng như production. `ArticleHttpTest.createdByDuocDienKhiDiQuaHttp` đối chiếu `articles.created_by` với id người vừa gọi API |
| ~~67~~ | ~~Cửa vào SPI thông báo vừa được gỡ khỏi giao dịch `readOnly` nhưng chưa có ai đi qua~~ | WS-16 (luật §10.20 lôi ra) | — | ✅ **Đóng ngay trong WS-16** — `NotificationPortTest` tiêm **interface** `NotificationPort` (tiêm lớp cài đặt là gọi đúng hàm production *không* gọi). ⭐ Cơ chế thông báo dựng từ WS-6 tới giờ **chưa có một bài kiểm tích hợp nào**; đây là bài đầu tiên |
| **69** | **Cổng công khai chưa có CSP.** Next chèn script nội tuyến để hydrate (`self.__next_f`), nên CSP ở đó cần nonce qua middleware. ⛔ Cố ý **không** vá bằng `'unsafe-inline'` — như thế là có CSP mà không có tác dụng. admin-app đã có CSP đầy đủ từ 21/8 | Rà soát 21/8 (§10.31) | **WS-11** (cùng nginx chung + TLS) | ⬜ Chờ |
| **70** | **Đếm lượt xem không khử trùng lặp.** `ViewCountService` cộng thẳng mỗi lượt `POST …/views`; tham số `cms.article.view-count-window-minutes` mô tả một tính năng không tồn tại nên đã gỡ khỏi `settings` (21/8). Làm thật thì cần một cách nhận diện người xem (cookie hoặc băm IP) — là quyết định tính năng, không phải việc dọn dẹp | Rà soát 21/8 (§10.31) | **Phase 2** | ⬜ Chờ |
| **68** | `HtmlSanitizer` chạy lúc **ghi**, nên bài viết seed và bài tạo trước WS-16 chưa đi qua nó. Không phải lỗ hổng (nội dung đó do ta soạn), nhưng dữ liệu thật của Công ty nhập trước khi có bộ lọc thì không được rà lại | WS-16 | **WS-22** — lệnh rà một lượt toàn bộ `article_versions`, in ra bài nào bị đổi trước khi ghi | ⬜ Chờ |

⚠ **Nợ #62 — hoãn thì phải nói rõ hoãn cái gì.** CN-01.3 ghi *"auto nén ảnh sang WebP (giữ bản gốc fallback); auto thumbnail 150/400/800px"*, nên đây là **mục nghiệm thu bị hoãn**, không phải việc tự nghĩ ra rồi tự bỏ. Lý do và đường quay lại: `architecture-review.md` §10.9.

**Ba điều kiện kích hoạt — trúng cái nào thì làm ngay, không chờ Phase 2:**
1. Một thư mục media hoặc một hồ sơ công trình vượt **~30 ảnh máy điện thoại** → lưới ảnh nặng vài trăm MB, `lazy` không cứu nổi khi người dùng cuộn hết.
2. Công ty nêu đích danh trong biên bản nghiệm thu.
3. Đo thật thấy trang chủ cổng công khai **quá 3 giây** (NFR-12) vì ảnh.

**Cái phải giữ trong lúc hoãn** — nếu không thì lúc quay lại là sửa rộng chứ không phải sửa một chỗ:
- Không nơi nào ghi cứng "URL ảnh = ảnh gốc" ở FE. Ảnh lấy qua **một hàm dựng URL duy nhất**, sau này chỉ hàm đó biết có phái sinh hay không.
- Không thêm cột nào vào `attachments` cho việc này. Đã kiểm: ảnh phái sinh về sau là **đối tượng MinIO nằm cạnh**, khoá suy ra từ `storage_key`, hoặc dòng `attachments` riêng với `purpose` khác — **cả hai đường đều không cần migration đổi bảng**. Đây chính là thứ làm cho việc hoãn rẻ.

---

## PHỤ THUỘC BÊN NGOÀI — CẦN CÔNG TY

| Mã | Cần gì | Chặn | Hạn nên có |
|---|---|---|---|
| **G13** | Bộ nhận diện cổng + tài khoản dịch vụ ngoài | Nghiệm thu WS-15/16 | Trước khi đóng WS-16 |
| **G14** | Sơ đồ danh mục/menu + nội dung trang tĩnh | Nghiệm thu WS-16 | Trước khi đóng WS-16 — ⭐ **chốt 19/8: seed khung đề xuất trước** (T13.13, T15.7), Công ty sửa qua giao diện |
| ~~G15~~ | ~~"Cụm công trình" là gì~~ | — | ✅ **Đã đóng 19/8**: chỉ là cách nhóm |
| **G8** | Danh mục công trình (Excel) + toạ độ | **Dữ liệu khởi tạo**, không chặn code — T17.9 đã dựng sẵn đường nhập | Trước nghiệm thu Phase 1 |
| **G5** | Mã số hệ thống văn bản riêng hay chung | **CN-01.7 — đã tách khỏi Phase 1** | Trước Phase 2 |

---

## Nhật ký thay đổi kế hoạch

| Ngày | Thay đổi |
|---|---|
| 2026-08-20 | **WS-16 xong — cổng công khai chạy thật, và WS-13 đóng nốt (nợ #63, #64).** 7 lớp `content` + 8 trang/thư viện Next · `HtmlSanitizer` (jsoup, danh sách cho phép) · `RateLimitPolicy.PUBLIC` · phục vụ tệp công khai qua BE. Đo trên `make dev-docker`: trang chủ **0,265 s**, revalidate → cổng đổi nội dung sau **114 ms**, sitemap **10** url. **384 test BE** (232 core + 152 app) + **47 FE** |
| 2026-08-20 | ⚠⚠ **Bốn lỗi chỉ lộ khi chạy thật, cả bốn xanh trong bộ kiểm thử** (`architecture-review.md` §10.16–§10.19): cổng dựng ra **trang trắng** trong Docker vì `NEXT_PUBLIC_*` là địa chỉ của *trình duyệt* · **`revalidateTag` không chữa được** tuyến đường có lượt `fetch` hỏng lúc build (không có mục cache mang nhãn) → phải `revalidatePath` + hâm nóng sau khởi động · `HttpClient` của JDK mặc định **HTTP/2** nên Next đóng kết nối, mà `curl` lại chạy được nên suýt truy sai hướng · **`CsrfFilter` chặn đường công khai** → bộ đếm lượt xem không bao giờ chạy được |
| 2026-08-20 | ⚠⚠ **Luật ArchUnit thứ ba: cấm tự gọi hàm `@Transactional`** — sau khi lỗi này sập **lần thứ hai** (`BackupService` WS-7, `ViewCountService` WS-16: ném `TransactionRequiredException` mỗi phút, **chưa từng ghi được một lượt xem nào**, trong khi bài kiểm xanh vì nó gọi một *đường khác*). Luật chạy lần đầu tìm ra **8 vi phạm trong mã production**: 1 lỗi thật (§10.21), 1 lỗi tiềm ẩn (`CodeGenerator` mất `REQUIRES_NEW` → **mã nghiệp vụ trùng** sau một lượt ghi hỏng), 6 chú thích ghi bảo đảm không tồn tại |
| 2026-08-20 | ⚠⚠ **Chú thích Java bám vào khai báo kế tiếp, không bám vào đoạn chú giải** (§10.21). Khối SPI thêm ở WS-12 chèn vào **giữa** `@Transactional(readOnly = true)` và hàm nó thuộc về → chú thích rơi sang `notify(NotifyRequest)`, **cửa vào SPI của mọi module nghiệp vụ**. Kiểm chứng ngược: PostgreSQL từ chối thẳng `cannot execute INSERT in a read-only transaction`. Sống được 4 WS và hơn 370 bài kiểm vì **chưa ai đi qua cửa đó**. Đóng ngay bằng `NotificationPortTest` — bài kiểm tích hợp **đầu tiên** của cơ chế thông báo dựng từ WS-6 |
| 2026-08-19 | ⚠ **Sửa một sai số của chính bảng này**: header ghi 101 task trong khi chỉ liệt kê **100** — T13.13 được chốt ngày lập kế hoạch nhưng không ai viết nó vào danh sách WS-13. Nay có mặt, tổng khớp lại **101** |
| 2026-08-19 | **WS-15 xong** — cấu hình giao diện, menu, banner. ⭐⭐ **SVG lần đầu đi qua đường tải lên thật**: `FileValidator.detect()` trả `null` cho mọi SVG (không có magic bytes) nên `SvgSanitizer` dựng ở WS-14 **chưa bao giờ được gọi** — nay có phép đoán SVG + nhánh khử trùng đặt ở tầng đính kèm. Cấu hình để ở `settings` nhóm `SITE` (không bảng riêng) + `SettingAdminPort` **ghi theo nhóm** · bộ nhớ đệm dọn bằng **sự kiện** để phủ cả hai màn hình sửa · khoá ngoại ghép `(parent_id, position)` bắt cây menu đúng ở CSDL · seed khung danh mục/menu/4 trang tĩnh (G14). 5 mã lỗi mới (**62 mã**). **340 test BE** + 24 FE |
| 2026-08-19 | ⚠ **Hai lỗi của chính bộ kiểm thử, lộ ra khi có dữ liệu seed**: (1) `DELETE FROM categories` của các bài kiểm cũ vi phạm khoá ngoại từ `menu_items`; (2) mốc "dòng do bài kiểm tạo" đặt theo `created_by IS NOT NULL` **không chạy** vì `AuditorAwareImpl` đọc `AuditContext` chứ không đọc `AuthContext` — test chỉ đặt cái thứ hai, nên mọi dòng đều `created_by = NULL` và phép dọn không xoá gì. Gom về `CmsFixtures`, phân biệt bằng **mốc id**. Lỗi (2) mở nợ **#66** |
| 2026-08-19 | **WS-14 xong** — thư viện media. ⭐⭐ **MinIO thật trong test tích hợp** (`SongnhueMinio`): từ WS-6 tới hết Phase 0 kho lưu trữ là địa chỉ giả `minio.invalid`, nên **chưa một lượt tải tệp nào đi tới nơi** dù test vẫn xanh → **đóng DoD #11**. Thêm `SvgSanitizer` (9 bài kiểm) · chữ ký MP4/WebM · nhóm dung lượng `video` 500MB · 2 mã lỗi (57 mã). **319 test BE** |
| 2026-08-19 | **WS-13 làm 10/12** — CMS danh mục & bài viết. 4 bảng + seed quy trình `ARTICLE` 10 bước chuyển · entity/service/controller đủ 3 tầng · **copy-on-write chạy thật** · 5 mã lỗi mới (BE=FE, 55 mã). **298 test BE**, `ArticleLifecycleTest` 14 bài trên CSDL thật. Còn T13.7/T13.10 → nợ #63, #64 |
| 2026-08-19 | ⚠⚠ **Vá lỗ Core lộ ra bởi người dùng đầu tiên**: `WorkflowEngine` chỉ biết luật G11 nên thông báo "có bài chờ duyệt" gửi cho **Ban điều hành** thay vì quản trị nội dung. Thêm `notify_permission` + `notify_owner` vào `workflow_transitions` — `architecture-review.md` §10.10 |
| 2026-08-19 | **WS-12 đóng.** T12.7 (ảnh phái sinh) **hoãn có chủ đích** → nợ #62, lý do ở `architecture-review.md` §10.9: đẩy sang `next/image` không dùng được ở đây (đòi `sharp` native · presigned URL 10' phá đệm của bộ tối ưu và làm vỡ ảnh trên trang ISR · `admin-app` là Vite nên nửa số ảnh không đi qua Next). Phần đắt duy nhất là bộ mã hoá **WebP**; thumbnail thì rẻ vì `ImageSanitizer` đã chạy `ImageIO` sẵn — nhưng chưa biết tải trọng ảnh thật thì làm là đoán mò. **⛔ Cố ý không seed tham số `settings`**: công tắc chưa ai đọc chính là lỗi vừa sửa ở T12.6 |
| 2026-08-19 | **WS-12 làm 5/8** — mở `core/spi` (trả nợ #56), chuyển `WorkflowAware` sang `core.common.persistence`, 6 service cài port, bài tự kiểm ranh giới + bài canh hai enum. 269 test BE xanh. Còn T12.5 (nhiều trạng thái khởi đầu), T12.6 (hạn mức đính kèm), T12.7 (ảnh phái sinh) |
| 2026-08-19 | **Chốt 4 mục nghiệp vụ**: sửa bài đã xuất bản = **copy-on-write** · Quản trị nội dung **được** tự duyệt (audit ghi rõ) · **G15 đóng** — cụm chỉ là cách nhóm → bảng riêng, T17.11 đổi từ "không dựng" sang "dựng", nợ #61 đóng · **G14** — seed khung danh mục/menu đề xuất (T13.13, T15.7). Tổng task 99 → **101** |
| 2026-08-19 | Lập kế hoạch Phase 1: 11 hạng mục WS-12→WS-22, 99 task, ~100 pd. Ba quyết định phạm vi: **gộp public-web vào Phase 1** (vì `POST /api/revalidate` của WS-9 chưa ai đi qua) · **giữ Liên hệ/Phản hồi ở Phase 2** · **dựng đường nhập Excel có chạy khô**. Làm rõ **18 điểm nghiệp vụ**; mở **3 mục mới cần Công ty** (G13, G14, G15) |
