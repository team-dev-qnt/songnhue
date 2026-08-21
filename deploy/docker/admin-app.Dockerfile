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

# =============================================================================
# Header bảo mật — conventions.md §4.5
# =============================================================================
#
# ⚠⚠ Trước bản này image KHÔNG đặt một header bảo mật nào. Đo thật:
# `curl -I http://localhost:15173/` trả về đúng `Server`, `Date`, `Content-Type`,
# `ETag`, `Cache-Control` — không có `X-Frame-Options`, không `CSP`, không
# `X-Content-Type-Options`. Trong khi đó public-web đặt sẵn ba cái đầu, nên hai
# tầng phục vụ của cùng một hệ thống có hai mức bảo vệ khác hẳn nhau.
#
# ⚠⚠ VÌ SAO PHẢI ĐỂ RIÊNG MỘT TỆP RỒI `include`, chứ không đặt một lần ở khối
#    `server`: trong nginx, chỉ thị `add_header` **không cộng dồn** — một khối
#    `location` có `add_header` riêng sẽ **vứt bỏ toàn bộ** `add_header` kế thừa
#    từ cấp trên. Cấu hình này có `add_header Cache-Control` ở cả `/assets/` lẫn
#    `/`, tức là đúng hai khối phục vụ mọi thứ người dùng tải về. Đặt header bảo
#    mật ở cấp `server` rồi kiểm bằng `curl /` sẽ thấy chúng **biến mất**, và rất
#    dễ kết luận nhầm là "đã cấu hình rồi mà không chạy".
#
# CSP — hai lựa chọn có chủ đích, cả hai đều đã đo:
#
#   * `style-src` PHẢI có `'unsafe-inline'`. AntD 5 dùng cssinjs: nó tạo thẻ
#     `<style data-css-hash=…>` **lúc chạy** (đã kiểm trong bundle đã dựng). Với
#     `style-src 'self'` thì trình duyệt chặn sạch và giao diện quản trị hiện ra
#     không còn định dạng nào. Đường thoát duy nhất là `StyleProvider` + nonce
#     theo từng request, mà bundle Vite là tĩnh do nginx phục vụ nên không có
#     chỗ sinh nonce. Đây là cái giá của việc chọn AntD, ghi ra để không ai
#     tưởng là sơ suất.
#   * `script-src 'self'` thì AN TOÀN, và đó mới là vế quan trọng: đã kiểm
#     `index.html` của bản dựng — đúng **một** thẻ script và nó có `src`, không
#     có script nội tuyến nào. Đây là lớp chặn thật sự chống XSS.
#
# HSTS cố ý KHÔNG đặt ở đây: nó thuộc về nơi kết thúc TLS (nginx chung ở
# production, T11.5). Đặt ở tầng này thì local chạy HTTP nên trình duyệt bỏ qua,
# và nó tạo cảm giác đã có lớp bảo vệ mà thật ra chưa.
COPY <<'EOF' /etc/nginx/snippets/security-headers.conf
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; frame-src 'self' https://www.google.com https://www.youtube-nocookie.com https://player.vimeo.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'" always;
EOF

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

    # Mặc định cho các khối KHÔNG có `add_header` riêng (VD /api/).
    include /etc/nginx/snippets/security-headers.conf;

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
        # BẮT BUỘC lặp lại: `add_header` ở trên đã cắt đứt kế thừa từ cấp server.
        include /etc/nginx/snippets/security-headers.conf;
        try_files $uri =404;
    }

    location / {
        add_header Cache-Control "no-cache";
        # BẮT BUỘC lặp lại — xem ghi chú ở phần khai snippet trong Dockerfile.
        include /etc/nginx/snippets/security-headers.conf;
        try_files $uri $uri/ /index.html;
    }
}
EOF

COPY --from=build /build/admin-app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1
