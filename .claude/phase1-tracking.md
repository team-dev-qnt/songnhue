# PHASE 1 — CMS & MASTER DATA CÔNG TRÌNH · BẢNG THEO DÕI TIẾN ĐỘ

> **Cập nhật lần cuối**: 2026-08-19 · **Tiến độ: 17/101 task (17%)** · 1 task **hoãn có chủ đích** (T12.7 → nợ #62) · **DoD: 0/17** · Trạng thái: 🟡 Đang làm — **WS-12 xong**, WS-13 10/12
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
| **WS-12** | Mở SPI Core + nền cho module nghiệp vụ | 8 | **7** | ✅ **Xong 19/8** — T12.7 hoãn có chủ đích (nợ #62) | Phase 0 | 6 pd |
| **WS-13** | CMS — Danh mục nội dung & Bài viết | 12 | **10** | 🟡 Đang làm (19/8) — còn T13.7, T13.10 (nợ #63, #64) | WS-12 | 12 pd |
| **WS-14** | CMS — Thư viện Media | 6 | 0 | ⬜ Chưa bắt đầu | WS-12 | 6 pd |
| **WS-15** | CMS — Cấu hình giao diện, Menu, Banner | 7 | 0 | ⬜ Chưa bắt đầu | WS-13 | 6 pd |
| **WS-16** | Public-web — hiển thị + ISR | 8 | 0 | ⬜ Chưa bắt đầu | WS-13, WS-15 | 8 pd |
| **WS-17** | Operations — Danh mục công trình | 12 | 0 | ⬜ Chưa bắt đầu | WS-12 | 14 pd |
| **WS-18** | Operations — Lịch sử sửa chữa & sự cố | 11 | 0 | ⬜ Chưa bắt đầu | WS-17 | 10 pd |
| **WS-19** | Operations — Tình hình vận hành + trạng thái dẫn xuất | 8 | 0 | ⬜ Chưa bắt đầu | WS-17, WS-18 | 7 pd |
| **WS-20** | FE admin — màn hình CMS | 10 | 0 | ⬜ Chưa bắt đầu | WS-13→15 (API) | 12 pd |
| **WS-21** | FE admin — màn hình Công trình | 10 | 0 | ⬜ Chưa bắt đầu | WS-17→19 (API) | 12 pd |
| **WS-22** | Kiểm thử, nghiệm thu Phase 1 & trả nợ | 8 | 0 | ⬜ Chưa bắt đầu | tất cả | 8 pd |
| | **TỔNG** | **101** | **17** | | | **101 pd** |

*(101 task triển khai + 17 mục Definition of Done ở cuối file.)*

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
- [ ] **T13.7** Hẹn giờ đăng: `published_at` tương lai; job 5' quét bài tới hạn → gọi revalidate (đấu nối thật ở WS-16) — điểm nghiệp vụ **5**
- [x] **T13.8** Tìm kiếm quản trị (CN-01.8 phần bài viết): `unaccent` + `pg_trgm`, lọc theo danh mục/trạng thái/tác giả/khoảng thời gian, phân trang 20/50/100, sắp xếp qua `PageUtils` (danh sách cột cho phép)
- [x] **T13.9** Xoá danh mục còn bài viết → chặn, yêu cầu chuyển bài trước (mã lỗi mới)
- [ ] **T13.10** Đếm lượt xem theo lô — điểm nghiệp vụ **6**
- [x] **T13.11** Mã lỗi mới → `ErrorCode` (BE) **và** `frontend/admin-app/src/shared/error-map.ts` — có bài kiểm canh sự đồng bộ, đừng để nó đỏ ở CI
- [x] **T13.12** Test: Biên tập viên gọi `APPROVE` → 403 · workflow đủ nhánh · slug trùng · phiên bản + phục hồi · hẹn giờ

**Kết quả (19/8)**: 10/12 task. Còn **T13.7** (job hẹn giờ đăng) và **T13.10** (đếm lượt xem theo lô) — tham số `settings` của cả hai đã seed, phần job chưa dựng. **298 test BE xanh** (211 core + 87 app), trong đó `ArticleLifecycleTest` **14 bài trên CSDL thật**.

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

- [ ] **T14.1** Migration `media_folders` — cây **tối đa 3 cấp**, chặn ở tầng service chứ không chỉ ở UI
- [ ] **T14.2** Tệp media = `attachments` với `owner_type='MEDIA_FOLDER'` — điểm nghiệp vụ **8**, không bảng tệp thứ hai
- [ ] **T14.3** Tải nhiều tệp; giới hạn theo loại đọc từ `settings`: ảnh 10MB · video 500MB · tài liệu 50MB · nén 100MB
- [ ] **T14.4** Danh sách Grid/List, lọc theo loại/thư mục/ngày, sao chép URL 1 lần bấm. ⚠ Ảnh hiển thị là **ảnh gốc** (T12.7 hoãn) → lưới ảnh **bắt buộc** `loading="lazy"` + khung CSS cố định, nếu không thì mở một thư mục 200 ảnh là tải về vài trăm MB
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
- [ ] **T15.7** ⭐ Seed **menu header/footer đề xuất** + 4 trang tĩnh rỗng có sẵn slug (Giới thiệu · Chức năng nhiệm vụ · Cơ cấu tổ chức · Liên hệ) — G14. Cổng có ruột để nghiệm thu ngay cả khi nội dung thật về muộn

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
- [ ] **T17.11** `construction_clusters` (mã, tên, đơn vị quản lý, thứ tự) + `constructions.cluster_id` **nullable** + CRUD danh mục cụm — điểm nghiệp vụ **12**, G15 đã đóng. ⚠ Cụm **chỉ để nhóm hiển thị và lọc**: cấm dùng `cluster_id` trong bất kỳ truy vấn phân quyền nào, phạm vi vẫn đi bằng `org_unit_id`
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
| 35 | Dựng luồng quên mật khẩu + vòng đời mật khẩu M5.15-a (đã chốt cách làm 18/8, **chưa có mã**) | WS-8/T8.6 | **Phase 2** — chốt 19/8, ~4 pd | ⬜ Chờ |
| 56 | `core/spi/` rỗng — chặn dòng mã Phase 1 đầu tiên | Rà soát 19/8 | **WS-12/T12.1→T12.4** | ✅ **Trả 19/8** — 6 port + 7 record + 2 enum; kiểm chứng ngược trên mã production |
| 11 (DoD P0) | Đính kèm chưa kiểm chứng đầu-cuối qua HTTP | WS-6 | **WS-14** | ⬜ Chờ |
| 20 | Dựng ClamAV trong compose để quét virus chạy thật | WS-6/T6.4 | WS-11/T11.3 *(vẫn ở Phase 0)* | ⬜ Chờ |

✅ **Nợ #35 đã có chỗ đứng — chốt 19/8: Phase 2, ~4 pd.** Hai lý do cho phép hoãn mà không gây hại:
- **Hạn 90 ngày chỉ cắn sau go-live 90 ngày** — không nằm trong bất kỳ kịch bản nghiệm thu Phase 1 nào.
- **Quên mật khẩu đã có đường đi từ ngày đầu**: quản trị viên cấp mật khẩu tạm (M5.15-a). Luồng tự phục vụ là tiện lợi, không phải điều kiện vận hành.

⛔ **Không được suy ra rằng chính sách mật khẩu đang bị tắt.** BCrypt cost ≥ 12, độ dài tối thiểu, bắt đổi lần đầu, khoá sau 5 lần sai — tất cả đã chạy từ WS-5. Phần hoãn **chỉ là**: hết hạn 90 ngày, nhắc trước 14/7/1 ngày, và luồng tự phục vụ gửi liên kết một lần.

### Nợ phát sinh trong Phase 1

| # | Nợ | Phát sinh ở | Task nhận | Trạng thái |
|:-:|---|---|---|:-:|
| 57 | Kiểm chứng tầng 3 trên entity nghiệp vụ thật | WS-12 | WS-17/T17.2 | ⬜ Chờ |
| 58 | `HydroAlertPort` mới có phần khai, chưa có phần cài | WS-19/T19.5 | Phase 2 (`hydro`) | ⬜ Chờ |
| 59 | Nút "Tạo bản ghi khắc phục" từ màn hình cảnh báo | WS-18/T18.10 | Phase 2 | ⬜ Chờ |
| 60 | Widget thủy văn ở cấu hình giao diện (nay ẩn) | WS-15/T15.5 | Phase 2 | ⬜ Chờ |
| ~~61~~ | ~~`construction_clusters` — chờ **G15**~~ | WS-17/T17.11 | — | ✅ **Đóng 19/8** — G15 trả lời trong ngày, việc dựng bảng nay nằm thẳng trong T17.11 |
| **62** | **Ảnh phái sinh (WebP + thumbnail 150/400/800)** — CN-01.3 yêu cầu, Phase 1 dùng ảnh gốc | WS-12/T12.7 | **Phase 2** *(hoặc sớm hơn nếu trúng điều kiện kích hoạt bên dưới)* | ⏸ **Hoãn có chủ đích 19/8** |
| **63** | Job hẹn giờ đăng bắn revalidate ISR (T13.7) — tham số `settings` đã seed, job chưa dựng. ⚠ **Không chặn nghiệp vụ**: bài tới hạn vẫn tự hiện vì truy vấn công khai lọc `published_at <= now()`; job chỉ để cổng tĩnh cập nhật đúng lúc | WS-13/T13.7 | **WS-16** (cùng chỗ đấu nối ISR thật) | ⬜ Chờ |
| **64** | Đếm lượt xem theo lô (T13.10) — `ArticleRepository.addViews` đã có, thiếu endpoint công khai + job đẩy. Cần cùng lúc với trang chi tiết bài ở cổng | WS-13/T13.10 | **WS-16** | ⬜ Chờ |
| **65** | `ArticleLifecycleTest` gọi thẳng service, **chưa đi qua HTTP** — chưa kiểm envelope, `@RequirePermission` tầng 2, và ràng buộc "phải nêu lý do khi trả bài" nằm ở controller | WS-13/T13.12 | **WS-20** (cùng lúc dựng màn hình) | ⬜ Chờ |

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
| 2026-08-19 | **WS-13 làm 10/12** — CMS danh mục & bài viết. 4 bảng + seed quy trình `ARTICLE` 10 bước chuyển · entity/service/controller đủ 3 tầng · **copy-on-write chạy thật** · 5 mã lỗi mới (BE=FE, 55 mã). **298 test BE**, `ArticleLifecycleTest` 14 bài trên CSDL thật. Còn T13.7/T13.10 → nợ #63, #64 |
| 2026-08-19 | ⚠⚠ **Vá lỗ Core lộ ra bởi người dùng đầu tiên**: `WorkflowEngine` chỉ biết luật G11 nên thông báo "có bài chờ duyệt" gửi cho **Ban điều hành** thay vì quản trị nội dung. Thêm `notify_permission` + `notify_owner` vào `workflow_transitions` — `architecture-review.md` §10.10 |
| 2026-08-19 | **WS-12 đóng.** T12.7 (ảnh phái sinh) **hoãn có chủ đích** → nợ #62, lý do ở `architecture-review.md` §10.9: đẩy sang `next/image` không dùng được ở đây (đòi `sharp` native · presigned URL 10' phá đệm của bộ tối ưu và làm vỡ ảnh trên trang ISR · `admin-app` là Vite nên nửa số ảnh không đi qua Next). Phần đắt duy nhất là bộ mã hoá **WebP**; thumbnail thì rẻ vì `ImageSanitizer` đã chạy `ImageIO` sẵn — nhưng chưa biết tải trọng ảnh thật thì làm là đoán mò. **⛔ Cố ý không seed tham số `settings`**: công tắc chưa ai đọc chính là lỗi vừa sửa ở T12.6 |
| 2026-08-19 | **WS-12 làm 5/8** — mở `core/spi` (trả nợ #56), chuyển `WorkflowAware` sang `core.common.persistence`, 6 service cài port, bài tự kiểm ranh giới + bài canh hai enum. 269 test BE xanh. Còn T12.5 (nhiều trạng thái khởi đầu), T12.6 (hạn mức đính kèm), T12.7 (ảnh phái sinh) |
| 2026-08-19 | **Chốt 4 mục nghiệp vụ**: sửa bài đã xuất bản = **copy-on-write** · Quản trị nội dung **được** tự duyệt (audit ghi rõ) · **G15 đóng** — cụm chỉ là cách nhóm → bảng riêng, T17.11 đổi từ "không dựng" sang "dựng", nợ #61 đóng · **G14** — seed khung danh mục/menu đề xuất (T13.13, T15.7). Tổng task 99 → **101** |
| 2026-08-19 | Lập kế hoạch Phase 1: 11 hạng mục WS-12→WS-22, 99 task, ~100 pd. Ba quyết định phạm vi: **gộp public-web vào Phase 1** (vì `POST /api/revalidate` của WS-9 chưa ai đi qua) · **giữ Liên hệ/Phản hồi ở Phase 2** · **dựng đường nhập Excel có chạy khô**. Làm rõ **18 điểm nghiệp vụ**; mở **3 mục mới cần Công ty** (G13, G14, G15) |
