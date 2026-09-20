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
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.CoMayBom;
import com.songnhue.operations.domain.DongNhomMay;
import com.songnhue.operations.domain.NhomMayBom;
import com.songnhue.operations.infra.CoMayBomRepository;
import com.songnhue.operations.infra.DanhMucMayBomQuery;
import com.songnhue.operations.infra.NhomMayBomRepository;

/**
 * Danh mục máy bơm của Báo cáo nhanh — 9 cỡ máy + nhóm máy từng trạm.
 *
 * <p>Thêm/sửa nhóm máy đi qua đường NHẬP TỆP ({@code TramBomImportService}); ở đây chỉ có đọc,
 * sửa biên cỡ máy và xoá một nhóm.
 */
@Service
public class DanhMucMayBomService {

    /** Biên mới của một cỡ — {@code null} = vô cực (chỉ hợp lệ ở cỡ đầu/cuối). */
    public record BienMoi(UUID publicId, BigDecimal tu, BigDecimal den) {}

    private final CoMayBomRepository coMay;
    private final NhomMayBomRepository nhomMay;
    private final DanhMucMayBomQuery query;

    public DanhMucMayBomService(CoMayBomRepository coMay, NhomMayBomRepository nhomMay, DanhMucMayBomQuery query) {
        this.coMay = coMay;
        this.nhomMay = nhomMay;
        this.query = query;
    }

    @Transactional(readOnly = true)
    public List<CoMayBom> danhSachCo() {
        return coMay.findByDeletedAtIsNullOrderBySortOrderAsc();
    }

    /** Bảng cỡ đã kiểm biên — nguồn duy nhất cho mọi phép xếp Q. */
    @Transactional(readOnly = true)
    public BangCoMayBom bangCo() {
        return BangCoMayBom.of(danhSachCo().stream().map(CoMayBom::toCo).toList());
    }

    @Transactional(readOnly = true)
    public List<DongNhomMay> danhSachNhom() {
        return query.tatCa();
    }

    /**
     * Sửa biên — nhận <b>ĐỦ 9 cỡ một lượt</b>.
     *
     * <p>⛔ Sửa từng cỡ một thì giữa hai lượt gọi luôn có một trạng thái có khe hoặc chồng, và
     * {@link BangCoMayBom#of} sẽ (đúng) từ chối lượt đầu. Nhận cả bảng thì kiểm một lần trên trạng thái
     * CUỐI. Thêm vế: mọi nhóm máy đang có phải còn xếp được — ⛔ để một lượt sửa biên làm mồ côi một Q
     * rồi mới đỏ lúc lập báo cáo.
     */
    @Transactional
    public List<CoMayBom> suaBien(List<BienMoi> bienMoi) {
        List<CoMayBom> hienCo = danhSachCo();
        Map<UUID, BienMoi> theoId =
                bienMoi.stream().collect(Collectors.toMap(BienMoi::publicId, Function.identity(), (a, b) -> a));
        if (theoId.size() != hienCo.size() || !hienCo.stream().allMatch(c -> theoId.containsKey(c.getPublicId()))) {
            throw new BusinessRuleException(
                    ErrorCode.OPS_2031, "phải gửi đủ %d cỡ máy, đang gửi %d".formatted(hienCo.size(), theoId.size()));
        }
        for (CoMayBom c : hienCo) {
            BienMoi b = theoId.get(c.getPublicId());
            c.datBien(b.tu(), b.den());
        }
        BangCoMayBom bang = BangCoMayBom.of(hienCo.stream().map(CoMayBom::toCo).toList());
        for (DongNhomMay n : query.tatCa()) {
            bang.xep(n.qM3h());
        }
        return coMay.saveAll(hienCo);
    }

    @Transactional
    public void xoaNhom(UUID publicId) {
        NhomMayBom n = nhomMay.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        n.markDeleted(DateTimeUtils.nowUtc());
        nhomMay.save(n);
    }
}
