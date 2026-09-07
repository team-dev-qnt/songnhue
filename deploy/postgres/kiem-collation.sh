#!/usr/bin/env bash
# =============================================================================
# ĐO collation của cluster ĐANG CHẠY — không đọc tệp cấu hình, hỏi thẳng CSDL.
#
# Dùng: ./postgres/kiem-collation.sh          (chạy từ đâu cũng được)
#
# ⭐ VÌ SAO PHẢI ĐO CHỨ KHÔNG ĐỌC CẤU HÌNH
#
#   `POSTGRES_INITDB_ARGS` chỉ có tác dụng ở lượt dựng volume ĐẦU TIÊN. Sau đó
#   image bỏ qua nó hoàn toàn. Nên tệp compose và cluster thật có thể nói hai
#   điều khác nhau vĩnh viễn, và không lệnh nào báo sai:
#
#     · cluster dựng TRƯỚC khi dòng ấy được thêm  → tệp đúng, dữ liệu sai
#     · ai đó dựng tay bằng `docker run`          → tệp đúng, dữ liệu sai
#     · volume khôi phục từ máy khác              → tệp đúng, dữ liệu sai
#
#   Cả ba trường hợp đều đã hoặc sẽ xảy ra ở dự án này: cluster staging dựng
#   ngày 25/8 KHÔNG có tham số ấy (nó chỉ được thêm vào `compose.prod.yml` ngày
#   26/8), nên staging đang xếp `ORDER BY` tiếng Việt theo byte.
#
#   CLAUDE.md luật 3: canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH.
#   CLAUDE.md luật 8: hỏi thứ đại diện cho điều mình đang khẳng định.
#
# ⛔ SAI THÌ SỬA THẾ NÀO: không sửa tại chỗ được. `ALTER DATABASE` không đổi
#    được collation của một cluster đã có dữ liệu. Đường duy nhất là
#    pg_dump → `docker compose down` → xoá volume `postgres-data` → dựng lại →
#    restore. Quy trình: `docs/deploy-guideline.md` §2.7.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ⛔ KHÔNG `docker compose exec`: compose nội suy TOÀN BỘ tệp trước khi trả lời, kể
#    cả một lệnh chỉ đọc. `compose.prod.yml` khai `image: ${APP_IMAGE:?…}` cho ba
#    service, và ba biến ấy chỉ tồn tại bên trong lượt triển khai — script này chạy
#    ở một phiên SSH khác, nên compose sẽ dừng với "Thiếu APP_IMAGE" và người đọc
#    sẽ tưởng CSDL có vấn đề (§10.48 — đã hỏng đúng vậy hai lượt).
#    `ScriptDockerLookupTest` canh luật này; bản đầu của tệp này vi phạm và bị bắt.
. "$SCRIPT_DIR/../lib/docker-svc.sh"
CT_POSTGRES="$(container_cua postgres)"

# ⚠ Truyền SQL qua `$0` của `sh -c`, không nhúng vào chuỗi lệnh.
#   Câu SQL có dấu nháy đơn; nhúng nó vào `sh -c "..."` là bốn tầng trích dẫn
#   (bash cục bộ → ssh → docker → sh trong container) và mỗi tầng là một chỗ câu
#   lệnh có thể bị cắt sai mà vẫn chạy ra MỘT kết quả trông hợp lệ.
#   `$POSTGRES_DB` thì cố ý để shell TRONG container giải — tên CSDL là thứ image
#   tự biết, không cần chép từ ngoài vào.
hoi() {
    docker exec "$CT_POSTGRES" \
        sh -c 'psql -U postgres -d "$POSTGRES_DB" -tAc "$0"' "$1" | tr -d ' \r\n'
}

# ⚠ ASCII thuần trên đường truyền: 'Đ' = U+0110 = chr(272), 'ă' = U+0103 = chr(259).
#   Viết thẳng ký tự tiếng Việt vào đây thì nó đi qua ssh → bash → docker → psql,
#   mỗi chặng một cơ hội đổi bảng mã — và một phép so hỏng vì bảng mã trông y hệt
#   một collation sai. chr() chỉ có một nghĩa.
#
# Khẳng định: thứ tự đúng là Anh < Dung < Đăng < Em (Đ đứng sau D trong bảng chữ
# cái tiếng Việt). Sai thì sai theo kiểu nào là TUỲ locale mặc định — đo ngày
# 26/8 trên chính image postgis/postgis:16-3.4:
#
#     mặc định của image (glibc en_US.utf8) → Anh < Đăng < Dung < Em
#     locale C (so theo byte)               → Anh < Dung < Em < Đăng
#     ICU vi-VN                             → Anh < Dung < Đăng < Em  ✓
#
# Nên phép kiểm phải so với thứ tự ĐÚNG, đừng so với một kiểu sai cụ thể: cái sai
# thứ hai sẽ đi lọt.
sql_thu_tu="SELECT string_agg(n, '' ORDER BY n) = 'AnhDung' || chr(272) || chr(259) || 'ngEm' \
FROM (VALUES ('Em'), (chr(272) || chr(259) || 'ng'), ('Anh'), ('Dung')) AS t(n)"

# Chẩn đoán — `to_jsonb(d)->>` thay vì gọi thẳng tên cột: `daticulocale` đổi tên
# thành `datlocale` ở PG17, và một câu chẩn đoán không nên chết theo phiên bản.
sql_mo_ta="SELECT d.datlocprovider::text || ' | collate=' || d.datcollate || ' | icu=' \
|| coalesce(to_jsonb(d)->>'daticulocale', to_jsonb(d)->>'datlocale', '(không có)') \
FROM pg_database d WHERE d.datname = current_database()"

thu_tu="$(hoi "$sql_thu_tu")"
mo_ta="$(docker exec "$CT_POSTGRES" sh -c 'psql -U postgres -d "$POSTGRES_DB" -tAc "$0"' "$sql_mo_ta" | tr -d '\r')"

# ⚠ So với "t", KHÔNG so với "khác f". psql trả rỗng khi câu lệnh không chạy được,
#   và một phép so ngược sẽ biến "không đo được" thành "đạt".
if [ "$thu_tu" = "t" ]; then
    echo "✓ Collation ICU vi-VN đúng — ORDER BY xếp Anh < Dung < Đăng < Em"
    echo "  $mo_ta"
    exit 0
fi

echo "::error::Cluster KHÔNG dùng collation ICU vi-VN (psql trả '${thu_tu:-rỗng}')."
echo "  đo được : ${mo_ta:-không hỏi được pg_database}"
echo "  cần     : i | icu=vi-VN"
echo ""
echo "Hệ quả: mọi ORDER BY trên tên tiếng Việt xếp sai ở danh bạ nhân sự (MOD-04),"
echo "danh mục công trình (MOD-02) và mọi danh sách khác. Với locale mặc định của"
echo "image thì 'Đăng' chen lên TRƯỚC 'Dung'; với locale C thì nó rơi xuống sau 'Em'."
echo ""
echo "⛔ KHÔNG sửa được bằng cách thêm POSTGRES_INITDB_ARGS rồi deploy lại: tham số"
echo "   ấy chỉ chạy lúc TẠO cluster. Phải dump → xoá volume postgres-data → dựng"
echo "   lại → restore. Các bước: docs/deploy-guideline.md §2.7."
exit 1
