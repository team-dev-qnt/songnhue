package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Trạng thái hiển thị của điểm đo — cả BỐN nhánh.
 *
 * <p>Bài kiểm này tồn tại vì trạng thái ấy <b>không có cột trong CSDL</b>: nó được suy ra mỗi lần
 * đọc. Một hàm thuần thì rẻ để kiểm, nhưng cũng dễ bị sửa nhầm mà không ai thấy, vì sai ở đây không
 * làm hỏng gì cả — chỉ làm marker trên bản đồ đổi màu.
 */
class StationDisplayStatusTest {

    private static final Instant BAY_GIO = Instant.parse("2026-08-31T10:00:00Z");
    private static final Duration KHUNG = Duration.ofMinutes(10);
    private static final int SO_KHUNG = 3;

    @Test
    @DisplayName("Có bản ghi trong hạn tươi → HOẠT ĐỘNG")
    void trongHanThiHoatDong() {
        Instant vuaCo = BAY_GIO.minus(Duration.ofMinutes(12));

        assertThat(StationDisplayStatus.suyRa(true, vuaCo, BAY_GIO, KHUNG, SO_KHUNG))
                .isEqualTo(StationDisplayStatus.HOAT_DONG);
    }

    @Test
    @DisplayName("Quá 3 khung không có bản ghi → MẤT TÍN HIỆU")
    void quaHanThiMatTinHieu() {
        Instant cu = BAY_GIO.minus(Duration.ofMinutes(31));

        assertThat(StationDisplayStatus.suyRa(true, cu, BAY_GIO, KHUNG, SO_KHUNG))
                .isEqualTo(StationDisplayStatus.MAT_TIN_HIEU);
    }

    /**
     * ⚠ Bài kiểm phân biệt HAI TRẠNG THÁI KỀ NHAU, không chỉ khẳng định một giá trị đúng.
     *
     * <p>Hạn tươi là {@code 10 phút × 3 = 30 phút}. Mốc 30 phút chẵn vẫn phải là HOẠT ĐỘNG (chưa
     * <i>quá</i> hạn), mốc 30 phút 1 giây mới là MẤT TÍN HIỆU. Một bài kiểm chỉ thử 12 phút và 31
     * phút sẽ xanh với cả {@code isBefore} lẫn {@code isAfter} lệch một dấu bằng.
     */
    @Test
    @DisplayName("Ranh giới hạn tươi: đúng 30 phút còn hoạt động, 30 phút 1 giây thì mất tín hiệu")
    void ranhGioiHanTuoi() {
        Instant dungHan = BAY_GIO.minus(Duration.ofMinutes(30));
        Instant quaHanMotGiay = BAY_GIO.minus(Duration.ofMinutes(30)).minusSeconds(1);

        assertThat(StationDisplayStatus.suyRa(true, dungHan, BAY_GIO, KHUNG, SO_KHUNG))
                .as("đúng 30 phút — chưa quá hạn")
                .isEqualTo(StationDisplayStatus.HOAT_DONG);
        assertThat(StationDisplayStatus.suyRa(true, quaHanMotGiay, BAY_GIO, KHUNG, SO_KHUNG))
                .as("30 phút 1 giây — đã quá hạn")
                .isEqualTo(StationDisplayStatus.MAT_TIN_HIEU);
    }

    @Test
    @DisplayName("Chưa từng có bản ghi → CHƯA CÓ DỮ LIỆU, KHÔNG phải mất tín hiệu")
    void chuaCoBanGhiThiKhacMatTinHieu() {
        StationDisplayStatus ket = StationDisplayStatus.suyRa(true, null, BAY_GIO, KHUNG, SO_KHUNG);

        assertThat(ket).isEqualTo(StationDisplayStatus.CHUA_CO_DU_LIEU);
        assertThat(ket)
                .as("gộp hai trạng thái này là biến ngày triển khai đầu tiên thành 19 cảnh báo giả")
                .isNotEqualTo(StationDisplayStatus.MAT_TIN_HIEU);
    }

    /**
     * ⛔ Nhánh quan trọng nhất: quyết định của con người thắng kết luận của máy.
     *
     * <p>Một điểm đo đã ngừng thì đương nhiên không có số về. Nếu {@code active} không được kiểm
     * trước, nó sẽ hiện MẤT TÍN HIỆU và sinh một cảnh báo cho đúng việc người vận hành vừa chủ động
     * làm.
     */
    @Test
    @DisplayName("Đã ngừng → NGƯNG, kể cả khi số liệu vừa mới về")
    void nguoiVanHanhNgungThiThangTatCa() {
        Instant vuaCo = BAY_GIO.minusSeconds(30);

        assertThat(StationDisplayStatus.suyRa(false, vuaCo, BAY_GIO, KHUNG, SO_KHUNG))
                .isEqualTo(StationDisplayStatus.NGUNG);
        assertThat(StationDisplayStatus.suyRa(false, null, BAY_GIO, KHUNG, SO_KHUNG))
                .as("ngừng mà chưa có dữ liệu vẫn là NGƯNG")
                .isEqualTo(StationDisplayStatus.NGUNG);
    }

    /**
     * Cấu hình {@code signal-loss-frames = 0} không được biến mọi điểm đo thành mất tín hiệu.
     *
     * <p>Ô nhập trên màn hình Cấu hình hệ thống nhận số nguyên; ràng buộc seed là {@code min=0}. Số 0
     * mà hiểu theo nghĩa đen thì hạn tươi bằng 0 và <b>toàn bộ 19 điểm đo</b> chuyển sang mất tín
     * hiệu ngay lập tức — một ô nhập làm sập cả màn hình giám sát.
     */
    @Test
    @DisplayName("signal-loss-frames = 0 vẫn giữ hạn tươi ít nhất một khung")
    void khongKhungNaoThiVanConMotKhung() {
        Instant trongMotKhung = BAY_GIO.minus(Duration.ofMinutes(5));

        assertThat(StationDisplayStatus.suyRa(true, trongMotKhung, BAY_GIO, KHUNG, 0))
                .isEqualTo(StationDisplayStatus.HOAT_DONG);
    }
}
