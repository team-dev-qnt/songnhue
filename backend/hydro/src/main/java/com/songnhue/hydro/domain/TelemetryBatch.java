package com.songnhue.hydro.domain;

import java.util.List;
import java.util.Objects;

/**
 * Kết quả bóc tách <b>một</b> response — mọi con số mà quy tắc parse 10 đòi ghi vào {@code sync_logs}.
 *
 * <p>⚠ Ba bộ đếm dưới đây tách riêng vì chúng cần <b>ba cách xử lý ngược nhau</b>, và gộp lại thành
 * một con số "bỏ qua" là xoá mất chính thông tin ấy (§10.68-B):
 *
 * <ul>
 *   <li>{@link #soDongRac} — nguồn <b>đổi định dạng</b> hoặc gửi rác. Người phải xử lý: lập trình
 *       viên. Tăng đột ngột nghĩa là adapter sắp mù.
 *   <li>{@link #soDongTrung} — cùng {@code (mã, mốc)} hai lần trong <i>một</i> response. Người phải
 *       xử lý: <b>không ai</b> — {@code ON CONFLICT DO NOTHING} lo nốt; đếm chỉ để biết nguồn có
 *       đang tự lặp không.
 *   <li>{@link #nguonBaoHong} — nguồn nói thẳng {@code not.working}. Người phải xử lý: Admin, và
 *       việc phải làm là <b>xem lại mã số</b>. ⚠ Thiếu dấu {@code ;} cuối mã số cho ra <i>đúng chuỗi
 *       này</i>, nên câu hỏi đầu tiên luôn là "mã số còn dấu chấm phẩy không".
 * </ul>
 *
 * <p>⛔ Bản ghi này <b>không</b> biết mã nào đã khai, mã nào lạ — đó là việc của bước ánh xạ ở
 * poller (quy tắc parse 5). Adapter mà tự tra {@code stations} là adapter phải có CSDL để chạy, và
 * bài kiểm định dạng khi ấy thành bài kiểm tích hợp.
 *
 * @param soDo các dòng đã bóc được, đã bỏ trùng, giữ nguyên thứ tự nguồn gửi
 * @param soDongRac số dòng không khớp regex hoặc mang mốc thời gian không tồn tại — quy tắc 4
 * @param soDongTrung số dòng bị loại vì trùng {@code (mã, mốc)} — quy tắc 8
 * @param nguonBaoHong body chứa {@code not.working} — quy tắc 2
 */
public record TelemetryBatch(List<TelemetryReading> soDo, int soDongRac, int soDongTrung, boolean nguonBaoHong) {

    public TelemetryBatch {
        Objects.requireNonNull(soDo, "soDo");
        soDo = List.copyOf(soDo);
        if (soDongRac < 0 || soDongTrung < 0) {
            throw new IllegalArgumentException("Bộ đếm không âm: rác=" + soDongRac + " trùng=" + soDongTrung);
        }
        if (nguonBaoHong && !soDo.isEmpty()) {
            // Quy tắc 2 nói thẳng: not.working ⇒ KHÔNG ghi reading nào. Ép ở hàm dựng chứ không ở
            // lời dặn — một mẻ vừa "nguồn hỏng" vừa có số đo là một mẻ không ai biết phải tin nửa nào.
            throw new IllegalArgumentException("not.working mà vẫn có " + soDo.size()
                    + " số đo — quy tắc parse 2: nguồn báo hỏng thì không ghi bản ghi nào");
        }
    }

    /** Nguồn báo hỏng — không có gì để bóc. ⚠ Tên khác {@link #nguonBaoHong()} vì record đã chiếm tên ấy. */
    public static TelemetryBatch meBaoHong() {
        return new TelemetryBatch(List.of(), 0, 0, true);
    }

    /**
     * <b>Quy tắc parse 9</b> — nguồn trả thiếu dữ liệu.
     *
     * <p>Ngưỡng là "dưới 50% số điểm đo đang hoạt động". ⚠ Không phải một lỗi: nguồn cập nhật rải
     * trong cửa sổ {@code x1:30 → x8:30} nên một lượt gọi sớm <i>được phép</i> thiếu trạm. Nó là một
     * cảnh báo để người trực biết mà nhìn — và nó là lý do rate-limit dừng theo <i>đủ toàn bộ
     * trạm</i>, ⛔ không dừng theo <i>có bản ghi đầu tiên</i>.
     *
     * @param soDiemDoDangHoatDong ⚠ {@code 0} trả về {@code false}: chưa khai điểm đo nào thì mọi
     *     response đều "thiếu", và một cảnh báo kêu suốt vì một lý do ai cũng biết là một cảnh báo
     *     sẽ bị tắt — rồi vẫn tắt vào ngày nguồn hỏng thật (§10.42)
     */
    public boolean thieuDuLieu(int soDiemDoDangHoatDong) {
        return soDiemDoDangHoatDong > 0 && soDo.size() * 2 < soDiemDoDangHoatDong;
    }
}
