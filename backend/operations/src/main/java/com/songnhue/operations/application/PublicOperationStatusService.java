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
    /**
     * Mốc "Cập nhật lúc" của khối vận hành trên cổng — <b>T43.9</b>.
     *
     * @param capNhatLuc thời điểm bản ghi tình hình vận hành <b>mới nhất</b> được ghi xuống, tức
     *     {@code MAX(COALESCE(updated_at, created_at))}. {@code null} ⇔ chưa có dòng nào.
     *     <p>⛔⛔ Đây <b>KHÔNG</b> phải {@code Instant.now()}. Trước T43.9 cổng lấy mốc này từ
     *     {@code GET /public/now} — đồng hồ máy chủ lúc dựng trang — nên khi trực ban ba ngày ⛔
     *     không ghi bản ghi nào, dòng <i>"Cập nhật lúc"</i> vẫn <b>nhảy sang giờ mới mỗi lượt F5</b>.
     *     Một câu khẳng định sai xuất hiện đúng lúc hệ đang ngừng được cập nhật.
     *     <p>⛔ Cũng ⛔ KHÔNG phải {@code MAX(effective_at)}: {@code effective_at} là mốc <i>hiệu
     *     lực nghiệp vụ</i>, ghi lùi hoặc ghi trước đều hợp lệ, nên lấy nó có ngày in ra một mốc
     *     cập nhật <b>ở tương lai</b> — đổi một lời nói dối lấy một lời nói dối khác.
     */
    public record MetaVanHanh(Instant capNhatLuc) {}

    /**
     * Bảng vận hành công bố ra cổng: các dòng <b>và</b> mốc cập nhật của chính chúng.
     *
     * <h2>⛔ Vì sao mốc đi kèm dữ liệu chứ ⛔ không để cổng tự tính</h2>
     *
     * <p>Vì cổng <b>⛔ không tính nổi</b>: {@link OperationStatusRow#updatedAt()} là
     * <b>nullable</b> ({@code updated_at timestamptz} ở {@code V202608221029:30}) và
     * {@code created_at} <b>⛔ không nằm trong DTO công khai</b>. Một bảng toàn dòng vừa tạo, chưa
     * ai sửa, sẽ cho {@code max(updatedAt)} = {@code null} ⇒ cổng in <i>"chưa rõ"</i> trong khi dữ
     * liệu đang có. Phép {@code COALESCE} chỉ thực hiện được ở nơi còn nhìn thấy {@code created_at}
     * (luật 12 — đặt bảo đảm ở chỗ dữ liệu đi qua).
     *
     * <p>Hình dạng cố ý trùng đường thuỷ văn ({@code meta} nằm <b>trong</b> {@code data}, chốt Q5):
     * hai cách trả lời cho cùng một câu hỏi <i>"số liệu này cũ tới đâu"</i> là hai nơi phải nhớ
     * (luật 14).
     */
    public record BangVanHanh(List<OperationStatusRow> dong, MetaVanHanh meta) {

        public BangVanHanh {
            dong = List.copyOf(dong);
            // ⛔ Ép ở HÀM DỰNG (quy tắc 16). Một mốc cập nhật khác null đi cùng bảng RỖNG là câu
            //    "số liệu cập nhật lúc X" đặt trên chỗ ⛔ không có số liệu nào — đúng loại khẳng
            //    định sai mà T43.9 sinh ra để gỡ. Chiều ngược lại cũng vậy: có dòng mà ⛔ không có
            //    mốc nghĩa là phép COALESCE ở dưới đã hụt một nhánh.
            if (dong.isEmpty() != (meta.capNhatLuc() == null)) {
                throw new IllegalArgumentException(
                        "Bảng vận hành: có dòng ⇔ có mốc cập nhật. Đang có %d dòng mà capNhatLuc=%s"
                                .formatted(dong.size(), meta.capNhatLuc()));
            }
        }
    }

    @Transactional(readOnly = true)
    public BangVanHanh hienHanh() {
        List<Construction> congTrinh = constructions.findByDeletedAtIsNull().stream()
                .filter(c -> c.getLifecycleState() != LifecycleState.DA_THANH_LY)
                .sorted(Comparator.comparing(Construction::getName, TIENG_VIET))
                .toList();
        if (congTrinh.isEmpty()) {
            return new BangVanHanh(List.of(), new MetaVanHanh(null));
        }

        Map<Long, OrgUnitRef> donVi = orgUnits.findRefsByIds(congTrinh.stream()
                .map(Construction::getOrgUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        // ⚠ MỘT lượt tra cho mỗi công trình, dùng cho CẢ dòng lẫn mốc cập nhật. Gọi
        //   `banGhiMoiNhat` lần thứ hai chỉ để lấy mốc là nhân đôi số truy vấn của khối này —
        //   và nó nằm trên đường dựng TRANG CHỦ (NFR-02, DOD1.17).
        List<ConstructionOperationStatus> banGhi = congTrinh.stream()
                .map(c -> statuses.banGhiMoiNhat(c.getId()))
                .flatMap(Optional::stream)
                .toList();

        Map<Long, Construction> theoId =
                congTrinh.stream().collect(java.util.stream.Collectors.toMap(Construction::getId, c -> c));

        List<OperationStatusRow> dong = banGhi.stream()
                .map(s -> thanhDong(theoId.get(s.getConstructionId()), s, donVi))
                .toList();

        return new BangVanHanh(dong, new MetaVanHanh(mocCapNhat(banGhi)));
    }

    /**
     * {@code MAX(COALESCE(updated_at, created_at))} của các bản ghi đang công bố — <b>T43.9</b>.
     *
     * <p>⛔ {@code COALESCE} ⛔ không phải phòng thân: {@code updated_at} chỉ được đặt khi có lượt
     * <i>sửa</i>, nên một bản ghi vừa tạo và chưa ai đụng tới mang {@code null} ở đó. Bỏ nhánh
     * {@code created_at} là để cả một bảng dữ liệu mới toanh báo <i>"chưa rõ"</i>.
     *
     * <p>⚠ Tính trên <b>chính tập bản ghi đang được công bố</b>, ⛔ không phải {@code MAX} toàn
     * bảng: một bản ghi của công trình đã thanh lý hoặc đã xoá mềm ⛔ không được nói hộ rằng bảng
     * này vừa được cập nhật.
     *
     * @return {@code null} ⇔ danh sách rỗng — bất biến của {@link BangVanHanh} neo vào đúng điều này
     */
    private static Instant mocCapNhat(List<ConstructionOperationStatus> banGhi) {
        return banGhi.stream()
                .map(s -> s.getUpdatedAt() == null ? s.getCreatedAt() : s.getUpdatedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
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
