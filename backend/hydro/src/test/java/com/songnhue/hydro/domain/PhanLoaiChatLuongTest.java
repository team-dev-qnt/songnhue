package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Bộ phân loại {@code HOP_LE} / {@code NGHI_NGO} — T32.1 · T32.2.
 *
 * <p>Bài này chạy <b>không Spring, không CSDL</b>: đó là toàn bộ lý do
 * {@link PhanLoaiChatLuong} là hàm thuần. Quy tắc nghiệp vụ nào phải dựng cả ứng dụng mới kiểm được
 * là quy tắc sẽ thôi được kiểm.
 */
class PhanLoaiChatLuongTest {

    private static final Instant MOC = Instant.parse("2026-09-02T03:20:00Z");

    /** Vỏ bọc đang seed ở {@code V202609021054} — ⛔ dùng đúng số thật, không bịa số cho dễ tính. */
    private static final QuyTacNghiNgo VO_BOC = new QuyTacNghiNgo(new BigDecimal("-10"), new BigDecimal("30"), null);

    private static ChanDoanChatLuong danhGia(String giaTri, QuyTacNghiNgo quyTac, SoDoTruoc truoc) {
        return PhanLoaiChatLuong.danhGia(new BigDecimal(giaTri), MOC, quyTac, truoc);
    }

    // =========================================================================
    // T32.2 — cấm lệnh nghiệp vụ
    // =========================================================================

    @Nested
    @DisplayName("⛔⛔ T32.2 — CẤM validate liên điểm đo kiểu 'TL phải cao hơn HL'")
    class CamSoCheoHaiDiemDo {

        /**
         * ⭐⭐ Số liệu <b>thật</b> của Công ty, ⛔ không phải số bịa cho dễ kể chuyện.
         *
         * <p>Hai cặp này bị đảo <i>một cách hợp lệ</i> — đó là trạng thái vận hành đúng của một cống
         * tiêu tự chảy khi sông ngoài đang cao. Một luật "TL &gt; HL" sẽ đánh dấu nghi ngờ đúng
         * những bản ghi <b>quan trọng nhất</b>, rồi quy tắc 14 loại chúng khỏi mọi báo cáo — người
         * trực mất số liệu đúng lúc cần nó nhất.
         */
        @Test
        @DisplayName("⭐⭐ Hai cặp TL < HL CÓ THẬT trong seed — cả bốn giá trị đều phải ra HOP_LE")
        void capBiDaoVanHopLe() {
            record Cap(String congTrinh, String thuongLuu, String haLuu) {}
            List<Cap> capThat = List.of(
                    new Cap("Vân Đình", "1.820", "2.180"), new Cap("Cống tiêu tự chảy Yên Nghĩa", "2.030", "3.510"));

            for (Cap c : capThat) {
                assertThat(danhGia(c.thuongLuu(), VO_BOC, null).chatLuong())
                        .as("%s — thượng lưu %s m", c.congTrinh(), c.thuongLuu())
                        .isEqualTo(ReadingQuality.HOP_LE);
                assertThat(danhGia(c.haLuu(), VO_BOC, null).chatLuong())
                        .as("%s — hạ lưu %s m (CAO HƠN thượng lưu, và đó là hợp lệ)", c.congTrinh(), c.haLuu())
                        .isEqualTo(ReadingQuality.HOP_LE);
            }

            // ⛔ Vế chống xanh-trên-tập-rỗng: nếu danh sách trống thì vòng lặp trên chẳng khẳng định gì.
            assertThat(capThat).hasSize(2);
        }

        /**
         * ⭐⭐ Cấm lệnh ép bằng <b>hình dạng chữ ký hàm</b>, ⛔ không bằng lời dặn (luật 12).
         *
         * <p>Bài trên chứng minh <i>hôm nay</i> không có luật so chéo. Bài này chứng minh <i>ngày
         * mai cũng không viết được</i>: không tham số nào của {@code danhGia} mang id một điểm đo,
         * nên phép so hai trạm là thứ <b>không diễn đạt được</b> ở tầng này.
         *
         * <p>⚠ Khẳng định về <b>số lượng</b> tham số, không chỉ về tên kiểu — nó không chia sẻ giả
         * định nào với danh sách kiểu bên dưới (luật 29). Ai thêm {@code long stationId} "cho tiện"
         * thì bài này đỏ <b>trước khi</b> luật cấm bị vi phạm.
         */
        @Test
        @DisplayName("⭐⭐ Chữ ký `danhGia` có ĐÚNG 4 tham số và ⛔ không tham số nào mang id điểm đo")
        void chuKyHamKhongChoPhepSoCheo() {
            Method m = Arrays.stream(PhanLoaiChatLuong.class.getDeclaredMethods())
                    .filter(x -> x.getName().equals("danhGia"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("⛔ Không còn hàm `danhGia` — bài này canh cái gì?"));

            assertThat(m.getParameterCount())
                    .as("⛔ Thêm một tham số thứ 5 là mở cửa cho phép so chéo hai điểm đo (T32.2). "
                            + "Bối cảnh lịch sử đi qua ĐÚNG một cửa: SoDoTruoc, và kiểu ấy không có id trạm.")
                    .isEqualTo(4);

            assertThat(m.getParameterTypes())
                    .containsExactly(BigDecimal.class, Instant.class, QuyTacNghiNgo.class, SoDoTruoc.class);

            // Vế phân biệt: `SoDoTruoc` tự nó cũng không được mang id trạm.
            assertThat(SoDoTruoc.class.getRecordComponents())
                    .as("⛔ `SoDoTruoc` chỉ có mốc và giá trị — thêm stationId là mở đúng cửa vừa khoá")
                    .hasSize(2);
        }
    }

    // =========================================================================
    // Khoảng vật lý
    // =========================================================================

    @Nested
    @DisplayName("Khoảng vật lý — vỏ bọc phát hiện hỏng cảm biến")
    class KhoangVatLy {

        /**
         * ⭐⭐ Chế độ hỏng đắt nhất đã lường trước, và là <b>lý do vỏ bọc này tồn tại</b>.
         *
         * <p>Nguồn trả <b>cm</b>; adapter chia 100. Một bản adapter đánh mất phép chia ấy cho ra
         * 157…493 thay vì 1,57…4,93. ⛔ Không ràng buộc CSDL nào bắt được — {@code NUMERIC(12,3)}
         * nhận thoải mái — và biểu đồ vẫn vẽ đẹp, chỉ là mọi con số sai 100 lần.
         */
        @Test
        @DisplayName("⭐⭐ Quên chia 100: 4,93 m thành 493 → bị bắt NGAY, ⛔ không trôi vào báo cáo")
        void quenChia100BiBat() {
            ChanDoanChatLuong r = danhGia("493", VO_BOC, null);

            assertThat(r.chatLuong()).isEqualTo(ReadingQuality.NGHI_NGO);
            assertThat(r.lyDo()).isEqualTo(LyDoNghiNgo.NGOAI_KHOANG_VAT_LY);
            assertThat(r.moTa()).contains("493").contains("-10").contains("30");

            // Vế phân biệt: giá trị ĐÚNG của chính lượt đo ấy phải đi lọt.
            assertThat(danhGia("4.930", VO_BOC, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("Sentinel âm của thiết bị đo (-9999) rơi dưới cận dưới")
        void sentinelAmBiBat() {
            assertThat(danhGia("-9999", VO_BOC, null).lyDo()).isEqualTo(LyDoNghiNgo.NGOAI_KHOANG_VAT_LY);
        }

        @Test
        @DisplayName("⚠ Biên là ĐÓNG cả hai đầu — đúng biên vẫn hợp lệ")
        void bienDong() {
            assertThat(danhGia("-10", VO_BOC, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
            assertThat(danhGia("30", VO_BOC, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
            // Vế phân biệt: ⛔ nếu biên là mở thì hai bài trên xanh mà hai bài này cũng xanh.
            assertThat(danhGia("-10.001", VO_BOC, null).dangNgo()).isTrue();
            assertThat(danhGia("30.001", VO_BOC, null).dangNgo()).isTrue();
        }

        @Test
        @DisplayName("⚠ So sánh theo GIÁ TRỊ, ⛔ không theo thang số — `30.000` bằng `30`")
        void soSanhTheoGiaTriKhongTheoThangSo() {
            // BigDecimal.equals phân biệt scale; compareTo thì không. Dùng nhầm là mọi giá trị ghi
            // scale 3 (đúng thang của mực nước) rơi ra ngoài biên viết scale 0.
            assertThat(danhGia("30.000", VO_BOC, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("Khai một nửa khoảng — chỉ cận trên, hoặc chỉ cận dưới")
        void motNuaKhoang() {
            QuyTacNghiNgo chiTran = new QuyTacNghiNgo(null, new BigDecimal("30"), null);
            assertThat(danhGia("-9999", chiTran, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
            assertThat(danhGia("31", chiTran, null).moTa()).contains("≤ 30");

            QuyTacNghiNgo chiSan = new QuyTacNghiNgo(new BigDecimal("-10"), null, null);
            assertThat(danhGia("9999", chiSan, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
            assertThat(danhGia("-11", chiSan, null).moTa()).contains("≥ -10");
        }

        @Test
        @DisplayName("⛔ Chưa cấu hình quy tắc ⇒ HOP_LE — nhưng ĐÓ KHÔNG PHẢI 'đã kiểm và thấy ổn'")
        void chuaCauHinhThiKhongKiem() {
            assertThat(danhGia("999999", QuyTacNghiNgo.KHONG_KIEM, null).chatLuong())
                    .isEqualTo(ReadingQuality.HOP_LE);

            // ⭐ Hai trạng thái ấy PHẢI phân biệt được ở đâu đó, nếu không màn hình sẽ hiện một bảng
            //   rỗng như thể "không có gì đáng ngờ" trong khi bộ phân loại đang tắt (quy tắc 16).
            assertThat(QuyTacNghiNgo.KHONG_KIEM.coKiem()).isFalse();
            assertThat(VO_BOC.coKiem()).isTrue();
            assertThat(BoQuyTacNghiNgo.RONG.coKiemGiKhong()).isFalse();
            assertThat(new BoQuyTacNghiNgo(java.util.Map.of("MUC_NUOC", VO_BOC)).coKiemGiKhong())
                    .isTrue();
        }

        @Test
        @DisplayName("⛔ Khoảng đảo ngược bị chặn ở hàm dựng — nếu không thì MỌI bản ghi thành NGHI_NGO")
        void khoangDaoNguocBiChan() {
            assertThat(BoQuyTacNghiNgo.RONG.cho("KHONG_CO"))
                    .as("loại chỉ số chưa khai ⇒ KHONG_KIEM, ⛔ không phải null")
                    .isEqualTo(QuyTacNghiNgo.KHONG_KIEM);

            assertThatThrownBy(() -> new QuyTacNghiNgo(new BigDecimal("30"), new BigDecimal("-10"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đảo ngược");
            assertThatThrownBy(() -> new QuyTacNghiNgo(null, null, new BigDecimal("-1")))
                    .hasMessageContaining("âm");
        }
    }

    // =========================================================================
    // Tốc độ đổi
    // =========================================================================

    @Nested
    @DisplayName("Delta/giờ — so với bản ghi HỢP LỆ liền trước của CHÍNH điểm đo ấy")
    class TocDoDoi {

        /** 2 m/giờ; ⚠ giá trị này ⛔ KHÔNG được seed — xem javadoc BoQuyTacNghiNgo. */
        private final QuyTacNghiNgo coDelta =
                new QuyTacNghiNgo(new BigDecimal("-10"), new BigDecimal("30"), new BigDecimal("2"));

        private SoDoTruoc truoc(String giaTri, Duration cachDay) {
            return new SoDoTruoc(MOC.minus(cachDay), new BigDecimal(giaTri));
        }

        @Test
        @DisplayName("⭐ Nhảy 1 m trong 10 phút = 6 m/giờ ⇒ NGHI_NGO; nhảy 0,3 m trong 10 phút = 1,8 ⇒ hợp lệ")
        void batDuocCuNhayNhanh() {
            ChanDoanChatLuong nhanh = danhGia("5.000", coDelta, truoc("4.000", Duration.ofMinutes(10)));
            assertThat(nhanh.lyDo()).isEqualTo(LyDoNghiNgo.NHAY_QUA_NHANH);
            assertThat(nhanh.moTa()).contains("10 phút").contains("giới hạn 2/giờ");

            assertThat(danhGia("4.300", coDelta, truoc("4.000", Duration.ofMinutes(10)))
                            .chatLuong())
                    .isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("⭐ ĐÚNG giới hạn thì KHÔNG vượt — nhân chéo, ⛔ không chia rồi làm tròn")
        void dungGioiHanThiKhongVuot() {
            // 1/3 m trong 10 phút = đúng 2 m/giờ. Chia ở scale 3 rồi so sẽ cho 2.000 > 2 → sai.
            assertThat(danhGia("4.000", coDelta, new SoDoTruoc(MOC.minus(Duration.ofHours(1)), new BigDecimal("2.000")))
                            .chatLuong())
                    .as("chênh đúng 2,000 m trong đúng 1 giờ = đúng giới hạn ⇒ ⛔ chưa vượt")
                    .isEqualTo(ReadingQuality.HOP_LE);

            assertThat(danhGia("4.001", coDelta, new SoDoTruoc(MOC.minus(Duration.ofHours(1)), new BigDecimal("2.000")))
                            .lyDo())
                    .as("vượt 1 mm trong 1 giờ vẫn là vượt — vế phân biệt của bài trên")
                    .isEqualTo(LyDoNghiNgo.NHAY_QUA_NHANH);
        }

        @Test
        @DisplayName("Đổi theo CẢ HAI chiều đều bị bắt — chênh là trị tuyệt đối")
        void haiChieuDeuBiBat() {
            assertThat(danhGia("3.000", coDelta, truoc("4.000", Duration.ofMinutes(10)))
                            .lyDo())
                    .isEqualTo(LyDoNghiNgo.NHAY_QUA_NHANH);
        }

        @Test
        @DisplayName("⛔ Chưa có bản hợp lệ nào ⇒ ⛔ KHÔNG kết luận — 'chưa có mốc' khác 'đã so và thấy ổn'")
        void chuaCoMocThiKhongKetLuan() {
            assertThat(danhGia("999", coDelta, null).lyDo())
                    .as("999 vẫn bị bắt bởi khoảng vật lý, ⛔ không bởi delta")
                    .isEqualTo(LyDoNghiNgo.NGOAI_KHOANG_VAT_LY);
            assertThat(danhGia("29", coDelta, null).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("⚠ Bản ghi CŨ HƠN mốc so sánh (nhập tay bù quá khứ) ⇒ bỏ qua phép kiểm, ⛔ không nổ")
        void banGhiCuHonThiBoQua() {
            SoDoTruoc tuongLai = new SoDoTruoc(MOC.plus(Duration.ofHours(1)), new BigDecimal("1.000"));
            assertThat(danhGia("29.000", coDelta, tuongLai).chatLuong())
                    .as("Δgiây ≤ 0 ⇒ không có 'tốc độ đổi' nào để tính; chia cho 0 thì nổ, "
                            + "còn lấy trị tuyệt đối của khoảng thời gian là bịa ra một chiều thời gian ngược")
                    .isEqualTo(ReadingQuality.HOP_LE);

            SoDoTruoc cungMoc = new SoDoTruoc(MOC, new BigDecimal("1.000"));
            assertThat(danhGia("29.000", coDelta, cungMoc).chatLuong()).isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("Khoảng cách LỚN thì tốc độ nhỏ — trạm im ba ngày rồi trả về ⛔ không bị bắt oan")
        void tramImLauKhongBiBatOan() {
            assertThat(danhGia("20.000", coDelta, truoc("1.000", Duration.ofDays(3)))
                            .chatLuong())
                    .as("chênh 19 m trong 72 giờ ≈ 0,26 m/giờ — dưới giới hạn")
                    .isEqualTo(ReadingQuality.HOP_LE);
        }

        @Test
        @DisplayName("⭐ Vượt CẢ HAI thì lý do là NGOÀI KHOẢNG — lý do nặng hơn thắng")
        void lyDoNangHonThang() {
            assertThat(danhGia("500", coDelta, truoc("4.000", Duration.ofMinutes(10)))
                            .lyDo())
                    .as("500 vừa ngoài khoảng vừa nhảy nhanh; hai lý do đòi hai cách xử lý NGƯỢC NHAU "
                            + "nên bản ghi chỉ mang một, và phải là cái không thể đúng ở bất kỳ tình huống nào")
                    .isEqualTo(LyDoNghiNgo.NGOAI_KHOANG_VAT_LY);
        }
    }

    // =========================================================================
    // Bất biến của kết luận
    // =========================================================================

    @Nested
    @DisplayName("ChanDoanChatLuong — ba trường đi liền nhau hoặc không có trường nào")
    class BatBienKetLuan {

        @Test
        @DisplayName("⛔ NGHI_NGO phải có lý do VÀ mô tả; HOP_LE thì ⛔ không được có")
        void capDiLienNhau() {
            assertThatThrownBy(() -> new ChanDoanChatLuong(ReadingQuality.NGHI_NGO, null, "vì sao đó"))
                    .hasMessageContaining("phải có lý do");
            assertThatThrownBy(() -> new ChanDoanChatLuong(ReadingQuality.NGHI_NGO, LyDoNghiNgo.NHAY_QUA_NHANH, null))
                    .hasMessageContaining("phải có mô tả");
            assertThatThrownBy(() -> new ChanDoanChatLuong(ReadingQuality.HOP_LE, LyDoNghiNgo.NHAY_QUA_NHANH, "x"))
                    .hasMessageContaining("HOP_LE thì không được có");
        }

        @Test
        @DisplayName("⛔ Mô tả dài quá 200 ký tự bị TỪ CHỐI, ⛔ không bị cắt trong im lặng")
        void moTaKhongBiCatNgam() {
            String dai = "x".repeat(ChanDoanChatLuong.DAI_TOI_DA_MO_TA + 1);
            assertThatThrownBy(() -> ChanDoanChatLuong.nghiNgo(LyDoNghiNgo.NHAY_QUA_NHANH, dai))
                    .hasMessageContaining("vượt 200");
        }

        @Test
        @DisplayName("⭐ Mọi mô tả bộ phân loại tự sinh đều VỪA cột 200 ký tự")
        void moTaTuSinhLuonVuaCot() {
            // ⚠ Bài trên chứng minh hàm dựng CHẶN được câu quá dài. Bài này chứng minh bộ phân loại
            //   ⛔ không bao giờ sinh ra một câu như thế — nếu không thì lượt ingest sẽ nổ ở giữa
            //   chừng, và khung dữ liệu ấy mất vĩnh viễn.
            QuyTacNghiNgo quyTacDaiNhat = new QuyTacNghiNgo(
                    new BigDecimal("-123456.789"), new BigDecimal("987654.321"), new BigDecimal("0.001"));
            List<ChanDoanChatLuong> moiCa = List.of(
                    danhGia("-999999.999", quyTacDaiNhat, null),
                    danhGia("999999.999", quyTacDaiNhat, null),
                    danhGia(
                            "500000.000",
                            new QuyTacNghiNgo(null, null, new BigDecimal("0.001")),
                            new SoDoTruoc(MOC.minus(Duration.ofMinutes(1)), new BigDecimal("-500000.000"))));

            assertThat(moiCa).hasSize(3);
            for (ChanDoanChatLuong c : moiCa) {
                assertThat(c.dangNgo()).isTrue();
                assertThat(c.moTa().length()).isLessThanOrEqualTo(ChanDoanChatLuong.DAI_TOI_DA_MO_TA);
            }
        }

        @Test
        @DisplayName("⛔ Đường ingest ⛔ không sinh ra được bản ghi XOA")
        void ingestKhongSinhRaXoa() {
            assertThatThrownBy(() -> new ReadingRow(
                            1L,
                            2L,
                            MOC,
                            new BigDecimal("4.930"),
                            new ChanDoanChatLuong(ReadingQuality.XOA, null, null),
                            ReadingSource.API,
                            null))
                    .hasMessageContaining("không sinh ra bản ghi XOA");
        }
    }
}
