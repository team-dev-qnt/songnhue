package com.songnhue.core.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.CryptoProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.util.CryptoService;

/**
 * Vé biểu mẫu công khai — T73.9 (ASVS 11.1.2). Biên của từng điều kiện, và điều kiện chịu lực nhất: máy khách ⛔ chọn
 * được mốc phát (đổi mốc là gãy chữ ký).
 */
class VeBieuMauServiceTest {

    private static final String KHOA =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final Instant bayGio = Instant.parse("2026-09-20T03:00:00Z");
    private SettingService settings;
    private VeBieuMauService ve;

    @BeforeEach
    void dung() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("v1");
        props.setKeys(new LinkedHashMap<>(Map.of("v1", KHOA)));
        ReflectionTestUtils.invokeMethod(props, "validateAndDecode");
        settings = mock(SettingService.class);
        when(settings.getInt(eq(VeBieuMauService.KHOA_GIAY_TOI_THIEU), anyInt()))
                .thenReturn(3);
        ve = new VeBieuMauService(new CryptoService(props), settings);
    }

    @Test
    @DisplayName("Biên tuổi tối thiểu: 2 giây ⛔ · 3 giây ✓ (khoá settings = 3)")
    void bienTuoiToiThieu() {
        assertThat(ve.hopLe(ve.phat(bayGio.minusSeconds(2)), bayGio)).isFalse();
        assertThat(ve.hopLe(ve.phat(bayGio.minusSeconds(3)), bayGio)).isTrue();
    }

    @Test
    @DisplayName("Biên hạn: đúng 24 giờ ✓ · 24 giờ + 1 giây ⛔ · mốc ở TƯƠNG LAI ⛔")
    void bienHan() {
        assertThat(ve.hopLe(ve.phat(bayGio.minus(VeBieuMauService.TUOI_TOI_DA)), bayGio))
                .isTrue();
        assertThat(ve.hopLe(ve.phat(bayGio.minus(VeBieuMauService.TUOI_TOI_DA).minusSeconds(1)), bayGio))
                .isFalse();
        assertThat(ve.hopLe(ve.phat(bayGio.plusSeconds(60)), bayGio)).isFalse();
    }

    @Test
    @DisplayName("⛔⛔ Máy khách đổi MỐC trên một vé thật (lùi 10 phút cho 'đủ tuổi') ⇒ chữ ký gãy ⇒ ⛔")
    void doiMocLaGayChuKy() {
        String that = ve.phat(bayGio);
        String[] phan = that.split("\\.", 3);
        String gia = phan[0] + "." + (Long.parseLong(phan[1]) - 600) + "." + phan[2];

        assertThat(ve.hopLe(that, bayGio.plusSeconds(5)))
                .as("đối chứng: vé thật đủ tuổi")
                .isTrue();
        assertThat(ve.hopLe(gia, bayGio)).isFalse();
    }

    @Test
    @DisplayName("⛔ Vé thiếu · rác · sai phiên bản · chữ ký sửa một ký tự ⇒ ⛔")
    void veHong() {
        String that = ve.phat(bayGio.minusSeconds(30));
        String suaMotKyTu = that.substring(0, that.length() - 1) + (that.endsWith("0") ? "1" : "0");

        assertThat(ve.hopLe(null, bayGio)).isFalse();
        assertThat(ve.hopLe("", bayGio)).isFalse();
        assertThat(ve.hopLe("rac", bayGio)).isFalse();
        assertThat(ve.hopLe(that.replaceFirst("^v1", "v2"), bayGio)).isFalse();
        assertThat(ve.hopLe(suaMotKyTu, bayGio)).isFalse();
        assertThat(ve.hopLe(that, bayGio)).as("đối chứng").isTrue();
    }

    @Test
    @DisplayName("Tuổi tối thiểu ĐỌC từ settings: đặt 0 ⇒ vé vừa phát hợp lệ (⛔ ghi cứng 3)")
    void tuoiDocTuSettings() {
        when(settings.getInt(eq(VeBieuMauService.KHOA_GIAY_TOI_THIEU), anyInt()))
                .thenReturn(0);
        assertThat(ve.hopLe(ve.phat(bayGio), bayGio)).isTrue();

        when(settings.getInt(eq(VeBieuMauService.KHOA_GIAY_TOI_THIEU), anyInt()))
                .thenReturn(9999);
        assertThat(ve.tuoiToiThieu())
                .as("kẹp ở trần %d giây", VeBieuMauService.TRAN_GIAY_TOI_THIEU)
                .isEqualTo(Duration.ofSeconds(VeBieuMauService.TRAN_GIAY_TOI_THIEU));
        assertThat(ve.tuoiToiThieuGiay())
                .as("số giây trả cho cổng cũng là số ĐÃ kẹp — cổng chờ 9999 giây là biểu mẫu chết")
                .isEqualTo(VeBieuMauService.TRAN_GIAY_TOI_THIEU);
    }

    @Test
    @DisplayName("kiem ⇒ CMS-2025 cho mọi nhánh hỏng — một mã, ⛔ gợi ý cho máy phải sửa gì")
    void kiemNemMotMa() {
        assertThatThrownBy(() -> ve.kiem("rac", bayGio))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((AppException) e).errorCode())
                .isEqualTo(ErrorCode.CMS_2025);
    }
}
