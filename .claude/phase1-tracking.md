# PHASE 1 — CMS & MASTER DATA CÔNG TRÌNH · BẢNG THEO DÕI TIẾN ĐỘ

> **Cập nhật lần cuối**: 2026-08-19 · **Tiến độ: 5/99 task (5%)** · **DoD: 0/17** · Trạng thái: 🟡 Đang làm (WS-12 5/8)
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
| `POST /api/revalidate` (ISR) | WS-9 | ❌ Chưa — viết sẵn "cho luồng duyệt bài Phase 1" |

Đây không phải ghi chú lịch sử. Nó quyết định thứ tự làm: **WS-12 phải xong trước mọi thứ**, và ba mục trên nằm trong Definition of Done chứ không phải "kiểm sau".

---

## Bảng tổng

| WS | Hạng mục | Task | Xong | Trạng thái | Phụ thuộc | Ước tính |
|---|---|:-:|:-:|---|---|:-:|
| **WS-12** | Mở SPI Core + nền cho module nghiệp vụ | 8 | **5** | 🟡 Đang làm (19/8) — còn T12.5–T12.7 | Phase 0 | 6 pd |
| **WS-13** | CMS — Danh mục nội dung & Bài viết | 12 | 0 | ⬜ Chưa bắt đầu | WS-12 | 12 pd |
| **WS-14** | CMS — Thư viện Media | 6 | 0 | ⬜ Chưa bắt đầu | WS-12 | 6 pd |
| **WS-15** | CMS — Cấu hình giao diện, Menu, Banner | 6 | 0 | ⬜ Chưa bắt đầu | WS-13 | 6 pd |
| **WS-16** | Public-web — hiển thị + ISR | 8 | 0 | ⬜ Chưa bắt đầu | WS-13, WS-15 | 8 pd |
| **WS-17** | Operations — Danh mục công trình | 12 | 0 | ⬜ Chưa bắt đầu | WS-12 | 13 pd |
| **WS-18** | Operations — Lịch sử sửa chữa & sự cố | 11 | 0 | ⬜ Chưa bắt đầu | WS-17 | 10 pd |
| **WS-19** | Operations — Tình hình vận hành + trạng thái dẫn xuất | 8 | 0 | ⬜ Chưa bắt đầu | WS-17, WS-18 | 7 pd |
| **WS-20** | FE admin — màn hình CMS | 10 | 0 | ⬜ Chưa bắt đầu | WS-13→15 (API) | 12 pd |
| **WS-21** | FE admin — màn hình Công trình | 10 | 0 | ⬜ Chưa bắt đầu | WS-17→19 (API) | 12 pd |
| **WS-22** | Kiểm thử, nghiệm thu Phase 1 & trả nợ | 8 | 0 | ⬜ Chưa bắt đầu | tất cả | 8 pd |
| | **TỔNG** | **99** | **5** | | | **100 pd** |

*(99 task triển khai + 17 mục Definition of Done ở cuối file.)*

**Trạng thái**: ⬜ Chưa bắt đầu · 🟡 Đang làm · ✅ Xong · ⏸ Tạm dừng · ❌ Bỏ

### Sơ đồ phụ thuộc

```
WS-12 ─────────────────────────────────────► [nền — CHẶN mọi thứ, làm trước]
   ├─► WS-13 ─► WS-15 ─► WS-16               [nhánh CMS + cổng công khai]
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
| 12 | **"Cụm công trình" là đơn vị tổ chức hay chỉ là cách nhóm?** `implement.md` nêu bảng `construction_clusters`, còn CN-02.1 lại xếp Cụm vào *cấp quản lý* cạnh Công ty/Xí nghiệp | **Phase 1 KHÔNG dựng `construction_clusters`.** Chỉ có `constructions.org_unit_id` → `org_units`. Lý do: thêm một FK rỗng về sau là migration rẻ; gỡ một cây tổ chức đã bị pha tạp thì không. Chờ G15 rồi quyết | ❓ **G15** |
| 13 | **Mã công trình tự sinh hay nhập tay?** | **Nhập tay, có gợi ý tự sinh** `<LOẠI>-<XN>-<số>`. Công ty đã có mã riêng (G8 đang xin file Excel) — ép tự sinh là buộc họ đổi mã đang dùng trên giấy tờ | 🔧 |
| 14 | **Trạng thái công trình là giá trị dẫn xuất — tính lúc đọc hay lưu sẵn?** Spec chỉ nói "tính ở BE" | **Lưu sẵn một cột + tính lại theo sự kiện + job đối soát định kỳ.** Tính lúc đọc thì mỗi lần mở bản đồ là vài trăm truy vấn con. Cột đó **không có API sửa** — sửa thẳng trả `OPS-3001` | 🔧 |
| 15 | **Bản ghi sửa chữa nhập sau khi xong thì bắt đầu ở trạng thái nào?** Spec: "mặc định Đã xử lý với công việc nhập sau khi hoàn thành", nhưng workflow engine chỉ có **một** `initial_state` | Workflow phải nhận **nhiều trạng thái khởi đầu** (T12.5). ⛔ Cấm lách bằng cách tạo ở `MOI` rồi chạy transition giả — lịch sử sẽ ghi một sự việc chưa từng xảy ra | 🔧 |
| 16 | **`alert_event_id` trỏ sang bảng của `hydro` — có đặt FK không?** | **Không FK.** Lưu `alert_event_public_id UUID`, tra qua `hydro.spi`. FK xuyên module trói hai module lại ở tầng CSDL, đúng thứ ranh giới module sinh ra để tránh | 🔧 |
| 17 | **Đơn vị thực hiện: nội bộ hay nhà thầu ngoài?** Spec ghi "Text / FK" | **Hai cột** `performer_org_unit_id` (FK) và `performer_name` (text) + CHECK **đúng một** cột có giá trị. Một cột lưu cả hai kiểu là bảo đảm sẽ có dữ liệu bẩn | 🔧 |
| 18 | **Tiền lưu đơn vị nào?** CN-02.2 ghi chi phí "VND", CN-02.1 ghi tổng vốn "triệu VND" | **Mọi cột tiền lưu VND, `NUMERIC(18,2)`.** Form nào hiển thị triệu thì quy đổi ở FE. Hai đơn vị trong cùng một CSDL là lỗi cộng dồn chờ sẵn (quy tắc 2) | 🔧 |

### Ba mục mới cần Công ty — đã ghi vào `business-open-questions.md`

| Mã | Cần gì | Chặn cái gì |
|---|---|---|
| **G13** | Bộ nhận diện cổng: logo, favicon, màu chủ đạo, thông tin footer, link mạng xã hội, GA/GTM, (và reCAPTCHA key cho Phase 2) | **Nghiệm thu** WS-15/WS-16 — không chặn code |
| **G14** | Sơ đồ danh mục nội dung + menu cổng + nội dung trang tĩnh (Giới thiệu, Liên hệ…) | **Nghiệm thu** WS-16 — cổng rỗng thì không có gì để nghiệm thu |
| **G15** | "Cụm công trình" có phải một đơn vị trong sơ đồ tổ chức (có người phụ trách, có nhân sự) hay chỉ là cách nhóm công trình? | **Thiết kế** — quyết định có `construction_clusters` hay không. Chưa trả lời thì làm theo phương án tối giản ở điểm 12 |

---

## WS-12 — Mở SPI Core + nền cho module nghiệp vụ · 6 pd

**Tiên quyết**: Phase 0 xong. **Đầu ra**: module nghiệp vụ gọi được cả 6 dịch vụ dùng chung của Core mà ArchUnit vẫn xanh; workflow nhận nhiều trạng thái khởi đầu; đính kèm có hạn mức và ảnh phái sinh.

> ⚠⚠ **Đây là nợ #56 và là việc CHẶN.** `core/spi/` hiện chỉ có `package-info.java`, trong khi cả sáu dịch vụ nằm ở `core.application.*`. Dòng mã Phase 1 đầu tiên gọi `WorkflowEngine` sẽ làm CI đỏ. Quyết định đã ghi ở `architecture-review.md` §9.14: **mở SPI, giữ nguyên luật ArchUnit**.
>
> ⚠ **Và việc này lớn hơn "thêm sáu interface".** Chữ ký hiện tại trả về **entity domain**: `AttachmentService.upload()` → `core.domain.attachment.Attachment`, `JobService.enqueue()` → `core.domain.job.Job`, `OrgUnitService.get()` → `core.domain.org.OrgUnit`. Module nghiệp vụ import những lớp đó là **vi phạm y hệt** như import `core.application`. Nên SPI phải có bộ record riêng, không phải bọc mỏng.

- [x] **T12.1** Sáu interface ở `core/spi/`: `WorkflowPort`, `NotificationPort`, `AttachmentPort`, `JobPort`, `SettingPort`, `OrgUnitPort` — *`architecture-review.md` §9.14* ✅ *19/8*
- [x] **T12.2** Bộ record truyền dữ liệu ở `core.spi`: `AllowedAction`, `AttachmentRef`, `AttachmentUploadCommand`, `JobRef`, `JobRequest`, `NotifyRequest`, `OrgUnitRef` + 2 enum `NotifySeverity`/`NotifyChannel` ✅ *19/8*
- [x] **T12.3** ⚠ Chuyển `WorkflowAware` từ `core.domain.workflow` sang `core.common.persistence` — entity của `content`/`operations` **phải implement** nó. Cùng lý do `BaseEntity`/`ScopedEntity` đã nằm ở `core.common`: đây là hợp đồng hạ tầng, không phải mô hình nghiệp vụ. Luật ArchUnit "`applyState` chỉ được gọi từ `WorkflowEngine`" (nợ #19) vẫn nguyên vẹn ✅ *19/8*
- [x] **T12.4** Service ở `core.application` cài interface tương ứng; bean công khai cho module khác **là interface** ✅ *19/8*
- [ ] **T12.5** ⚠ Workflow nhiều trạng thái khởi đầu — điểm nghiệp vụ **15**. Thêm `workflow_initial_states` (hoặc cột `is_initial`) + `WorkflowPort.initialStates(entityType)`; engine kiểm trạng thái khởi tạo có hợp lệ không
- [ ] **T12.6** `AttachmentPort`: hạn mức theo chủ sở hữu (CN-02.3 — 500MB/công trình) + API đếm dung lượng đang dùng; ngưỡng đọc từ `settings`
- [ ] **T12.7** Ảnh phái sinh chạy bằng job nền: WebP + thumbnail 150/400/800 (CN-01.3), giữ bản gốc làm dự phòng. Dùng chung cho media CMS và ảnh hiện trạng công trình
- [x] **T12.8** ⭐ **Bài kiểm chứng minh ranh giới bắt được vi phạm** — `conventions.md` §1.5. `ModuleBoundarySelfCheckTest` + `BoundaryFixtures` (gói `com.songnhue.content.boundaryfixture` trong `src/test`) ✅ *19/8*

**Kết quả phần đã làm (19/8)**: 16 tệp ở `core/spi/` · 6 service cài port · **269 test BE xanh** (211 core + 58 app, tăng 6).

**Kiểm chứng — đã chạy**:
- ✅ `./mvnw verify` → **BUILD SUCCESS**, 7/7 module
- ✅ **Kiểm chứng ngược trên mã production, không chỉ fixture**: đặt một lớp thật ở `content.application` nhận `SettingService` → `ModuleBoundaryTest` **đỏ**, chỉ đích danh cả 3 cạnh phụ thuộc (tham số hàm dựng · kiểu trường · lời gọi phương thức). Đổi đúng lớp đó sang `SettingPort` → **xanh**
- ✅ `ModuleBoundarySelfCheckTest` 4 bài: đường qua `core.spi` **được cho qua** · gọi thẳng `core.application` **bị chặn** · **chỉ nhận về** một entity `core.domain` cũng **bị chặn** · fixture nằm ngoài tập lớp production
- ✅ `NotificationEnumParityTest` — và nó **bắt lỗi ngay lượt chạy đầu**, xem bên dưới
- ⬜ Chưa chạy: kiểm chứng trạng thái khởi đầu thứ hai (thuộc T12.5)

**Quyết định phát sinh khi làm** (khác/bổ sung so với kế hoạch):
| Việc | Xử lý |
|---|---|
| Service cài port trực tiếp hay thêm lớp adapter? | **Cài trực tiếp.** Tên phương thức của port khác tên nội bộ ở chỗ kiểu trả về khác (`refsOf` vs `listOf`, `findJob` vs `getOwn`) — vừa tránh đụng độ chữ ký sau xoá kiểu, vừa là dấu hiệu đọc được: tên nào thuộc hợp đồng, tên nào là nội bộ |
| `NotificationRequest` tham chiếu enum của `domain` | Nhân bản thành `NotifySeverity`/`NotifyChannel` ở `core.spi` + ánh xạ bằng `valueOf(name())`. Hợp đồng SPI **không được trói vào mô hình lưu trữ** của core |
| ⚠⚠ **Và bản sao đó trôi lệch ngay lập tức** | Tôi chép thiếu `DANGER` và `WEB_PUSH`. `valueOf(name())` nghĩa là lỗi này **biên dịch trót lọt, test đơn vị vẫn xanh, rồi ném lỗi lúc chạy** đúng lúc có người bấm gửi thông báo. `NotificationEnumParityTest` bắt được ở lượt chạy đầu tiên — bài canh viết ra 10 phút thì thu hồi vốn ngay trong 10 phút |
| `JobService.getOwn` và `findJob` cùng mang một luật bảo mật | Rút luật vào private `findOwn(...)`; hai phương thức công khai chỉ là hai hình dạng trả về. Viết luật hai lần là để hai bản lệch nhau |
| Fixture đặt gói nào? | `com.songnhue.content.boundaryfixture` — luật phân loại module **theo tên gói**, đặt ở `com.songnhue.app..` thì nó bỏ qua sạch và bài tự kiểm thành trang trí |

**Nợ giao cho WS sau**: ⬜ T12.9 → WS-17/T17.2 (kiểm chứng tầng 3 trên entity nghiệp vụ thật) · ⬜ ClamAV chạy thật vẫn treo ở nợ #20

---

## WS-13 — CMS: Danh mục nội dung & Bài viết · 12 pd

**Tiên quyết**: WS-12. **Đầu ra**: soạn → gửi duyệt → duyệt → xuất bản chạy hết bằng API, có phiên bản và nhật ký.

- [ ] **T13.1** Migration `db/migration/**cms**/`: `categories` (cây 3 cấp, materialized path), `articles`, `article_categories`, `article_versions`, `tags`, `article_tags` — ⚠ tiền tố thư mục là **`cms`**, không phải `content` (`docs/coding-guide.md` §3.1)
- [ ] **T13.2** Entity + `@Audited(module="cms")`; `Article implements WorkflowAware`; **kế thừa `BaseEntity`, KHÔNG `ScopedEntity`** — điểm nghiệp vụ **9**
- [ ] **T13.3** Seed workflow `ARTICLE` bằng migration: `NHAP · CHO_DUYET · YEU_CAU_CHINH_SUA · XUAT_BAN · GO_BAI · LUU_TRU` + transition kèm `required_permission` và `notify_event`. **Quy tắc tách vai trò**: `SUBMIT` cần `cms:article:submit`, `APPROVE` cần `cms:article:approve` — Biên tập viên không có mã thứ hai nên không tự xuất bản được, ràng buộc nằm ở **dữ liệu**, không ở `if` trong service
- [ ] **T13.4** Slug: `SlugUtils` bỏ dấu tiếng Việt, cho sửa tay, duy nhất → trùng trả `CMS-2001` — điểm nghiệp vụ **4**
- [ ] **T13.5** `article_versions`: mỗi lần lưu nội dung ghi một bản; API so sánh (diff) + phục hồi bản cũ
- [ ] **T13.6** Sửa bài đã xuất bản theo cơ chế **copy-on-write** — điểm nghiệp vụ **1**
- [ ] **T13.7** Hẹn giờ đăng: `published_at` tương lai; job 5' quét bài tới hạn → gọi revalidate (đấu nối thật ở WS-16) — điểm nghiệp vụ **5**
- [ ] **T13.8** Tìm kiếm quản trị (CN-01.8 phần bài viết): `unaccent` + `pg_trgm`, lọc theo danh mục/trạng thái/tác giả/khoảng thời gian, phân trang 20/50/100, sắp xếp qua `PageUtils` (danh sách cột cho phép)
- [ ] **T13.9** Xoá danh mục còn bài viết → chặn, yêu cầu chuyển bài trước (mã lỗi mới)
- [ ] **T13.10** Đếm lượt xem theo lô — điểm nghiệp vụ **6**
- [ ] **T13.11** Mã lỗi mới → `ErrorCode` (BE) **và** `frontend/admin-app/src/shared/error-map.ts` — có bài kiểm canh sự đồng bộ, đừng để nó đỏ ở CI
- [ ] **T13.12** Test: Biên tập viên gọi `APPROVE` → 403 · workflow đủ nhánh · slug trùng · phiên bản + phục hồi · hẹn giờ

**Kiểm chứng**: một tài khoản Biên tập viên và một tài khoản Quản trị nội dung đi hết vòng đời bài viết bằng HTTP thật; `audit_logs` có đủ old/new; `notifications` có bản ghi ở bước gửi duyệt và duyệt.

---

## WS-14 — CMS: Thư viện Media · 6 pd

**Tiên quyết**: WS-12 (T12.6, T12.7). **Đầu ra**: tải ảnh/tài liệu lên, có thư mục, có ảnh phái sinh, chèn được vào bài viết.

- [ ] **T14.1** Migration `media_folders` — cây **tối đa 3 cấp**, chặn ở tầng service chứ không chỉ ở UI
- [ ] **T14.2** Tệp media = `attachments` với `owner_type='MEDIA_FOLDER'` — điểm nghiệp vụ **8**, không bảng tệp thứ hai
- [ ] **T14.3** Tải nhiều tệp; giới hạn theo loại đọc từ `settings`: ảnh 10MB · video 500MB · tài liệu 50MB · nén 100MB
- [ ] **T14.4** Ảnh phái sinh (T12.7) + danh sách Grid/List, lọc theo loại/thư mục/ngày, sao chép URL 1 lần bấm
- [ ] **T14.5** Xoá tệp đang được bài viết tham chiếu → cảnh báo có danh sách bài đang dùng; xoá thư mục **chỉ khi rỗng**
- [ ] **T14.6** ⚠ SVG — điểm nghiệp vụ **7**: chỉ nhận ở màn hình cấu hình, khử trùng trước khi lưu; test bằng SVG có `onload` và có `<script>`

**Kiểm chứng**: tải thật lên MinIO qua HTTP multipart; tệp đổi đuôi giả mạo bị `FileValidator` loại; SVG độc hại bị khử trùng — **đóng luôn DoD #11 của Phase 0** (đính kèm chưa từng kiểm chứng đầu-cuối qua HTTP).

---

## WS-15 — CMS: Cấu hình giao diện, Menu, Banner · 6 pd

**Tiên quyết**: WS-13. **Đầu ra**: cổng công khai lấy được toàn bộ cấu hình hiển thị từ API.

- [ ] **T15.1** Migration `banners`, `menus` (cây lồng nhau, hai vị trí header/footer độc lập)
- [ ] **T15.2** Cấu hình chung website → **nhóm `site` trong `settings`**, không bảng mới: tên site, slogan, logo, favicon, màu chủ đạo/phụ, GA Tracking ID, GTM Container ID, Maintenance Mode
- [ ] **T15.3** Footer: khối thông tin công ty, bản đồ nhúng, liên kết nhanh, mạng xã hội, copyright
- [ ] **T15.4** Trang đặc biệt: chọn khối hiển thị trang chủ; trang 404 tuỳ biến
- [ ] **T15.5** ⛔ **Widget thủy văn: chỉ giữ chỗ cấu hình, ẩn khỏi UI ở v1** — cần MOD-03 (Phase 2). Ghi rõ trong màn hình để không ai tưởng là lỗi
- [ ] **T15.6** Cache cấu hình bằng Caffeine + vô hiệu hoá ngay khi sửa (cổng công khai đọc rất nhiều, đổi rất ít)

**Kiểm chứng**: sửa một tham số ở admin → API công khai trả giá trị mới **trong cùng phiên**, không phải chờ hết hạn cache.

---

## WS-16 — Public-web: hiển thị + ISR · 8 pd

**Tiên quyết**: WS-13, WS-15. **Đầu ra**: bài viết duyệt xong hiện trên cổng thật, không phải bấm build lại.

> ⭐ WS này bổ sung so với `implement.md` §3 (vốn xếp phần public vào Phase 2). Lý do ở `architecture-review.md` §10.1: `POST /api/revalidate` đã được dựng ở WS-9 **cho đúng luồng này** và tới giờ chưa ai đi qua.

- [ ] **T16.1** Nhóm API công khai `@PublicEndpoint`: danh sách bài, chi tiết theo slug, theo danh mục, menu, banner, cấu hình site. ⛔ **Chỉ trả bài `XUAT_BAN` và `published_at <= now()`** — có bài kiểm cố tình hỏi bài Nháp bằng slug đúng và phải nhận 404
- [ ] **T16.2** Giới hạn tần suất riêng cho nhóm công khai + cache; không đụng bucket của API quản trị
- [ ] **T16.3** Trang Next: danh sách, chi tiết, theo danh mục, tìm kiếm; dùng ISR
- [ ] **T16.4** SEO: metadata + Open Graph theo từng bài; `sitemap.ts` **đọc từ DB** thay vì danh sách tĩnh; giữ nguyên cơ chế tự chặn lập chỉ mục ở staging/local
- [ ] **T16.5** ⭐ `POST /api/revalidate` đấu nối thật vào bước xuất bản và bước hẹn giờ tới hạn; có bí mật chia sẻ, có ghi log lượt gọi
- [ ] **T16.6** Ảnh trong bài: quyết định đường phục vụ tệp công khai từ MinIO (bucket công khai riêng hay proxy qua BE) — **không** dùng presigned URL cho ảnh trang công khai, vì URL hết hạn thì trang tĩnh đã cache sẽ hỏng ảnh
- [ ] **T16.7** Trang 404/500; bài `GO_BAI` trả 404 nhưng **giữ nguyên dữ liệu**; bài `LUU_TRU` không lên danh sách nhưng vẫn vào được bằng URL trực tiếp
- [ ] **T16.8** Kiểm chứng đầu-cuối: soạn → gửi duyệt → duyệt → xuất bản → cổng hiện bài, **đo thời gian thật** từ lúc bấm tới lúc trang đổi

**Kiểm chứng**: chạy `make dev-docker`, đi trọn luồng trên trình duyệt. Trang chủ đo được **< 3s** (NFR-02).

---

## WS-17 — Operations: Danh mục công trình · 13 pd

**Tiên quyết**: WS-12. **Đầu ra**: hồ sơ công trình đủ 4 loại, có toạ độ, có tài liệu, có nhật ký thay đổi, và **phân quyền tầng 3 chạy trên entity thật**.

- [ ] **T17.1** Migration `db/migration/**ops**/`: `constructions` + `pump_station_specs` + `sluice_specs` + hồ sơ tối thiểu cho đê/kênh. Mọi số đo `NUMERIC`, tiền `NUMERIC(18,2)` VND — điểm nghiệp vụ **18**
- [ ] **T17.2** ⭐ `Construction extends ScopedEntity` — **entity nghiệp vụ đầu tiên thuộc phạm vi đơn vị**. Trả nợ T12.9: chạy lại kiểm chứng tầng 3 trên bảng thật, thay cho `ScopedRecord` dựng riêng cho test ở Phase 0. Dòng log *"Chưa có entity nào thuộc phạm vi đơn vị"* phải **biến mất**
- [ ] **T17.3** Mã công trình duy nhất toàn hệ thống; gợi ý tự sinh nhưng cho sửa — điểm nghiệp vụ **13**
- [ ] **T17.4** Toạ độ `Decimal(9,6)` + cột PostGIS; `river_name`, `chainage` (`K<km>+<m>`); danh sách "Công trình chưa có vị trí GIS"
- [ ] **T17.5** Lưu vực / khu tưới tiêu = **trường văn bản** (chốt F3) — ⛔ không bảng danh mục, không polygon riêng
- [ ] **T17.6** Trạng thái vận hành là **cột dẫn xuất** (tính ở WS-19); API nhận `status` từ client → `OPS-3001`
- [ ] **T17.7** Tài liệu công trình (CN-02.3) qua `AttachmentPort`: hạn mức 500MB/công trình, nhãn loại tài liệu, ngày lập + ngày hết hiệu lực, phiên bản
- [ ] **T17.8** Nhật ký thay đổi hồ sơ (CN-02.7) = **API đọc `audit_logs`** lọc theo `entity_type='CONSTRUCTION'` — ⛔ không dựng bảng lịch sử thứ hai; `@Audited` đã ghi đủ old/new
- [ ] **T17.9** Nhập từ Excel/CSV: **chạy khô trước** (xem trước + báo lỗi từng dòng + đếm sẽ thêm/sửa bao nhiêu), có lỗi chặn thì **không nhập dòng nào**. Đây cũng là đường seed dữ liệu thật khi G8 về
- [ ] **T17.10** Thống kê & tìm kiếm (CN-02.6): đếm theo loại / đơn vị / trạng thái / cấp quản lý; lọc trên danh sách. Biểu đồ để Phase 3
- [ ] **T17.11** ⚠ **Không dựng `construction_clusters`** — điểm nghiệp vụ **12**, chờ G15. Nhóm hiển thị theo `org_units`
- [ ] **T17.12** Test: tầng 3 đủ 3 nhánh (đơn vị mình · cấp trên thấy cấp dưới · đơn vị khác → `AUTH-3002` + `security_events`) + mã lỗi mới

**Kiểm chứng**: hai tài khoản thuộc hai Xí nghiệp khác nhau — mỗi người chỉ thấy công trình đơn vị mình; tài khoản cấp Công ty thấy cả hai. **Đây là lần đầu tiên điều đó được chứng minh trên dữ liệu nghiệp vụ thật.**

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

- [ ] **T20.1** Trình soạn thảo (CKEditor 5 hoặc TinyMCE) — ⛔ **bản tự host, không CDN**: CSP của trang chặn mọi tài nguyên ngoài
- [ ] **T20.2** Danh sách bài + bộ lọc + thao tác hàng loạt + phân trang phía máy chủ
- [ ] **T20.3** Biểu mẫu bài viết: SEO đếm ký tự có cảnh báo vượt ngưỡng, ảnh đại diện, tệp đính kèm, hẹn giờ đăng
- [ ] **T20.4** ⭐ Nút duyệt render từ `allowedActions` của API — ⛔ **không tự suy từ trạng thái** (`conventions.md` §3)
- [ ] **T20.5** So sánh phiên bản (diff) + phục hồi bản cũ
- [ ] **T20.6** Cây danh mục kéo thả (`OrgUnitTreeSelect` đã có, dùng lại cho cây danh mục)
- [ ] **T20.7** Thư viện media: kéo-thả nhiều tệp có thanh tiến trình từng tệp, Grid/List, hộp chọn ảnh cho bài viết
- [ ] **T20.8** Cấu hình giao diện: banner kéo thả, menu lồng nhau, footer, cấu hình chung
- [ ] **T20.9** Mã lỗi mới vào `error-map.ts` — bài kiểm đọc thẳng file `ErrorCode.java` của backend nên **lệch là CI đỏ**
- [ ] **T20.10** Test FE cho các hàm thuần: quy tắc hiện/ẩn nút, đếm ký tự SEO, dựng cây danh mục

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
| 35 | Dựng luồng quên mật khẩu + vòng đời mật khẩu M5.15-a (đã chốt cách làm 18/8, **chưa có mã**) | WS-8/T8.6 | **Phase 1 — chưa xếp WS** | ⬜ Chờ |
| 56 | `core/spi/` rỗng — chặn dòng mã Phase 1 đầu tiên | Rà soát 19/8 | **WS-12/T12.1→T12.4** | ✅ **Trả 19/8** — 6 port + 7 record + 2 enum; kiểm chứng ngược trên mã production |
| 11 (DoD P0) | Đính kèm chưa kiểm chứng đầu-cuối qua HTTP | WS-6 | **WS-14** | ⬜ Chờ |
| 20 | Dựng ClamAV trong compose để quét virus chạy thật | WS-6/T6.4 | WS-11/T11.3 *(vẫn ở Phase 0)* | ⬜ Chờ |

⚠ **Nợ #35 chưa có chỗ đứng.** Vòng đời mật khẩu đã đặc tả xong ở `function-spec.md` M5.15-a nhưng Phase 1 hiện không có WS nào nhận. Hai lựa chọn: chen vào WS-12 (cùng vùng Core, ~4 pd) hoặc để Phase 2. **Phải quyết trước khi đóng WS-12** — để trôi là đúng cái bẫy "WS nhận không có task nào đứng tên" mà sổ nợ này sinh ra để chặn.

### Nợ phát sinh trong Phase 1

| # | Nợ | Phát sinh ở | Task nhận | Trạng thái |
|:-:|---|---|---|:-:|
| 57 | Kiểm chứng tầng 3 trên entity nghiệp vụ thật | WS-12 | WS-17/T17.2 | ⬜ Chờ |
| 58 | `HydroAlertPort` mới có phần khai, chưa có phần cài | WS-19/T19.5 | Phase 2 (`hydro`) | ⬜ Chờ |
| 59 | Nút "Tạo bản ghi khắc phục" từ màn hình cảnh báo | WS-18/T18.10 | Phase 2 | ⬜ Chờ |
| 60 | Widget thủy văn ở cấu hình giao diện (nay ẩn) | WS-15/T15.5 | Phase 2 | ⬜ Chờ |
| 61 | `construction_clusters` — chờ **G15** | WS-17/T17.11 | Sau khi Công ty trả lời | ⬜ Chờ |

---

## PHỤ THUỘC BÊN NGOÀI — CẦN CÔNG TY

| Mã | Cần gì | Chặn | Hạn nên có |
|---|---|---|---|
| **G13** | Bộ nhận diện cổng + tài khoản dịch vụ ngoài | Nghiệm thu WS-15/16 | Trước khi đóng WS-16 |
| **G14** | Sơ đồ danh mục/menu + nội dung trang tĩnh | Nghiệm thu WS-16 | Trước khi đóng WS-16 |
| **G15** | "Cụm công trình" là đơn vị tổ chức hay cách nhóm? | Thiết kế WS-17 (đang đi đường tối giản) | Trước khi đóng WS-17 |
| **G8** | Danh mục công trình (Excel) + toạ độ | **Dữ liệu khởi tạo**, không chặn code — T17.9 đã dựng sẵn đường nhập | Trước nghiệm thu Phase 1 |
| **G5** | Mã số hệ thống văn bản riêng hay chung | **CN-01.7 — đã tách khỏi Phase 1** | Trước Phase 2 |

---

## Nhật ký thay đổi kế hoạch

| Ngày | Thay đổi |
|---|---|
| 2026-08-19 | **WS-12 làm 5/8** — mở `core/spi` (trả nợ #56), chuyển `WorkflowAware` sang `core.common.persistence`, 6 service cài port, bài tự kiểm ranh giới + bài canh hai enum. 269 test BE xanh. Còn T12.5 (nhiều trạng thái khởi đầu), T12.6 (hạn mức đính kèm), T12.7 (ảnh phái sinh) |
| 2026-08-19 | Lập kế hoạch Phase 1: 11 hạng mục WS-12→WS-22, 99 task, ~100 pd. Ba quyết định phạm vi: **gộp public-web vào Phase 1** (vì `POST /api/revalidate` của WS-9 chưa ai đi qua) · **giữ Liên hệ/Phản hồi ở Phase 2** · **dựng đường nhập Excel có chạy khô**. Làm rõ **18 điểm nghiệp vụ**; mở **3 mục mới cần Công ty** (G13, G14, G15) |
