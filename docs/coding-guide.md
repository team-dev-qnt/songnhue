# Hướng dẫn viết một chức năng nghiệp vụ

> Dành cho người sắp viết mã ở `content` / `operations` / `hydro` / `hr`.
> Đọc `conventions.md` để biết **luật**; đọc file này để biết **làm theo thứ tự nào**.
>
> Nguyên tắc bao trùm: **Core đã dựng sẵn 6 cơ chế dùng chung. Việc của module nghiệp vụ là khai
> báo và cắm vào, không phải cài lại.** Mỗi lần một module tự viết lại cây phân cấp, tự gọi SMTP,
> tự đổi trạng thái entity là một lần hệ thống có thêm một bản sao để lệch nhau.

---

## 0. Trước khi gõ dòng đầu tiên

| Bước | Vì sao |
|---|---|
| Mở `business-open-questions.md` **Phần III**, tìm mã chức năng (CN-xx.y) | Biết chức năng này còn "vùng chưa chốt" nào, và **được phép làm tới đâu**. Có mục 🟥 là **chặn**, đừng đoán |
| Mở `function-spec.md` phần tương ứng | Nguồn sự thật về trường dữ liệu, workflow, validation, RBAC |
| Chạy `make hooks` nếu đây là bản clone mới | `core.hooksPath` là cấu hình cục bộ; quên thì hai hook **im lặng không tồn tại** |

---

## 1. Core đang cho sẵn những gì

Đây là danh sách đầy đủ tính tới 19/8/2026. **Trước khi viết bất cứ thứ gì trong bảng này, dừng
lại — nó đã có rồi.**

⛔ **Tiêm vào `interface` ở `com.songnhue.core.spi`, không phải lớp service.** Lớp service nằm ở
`core.application` — module nghiệp vụ chạm vào đó là ArchUnit đỏ (xem §2).

| Cần làm gì | Tiêm cái này | Chữ ký thật |
|---|---|---|
| Đổi trạng thái entity | `WorkflowPort` | `execute(entity, action, title)` · `execute(entity, action, title, reason)` — bản 4 tham số bắt buộc khi bước chuyển khai `requires_reason` · `allowedActions(entity)` · `initialState(entityType)` |
| Tệp đính kèm | `AttachmentPort` | `upload(AttachmentUploadCommand)` · `downloadUrl(publicId)` · `findRef(publicId)` · `refsOf(ownerType, ownerId)` · `usedBytes(ownerType, ownerId)` · `setValidity(publicId, từNgày, đếnNgày)` · `readForPublic(publicId, loạiChoPhép)` · `delete(publicId)` |
| Thông báo (in-app + email) | `NotificationPort` | `notify(NotifyRequest)` · `broadcast(request, userIds)` |
| Việc chạy nền | `JobPort` + bean cài `JobHandler` | `enqueue(JobRequest)` · `findJob(publicId)` |
| Tham số cấu hình được | `SettingPort` | `getInt/getBoolean/getString/getMinutes/getTime(key, fallback)` |
| Cây đơn vị | `OrgUnitPort` | `findRef(publicId)` · `findRefById(id)` · `findRefByCode(code)` — bản theo mã dành cho đường **nhập dữ liệu hàng loạt**, nơi tệp nguồn ghi mã đơn vị chứ không ghi định danh của hệ thống |
| Nhật ký thay đổi của **một** bản ghi | `AuditQueryPort` | `historyOf(module, entityType, entityId, từ, đến, limit)` — ⛔ **module nghiệp vụ không dựng bảng lịch sử riêng**; `audit_logs` đã ghi đủ old/new ở tầng Hibernate và có chuỗi băm chống sửa. ⚠ Bắt buộc truyền khoảng thời gian vì bảng phân mảnh theo tháng; cận dưới **phải lùi về đầu tháng** chứa `createdAt`, không lấy đúng `createdAt` — xem `architecture-review.md` §10.32 |
| Cây danh mục của chính module | `MaterializedPath`, `TreeBuilder` (`core.common.tree`) | |
| Nhật ký kiểm toán | `@Audited` trên entity | tự động, không phải gọi gì |
| Mã hoá trường nhạy cảm | `CryptoService` (AES-256-GCM + `key_id`) | |
| Ngày giờ, số, chuỗi tiếng Việt, sinh mã, che dữ liệu, phân trang, kiểm tệp | 8 utils ở `core.common.util` | **cấm module tự viết lại** — `conventions.md` §2.5 |

⚠ **Các cổng này trả `record` chứ không trả entity** (`AttachmentRef`, `JobRef`, `OrgUnitRef`). Đó là
điều kiện để ranh giới đứng vững: một interface đặt đúng chỗ nhưng *trả về* entity domain thì nơi gọi
vẫn phải import `core.domain.*`, tức là SPI chỉ dời chỗ vi phạm chứ không xoá nó.

**Thiếu phương thức cần dùng?** SPI cố ý mỏng — chỉ khai những gì đang có người gọi. Thêm vào
`core.spi` kèm bài kiểm ở chỗ gọi; ⛔ **đừng** vòng qua bằng cách gọi thẳng lớp service.

Ngoài ra mọi entity nghiệp vụ kế thừa một trong hai lớp ở `core.common.persistence`:

- `BaseEntity` — `id`, `public_id` (UUID), `created_at/by`, `updated_at/by`, `deleted_at`, `version`
- `ScopedEntity` — thêm ràng buộc **phạm vi đơn vị**, để tầng 3 phân quyền tự lọc

> ⚠ Mọi lookup từ request người dùng phải đi qua **`public_id`**, không dùng `id` tuần tự. Đây là
> chống IDOR, không phải sở thích (`conventions.md` §4.2).

---

## 2. ⛔ Ranh giới module

ArchUnit (`ModuleBoundaryTest`) chỉ cho phép một module import:

```
com.songnhue.<module_khac>.spi.*     ← service interface công khai
com.songnhue.core.common.*           ← Common Platform (hạ tầng dùng chung)
```

Mọi thứ khác — `application/`, `domain/`, `infra/` của module khác — **CI đỏ**.

✅ **`core/spi/` đã mở (19/8/2026, WS-12)** — sáu cổng ở bảng mục 1 dùng được ngay.

Luật bắt **cả hai** dạng vi phạm, đã kiểm chứng ngược trên mã production:

```java
import com.songnhue.core.application.settings.SettingService;  // ⛔ gọi thẳng service
import com.songnhue.core.domain.attachment.Attachment;         // ⛔ chỉ NHẬN VỀ entity cũng đỏ
import com.songnhue.core.spi.SettingPort;                      // ✅
```

Thông báo lỗi chỉ đích danh từng cạnh phụ thuộc (tham số hàm dựng, kiểu trường, lời gọi phương
thức), nên tìm chỗ sửa không mất thời gian.

> ⛔ **Cách xử lý sai khi gặp luật này**: nới ArchUnit cho phép import `core.application.*`. Làm thế
> là xoá ranh giới đã dựng cả Phase 0 để tiết kiệm mười phút — và ranh giới module là thứ giữ cho
> Modular Monolith không biến thành một khối dính. Nếu thấy mình đang sửa file test kiến trúc để mã
> của mình chạy được, đó là dấu hiệu đang đi sai đường.

---

## 3. Công thức: thêm một chức năng nghiệp vụ

Thứ tự này không tuỳ tiện — mỗi bước tạo ra thứ mà bước sau cần.

### 3.1. Migration trước, mã sau

`backend/<module>/src/main/resources/db/migration/<tiền-tố>/V<yyyyMMdd><nnnn>__<mo_ta>.sql`

⛔⛔ **`<nnnn>` là SỐ THỨ TỰ chạy tiếp, KHÔNG PHẢI giờ-phút.** Chuỗi ấy chạy `1023 → 1024 → … →
1038` xuyên suốt cả kho, không đếm lại theo ngày. Số hiệu mới phải **lớn hơn mọi số đã có** — kể cả
số của một tệp mang **ngày lớn hơn hôm nay** (chuyện thường gặp: một PR mở trước, merge sau).

Viết `<nnnn>` thành `HHmm` cho ra một chuỗi *trông đúng* mà sắp sai: `202608272320` (23:20 ngày 27)
đứng **trước** `202608281036`. Ngày 27/8 điều đó làm CD Staging đỏ (`Detected resolved migration not
applied to database`) **và** làm một câu `UPDATE` của bộ seed chạm 0 hàng trong im lặng — §10.66.

Bộ canh: `make migration-order` (`backend/tools/kiem-thu-tu-migration.sh`), chạy sẵn ở bước 2/10 của
`make ci-local` và ở job CI *Thứ tự migration*. Nó so với **nhánh nền**, vì thứ đã merge vào `dev`
là thứ đã (hoặc sắp) áp lên staging.

⚠ **Tiền tố thư mục KHÔNG trùng tên module** — Flyway chỉ quét đúng 5 đường dẫn khai trong
`app/src/main/resources/application.yml`:

| Module Maven | Thư mục migration |
|---|---|
| `core` | `db/migration/core` |
| `content` | `db/migration/**cms**` |
| `operations` | `db/migration/**ops**` |
| `hydro` | `db/migration/**hyd**` |
| `hr` | `db/migration/hr` |

Đặt nhầm vào `db/migration/content/` thì migration **không chạy và không có lỗi nào** — app lên
bình thường, bảng không tồn tại, và triệu chứng đầu tiên là một `relation does not exist` ở tầng
nghiệp vụ. Thêm module mới thì phải thêm dòng vào `locations` trước.

- Cột chuẩn theo `conventions.md` §1.2 — luôn có `public_id UUID`, `version`, `deleted_at`
- **`VARCHAR`, không bao giờ `CHAR(n)`** — lệch với `String` của entity làm `ddl-auto: validate`
  chặn **toàn bộ** context test tích hợp (đã mất một buổi vì đúng một cột)
- `NUMERIC` cho mọi số đo và tiền — **cấm `float/double`**, ArchUnit chặn ở tầng Java
- `timestamptz`, lưu UTC

### 3.2. Entity ở `domain/`

```java
@Entity
@Table(name = "…")
@Audited(module = "cms", entityType = "ARTICLE")     // nhật ký tự động
public class Article extends ScopedEntity implements WorkflowAware {
```

- Kế thừa `BaseEntity` hoặc `ScopedEntity` (có phạm vi đơn vị thì bắt buộc `ScopedEntity`)
- Cài `WorkflowAware` nếu entity có trạng thái duyệt
- Trường nhạy cảm (🔒 trong spec) **không nằm ở bảng chính** — bảng riêng + `CryptoService`

### 3.3. Quy trình duyệt khai bằng DỮ LIỆU, không phải mã

Trạng thái và bước chuyển nằm ở `workflow_definitions` + `workflow_transitions` (seed bằng
migration), không phải `switch/case` trong service.

```java
article = workflowEngine.execute(article, "SUBMIT", "Gửi duyệt bài viết");

// Bước nào khai `requires_reason = TRUE` thì phải truyền lý do người dùng nhập.
article = workflowEngine.execute(article, "REQUEST_CHANGES", null, lyDo);
```

⛔ **Cấm gọi `applyState` hay `setStatus` trực tiếp.** Đi đường tắt là bỏ qua kiểm quyền, bỏ qua
bắn thông báo, bỏ qua ghi nhật ký — cả ba đều im lặng (quy tắc 4 của `CLAUDE.md`).

FE lấy danh sách nút từ `allowedActions()`, **không tự suy ra từ trạng thái**.

#### Bước chuyển đòi lý do — khai bằng cột, đừng khai bằng `if`

`workflow_transitions.requires_reason` là **một dòng dữ liệu, hai người đọc**: engine trả cờ ra cho
giao diện trong `AllowedAction` để nó mở ô nhập, và `execute()` ép buộc khi chuyển. Cùng đọc một
dòng nên hai bên không lệch nhau được.

⚠⚠ **Bẫy đã trả giá (24/8, §10.37).** Luật này từng khai cứng trong `ArticleController`
(`"REQUEST_CHANGES".equals(action) && blank(reason)`), còn giao diện đọc một cờ `requiresReason` mà
record `AllowedAction` **không có và không nơi nào điền**. Vế ép buộc đúng, vế quảng cáo hỏng, và
người duyệt bấm *"Yêu cầu chỉnh sửa"* thì **không có ô nào để nhập lý do, cũng không có đường đi
tiếp**. Bài kiểm HTTP có sẵn vẫn xanh vì nó gửi JSON dựng tay, không bao giờ chạm `allowedActions`.

- Thêm một bước đòi lý do = **một dòng `UPDATE`**, không phải sửa mã rồi deploy.
- Bản `execute` 3 tham số uỷ quyền với `reason = null` → bước đòi lý do mà quên truyền thì **ném
  `SYS-0003` ngay lượt gọi đầu**. Hỏng đóng, cố ý — không phải cửa lách.
- ⛔ Đừng thêm trường **trình bày** vào `AllowedAction` (màu nút, nút chính). Backend không biết gì
  về thẩm mỹ, và một trường chỉ có người đọc mà không có người ghi là một lỗi (quy tắc 15). Kiểu
  phía FE từng mang `primary`/`danger` như vậy — chưa nút nào từng đổi hình dạng.

### 3.4. Service ở `application/`

- `@Transactional` **chỉ** đặt ở đây, không ở controller/repository
- ⚠ **`@Transactional` trên phương thức TỰ GỌI trong cùng lớp không có tác dụng** — không đi qua
  proxy Spring. Cần ranh giới giao dịch bên trong một phương thức thì dùng `TransactionTemplate`
  như `JobService`/`BackupService` đang làm
- Entity **không ra khỏi tầng này** — controller chỉ nhận/trả `record` DTO

### 3.5. Controller ở `api/`

```java
@RequirePermission("cms:article:create")
@PostMapping
public ArticleResponse create(@Valid @RequestBody CreateArticleRequest request) { … }
```

**Mỗi phương thức controller phải có đúng một trong ba annotation**: `@RequirePermission`,
`@AuthenticatedEndpoint`, `@PublicEndpoint`. Thiếu cả ba → `DenyByDefaultTest` làm **CI đỏ**. Đây
là cố ý bắt khai báo tường minh: quên khai không bao giờ trở thành "mặc định cho qua".

Không tự bọc response — `ResponseEnvelopeAdvice` tự gói và gắn `traceId`.

### 3.6. Quyền phải được seed, không chỉ được khai

Mã quyền mới (`cms:article:create`) phải có dòng trong migration seed `permissions` **và**
`role_permissions`, nếu không thì annotation trỏ vào một mã không tồn tại và mọi request đều 403.
`RbacMatrixTest` đối chiếu với `function-spec.md` §6 trên DB thật.

### 3.7. Mã lỗi mới → sửa **hai** chỗ

Thêm vào `ErrorCode` (BE) **và** `frontend/admin-app/src/shared/error-map.ts`. Có bài kiểm canh sự
đồng bộ này — nó từng lệch 4 lần liên tiếp (31 → 36 → 43 → 49 mã) khi còn dựa vào trí nhớ.

### 3.8. Test — cái nào là bắt buộc

| Loại | Khi nào bắt buộc |
|---|---|
| Unit test tầng `domain` | Mọi quy tắc nghiệp vụ. Cổng bao phủ JaCoCo **chỉ soi gói `domain`** |
| Integration test (Testcontainers) | Mọi thứ chạm CSDL, đặc biệt truy vấn có phạm vi đơn vị |
| **Một** bài chạy thật qua ranh giới ra ngoài | Nếu có mock ở chỗ mã chạm CSDL/tệp/tiến trình con/mạng |

⚠⚠ **Mock đặt đúng chỗ mã chạm ra ngoài = chưa kiểm gì cả.** Đây là bài học đắt nhất của Phase 0:
`BackupServiceTest` mock `PostgresToolRunner` nên xanh trọn vẹn trong khi `pg_dump` **chưa từng
chạy được một lần nào** suốt ba work stream — vì thiếu một quyền trên CSDL. Mock chứng minh phần
điều phối; luôn phải có một bài đi qua thật.

---

## 4. Những cái bẫy đã trả giá rồi

Không cần đọc thuộc — chỉ cần biết chúng tồn tại để lúc gặp còn nhận ra.

| Bẫy | Triệu chứng |
|---|---|
| Đăng ký Hibernate listener sau khi app đã lên | `audit_logs` **trống rỗng**, không lỗi nào |
| `@Modifying` hàng loạt | Không đi qua bộ ghi nhật ký — Hibernate không nạp entity nên không có sự kiện |
| Thứ tự aspect quanh transaction | Bộ lọc phạm vi rơi vào `Session` tạm bị vứt đi → **mọi đơn vị đọc được dữ liệu của nhau**, không một dòng lỗi |
| Khai bean `DataSource`/`JdbcTemplate` | Spring Boot **ngừng** tạo bản chính; cả app chạy bằng vai trò CSDL sai |
| `data.quality = NGHI_NGO` | Mọi truy vấn báo cáo/alert/tổng hợp **phải lọc `HOP_LE`** — bẫy sai số liệu dễ mắc nhất (quy tắc 14) |
| `useRef` + `useEffect([])` để đo phần tử | Trang hiện khung xương trước thì thẻ **chưa vào DOM** lúc effect chạy, và deps rỗng nghĩa là không bao giờ chạy lại → bố cục kẹt ở giá trị mặc định. Dùng **ref dạng hàm** |
| Trả `0` cho ô số liệu **chưa có nguồn** | `0` là câu khẳng định "đã đo và bằng không". Phải trả rỗng kèm lý do, và ép ràng buộc đó ở **hàm dựng** chứ không ở lời dặn |
| Nguồn ngoài (tile bản đồ, font, ảnh) | CSP `default-src 'self'` chặn **im lặng** — không lỗi ở tầng ứng dụng. Đổi host trong `settings` thì phải mở CSP ở nginx, và phải có bài kiểm đối chiếu hai nơi |
| Lớp kiểm thử HTTP đăng nhập ở `@BeforeEach` | Hạn mức **theo IP** là ngân sách dùng chung cho cả lượt chạy — đăng nhập 30 lượt/15' **và API thường 100 lượt/phút**. Vượt trần thì đỏ ở một lớp *khác*, với `SYS-0002`. Hai việc phải làm: đăng nhập ở `@BeforeAll`, và `PhienHttp` gắn `X-Forwarded-For` riêng cho mỗi thực thể (mỗi lớp = một máy khách). ⛔ **Đừng nới hạn mức ở hồ sơ kiểm thử** — làm thế thì cơ chế đó không còn lượt chạy nào đi qua ở CI |
| Kiểm quy tắc nghiệp vụ **sau** `workflowEngine.execute(...)` | Không bao giờ chạy tới. Engine ghi một dòng thông báo, lượt ghi đó **flush** entity đang bẩn, và CHECK của CSDL bắn trước → người dùng nhận lỗi ràng buộc thô thay vì mã lỗi nghiệp vụ. Kiểm **trước**, tra đích đến bằng chính `allowedActions()` của engine |
| Backend **ép buộc** một điều kiện mà không **nói ra** cho giao diện | Hai vế của cùng một luật, và chúng hỏng độc lập. Bài kiểm chỉ đi vế ép buộc vẫn xanh trọn vẹn trong khi màn hình tắc hoàn toàn — đúng chuyện đã xảy ra với `requires_reason`: server đòi lý do, giao diện không có ô nào để nhập. Điều kiện nào chặn được người dùng thì phải có mặt trong payload mô tả hành động, **và có bài kiểm đi qua payload đó** |
| Khai một trường ở kiểu FE mà backend không gửi | TypeScript im lặng nếu trường đó `optional`, và nơi đọc nhận `undefined` **vĩnh viễn** — nhánh mã phụ thuộc vào nó không bao giờ chạy. Kiểu mô tả payload phải khớp record BE từng trường một; có `AllowedActionParityTest` canh cho `AllowedAction`, còn kiểu mới thì đối chiếu tay lúc chép |
| `updatedAt == null` để hỏi "chưa ai sửa" | Bộ ghi nhật ký của Spring Data đặt `@LastModifiedDate` ngay ở lượt **chèn** → điều kiện luôn sai, và công tắc dựa vào nó không mở cho ai. Dùng `version == 0` |
| Trả `null` trong một `record` DTO | Cấu hình `NON_NULL` chung **xoá hẳn khoá** khỏi JSON; phía nhận đọc ra `undefined`, không phân biệt được với "API đổi tên trường". Ô nào cố ý rỗng phải đè `@JsonInclude(ALWAYS)` |
| Dựng thân JSON của bài kiểm bằng `replace` chồng lên bản mặc định | Để lại **hai khoá cùng tên**; Jackson lấy khoá sau, tức là giá trị mặc định. Bài kiểm nhận một mã lỗi khác và ta đi tìm lỗi ở chỗ không có lỗi. Dựng bằng tham số |
| **Số hiệu migration mới nhỏ hơn số đã áp ở môi trường** | Flyway `validate` chặn lượt deploy: `Detected resolved migration not applied to database`. **Bộ test không bao giờ bắt được** — nó chạy từ CSDL rỗng, mà ở đó không tồn tại out-of-order. Chạy `make migration-order` trước khi mở PR |
| **Seed `UPDATE` một khoá do migration khác `INSERT`** | Số hiệu quyết định thứ tự. Chạy trước tệp tạo ra khoá thì `UPDATE` chạm **0 hàng**: không lỗi, không cảnh báo, ô cấu hình rỗng vĩnh viễn. Ràng buộc thứ tự phải ghi vào đầu tệp seed **và** có một bài kiểm nối hai vế bằng `JOIN` |
| **Đổi tên một tệp migration** | Maven copy tài nguyên **tăng dần**, không xoá tệp đã biến mất khỏi `target/classes` → Flyway thấy **hai** migration cùng số hiệu và chết lúc khởi động. Bắt buộc `./mvnw clean` sau mỗi lần đổi tên hoặc đổi số hiệu migration |
| `@Generated` thiếu `insertable = false, updatable = false` | Bản ghi trả về sau khi tạo mới bị **rỗng** ở cột đó dù CSDL đã tính xong (do Hibernate không nhả để tự đọc lại). Giao diện hiển thị ô trống, người dùng F5 thì có — loại lỗi mất thời gian truy vết nhất |
| Migration thiếu cột chuẩn của `BaseEntity`/`ScopedEntity` | Bảng mới kế thừa `BaseEntity` nhưng migration chỉ có `created_at`, `created_by` mà **thiếu `public_id`, `deleted_at`, `updated_at`, `updated_by`, `version`**. Hibernate schema-validation phát hiện ở CI nhưng **lỗi nằm ở tên cột**, không ở entity → truy vết sai hướng. Khi tạo bảng mới, đối chiếu migration với **tất cả** cột của lớp cha (`BaseEntity`: 7 cột, `ScopedEntity`: 8 cột) |
| Controller trả entity JPA thay vì record DTO | ArchUnit (`endpointsExposeDtosOnly`) bắt nhưng nếu chưa có luật thì **lộ field nội bộ** (`passwordHash`, quan hệ lazy bung ngoài transaction). Mỗi cột mới thêm vào bảng lặng lẽ trở thành một phần hợp đồng API. Luôn trả `record` DTO, dùng factory method `from(Entity)` |
| `process.env.X ?? 'mặc định'` ở frontend | **"Rỗng" khác "chưa đặt"**. Docker `ARG` không truyền vẫn khiến `ENV` gán **chuỗi rỗng**, mà chuỗi rỗng không nullish → mặc định không bao giờ chạm tới. Đã giết một lượt `next build` bằng `new URL('')`. Dùng `||` cho **mọi** hằng số đọc từ env, và kiểm bằng cách nạp lại module với biến rỗng — đừng grep toán tử |
| Tin `make ci-local` xanh là CI sẽ xanh | Lượt build ở máy **luôn nạp `.env.local`**, runner thì không. Biến môi trường rỗng là trạng thái chỉ tồn tại trên runner. Đụng `Dockerfile`, `ci.yml` hay hằng số đọc env thì chạy thêm `make ci-image` |
| **Xoá mềm một bản ghi mà bảng khác `REFERENCES … ON DELETE SET NULL`** | Ràng buộc ấy **không bao giờ bắn**: xoá mềm là `UPDATE deleted_at`, nên với CSDL ⛔ không có gì bị xoá. Quy tắc 9 và luật ràng buộc của CSDL **loại trừ nhau**, và mỗi vế nhìn riêng đều đúng. Đo 04/09: **5 cột ở 2 module** ở trạng thái ấy — cổng dựng một liên kết tải về trả **404 câm** trong khi màn hình quản trị vẫn hiện tài liệu "đã khai". Gỡ tham chiếu bằng **sự kiện trong cùng giao dịch** (`AttachmentDeletedEvent`) |
| Phép đếm **"còn ai dùng không"** chạy qua repository **có lọc phạm vi** | Câu trả lời phụ thuộc **người đang hỏi**: XN A đếm ra 0 trong khi XN B còn 12 bản ghi trỏ vào ⇒ ràng buộc toàn vẹn ⛔ không bắn, và **người gây ra ⛔ không nhìn thấy hậu quả** vì nó ngoài phạm vi họ. *"Đối tượng này còn được tham chiếu không"* có **một** câu trả lời đúng cho cả hệ — dùng `nativeQuery` (`@Filter` của Hibernate ⛔ không áp cho SQL thuần) |
| Endpoint **chung** và endpoint **module** cùng làm một việc, gác hai quyền khác nhau | Đường **rộng hơn** là đường ⛔ không ai canh. `DELETE /api/v1/attachments/{id}` gác `ops:document:upload` trong khi hai đường riêng gác `:delete` ⇒ ba vai trò xoá được bất kỳ tệp nào. ⚠ Tầng 1 (menu) và tầng 2 (nút) **đều đúng**, nên ⛔ không màn hình nào lộ ra gì — chỉ tầng 3 sai, và tầng 3 là tầng **duy nhất** thật sự chặn |
| Tuyến mở bằng quyền **XEM**, nút Lưu gọi endpoint đòi quyền **GHI** | Người dùng mở màn hình → gõ xong việc → bấm Lưu → **403**, ⛔ không màn hình nào giải thích được, và họ báo lại là *"hệ thống lỗi"*. ⛔ Đừng siết tuyến lên quyền ghi (mất luôn quyền xem hợp lệ) — cho **tầng 2 nói thật**: `disabled` + `title` nêu đúng mã quyền còn thiếu |
| `tsc --noEmit -p <app>/tsconfig.json` khi tsconfig là tệp **solution** | `"files": []` + `references` ⇒ biên dịch **đúng 0 tệp**, đọc đúng cấu hình, **thoát 0**. Đã báo xanh trên một tệp thiếu hẳn một trường bắt buộc. Chỉ `tsc -b` mới đi theo `references` — dùng `npm run typecheck`, ⛔ đừng gõ `tsc` tay |
| `./mvnw -pl <module> test` **thiếu `-am`** | Maven dùng **jar CŨ** của module khác trong repo local ⇒ một lượt "phá rồi thử" báo **XANH** vì bản hỏng chưa từng được nạp. Mọi lượt kiểm chứng ngược chạm module khác bắt buộc `-am` (luật 10) |
| `-Dtest=<Lớp>` trên lớp có `@Nested` | Dòng tổng của `.txt` surefire ghi `Tests run: 0` trong khi XML liệt đủ testcase, và Maven **thoát 0**. Đọc **XML**, hoặc đếm `ls target/surefire-reports/*<Tên>*.txt \| wc -l` |
| Thêm tham số vào hàm dựng một service có `@InjectMocks` | `test-compile` **XANH** (Mockito nối bằng phản chiếu), rồi cả lớp đổ vỡ lúc chạy với `NullPointerException`. §10.70 ở dạng nhỏ nhất: *"biên dịch được" ⛔ KHÔNG phải "qua cổng kiểm"* — thiếu `@Mock` thì chỉ `make ci-local` mới thấy |
| `rs.getObject(cot, Instant.class)` trên cột `timestamptz` | Trình điều khiển PostgreSQL **ném** (⛔ không khai phép đổi ấy), và ngoại lệ bị dịch thành `SYS-0005`/409 nên triệu chứng ⛔ không hề trỏ vào dòng mã sai. Đọc qua `OffsetDateTime` rồi `.toInstant()` |
| Bộ canh dạng **quét văn bản** cho một khái niệm của **mã** | Sai được **cả hai chiều**, và đã sai cả hai trong một ngày: mẫu **quá rộng** đếm một dòng javadoc thành "có nơi gọi"; mẫu **quá hẹp** ⛔ không thấy `hasPermission(a ? 'x' : 'y')` rồi báo đỏ trên mã đúng, và ⛔ không thấy `INSERT` xuống dòng sau `(` nên bắt được 8/11 khoá. Canh **cấu trúc** (ràng buộc import · đối số của lời gọi · dòng khai `^TÊN=`), và luôn kèm một khẳng định **về số lượng** — vế duy nhất ⛔ không chia sẻ giả định nào với mẫu regex |
| `ORDER BY <cột path>, sort_order` trên cây materialized path | Vế `sort_order` **không bao giờ được so tới**: path chứa id của chính nút nên hai anh em không thể trùng path. Núm sắp thứ tự ở màn hình quản trị trả **204 thành công** và cổng ⛔ không đổi gì. Và thứ tự thật là thứ tự id **so theo CHUỖI** ⇒ `/10/` trước `/9/`, mục mới rơi vào **GIỮA** danh sách. Đi qua `MaterializedPath.sortForDisplay` (§11.12) |
| Phép sắp/định dạng đặt trên đường **đọc** mà ném với dữ liệu dở dang | Ba service lưu **hai bước** (INSERT lấy id → ghi path thật), nên có một trạng thái **hợp lệ theo thiết kế** mà lượt đọc trong cùng giao dịch nhìn thấy. Một phép sắp **hiển thị** ném ở đó là làm gãy đường **GHI**. Nới ở đường đọc, siết ở đường ghi — ⛔ không nới cả hai (§11.12) |
| Bộ canh quét **theo DÒNG** một chú giải mà bộ định dạng có thể ngắt | Spotless ngắt `@DisplayName` xuống dòng khi chuỗi dài ⇒ bộ canh mất dấu và báo *“không có bài kiểm”* trong khi bài vẫn nằm nguyên. Một bộ canh mà **bộ định dạng mã** làm cho sai sẽ đỏ giả vào ngày không ai đoán trước, và lượt sửa rất dễ thành *nới cho hết đỏ*. Quét theo **khối chú giải**, đừng quét theo dòng (§11.13) |
| `git checkout -- <tệp>` để khôi phục sau khi phá có chủ đích | **Im lặng thất bại trên tệp chưa track** — bản khôi phục chưa từng được nạp, và trong một chuỗi `&&` thì dòng in số đo phía sau cũng không chạy. Luật 10 có **hai** vế: xác nhận bản *phá* đã nạp, **và** bản *khôi phục* cũng vậy. In một con số đếm được ở cả hai đầu (§11.15) |
| `git diff` để chứng minh **đã khôi phục** một tệp | Trên tệp **chưa track**, `git diff` **luôn rỗng** — kể cả khi bản phá còn nguyên. Nó ⛔ không phải phép đo khôi phục; chỉ `grep -c` trên chính chuỗi vừa sửa mới là (§11.17) |
| Công cụ đọc **đường dẫn tương đối** (`os.getcwd()`, `./x`) | Nó giải theo cây của **TIẾN TRÌNH**, ⛔ không phải cây bạn đang gõ lệnh. Với `git worktree` đó là **hai tệp khác nhau**, và đầu ra vẫn báo thành công. Bắt công cụ **in ra đường dẫn tuyệt đối** trong *câu trả lời*, ⛔ không chỉ trong log (§11.16, luật 34) |
| Bộ canh neo vào **đối số** của một lời gọi (`f(os.path.join`) | Gãy ngay lượt ai đó **bóc đối số ra biến** — dù bất biến nó canh ⛔ không hề đổi. Một bộ canh hỏng vì mã được **dọn dẹp** là một bộ canh đang canh văn bản. Neo vào lời gọi `f(` (luật 2, §11.16) |
| Hằng số **thời gian ghim** trong bài kiểm | Đặt sai ⇒ mọi "tuổi" ra số **ÂM**, và khi ấy **chỉ nhánh xa nhất ĐỎ** — các nhánh gần **xanh giả**. Cách sửa rẻ nhất lúc ấy là *nới ngưỡng cho hết đỏ*, tức tháo bộ canh. Đối chiếu mốc bằng một phép tính **nguồn khác** (`java.time` vs `date` của shell — luật 29, §11.17) |
| `\`` trong chuỗi **nháy đơn** của `printf` | ⛔ Không phải escape — nó in ra **cả dấu gạch chéo**, nên thân thông báo hiện `` \`x\` `` thay vì mã nguồn. Nháy đơn đã bảo vệ nháy ngược rồi, để **trần**. Đo bằng `od -c`, ⛔ đừng đọc bằng mắt (§11.17) |
| Hai cái chuông cùng kêu cho **một** trạng thái | Mỗi sự cố sinh hai thông báo ⇒ người nhận ngừng đọc cả hai. Hai chuông phải phủ **hai tập rời nhau**, và ranh giới ấy cần một bài kiểm đích danh (§11.17, T11.84) |
| Bộ canh nằm **trong chính** thứ nó canh | Workflow/tệp ấy vỡ thì mọi thứ **bên trong** cũng chết — kể cả phần đi báo rằng nó chết. Bộ canh cho *sự vắng mặt* phải sống ở **nhà khác**, và có bài kiểm khẳng định điều đó (§11.17) |
| Vòng lặp đọc tệp có **trần số dòng** | `for (…; i < n && ket.size() < TRAN; i++)` **cắt cụt trong im lặng**, và phép đếm sau đó đếm phần đã cắt ⇒ báo cáo nói *"thành công N dòng"* cho một tệp lớn hơn N. Chạm trần thì **NÉM**, và nêu **số dòng như người dùng thấy trong Excel** (§11.18, T42.4) |
| `accept=` của ô chọn tệp | Nó là một **lời hứa**, và nó lệch khỏi bộ đọc rất êm: nhận `.xls` (OLE2) mà bộ đọc nhận diện XLSX bằng chữ ký ZIP ⇒ tệp đúng bị báo *"thiếu cột bắt buộc"*; chặn `.csv` mà bộ đọc xử lý đầy đủ. Đối chiếu với **magic bytes** bộ đọc dùng, ⛔ đừng đối chiếu với tên định dạng (§11.18) |
| Chữ *"đúng biểu mẫu"* trên giao diện | Phải có một **nút tải mẫu**, và mẫu ấy phải **sinh từ chính hằng số bộ đọc dùng** — tệp tĩnh trong `public/` sẽ lệch vào ngày ai đó thêm cột (luật 14). ⭐ Mẫu là *tiêu đề + dòng MÔ TẢ*, ⛔ không phải dòng ví dụ hợp lệ: mẫu hợp lệ tải về rồi nhập lại sẽ **im lặng tạo hồ sơ rác** (§11.18, T42.6) |
| Bản kết xuất **sai nội dung** | Nó trông y hệt bản đúng — có tiêu đề, có dữ liệu, mở được trong Excel. Chỉ một phép **đối chiếu với đặc tả, ở dạng bài kiểm** mới phân biệt được. BC-13 xuất nhầm bảng suốt từ T34.7 vì bài end-to-end duy nhất chỉ xuất BC05 (§11.18, luật 28) |
| Một DTO mang **hai** danh sách | Nơi kết xuất rất dễ chỉ dùng một, và cái kia thành *0 nơi gọi* mà `tsc`/`javac` ⛔ không thấy. Kiểm bằng cách hỏi *"ai gọi getter thứ hai"*, ⛔ đừng hỏi *"DTO có đủ trường không"* (§11.18) |
| CSV nhiều **khối** trong một tệp | `BangCsv` **ném** khi dòng lệch số cột, và nó đúng: CSV lệch cột vẫn mở được trong Excel, chỉ đẩy dữ liệu sang ô bên cạnh — im lặng. Hai khối khác bề rộng thì **đệm cho đủ lưới**, ⛔ đừng nới bộ canh (§11.18) |
| Nới một bộ canh vì thực tế đổi | Phải thay bằng thứ **CHẶT HƠN**, ⛔ không phải bớt đi. *"Mọi thứ NULL"* hết đúng ⇒ đổi sang **ghim từng ô**, kể cả các ô vẫn NULL — *"13 dòng có giá trị"* vẫn xanh khi ai đó xoá một dòng rồi điền dòng khác (§11.18, T42.3) |
| Cắt ngày trên `timestamptz` | `?::timestamptz` cắt theo múi giờ **phiên**. Dùng `hyd_dau_ngay_vn()` — **một chỗ duy nhất** trong hệ biết `Asia/Ho_Chi_Minh`; cắt theo UTC đẩy **42/144 khung mỗi ngày** sang hôm trước, im lặng (T42.10) |
| Đếm bảng con trong câu có `GROUP BY` | JOIN vào cùng khối sẽ **nhân bản** mọi hàng của nhóm ⇒ `sum()` và trung bình sai mà ⛔ không có gì báo. Dùng **subquery tương quan** trong `SELECT` (T42.10) |
| Ô "chờ khách hàng" trong sổ nợ | Nó **chuyển trách nhiệm ra ngoài**, nên ⛔ không lượt rà nào mở nó ra nữa — `⛔ không làm được` và `⛔ chưa làm` đọc giống hệt nhau. Mỗi mục chờ khách phải kèm **danh sách việc của TA** trong cùng mục (§11.18) |
| Hai nguồn dữ liệu của **cùng một** khách | Có thể lệch nhau đúng một dòng, và chữ *"đã chốt"* trong chú thích ⛔ không làm dữ liệu đúng lên. Lấy bản nhất quán hơn, **ghi lý do vào migration, và hỏi lại khách** — ⛔ đừng im lặng chọn một bên (§11.18, T42.2) |
| `App.useApp()` trong bài kiểm | Ngoài `<App>` của AntD nó trả **đối tượng rỗng** ⇒ `message.error` ném `TypeError` **bên trong `onError` của mutation**, tức một unhandled rejection ở nơi khác hẳn chỗ đọc kết quả. Bọc `<App>` trong hàm dựng của bài kiểm (T42.8) |
| Khẳng định trên **con số trong thông điệp lỗi** | Bộ định dạng nhóm hàng nghìn kiểu Việt Nam: `5002` in ra `5.002`. Bỏ dấu nhóm rồi mới so — khẳng định về **con số**, ⛔ không về **định dạng** (T42.12) |
| Đoán mã HTTP của một `ErrorCode` | *"Lỗi hợp lệ hoá thì phải 422"* — `SYS-0003` khai `BAD_REQUEST`. Đọc `ErrorCode.java`, và khẳng định theo **mã lỗi** thay vì theo con số HTTP (T42.12) |
| Thân `PUT` dựng tay trong bài kiểm | `PUT` là **thay-toàn-phần**: trường thiếu bị XOÁ. Bài kiểm dùng chung một CSDL ⇒ mỗi lượt ghi là tác dụng phụ lên lớp khác. Gửi **trọn** trạng thái đang có, ⛔ đừng gửi phần mình quan tâm (§11.19) |
| Một khuyết tật trên cột **vốn đang NULL** | Nó ⛔ không có triệu chứng nào cho tới ngày cột ấy có giá trị — tức đúng ngày dữ liệu thật về, đúng lúc mất nó đắt nhất. Trước khi seed dữ liệu thật vào một cột, hỏi *"đường ghi nào chạm cột này, và nó có giữ được không"* (§11.19) |
| Bài kiểm **xanh ở máy, đỏ trên CI** | Nghi **thứ tự chạy** trước khi nghi mã: surefire xếp lớp theo thứ tự hệ tệp, macOS và Linux ngược nhau. ⇒ **`make ci-order`** — chạy lại ở một thứ tự lớp KHÁC. ⛔ Đừng chạy một lớp đơn độc: đó chính là cách phụ thuộc thứ tự trốn thoát (§11.19) |
| Bộ canh cho một giá trị "phải giữ nguyên" | Phải có vế **"dữ liệu mốc ĐANG CÓ giá trị"**, nếu không nó xanh trong đúng tình huống nó sinh ra để bắt — khi khuyết tật còn đó thì mốc đã bị xoá trắng từ trước (luật 7, §11.19) |
| Nguồn ngoài trả **HTTP 200** cho khoá sai | `bhh40` trả `not.working` + 200, ⛔ không phải 401. Đọc "rỗng" thành "hôm nay ⛔ không có số" là đánh mất dữ liệu **vĩnh viễn** (quy tắc 18). Kiểm cờ hỏng **TRƯỚC** khi tách dòng, và có bài kiểm cho cả hai trạng thái (T42.17) |
| Một tiện ích nhập/xuất đặt trong module nghiệp vụ | Module ⛔ không import nhau (luật 6) ⇒ module thứ hai cần nó sẽ **chép một bản gần giống**, và bản chép lệch ở nhánh ít chạy nhất (thường là nhánh hiện lỗi). Đặt ở `core/common/`, và mã lỗi của nó mang tiền tố `SYS` chứ ⛔ không phải tiền tố module (T42.19) |
| Ô trống trong tệp nhập | Phải chọn **một** nghĩa và nói ra: *giữ nguyên* hay *xoá*. Tệp thường lập từng phần, nên hiểu ô trống là "xoá" khiến lượt nhập thứ hai **xoá mất** thứ lượt đầu vừa điền — im lặng (T42.20) |
| Nhập hàng loạt theo một khoá **bất biến** | Mã ⛔ không khớp phải là **lỗi dòng**, ⛔ đừng tạo bản ghi mới: một mã gõ sai sẽ lặng lẽ sinh ra bản ghi ma ⛔ không bao giờ có dữ liệu, và ⛔ không ai biết nó từ đâu ra (T42.20) |
| Bài kiểm ghi vào bảng có bộ canh ghim từng ô | Khôi phục trong `finally`, và **đếm số hàng chạm** khi khôi phục. Để lại dữ liệu thừa là làm đỏ một lớp khác vì lý do ⛔ không liên quan (T42.20) |

Chi tiết nguyên nhân: `architecture-review.md` §9.7, §9.8, §9.12, §10.33, §10.38.

---

## 5. Trước khi mở PR

```bash
cd backend && ./mvnw verify      # test + ArchUnit + Spotless + Checkstyle + cổng bao phủ
cd frontend && npm run lint && npm test
make branch-check                # nhánh có lỗi thời sau squash merge không
```

Rồi tự hỏi ba câu:

1. **Có cơ chế canh gác nào tôi vừa thêm không?** Nếu có, đã có bài kiểm chứng minh nó *bắt được*
   vi phạm chưa — hay chỉ chứng minh nó không đỏ? (`conventions.md` §1.5)
2. **Có chỗ nào tôi mock đúng ranh giới chạm ra ngoài không?** Nếu có, đã có một bài chạy thật chưa?
3. **Tôi có sửa file test kiến trúc để mã của mình chạy được không?** Nếu có, gần như chắc chắn là
   đang đi sai đường — dừng lại và hỏi.

Định nghĩa Hoàn thành đầy đủ cho mỗi PR: `conventions.md` §5.

## 10. Task Tracking (SSoT)

- Mọi thao tác cập nhật tiến độ, đánh dấu hoàn thành `[x]`, hoặc thêm take note đều CHỈ ĐƯỢC PHÉP thực hiện tại file `.claude/master-tracking.md`.
- Tuyệt đối tuân thủ quy tắc tổng hợp: xóa bỏ mọi icon, markdown thừa; mô tả vắn tắt đúng trọng tâm hành động; giới hạn ghi chú.
- Các file phase cũ (phase0, phase1...) chỉ là tài liệu lưu trữ, cấm sửa đổi.
