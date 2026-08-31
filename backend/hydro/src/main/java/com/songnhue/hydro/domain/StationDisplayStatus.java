package com.songnhue.hydro.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Trạng thái điểm đo <b>hiển thị</b> — giá trị DẪN XUẤT, không có cột nào trong CSDL.
 *
 * <h2>⛔ Vì sao không lưu, dù {@code .claude/phase2-plan.md} T28.3 mô tả một cột {@code status} 4
 * giá trị</h2>
 *
 * <p>Bốn giá trị ấy trộn hai bản chất: {@code NGUNG} là quyết định của <b>con người</b>, ba giá trị
 * còn lại là kết luận của <b>máy</b>. Lưu chung một cột thì lần đầu poller ghi {@code MAT_TIN_HIEU}
 * là xoá mất quyết định {@code NGUNG}; tới lúc trạm có tín hiệu lại, một điểm đo đã ngừng tự quay về
 * hoạt động. Đúng cái bẫy mà {@code Construction} đã phải tách làm hai cột mới thoát ra được.
 *
 * <p>Nhưng ở đây còn một lý do mạnh hơn, và nó là lý do khiến vế máy <b>không được lưu ở đâu cả</b>:
 * một cột trạng thái tín hiệu chỉ đúng khi có ai đó cập nhật nó, mà người cập nhật duy nhất là
 * poller. <b>Poller chết là lúc trạng thái ấy quan trọng nhất, và cũng chính là lúc không ai ghi
 * nữa</b> — cả 19 điểm đo sẽ hiện "hoạt động" trong khi không có một số nào về suốt nhiều giờ. Đó
 * là hỏng câm ở dạng tệ nhất: màn hình đầy đủ, màu xanh, và sai.
 *
 * <p>Hàm {@link #suyRa} dưới đây không phụ thuộc vào việc poller còn sống: nó chỉ so mốc đọc gần
 * nhất với hiện tại. Poller chết bao lâu thì {@code readingAt} lùi xa bấy nhiêu, và tất cả điểm đo
 * chuyển sang {@link #MAT_TIN_HIEU} <i>tự nó</i>.
 */
public enum StationDisplayStatus {

    /** Có bản ghi trong khoảng thời gian còn coi là tươi. */
    HOAT_DONG,

    /**
     * Đang dùng nhưng đã quá {@code soKhungMatTinHieu} khung mà không có bản ghi mới.
     *
     * <p>Điểm đo ở trạng thái này bị <b>loại khỏi đánh giá ngưỡng</b> (HYD-2004) và hiện marker xám
     * trên bản đồ: giá trị cũ của một trạm đã chết không được dùng để kết luận mực nước hiện tại.
     */
    MAT_TIN_HIEU,

    /**
     * Chưa từng có bản ghi nào.
     *
     * <p>Khác hẳn {@link #MAT_TIN_HIEU} và phải khác: một điểm đo vừa seed mà chưa tới lượt polling
     * đầu tiên <i>không</i> phải một trạm hỏng. Gộp hai trạng thái này là biến ngày triển khai đầu
     * tiên thành 19 cảnh báo giả.
     */
    CHUA_CO_DU_LIEU,

    /** Người vận hành đã ngừng dùng điểm đo. Quyết định của con người, luôn thắng. */
    NGUNG;

    /**
     * Suy trạng thái hiển thị từ những thứ CSDL thật sự lưu.
     *
     * <p>Hàm thuần, không đọc đồng hồ hệ thống — {@code now} là tham số để bài kiểm dựng được cả bốn
     * nhánh mà không phải chờ thật.
     *
     * @param active điểm đo còn dùng không ({@code stations.active}) — quyết định của con người
     * @param readingAt mốc bản ghi gần nhất ({@code hydro_latest.reading_at}), {@code null} nếu chưa
     *     có bản ghi nào
     * @param now thời điểm đánh giá
     * @param khungNguon độ dài một khung cập nhật của nguồn ({@code settings})
     * @param soKhungMatTinHieu số khung liên tiếp không có bản ghi thì coi là mất tín hiệu
     */
    public static StationDisplayStatus suyRa(
            boolean active, Instant readingAt, Instant now, Duration khungNguon, int soKhungMatTinHieu) {
        if (!active) {
            // ⚠ Kiểm TRƯỚC mọi thứ khác. Một điểm đo đã ngừng thì đương nhiên không có số về; báo nó
            // "mất tín hiệu" là sinh một cảnh báo cho việc chính mình vừa quyết định.
            return NGUNG;
        }
        if (readingAt == null) {
            return CHUA_CO_DU_LIEU;
        }
        Duration hanTuoi = khungNguon.multipliedBy(Math.max(1, soKhungMatTinHieu));
        return readingAt.isBefore(now.minus(hanTuoi)) ? MAT_TIN_HIEU : HOAT_DONG;
    }
}
