# Đề xuất hạ tầng — Hệ thống Quản lý Thuỷ lợi Sông Nhuệ

> Chốt **23/8/2026**. Cách làm từng bước: `docs/deploy-guideline.md`. Luồng CI/CD: `docs/cicd.md`.

## 1. Chốt: 2 VPS đặt tại Việt Nam, chạy docker-compose

| | Máy | Cấu hình | Chạy gì |
|---|---|---|---|
| **VPS-1** | Production | 4 vCPU · 8 GB · 160 GB SSD · Ubuntu 24.04 | nginx · app · postgres · minio · admin-app · public-web |
| **VPS-2** | Staging | **2 vCPU · 8 GB · 80 GB** | cùng stack (nhỏ hơn) **+ kho sao lưu + Prometheus/Grafana** |
| — | Kho ngoài | B2 / R2, **nhà cung cấp khác** | bản sao lưu đã mã hoá |

Nhà cung cấp: lấy báo giá Viettel IDC · VNPT · FPT · BizFly · VNG. Chênh nhau nhiều và hay có giá
trả trước theo năm — hỏi thẳng, đừng lấy giá niêm yết.

> ⚠ **VPS-2 đã sửa 4 GB → 8 GB (24/8).** Bản đầu ghi 4 GB, và con số đó có từ lúc gộp VM-3 vào
> VPS-2 mà **không tính lại ngân sách bộ nhớ**: stack staging 3.968 MB + giám sát 928 MB + hệ điều
> hành ~500 MB ≈ **5,4 GB**. Phép cộng đầy đủ và hai phương án bị loại: **§8** bên dưới.

📌 **Tên miền**: chỉ mua **một**, cho cả hai môi trường — sáu địa chỉ của hệ thống là 1 tên miền gốc
+ 5 tên miền phụ miễn phí. Chọn nhà đăng ký, hồ sơ chủ thể là Công ty, và cái bẫy "nhà đăng ký đứng
tên hộ": **§9** bên dưới.

**Ba máy rút còn hai.** Kế hoạch cũ (`.claude/phase0-tracking.md` T11.2) có VM-3 riêng cho sao lưu
và giám sát. VM-3 gộp vào VPS-2 vì tính chất cần giữ là *"nằm ngoài máy production"*, không phải
*"là một máy thứ ba"*. Gộp lại vẫn giữ đủ: chuỗi giám sát sống sót khi VPS-1 chết, và kho sao lưu
không nằm cùng đĩa với CSDL.

## 2. Vì sao không PaaS — năm bảo đảm phải tháo

Đã có một bản kế hoạch chuyển sang Vercel + Railway. Bản đó bị bác, và lý do không phải "PaaS tệ"
mà là: với repo **này**, giữ PaaS đòi tháo năm thứ đã dựng có chủ đích.

| # | PaaS bắt phải làm gì | Đang bảo vệ điều gì |
|---|---|---|
| 1 | Thêm cấu hình CORS vào backend | Hiện backend **không có một dòng CORS nào**, vì giao diện và API luôn cùng origin |
| 2 | Hạ cookie refresh `SameSite=Strict` → `None` | Chống CSRF ở tầng trình duyệt (`CsrfTokens.java:87`) |
| 3 | Bỏ service `migrator` riêng | Migration hỏng thì app **không lên nửa vời** |
| 4 | Bỏ 4 vai trò CSDL tách quyền | `audit_logs` chỉ-ghi-thêm **ở tầng CSDL**, không chỉ ở mã |
| 5 | Chuyển header bảo mật vào Spring | `NginxSecurityHeadersTest` đang canh chúng ở đúng nơi chúng sống |

Cộng thêm: **hai phần ba công việc đã làm xong cho đường VPS** — Dockerfile, compose, init CSDL,
4 script sao lưu, cấu hình nginx, hai workflow triển khai. Đường PaaS vứt hết chỗ đó rồi dựng lại
một bản yếu hơn.

Và một lý do không thuộc kỹ thuật, nhưng là lý do lớn nhất: hệ này quản lý công trình thuỷ lợi của
một công ty nhà nước. Câu *"dữ liệu nhân sự đang nằm trên máy chủ nước ngoài do cá nhân nhà thầu
đứng tên"* rất khó trả lời khi có người hỏi.

## 3. Đặt máy trong nước gỡ được gì, và không gỡ được gì

| Nghĩa vụ | Đặt trong nước | Đặt nước ngoài |
|---|---|---|
| NĐ 13/2023 **Điều 25** — hồ sơ đánh giá tác động xử lý DLCN | **Vẫn phải làm** | Vẫn phải làm |
| NĐ 13/2023 **Điều 26** — hồ sơ chuyển DLCN ra nước ngoài | Không phát sinh | Phải làm |

Còn một hồ sơ thay vì hai, và hồ sơ còn lại thuộc loại tự lập tự lưu.

**Ba việc rẻ, giá gần bằng 0, làm ngay:**

1. **Ký phụ lục xử lý dữ liệu cá nhân với Công ty** — Công ty là Bên Kiểm soát, người vận hành là
   Bên Xử lý. Hiện chưa có văn bản nào định danh tư cách, mà dữ liệu cá nhân **đã tồn tại từ Phase
   0** (`users.full_name`, email, nhật ký audit), không phải đợi tới `employee_sensitive` ở Phase 2.
2. **Tên miền `.vn` đăng ký chủ thể là Công ty**, không phải cá nhân.
3. **Tài khoản hosting mở bằng email trung tính**, nhà cung cấp xuất hoá đơn VAT cho Công ty.

> ⚠ Đây là phần cần **pháp chế của Công ty xác nhận**, không phải kết luận kỹ thuật. Mẫu biểu và
> cách nộp hồ sơ Điều 25 tôi không khẳng định — nhưng phải có mục (1) trước, vì thiếu nó thì không
> rõ ai là bên phải nộp.

> ⚠ **Bàn giao** — chỗ chưa ai tính. Hệ này rồi sẽ chuyển cho Công ty hoặc đơn vị bảo trì kế tiếp.
> Tài khoản PaaS cá nhân gắn thẻ cá nhân thì không bàn giao được, phải dựng lại từ đầu. VPS + tên
> miền đứng tên Công ty thì bàn giao là đổi mật khẩu.

## 4. Cắt gánh nặng quản trị xuống gần 0

Lý do chính chọn PaaS là **không muốn trông máy chủ**. Lấy lại phần lớn điều đó bằng 5 việc làm
một lần:

| Việc | Cách | Sau đó |
|---|---|---|
| Vá bảo mật hệ điều hành | `unattended-upgrades` | tự động, vĩnh viễn |
| Chứng chỉ TLS | certbot webroot + cron hằng tuần, đã kiểm bằng `--dry-run` | hết hạn lúc 3h sáng không còn là một loại sự cố |
| Container chết | `restart: unless-stopped` | tự dậy |
| Biết khi sập | UptimeRobot bản miễn phí, ping `/healthz` từ ngoài | không cần máy thứ ba cho giám sát |
| Biết khi sao lưu chết | cảnh báo `backup_last_success > 26h` | đã có sẵn `deploy/observability/alerts.yml` |

Cloudflare: **cổng công khai bật proxy** (cache + chống ngập cho phần dân truy cập); **`admin` và
`files` để DNS-only** — không cho phiên quản trị và tệp nhân sự đi vòng qua hạ tầng nước ngoài,
vốn là điều đang cố tránh.

## 5. Sao lưu — sửa hai điểm sai của bản PaaS

Bản trước đề xuất `pg_dump` **hàng tuần**, lưu **vào MinIO cùng nền tảng**. Cả hai đều sai theo
hướng nguy hiểm:

| Bản PaaS | Vấn đề | Chốt |
|---|---|---|
| hàng tuần | RPO thành 7 ngày; đã chốt **≤ 24h** (NFR-08) | **hàng đêm 02:00** — `backup.sh` đã viết sẵn |
| lưu vào MinIO | MinIO **cùng nền tảng** với CSDL → mất tài khoản là mất cả hai | kéo về VPS-2 **+** đẩy ra nhà cung cấp khác |

Thành ba bản, mỗi bản trả lời một câu hỏi khác nhau:

| Bản | Ở đâu | Trả lời |
|---|---|---|
| Đêm 02:00 | VPS-1 | "xoá nhầm bảng lúc chiều" |
| Kéo về 03:00 | VPS-2 | "VPS-1 chết / đĩa hỏng" |
| Đẩy ra 03:30, **đã mã hoá** | B2/R2 | "mất tài khoản nhà cung cấp" |

Cộng bản `predeploy-*` tự động trước mỗi lượt deploy production — điểm quay lui **duy nhất** khi
migration làm hỏng dữ liệu, vì hệ này không có PITR.

Ba điều bổ sung, mỗi điều bịt một lỗ:

1. **Mã hoá trước khi rời máy** (`age`). Bản dump chứa dữ liệu nhân sự; mã hoá xong thì thứ rời
   khỏi máy chỉ là byte ngẫu nhiên, và kho ở đâu không còn là câu hỏi về dữ liệu cá nhân.
2. **Sao lưu cả tệp đính kèm.** Kế hoạch cũ chỉ sao lưu CSDL. Hồ sơ công trình nằm trong MinIO và
   **chưa có bản sao nào** — khôi phục xong sẽ được một hệ thống đầy đủ bản ghi mà mọi đường tải
   về đều 404.
3. **Diễn tập khôi phục một lần trước go-live**, ghi con số RTO thật vào runbook. Dự án đã có 4
   ngày sao lưu chạy mà không sinh ra tệp nào, trong khi bài kiểm vẫn xanh. **Chưa khôi phục thật
   thì chưa có sao lưu, chỉ có tệp.**

## 6. Rủi ro chấp nhận — ghi rõ để về sau có căn cứ

| # | Rủi ro | Hệ quả xấu nhất | Vì sao chấp nhận |
|---|---|---|---|
| 1 | Mất tối đa 1 ngày dữ liệu | Nhập lại số liệu trong ngày | Giờ hành chính, khối lượng nhập nhỏ |
| 2 | Không HA — CSDL chết là dừng dịch vụ | Ngừng 30–60 phút | NFR-01 (99% ≈ 7,2h/tháng) thừa biên |
| 3 | Không PITR | Migration hỏng giữa ngày | Bù bằng bản chụp `predeploy-*` |
| 4 | Một người vận hành duy nhất | Người đó bận thì không ai xử lý | ⚠ **Chưa có phương án.** Tối thiểu: đưa `docs/runbook/` cho một người thứ hai và cất bản sao khoá riêng ở nơi thứ hai |

Rủi ro #4 là rủi ro thật nhất trong bảng này và là rủi ro duy nhất chưa được xử lý bằng kỹ thuật.

## 7. Đường nâng cấp — khi nào cần, không phải bây giờ

Bật PITR về sau là **thêm cấu hình, không sửa mã**: `archive_mode=on` + `archive_timeout` +
`pg_basebackup` hàng tuần. Cân nhắc khi dữ liệu thuỷ văn đã tích luỹ nhiều năm, hoặc Công ty nâng
cam kết uptime, hoặc lên ≥2 node.

> 📌 Lưu ý kỹ thuật cho lần đó: **không thể replay WAL lên bản `pg_dump`**. PITR bắt buộc phải có
> `pg_basebackup` (bản vật lý). Bật WAL archiving mà chỉ có dump logic là vô nghĩa.

---

## 8. Ngân sách bộ nhớ VPS-2 — phép cộng dẫn tới 8 GB

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

---

## 9. Tên miền — mua ở đâu, mua thế nào

### 9.1. Đếm trước đã: chỉ phải mua **một** tên miền

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

### 9.2. Chọn đuôi nào: `.vn` hay `.com.vn`

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

### 9.3. Mua ở đâu — không mua thẳng từ VNNIC được

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

### 9.4. ⛔ Chủ thể phải là **Công ty** — đây là phần dễ làm sai nhất

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

### 9.5. Chi phí — hai khoản, đừng chỉ nhìn khoản đầu

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

### 9.6. Sau khi mua — trỏ DNS về đâu

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

### 9.7. Gia hạn — chỗ hỏng lặng lẽ nhất trong cả hạ tầng

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

---

## 10. Cắt chi phí ở đâu — và ba chỗ tuyệt đối không cắt

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
   production — xem §9.1.
2. **Khoá riêng của staging.** Dùng chung `jwt-private.pem` hay `AES_KEY_V1` với production nghĩa là
   token cấp ở staging **mở được production**, và một bản dump staging — thứ luôn được xử lý lỏng tay
   hơn — trở thành đường vào dữ liệu thật.
3. **Giám sát.** Nó nằm trên VPS-2 để sống sót khi VPS-1 chết. Bỏ nó đi thì lúc production sập, thứ
   duy nhất còn báo là điện thoại của người dùng.

---
