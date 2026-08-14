# syntax=docker/dockerfile:1.7
# =============================================================================
# public-web (Next.js + Tailwind) — chế độ standalone.
#
# ⬜ CHƯA BUILD ĐƯỢC cho tới khi WS-9 tạo `frontend/public-web`.
#
# Build context là thư mục `frontend/`.
#
# ⚠ Cần `output: 'standalone'` trong next.config — nếu không thì thư mục
#   `.next/standalone` không tồn tại và tầng runtime chép hụt. WS-9 phải bật.
#
# ⚠ Biến `NEXT_PUBLIC_*` nhúng vào bundle lúc build (giống Vite) → đổi là phải
#   build lại image.
# =============================================================================

FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
COPY admin-app/package.json admin-app/
COPY public-web/package.json public-web/
RUN npm ci

COPY . .

ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build --workspace public-web

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1

RUN addgroup -S nodejs && adduser -S -G nodejs nextjs

# Bản standalone đã gói sẵn phần node_modules cần thiết — không cần npm ci lại
COPY --from=build --chown=nextjs:nodejs /build/public-web/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /build/public-web/.next/static ./public-web/.next/static
COPY --from=build --chown=nextjs:nodejs /build/public-web/public ./public-web/public

USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/api/health >/dev/null 2>&1 || exit 1

CMD ["node", "public-web/server.js"]
