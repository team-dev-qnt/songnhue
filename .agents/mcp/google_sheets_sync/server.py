"""Đồng bộ .claude/master-tracking.md lên Google Sheets.

Bốn thứ được sửa lại từ gốc trong bản này, mỗi thứ đều từng làm công cụ báo "Success!" trong khi
việc thật đã hỏng:

1.  **Ghi trước, dọn sau.** Bản trước gọi ``clear("A:F")`` rồi mới ``update``. Một lượt parse ra
    rỗng — vì đổi cú pháp file, vì sai đường dẫn, vì bất cứ gì — sẽ **xoá sạch bảng** rồi báo thành
    công. Nay: ghi đè dữ liệu mới trước, chỉ dọn phần dư phía dưới, và **từ chối ghi khi parse ra 0
    dòng**.
2.  **Đọc đúng cú pháp đã quy ước.** ``conventions.md`` §10 quy định
    ``- [x] T1.1: Tên task | Date: … | Note: …`` — có dấu hai chấm. Regex cũ đòi khoảng trắng
    (``^(T\\d+\\.\\d+)\\s+``) nên **không khớp một dòng nào**: đo trên file thật là 310/310 dòng có
    cột Task ID rỗng, còn mã số thì dính vào cột mô tả.
3.  **Một WS là một khoá.** Tiêu đề viết ``## WS-19`` và ``## 1. WS-19`` cho ra hai khoá nhóm khác
    nhau, nên cùng một hạng mục nằm rải ở hai chỗ trên bảng.
4.  **Không nuốt lỗi.** ``except Exception`` trả chuỗi làm mọi hỏng hóc — hết hạn mức, sai quyền,
    mất mạng — hiện ra như một câu thông báo bình thường; nơi gọi không phân biệt được với thành
    công.

Cấu hình đọc từ biến môi trường (quy tắc 11 của dự án — cấm hardcode):
``GSHEETS_SPREADSHEET_ID`` (bắt buộc), ``GSHEETS_SHEET_NAME`` (mặc định ``Sheet1``),
``GSHEETS_CREDENTIALS_PATH`` (mặc định ``.claude/google-credentials.json``).
"""

import logging
import os
import re
from collections import Counter

from fastmcp import FastMCP
from google.oauth2 import service_account
from googleapiclient.discovery import build

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("google_sheets_sync")

mcp = FastMCP("GoogleSheetsSync")

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
                status = _status_from_text(content, STATUS_BY_MARK.get(task_line.group(1), "Unknown"))
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


def find_duplicate_task_ids(tasks):
    """Mã số xuất hiện nhiều lần — mỗi cái là một chỗ hai dòng có thể nói ngược nhau."""
    counts = Counter(row[1] for row in tasks if row[1])
    return sorted(task_id for task_id, count in counts.items() if count > 1)


def _config():
    spreadsheet_id = os.environ.get("GSHEETS_SPREADSHEET_ID", "").strip()
    if not spreadsheet_id:
        raise SyncError(
            "Thiếu biến môi trường GSHEETS_SPREADSHEET_ID. Khai trong cấu hình MCP "
            "(.claude/settings.json → mcpServers.google_sheets_sync.env)."
        )
    sheet_name = os.environ.get("GSHEETS_SHEET_NAME", "Sheet1").strip() or "Sheet1"
    credentials_path = os.environ.get(
        "GSHEETS_CREDENTIALS_PATH", os.path.join(".claude", "google-credentials.json")
    )
    if not os.path.isabs(credentials_path):
        credentials_path = os.path.join(os.getcwd(), credentials_path)
    if not os.path.exists(credentials_path):
        raise SyncError(f"Không tìm thấy tệp thông tin xác thực: {credentials_path}")
    return spreadsheet_id, sheet_name, credentials_path


def _sheet_id_by_name(service, spreadsheet_id, sheet_name):
    """Tra ``sheetId`` thật thay vì giả định bằng 0 — tab đầu tiên không nhất thiết mang id 0."""
    meta = service.spreadsheets().get(spreadsheetId=spreadsheet_id).execute()
    for sheet in meta.get("sheets", []):
        properties = sheet.get("properties", {})
        if properties.get("title") == sheet_name:
            return properties.get("sheetId")
    raise SyncError(f"Bảng tính không có tab tên '{sheet_name}'.")


@mcp.tool()
def sync_markdown_to_sheets() -> str:
    """Đồng bộ .claude/master-tracking.md lên Google Sheet đã cấu hình.

    Từ chối ghi khi file không parse ra dòng nào, hoặc khi có mã số task trùng nhau.
    """
    logger.info("Bắt đầu đồng bộ tracking lên Google Sheets")

    try:
        spreadsheet_id, sheet_name, credentials_path = _config()
        tasks = parse_markdown_to_data(os.path.join(os.getcwd(), TRACKING_FILE))
    except SyncError as error:
        logger.error("Dừng trước khi ghi: %s", error)
        raise

    # ⛔ Lưới an toàn quan trọng nhất của công cụ này: không có nó thì một lượt parse hỏng sẽ ghi đè
    #    bảng bằng đúng một dòng tiêu đề, và câu trả lời trả về vẫn là "Success!".
    if not tasks:
        raise SyncError(
            f"{TRACKING_FILE} không parse ra dòng công việc nào — KHÔNG ghi gì lên bảng. "
            "Kiểm tra cú pháp theo conventions.md §10: '- [x] T1.1: Tên task | Date: … | Note: …'."
        )

    thieu_ma = sum(1 for row in tasks if not row[1])
    if thieu_ma == len(tasks):
        raise SyncError(
            f"Cả {len(tasks)} dòng đều không tách được mã số task — dấu hiệu cú pháp file đã đổi. "
            "KHÔNG ghi gì lên bảng."
        )

    trung = find_duplicate_task_ids(tasks)
    if trung:
        raise SyncError(
            f"Có {len(trung)} mã số task xuất hiện nhiều lần: {', '.join(trung[:20])}"
            f"{' …' if len(trung) > 20 else ''}. Hai dòng cùng mã số có thể mang trạng thái ngược "
            "nhau, và bảng sẽ hiện cái nào tuỳ thứ tự. Gộp lại trong master-tracking.md rồi chạy lại."
        )

    all_data = [HEADER] + tasks
    logger.info("Đã đọc %d dòng công việc, %d dòng thiếu mã số", len(tasks), thieu_ma)

    scopes = ["https://www.googleapis.com/auth/spreadsheets"]
    creds = service_account.Credentials.from_service_account_file(credentials_path, scopes=scopes)
    service = build("sheets", "v4", credentials=creds)
    sheet_id = _sheet_id_by_name(service, spreadsheet_id, sheet_name)

    # 1) GHI TRƯỚC. Bảng cũ còn nguyên cho tới lúc lượt ghi này thành công.
    result = (
        service.spreadsheets()
        .values()
        .update(
            spreadsheetId=spreadsheet_id,
            range=f"'{sheet_name}'!A1",
            valueInputOption="RAW",
            body={"values": all_data},
        )
        .execute()
    )
    updated_rows = result.get("updatedRows", 0)

    # 2) DỌN SAU, và chỉ dọn phần dư phía dưới dữ liệu vừa ghi.
    service.spreadsheets().values().clear(
        spreadsheetId=spreadsheet_id,
        range=f"'{sheet_name}'!A{len(all_data) + 1}:F",
        body={},
    ).execute()

    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": [
                {
                    "repeatCell": {
                        "range": {
                            "sheetId": sheet_id,
                            "startRowIndex": 0,
                            "endRowIndex": 1,
                            "startColumnIndex": 0,
                            "endColumnIndex": len(HEADER),
                        },
                        "cell": {
                            "userEnteredFormat": {
                                "textFormat": {"bold": True},
                                "backgroundColor": {"red": 0.9, "green": 0.9, "blue": 0.9},
                            }
                        },
                        "fields": "userEnteredFormat(textFormat,backgroundColor)",
                    }
                }
            ]
        },
    ).execute()

    logger.info("Đồng bộ xong %d dòng", updated_rows - 1)
    return f"Đã đồng bộ {updated_rows - 1} công việc lên tab '{sheet_name}'."


if __name__ == "__main__":
    mcp.run()
