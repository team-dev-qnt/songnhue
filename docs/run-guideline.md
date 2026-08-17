# Hướng dẫn chạy — songnhue

Dựng máy lần đầu: [`setup-guideline.md`](setup-guideline.md).

---

## Nguyên tắc một câu

> **Service nào bạn đang sửa thì chạy native. Còn lại đẩy hết vào Docker.**

Docker ở đây không phải để thay thế môi trường phát triển — nó để dựng **những
thứ bạn không đụng vào**, đúng như trên máy chủ, mà không bắt bạn cài JDK/Node
cho phần việc của người khác.

---

## Bốn chế độ chạy

| Lệnh | Docker chạy | Bạn chạy native | Dành cho |
|---|---|---|---|
| `make dev-infra` | PostgreSQL · MinIO · Mailpit | backend + frontend | Fullstack, muốn hot-reload cả hai đầu |
| `make dev-be` | + **backend** | frontend | **Người làm FE** — không cần cài JDK |
| `make dev-fe` | + **admin-app, public-web** | backend | **Người làm BE** — không cần cài Node |
| `make dev-docker` | **tất cả** | không | QA, demo, kiểm thử gần giống production |

Mọi lệnh `dev-*` **luôn build lại image** từ mã nguồn hiện tại trước khi chạy — khi mã không đổi
chỉ tốn ~10 giây vì Docker dùng lại lớp cache. Thêm `NOBUILD=1` nếu muốn bỏ qua bước đó.

> ⚠ Mặc định này đổi ngày 17/8. Trước đó phải gõ `BUILD=1` mới build lại, và hệ quả là image
> backend nằm nguyên từ WS-3: container lên, `/actuator/health` xanh, mà **mọi endpoint
> `/api/v1/**` trả 404** vì bản dựng đó chưa có controller nào.

Lệnh phụ trợ: `make ps` · `make logs` · `make down` · `make reset-db` ·
`make doctor`.

---

## Docker có biên dịch theo code local không?

**Có — nhưng chỉ khi bạn yêu cầu build lại.**

Compose khai báo `build:` trỏ thẳng vào `backend/` và `frontend/`, và việc biên
dịch diễn ra **bên trong container** (Dockerfile đa tầng). Nên:

- Image **luôn dựng từ mã nguồn trên máy bạn**, không kéo từ registry.
- Máy **không cần** JDK hay Maven để build được backend.
- Image **không tự bám theo file**: sửa code xong mà chỉ `make dev-be` thì
  container vẫn chạy bản đã build lần trước.

```bash
make dev-be            # dùng image đã build sẵn — KHÔNG build lại
make dev-be            # luôn build lại từ code hiện tại rồi chạy
make build-images      # chỉ build, không chạy
```

**Chi phí build lại** (đo trên Apple Silicon, backend):

| Lần | Thời gian | Vì sao |
|---|---|---|
| Đầu tiên | ~9–10 phút | Tải toàn bộ dependency Maven |
| Sau khi sửa code | **~7 giây** | Lớp dependency được cache, chỉ biên dịch lại mã nguồn |
| `make dev-be` trọn gói | **~11 giây** | Gồm cả build + khởi động lại container |

Dockerfile chép `pom.xml` trước rồi mới chép mã nguồn, nên **sửa code mà không
đụng `pom.xml`** thì cache dependency vẫn dùng lại được. Đổi `pom.xml` thì lần
build kế tiếp lâu hơn.

### Vì sao không bind-mount mã nguồn để hot-reload trong Docker

Đây là lựa chọn có cân nhắc, không phải thiếu sót:

- **Không giải quyết đúng vấn đề.** Người muốn dùng Docker là người *không* sửa
  service đó. Người đang sửa thì chạy native — nhanh hơn, debug được từ IDE.
- **Nhiều bẫy.** `node_modules` cài trên macOS có gói native không chạy được
  trong container Linux; `target/` do container ghi ra thuộc quyền root và làm
  hỏng build native sau đó.
- **Bind-mount trên macOS chậm** — Maven và Vite đọc/ghi rất nhiều file nhỏ.

Đổi lại, mọi image chạy đúng ở chế độ giống production (bundle tĩnh, jar đóng
gói, chạy bằng user thường), nên thứ bạn thấy ở local sát với thứ sẽ chạy thật.

---

## Công thức theo vai trò

### Bạn làm frontend

```bash
make dev-be                       # backend + hạ tầng trong Docker
cd frontend/admin-app && npm run dev
```

- Backend ở `http://localhost:18080` → đặt
  `VITE_API_BASE_URL=http://localhost:18080/api/v1` (đây là giá trị mặc định
  trong `local.env.example`).
- Backend đổi code (do người khác push)? `git pull` rồi `make dev-be` — image tự build lại.
- **Không cần cài JDK.**

### Bạn làm backend

```bash
make dev-fe                       # 2 app FE + hạ tầng trong Docker
make dev-native                   # backend native, cổng 8080
```

- `make dev-fe` tự dựng image FE trỏ về backend **native** (cổng 8080), không
  phải cổng Docker.
- Không cần FE thì chỉ `make dev-infra` + `make dev-native`.
- **Không cần cài Node.**

### Bạn làm cả hai đầu

```bash
make dev-infra
make dev-native                            # cửa sổ 1
cd frontend/admin-app && npm run dev       # cửa sổ 2
```

Hot-reload cả hai phía, Docker chỉ giữ PostgreSQL/MinIO/Mailpit.

### QA hoặc demo

```bash
git pull
make dev-docker
```

Toàn bộ stack từ code của nhánh hiện tại, chạy ở chế độ giống production.

---

## Địa chỉ

| | Native | Docker |
|---|---|---|
| Backend | http://localhost:8080 | http://localhost:18080 |
| admin-app | http://localhost:5173 | http://localhost:15173 |
| public-web | http://localhost:3000 | http://localhost:13000 |
| PostgreSQL | — | `localhost:15432` |
| MinIO console | — | http://localhost:19001 |
| Hộp thư (Mailpit) | — | http://localhost:18025 |

Hai dải tách bạch nên **chạy song song native và Docker cùng lúc là được** —
tiện khi cần đối chiếu hành vi giữa bản đang sửa và bản đã build.

---

## Migration

Local mặc định `FLYWAY_ENABLED=true`, nên **backend native tự chạy migration**
lúc khởi động.

Trong Docker thì khác, và **cố ý giống production**: có service `migrator` riêng
chạy trước, app khởi động với `FLYWAY_ENABLED=false`. Migration hỏng thì
`migrator` thoát với mã lỗi và **app không lên** — thay vì lên nửa vời trên
schema hỏng.

```bash
make migrate-info      # migration nào đã áp dụng
make migrate-native    # chạy migration rồi thoát (không khởi động web)
make db-verify-audit   # kiểm tra chuỗi hash audit — 0 dòng = nguyên vẹn
```

Thêm migration mới thì image backend phải được build lại (mặc định đã làm) — file `.sql`
nằm trong jar.

---

## Sự cố hay gặp

**Sửa code mà không thấy đổi** → có đang chạy với `NOBUILD=1` không? Bỏ nó đi.

**`✗ Chưa có frontend/admin-app/`** → app FE do WS-8/WS-9 tạo, chưa có. Dùng
`make dev-be`.

**`port is already allocated`** → `make doctor` để biết ai giữ cổng, rồi đổi
biến `DOCKER_*_PORT` trong `deploy/env/local.env`.

**FE gọi API bị lỗi kết nối** → `VITE_API_BASE_URL` phải trỏ tới **nơi backend
đang chạy**: `8080` nếu native, `18080` nếu Docker.

**FE và backend khác origin** → cần cấu hình CORS. Việc này thuộc **WS-5**;
hiện chưa có endpoint nghiệp vụ nào nên chưa gặp. Trên production không có vấn
đề này vì nginx gom về cùng một origin (WS-11).

**Container backend `unhealthy`** → `make logs`. Thường là DB chưa sẵn sàng
hoặc thiếu biến env bắt buộc (app cố ý fail-fast, không chạy với default ngầm).

**Muốn làm sạch hoàn toàn:**

```bash
make down          # dừng, GIỮ dữ liệu
make reset-db      # xóa sạch volume PostgreSQL + MinIO (hỏi xác nhận)
```
