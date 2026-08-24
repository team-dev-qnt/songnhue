# MCP `google_sheets_sync`

Đẩy `.claude/master-tracking.md` lên một tab Google Sheets.

## Cấu hình — bắt buộc khai bằng biến môi trường

Quy tắc 11 của dự án cấm hardcode mọi thông tin kết nối. Bản trước ghi cứng `SHEET_ID` trong
`server.py`; nay công cụ **dừng ngay lúc gọi** nếu thiếu biến.

Khai ở chỗ đăng ký MCP server (`~/.claude.json` hoặc `.mcp.json` của dự án):

```jsonc
{
  "mcpServers": {
    "google_sheets_sync": {
      "command": ".agents/mcp/google_sheets_sync/venv/bin/python",
      "args": [".agents/mcp/google_sheets_sync/server.py"],
      "env": {
        "GSHEETS_SPREADSHEET_ID": "<id trong URL bảng tính>",
        "GSHEETS_SHEET_NAME": "Sheet1",
        "GSHEETS_CREDENTIALS_PATH": ".claude/google-credentials.json"
      }
    }
  }
}
```

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `GSHEETS_SPREADSHEET_ID` | ✅ | — (thiếu là dừng) |
| `GSHEETS_SHEET_NAME` | | `Sheet1` |
| `GSHEETS_CREDENTIALS_PATH` | | `.claude/google-credentials.json` |

⛔ `google-credentials.json` là khoá tài khoản dịch vụ — không commit, không dán vào issue.

## Bốn lưới an toàn, và vì sao mỗi cái tồn tại

| Lưới | Chuyện đã xảy ra nếu thiếu nó |
|---|---|
| Ghi trước, dọn sau | Bản trước `clear("A:F")` rồi mới `update`. Một lượt parse ra rỗng **xoá sạch bảng** rồi trả về `Success!` |
| Từ chối ghi khi parse ra 0 dòng | Cùng gốc như trên — chặn ở tầng dữ liệu thay vì trông chờ thứ tự lời gọi |
| Từ chối ghi khi mã số task trùng | Đo trên file thật: **29 mã số trùng, 19 cặp mâu thuẫn trạng thái**. Bảng hiện cái nào là tuỳ thứ tự dòng |
| Không nuốt lỗi | `except Exception` trả chuỗi làm hết hạn mức / sai quyền / mất mạng đều hiện ra như thông báo bình thường |

## Chạy phép kiểm

```bash
.agents/mcp/google_sheets_sync/venv/bin/python .agents/mcp/google_sheets_sync/test_parse.py
```

Phép kiểm chạy trên **chính** `.claude/master-tracking.md`, không trên chuỗi mẫu tự soạn. Lý do:
bộ đọc này hỏng đúng ở chỗ nó gặp cú pháp thật — regex cũ đòi khoảng trắng sau mã số trong khi
`conventions.md` §6 quy định dấu hai chấm, và kết quả là **310/310 dòng có cột Task ID rỗng** suốt
thời gian công cụ được coi là chạy tốt. Một bộ mẫu soạn tay không lộ ra điều đó, vì người soạn mẫu
chép lại chính giả định sai của mình.
