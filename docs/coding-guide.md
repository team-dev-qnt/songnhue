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
| **Đổi tên một tệp migration** | Maven copy tài nguyên **tăng dần**, không xoá tệp đã biến mất khỏi `target/classes` → Flyway thấy **hai** migration cùng số hiệu và chết lúc khởi động. Bắt buộc `./mvnw clean` sau mỗi lần đổi tên hoặc đổi số hiệu migration |
| `@Generated` thiếu `insertable = false, updatable = false` | Bản ghi trả về sau khi tạo mới bị **rỗng** ở cột đó dù CSDL đã tính xong (do Hibernate không nhả để tự đọc lại). Giao diện hiển thị ô trống, người dùng F5 thì có — loại lỗi mất thời gian truy vết nhất |
| Migration thiếu cột chuẩn của `BaseEntity`/`ScopedEntity` | Bảng mới kế thừa `BaseEntity` nhưng migration chỉ có `created_at`, `created_by` mà **thiếu `public_id`, `deleted_at`, `updated_at`, `updated_by`, `version`**. Hibernate schema-validation phát hiện ở CI nhưng **lỗi nằm ở tên cột**, không ở entity → truy vết sai hướng. Khi tạo bảng mới, đối chiếu migration với **tất cả** cột của lớp cha (`BaseEntity`: 7 cột, `ScopedEntity`: 8 cột) |
| Controller trả entity JPA thay vì record DTO | ArchUnit (`endpointsExposeDtosOnly`) bắt nhưng nếu chưa có luật thì **lộ field nội bộ** (`passwordHash`, quan hệ lazy bung ngoài transaction). Mỗi cột mới thêm vào bảng lặng lẽ trở thành một phần hợp đồng API. Luôn trả `record` DTO, dùng factory method `from(Entity)` |
| `process.env.X ?? 'mặc định'` ở frontend | **"Rỗng" khác "chưa đặt"**. Docker `ARG` không truyền vẫn khiến `ENV` gán **chuỗi rỗng**, mà chuỗi rỗng không nullish → mặc định không bao giờ chạm tới. Đã giết một lượt `next build` bằng `new URL('')`. Dùng `||` cho **mọi** hằng số đọc từ env, và kiểm bằng cách nạp lại module với biến rỗng — đừng grep toán tử |
| Tin `make ci-local` xanh là CI sẽ xanh | Lượt build ở máy **luôn nạp `.env.local`**, runner thì không. Biến môi trường rỗng là trạng thái chỉ tồn tại trên runner. Đụng `Dockerfile`, `ci.yml` hay hằng số đọc env thì chạy thêm `make ci-image` |

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
