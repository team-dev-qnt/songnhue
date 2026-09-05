package com.songnhue.hydro.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sức khoẻ đường ingest trong một cửa sổ thời gian — dải tóm tắt trên đầu màn hình <i>Nhật ký đồng
 * bộ</i> (T31.13).
 *
 * <h2>⭐ Vì sao cần một dải tóm tắt bên cạnh danh sách</h2>
 *
 * <p>Poller chạy 2 phút một lượt ⇒ <b>720 dòng mỗi ngày cho một nguồn</b>. Một danh sách 720 dòng
 * không trả lời được câu hỏi người vận hành thật sự hỏi — <i>"đêm qua có gì hỏng không"</i> — vì câu
 * trả lời nằm ở <b>tỉ lệ</b>, không nằm ở dòng nào cả.
 *
 * <h2>⛔⛔ Hai bản đồ phải mang ĐỦ MỌI KHOÁ, kể cả khoá bằng 0 — ép ở hàm dựng</h2>
 *
 * <p>Quy tắc 16: <b>số 0 là một câu khẳng định</b>. "Trong 24 giờ qua có <b>0</b> lượt
 * {@code NOT_WORKING}" là một điều đã đo được và là điều người đọc cần thấy; bỏ khoá ấy đi thì màn
 * hình chỉ còn <i>im lặng</i>, mà im lặng đọc giống hệt "chưa ai đo".
 *
 * <p>Và ràng buộc ấy ép ở <b>hàm dựng</b>, ⛔ không ở lời dặn nơi gọi: một câu
 * {@code GROUP BY status} trên bảng rỗng trả về <b>không dòng nào</b>, nên bản đồ thiếu khoá là kết
 * quả <i>mặc định</i> của đường đi tự nhiên nhất. Thứ gì là mặc định thì phải bị chặn ở chỗ hẹp
 * nhất.
 *
 * @param tuMoc đầu cửa sổ đo — ⚠ mọi con số dưới đây <b>chỉ nói về khoảng từ mốc này tới bây giờ</b>
 * @param mocGanNhat lượt polling gần nhất bất kể kết cục; {@code null} = <b>không có lượt nào trong
 *     cửa sổ</b>, và đó là triệu chứng nặng hơn mọi con số lỗi — poller đã đứng
 */
public record TongHopDongBo(
        Instant tuMoc,
        long soLuot,
        Map<SyncStatus, Long> theoTrangThai,
        Map<SyncFailureKind, Long> theoLoi,
        Instant mocGanNhat) {

    public TongHopDongBo {
        Objects.requireNonNull(tuMoc, "tuMoc");
        theoTrangThai = daDuKhoa(theoTrangThai, SyncStatus.values(), "theoTrangThai");
        theoLoi = daDuKhoa(theoLoi, SyncFailureKind.values(), "theoLoi");
    }

    private static <E extends Enum<E>> Map<E, Long> daDuKhoa(Map<E, Long> dem, E[] moiGiaTri, String ten) {
        Objects.requireNonNull(dem, ten);
        Map<E, Long> du = new EnumMap<>(moiGiaTri[0].getDeclaringClass());
        for (E giaTri : moiGiaTri) {
            Long so = dem.get(giaTri);
            du.put(giaTri, so == null ? 0L : so);
        }
        if (!du.keySet().containsAll(dem.keySet())) {
            throw new IllegalArgumentException(ten + " mang khoá không thuộc enum: " + dem.keySet());
        }
        return Map.copyOf(du);
    }

    /** Số lượt <b>đã mở kết nối và hỏng</b> — ⛔ không tính {@code THIEU_MA_SO} (chưa gọi lần nào). */
    public long soLuotGoiHong() {
        return theoLoi.entrySet().stream()
                .filter(e -> e.getKey().duocGhiVaoRawLog())
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
