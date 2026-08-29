"""Phần ĐỌC của công cụ đồng bộ tracking — tách khỏi ``server.py`` ngày 27/8.

⭐ VÌ SAO TÁCH

``test_parse.py`` chỉ cần logic đọc, nhưng nó phải ``import server``, mà ``server`` kéo theo
``fastmcp`` + ``google-api-python-client``. Máy chưa dựng venv thì import hỏng, và bản trước xử lý
bằng::

    except ImportError as error:
        print(f"BỎ QUA: chưa cài phụ thuộc của server.py ({error})")
        sys.exit(0)

**``sys.exit(0)``** — tức là bộ kiểm *xanh mà không kiểm gì*. Suốt thời gian ấy nó cũng không nằm
trong ``ci.yml`` lẫn ``Makefile``, nên chưa cổng nào chạy nó. Hai chuyện cộng lại: 9 phép kiểm canh
**nguồn sự thật DUY NHẤT về task và nợ** của dự án mà không ai chạy, và nếu có chạy thì cũng xanh
giả (CLAUDE.md luật 1 và luật 7).

Tệp này không import gì ngoài thư viện chuẩn, nên bộ kiểm chạy được ở mọi nơi — kể cả runner CI
không cài gì thêm. ``server.py`` import lại từ đây, nên hành vi của công cụ MCP không đổi.
"""

import os
import hashlib
import pathlib
import re
from collections import Counter


TRACKING_FILE = ".claude/master-tracking.md"
HEADER = ["Phase/WS", "Task ID", "Description", "Status", "Date", "Notes"]

STATUS_BY_MARK = {
    "x": "Done",
    " ": "Pending",
    "~": "In Progress",
    "✅": "Done",
    "🟡": "In Progress",
    "⬜": "Pending",
    "❌": "Cancelled",
    "⏸": "Paused",
}

TASK_LINE = re.compile(r"^\s*-\s+\[(x| |~|✅|🟡|⬜|❌|⏸)\]\s+(.*)$")

# ⭐ Cú pháp chính thức theo conventions.md §10: "T1.1:" — dấu hai chấm, không phải khoảng trắng.
#    Hai dạng còn lại (in đậm, khoảng trắng) giữ lại để đọc được các dòng viết trước khi §10 ra đời.
TASK_ID_FORMS = (
    re.compile(r"^\*\*(?P<id>[A-Z]+\d+\.\d+(?:-?[a-z])?)\*\*[:.]?\s*(?P<rest>.*)$"),
    re.compile(r"^(?P<id>[A-Z]+\d+\.\d+(?:-?[a-z])?)\s*:\s*(?P<rest>.*)$"),
    re.compile(r"^(?P<id>[A-Z]+\d+\.\d+(?:-?[a-z])?)\s+(?P<rest>.*)$"),
)

# "## WS-19", "## 1. WS-19 — Tình hình vận hành", "## Phase 1" đều phải quy về MỘT khoá.
#
# ⚠ `DoD Phase n` phải đứng TRƯỚC nhánh `Phase\s*\d+` trong phép hoặc, và phải được nhận diện —
#   bản trước không khớp tiêu đề này nên 38 dòng Definition of Done **thừa kế khoá của mục phía
#   trên** và nằm gọn dưới `WS-22` trên bảng. Không có lỗi nào báo ra: khoá "WS-22" vẫn là một khoá
#   hợp lệ, chỉ là gán sai chỗ.
SECTION_HEADING = re.compile(
    r"^#{2,3}\s+(?:\d+\.\s*)?(?P<key>DoD\s+Phase\s*\d+|WS-\d+|VM-\d+|Phase\s*\d+)\b", re.IGNORECASE
)

TABLE_SEPARATOR = re.compile(r"^\s*\|[-:| ]+\|[-:| ]+\|")
TABLE_ROW = re.compile(r"^\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|(.*)\|$")

STATUS_BY_TEXT = (
    ("✅", "Done"),
    ("Xong", "Done"),
    ("🟡", "In Progress"),
    ("Đang làm", "In Progress"),
    ("⬜", "Pending"),
    ("Chưa làm", "Pending"),
    ("❌", "Cancelled"),
    ("⏸", "Paused"),
)


class SyncError(RuntimeError):
    """Hỏng hóc đã được nhận diện và có câu trả lời rõ ràng cho người dùng."""


def _split_task_id(content):
    """Tách mã số khỏi phần mô tả. Trả về ``(task_id, phần còn lại)``."""
    for form in TASK_ID_FORMS:
        match = form.match(content)
        if match:
            return match.group("id"), match.group("rest").strip()
    return "", content


def _split_metadata(rest):
    """Bóc ``| Date: … | Note: …`` khỏi phần mô tả."""
    if "|" not in rest:
        return rest.strip(" -*"), "", ""

    parts = [p.strip() for p in rest.split("|")]
    description = parts[0].strip(" -*")
    date_str = ""
    note_str = ""
    for part in parts[1:]:
        lowered = part.lower()
        if lowered.startswith("date:"):
            date_str = part[5:].strip()
        elif lowered.startswith("note:"):
            note_str = part[5:].strip()
        elif not date_str and re.fullmatch(r"\d{1,2}/\d{1,2}/\d{4}", part):
            date_str = part
        elif not note_str:
            note_str = part
    return description, date_str, note_str


def _status_from_text(text, fallback):
    for needle, status in STATUS_BY_TEXT:
        if needle in text:
            return status
    return fallback


def parse_markdown_to_data(input_path):
    """Đọc file tracking thành các dòng ``[WS, TaskID, Mô tả, Trạng thái, Ngày, Ghi chú]``."""
    if not os.path.exists(input_path):
        raise SyncError(f"Không tìm thấy file tracking: {input_path}")

    tasks = []
    current_section = ""
    in_table = False

    with open(input_path, "r", encoding="utf-8") as handle:
        for raw_line in handle:
            line = raw_line.strip()

            heading = SECTION_HEADING.match(line)
            if heading:
                current_section = " ".join(heading.group("key").upper().split())
                in_table = False
                continue

            if TABLE_SEPARATOR.match(line):
                in_table = True
                continue

            task_line = TASK_LINE.match(line)
            if task_line:
                content = task_line.group(2).strip()
                # ⛔ DẤU TÍCH THẮNG, không để phần chữ ghi đè.
                #
                # Bản trước gọi `_status_from_text(content, …)` trên TOÀN dòng — kể cả cột Note. Mà
                # Note thì thường xuyên có `✅` để đánh dấu một Ý PHỤ đã xong ("✅ Không phải sự cố
                # xâm nhập", "✅ repo secret `NVD_API_KEY`"), và một dấu ấy biến cả task thành Done.
                #
                # Đo trên chính file tracking ngày 27/8: **2 dòng** bị báo sai, và cả hai đều là việc
                # còn mở quan trọng nhất — T11.45 `[ ]` (siết SSH, đang làm đỏ deploy) và T11.7 `[~]`
                # (secret production) đều hiện **Done** trên Google Sheet mà Công ty đọc.
                #
                # Dấu tích là thứ người viết CỐ Ý đặt; chữ trong ghi chú thì không. Chỉ khi dấu tích
                # không nhận ra được mới đi đoán theo chữ.
                dau_tich = STATUS_BY_MARK.get(task_line.group(1))
                status = dau_tich if dau_tich else _status_from_text(content, "Unknown")
                task_id, rest = _split_task_id(content)
                description, date_str, note_str = _split_metadata(rest)
                tasks.append([current_section, task_id, description, status, date_str, note_str])
                continue

            table_row = TABLE_ROW.match(line) if in_table else None
            if table_row:
                id_col = table_row.group(1).strip()
                if id_col.lower() in ("id", "mã", "ws", "task"):
                    continue
                if not re.match(r"^(T\d+\.\d+|WS-\d+)", id_col):
                    continue
                status_col = table_row.group(3).strip()
                tasks.append([
                    current_section,
                    id_col,
                    table_row.group(2).strip(),
                    _status_from_text(status_col, status_col),
                    "",
                    (table_row.group(4) or "").strip(" |"),
                ])

    return tasks


def van_tay_nguon(duong_dan):
    """Vân tay SHA-256 của các tệp NGUỒN của bộ đồng bộ, ghép lại theo thứ tự.

    ⛔⛔ VÌ SAO CẦN — chuyện đã xảy ra ngày 28/08

    Máy chủ MCP là một tiến trình SỐNG LÂU: nó ``import`` bộ đọc đúng một lần lúc khởi động rồi
    giữ trong bộ nhớ. Bản vá T11.47 (dấu tích thắng phần chữ) vào kho lúc **27/08 19:36**, nhưng
    hai tiến trình MCP đang phục vụ khởi động lúc **15:17** và **19:13** — cả hai trước đó. Nên
    mọi lượt đồng bộ sau bản vá vẫn chạy MÃ CŨ, và ghi lên bảng Công ty đọc ba trạng thái sai:
    ``T11.7`` (secret production, đang làm) hiện **Done**, ``T11.50`` và ``T25.29`` (đã xong)
    hiện **Pending**.

    ⚠ Đọc-ngược-sau-khi-ghi KHÔNG bắt được lớp lỗi này: tiến trình cũ ghi giá trị cũ rồi đọc lại
    thấy khớp với chính nó. Thứ duy nhất phân biệt được là so mã TRÊN ĐĨA với mã ĐÃ NẠP.

    Cùng họ với luật 10 — *xác nhận bản đã sửa THẬT SỰ được nạp* — và với §10.56: trạng thái tích
    luỹ ngoài tệp nguồn không suy ra được từ tệp nguồn.
    """
    bam = hashlib.sha256()
    for duong in duong_dan:
        bam.update(pathlib.Path(duong).read_bytes())
    return bam.hexdigest()


def find_duplicate_task_ids(tasks):
    """Mã số xuất hiện nhiều lần — mỗi cái là một chỗ hai dòng có thể nói ngược nhau."""
    counts = Counter(row[1] for row in tasks if row[1])
    return sorted(task_id for task_id, count in counts.items() if count > 1)
