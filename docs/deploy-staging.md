# Triển khai STAGING — kế hoạch thực thi

> **Đây là thứ tự làm việc cho riêng staging.** Các bước dựng máy dùng chung với production nằm ở
> `docs/deploy-guideline.md` §2–§4 — tài liệu này **trỏ sang**, không chép lại.
> Mua gì và vì sao: `hosting_recommendations.md`. Luồng CI/CD: `docs/cicd.md`.

---

## 0. Bốn lỗi chặn đường đã vá trước khi viết tài liệu này (24/8)

Rà lại đường triển khai staging bằng **mã thật** thay vì bằng bản ghi tiến độ, và tìm ra bốn chỗ mà
làm theo tài liệu cũ sẽ hỏng — cả bốn đều hỏng **im lặng hoặc rất muộn**:

| # | Lỗi | Nếu không vá thì |
|---|---|---|
| 1 | Smoke test của CD hỏi `$BASE_URL/actuator/health/readiness` — nginx biên **không định tuyến `/actuator`** | Lượt deploy đầu tiên **đỏ sau đúng 5 phút chờ**. Đo thật: public-web trả `404`, admin-app trả `200` kèm **trang HTML của SPA** — trường hợp sau nguy hiểm hơn vì `curl -f` đi qua |
| 2 | 6 biến `compose.prod.yml` cần **không có trong tệp env mẫu nào** | Viết dạng `${TÊN}` không `:?` → thiếu là **chuỗi rỗng, không báo lỗi**: `server_name` rỗng, MinIO chạy tài khoản mặc định, `/api/revalidate` trả 503 |
| 3 | `GRAFANA_ADMIN_PASSWORD` không được nhắc ở đâu, mà lại viết `${…:?}` | `docker compose -f compose.observability.yml up -d` **dừng ngay**, không container nào lên |
| 4 | Ba service giám sát **không có `mem_limit` nào** | Trên máy 4 GB chạy chung với staging, Prometheus lớn dần tới lúc OOM-killer chọn `app` hoặc `postgres` |

Đã vá cả bốn, và thêm `ComposeEnvCompletenessTest` canh cho lỗi #2 tái phát — **có kiểm chứng
ngược**: bỏ `REVALIDATE_SECRET` khỏi tệp mẫu → bài kiểm đỏ đúng tên biến đó.

---

## 1. ✅ Cấu hình VPS-2 — ĐÃ CHỐT: 2 vCPU / 8 GB / 80 GB

Bản đầu của `hosting_recommendations.md` chốt VPS-2 = 2 vCPU / **4 GB**. Con số đó có từ lúc gộp
VM-3 (sao lưu + giám sát) vào VPS-2 để tiết kiệm — nhưng **ngân sách bộ nhớ không được tính lại sau
khi gộp**. Dưới đây là phép cộng dẫn tới quyết định nâng lên 8 GB.

Cộng đúng những gì `compose.staging.yml` và `compose.observability.yml` khai:

| Thành phần | Trần bộ nhớ | Ghi chú |
|---|---:|---|
| postgres | 1.024 MB | `compose.staging.yml` hạ từ 2 GB |
| app | 1.536 MB | hạ từ 3 GB |
| minio | 512 MB | hạ từ 1 GB |
| public-web | 384 MB | hạ từ 512 MB |
| admin-app | 256 MB | **kế thừa production, không hạ** |
| nginx | 256 MB | **kế thừa production, không hạ** |
| **Cộng stack staging** | **3.968 MB** | |
| prometheus · grafana · node-exporter | 928 MB | trần mới đặt 24/8; trước đó **không giới hạn** |
| **Cộng cả máy** | **4.896 MB** | |
| Hệ điều hành + Docker daemon | ~450–600 MB | |
| **Tổng thực tế** | **≈ 5,4 GB** | |

**Trên máy 4 GB con số này không vừa, và nó hỏng theo kiểu tệ nhất.** Hết bộ nhớ thì OOM-killer chọn
tiến trình có RSS lớn nhất — gần như luôn là `app` hoặc `postgres`. `app` có
`-XX:+ExitOnOutOfMemoryError` nên JVM thoát, `restart: unless-stopped` dựng lại, và triệu chứng bên
ngoài là **"staging chập chờn"** chứ không phải một lỗi đọc được. Nguy hiểm hơn: VPS-2 **cũng là nơi
giữ bản sao lưu**; một lượt OOM lúc 03:00 làm hỏng lượt kéo về mà không ai biết.

### ✅ Chốt: phương án A — 2 vCPU / **8 GB** / 80 GB, Ubuntu 24.04 LTS

Quyết định ngày 24/8. Hai phương án còn lại giữ lại đây để về sau còn biết vì sao **không** chọn:

| | Cấu hình | Vì sao không chọn |
|---|---|---|
| B | 6 GB + hạ tiếp trần staging | Vừa đủ, **không còn biên**. Lượt `pg_restore` khi diễn tập khôi phục ăn thêm bộ nhớ đúng lúc cả hai stack đang chạy — và diễn tập khôi phục là mục nghiệm thu bắt buộc (DOD0.14), không phải việc tuỳ chọn |
| C | Giữ 4 GB, chuyển giám sát sang VPS-1 | Phá đúng lý do dựng ra nó: **giám sát phải sống khi production chết**. Đặt chung máy với thứ mình canh gác là canh gác vô nghĩa |

**8 GB để lại biên bao nhiêu:** 8.192 − 5.408 = **2.784 MB**. Biên đó không thừa, nó có ba việc cụ
thể — `pg_restore` lúc diễn tập khôi phục · bộ đệm trang của Postgres (thứ quyết định staging chạy
nhanh hay ì) · và lượt `docker compose pull` giữ đồng thời image cũ lẫn image mới.

> 📌 Vì đã chọn A nên **không sửa `compose.staging.yml`**. Trần bộ nhớ hiện tại giữ nguyên — staging
> càng giống production càng tốt, và mỗi lần hạ trần là một khác biệt nữa phải nhớ.

### Đĩa và CPU

* **80 GB đủ cho năm đầu**: image Docker ≈ 2 GB (giữ 168h bản cũ) · postgres + minio staging vài GB ·
  kho sao lưu kéo từ production là phần lớn dần nhất. Đặt cảnh báo khi đĩa > 75%.
* **2 vCPU đủ.** Staging không có tải. Nút thắt duy nhất là lượt `pg_restore` khi diễn tập khôi phục,
  và chậm hơn vài phút ở staging không phải vấn đề.

---

## 2. Tên miền — mua ở đâu, mua thế nào

### 2.1. Đếm trước đã: chỉ phải mua **một** tên miền

Sáu địa chỉ mà hệ thống cần, cho **cả hai** môi trường:

| Địa chỉ | Trỏ tới | Phải mua? |
|---|---|---|
| `songnhue.vn` + `www` | VPS-1 | ✅ đây là thứ duy nhất mất tiền |
| `admin.songnhue.vn` | VPS-1 | miễn phí — tên miền phụ |
| `files.songnhue.vn` | VPS-1 | miễn phí |
| `staging.songnhue.vn` | VPS-2 | miễn phí |
| `admin-staging.songnhue.vn` | VPS-2 | miễn phí |
| `files-staging.songnhue.vn` | VPS-2 | miễn phí |

**Tên miền phụ (subdomain) không phải mua** — có tên miền gốc là tự tạo bao nhiêu cũng được, chỉ là
thêm một bản ghi DNS. Đây là lý do "tên miền riêng cho staging" nằm ở mục *không được cắt* mà vẫn
không tốn thêm đồng nào.

> ⛔ **Đừng mua một tên miền riêng cho staging** (kiểu `songnhue-staging.vn`). Vừa tốn tiền, vừa phải
> gia hạn hai chỗ, vừa mất đúng thứ đang cần kiểm: cookie và chữ ký presigned phụ thuộc vào **quan hệ
> giữa các tên miền cùng gốc**. Staging phải có **cùng hình dạng** với production, không phải cùng
> tên.

### 2.2. Chọn đuôi nào: `.vn` hay `.com.vn`

| Đuôi | Nhận xét |
|---|---|
| **`.vn`** ⭐ | Ngắn, uy tín nhất trong nước, ai cũng nhận ra là tổ chức Việt Nam. Phí duy trì hằng năm **cao hơn** `.com.vn` |
| `.com.vn` | Rẻ hơn, vẫn hoàn toàn chính danh. Nhiều doanh nghiệp nhà nước đang dùng |
| `.gov.vn` | ⛔ **Không đủ điều kiện.** Dành riêng cho **cơ quan nhà nước**. Công ty TNHH MTV là *doanh nghiệp* nhà nước, không phải cơ quan nhà nước — hai tư cách khác nhau |
| `.com` quốc tế | ⛔ Không nên là tên miền chính. Cả lập luận "đặt máy trong nước, đứng tên Công ty" ở `hosting_recommendations.md` §3 mất một nửa sức thuyết phục nếu tên miền đăng ký ở nước ngoài dưới tên cá nhân |

**Khuyến nghị: mua `.vn`**, và nếu ngân sách cho phép thì đăng ký thêm `.com.vn` **để giữ chỗ** (trỏ
chuyển hướng về `.vn`) — chặn người khác lấy mất một tên gần giống tên Công ty.

> ⚠ **Kiểm tra tên còn trống trước khi làm gì khác.** Tra ở `https://whois.vnnic.vn` hoặc ngay trên
> trang của nhà đăng ký. Nếu `songnhue.vn` đã có người giữ, phải chốt phương án tên thay thế **trước
> khi** điền hồ sơ — tên miền đi vào `.env`, vào chứng chỉ TLS, vào chữ ký presigned và vào cấu hình
> nginx, nên đổi tên sau khi đã dựng là dựng lại kha khá thứ.

### 2.3. Mua ở đâu — không mua thẳng từ VNNIC được

Tên miền `.vn` do **VNNIC** (Trung tâm Internet Việt Nam) quản lý, nhưng VNNIC **không bán trực
tiếp cho người dùng cuối**. Phải đi qua một **Nhà đăng ký tên miền `.vn`** được VNNIC công nhận.

Một số nhà đăng ký lớn, đang hoạt động lâu năm:

| Nhóm | Tên |
|---|---|
| Nhà mạng | **Viettel** · **VNPT** · **FPT Telecom** |
| Chuyên tên miền / hosting | **PA Việt Nam** · **Mắt Bão** · **iNET** · **Nhân Hoà** · **Tenten (GMO)** · **BKNS** |

> ⚠ **Danh sách chính thức và đầy đủ nằm ở `vnnic.vn`** — mục *Nhà đăng ký tên miền ".vn"*. Danh
> sách có thay đổi theo thời gian; tra ở đó trước khi chọn, đừng tin danh sách trong tài liệu này là
> đã cập nhật.

**Ba tiêu chí chọn, theo thứ tự quan trọng:**

1. **Có xuất hoá đơn VAT cho Công ty.** Đây là tiêu chí bắt buộc, không phải tiêu chí "nếu có thì
   tốt" — không có hoá đơn thì Công ty không thanh toán được, và cả chuỗi lập luận "tài sản của Công
   ty" ở `hosting_recommendations.md` §3 gãy ngay ở khâu chứng từ.
2. **Cho phép tự quản lý bản ghi DNS và tự đổi nameserver.** Cần để trỏ sang Cloudflare ở §2.6. Đa
   số nhà đăng ký lớn đều cho; hỏi trước vẫn hơn.
3. **Giá duy trì hằng năm**, không phải giá năm đầu. Nhiều nơi khuyến mãi năm đầu rồi thu đủ từ năm
   thứ hai.

> 📌 **Gộp luôn với nhà cung cấp VPS nếu tiện** (Viettel IDC, VNPT, FPT đều vừa bán VPS vừa là nhà
> đăng ký) — một đầu mối, một hoá đơn, một chỗ để gọi khi có việc. Nhưng đừng đánh đổi ba tiêu chí
> trên chỉ để gộp.

### 2.4. ⛔ Chủ thể phải là **Công ty** — đây là phần dễ làm sai nhất

Khi điền hồ sơ, nhà đăng ký sẽ hỏi tên miền đăng ký cho **cá nhân** hay **tổ chức**. Phải chọn
**tổ chức**, và tổ chức đó là Công ty TNHH MTV Đầu tư Phát triển Thuỷ lợi Sông Nhuệ.

**Chuẩn bị sẵn trước khi ngồi điền** (đây là những thứ phải xin từ phía Công ty, nên xin sớm):

* Tên đầy đủ của Công ty, **đúng như trên giấy đăng ký doanh nghiệp**
* **Mã số doanh nghiệp / mã số thuế**
* Địa chỉ trụ sở đăng ký
* Người đại diện theo pháp luật và thông tin liên hệ
* Email liên hệ — **dùng email trung tính của Công ty**, không dùng email cá nhân
* Bản khai đăng ký tên miền có **chữ ký người đại diện và dấu của Công ty** (nhiều nhà đăng ký nay
  nhận bản điện tử/ký số — hỏi họ nhận dạng nào)

> ⚠⚠ **Đổi chủ thể tên miền `.vn` về sau là một thủ tục hành chính thật**, cần hồ sơ từ **cả hai
> bên** (bên chuyển và bên nhận), không phải một nút bấm trong bảng điều khiển. Đăng ký nhầm dưới
> tên cá nhân rồi chuyển về Công ty sau là tự tạo cho mình một việc mất nhiều tuần — và nó rơi đúng
> vào lúc bàn giao, tức là lúc bận nhất.

> ⛔ **Cái bẫy thật sự: có nhà đăng ký đăng ký tên miền dưới tên CHÍNH HỌ rồi "quản lý hộ" khách.**
> Nghe thì tiện, nhưng khi đó Công ty **không sở hữu** tên miền — muốn chuyển đi phải xin phép, và
> nếu quan hệ với nhà cung cấp xấu đi thì mất luôn địa chỉ web. Phải kiểm, không phải hỏi miệng:

```bash
whois songnhue.vn | grep -iA2 "Chủ thể\|Registrant\|Tên tổ chức"
```

Trường chủ thể phải hiện **tên Công ty**. Hiện tên nhà đăng ký hoặc tên cá nhân nào đó là **sai — bắt
làm lại ngay**, đừng để sang bước tiếp theo. Tra trên web tại `https://whois.vnnic.vn`.

### 2.5. Chi phí — hai khoản, đừng chỉ nhìn khoản đầu

Tên miền `.vn` có **hai** khoản tiền, và người mua lần đầu hay chỉ nhìn khoản thứ nhất:

| Khoản | Đóng khi nào |
|---|---|
| **Phí đăng ký** | một lần, lúc mở tên miền |
| **Phí duy trì** | **mỗi năm**, đóng đều cho tới khi thôi dùng |

Cộng lại, `.vn` cho một năm đầu thường rơi vào khoảng **vài trăm nghìn đến trên một triệu đồng**,
tuỳ đuôi và tuỳ nhà đăng ký. So với tiền VPS thì đây là khoản nhỏ nhất trong cả hạ tầng.

> ⚠ **Tôi không khẳng định con số cụ thể.** Mức phí `.vn` do văn bản của cơ quan quản lý quy định và
> **có thay đổi**; nhà đăng ký còn cộng thêm phí dịch vụ riêng. Lấy báo giá bằng văn bản từ 2–3 nhà
> đăng ký, và **hỏi rõ giá duy trì từ năm thứ hai** — đó mới là con số phải trả mãi.

💡 **Trả trước nhiều năm nếu được.** Thường có chiết khấu, và quan trọng hơn: mỗi lần gia hạn là một
lần có thể quên.

### 2.6. Sau khi mua — trỏ DNS về đâu

Hai lựa chọn, và khuyến nghị khác nhau tuỳ giai đoạn:

| | Cách | Khi nào |
|---|---|---|
| **Giai đoạn dựng staging** ⭐ | Dùng thẳng **DNS của nhà đăng ký**, tạo bản ghi A | Ít mắt xích nhất. Thử thách ACME đi qua HTTP-01 và một tầng proxy ở giữa lúc cấp chứng chỉ lần đầu chỉ thêm biến số |
| **Trước khi mở production** | Đổi nameserver sang **Cloudflare** (gói miễn phí) | Có cache, chống ngập, và bảng điều khiển DNS dễ dùng hơn |

Nếu chuyển sang Cloudflare thì giữ đúng luật đã chốt ở `hosting_recommendations.md` §4:

| Bản ghi | Chế độ |
|---|---|
| `songnhue.vn`, `www` | 🟠 **Proxy bật** — cache và chống ngập cho phần dân truy cập |
| `admin`, `files` và mọi bản ghi `*-staging` | ⚪ **DNS-only** |

> ⛔ Lý do `admin` và `files` phải DNS-only: không cho **phiên quản trị** và **tệp nhân sự** đi vòng
> qua hạ tầng đặt ở nước ngoài — vốn chính là điều đang cố tránh khi chọn đặt máy trong nước. Và về
> kỹ thuật, proxy đứng trước `files` còn phá chữ ký presigned của MinIO, vì chữ ký ký **cả tên máy**.

**Bản ghi cần tạo cho staging** (bước 4 ở §4):

```
staging          A    <IP VPS-2>
admin-staging    A    <IP VPS-2>
files-staging    A    <IP VPS-2>
```

✅ **Kiểm chứng trước khi sang bước cấp chứng chỉ** — DNS lan truyền không tức thời, có thể mất từ
vài phút tới vài giờ:

```bash
for h in staging admin-staging files-staging; do
  printf '%-16s %s\n' "$h" "$(dig +short $h.songnhue.vn)"
done
```

Cả ba phải ra **đúng IP VPS-2**. Còn trống dòng nào thì **đợi**, đừng chạy certbot — thử thách ACME
hỏng vì DNS chưa lan là một trong những lỗi mất thời gian nhất, vì thông báo lỗi của nó không nói ra
điều đó.

### 2.7. Gia hạn — chỗ hỏng lặng lẽ nhất trong cả hạ tầng

Tên miền hết hạn thì **mọi thứ dừng cùng lúc**: cổng thông tin, giao diện quản trị, đường tải tệp,
và cả lượt gia hạn chứng chỉ TLS. Không có cảnh báo nào trong hệ thống bắt được việc này — Prometheus
canh máy chủ, mà máy chủ vẫn chạy tốt.

Ba việc, làm ngay lúc mua:

1. **Bật tự động gia hạn** ở bảng điều khiển nhà đăng ký, **và** giữ phương thức thanh toán còn hiệu
   lực. Bật tự gia hạn với một cái thẻ đã hết hạn là không bật gì cả.
2. **Đặt lịch nhắc trước 60 ngày** trong lịch của Công ty, không phải lịch cá nhân.
3. **Email liên hệ của tên miền phải là email Công ty** còn người đọc. Cảnh báo sắp hết hạn gửi vào
   một hộp thư không ai mở là cảnh báo không tồn tại.

> 📌 Cùng một hình dạng với `docs/runbook/` và khoá riêng `age`: **thứ chỉ một người biết là thứ sẽ
> mất.** Rủi ro #4 ở `hosting_recommendations.md` §6 — "một người vận hành duy nhất" — áp cho tài
> khoản tên miền y như áp cho máy chủ.

---

## 3. Cắt chi phí ở đâu — và ba chỗ tuyệt đối không cắt

**Cắt được, không mất gì:**

| Cắt | Tiết kiệm | Vì sao an toàn |
|---|---|---|
| Trả trước theo năm | thường 15–25% | Nhà cung cấp trong nước hay có mức này nhưng **không niêm yết** — phải hỏi thẳng |
| Cùng nhà cung cấp với VPS-1 | chiết khấu 2 máy | Kho sao lưu **ngoài** (B2/R2) mới là bản chống "mất tài khoản nhà cung cấp"; hai VPS cùng nhà không phá điều đó |
| Prometheus retention 90d → 30d | vài GB đĩa | 90 ngày là để đối chiếu xu hướng lúc nghiệm thu NFR; chưa tới lúc đó |
| **Không** mua kho ngoài riêng cho staging | ~vài chục nghìn/tháng | Kho ngoài bảo vệ **dữ liệu thật**. Staging không có dữ liệu thật |
| **Không** mua SMTP trả phí cho staging | | Dùng chung tài khoản SMTP với production nhưng đổi `SMTP_FROM`, hoặc trỏ vào hộp thư thử |

**⛔ Ba chỗ cắt là hỏng, không phải tiết kiệm:**

1. **Tên miền và chứng chỉ TLS riêng cho staging.** Dùng chung tên miền với production, hoặc chạy
   staging trên HTTP trần, là bỏ qua đúng phần khó nhất: cookie `Secure` + `SameSite=Strict`, HSTS,
   CSP, chữ ký presigned ký cả tên máy. Cả bốn thứ ấy **chỉ hỏng khi có TLS thật và tên miền thật** —
   staging không có chúng thì nó không kiểm được gì. Và **chỗ này không tốn thêm đồng nào**: Let's
   Encrypt miễn phí, còn ba địa chỉ staging chỉ là **tên miền phụ** của tên miền đã mua cho
   production — xem §2.1.
2. **Khoá riêng của staging.** Dùng chung `jwt-private.pem` hay `AES_KEY_V1` với production nghĩa là
   token cấp ở staging **mở được production**, và một bản dump staging — thứ luôn được xử lý lỏng tay
   hơn — trở thành đường vào dữ liệu thật.
3. **Giám sát.** Nó nằm trên VPS-2 để sống sót khi VPS-1 chết. Bỏ nó đi thì lúc production sập, thứ
   duy nhất còn báo là điện thoại của người dùng.

---

## 4. Thứ tự thực thi — 9 bước

Mỗi bước có phép kiểm ở cuối. **Đừng sang bước sau khi phép kiểm chưa xanh** — sai ở bước 3 mà phát
hiện ở bước 8 thì phải lần ngược qua năm bước.

### Bước 1 — Mua và đăng ký *(làm trước, có việc chờ bên ngoài)*

| # | Việc | Chi tiết |
|---|---|---|
| 1.1 | **VPS-2 — 2 vCPU / 8 GB / 80 GB**, Ubuntu 24.04 LTS, đặt tại Việt Nam | Cấu hình **đã chốt**, xem §1 để biết vì sao 4 GB không đủ |
| 1.2 | **Một** tên miền `.vn`, **chủ thể là Công ty** | Toàn bộ cách làm ở **§2**. Sáu địa chỉ của hệ thống = 1 tên miền mua + 5 tên miền phụ miễn phí |
| 1.3 | Email trung tính (`it@…`) mở **mọi** tài khoản | ⛔ Không dùng Gmail cá nhân — VPS, tên miền, hoá đơn đều phải đứng tên Công ty (`hosting_recommendations.md` §3) |
| 1.4 | Tài khoản SMTP | Staging dùng chung với production được, chỉ đổi `SMTP_FROM` |
| 1.5 | Xin từ Công ty: **mã số doanh nghiệp, địa chỉ trụ sở, người đại diện, bản khai có dấu** | Đây là thứ chờ lâu nhất và không do mình quyết — xin ngay hôm nay, xem §2.4 |

⏳ **Việc chờ lâu nhất là hồ sơ tên miền, không phải VPS.** VPS thường có trong vài giờ; hồ sơ đăng
ký tên miền tổ chức cần chữ ký và dấu của Công ty, rồi nhà đăng ký còn duyệt. Bước 2 và 3 chỉ cần
VPS — làm song song trong lúc chờ tên miền.

### Bước 2 — Dựng máy → `deploy-guideline.md` §2

SSH khoá công khai · tường lửa `ufw` · Docker · `unattended-upgrades` · cây thư mục.

> ⚠ **Grafana: không mở cổng tường lửa nào.** `compose.observability.yml` publish ra
> `127.0.0.1:13001`, nên đã bind loopback thì `ufw allow` không có tác dụng — và cổng cũng không
> phải 3000. Vào bằng SSH tunnel (`deploy-guideline.md` §2.2 đã sửa theo):
>
> ```bash
> ssh -N -L 13001:127.0.0.1:13001 songnhue@<VPS-2>   # rồi mở http://localhost:13001
> ```

✅ **Kiểm chứng**: `ssh songnhue@<VPS-2> 'docker run --rm hello-world'` chạy được, và đăng nhập
`root` bị từ chối.

### Bước 3 — Khoá và `.env` → `deploy-guideline.md` §3

Chép **`deploy/env/staging.env.example`** (không phải `prod`) thành `/opt/songnhue/.env`, `chmod 600`.

⛔ **Ba giá trị staging khác production, và quên một cái là hỏng thật:**

```bash
ROBOTS_TAG=noindex, nofollow   # ⛔⛔ quên → compose lấy mặc định `all` → GOOGLE ĐÁNH CHỈ MỤC STAGING
SPRING_PROFILES_ACTIVE=staging
APP_ENVIRONMENT=staging        # nhãn gắn vào mọi chỉ số, để Grafana phân biệt hai môi trường
```

⛔ **Sinh khoá riêng cho staging trên chính VPS-2**, không chép từ production sang — xem §2 điểm 2.

✅ **Kiểm chứng fail-fast là có thật** *(đừng bỏ — đây là DoD #3 của Phase 0)*:

```bash
cd /opt/songnhue
cp .env .env.bak && sed -i 's/^AES_KEY_V1=.*/AES_KEY_V1=/' .env
docker compose --env-file .env -f compose.staging.yml run --rm app 2>&1 | grep -i AES_KEY_V1
mv .env.bak .env
```

Phải thấy dòng báo thiếu `AES_KEY_V1` và tiến trình **không khởi động**.

### Bước 4 — DNS và TLS → `deploy-guideline.md` §4

Ba bản ghi A trỏ về VPS-2: `staging` · `admin-staging` · `files-staging` — cách tạo và nơi tạo ở
**§2.6**.

> ⚠ **DNS-only cho cả ba** (không bật proxy Cloudflare ở staging). Thử thách ACME đi qua HTTP-01, và
> một tầng proxy ở giữa lúc cấp chứng chỉ lần đầu chỉ thêm một biến số không cần thiết.

⏳ **Đợi DNS lan trước khi chạy certbot.** Cả ba tên phải `dig` ra đúng IP VPS-2 (phép kiểm ở §2.6).
Thử thách ACME hỏng vì DNS chưa lan là một trong những lỗi mất thời gian nhất, vì thông báo lỗi của
nó không nói ra điều đó.

✅ **Kiểm chứng**: `certbot renew --dry-run` xanh. Chứng chỉ hết hạn lúc 3h sáng không được là một
loại sự cố.

### Bước 5 — Dựng stack lần đầu, bằng tay → `deploy-guideline.md` §5

Làm tay đúng một lần để thấy từng bước; từ lượt sau CD làm hộ.

```bash
docker compose --env-file .env -f compose.staging.yml pull
docker compose --env-file .env -f compose.staging.yml up -d postgres minio
docker compose --env-file .env -f compose.staging.yml run --rm minio-init
docker compose --env-file .env -f compose.staging.yml run --rm migrator   # ← PHẢI thoát mã 0
docker compose --env-file .env -f compose.staging.yml up -d app admin-app public-web nginx
```

### Bước 6 — Sáu phép kiểm, làm đủ

```bash
# 1. ĐI HẾT CHẶNG nginx → public-web → Route Handler → app → postgres
#    (sửa 24/8 — bản cũ hỏi /actuator, đường đó không đi tới đâu, xem §0)
curl -fsS https://staging.songnhue.vn/api/v1/public/site-config | head -c 120   # → "success":true

# 2. ⚠ PHẢI có Origin. curl trần không preflight nên nó đi lọt qua đúng bức tường
#    chặn người dùng thật — CORS đã chặn cả giao diện quản trị suốt WS-8→WS-20.
curl -si -X OPTIONS https://admin-staging.songnhue.vn/api/v1/auth/login \
     -H "Origin: https://admin-staging.songnhue.vn" \
     -H "Access-Control-Request-Method: POST" | head -1

# 3. Header bảo mật có mặt trên CẢ HAI tên miền
for d in staging admin-staging; do
  echo "--- $d"; curl -sI https://$d.songnhue.vn/ | grep -iE "strict-transport|content-security|x-frame|x-robots"
done

# 4. Staging KHÔNG được đánh chỉ mục
curl -sI https://staging.songnhue.vn/ | grep -i x-robots-tag      # → noindex, nofollow

# 5. Gõ thẳng IP phải bị từ chối ở tầng TLS
curl -sk https://<IP-VPS2>/ -o /dev/null -w '%{http_code}\n'      # → 000

# 6. Đăng nhập superadmin → đổi mật khẩu → bật 2FA → TẢI VỀ một tệp đính kèm
```

> **Phép 6 không bỏ được.** Năm phép đầu vẫn xanh trọn vẹn khi `MINIO_ENDPOINT` sai, vì lỗi chỉ xuất
> hiện ở đường tải tệp về — tải **lên** vẫn chạy bình thường. Đó là lý do nó khó thấy.

### Bước 7 — Nối GitHub

**Settings → Environments → `staging`**:

| Tên | Giá trị |
|---|---|
| `STAGING_HOST` | IP VPS-2 |
| `STAGING_USER` | `songnhue` |
| `STAGING_SSH_KEY` | nội dung `~/.ssh/songnhue_deploy` (khoá **riêng**) |
| `STAGING_BASE_URL` | `https://staging.songnhue.vn` |

> ⚠ **Đặt đúng cấp, không chỉ đúng tên.** Secret của environment chỉ đến được job có khai
> `environment:`. Lần đầu `NVD_API_KEY` bị đặt nhầm vào environment `staging` và phép quét CVE
> **im lặng bỏ qua** — `secrets.NVD_API_KEY` giải ra chuỗi rỗng, không có lỗi nào.
>
> ⚠ `deploy-staging.yml` **tự bỏ qua bước triển khai** khi thiếu `STAGING_HOST`, và chỉ ghi một dòng
> `::warning::`. Lượt chạy vẫn **xanh**. Đặt thiếu secret thì trông y hệt đặt đủ.

Đồng thời đặt **repo variable** `PUBLIC_SITE_URL` = địa chỉ production (`https://songnhue.vn`).
Chưa đặt thì image `public-web` rơi về `http://localhost:3000` cho canonical/sitemap — xem
`architecture-review.md` §10.38.

### Bước 8 — Lượt deploy tự động đầu tiên

```
PR  dev → staging   ·   MERGE COMMIT (không squash)
```

⛔ **Không squash.** `deploy-staging.yml` tìm image bằng `HEAD^2` — cha thứ hai của merge commit
chính là đỉnh `dev`, và đó là mối liên hệ duy nhất với image đã qua CI. Squash sinh SHA mới không có
cha thứ hai và cắt đứt liên hệ đó. Đây cũng là lý do `required_linear_history` **tắt** ở nhánh
`staging` (`docs/branch-protection.md` §2.3).

`Promotion guard` chạy ở PR và xác minh đúng commit ấy đã xanh CI ở `dev`.

✅ **Kiểm chứng**: job `Triển khai Staging` xanh **và** phần tóm tắt ghi *"Triển khai thật: có"*.
Nếu ghi *"CHƯA (thiếu secret)"* thì bước 7 chưa xong — và lượt chạy vẫn xanh.

### Bước 9 — Giám sát và kho sao lưu trên VPS-2

```bash
cd /opt/songnhue
docker compose --env-file .env -f compose.observability.yml up -d
```

> ⚠ Cần `GRAFANA_ADMIN_PASSWORD` trong `.env` — biến này viết `${…:?}` nên **thiếu là dừng ngay**,
> không container nào lên. Trước 24/8 nó không được nhắc ở tệp mẫu hay tài liệu nào.

Kho sao lưu kéo từ production chỉ dựng được **sau khi có VPS-1** — `deploy-guideline.md` §9.1.
Ghi vào việc cần làm, đừng để quên: staging đứng một mình thì VPS-2 mới làm được **một nửa** vai trò
của nó.

---

## 5. Việc còn treo sau khi staging chạy

| Việc | Chặn cái gì |
|---|---|
| **DOD1.17** — đo trang chủ cổng < 3s (NFR-02) | Chỉ đo được trên môi trường gần thật. Đây là mục DoD Phase 1 **duy nhất còn treo** |
| **DOD0.14** — đo RTO thật bằng một lượt khôi phục | Cần cả VPS-1. Chưa khôi phục thật thì chưa có sao lưu, chỉ có tệp |
| Nợ #46 — thêm 3 context đóng gói image vào required checks | `docs/branch-protection.md` §4.1 |
| Nợ #45 — bật Dependency graph | Job *Soi phụ thuộc PR thêm vào* đang `skipped`, mà `skipped` được tính là **đạt** |

### Ba khoản nợ kỹ thuật đã biết, sống chung được

`deploy-guideline.md` §9.3 giữ bản đầy đủ. Riêng khoản ảnh hưởng staging nhiều nhất:

> `NEXT_PUBLIC_SITE_URL` **nướng vào bundle lúc build**, mà CI chỉ dựng **một** bộ image cho cả hai
> môi trường. Nghĩa là `sitemap.xml`, thẻ canonical và ảnh Open Graph của **staging mang địa chỉ
> production**. Thứ duy nhất chặn hậu quả là `X-Robots-Tag: noindex, nofollow` ở nginx biên — nên
> phép kiểm số 4 ở bước 6 **không phải thủ tục cho có**, nó là lớp bảo vệ duy nhất đang đứng.
