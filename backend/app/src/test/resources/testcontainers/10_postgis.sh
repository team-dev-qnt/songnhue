#!/bin/sh
# =============================================================================
# Ghi đè script CÙNG TÊN có sẵn trong image `postgis/postgis` — cố ý để trống.
#
# Vì sao. Image mang sẵn `/docker-entrypoint-initdb.d/10_postgis.sh`, tạo thêm
# `postgis_topology` và `postgis_tiger_geocoder` (kéo theo 2 schema `topology`
# và `tiger`). Ở môi trường thật script đó KHÔNG BAO GIỜ chạy: `compose.infra.yml`
# bind-mount `deploy/postgres/init` đè lên cả thư mục, nên chỉ `10-bootstrap.sh`
# của dự án chạy và CSDL chỉ có postgis + unaccent + pg_trgm.
#
# Nhưng Testcontainers `withCopyFileToContainer` là CHÉP VÀO thư mục, không đè
# thư mục — nên trong test cả hai script cùng chạy. Kết quả: CSDL test có schema
# mà production không có.
#
# Đã trả giá thật (17/8): `pg_dump` bằng vai trò readonly đỏ trong test với
# "permission denied for schema tiger" — một lỗi không tồn tại ở production. Sai
# lệch kiểu này nguy hiểm theo cả hai chiều: bài kiểm đỏ vì thứ không có thật,
# và mã dùng hàm của `topology` thì xanh ở đây rồi hỏng khi chạy thật.
#
# Giữ tệp rỗng thay vì xoá: entrypoint chỉ đọc đúng thư mục này, không có cách
# nào gỡ tệp của image ra ngoài việc ghi đè lên nó.
# =============================================================================
echo "→ Bỏ qua 10_postgis.sh của image: extension do 10-bootstrap.sh của dự án tạo (khớp production)"
