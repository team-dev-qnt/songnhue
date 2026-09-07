# =============================================================================
# doc_env — đọc tệp .env của Docker Compose ĐÚNG theo luật của Compose
#
# ⛔ VÌ SAO TỒN TẠI: tệp .env KHÔNG phải script shell, và `source` nó là một lỗi
#    có thật, không phải chuyện lý thuyết. Lượt chạy thử đầu tiên của
#    `seed-staging.yml` chết ngay:
#
#        /opt/songnhue/.env: line 87: nofollow: command not found   (exit 127)
#
#    Dòng thủ phạm hoàn toàn HỢP LỆ với Compose:
#
#        ROBOTS_TAG=noindex, nofollow
#
#    Compose đọc thành chuỗi `noindex, nofollow`. Shell gán `ROBOTS_TAG=noindex,`
#    rồi CHẠY `nofollow` như một lệnh.
#
# ⚠⚠ Và cái bẫy này chỉ lộ ra ở STAGING: chỉ staging mới đặt
#    `ROBOTS_TAG=noindex, nofollow` (production phải cho đánh chỉ mục). Đo trực
#    tiếp trên hai tệp mẫu: `source prod.env.example` chạy lọt, còn
#    `source staging.env.example` chết ở dòng 137. Một tệp im lặng, một tệp nổ.
#
# Cách dùng — thay thẳng cho `set -a; . "$ENV_FILE"; set +a`:
#
#     . "$SCRIPT_DIR/../lib/read-env.sh"
#     set -a; eval "$(doc_env "$ENV_FILE")"; set +a          # nạp TẤT CẢ
#     eval "$(doc_env "$ENV_FILE" DB_NAME DB_USER)"          # chỉ vài khoá
#
# `set -a` bọc ngoài giữ nguyên hành vi tự-export của bản cũ.
# =============================================================================

# doc_env <đường-dẫn-.env> [TÊN...]
#   In ra từng dòng `TÊN=<giá trị đã trích dẫn an toàn>` để `eval`.
#   Không truyền TÊN nào = in tất cả khoá đọc được.
doc_env() {
  python3 - "$@" <<'PYENV'
import re, shlex, sys

duong_dan, *ten_can = sys.argv[1:]
gia_tri = {}

for dong in open(duong_dan, encoding='utf-8'):
    t = dong.rstrip('\n').lstrip()
    if not t or t.startswith('#'):
        continue
    if t.startswith('export '):
        t = t[7:].lstrip()
    if '=' not in t:
        continue
    ten, con = t.split('=', 1)
    ten = ten.strip()
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', ten):
        continue
    con = con.lstrip()
    if con[:1] == "'":
        # Nháy đơn: nguyên văn, không diễn giải gì.
        het = con.find("'", 1)
        gia_tri[ten] = con[1:] if het < 0 else con[1:het]
    elif con[:1] == '"':
        m = re.search(r'(?<!\\)"', con[1:])
        noi_dung = con[1:] if m is None else con[1:1 + m.start()]
        gia_tri[ten] = noi_dung.replace('\\n', '\n').replace('\\"', '"').replace('\\\\', '\\')
    else:
        # Chú thích cuối dòng CHỈ tính khi có KHOẢNG TRẮNG trước `#` — đúng luật
        # compose-go. Nhờ vậy `MAT_KHAU=abc#def` giữ nguyên cả đuôi, thay vì bị
        # cắt cụt thành `abc` rồi hỏng ở chỗ không ai ngờ.
        cat = re.search(r'\s#', con)
        gia_tri[ten] = (con[:cat.start()] if cat else con).strip()

# `shlex.quote` làm giá trị an toàn tuyệt đối khi đi qua `eval`: mật khẩu chứa
# dấu cách, `$`, nháy, hay `;` đều về đúng nguyên trạng và không chạy được gì.
for ten in (ten_can or gia_tri):
    if ten in gia_tri:
        print(f"{ten}={shlex.quote(gia_tri[ten])}")
PYENV
}
