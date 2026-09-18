package com.songnhue.core.common.importer;

/**
 * Một cột của tệp nhập — <b>khai một lần, dùng cho cả bộ đọc lẫn tệp mẫu</b>.
 *
 * <h2>⭐ Vì sao danh mục cột phải là dữ liệu, ⛔ không phải một tệp mẫu tĩnh</h2>
 *
 * <p>Tới 09/09/2026 hộp thoại nhập danh mục công trình nói <i>"tải tệp Excel <b>đúng biểu mẫu</b>"</i>
 * trong khi kho ⛔ <b>không có một tệp mẫu nào</b>, và ⛔ không màn hình nào liệt kê một tên cột.
 * Người lập tệp phải đoán — mà tên cột được chuẩn hoá về không dấu/chữ thường/gạch dưới, nên
 * <i>"Mã CT"</i> và <i>"Mã công trình"</i> cho ra hai kết quả khác nhau và chỉ một cái chạy.
 *
 * <p>Đặt một tệp mẫu tĩnh vào {@code public/} chữa được triệu chứng <b>hôm nay</b> rồi lệch khỏi bộ
 * đọc vào ngày ai đó thêm cột — đúng luật 14 (<i>chỗ nào con người phải nhớ hai nơi thì chỗ đó cần
 * một phép kiểm nhớ hộ</i>). Ở đây ⛔ không cần phép kiểm ấy, vì <b>chỉ có một nơi</b>:
 * {@link BieuMauCsv#dung} in ra đúng danh sách mà lớp đọc dữ liệu đang đọc.
 *
 * @param ten tên cột <b>đã chuẩn hoá</b> ({@code ma_cong_trinh}) — đúng khoá mà
 *     {@link SpreadsheetReader.Row#get} nhận
 * @param batBuoc thiếu cột này thì từ chối cả tệp, ⛔ không đọc dòng nào
 * @param moTa quy cách của ô, in thành <b>dòng 2</b> của tệp mẫu. ⚠ Viết cho người lập tệp đọc, ⛔
 *     không phải cho lập trình viên: nêu giá trị hợp lệ và ví dụ, ⛔ đừng nêu tên kiểu Java
 */
public record CotMau(String ten, boolean batBuoc, String moTa) {

    public CotMau {
        if (ten == null || ten.isBlank()) {
            throw new IllegalArgumentException("Cột mẫu phải có tên — một cột không tên ⛔ không ai điền được");
        }
        if (moTa == null || moTa.isBlank()) {
            throw new IllegalArgumentException(
                    "Cột '" + ten + "' thiếu mô tả. Dòng mô tả LÀ tài liệu của tệp mẫu; bỏ trống nó là "
                            + "trả người lập tệp về đúng chỗ phải đoán (⛔ không có tệp mẫu nào cả)");
        }
    }

    /** Tên cột bắt buộc, theo đúng thứ tự khai — dùng cho phép kiểm "tệp thiếu cột nào". */
    public static java.util.List<String> tenBatBuoc(java.util.List<CotMau> cot) {
        return cot.stream().filter(CotMau::batBuoc).map(CotMau::ten).toList();
    }
}
