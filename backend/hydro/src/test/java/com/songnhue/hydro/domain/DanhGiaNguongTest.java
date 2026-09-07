package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Bộ đánh giá ngưỡng cảnh báo — WS-33 / T33.5. ⛔ Không Spring, ⛔ không CSDL.
 *
 * <p>Bốn nhóm, và nhóm đầu là nhóm chịu lực: nó ⛔ không kiểm một hành vi mà kiểm <b>hình dạng chữ
 * ký hàm</b> — thứ duy nhất làm cho cấm lệnh "⛔ không so chéo hai điểm đo" trở thành điều không
 * viết ra được, thay vì một lời dặn trong javadoc mà người sau đọc lướt qua (T32.2, luật 12).
 */
class DanhGiaNguongTest {

    private static final Instant MOC = Instant.parse("2026-09-02T10:20:00Z");

    // =========================================================================

    @Nested
    @DisplayName("⛔⛔ Cấm so chéo hai điểm đo — ép bằng hình dạng, không bằng lời dặn")
    class CamSoCheoHaiDiemDo {

        @Test
        @DisplayName("⭐⭐ danhGia nhận ĐÚNG 4 tham số, và không tham số nào mang id điểm đo")
        void theSignatureCannotCarryAStationId() throws Exception {
            Method danhGia = DanhGiaNguong.class.getDeclaredMethod(
                    "danhGia", BigDecimal.class, Instant.class, DieuKienNguong.class, SoDoTruoc.class);

            assertThat(danhGia.getParameterCount())
                    .as(
                            """
                            ⛔ Thêm một tham số thứ năm — `stationId` "cho tiện" — là mở đúng cánh cửa mà \
                            T32.2 đã đóng: ngưỡng của Cống Liên Mạc đọc số của Vân Đình. Khẳng định về SỐ \
                            LƯỢNG không chia sẻ giả định nào với danh sách tên kiểu, nên nó bắt được cả \
                            trường hợp ai đó đổi tên tham số cho khớp (luật 29).""")
                    .isEqualTo(4);

            assertThat(SoDoTruoc.class.getRecordComponents())
                    .as("SoDoTruoc là bối cảnh lịch sử DUY NHẤT của hàm — hai thành phần, ⛔ không có id trạm")
                    .hasSize(2);
        }

        @Test
        @DisplayName("⛔ danhGia KHÔNG nhận ReadingQuality — nơi gọi phải lọc HOP_LE trước (quy tắc 14)")
        void theSignatureCannotCarryAQuality() {
            assertThat(DanhGiaNguong.class.getDeclaredMethods())
                    .filteredOn(m -> "danhGia".equals(m.getName()))
                    .allSatisfy(m -> assertThat(m.getParameterTypes())
                            .as("một tham số chất lượng ở đây là lời mời truyền NGHI_NGO vào rồi trông chờ "
                                    + "hàm tự lo — hỏi 'có đáng lo không' về một con số chưa tin là thật thì "
                                    + "câu trả lời nào cũng vô nghĩa")
                            .doesNotContain(ReadingQuality.class));
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("GT / LT — và BIÊN là không vi phạm")
    class NguongMotChieu {

        @Test
        @DisplayName("GT: cao hơn ngưỡng → vi phạm")
        void aboveTheThresholdBreaches() {
            assertThat(danhGia("2.51", gt("2.50")).dangViPham()).isTrue();
        }

        @Test
        @DisplayName("⚠⚠ GT: ĐÚNG BẰNG ngưỡng → KHÔNG vi phạm — quyết định nghiệp vụ, ghim tại đây")
        void exactlyAtTheThresholdDoesNotBreach() {
            assertThat(danhGia("2.50", gt("2.50")).trangThai())
                    .as(
                            """
                            ⬜ Đây là điểm CHƯA CHỐT với Công ty (gắn G9-a): thực hành phòng chống lụt bão \
                            nói "mực nước ĐẠT báo động I", tức là >=. Mã hiện làm > vì tên hằng `GT` phải \
                            nói đúng thứ nó làm (luật 14). Chênh lệch đúng bằng MỘT bước đo (1 cm) nên nó \
                            ⛔ không bao giờ lộ ra khi thử tay — bài kiểm này là thứ duy nhất bắt được nếu \
                            ai đó đổi ý và sửa lặng lẽ.""")
                    .isEqualTo(KetLuanNguong.TrangThai.KHONG_VI_PHAM);
        }

        @Test
        @DisplayName("⭐ 2.50 và 2.5 là CÙNG một giá trị — so bằng compareTo, không bằng equals")
        void scaleDoesNotChangeTheVerdict() {
            assertThat(danhGia("2.5", gt("2.50")).dangViPham())
                    .as("BigDecimal.equals phân biệt scale: 2.50 ≠ 2.5. Nguồn trả cm rồi chia 100 ra "
                            + "scale 3, còn người gõ tay ra scale 1 — hai đường ghi cùng một mực nước")
                    .isFalse();
        }

        @Test
        @DisplayName("LT: thấp hơn ngưỡng → vi phạm; đúng bằng → không")
        void belowTheThresholdBreaches() {
            assertThat(danhGia("0.19", lt("0.20")).dangViPham()).isTrue();
            assertThat(danhGia("0.20", lt("0.20")).dangViPham()).isFalse();
            assertThat(danhGia("0.21", lt("0.20")).dangViPham()).isFalse();
        }

        @Test
        @DisplayName("Câu giải thích mang CẢ giá trị lẫn ngưỡng — người trực đọc một dòng là đủ")
        void theMessageCarriesBothNumbers() {
            assertThat(danhGia("2.51", gt("2.50")).moTa()).contains("2.51").contains("2.50");
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("OUT_OF_RANGE — khoảng ĐÓNG, hai biên đều không vi phạm")
    class NgoaiKhoang {

        @Test
        @DisplayName("Trong khoảng và ở đúng hai biên → không vi phạm")
        void insideTheClosedIntervalIsFine() {
            assertThat(danhGia("1.50", khoang("1.00", "2.00")).dangViPham()).isFalse();
            assertThat(danhGia("1.00", khoang("1.00", "2.00")).dangViPham()).isFalse();
            assertThat(danhGia("2.00", khoang("1.00", "2.00")).dangViPham()).isFalse();
        }

        @Test
        @DisplayName("⭐ Vi phạm ở CẢ HAI chiều — nửa luật là cảnh báo lũ không bao giờ bắn")
        void bothSidesBreach() {
            assertThat(danhGia("0.99", khoang("1.00", "2.00")).dangViPham()).isTrue();
            assertThat(danhGia("2.01", khoang("1.00", "2.00")).dangViPham()).isTrue();
        }

        @Test
        @DisplayName("⛔ Hàm dựng CHẶN khoảng thiếu cận trên và khoảng đảo ngược")
        void theConstructorRefusesBrokenRanges() {
            assertThatThrownBy(() -> new DieuKienNguong(AlertConditionType.OUT_OF_RANGE, so("1.00"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cận trên");

            assertThatThrownBy(() -> new DieuKienNguong(AlertConditionType.OUT_OF_RANGE, so("2.00"), so("1.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đảo ngược");
        }

        @Test
        @DisplayName("⛔ Tốc độ đổi âm bị chặn ở hàm dựng — quy tắc 16, ép ở nơi dựng")
        void aNegativeRateIsRefused() {
            assertThatThrownBy(() -> new DieuKienNguong(AlertConditionType.RATE_OF_CHANGE, so("-1"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("⭐⭐ RATE_OF_CHANGE — và ba kết cục, không phải hai")
    class TocDoDoi {

        private static final DieuKienNguong NUA_MET_MOI_GIO =
                new DieuKienNguong(AlertConditionType.RATE_OF_CHANGE, so("0.50"), null);

        @Test
        @DisplayName("⭐⭐ Chưa có mốc so sánh → KHÔNG KẾT LUẬN ĐƯỢC, ⛔ không phải 'không vi phạm'")
        void withoutAPreviousReadingItCannotConclude() {
            KetLuanNguong ket = DanhGiaNguong.danhGia(so("2.00"), MOC, NUA_MET_MOI_GIO, null);

            assertThat(ket.trangThai())
                    .as(
                            """
                            ⛔ Đây là quy tắc 16 ở dạng nguy hiểm nhất: "chưa canh được gì" và "đã canh, \
                            trạm ổn" cùng cho ra MỘT ô xanh trên màn hình. Luật tốc độ đổi rơi vào nhánh \
                            này ở MỌI lượt đầu sau khi khai điểm đo, sau mỗi quãng API chết, và sau mỗi \
                            lượt xoá bản ghi nghi ngờ — tức là thường xuyên, không phải hiếm.""")
                    .isEqualTo(KetLuanNguong.TrangThai.KHONG_KET_LUAN_DUOC);
            assertThat(ket.dangViPham()).isFalse();
            assertThat(ket.lyDoKhongKetLuan()).isNotBlank();
        }

        @Test
        @DisplayName("⛔ Mốc đo KHÔNG muộn hơn bản trước (nhập bù quá khứ) → không kết luận, ⛔ không bịa")
        void aBackdatedReadingCannotConclude() {
            SoDoTruoc sau = new SoDoTruoc(MOC.plusSeconds(600), so("1.00"));

            assertThat(DanhGiaNguong.danhGia(so("9.00"), MOC, NUA_MET_MOI_GIO, sau)
                            .trangThai())
                    .as("lấy trị tuyệt đối của khoảng thời gian là bịa ra một chiều thời gian ngược")
                    .isEqualTo(KetLuanNguong.TrangThai.KHONG_KET_LUAN_DUOC);
        }

        @Test
        @DisplayName("Đổi 0,20 m trong 10 phút = 1,2 m/giờ → vượt ngưỡng 0,5 m/giờ")
        void aFastRiseBreaches() {
            KetLuanNguong ket = DanhGiaNguong.danhGia(so("2.20"), MOC, NUA_MET_MOI_GIO, truoc10Phut("2.00"));

            assertThat(ket.dangViPham()).isTrue();
            assertThat(ket.moTa()).contains("1.200").contains("0.50");
        }

        @Test
        @DisplayName("⭐ Đổi theo chiều GIẢM cũng vượt — |Δ|, không phải Δ")
        void aFastFallAlsoBreaches() {
            assertThat(DanhGiaNguong.danhGia(so("1.80"), MOC, NUA_MET_MOI_GIO, truoc10Phut("2.00"))
                            .dangViPham())
                    .as("nước RÚT nhanh cũng là một sự kiện vận hành — vỡ bờ, hoặc một cống mở sai chiều")
                    .isTrue();
        }

        @Test
        @DisplayName("⭐ Đúng BẰNG tốc độ giới hạn → không vi phạm, và không phụ thuộc làm tròn")
        void exactlyAtTheRateLimitDoesNotBreach() {
            // 0,0833… m mỗi 10 phút = đúng 0,50 m/giờ. Phép chia ở scale 3 cho 0.500 rồi so bằng —
            // nhân chéo cho ra cùng kết luận mà ⛔ không phải chọn một RoundingMode nào.
            assertThat(DanhGiaNguong.danhGia(
                                    so("2.5"), MOC, NUA_MET_MOI_GIO, new SoDoTruoc(MOC.minusSeconds(3600), so("2.0")))
                            .dangViPham())
                    .isFalse();
        }

        private static SoDoTruoc truoc10Phut(String giaTri) {
            return new SoDoTruoc(MOC.minusSeconds(600), so(giaTri));
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("Bất biến của kết luận")
    class BatBienKetLuan {

        @Test
        @DisplayName("⛔ VI_PHAM phải có mô tả, KHONG_VI_PHAM phải KHÔNG có — ép ở hàm dựng")
        void theDescriptionTravelsWithTheVerdict() {
            assertThatThrownBy(() -> new KetLuanNguong(KetLuanNguong.TrangThai.VI_PHAM, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new KetLuanNguong(KetLuanNguong.TrangThai.KHONG_VI_PHAM, "có gì đó"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new KetLuanNguong(KetLuanNguong.TrangThai.KHONG_KET_LUAN_DUOC, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("⭐ MỌI dạng điều kiện đều được xử lý — không dạng nào rơi ra ngoài")
        void everyConditionTypeIsHandled() {
            assertThat(AlertConditionType.values())
                    .as("khẳng định về SỐ LƯỢNG: thêm một dạng thứ năm mà quên nhánh xử lý thì bài này "
                            + "đỏ ngay, chứ không đợi tới lượt chạy thật (luật 29)")
                    .hasSize(4);

            for (AlertConditionType loai : AlertConditionType.values()) {
                DieuKienNguong dieuKien = loai == AlertConditionType.OUT_OF_RANGE
                        ? new DieuKienNguong(loai, so("1.00"), so("2.00"))
                        : new DieuKienNguong(loai, so("1.00"), null);

                assertThat(DanhGiaNguong.danhGia(
                                so("1.50"), MOC, dieuKien, new SoDoTruoc(MOC.minusSeconds(600), so("1.50"))))
                        .as("dạng %s không trả về kết luận nào", loai)
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("canMocSoSanh chỉ đúng với RATE_OF_CHANGE — ba dạng kia kết luận được ngay")
        void onlyRateOfChangeNeedsHistory() {
            assertThat(new DieuKienNguong(AlertConditionType.RATE_OF_CHANGE, so("1"), null).canMocSoSanh())
                    .isTrue();
            assertThat(gt("1").canMocSoSanh()).isFalse();
            assertThat(lt("1").canMocSoSanh()).isFalse();
            assertThat(khoang("1", "2").canMocSoSanh()).isFalse();
        }
    }

    // -------------------------------------------------------------------------

    private static KetLuanNguong danhGia(String giaTri, DieuKienNguong dieuKien) {
        return DanhGiaNguong.danhGia(so(giaTri), MOC, dieuKien, null);
    }

    private static DieuKienNguong gt(String nguong) {
        return new DieuKienNguong(AlertConditionType.GT, so(nguong), null);
    }

    private static DieuKienNguong lt(String nguong) {
        return new DieuKienNguong(AlertConditionType.LT, so(nguong), null);
    }

    private static DieuKienNguong khoang(String thap, String cao) {
        return new DieuKienNguong(AlertConditionType.OUT_OF_RANGE, so(thap), so(cao));
    }

    private static BigDecimal so(String v) {
        return new BigDecimal(v);
    }
}
