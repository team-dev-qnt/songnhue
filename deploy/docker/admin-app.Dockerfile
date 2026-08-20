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
COPY design-tokens/package.json design-tokens/
COPY admin-app/package.json admin-app/
RUN npm ci --workspace admin-app --include-workspace-root

COPY . .

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build --workspace admin-app

FROM nginx:1.27-alpine AS runtime

# Máy chủ backend mà nginx chuyển tiếp `/api` tới. Đọc LÚC CHẠY qua cơ chế
# template của image nginx (`/etc/nginx/templates/*.template` + envsubst), nên
# đổi đích không phải build lại image — khác hẳn biến `VITE_*` ở tầng build.
ENV API_UPSTREAM=http://app:8080

# ⚠ Bật cơ chế đọc máy chủ DNS từ `/etc/resolv.conf` của chính container, để chỉ
#   thị `resolver` bên dưới có giá trị thật.
#
#   Đây là tính năng **phải chủ động mở**: script `15-local-resolvers.envsh` của
#   image mở đầu bằng `[ "${NGINX_ENTRYPOINT_LOCAL_RESOLVERS:-}" ] || return 0`,
#   nên thiếu biến này thì `${NGINX_LOCAL_RESOLVERS}` **không được thay**, nginx
#   đọc nguyên văn chuỗi `${NGINX_LOCAL_RESOLVERS}` và chết với
#   `[emerg] host not found in resolver`. Đã sập đúng như vậy một lượt.
#
#   Đọc từ `/etc/resolv.conf` chứ không ghi cứng `127.0.0.11` (DNS nội bộ của
#   Docker): cùng image chạy được cả ngoài Docker mà không phải sửa gì.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

# SPA: mọi đường dẫn không khớp file tĩnh đều trả index.html, để React Router
# xử lý; thiếu dòng này thì F5 giữa chừng là 404.
COPY <<'EOF' /etc/nginx/templates/default.conf.template
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

    # =========================================================================
    # ⚠⚠ Chuyển tiếp API — thiếu khối này thì KHÔNG MỘT LƯỢT GỌI NÀO chạy được
    # =========================================================================
    #
    # Bản đầu không có, và bundle được build với `VITE_API_BASE_URL` trỏ thẳng
    # `http://localhost:18080`. Trình duyệt ở cổng 15173 gọi sang cổng 18080 là
    # **khác origin**, nên lượt kiểm trước (preflight OPTIONS) bị backend trả
    # `403 Invalid CORS request` — toàn bộ giao diện quản trị không dùng được,
    # bắt đầu từ ô đăng nhập.
    #
    # Vì sao chuyển tiếp chứ không mở CORS ở backend:
    #
    #  1. **Giống hình dạng production.** T11.5 đã chốt nginx đứng trước cả hệ,
    #     nên ở production admin-app và API vốn CÙNG origin và không cần CORS.
    #     Mở CORS chỉ để chạy local là tạo ra một đường đi mà production không
    #     có — đúng loại chênh lệch dự án này đã trả giá (native vs Docker).
    #  2. **Vé phiên đi được.** Refresh token nằm trong cookie `httpOnly` +
    #     `SameSite=Strict`. Cùng origin thì trình duyệt gửi kèm mà không cần
    #     `withCredentials`, không cần `Allow-Credentials`, không cần liệt kê
    #     origin ở đâu cả.
    #  3. Bớt một danh sách origin phải nhớ cập nhật mỗi lần đổi tên miền.
    # ⚠⚠ `resolver` + biến trong `proxy_pass` là BẮT BUỘC, không phải cho gọn.
    #
    # Viết thẳng `proxy_pass http://app:8080` thì nginx phân giải tên máy **lúc
    # nạp cấu hình**. Backend chưa lên là nginx **từ chối khởi động**:
    #
    #   [emerg] host not found in upstream "app"
    #
    # và container quay vòng khởi động lại. Đo thật ở lượt dựng đầu tiên: giao
    # diện quản trị không truy cập được cho tới khi backend sẵn sàng — tức là một
    # sự cố của backend kéo theo **cả trang trắng**, thay vì chỉ hỏng lượt gọi API.
    #
    # Đặt qua biến thì nginx hoãn phân giải tới lúc có request: container lên
    # ngay, SPA phục vụ được, và chỉ những lượt gọi `/api` trả 502 trong lúc
    # backend còn chưa sẵn sàng.
    resolver ${NGINX_LOCAL_RESOLVERS} valid=10s ipv6=off;

    location /api/ {
        set $api_upstream ${API_UPSTREAM};
        proxy_pass $api_upstream;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # Backend đọc IP thật từ đây để ghi security event và tính hạn mức theo
        # IP. Thiếu thì mọi lượt đăng nhập trông như đến từ chính nginx, và một
        # người gõ sai mật khẩu sẽ khoá hạn mức của cả cơ quan.
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # Tải tệp đính kèm: 0 = không giới hạn ở tầng nginx, để hạn mức thật
        # nằm ở `settings` như quy tắc 12 (một chỗ chỉnh, có UI).
        client_max_body_size 0;
        proxy_request_buffering off;
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
