package com.songnhue.hydro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.spi.SecurityEventPort;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.infra.ApiSourceRepository;
import com.songnhue.hydro.infra.StationRepository;

@ExtendWith(MockitoExtension.class)
class ApiSourceServiceTest {

    private static final UUID PUBLIC_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock
    private ApiSourceRepository sources;

    @Mock
    private StationRepository stations;

    @Mock
    private HydroSettings settings;

    @Mock
    private CryptoService crypto;

    @Mock
    private SecurityEventPort securityEvents;

    @InjectMocks
    private ApiSourceService service;

    private static ApiSource nguon() {
        return new ApiSource("BHH40", "Telemetry Sông Nhuệ", AdapterType.BHH40, "http://songnhue.bhh40.net");
    }

    /**
     * ⭐ Bốn cột {@code null} ⇒ nhận tham số chung, và <b>cờ nói rõ điều đó</b>.
     *
     * <p>Nếu chỉ khẳng định giá trị mà bỏ cờ thì bài kiểm vẫn xanh với một cài đặt luôn trả
     * {@code dungChung = false} — và màn hình sẽ nói với người vận hành rằng nguồn có cấu hình riêng
     * trong khi nó đang chạy theo tham số chung.
     */
    @Test
    @DisplayName("Không cột nào đặt riêng → dùng tham số chung, cờ dungChung = true")
    void khongDatRiengThiDungThamSoChung() {
        when(settings.cronPolling()).thenReturn("45 1/2 * * * *");
        when(settings.khungNguon()).thenReturn(Duration.ofMinutes(10));
        when(settings.timeoutGoiNguon()).thenReturn(Duration.ofSeconds(30));
        when(settings.soLanThuLai()).thenReturn(3);

        ThamSoNguon thamSo = service.thamSoHieuLuc(nguon());

        assertThat(thamSo.cron()).isEqualTo("45 1/2 * * * *");
        assertThat(thamSo.khungNguon()).isEqualTo(Duration.ofMinutes(10));
        assertThat(thamSo.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(thamSo.soLanThuLai()).isEqualTo(3);
        assertThat(thamSo.cronDungChung()).isTrue();
        assertThat(thamSo.khungDungChung()).isTrue();
        assertThat(thamSo.timeoutDungChung()).isTrue();
        assertThat(thamSo.thuLaiDungChung()).isTrue();
        assertThat(thamSo.coTuyChinh()).isFalse();
    }

    /**
     * Cột riêng thắng tham số chung — và bài kiểm dùng giá trị <b>khác hẳn</b> giá trị chung.
     *
     * <p>{@code architecture-review.md} §10.29-a: canh giá trị ĐÃ GIẢI. Nếu cột riêng đặt trùng con
     * số mặc định thì bài kiểm xanh dù cài đặt bỏ qua cột riêng hoàn toàn.
     */
    @Test
    @DisplayName("Cột riêng thắng tham số chung, và các tham số khác vẫn dùng chung")
    void cotRiengThangThamSoChung() {
        when(settings.khungNguon()).thenReturn(Duration.ofMinutes(10));
        when(settings.timeoutGoiNguon()).thenReturn(Duration.ofSeconds(30));
        when(settings.soLanThuLai()).thenReturn(3);
        ApiSource nguon = nguon();
        nguon.setCron("0 0/5 * * * *");

        ThamSoNguon thamSo = service.thamSoHieuLuc(nguon);

        assertThat(thamSo.cron()).isEqualTo("0 0/5 * * * *");
        assertThat(thamSo.cronDungChung()).isFalse();
        assertThat(thamSo.khungDungChung()).as("chỉ cron đặt riêng").isTrue();
        assertThat(thamSo.coTuyChinh()).isTrue();
        verify(settings, never()).cronPolling();
    }

    /**
     * ⚠ Cron rỗng (không phải {@code null}) cũng phải rơi về tham số chung.
     *
     * <p>Một ô nhập trên giao diện xoá trắng gửi lên chuỗi {@code ""}, không phải {@code null}. Nếu
     * chuỗi rỗng lọt qua thì Spring nhận cron rỗng và lịch polling <b>im lặng không chạy</b>.
     */
    @Test
    @DisplayName("Cron rỗng cũng là 'dùng tham số chung', không phải cron rỗng")
    void cronRongThiVanDungThamSoChung() {
        when(settings.cronPolling()).thenReturn("45 1/2 * * * *");
        when(settings.khungNguon()).thenReturn(Duration.ofMinutes(10));
        when(settings.timeoutGoiNguon()).thenReturn(Duration.ofSeconds(30));
        when(settings.soLanThuLai()).thenReturn(3);
        ApiSource nguon = nguon();
        nguon.setCron("   ");

        ThamSoNguon thamSo = service.thamSoHieuLuc(nguon);

        assertThat(thamSo.cron()).isEqualTo("45 1/2 * * * *");
        assertThat(thamSo.cronDungChung()).isTrue();
    }

    /**
     * ⛔ Mã số lưu vào CSDL phải là <b>bản mã</b>, không phải chuỗi thô.
     *
     * <p>Khẳng định hai chiều: cột chứa đúng bản mã, và cột <b>không</b> chứa chuỗi thô. Chỉ khẳng
     * định vế đầu thì một cài đặt quên gọi {@code encrypt} vẫn có thể xanh nếu {@code crypto} bị
     * stub trả về chính đầu vào.
     */
    @Test
    @DisplayName("Đặt mã số lưu bản mã, không lưu chuỗi thô, và ghi sự kiện bảo mật")
    void datMaSoThiMaHoaVaGhiSuKien() {
        ApiSource nguon = nguon();
        when(sources.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(java.util.Optional.of(nguon));
        when(crypto.encrypt("MASO-THAT;")).thenReturn("v1:YmFuLW1h");

        service.datMaSo(PUBLIC_ID, "MASO-THAT;");

        assertThat(nguon.getCredential()).isEqualTo("v1:YmFuLW1h");
        assertThat(nguon.getCredential()).isNotEqualTo("MASO-THAT;");
        assertThat(nguon.isCredentialDaCauHinh()).isTrue();
        verify(securityEvents).externalCredentialChanged("BHH40", "DAT_LAN_DAU");
    }

    /**
     * ⚠ Dấu {@code ;} cuối mã số KHÔNG được cắt.
     *
     * <p>Thiếu nó thì nguồn trả {@code not.working} — trông y hệt lỗi sai mã số, nên người vận hành
     * sẽ đi tìm mã số mới thay vì tìm chỗ {@code trim()}.
     */
    @Test
    @DisplayName("Không trim dấu ';' cuối mã số")
    void giuNguyenDauChamPhayCuoi() {
        ApiSource nguon = nguon();
        when(sources.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(java.util.Optional.of(nguon));
        when(crypto.encrypt(anyString())).thenReturn("v1:x");

        service.datMaSo(PUBLIC_ID, "  MASO-THAT;  ");

        verify(crypto).encrypt("  MASO-THAT;  ");
    }

    @Test
    @DisplayName("Thay mã số đang có ghi sự kiện THAY, không phải DAT_LAN_DAU")
    void thayMaSoGhiSuKienKhac() {
        ApiSource nguon = nguon();
        nguon.datCredential("v1:cu");
        when(sources.findByPublicIdAndDeletedAtIsNull(PUBLIC_ID)).thenReturn(java.util.Optional.of(nguon));
        when(crypto.encrypt(anyString())).thenReturn("v1:moi");

        service.datMaSo(PUBLIC_ID, "MASO-MOI;");

        verify(securityEvents).externalCredentialChanged("BHH40", "THAY");
        verify(securityEvents, never()).externalCredentialChanged(anyString(), eq("DAT_LAN_DAU"));
    }

    /**
     * Giải mã hỏng → trả {@code null} và để lại sự kiện bảo mật, KHÔNG ném lên trên.
     *
     * <p>Ném lên thì lượt polling đổ với một stack trace về AES và người trực ban đi tìm lỗi mạng.
     * Trả {@code null} có sự kiện thì nguồn hiện đúng "không gọi được", còn nguyên nhân thật nằm ở
     * nhật ký bảo mật — nơi người quản trị hệ thống tìm.
     */
    @Test
    @DisplayName("Giải mã hỏng: trả null + ghi sự kiện, không ném ngoại lệ")
    void giaiMaHongThiGhiSuKien() {
        ApiSource nguon = nguon();
        nguon.datCredential("v1:hong");
        when(crypto.decrypt("v1:hong")).thenThrow(new IllegalStateException("sai khoá"));
        when(crypto.keyIdOf("v1:hong")).thenReturn("v1");

        String maSo = service.maSoDeGoi(nguon);

        assertThat(maSo).isNull();
        verify(securityEvents).externalCredentialDecryptFailed("BHH40", "v1");
    }

    @Test
    @DisplayName("Nguồn chưa cấu hình: không gọi giải mã, không sinh sự kiện")
    void chuaCauHinhThiKhongGoiGiaiMa() {
        String maSo = service.maSoDeGoi(nguon());

        assertThat(maSo).isNull();
        verify(crypto, never()).decrypt(any());
        verify(securityEvents, never()).externalCredentialDecryptFailed(anyString(), any());
    }
}
