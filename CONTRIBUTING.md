# Hướng dẫn đóng góp — songnhue

Tài liệu này chỉ nói **cách làm việc với repo**. Quy tắc kỹ thuật nằm ở:

| Cần biết | Đọc |
|---|---|
| Nghiệp vụ, trường dữ liệu, workflow | `.claude/function-spec.md` |
| Coding / design / security convention | `.claude/conventions.md` |
| Kiến trúc đã chốt + lý do | `.claude/architecture-review.md` |
| Kế hoạch & tiến độ Phase 0 | `.claude/phase0-tracking.md` |
| Mục nghiệp vụ còn chờ khách chốt | `.claude/business-open-questions.md` (Phần III) |
| **Dựng máy lần đầu** | `docs/setup-guideline.md` |
| **Chạy hằng ngày (native / Docker)** | `docs/run-guideline.md` |

> ⚠ **Trước khi code một chức năng**, xem Phần III của `business-open-questions.md` để biết chức năng đó có "vùng chưa chốt" nào không.

## Bắt đầu

```bash
make hooks          # bật hook kiểm tra commit message
make env            # tạo deploy/env/local.env từ mẫu, rồi sửa giá trị
make doctor         # kiểm tra công cụ + cổng trống
```

Rồi chọn chế độ theo việc bạn **đang sửa**:

| Lệnh | Docker chạy | Bạn chạy native | Dành cho |
|---|---|---|---|
| `make dev-infra` | PostgreSQL · MinIO · Mailpit | backend + frontend | Fullstack |
| `make dev-be` | + backend | frontend | **Người làm FE** |
| `make dev-fe` | + 2 app FE | backend | **Người làm BE** |
| `make dev-docker` | tất cả | — | QA, demo |

Thêm `BUILD=1` để build lại image từ code hiện tại. `make` không tham số sẽ liệt kê toàn bộ lệnh.

**Yêu cầu máy**: Docker + Compose (bắt buộc) · JDK 21 *chỉ khi* chạy backend native · Node 22 *chỉ khi* chạy frontend native. Không cần cài Maven — repo dùng `mvnw` wrapper.

## Nhánh & commit

Nhánh: `feat/<module>-<mô-tả>`, `fix/…`, `chore/…`

Commit theo [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <mô tả>

type   feat | fix | docs | style | refactor | perf | test | build | ci | chore
scope  core | cms | ops | hyd | hr | fe | deploy | db | ws1 …
```

Ví dụ: `feat(core): thêm envelope response và global exception handler`

Hook `commit-msg` chặn commit sai định dạng — bật bằng `make hooks`.

## Trước khi mở PR

```bash
make format     # tự sửa định dạng
make lint       # Spotless + Checkstyle + ESLint + Prettier
make test-be    # unit + Testcontainers + ArchUnit
```

PR cần **1 reviewer** và **CI xanh**. Template PR đã gắn sẵn Definition of Done — tick đủ trước khi xin review.

## Ranh giới module (quan trọng nhất)

Backend là **Modular Monolith**: `core · content · operations · hydro · hr`, mỗi module có 5 tầng
`api / application / domain / infra / spi`.

**Module chỉ được import `spi/` của module khác.** Import `domain/`, `infra/`, `application/` chéo module
sẽ làm **ArchUnit test đỏ** trong CI. Đây là ràng buộc giữ cho module tách ra service riêng được về sau —
đừng lách bằng cách nới rule.

## Ba điều cấm hay bị quên

1. **`float`/`double` cho số đo và tiền** — luôn dùng `BigDecimal`. Sai số tích lũy qua Σ là không chấp nhận được.
2. **Commit secret** — `.env`, credential, khóa. `.gitignore` đã chặn, nhưng đừng dùng `git add -f`.
3. **Quên lọc `quality = HOP_LE`** khi truy vấn dữ liệu thủy văn — bản ghi `NGHI_NGO` nằm chung bảng chính,
   đây là bẫy sai số liệu dễ mắc nhất của dự án.
