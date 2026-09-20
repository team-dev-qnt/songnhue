package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.operations.domain.BaoCaoNhanhLuongMua;
import com.songnhue.operations.domain.BaoCaoNhanhNgapUng;
import com.songnhue.operations.domain.TinhBaoCaoNhanh.DongNgapUng;
import com.songnhue.operations.infra.BaoCaoNhanhLuongMuaRepository;
import com.songnhue.operations.infra.BaoCaoNhanhNgapUngRepository;
import com.songnhue.operations.infra.BaoCaoNhanhQuery;

/**
 * Hai bảng NHẬP TAY theo kỳ của Báo cáo nhanh — Bảng 5 (diện tích ngập úng, ha) và Bảng 4 (lượng
 * mưa, mm). {@code null} = chưa nhập (ô trống), khác 0.
 *
 * <p>⛔ kiểm trạng thái kỳ ở đây: nơi gọi ({@link BaoCaoNhanhService}) đã chặn kỳ đã chốt
 * ({@code OPS-2029}) trước khi truyền {@code baoCaoId} vào.
 *
 * <h2>Bảng 4 nhập tay — ĐẢO chốt sáng 18/09/2026</h2>
 *
 * <p>Chốt ban đầu: Bảng 4 để trống cho tới khi có nguồn lượng mưa tự động (G3-a). Cùng ngày QuanTran
 * chốt lại: mục chờ Công ty thì cho nhập trên giao diện. Ngày G3-a về, nguồn tự động THAY ô nhập — ⛔
 * trộn hai nguồn trong một kỳ.
 */
@Service
public class SoLieuNhapTayService {

    /** Một dòng nhập của Bảng 5 — thay TOÀN PHẦN bốn ô của xã. */
    public record NhapNgapUng(
            UUID xaPublicId,
            BigDecimal ngapTrangLua,
            BigDecimal ngapTrangRau,
            BigDecimal sauNuocLua,
            BigDecimal sauNuocRau) {}

    /** Một ô nhập của Bảng 4 — {@code luongMuaMm = null} xoá số đã nhập. */
    public record NhapLuongMua(UUID diemMuaPublicId, BigDecimal luongMuaMm) {}

    /** Một dòng Bảng 4 để hiển thị / điền bản Word. */
    public record DongLuongMua(UUID diemMuaPublicId, String ten, int thuTu, BigDecimal luongMuaMm) {}

    private final BaoCaoNhanhNgapUngRepository ngapUng;
    private final BaoCaoNhanhLuongMuaRepository luongMua;
    private final BaoCaoNhanhQuery query;

    public SoLieuNhapTayService(
            BaoCaoNhanhNgapUngRepository ngapUng, BaoCaoNhanhLuongMuaRepository luongMua, BaoCaoNhanhQuery query) {
        this.ngapUng = ngapUng;
        this.luongMua = luongMua;
        this.query = query;
    }

    // ==== Bảng 5 ============================================================

    /** Ghi Bảng 5 — thay TOÀN PHẦN bốn ô của mỗi xã gửi lên. Chỉ xã của Sông Nhuệ. */
    @Transactional
    public void luuNgapUng(Long baoCaoId, List<NhapNgapUng> nhap) {
        Map<UUID, BaoCaoNhanhQuery.Xa> xa = query.xa(BaoCaoNhanhService.CONG_TY).stream()
                .collect(Collectors.toMap(BaoCaoNhanhQuery.Xa::publicId, Function.identity()));
        Map<Long, BaoCaoNhanhNgapUng> daCo = ngapUng.findByBaoCaoIdAndDeletedAtIsNull(baoCaoId).stream()
                .collect(Collectors.toMap(BaoCaoNhanhNgapUng::getDonViHanhChinhId, Function.identity()));

        for (NhapNgapUng o : nhap) {
            BaoCaoNhanhQuery.Xa x = xa.get(o.xaPublicId());
            if (x == null) {
                // ⛔ Xã của công ty khác (hoặc id bịa) — hệ ⛔ nhập hộ ba công ty kia (OI-BC1).
                throw new ValidationException(ErrorCode.SYS_0003).withDetail("xaPublicId", "NOT_FOUND", o.xaPublicId());
            }
            for (BigDecimal v : new BigDecimal[] {o.ngapTrangLua(), o.ngapTrangRau(), o.sauNuocLua(), o.sauNuocRau()}) {
                khongAm("dienTich", v);
            }
            BaoCaoNhanhNgapUng dong = daCo.computeIfAbsent(x.id(), id -> new BaoCaoNhanhNgapUng(baoCaoId, id));
            dong.ghi(o.ngapTrangLua(), o.ngapTrangRau(), o.sauNuocLua(), o.sauNuocRau());
            ngapUng.save(dong);
        }
    }

    @Transactional(readOnly = true)
    public List<DongNgapUng> ngapUngCua(Long baoCaoId) {
        Map<Long, BaoCaoNhanhNgapUng> daNhap = ngapUng.findByBaoCaoIdAndDeletedAtIsNull(baoCaoId).stream()
                .collect(Collectors.toMap(BaoCaoNhanhNgapUng::getDonViHanhChinhId, Function.identity()));
        return query.xa(BaoCaoNhanhService.CONG_TY).stream()
                .map(x -> {
                    BaoCaoNhanhNgapUng n = daNhap.get(x.id());
                    return new DongNgapUng(
                            x.id(),
                            x.publicId(),
                            x.ten(),
                            x.thuTu(),
                            n == null ? null : n.getNgapTrangLua(),
                            n == null ? null : n.getNgapTrangRau(),
                            n == null ? null : n.getSauNuocLua(),
                            n == null ? null : n.getSauNuocRau());
                })
                .toList();
    }

    // ==== Bảng 4 ============================================================

    /** Ghi Bảng 4 — chỉ các ô gửi lên; ô ⛔ gửi giữ nguyên. */
    @Transactional
    public void luuLuongMua(Long baoCaoId, List<NhapLuongMua> nhap) {
        Map<UUID, BaoCaoNhanhQuery.DiemMua> diem = query.diemMua().stream()
                .collect(Collectors.toMap(BaoCaoNhanhQuery.DiemMua::publicId, Function.identity()));
        Map<Long, BaoCaoNhanhLuongMua> daCo = luongMua.findByBaoCaoIdAndDeletedAtIsNull(baoCaoId).stream()
                .collect(Collectors.toMap(BaoCaoNhanhLuongMua::getDiemMuaId, Function.identity()));

        for (NhapLuongMua o : nhap) {
            BaoCaoNhanhQuery.DiemMua d = diem.get(o.diemMuaPublicId());
            if (d == null) {
                throw new ValidationException(ErrorCode.SYS_0003)
                        .withDetail("diemMuaPublicId", "NOT_FOUND", o.diemMuaPublicId());
            }
            khongAm("luongMuaMm", o.luongMuaMm());
            BaoCaoNhanhLuongMua dong = daCo.computeIfAbsent(d.id(), id -> new BaoCaoNhanhLuongMua(baoCaoId, id));
            dong.ghi(o.luongMuaMm());
            luongMua.save(dong);
        }
    }

    @Transactional(readOnly = true)
    public List<DongLuongMua> luongMuaCua(Long baoCaoId) {
        Map<Long, BigDecimal> daNhap = luongMua.findByBaoCaoIdAndDeletedAtIsNull(baoCaoId).stream()
                .filter(l -> l.getLuongMuaMm() != null)
                .collect(Collectors.toMap(BaoCaoNhanhLuongMua::getDiemMuaId, BaoCaoNhanhLuongMua::getLuongMuaMm));
        return query.diemMua().stream()
                .map(d -> new DongLuongMua(d.publicId(), d.ten(), d.thuTu(), daNhap.get(d.id())))
                .toList();
    }

    private static void khongAm(String truong, BigDecimal v) {
        if (v != null && v.signum() < 0) {
            throw new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "MIN", v);
        }
    }
}
