package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ReadingRow} và {@link UnmappedRow} — hai kiểu dữ liệu đi vào đường ingest. */
class ReadingRowTest {

    private static final Instant KHUNG = Instant.parse("2026-09-01T10:20:00Z");

    private static final ChanDoanChatLuong TOT = ChanDoanChatLuong.hopLe();
    private static final ChanDoanChatLuong NGO =
            ChanDoanChatLuong.nghiNgo(LyDoNghiNgo.NGOAI_KHOANG_VAT_LY, "Giá trị 99.900 ngoài khoảng vật lý [-10 … 30]");

    @Test
    @DisplayName("⭐ hopLe() là vế đọc của quy tắc 14 — chỉ HOP_LE mới được hiển thị và so ngưỡng")
    void chiHopLeMoiDuocDung() {
        ReadingRow tot = new ReadingRow(1L, 2L, KHUNG, new BigDecimal("4.930"), TOT, ReadingSource.API, 9L);
        ReadingRow nghi = new ReadingRow(1L, 2L, KHUNG, new BigDecimal("99.900"), NGO, ReadingSource.API, 9L);

        assertThat(tot.hopLe()).isTrue();
        assertThat(nghi.hopLe())
                .as("bản ghi NGHI_NGO vẫn được GHI vào bảng chính, nhưng ⛔ không được đem đi hiển thị")
                .isFalse();
    }

    @Test
    @DisplayName("⛔ Năm trường bắt buộc bị chặn ở hàm dựng — mỗi cái là một INSERT hỏng giữa lượt ingest")
    void truongBatBuocBiChan() {
        BigDecimal v = new BigDecimal("4.930");
        assertThatThrownBy(() -> new ReadingRow(null, 2L, KHUNG, v, TOT, ReadingSource.API, null))
                .hasMessageContaining("stationId");
        assertThatThrownBy(() -> new ReadingRow(1L, null, KHUNG, v, TOT, ReadingSource.API, null))
                .hasMessageContaining("measurementTypeId");
        assertThatThrownBy(() -> new ReadingRow(1L, 2L, null, v, TOT, ReadingSource.API, null))
                .hasMessageContaining("measuredAt");
        assertThatThrownBy(() -> new ReadingRow(1L, 2L, KHUNG, null, TOT, ReadingSource.API, null))
                .hasMessageContaining("value");
        assertThatThrownBy(() -> new ReadingRow(1L, 2L, KHUNG, v, null, ReadingSource.API, null))
                .hasMessageContaining("chanDoan");
    }

    @Test
    @DisplayName("⭐ Giá trị giữ nguyên thang số BigDecimal — ⛔ cấm float/double (quy tắc 2)")
    void giuNguyenThangSo() {
        // Sai chỗ này là sai TOÀN BỘ ngưỡng cảnh báo: nguồn trả cm nguyên, adapter chia 100 với
        // scale 3. `2.30` và `2.3` là hai chuỗi khác nhau ở màn hình, dù bằng nhau về giá trị.
        ReadingRow r = new ReadingRow(1L, 2L, KHUNG, new BigDecimal("2.300"), TOT, ReadingSource.API, null);
        assertThat(r.value().scale()).isEqualTo(3);
        assertThat(r.value().toPlainString()).isEqualTo("2.300");
    }

    @Test
    @DisplayName("⛔ Mã lạ: đơn vị nguồn là bắt buộc — số không đơn vị là số vô nghĩa")
    void maLaBatBuocCoDonVi() {
        assertThatThrownBy(() -> new UnmappedRow("F01613", 1L, KHUNG, new BigDecimal("198"), "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawUnit");

        UnmappedRow ok = new UnmappedRow("F01613", 1L, KHUNG, new BigDecimal("198"), "cm", 9L);
        assertThat(ok.rawValue())
                .as("⛔ và giá trị giữ NGUYÊN VĂN nguồn — quy đổi lúc chưa biết loại chỉ số là đoán")
                .isEqualByComparingTo("198");
    }
}
