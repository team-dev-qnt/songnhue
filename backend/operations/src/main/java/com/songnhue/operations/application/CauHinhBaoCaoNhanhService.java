package com.songnhue.operations.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.HydroSnapshotPort;
import com.songnhue.operations.domain.Bang3SongNhue;
import com.songnhue.operations.domain.BaoCaoNhanh;
import com.songnhue.operations.domain.BaoCaoNhanhViTri;
import com.songnhue.operations.domain.BaoCaoNhanhViTriKy;
import com.songnhue.operations.domain.CongTrinhGan;
import com.songnhue.operations.infra.BaoCaoNhanhQuery;
import com.songnhue.operations.infra.BaoCaoNhanhViTriKyRepository;
import com.songnhue.operations.infra.BaoCaoNhanhViTriRepository;

/**
 * Cấu hình Báo cáo nhanh — công trình gắn vào từng vị trí của mẫu (7 cống Bảng 3 + trạm Yên Nghĩa), và
 * Bảng 3 dựng từ cấu hình ấy.
 *
 * <h2>Vì sao là dữ liệu, ⛔ hằng số</h2>
 *
 * <p>Trước 18/09/2026 14 mã điểm đo và mã trạm Yên Nghĩa nằm trong mã nguồn — Công ty thêm điểm đo cho
 * một vế đang trống (OI-BC14) hay đổi mã trạm là phải chờ một lượt deploy (quy tắc 16). Nay: Công ty
 * GẮN công trình vào vị trí trên màn hình cấu hình; điểm đo từng vế SUY RA từ liên kết điểm đo–công
 * trình ({@code station_constructions.role}) mà màn hình Điểm đo đã quản lý.
 *
 * <h2>Kỳ đã chốt đọc ẢNH CHỤP</h2>
 *
 * <p>Cùng lý lẽ Bảng 2: văn bản đã gửi ⛔ đổi khi cấu hình đổi sau đó. {@link #chup} ghi ảnh chụp lúc
 * CHỐT; {@link #cauHinhKy} chọn nguồn theo trạng thái kỳ.
 */
@Service
public class CauHinhBaoCaoNhanhService {

    static final String LY_DO_CHUA_GAN = "Chưa gắn công trình vào dòng này — chọn ở Cấu hình Báo cáo nhanh";
    static final String LY_DO_CHOT_CHUA_GAN = "Lúc chốt kỳ, dòng này chưa gắn công trình";
    static final String LY_DO_CHOT_CHUA_CO_DIEM_DO = "Lúc chốt kỳ, công trình chưa có điểm đo ở vế này";

    /** Một vế đã giải: {@code apiCode} hoặc {@code lyDo} — đúng một trong hai khác {@code null}. */
    public record VeDiemDo(String apiCode, String lyDo) {

        static VeDiemDo co(String apiCode) {
            return new VeDiemDo(apiCode, null);
        }

        static VeDiemDo thieu(String lyDo) {
            return new VeDiemDo(null, lyDo);
        }
    }

    /** Một vị trí đã giải — {@code congTrinh == null} = chưa gắn. */
    public record ViTri(BaoCaoNhanhViTri viTri, CongTrinhGan congTrinh, VeDiemDo tl, VeDiemDo hl) {}

    /**
     * Cấu hình dùng cho MỘT kỳ — sống (đang nhập) hoặc ảnh chụp (đã chốt).
     *
     * @param tramYenNghia ⚠ mang nguyên {@link CongTrinhGan} chứ ⛔ chỉ {@code Long} (T78.1): câu ghi
     *     chú in TÊN của trạm này ra văn bản, và một cặp (id, tên) truyền rời nhau thì lệch nhau
     *     được — một bản ghi thì ⛔ (quy tắc 14).
     */
    public record CauHinhKy(CongTrinhGan tramYenNghia, Map<String, VeDiemDo[]> bang3) {}

    /** Một ô Bảng 3 — {@code apiCode == null} ⇒ {@code lyDoThieu} nói vì sao. */
    public record OBang3(String apiCode, HydroSnapshotPort.MucNuoc mucNuoc, String lyDoThieu) {}

    public record DongBang3(String nhanCong, String lyTrinh, String nhanTl, OBang3 tl, String nhanHl, OBang3 hl) {}

    private final BaoCaoNhanhViTriRepository viTri;
    private final BaoCaoNhanhViTriKyRepository viTriKy;
    private final BaoCaoNhanhQuery query;
    private final HydroSnapshotPort hydro;

    public CauHinhBaoCaoNhanhService(
            BaoCaoNhanhViTriRepository viTri,
            BaoCaoNhanhViTriKyRepository viTriKy,
            BaoCaoNhanhQuery query,
            HydroSnapshotPort hydro) {
        this.viTri = viTri;
        this.viTriKy = viTriKy;
        this.query = query;
        this.hydro = hydro;
    }

    // ==== Màn hình cấu hình =================================================

    @Transactional(readOnly = true)
    public List<ViTri> danhSach() {
        return giai(viTri.findByDeletedAtIsNullOrderBySortOrder());
    }

    @Transactional(readOnly = true)
    public List<CongTrinhGan> congTrinhTheoLoai(String loai) {
        return query.congTrinhTheoLoai(loai);
    }

    /**
     * Gắn (hoặc gỡ, khi {@code constructionPublicId == null}) công trình vào một vị trí.
     *
     * <p>⛔ nhận công trình khác loại vị trí đòi ({@code OPS-2032}): gắn nhầm *Cống tiêu tự chảy Yên
     * Nghĩa* vào ghi chú *Trạm bơm Yên Nghĩa* thì câu ghi chú luôn "chưa có trong danh mục máy bơm" mà
     * ⛔ ai hiểu vì sao.
     */
    @Transactional
    public ViTri gan(UUID viTriPublicId, UUID constructionPublicId) {
        BaoCaoNhanhViTri vt = viTri.findByPublicIdAndDeletedAtIsNull(viTriPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        Long id = null;
        if (constructionPublicId != null) {
            CongTrinhGan ct = query.congTrinhTheoPublicId(constructionPublicId).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
            if (!vt.getLoaiCongTrinh().equals(ct.loai())) {
                throw new BusinessRuleException(ErrorCode.OPS_2032, ct.ten(), vt.getNhan());
            }
            id = ct.id();
        }
        vt.ganCongTrinh(id);
        return giai(List.of(viTri.save(vt))).get(0);
    }

    // ==== Dùng cho một kỳ ===================================================

    /** Nguồn cấu hình của một kỳ: sống khi đang nhập, ảnh chụp khi đã chốt. */
    @Transactional(readOnly = true)
    public CauHinhKy cauHinhKy(BaoCaoNhanh bc) {
        List<BaoCaoNhanhViTri> ds = viTri.findByDeletedAtIsNullOrderBySortOrder();
        Map<String, VeDiemDo[]> bang3 = new HashMap<>();
        CongTrinhGan yenNghia = null;
        if (!bc.daChot()) {
            for (ViTri v : giai(ds)) {
                bang3.put(v.viTri().getMa(), new VeDiemDo[] {v.tl(), v.hl()});
                if (BaoCaoNhanhViTri.YEN_NGHIA.equals(v.viTri().getMa())) {
                    yenNghia = v.congTrinh();
                }
            }
            return new CauHinhKy(yenNghia, bang3);
        }
        Map<Long, BaoCaoNhanhViTriKy> chup = viTriKy.findByBaoCaoIdAndDeletedAtIsNull(bc.getId()).stream()
                .collect(Collectors.toMap(BaoCaoNhanhViTriKy::getViTriId, Function.identity()));
        Long idYenNghia = null;
        for (BaoCaoNhanhViTri v : ds) {
            BaoCaoNhanhViTriKy k = chup.get(v.getId());
            Long ct = k == null ? null : k.getConstructionId();
            bang3.put(
                    v.getMa(),
                    ct == null
                            ? new VeDiemDo[] {VeDiemDo.thieu(LY_DO_CHOT_CHUA_GAN), VeDiemDo.thieu(LY_DO_CHOT_CHUA_GAN)}
                            : new VeDiemDo[] {veChup(k.getApiTl()), veChup(k.getApiHl())});
            if (BaoCaoNhanhViTri.YEN_NGHIA.equals(v.getMa())) {
                idYenNghia = ct;
            }
        }
        // ⚠ Ảnh chụp ghim *trạm nào*, ⛔ ghim *tên gọi lúc chốt* ⇒ tra lại tên/mã ở danh mục hiện
        //   hành. Cùng lựa chọn Bảng 2 đã làm (tên trạm ở đó cũng đọc sống) — xem javadoc
        //   `TinhBaoCaoNhanh.yenNghia`.
        if (idYenNghia != null) {
            yenNghia = query.congTrinhTheoIds(List.of(idYenNghia)).stream()
                    .findFirst()
                    .orElse(null);
        }
        return new CauHinhKy(yenNghia, bang3);
    }

    /**
     * Chụp cấu hình SỐNG vào kỳ — gọi trong lượt CHỐT, TRƯỚC {@code workflow.execute}. Chốt lại sau khi
     * mở lại ghi đè trọn ảnh chụp cũ (mỗi (kỳ, vị trí) một hàng).
     */
    @Transactional
    public void chup(BaoCaoNhanh bc) {
        Map<Long, BaoCaoNhanhViTriKy> daCo = viTriKy.findByBaoCaoIdAndDeletedAtIsNull(bc.getId()).stream()
                .collect(Collectors.toMap(BaoCaoNhanhViTriKy::getViTriId, Function.identity()));
        for (ViTri v : giai(viTri.findByDeletedAtIsNullOrderBySortOrder())) {
            BaoCaoNhanhViTriKy k =
                    daCo.computeIfAbsent(v.viTri().getId(), id -> new BaoCaoNhanhViTriKy(bc.getId(), id));
            k.ghi(v.congTrinh() == null ? null : v.congTrinh().id(), v.tl().apiCode(), v.hl().apiCode());
            viTriKy.save(k);
        }
    }

    /** Bảng 3 mục 11 — giá trị TẠI {@code den}; ô thiếu điểm đo mang lý do. */
    public List<DongBang3> bang3(CauHinhKy cauHinh, Instant den) {
        List<String> ma = cauHinh.bang3().values().stream()
                .flatMap(Stream::of)
                .map(VeDiemDo::apiCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, HydroSnapshotPort.MucNuoc> so = hydro.mucNuocTaiThoiDiem(ma, den).stream()
                .collect(Collectors.toMap(HydroSnapshotPort.MucNuoc::apiCode, Function.identity()));
        List<DongBang3> ket = new ArrayList<>();
        for (Bang3SongNhue.Dong d : Bang3SongNhue.DONG) {
            VeDiemDo[] ve = cauHinh.bang3().getOrDefault(d.maViTri(), new VeDiemDo[] {
                VeDiemDo.thieu(LY_DO_CHUA_GAN), VeDiemDo.thieu(LY_DO_CHUA_GAN)
            });
            ket.add(new DongBang3(d.nhanCong(), d.lyTrinh(), d.nhanTl(), o(ve[0], so), d.nhanHl(), o(ve[1], so)));
        }
        return ket;
    }

    // ==== Nội bộ ============================================================

    private static OBang3 o(VeDiemDo v, Map<String, HydroSnapshotPort.MucNuoc> so) {
        return v.apiCode() == null
                ? new OBang3(null, null, v.lyDo())
                : new OBang3(v.apiCode(), so.get(v.apiCode()), null);
    }

    private static VeDiemDo veChup(String api) {
        return api == null ? VeDiemDo.thieu(LY_DO_CHOT_CHUA_CO_DIEM_DO) : VeDiemDo.co(api);
    }

    private List<ViTri> giai(List<BaoCaoNhanhViTri> ds) {
        List<Long> ids = ds.stream()
                .map(BaoCaoNhanhViTri::getConstructionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, CongTrinhGan> ct =
                query.congTrinhTheoIds(ids).stream().collect(Collectors.toMap(CongTrinhGan::id, Function.identity()));
        Map<Long, List<HydroSnapshotPort.DiemDoVe>> lienKet = hydro.diemDoMucNuocCuaCongTrinh(ids).stream()
                .collect(Collectors.groupingBy(HydroSnapshotPort.DiemDoVe::constructionId));
        return ds.stream()
                .map(v -> {
                    // Công trình đã xoá mềm ⇒ vắng khỏi `ct` ⇒ coi như chưa gắn.
                    CongTrinhGan c = v.getConstructionId() == null ? null : ct.get(v.getConstructionId());
                    if (c == null) {
                        return new ViTri(v, null, VeDiemDo.thieu(LY_DO_CHUA_GAN), VeDiemDo.thieu(LY_DO_CHUA_GAN));
                    }
                    List<HydroSnapshotPort.DiemDoVe> lk = lienKet.getOrDefault(c.id(), List.of());
                    return new ViTri(v, c, chonVe(c, lk, "THUONG_LUU"), chonVe(c, lk, "HA_LUU"));
                })
                .toList();
    }

    /**
     * Điểm đo của một vế: đúng một liên kết ⇒ nó; nhiều ⇒ liên kết CHÍNH nếu có đúng một; còn lại ⇒ ô
     * trống kèm lý do. ⛔ chọn bừa theo thứ tự — hai điểm đo cùng vế là một câu hỏi cho Công ty.
     */
    static VeDiemDo chonVe(CongTrinhGan c, List<HydroSnapshotPort.DiemDoVe> lk, String vaiTro) {
        String ve = "THUONG_LUU".equals(vaiTro) ? "thượng lưu" : "hạ lưu";
        List<HydroSnapshotPort.DiemDoVe> cung =
                lk.stream().filter(l -> vaiTro.equals(l.vaiTro())).toList();
        if (cung.isEmpty()) {
            return VeDiemDo.thieu("%s chưa liên kết điểm đo %s (OI-BC14)".formatted(c.ten(), ve));
        }
        if (cung.size() == 1) {
            return VeDiemDo.co(cung.get(0).apiCode());
        }
        List<HydroSnapshotPort.DiemDoVe> chinh =
                cung.stream().filter(HydroSnapshotPort.DiemDoVe::chinh).toList();
        if (chinh.size() == 1) {
            return VeDiemDo.co(chinh.get(0).apiCode());
        }
        return VeDiemDo.thieu("%s có %d điểm đo %s — đánh dấu một liên kết chính".formatted(c.ten(), cung.size(), ve));
    }
}
