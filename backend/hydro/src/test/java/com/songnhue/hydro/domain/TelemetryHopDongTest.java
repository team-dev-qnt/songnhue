package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Bất biến của bốn kiểu giá trị mà adapter và poller trao đổi — ép ở <b>hàm dựng</b>, ⛔ không ở lời
 * dặn (quy tắc 16).
 *
 * <p>Mỗi khẳng định ở đây tương ứng một lượt vỡ ở <i>giữa một lượt ingest</i> nếu không có nó — chỗ
 * xa nhất so với dòng mã viết sai, và là chỗ đắt nhất vì nguồn không có API lịch sử.
 */
class TelemetryHopDongTest {

    @Nested
    @DisplayName("TelemetryCall — bản ghi mang credential")
    class Call {

        @Test
        @DisplayName("⭐⭐ toString() KHÔNG in mã số — bản mặc định của record in mọi thành phần")
        void toStringKhongInMaSo() {
            TelemetryCall call = new TelemetryCall("http://x/", "maso-bi-mat-123;", Duration.ofSeconds(30));

            assertThat(call.toString())
                    .as("⛔ Một dòng log.debug(\"gọi {}\", call) là đủ để mã số nằm VĨNH VIỄN trong tệp "
                            + "log — nơi nhiều người xem hơn bảng api_sources và không mã hoá (§4.7)")
                    .doesNotContain("maso-bi-mat-123")
                    .contains("***")
                    .contains("http://x/");
        }

        @Test
        @DisplayName("⚠ Dấu ';' cuối được giữ NGUYÊN VĂN — đây là tầng thứ năm và là tầng cuối trước dây")
        void giuNguyenDauChamPhayCuoi() {
            assertThat(new TelemetryCall("http://x/", "abc;", Duration.ofSeconds(1)).maSo())
                    .as("thiếu ';' thì nguồn trả not.working — TRÔNG Y HỆT lỗi sai mã số")
                    .isEqualTo("abc;");
        }

        @Test
        @DisplayName("⭐ Mã số rỗng bị TỪ CHỐI — 'chưa cấu hình' phải dừng trước HTTP, không thành 'mã số sai'")
        void maSoRongBiTuChoi() {
            for (String maSo : new String[] {null, "", "   "}) {
                assertThatThrownBy(() -> new TelemetryCall("http://x/", maSo, Duration.ofSeconds(1)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("THIEU_MA_SO");
            }
        }

        @Test
        @DisplayName("Timeout không dương bị từ chối — 0 nghĩa là 'chờ mãi mãi' ở một số tầng")
        void timeoutPhaiDuong() {
            assertThatThrownBy(() -> new TelemetryCall("http://x/", "a;", Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TelemetryCall("http://x/", "a;", Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("TelemetryReading — quy đổi đơn vị (quy tắc parse 7)")
    class Reading {

        private TelemetryReading doDuoc(String cm) {
            return new TelemetryReading("F01652", Instant.EPOCH, new BigDecimal(cm), TelemetryReading.DON_VI_CM);
        }

        @Test
        @DisplayName("⭐⭐ cm → m, scale 3, BigDecimal — giá trị THẬT của bản mẫu 01/09/2026")
        void quyDoiDungTheoGiaTriThat() {
            assertThat(doDuoc("493").giaTri()).isEqualTo(new BigDecimal("4.930"));
            assertThat(doDuoc("79").giaTri()).isEqualTo(new BigDecimal("0.790"));
            assertThat(doDuoc("-15").giaTri()).isEqualTo(new BigDecimal("-0.150"));
        }

        @Test
        @DisplayName("⛔ Cấm double: 493/100.0 KHÔNG bằng 4.930 — mọi ngưỡng cảnh báo sau đó so sai")
        void doubleChoRaMotConSoKhac() {
            BigDecimal dung = doDuoc("493").giaTri();
            BigDecimal saiViDouble = BigDecimal.valueOf(493 / 100.0);

            assertThat(dung.toPlainString()).isEqualTo("4.930");
            assertThat(saiViDouble.toPlainString())
                    .as("⚠ Vế PHÂN BIỆT: nếu hai cách cho cùng một chuỗi thì lời cấm double ở đây "
                            + "không nói lên điều gì")
                    .isNotEqualTo("4.930");
        }

        @Test
        @DisplayName("⭐ Số thập phân từ nguồn KHÔNG làm ném ArithmeticException — RoundingMode khai tường minh")
        void soThapPhanKhongLamNem() {
            assertThat(doDuoc("4.93").giaTri())
                    .as("regex của quy tắc 4 CHO PHÉP -?\\d+([.,]\\d+)? — divide() không có "
                            + "RoundingMode sẽ ném ngay giữa một lượt ingest")
                    .isEqualTo(new BigDecimal("0.049"));
        }

        @Test
        @DisplayName("⛔ Đơn vị lạ bị TỪ CHỐI ở hàm dựng — điểm cắm nguồn thứ hai là đây, ⛔ không phải chỗ nới")
        void donViLaBiTuChoi() {
            assertThatThrownBy(() -> new TelemetryReading("F1", Instant.EPOCH, BigDecimal.ONE, "mm"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chưa biết quy đổi đơn vị");
        }

        @Test
        @DisplayName("Khoá chống trùng gồm CẢ mã lẫn mốc — cùng mã khác mốc là hai bản ghi hợp lệ")
        void khoaTrungGomCaMaVaMoc() {
            TelemetryReading a = doDuoc("100");
            TelemetryReading b = new TelemetryReading(
                    "F01652", Instant.EPOCH.plusSeconds(600), new BigDecimal("101"), TelemetryReading.DON_VI_CM);

            assertThat(a.khoaTrung()).isNotEqualTo(b.khoaTrung());
        }
    }

    @Nested
    @DisplayName("TelemetryFetch — kết quả thô của một lượt gọi")
    class Fetch {

        @Test
        @DisplayName("⭐⭐ THIEU_MA_SO bị chặn: hydro_raw_logs có CHECK BỐN giá trị, cố ý thiếu nó")
        void thieuMaSoKhongThuocMotFetch() {
            assertThatThrownBy(() -> new TelemetryFetch(null, 0, null, SyncFailureKind.THIEU_MA_SO, "x"))
                    .as("thiếu mã số nghĩa là KHÔNG có lượt gọi nào ⇒ không có response để ghi. Để nó "
                            + "lọt tới đây là dựng sẵn một INSERT chắc chắn vỡ vì ràng buộc, ở giữa "
                            + "một lượt ingest")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName(
                "⭐ Hỏng thì BẮT BUỘC có lý do — §10.68-B: một vân tay cho ba nguyên nhân là không có nguyên nhân nào")
        void hongPhaiCoLyDo() {
            assertThatThrownBy(() -> new TelemetryFetch(500, 1, null, SyncFailureKind.HTTP_ERROR, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TelemetryFetch(500, 1, null, SyncFailureKind.HTTP_ERROR, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Thành công thì KHÔNG được có lý do hỏng — nửa cặp mâu thuẫn cũng là một lỗi")
        void thanhCongThiKhongCoLyDo() {
            assertThatThrownBy(() -> new TelemetryFetch(200, 1, "x", null, "vẫn có lý do?"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new TelemetryFetch(200, 1, "x", null, null).thanhCong()).isTrue();
        }
    }

    @Nested
    @DisplayName("TelemetryBatch — kết quả bóc tách")
    class Batch {

        private TelemetryReading soDo(String ma) {
            return new TelemetryReading(ma, Instant.EPOCH, BigDecimal.TEN, TelemetryReading.DON_VI_CM);
        }

        @Test
        @DisplayName("⭐ Quy tắc 2 ép ở hàm dựng: 'nguồn báo hỏng' mà vẫn có số đo ⇒ TỪ CHỐI")
        void nguonHongThiKhongDuocCoSoDo() {
            assertThatThrownBy(() -> new TelemetryBatch(List.of(soDo("F1")), 0, 0, true))
                    .as("một mẻ vừa 'nguồn hỏng' vừa có dữ liệu là một mẻ không ai biết phải tin nửa nào")
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(TelemetryBatch.meBaoHong().soDo()).isEmpty();
        }

        @Test
        @DisplayName("⭐⭐ Quy tắc 9: dưới 50% điểm đo đang hoạt động ⇒ 'nguồn trả thiếu'")
        void quyTac9DuoiNuaLaThieu() {
            TelemetryBatch chinBanGhi = new TelemetryBatch(
                    List.of(
                            soDo("F1"),
                            soDo("F2"),
                            soDo("F3"),
                            soDo("F4"),
                            soDo("F5"),
                            soDo("F6"),
                            soDo("F7"),
                            soDo("F8"),
                            soDo("F9")),
                    0,
                    0,
                    false);

            assertThat(chinBanGhi.thieuDuLieu(19)).as("9 < 19/2 = 9,5 ⇒ thiếu").isTrue();
            assertThat(chinBanGhi.thieuDuLieu(18))
                    .as("9 = 18/2 ⇒ ĐÚNG 50%, ⛔ chưa thiếu")
                    .isFalse();
            assertThat(chinBanGhi.thieuDuLieu(10)).isFalse();
        }

        @Test
        @DisplayName("⭐ Chưa khai điểm đo nào ⇒ KHÔNG cảnh báo thiếu — chuông kêu vì lý do ai cũng biết sẽ bị tắt")
        void chuaKhaiDiemDoThiKhongCanhBao() {
            assertThat(new TelemetryBatch(List.of(), 0, 0, false).thieuDuLieu(0))
                    .as("§10.42: hạ mức một cảnh báo rồi để nó trôi qua là cách nó im vào ngày quan trọng")
                    .isFalse();
        }

        @Test
        @DisplayName("Bộ đếm âm bị từ chối; danh sách số đo là bản sao bất biến")
        void boDemKhongAmVaDanhSachBatBien() {
            assertThatThrownBy(() -> new TelemetryBatch(List.of(), -1, 0, false))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TelemetryBatch(List.of(), 0, -1, false))
                    .isInstanceOf(IllegalArgumentException.class);

            List<TelemetryReading> nguon = new java.util.ArrayList<>(List.of(soDo("F1")));
            TelemetryBatch me = new TelemetryBatch(nguon, 0, 0, false);
            nguon.clear();
            assertThat(me.soDo()).hasSize(1);
        }
    }
}
