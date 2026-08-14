# syntax=docker/dockerfile:1.7
# =============================================================================
# Backend — build đa tầng: BIÊN DỊCH TỪ MÃ NGUỒN LOCAL, ngay trong container.
#
# Máy chạy KHÔNG cần cài JDK hay Maven — đây là điểm mấu chốt để người làm FE
# dựng được backend mà không phải dựng môi trường Java.
#
# Cùng một image dùng cho 2 việc:
#   • service `app`      → ENTRYPOINT mặc định, chạy web
#   • service `migrator` → thêm tham số --spring.profiles.active=migrate,
#                          chạy Flyway rồi thoát (architecture-review.md §9.2)
#
# Build context là thư mục `backend/`.
# =============================================================================

# --- Tầng build --------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Chép pom trước, tải dependency trước, rồi mới chép mã nguồn: sửa code mà không
# đụng pom thì Docker dùng lại lớp cache dependency, build lại chỉ còn vài chục giây.
COPY .mvn .mvn
COPY pom.xml .
COPY core/pom.xml core/
COPY content/pom.xml content/
COPY operations/pom.xml operations/
COPY hydro/pom.xml hydro/
COPY hr/pom.xml hr/
COPY app/pom.xml app/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline -DskipTests

COPY core core
COPY content content
COPY operations operations
COPY hydro hydro
COPY hr hr
COPY app app

# Bỏ qua lint và test trong image build — hai việc đó đã chạy ở `make lint` /
# `make test` và ở CI. Image build chỉ để ra artifact.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true

# --- Tầng chạy ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Chạy bằng người dùng thường, không phải root (conventions.md §4.5)
RUN addgroup -S songnhue && adduser -S -G songnhue -h /app songnhue
WORKDIR /app

COPY --from=build --chown=songnhue:songnhue /build/app/target/songnhue-app.jar app.jar

USER songnhue
EXPOSE 8080

# Dùng readiness chứ không phải liveness: chỉ báo khỏe khi đã sẵn sàng nhận
# request, để `depends_on: service_healthy` không thả request vào lúc còn khởi động.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null \
        | grep -q '"status":"UP"' || exit 1

# MaxRAMPercentage: JVM tự co theo giới hạn bộ nhớ của container thay vì đọc RAM
# của cả máy chủ — không có dòng này thì heap phình ra và bị OOM-kill.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
