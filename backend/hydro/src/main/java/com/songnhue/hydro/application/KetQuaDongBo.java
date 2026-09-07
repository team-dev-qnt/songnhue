package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;

/**
 * Kết cục <b>một lượt đồng bộ</b> — dùng chung cho poller (WS-31) và nút <i>Gọi thử</i> (WS-30).
 *
 * <h2>⭐ Một kiểu, hai người gọi — cố ý</h2>
 *
 * <p>Bản trước tên {@code KetQuaGoiThu} và chỉ nút Gọi thử dùng. Giữ nguyên tên ấy rồi cho poller
 * dựng một kiểu thứ hai là mở ra đúng thứ luật 14 cấm: hai đường tính <b>cùng một bộ số</b>, rồi một
 * hôm chúng đếm khác nhau và không ai biết bên nào đúng. Nút Gọi thử <b>là</b> một lượt đồng bộ —
 * chỉ khác ở chỗ nó bỏ qua rate-limit, vì một con người vừa bấm nút và đang chờ câu trả lời.
 *
 * <h2>⛔⛔ KHÔNG mang thân phản hồi, và đó là điều kiện tồn tại của kiểu này</h2>
 *
 * <p>Thân phản hồi của {@code bhh40} chứa <b>chính mã số</b> ({@code <form action="…?key=…%3b">}, đo
 * 01/09/2026). Trả nó ra API là trả credential cho bất kỳ ai mở DevTools — vi phạm thẳng
 * {@code conventions.md} §4.7. Adapter đã che mã số trước khi thân đi đâu, nhưng ⛔ <b>không dựa vào
 * lớp che ấy làm lớp bảo vệ duy nhất</b>: bộ che là mã, mã thì có ngày sửa sai. Bản ghi này không có
 * chỗ nào để đặt thân vào — đó là bảo đảm ở tầng <i>cấu trúc</i>, thứ không sửa nhầm được (luật 12).
 *
 * <p>⇒ Người vận hành cần đối chiếu nguyên văn thì tra {@code hydro_raw_logs} — nơi có phân quyền,
 * có phân mảnh, có hạn lưu và có bộ che.
 *
 * <h2>⚠ Khác {@code SyncOutcome} thế nào</h2>
 *
 * <p>{@code SyncOutcome} là <b>mô hình GHI</b> của một dòng {@code sync_logs}: đúng những cột bảng
 * ấy có, với hai bất biến ép ở hàm dựng trùng khít hai ràng buộc CHECK. Bản ghi này là <b>mô hình
 * TRẢ VỀ</b>: nó mang thêm những thứ chẩn đoán không đáng lưu 90 ngày (danh sách mã lạ, số byte
 * thân, mốc đo của mẻ). Gộp làm một thì hoặc bảng phình thêm bốn cột không ai truy vấn, hoặc màn
 * hình mất bốn thông tin nó cần ngay lúc bấm nút.
 *
 * @param trangThai ⭐ bốn giá trị phân biệt được — {@link SyncStatus#SKIPPED_UP_TO_DATE} là kết cục
 *     <b>bình thường và mong muốn</b> của 4/5 lượt chạy, ⛔ không phải một lỗi
 * @param httpStatus {@code null} khi chưa nhận được phản hồi nào
 * @param durationMs thời gian lượt gọi — số này là thứ duy nhất phân biệt "nguồn treo" với "không có
 *     đường mạng" khi cả hai cùng hỏng
 * @param loi {@code null} khi không hỏng
 * @param lyDo câu ngắn cho người đọc; ⛔ đã qua bộ che mã số
 * @param soByteThan số byte thân <b>đã lưu</b> (sau khi che), 0 khi không nhận được gì
 * @param khungNhamToi mốc đầu khung 10' mà lượt này nhắm tới
 * @param soBanGhi số dòng bóc được từ thân
 * @param soGhiMoi ⭐ số dòng <b>thật sự mới</b> xuống {@code hydro_readings}. ⚠ Bằng 0 là bình thường
 *     ở 4/5 lượt (poll 2' trên nguồn 10'); nó chỉ đáng lo khi <i>kéo dài nhiều khung</i>
 * @param soTrungBoQua số dòng bị {@code ON CONFLICT DO NOTHING} bỏ qua vì đã có
 * @param soMaLa số dòng xuống {@code hydro_unmapped_readings} — mã nguồn chưa ai khai
 * @param soDongRac dòng không khớp định dạng — tăng đột ngột nghĩa là nguồn đổi định dạng
 * @param soDongTrung dòng trùng {@code (mã, mốc)} trong cùng một response
 * @param maChuaKhai ⭐ mã nguồn trả về mà {@code stations.api_code} chưa có — ⛔ <b>không tự tạo điểm
 *     đo</b> (quy tắc parse 5): bản suy đoán trước đó từ biểu tổng hợp đã sai 1/4 mã
 * @param soDiemDoDangHoatDong mẫu số của cả rate-limit lẫn quy tắc parse 9
 * @param soThieuLoaiChiSo ⚠ điểm đo có hồ sơ nhưng <b>chưa tích loại chỉ số</b> mà adapter đang giao
 *     — số đo <i>vẫn được ghi</i>, đây là lỗi danh mục cần người sửa (xem {@code DiemDoDich})
 * @param soKhacNguon điểm đo mà hồ sơ khai thuộc <b>nguồn khác</b> nhưng nguồn này cũng trả mã ấy
 * @param mocDoGanNhat mốc đo chung của mẻ; {@code null} khi không bóc được dòng nào
 * @param rawLogId dòng {@code hydro_raw_logs} vừa ghi — ⛔ {@code null} nghĩa là <b>không</b> ghi
 *     được (hoặc lượt gọi chưa từng xảy ra)
 * @param syncLogId dòng {@code sync_logs} vừa ghi — nuôi màn hình <i>Nhật ký đồng bộ</i>
 */
public record KetQuaDongBo(
        SyncStatus trangThai,
        Integer httpStatus,
        int durationMs,
        SyncFailureKind loi,
        String lyDo,
        int soByteThan,
        Instant khungNhamToi,
        int soBanGhi,
        int soGhiMoi,
        int soTrungBoQua,
        int soMaLa,
        int soDongRac,
        int soDongTrung,
        List<String> maChuaKhai,
        int soDiemDoDangHoatDong,
        int soThieuLoaiChiSo,
        int soKhacNguon,
        Instant mocDoGanNhat,
        Long rawLogId,
        Long syncLogId) {

    public KetQuaDongBo {
        Objects.requireNonNull(trangThai, "trangThai");
        maChuaKhai = maChuaKhai == null ? List.of() : List.copyOf(maChuaKhai);
    }

    /**
     * Lượt này lấy được dữ liệu dùng được không.
     *
     * <p>⚠ {@link SyncStatus#PARTIAL} tính là <b>thành công</b>: nguồn trả lời, ta ghi được, chỉ là
     * chưa đủ trạm — và với một nguồn đẩy rải rác trong cửa sổ 7 phút thì đó là chuyện thường của
     * lượt gọi sớm. ⛔ Đừng vẽ nó màu đỏ; màu đỏ dành cho {@link SyncStatus#FAILED}.
     */
    public boolean thanhCong() {
        return trangThai == SyncStatus.SUCCESS || trangThai == SyncStatus.PARTIAL;
    }

    /** <b>Quy tắc parse 9</b> — dưới 50% số điểm đo đang hoạt động. Suy từ trạng thái, ⛔ không lưu hai lần. */
    public boolean thieuDuLieu() {
        return trangThai == SyncStatus.PARTIAL;
    }

    /** Lượt cố ý không gọi vì toàn bộ điểm đo đã đủ dữ liệu của khung hiện tại — quy tắc 17. */
    public boolean boQua() {
        return trangThai == SyncStatus.SKIPPED_UP_TO_DATE;
    }
}
