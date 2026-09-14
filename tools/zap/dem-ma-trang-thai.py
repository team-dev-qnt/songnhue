# Hook cho zap-baseline.py (--hook=...) — đếm mã trạng thái HTTP của mọi lượt ZAP đã gửi.
#
# Vì sao cần: báo cáo baseline chỉ liệt kê CẢNH BÁO, ⛔ không liệt kê phản hồi. Một lượt quét mà
# phần lớn phản hồi là 429 (hạn mức PUBLIC 300/phút mỗi IP) vẫn in ra một báo cáo "ít cảnh báo" —
# trông như sạch, thật ra là chưa soi được gì. Tệp này ghi ra bằng chứng để phân biệt hai trạng thái.
#
# Định dạng tệp ra (TEP_MA_TRANG_THAI), mỗi dòng "<khoá> <số>":
#   tong 812
#   200 640
#   429 150
#
# ⛔ Mọi lỗi ở đây đều bị bắt: hook ném ngoại lệ sẽ làm zap-baseline thoát 3 và mất cả báo cáo.
# Khi đếm hỏng, tệp KHÔNG được ghi ⇒ script vỏ đọc là "KHÔNG ĐO ĐƯỢC", ⛔ không phải "0".

import os

TRANG = 500


def zap_pre_shutdown(zap):
    dich = os.environ.get("TEP_MA_TRANG_THAI", "/zap/wrk/ma-trang-thai.txt")
    try:
        tong = int(zap.core.number_of_messages())
        dem = {}
        bat_dau = 0
        da_doc = 0
        while bat_dau < tong:
            lo = zap.core.messages(start=str(bat_dau), count=str(TRANG))
            if not lo:
                break
            for tin in lo:
                dau = (tin.get("responseHeader") or "").split(" ", 2)
                ma = dau[1] if len(dau) > 1 and dau[1].isdigit() else "khong-co-phan-hoi"
                dem[ma] = dem.get(ma, 0) + 1
                da_doc += 1
            bat_dau += len(lo)
        if da_doc != tong:
            print("[dem-ma-trang-thai] doc %d/%d tin — khong ghi tep de tranh so lieu thieu" % (da_doc, tong))
            return
        with open(dich, "w") as tep:
            tep.write("tong %d\n" % tong)
            for ma in sorted(dem):
                tep.write("%s %d\n" % (ma, dem[ma]))
        print("[dem-ma-trang-thai] tong %d, 429 = %d" % (tong, dem.get("429", 0)))
    except Exception as e:  # noqa: BLE001 — cố ý bắt tất cả, xem đầu tệp
        print("[dem-ma-trang-thai] khong dem duoc: %s" % e)
