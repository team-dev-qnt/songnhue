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
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# `server` kéo theo fastmcp/google-api; khi thiếu chúng thì vẫn phải kiểm được phần thuần logic.
try:
    from server import (
        STATUS_BY_MARK,
        TASK_LINE,
        TRACKING_FILE,
        find_duplicate_task_ids,
        parse_markdown_to_data,
    )
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


TRACKING_FILE_ABS = os.path.join(_repo_root(), TRACKING_FILE)
TASKS = parse_markdown_to_data(TRACKING_FILE_ABS)

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


def test_dau_tich_thang_chu_trong_ghi_chu():
    """⭐⭐ Trạng thái parser trả về phải bằng ĐÚNG dấu tích, không bị chữ trong ghi chú ghi đè.

    Đo trên chính file tracking ngày 27/8: bản trước quét ``✅`` trên TOÀN dòng — kể cả cột Note —
    nên một dấu ``✅`` đánh dấu một ý phụ đã xong biến cả task thành ``Done``. Hai dòng bị báo sai,
    và cả hai đều là việc còn mở quan trọng nhất:

    * ``T11.45`` ``[ ]`` siết SSH (đang làm đỏ lượt deploy) → Sheet hiện **Done**
    * ``T11.7``  ``[~]`` secret production                  → Sheet hiện **Done**

    Công ty đọc Google Sheet, không đọc file markdown. Một task chưa làm hiện là đã làm thì không ai
    hỏi tới nó nữa — đúng hình dạng "đã tick không phải bằng chứng" của dự án, lần này ở tầng báo cáo.

    ⚠ Bài này phải so với ĐẦU RA CỦA PARSER (``TASKS``), không được tự suy lại trạng thái từ dấu tích
    rồi so với chính dấu tích ấy. Bản đầu của bài đã mắc đúng lỗi đó và **vẫn xanh sau khi gỡ bản
    vá** — một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì.
    """
    dau_tich = _dau_tich_theo_thu_tu()
    assert len(dau_tich) == len(TASKS), (
        f"{len(dau_tich)} dòng task trong file nhưng parser trả {len(TASKS)} dòng — "
        "phép ghép theo thứ tự không còn đúng, SỬA bài kiểm chứ đừng nới lỏng nó"
    )

    lech = []
    for mark, row in zip(dau_tich, TASKS):
        mong_doi = STATUS_BY_MARK.get(mark)
        if mong_doi and row[3] != mong_doi:
            lech.append(f"{row[1] or row[2][:40]}: dấu [{mark}] = {mong_doi} nhưng parser trả '{row[3]}'")
    assert not lech, f"{len(lech)} dòng có trạng thái khác dấu tích: {lech[:5]}"


def _dau_tich_theo_thu_tu():
    """Dấu trong ``- [x]`` của từng dòng task, ĐÚNG thứ tự parser duyệt file.

    Parser nối thêm một dòng cho mỗi ``TASK_LINE`` khớp, theo thứ tự đọc file — nên ghép theo chỉ số
    là ghép 1:1. Bài trên khẳng định độ dài khớp trước khi ghép, để phép ghép hỏng thì ĐỎ chứ không
    âm thầm so lệch hàng.
    """
    marks = []
    with open(TRACKING_FILE_ABS, encoding="utf-8") as handle:
        for raw in handle:
            match = TASK_LINE.match(raw.strip())
            if match:
                marks.append(match.group(1))
    return marks


def test_trang_thai_nam_trong_tap_da_biet():
    hop_le = {"Done", "In Progress", "Pending", "Cancelled", "Paused"}
    la = sorted({row[3] for row in TASKS} - hop_le)
    assert not la, f"trạng thái lạ: {la}"


def test_task_da_tick_khong_bi_ghi_chu_ha_trang_thai():
    """⛔ Một task tick ``[x]`` phải ra ``Done`` — ghi chú của nó không được hạ trạng thái.

    ⚠⚠ **Lỗi có thật, tìm ra ngày 28/08/2026, và nó đã sai sổ từ hôm trước.**

    ``_status_from_text`` quét ghi chú tìm ký hiệu trạng thái (``⬜``, ``❌``, ``🟡``…) để đọc được
    những dòng bảng không có ô tick. Nhưng nó cũng quét ghi chú của dòng **đã có tick**, nên một
    task hoàn thành mà ghi chú nhắc tới phần còn treo — ``"⬜ Nợ: màn hình quản trị chưa có nút"`` —
    bị hạ xuống ``Pending`` và lên Google Sheet dưới dạng *chưa làm*.

    Đo được lúc phát hiện: **T24.25** (tick từ 27/8) và **T25.13** đều báo ``Pending``. Không lệnh
    nào báo sai; markdown đọc đúng, Sheet đọc sai, và hai bên chỉ lệch nhau ở một ký tự trong ghi
    chú.

    Đây đúng họ với **T11.47** — *bản ghi tiến độ nói một đằng, thứ nó mô tả nằm một nẻo*. Cách
    chữa ở phía nội dung (đừng gắn ký hiệu trạng thái vào ghi chú của task đã xong) chứ không ở
    phía bộ đọc: nới lỏng ``_status_from_text`` sẽ làm hỏng những dòng bảng vốn dựa vào nó.
    """
    goc = _repo_root()
    duong = os.path.join(goc, TRACKING_FILE)
    tasks = parse_markdown_to_data(duong)
    theo_id = {row[1]: row for row in tasks}

    lech = []
    with open(duong, encoding="utf-8") as tep:
        for dong in tep:
            khop = TASK_LINE.match(dong)
            if not khop or khop.group(1) != "x":
                continue
            ma = re.match(r"\*{0,2}(T[\d.]+[a-z]?)\*{0,2}\s*:", khop.group(2).lstrip("*"))
            if not ma:
                continue
            hang = theo_id.get(ma.group(1))
            if hang and hang[3] != "Done":
                lech.append((ma.group(1), hang[3]))

    assert not lech, (
        "Những task tick [x] nhưng bộ đọc trả về trạng thái khác 'Done': "
        f"{lech}. Gần như chắc chắn ghi chú của chúng có chứa một ký hiệu trạng thái "
        "(⬜ ❌ 🟡 ⏸). Gỡ ký hiệu ra khỏi ghi chú, hoặc tách phần còn treo thành một task riêng."
    )


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
