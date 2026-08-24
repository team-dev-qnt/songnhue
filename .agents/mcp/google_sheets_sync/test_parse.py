"""Phép kiểm bộ đọc file tracking — chạy trên CHÍNH .claude/master-tracking.md.

Vì sao chạy trên file thật chứ không trên chuỗi mẫu tự soạn: bộ đọc này hỏng đúng ở chỗ nó gặp cú
pháp thật. Bản trước có regex đòi khoảng trắng sau mã số (``T1.1 Tên``) trong khi file viết theo
``conventions.md`` §10 là dấu hai chấm (``T1.1: Tên``) — đo trên file thật cho ra **310/310 dòng có
cột Task ID rỗng**. Một bộ dữ liệu mẫu soạn tay sẽ không bao giờ lộ ra điều đó, vì người soạn mẫu
chép lại chính giả định sai của mình.

Chạy: ``python3 -m pytest .agents/mcp/google_sheets_sync/test_parse.py`` từ gốc repo,
hoặc ``python3 .agents/mcp/google_sheets_sync/test_parse.py`` (không cần pytest).
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# `server` kéo theo fastmcp/google-api; khi thiếu chúng thì vẫn phải kiểm được phần thuần logic.
try:
    from server import TRACKING_FILE, find_duplicate_task_ids, parse_markdown_to_data
except ImportError as error:  # pragma: no cover - chỉ chạy khi venv chưa dựng
    print(f"BỎ QUA: chưa cài phụ thuộc của server.py ({error})")
    sys.exit(0)


def _repo_root():
    directory = os.path.dirname(os.path.abspath(__file__))
    while directory != "/":
        if os.path.isdir(os.path.join(directory, ".claude")):
            return directory
        directory = os.path.dirname(directory)
    raise AssertionError("Không tìm thấy gốc repo (thư mục chứa .claude)")


TASKS = parse_markdown_to_data(os.path.join(_repo_root(), TRACKING_FILE))

# Dòng sổ nợ liên WS — không phải công việc nên không mang mã số. Xem conventions.md §6.
NOTE_PREFIXES = ("Nhận nợ", "Trả nợ", "Kèm theo")


def test_doc_ra_duoc_cong_viec():
    """Tập rỗng làm mọi khẳng định bên dưới đúng một cách vô nghĩa — luật 7."""
    assert len(TASKS) > 100, f"chỉ đọc được {len(TASKS)} dòng, file tracking có hàng trăm"


def test_moi_dong_deu_tach_duoc_ma_so_hoac_la_ghi_chu():
    """⭐ Đây là bài đáng lẽ phải có từ đầu — nó bắt đúng lỗi 310/310.

    File có hai loại dòng: **công việc** (bắt buộc có mã số) và **ghi chú sổ nợ** (mở đầu bằng
    "Nhận nợ" / "Trả nợ" / "Kèm theo"). Cho phép loại thứ hai vắng mã số, nhưng KHÔNG cho phép một
    dòng công việc lặng lẽ rơi vào nhóm đó — nếu cú pháp mã số đổi lần nữa, mô tả sẽ không khớp tiền
    tố nào và bài này đỏ.
    """
    thieu = [row for row in TASKS if not row[1]]
    khong_phai_ghi_chu = [row for row in thieu if not row[2].startswith(NOTE_PREFIXES)]
    assert not khong_phai_ghi_chu, (
        f"{len(khong_phai_ghi_chu)}/{len(TASKS)} dòng không tách được mã số và cũng không phải ghi "
        f"chú sổ nợ. Ví dụ: {[row[2][:60] for row in khong_phai_ghi_chu[:3]]}"
    )
    assert len(thieu) < len(TASKS) * 0.2, (
        f"{len(thieu)}/{len(TASKS)} dòng vắng mã số — quá nhiều để là ghi chú sổ nợ, nhiều khả năng "
        "cú pháp mã số đã đổi và bộ đọc không còn khớp"
    )


def test_ma_so_khong_dinh_vao_mo_ta():
    """Mã số đã tách rồi thì không được còn nguyên ở đầu cột mô tả."""
    dinh = [row for row in TASKS if row[1] and row[2].startswith(row[1])]
    assert not dinh, f"{len(dinh)} dòng còn mã số dính trong mô tả: {[r[2][:50] for r in dinh[:3]]}"


def test_khoa_nhom_khong_bi_tach_lam_hai():
    """``## WS-19`` và ``## 1. WS-19`` phải quy về MỘT khoá, không thành hai nhóm rời."""
    khoa = {row[0] for row in TASKS if row[0]}
    xau = [k for k in khoa if not k.upper().startswith(("WS-", "VM-", "PHASE", "DOD"))]
    assert not xau, f"khoá nhóm không chuẩn hoá: {xau}"


def test_ma_so_khop_voi_nhom_chua_no():
    """⭐⭐ Mã số phải khớp nhóm đang chứa nó — bài bắt được lỗi "gán nhầm mục".

    Bài trên chỉ khẳng định khoá nhóm *có dạng đúng*, nên nó xanh trọn vẹn khi 38 dòng
    ``DOD1.x``/``DOD0.x`` thừa kế khoá ``WS-22`` của mục phía trên (regex tiêu đề không nhận
    ``## DoD Phase n``). Một khoá hợp lệ đặt sai chỗ vẫn là một khoá hợp lệ.

    Ràng buộc thật là: ``T19.4`` chỉ được nằm trong ``WS-19``, ``DOD1.7`` chỉ được nằm trong một
    mục DoD. Đó là thứ phân biệt được "đúng nhóm" với "đúng dạng".
    """
    lech = []
    for nhom, ma, mo_ta, *_ in TASKS:
        if not ma:
            continue
        if ma.upper().startswith("DOD"):
            if not nhom.upper().startswith("DOD"):
                lech.append(f"{ma} nằm trong {nhom}")
        elif ma[0].isalpha():
            so_ws = ma[1:].split(".")[0]
            if nhom.upper() != f"WS-{so_ws}":
                lech.append(f"{ma} nằm trong {nhom}, đáng lẽ WS-{so_ws}")
    assert not lech, f"{len(lech)} dòng gán nhầm mục: {lech[:8]}"


def test_khong_co_ma_so_trung():
    """Hai dòng cùng mã số có thể mang trạng thái ngược nhau, và bảng hiện cái nào là tuỳ thứ tự."""
    trung = find_duplicate_task_ids(TASKS)
    assert not trung, f"{len(trung)} mã số trùng: {trung[:20]}"


def test_trang_thai_nam_trong_tap_da_biet():
    hop_le = {"Done", "In Progress", "Pending", "Cancelled", "Paused"}
    la = sorted({row[3] for row in TASKS} - hop_le)
    assert not la, f"trạng thái lạ: {la}"


if __name__ == "__main__":
    that_bai = 0
    for ten, ham in sorted(globals().items()):
        if not ten.startswith("test_") or not callable(ham):
            continue
        try:
            ham()
            print(f"  ✓ {ten}")
        except AssertionError as error:
            that_bai += 1
            print(f"  ✗ {ten}\n      {error}")
    print(f"\n{len(TASKS)} dòng đọc được — {that_bai} phép kiểm đỏ")
    sys.exit(1 if that_bai else 0)
