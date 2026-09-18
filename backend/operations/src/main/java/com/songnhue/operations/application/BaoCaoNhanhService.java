package com.songnhue.operations.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitTreeRef;
import com.songnhue.core.spi.WorkflowPort;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.BaoCaoNhanh;
import com.songnhue.operations.domain.BaoCaoNhanhVanHanh;
import com.songnhue.operations.domain.DongNhomMay;
import com.songnhue.operations.domain.TinhBaoCaoNhanh;
import com.songnhue.operations.domain.TinhBaoCaoNhanh.DongVanHanh;
import com.songnhue.operations.infra.BaoCaoNhanhQuery;
import com.songnhue.operations.infra.BaoCaoNhanhRepository;
import com.songnhue.operations.infra.BaoCaoNhanhVanHanhRepository;

/**
 * Báo cáo nhanh — kỳ báo cáo, nhập Bảng 2/5, chốt/mở lại, và dựng TOÀN BỘ nội dung dẫn xuất.
 *
 * <h2>Nguồn của Bảng 2 đổi theo trạng thái — và đó là chủ ý</h2>
 *
 * <ul>
 *   <li><b>Đang nhập</b>: danh mục SỐNG ⟕ số đã nhập — trạm Công ty vừa thêm hiện ngay, số máy thiết
 *       kế vừa sửa có hiệu lực ngay.
 *   <li><b>Đã chốt</b>: ẢNH CHỤP của kỳ — văn bản đã gửi UBND ⛔ đổi theo danh mục.
 * </ul>
 *
 * <p>Lượt CHỐT là lúc chụp: mọi nhóm máy sống được ghi xuống bảng của kỳ (kể cả nhóm chưa nhập, với
 * {@code so_may_van_hanh = NULL}).
 *
 * <h2>⛔ Kiểm nghiệp vụ TRƯỚC {@code workflow.execute}</h2>
 *
 * <p>Engine ghi thông báo ⇒ flush ⇒ CHECK của CSDL bắn trước mã lỗi nghiệp vụ (coding-guide §4).
 *
 * <p>Bảng 4/5 (nhập tay) ở {@link SoLieuNhapTayService}; vị trí gắn công trình + Bảng 3 ở
 * {@link CauHinhBaoCaoNhanhService}. Lớp này giữ vòng đời kỳ và gác {@code OPS-2029} cho MỌI lượt ghi.
 */
@Service
public class BaoCaoNhanhService {

    /** Công ty thuỷ lợi mà hệ này nhập được — ba công ty còn lại ngoài phạm vi (OI-BC1). */
    public static final String CONG_TY = "SONG_NHUE";

    /** Một ô nhập của Bảng 2 — {@code soMayVanHanh = null} xoá số đã nhập (về "chưa nhập"). */
    public record NhapVanHanh(UUID nhomMayPublicId, Integer soMayVanHanh) {}

    /** Toàn bộ nội dung một kỳ — số do BE tính, FE và bản Word chỉ hiển thị (quy tắc 3). */
    public record ChiTiet(
            BaoCaoNhanh baoCao,
            List<AllowedAction> hanhDong,
            BangCoMayBom bangCo,
            List<TinhBaoCaoNhanh.KhoiBang2> bang2,
            Map<Long, String> tenDonVi,
            TinhBaoCaoNhanh.DongBang1 bang1SongNhue,
            TinhBaoCaoNhanh.Muc1 muc1,
            TinhBaoCaoNhanh.GhiChuYenNghia ghiChuYenNghia,
            List<CauHinhBaoCaoNhanhService.DongBang3> bang3,
            List<SoLieuNhapTayService.DongLuongMua> bang4,
            List<TinhBaoCaoNhanh.DongBang5> bang5,
            TinhBaoCaoNhanh.ChinO bang5CongTy) {}

    private final BaoCaoNhanhRepository baoCao;
    private final BaoCaoNhanhVanHanhRepository vanHanh;
    private final BaoCaoNhanhQuery query;
    private final DanhMucMayBomService danhMuc;
    private final WorkflowPort workflow;
    private final OrgUnitPort orgUnits;
    private final SoLieuNhapTayService nhapTay;
    private final CauHinhBaoCaoNhanhService cauHinh;

    public BaoCaoNhanhService(
            BaoCaoNhanhRepository baoCao,
            BaoCaoNhanhVanHanhRepository vanHanh,
            BaoCaoNhanhQuery query,
            DanhMucMayBomService danhMuc,
            WorkflowPort workflow,
            OrgUnitPort orgUnits,
            SoLieuNhapTayService nhapTay,
            CauHinhBaoCaoNhanhService cauHinh) {
        this.baoCao = baoCao;
        this.vanHanh = vanHanh;
        this.query = query;
        this.danhMuc = danhMuc;
        this.workflow = workflow;
        this.orgUnits = orgUnits;
        this.nhapTay = nhapTay;
        this.cauHinh = cauHinh;
    }

    // ==== Kỳ báo cáo ========================================================

    @Transactional(readOnly = true)
    public Page<BaoCaoNhanh> danhSach(Pageable pageable) {
        return baoCao.findByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public BaoCaoNhanh get(UUID publicId) {
        return tim(publicId);
    }

    /**
     * ⛔ KHÔNG {@code @Transactional}: mọi hàm trong lớp gọi hàm này, ⛔ gọi {@link #get} — tự gọi một
     * hàm {@code @Transactional} của chính lớp mình đi vòng qua proxy (SilentFailureRuleTest).
     */
    private BaoCaoNhanh tim(UUID publicId) {
        return baoCao.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional
    public BaoCaoNhanh tao(Instant tu, Instant den) {
        kiemKhung(tu, den);
        return baoCao.save(new BaoCaoNhanh(tu, den, workflow.initialState(BaoCaoNhanh.ENTITY_TYPE)));
    }

    @Transactional
    public BaoCaoNhanh suaKhung(UUID publicId, Instant tu, Instant den) {
        BaoCaoNhanh bc = choPhepSua(publicId);
        kiemKhung(tu, den);
        bc.datKhung(tu, den);
        return baoCao.save(bc);
    }

    // ==== Nhập liệu =========================================================

    /**
     * Ghi số máy đang chạy — chỉ những ô gửi lên; ô ⛔ gửi giữ nguyên.
     *
     * <p>⛔ {@code OPS-2028} ném TRƯỚC khi tới CHECK của CSDL, gọi đích danh trạm.
     */
    @Transactional
    public void luuVanHanh(UUID publicId, List<NhapVanHanh> nhap) {
        BaoCaoNhanh bc = choPhepSua(publicId);
        Map<UUID, DongNhomMay> nhomSong = danhMuc.danhSachNhom().stream()
                .collect(Collectors.toMap(DongNhomMay::nhomPublicId, Function.identity()));
        Map<Long, BaoCaoNhanhVanHanh> daCo = vanHanh.findByBaoCaoIdAndDeletedAtIsNull(bc.getId()).stream()
                .collect(Collectors.toMap(BaoCaoNhanhVanHanh::getNhomMayId, Function.identity()));

        for (NhapVanHanh o : nhap) {
            DongNhomMay nhom = nhomSong.get(o.nhomMayPublicId());
            if (nhom == null) {
                throw new ValidationException(ErrorCode.SYS_0003)
                        .withDetail("nhomMayPublicId", "NOT_FOUND", o.nhomMayPublicId());
            }
            Integer so = o.soMayVanHanh();
            if (so != null && so < 0) {
                throw new ValidationException(ErrorCode.SYS_0003).withDetail("soMayVanHanh", "MIN", so);
            }
            if (so != null && so > nhom.soMay()) {
                throw new BusinessRuleException(ErrorCode.OPS_2028, nhom.tenCongTrinh(), so, nhom.soMay());
            }
            BaoCaoNhanhVanHanh dong = daCo.computeIfAbsent(nhom.nhomId(), id -> new BaoCaoNhanhVanHanh(bc.getId(), id));
            dong.ghi((short) nhom.soMay(), nhom.qM3h(), so == null ? null : so.shortValue());
            vanHanh.save(dong);
        }
    }

    /** Ghi Bảng 5 — thay TOÀN PHẦN bốn ô của mỗi xã gửi lên. Chỉ xã của Sông Nhuệ. */
    @Transactional
    public void luuNgapUng(UUID publicId, List<SoLieuNhapTayService.NhapNgapUng> nhap) {
        nhapTay.luuNgapUng(choPhepSua(publicId).getId(), nhap);
    }

    /** Ghi Bảng 4 — lượng mưa (mm) của các điểm mưa gửi lên. */
    @Transactional
    public void luuLuongMua(UUID publicId, List<SoLieuNhapTayService.NhapLuongMua> nhap) {
        nhapTay.luuLuongMua(choPhepSua(publicId).getId(), nhap);
    }

    // ==== Chốt / mở lại =====================================================

    /**
     * Chốt kỳ — CHỤP danh mục vào bảng của kỳ rồi chuyển trạng thái qua Workflow engine.
     *
     * <p>⚠ Chụp TRƯỚC {@code execute}: một nhóm máy vừa bị giảm số thiết kế xuống dưới số đã nhập ném
     * {@code OPS-2028} ở đây, ⛔ để CHECK của CSDL bắn giữa lượt ghi của engine.
     */
    @Transactional
    public BaoCaoNhanh chot(UUID publicId) {
        BaoCaoNhanh bc = choPhepSua(publicId);
        Map<Long, BaoCaoNhanhVanHanh> daCo = vanHanh.findByBaoCaoIdAndDeletedAtIsNull(bc.getId()).stream()
                .collect(Collectors.toMap(BaoCaoNhanhVanHanh::getNhomMayId, Function.identity()));
        for (DongNhomMay nhom : danhMuc.danhSachNhom()) {
            BaoCaoNhanhVanHanh dong = daCo.remove(nhom.nhomId());
            Short so = dong == null ? null : dong.getSoMayVanHanh();
            if (so != null && so > nhom.soMay()) {
                throw new BusinessRuleException(ErrorCode.OPS_2028, nhom.tenCongTrinh(), so, nhom.soMay());
            }
            if (dong == null) {
                dong = new BaoCaoNhanhVanHanh(bc.getId(), nhom.nhomId());
            }
            dong.ghi((short) nhom.soMay(), nhom.qM3h(), so);
            vanHanh.save(dong);
        }
        // Nhóm đã bị xoá khỏi danh mục từ lúc nhập ⇒ bỏ khỏi kỳ: văn bản chốt theo danh mục hiện hành.
        daCo.values().forEach(d -> {
            d.markDeleted(com.songnhue.core.common.util.DateTimeUtils.nowUtc());
            vanHanh.save(d);
        });
        cauHinh.chup(bc);
        return workflow.execute(bc, "CHOT", "Chốt Báo cáo nhanh");
    }

    /** Mở lại — {@code requires_reason = TRUE}: thiếu lý do thì engine ném {@code SYS-0003}. */
    @Transactional
    public BaoCaoNhanh moLai(UUID publicId, String lyDo) {
        BaoCaoNhanh bc = tim(publicId);
        return workflow.execute(bc, "MO_LAI", "Mở lại Báo cáo nhanh", lyDo);
    }

    // ==== Dựng nội dung =====================================================

    @Transactional(readOnly = true)
    public ChiTiet chiTiet(UUID publicId) {
        BaoCaoNhanh bc = tim(publicId);
        BangCoMayBom bang = danhMuc.bangCo();

        List<DongVanHanh> dong = bc.daChot() ? query.anhChup(bc.getId()) : dongSong(bc.getId());

        List<OrgUnitTreeRef> cay = orgUnits.cayPhang();
        Map<Long, Integer> thuTu = new HashMap<>();
        Map<Long, String> ten = new HashMap<>();
        for (int i = 0; i < cay.size(); i++) {
            thuTu.put(cay.get(i).donVi().id(), i);
            ten.put(cay.get(i).donVi().id(), cay.get(i).donVi().name());
        }

        TinhBaoCaoNhanh.DongBang1 b1 = TinhBaoCaoNhanh.bang1(dong, bang);
        List<TinhBaoCaoNhanh.DongBang5> b5 = TinhBaoCaoNhanh.bang5(nhapTay.ngapUngCua(bc.getId()));
        CauHinhBaoCaoNhanhService.CauHinhKy ch = cauHinh.cauHinhKy(bc);

        return new ChiTiet(
                bc,
                workflow.allowedActions(bc),
                bang,
                TinhBaoCaoNhanh.bang2(dong, thuTu),
                ten,
                b1,
                TinhBaoCaoNhanh.muc1(b1),
                TinhBaoCaoNhanh.yenNghia(dong, ch.tramYenNghia()),
                cauHinh.bang3(ch, bc.getDenThoiDiem()),
                nhapTay.luongMuaCua(bc.getId()),
                b5,
                TinhBaoCaoNhanh.dongCongTy(b5));
    }

    /** Danh mục SỐNG ⟕ số đã nhập của kỳ. */
    private List<DongVanHanh> dongSong(Long baoCaoId) {
        Map<Long, Short> daNhap = new HashMap<>();
        vanHanh.findByBaoCaoIdAndDeletedAtIsNull(baoCaoId)
                .forEach(v -> daNhap.put(v.getNhomMayId(), v.getSoMayVanHanh()));
        return danhMuc.danhSachNhom().stream()
                .map(n -> {
                    Short so = daNhap.get(n.nhomId());
                    return new DongVanHanh(n, so == null ? null : so.intValue());
                })
                .toList();
    }

    // ==== Nội bộ ============================================================

    private BaoCaoNhanh choPhepSua(UUID publicId) {
        BaoCaoNhanh bc = tim(publicId);
        if (bc.daChot()) {
            throw new BusinessRuleException(ErrorCode.OPS_2029);
        }
        return bc;
    }

    private static void kiemKhung(Instant tu, Instant den) {
        if (tu == null || den == null || !den.isAfter(tu)) {
            throw new BusinessRuleException(ErrorCode.OPS_2030);
        }
    }
}
