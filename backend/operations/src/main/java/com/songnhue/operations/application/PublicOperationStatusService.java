package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Tình hình vận hành <b>hiện hành</b> của các cống, công bố ra cổng — CN-02.11, khối §5.3.
 *
 * <h2>Khối này là gì và KHÔNG phải gì</h2>
 *
 * <p>Đây là dữ liệu <b>nhập tay</b> của trực ban (chốt G4): mỗi bản ghi mang một mã tình hình vận
 * hành (MT · ĐK · ĐTTL · ĐTHL…) kèm tối đa một giá trị tham số. Nó <b>không</b> phải câu trả lời cho
 * §5.3 của văn bản nghiệm thu — mục ấy đòi bốn trường cùng lúc theo ngày (trạng thái trạm, số máy
 * đang chạy, lưu lượng) và cần một API nguồn chưa tồn tại (<b>OI-02 còn mở</b>). Trả lời đúng cho
 * phần ấy vẫn là "chưa có nguồn"; khối trên cổng đổ được dòng thật từ đây là phần <i>đã</i> có dữ
 * liệu, không phải toàn bộ mục.
 *
 * <h2>⛔ Không có ai đăng nhập, nên mọi phép lọc phải nằm trong lớp này</h2>
 *
 * <p>{@code ScopeFilterAspect} chỉ bật bộ lọc phạm vi đơn vị khi có {@code AuthContext}. Ở đường
 * công khai không có, nên truy vấn nhìn thấy bản ghi của <b>mọi</b> Xí nghiệp — y như
 * {@link PublicConstructionCatalogService}. Đó là điều được yêu cầu, nhưng nó có nghĩa là mỗi cột
 * không công bố phải bị loại <b>ngay ở đây</b>, không phải ở giao diện.
 *
 * <h2>Sáu cột, và hai trường cố ý bị bỏ lại</h2>
 *
 * <p>Công bố đúng sáu cột đã chốt ở {@code homeDataColumns.ts}: Công trình · Xí nghiệp quản lý ·
 * Mã tình hình vận hành · Giá trị tham số · Thời điểm hiệu lực · Cập nhật lần cuối.
 *
 * <p>⛔ <b>{@code note} và người cập nhật KHÔNG ra cổng.</b> Hai trường ấy có trong bảng nhưng là
 * ghi chú nội bộ giữa các ca trực và danh tính cán bộ; công bố chúng là một quyết định về phạm vi
 * công bố chứ không phải một cột thêm vào DTO. Không cho chúng một chỗ ngồi trong record là cách
 * chắc chắn nhất — một cột không tồn tại thì không ai vô tình đấu dây cho nó.
 *
 * <h2>⚠ Vì sao lớp này gọi hai truy vấn có sẵn thay vì một câu gộp</h2>
 *
 * <p>"Hiện hành" ở đây phải trùng khít định nghĩa mà mắt xích 4 của
 * {@code ConstructionStatusService} đang dùng, nếu không cổng nói cống mở treo trong khi dashboard
 * nội bộ nói đóng kín — và không gì báo sai. Cách chắc chắn nhất để hai nơi không lệch là
 * <b>gọi đúng một hàm</b>: {@link ConstructionOperationStatusRepository#banGhiMoiNhat}, câu native
 * đã chạy thật từ WS-19, thay vì viết một câu gộp thứ hai nói cùng một điều bằng SQL khác.
 *
 * <p>Cái giá là N+1 truy vấn. Chấp nhận được, và có số: danh mục công trình dự kiến vài chục dòng,
 * đường này nằm sau ISR 5 phút của cổng, và ưu tiên xuyên suốt của dự án là <i>độ chính xác trước
 * tối ưu</i>. ⬜ Khi danh mục vượt ~200 công trình thì đổi sang một câu {@code DISTINCT ON
 * (construction_id)} — và lúc ấy phải đổi <b>cả</b> {@code banGhiMoiNhat} để hai nơi vẫn nói một
 * điều.
 */
@Service
public class PublicOperationStatusService {

    /** Sắp tên công trình theo tiếng Việt — cùng lý do đã ghi ở {@link PublicConstructionCatalogService}. */
    private static final Collator TIENG_VIET = Collator.getInstance(java.util.Locale.of("vi", "VN"));

    private final ConstructionRepository constructions;
    private final ConstructionOperationStatusRepository statuses;
    private final OrgUnitPort orgUnits;

    public PublicOperationStatusService(
            ConstructionRepository constructions,
            ConstructionOperationStatusRepository statuses,
            OrgUnitPort orgUnits) {
        this.constructions = constructions;
        this.statuses = statuses;
        this.orgUnits = orgUnits;
    }

    /**
     * Một dòng của khối "Tình hình vận hành công trình" trên cổng.
     *
     * @param parameterValue {@code null} khi mã không mang tham số (VD "Đóng kín") — cổng hiện dấu
     *     gạch. ⛔ Không quy về {@code 0}: quy tắc 16, <i>số 0 là một câu khẳng định</i>, và trên một
     *     bảng mực nước thì "điều tiết 0,00 m" khác hẳn "mã này không có tham số"
     * @param statusColor màu badge do Công ty tự đặt trong danh mục mã (CRUD đầy đủ, chốt G4). Trả
     *     mã màu ra để cổng khỏi giữ một bảng ánh xạ thứ hai — thêm mã mới không được đòi deploy
     * @param unitName {@code null} khi công trình chưa gán đơn vị hoặc đơn vị đã rời sơ đồ tổ chức;
     *     cổng hiện "Chưa phân đơn vị quản lý" chứ không giấu cả dòng đi
     */
    public record OperationStatusRow(
            String constructionCode,
            String constructionName,
            String unitName,
            String statusCode,
            String statusName,
            String statusColor,
            /**
             * ⚠⚠ Ra dây dưới dạng <b>CHUỖI</b>, và đó là điều kiện để quy tắc 2 còn nghĩa.
             *
             * <p>Mặc định Jackson viết {@code BigDecimal} thành số JSON — giữ đúng {@code 2.30} trên
             * dây (đã đo bằng {@code PublicConstructionPortalHttpTest}). Nhưng
             * {@code JSON.parse("2.30")} ở trình duyệt cho ra {@code 2.3}: số của JavaScript là
             * {@code double}, không mang thang đo. Mực nước <b>2,30 m</b> hiện thành <b>2,3 m</b> trên
             * cổng công khai — mất đúng thứ mà "cấm float/double cho mọi số đo" sinh ra để giữ, và
             * mất ở chặng cuối cùng nơi không ai còn nhìn.
             *
             * <p>{@code lib/api.ts} đã khai {@code parameterValue: string | null} kèm nguyên văn lý do
             * ấy từ trước. Khai kiểu là một <i>lời khẳng định</i>, không phải một phép đo (T27.22) —
             * annotation này là thứ làm cho lời khẳng định đó thành đúng.
             */
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal parameterValue,
            String parameterUnit,
            OffsetDateTime effectiveAt,
            Instant updatedAt) {}

    /**
     * Tình hình vận hành hiện hành của mọi công trình đang được công bố.
     *
     * <p>Bộ lọc công trình trùng khít {@link PublicConstructionCatalogService#catalogByUnit()}: bỏ
     * hồ sơ đã xoá mềm, bỏ công trình {@link LifecycleState#DA_THANH_LY}. Công trình chưa có bản ghi
     * tình hình vận hành nào <b>không xuất hiện</b> — nó là "chưa bắt đầu ghi nhận", không phải "mã
     * rỗng", và một dòng toàn dấu gạch trên cổng trông y hệt một dòng có dữ liệu bị mất.
     *
     * @return rỗng khi chưa công trình nào được ghi nhận. Rỗng là câu trả lời đúng ở thời điểm này
     *     (danh mục công trình thuộc <b>G8</b>) — cổng phải nói thẳng, ⛔ không dựng sẵn một lưới
     *     mười cống với dấu gạch cho có (§10.54, §10.61 mục 6)
     */
    @Transactional(readOnly = true)
    public List<OperationStatusRow> hienHanh() {
        List<Construction> congTrinh = constructions.findByDeletedAtIsNull().stream()
                .filter(c -> c.getLifecycleState() != LifecycleState.DA_THANH_LY)
                .sorted(Comparator.comparing(Construction::getName, TIENG_VIET))
                .toList();
        if (congTrinh.isEmpty()) {
            return List.of();
        }

        Map<Long, OrgUnitRef> donVi = orgUnits.findRefsByIds(congTrinh.stream()
                .map(Construction::getOrgUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        return congTrinh.stream()
                .map(c -> statuses.banGhiMoiNhat(c.getId()).map(s -> thanhDong(c, s, donVi)))
                .flatMap(Optional::stream)
                .toList();
    }

    private static OperationStatusRow thanhDong(
            Construction c, ConstructionOperationStatus s, Map<Long, OrgUnitRef> donVi) {
        OperationStatusCode ma = s.getOperationCode();
        OrgUnitRef ref = c.getOrgUnitId() == null ? null : donVi.get(c.getOrgUnitId());
        return new OperationStatusRow(
                c.getCode(),
                c.getName(),
                ref == null ? null : ref.name(),
                ma.getCode(),
                ma.getName(),
                ma.getColorHex(),
                // ⛔ Đơn vị chỉ đi kèm khi mã THẬT SỰ mang tham số. Trả "m" cho một mã không có
                //    tham số là mời giao diện in "— m".
                ma.isHasParameter() ? s.getParameterValue() : null,
                ma.isHasParameter() ? ma.getParameterUnit() : null,
                s.getEffectiveAt(),
                s.getUpdatedAt());
    }
}
