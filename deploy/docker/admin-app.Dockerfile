# syntax=docker/dockerfile:1.7
# =============================================================================
# admin-app (Vite + React 18 + AntD 5) — build bundle tĩnh, phục vụ bằng nginx.
#
# Build context là thư mục `frontend/` (npm workspaces nằm ở đó).
#
# ⚠ Vite nhúng biến `VITE_*` vào bundle LÚC BUILD, không đọc lúc chạy. Đổi
#   VITE_API_BASE_URL bắt buộc phải build lại image — đây là lý do biến đó là
#   `ARG` chứ không phải `ENV` của container.
# =============================================================================

FROM node:22-alpine AS build
WORKDIR /build

# Cài dependency trước theo lockfile để tận dụng cache lớp.
#
# ⚠ CHỈ chép manifest của workspace này, và cài bằng `--workspace admin-app`.
#   Bản đầu chép luôn `public-web/package.json` và chạy `npm ci` trần — build đổ
#   ngay ở bước COPY vì WS-9 chưa tạo thư mục đó. Hai image FE build độc lập với
#   nhau thì thêm/bớt app không kéo image kia hỏng theo, và mỗi image cũng chỉ
#   tải đúng phần phụ thuộc của mình.
COPY package.json package-lock.json .npmrc ./
COPY admin-app/package.json admin-app/
RUN npm ci --workspace admin-app --include-workspace-root

COPY . .

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build --workspace admin-app

FROM nginx:1.27-alpine AS runtime

# SPA: mọi đường dẫn không khớp file tĩnh đều trả index.html, để React Router
# xử lý; thiếu dòng này thì F5 giữa chừng là 404.
COPY <<'EOF' /etc/nginx/conf.d/default.conf
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    # Ẩn phiên bản nginx (conventions.md §4.5)
    server_tokens off;

    location /healthz {
        access_log off;
        return 200 "ok\n";
    }

    # File có hash trong tên → cache dài. index.html thì không, để deploy mới
    # là trình duyệt thấy ngay.
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    location / {
        add_header Cache-Control "no-cache";
        try_files $uri $uri/ /index.html;
    }
}
EOF

COPY --from=build /build/admin-app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1
