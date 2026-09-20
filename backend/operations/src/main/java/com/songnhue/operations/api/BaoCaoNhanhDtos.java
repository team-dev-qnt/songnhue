package com.songnhue.operations.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.HydroSnapshotPort;
import com.songnhue.operations.application.BaoCaoNhanhService;
import com.songnhue.operations.application.CauHinhBaoCaoNhanhService;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.BaoCaoNhanh;
import com.songnhue.operations.domain.CongTrinhGan;
import com.songnhue.operations.domain.TinhBaoCaoNhanh;

/**
 * DTO của Báo cáo nhanh.
 *
 * <p>⛔ Mọi con số ở đây do BE tính (quy tắc 3); FE chỉ định dạng. Ô cố ý TRỐNG mang
 * {@code @JsonInclude(ALWAYS)}: cấu hình {@code NON_NULL} chung xoá hẳn khoá, và phía nhận ⛔ phân
 * biệt được "ô trống" với "API đổi tên trường" (coding-guide §4).
 */
public final class BaoCaoNhanhDtos {

    private BaoCaoNhanhDtos() {}

    // ==== Yêu cầu ===========================================================

    /** Khung giờ — cả tạo kỳ lẫn sửa khung. 2 trường ⇒ ⛔ chỗ cho một trường lặng lẽ biến mất. */
    public record KhungRequest(@NotNull Instant tuThoiDiem, @NotNull Instant denThoiDiem) {}

    public record OVanHanh(@NotNull UUID nhomMayPublicId, @Min(0) @Max(999) Integer soMayVanHanh) {}

    /** Bảng 2 — gửi các ô ĐÃ ĐỔI; ô ⛔ gửi giữ nguyên. */
    public record VanHanhRequest(@NotNull @Size(max = 2000) List<@Valid OVanHanh> o) {}

    public record ONgapUng(
            @NotNull UUID xaPublicId,
            @DecimalMin("0") BigDecimal ngapTrangLua,
            @DecimalMin("0") BigDecimal ngapTrangRau,
            @DecimalMin("0") BigDecimal sauNuocLua,
            @DecimalMin("0") BigDecimal sauNuocRau) {}

    /** Bảng 5 — mỗi xã gửi lên là thay TOÀN PHẦN bốn ô của xã ấy. */
    public record NgapUngRequest(@NotNull @Size(max = 100) List<@Valid ONgapUng> dong) {}

    public record OLuongMua(
            @NotNull UUID diemMuaPublicId, @DecimalMin("0") @Digits(integer = 7, fraction = 1) BigDecimal luongMuaMm) {}

    /** Bảng 4 — gửi các ô ĐÃ ĐỔI; {@code luongMuaMm = null} xoá số đã nhập. */
    public record LuongMuaRequest(@NotNull @Size(max = 100) List<@Valid OLuongMua> o) {}

    public record MoLaiRequest(@NotBlank @Size(max = 1000) String lyDo) {}

    /** Gắn công trình vào một vị trí của mẫu — {@code null} gỡ ra. */
    public record GanCongTrinhRequest(UUID constructionPublicId) {}

    // ==== Phản hồi ==========================================================

    public record KyView(
            UUID publicId,
            Instant tuThoiDiem,
            Instant denThoiDiem,
            String trangThai,
            String lyDoMoLai,
            Instant createdAt) {

        static KyView of(BaoCaoNhanh b) {
            return new KyView(
                    b.getPublicId(),
                    b.getTuThoiDiem(),
                    b.getDenThoiDiem(),
                    b.getTrangThai().name(),
                    b.getLyDoMoLai(),
                    b.getCreatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NhomView(
            UUID nhomMayPublicId, int soMayThietKe, BigDecimal qMotMayM3h, String coMay, Integer soMayVanHanh) {}

    /**
     * @param ma mã công trình — màn hình nhập liệu bày ra để phân biệt hai trạm TRÙNG TÊN. ⛔ đi vào
     *     bản Word (cột của mẫu là *"Tên trạm bơm"*).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TramView(
            UUID constructionPublicId, String ma, String ten, String nguonTuoiHuongTieu, List<NhomView> nhom) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record KhoiView(String tenDonVi, int tongMayThietKe, List<TramView> tram) {}

    public record Bang1View(int tongTram, int tongMay, int[] theoCo, BigDecimal tongLuuLuongM3h) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Muc1View(Integer tongTram, Integer tongMay, BigDecimal tongLuuLuongM3h) {}

    /**
     * @param tenTram · {@code maTram} trạm Công ty đã gắn vào vị trí ghi chú — {@code null} khi chưa
     *     gắn. Màn hình bày cả hai để người lập báo cáo thấy câu kia đang nói về trạm nào; mã ⛔ đi
     *     vào bản Word (T78.1).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record YenNghiaView(
            String trangThai, String cau, Integer soMay, BigDecimal luuLuongM3s, String tenTram, String maTram) {}

    /**
     * @param lyDo {@code null} khi có số; ngược lại nói VÌ SAO ô trống — ⛔ có điểm đo, hay ⛔ có số
     *     đo hợp lệ trong 24 giờ trước mốc
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OMucNuocView(
            String nhan, String apiCode, BigDecimal giaTriM, Instant mocDo, Boolean dungMoc, String lyDo) {}

    public record DongBang3View(String nhanCong, String lyTrinh, OMucNuocView tl, OMucNuocView hl) {}

    /** Một điểm mưa Bảng 4 — {@code luongMuaMm = null} = chưa nhập. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DongBang4View(UUID diemMuaPublicId, String ten, int thuTu, BigDecimal luongMuaMm) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DongBang5View(UUID xaPublicId, String ten, int thuTu, TinhBaoCaoNhanh.ChinO o) {}

    /**
     * Toàn bộ một kỳ.
     *
     * @param coMay nhãn 9 cột Bảng 1 — thứ tự của {@code bang1SongNhue.theoCo}
     * @param bang1SongNhue {@code null} = chưa ô nào của Bảng 2 được nhập
     * @param bang4 lượng mưa NHẬP TAY — nguồn tự động (G3-a) chưa có
     * @param muc3 COPY dòng III của Bảng 5 — cùng giá trị {@code bang5CongTy}, khai riêng để giao diện
     *     ⛔ phải biết luật copy
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChiTietView(
            KyView ky,
            List<AllowedAction> hanhDong,
            List<String> coMay,
            List<KhoiView> bang2,
            Bang1View bang1SongNhue,
            Muc1View muc1,
            YenNghiaView ghiChuYenNghia,
            List<DongBang3View> bang3,
            List<DongBang4View> bang4,
            List<DongBang5View> bang5,
            TinhBaoCaoNhanh.ChinO bang5CongTy,
            TinhBaoCaoNhanh.ChinO muc3) {

        static ChiTietView of(BaoCaoNhanhService.ChiTiet c) {
            BangCoMayBom bang = c.bangCo();
            List<KhoiView> b2 = c.bang2().stream()
                    .map(k -> new KhoiView(
                            c.tenDonVi().get(k.orgUnitId()),
                            k.tongMayThietKe(),
                            k.tram().stream()
                                    .map(t -> new TramView(
                                            t.nhom().get(0).nhom().constructionPublicId(),
                                            t.ma(),
                                            t.ten(),
                                            t.nguonTuoiHuongTieu(),
                                            t.nhom().stream()
                                                    .map(d -> new NhomView(
                                                            d.nhom().nhomPublicId(),
                                                            d.nhom().soMay(),
                                                            d.nhom().qM3h(),
                                                            bang.co()
                                                                    .get(bang.xep(d.nhom()
                                                                            .qM3h()))
                                                                    .nhan(),
                                                            d.soMayVanHanh()))
                                                    .toList()))
                                    .toList()))
                    .toList();
            TinhBaoCaoNhanh.DongBang1 b1 = c.bang1SongNhue();
            TinhBaoCaoNhanh.GhiChuYenNghia yn = c.ghiChuYenNghia();
            return new ChiTietView(
                    KyView.of(c.baoCao()),
                    c.hanhDong(),
                    bang.co().stream().map(BangCoMayBom.Co::nhan).toList(),
                    b2,
                    b1 == null ? null : new Bang1View(b1.tongTram(), b1.tongMay(), b1.theoCo(), b1.tongLuuLuongM3h()),
                    new Muc1View(
                            c.muc1().tongTram(), c.muc1().tongMay(), c.muc1().tongLuuLuongM3h()),
                    new YenNghiaView(
                            yn.trangThai().name(), yn.cau(), yn.soMay(), yn.luuLuongM3s(), yn.tenTram(), yn.maTram()),
                    c.bang3().stream()
                            .map(d -> new DongBang3View(
                                    d.nhanCong(),
                                    d.lyTrinh(),
                                    oMucNuoc(d.nhanTl(), d.tl()),
                                    oMucNuoc(d.nhanHl(), d.hl())))
                            .toList(),
                    c.bang4().stream()
                            .map(d -> new DongBang4View(d.diemMuaPublicId(), d.ten(), d.thuTu(), d.luongMuaMm()))
                            .toList(),
                    c.bang5().stream()
                            .map(d -> new DongBang5View(d.xa().xaPublicId(), d.xa().ten(), d.xa().thuTu(), d.o()))
                            .toList(),
                    c.bang5CongTy(),
                    c.bang5CongTy());
        }

        private static OMucNuocView oMucNuoc(String nhan, CauHinhBaoCaoNhanhService.OBang3 o) {
            if (o.apiCode() == null) {
                return new OMucNuocView(nhan, null, null, null, null, o.lyDoThieu());
            }
            HydroSnapshotPort.MucNuoc m = o.mucNuoc();
            if (m == null || m.giaTriM() == null) {
                return new OMucNuocView(nhan, o.apiCode(), null, null, null, LY_DO_KHONG_CO_SO_DO);
            }
            return new OMucNuocView(nhan, o.apiCode(), m.giaTriM(), m.mocDo(), m.dungMoc(), null);
        }
    }

    static final String LY_DO_KHONG_CO_SO_DO = "Không có số đo hợp lệ trong 24 giờ trước mốc báo cáo";

    // ==== Cấu hình ==========================================================

    public record CongTrinhView(UUID publicId, String ma, String ten) {

        static CongTrinhView of(CongTrinhGan c) {
            return c == null ? null : new CongTrinhView(c.publicId(), c.ma(), c.ten());
        }
    }

    /** Vế đã giải — đúng một trong hai: mã điểm đo, hoặc lý do ô sẽ TRỐNG. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VeView(String apiCode, String lyDo) {}

    /**
     * Một vị trí của mẫu.
     *
     * @param congTrinh {@code null} = chưa gắn
     * @param tl điểm đo thượng lưu suy ra — {@code null} ở vị trí ⛔ thuộc Bảng 3 (Yên Nghĩa)
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ViTriView(
            UUID publicId,
            String ma,
            String nhan,
            String loaiCongTrinh,
            CongTrinhView congTrinh,
            VeView tl,
            VeView hl) {

        static ViTriView of(CauHinhBaoCaoNhanhService.ViTri v) {
            boolean bang3 = v.viTri().getMa().startsWith("B3_");
            return new ViTriView(
                    v.viTri().getPublicId(),
                    v.viTri().getMa(),
                    v.viTri().getNhan(),
                    v.viTri().getLoaiCongTrinh(),
                    CongTrinhView.of(v.congTrinh()),
                    bang3 ? new VeView(v.tl().apiCode(), v.tl().lyDo()) : null,
                    bang3 ? new VeView(v.hl().apiCode(), v.hl().lyDo()) : null);
        }
    }
}
