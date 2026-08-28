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

from tracking_parser import (  # noqa: F401 — tái xuất để nơi gọi cũ không phải đổi
    HEADER,
    STATUS_BY_MARK,
    STATUS_BY_TEXT,
    TABLE_ROW,
    TABLE_SEPARATOR,
    TASK_ID_FORMS,
    TASK_LINE,
    TRACKING_FILE,
    SECTION_HEADING,
    SyncError,
    find_duplicate_task_ids,
    parse_markdown_to_data,
    van_tay_nguon,
)

# ── Vân tay của chính mã này TẠI THỜI ĐIỂM NẠP ──────────────────────────────────
#
# ⛔⛔ Máy chủ MCP sống lâu: nó `import` một lần rồi giữ trong bộ nhớ. Sửa mã trên đĩa KHÔNG chạm
#    tới tiến trình đang phục vụ. Ngày 28/08 điều đó làm bảng Công ty đọc mang ba trạng thái sai
#    suốt từ 27/08 19:36 (lúc bản vá T11.47 vào kho) — hai tiến trình đang chạy khởi động lúc
#    15:17 và 19:13, tức trước bản vá.
#
# ⚠ Đọc-ngược-sau-khi-ghi không đỡ được: tiến trình cũ ghi giá trị cũ rồi đọc lại thấy khớp với
#   CHÍNH NÓ. Chỉ phép so đĩa ↔ bộ nhớ phân biệt được hai trạng thái ấy (CLAUDE.md luật 9).
_NGUON = [
    os.path.join(os.path.dirname(os.path.abspath(__file__)), ten)
    for ten in ("tracking_parser.py", "server.py")
]
_VAN_TAY_LUC_NAP = van_tay_nguon(_NGUON)


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

    # ⛔ TRƯỚC MỌI VIỆC KHÁC: tiến trình này có đang chạy đúng mã trên đĩa không.
    if van_tay_nguon(_NGUON) != _VAN_TAY_LUC_NAP:
        raise SyncError(
            "Mã của bộ đồng bộ trên ĐĨA đã đổi sau khi tiến trình MCP này nạp nó, nên lượt ghi "
            "sắp tới sẽ dùng MÃ CŨ trong bộ nhớ — âm thầm, và bảng vẫn báo thành công. "
            "Đúng chuyện đã xảy ra ngày 28/08: ba trạng thái sai lên bảng Công ty đọc, trong đó "
            "T11.7 (secret production, đang làm) hiện 'Done'. "
            "Hãy KẾT NỐI LẠI máy chủ MCP (lệnh /mcp trong Claude Code) rồi đồng bộ lại."
        )

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
