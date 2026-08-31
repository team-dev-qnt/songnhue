package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Danh mục công trình công bố trên cổng, gom theo Xí nghiệp — CR-27, CR-28.
 *
 * <h2>⛔ Không có ai đăng nhập, nên bộ lọc phạm vi đơn vị KHÔNG bật</h2>
 *
 * <p>{@code ScopeFilterAspect} chỉ bật {@code orgUnitScopeFilter} khi có {@code AuthContext}. Ở
 * đường công khai không có, nên truy vấn dưới đây nhìn thấy công trình của <b>mọi</b> Xí nghiệp.
 * Đó là điều §6 của tài liệu chỉnh sửa 27/08/2026 yêu cầu — "Danh mục công trình · Tất cả người
 * dùng" — nhưng nó có nghĩa là <b>mọi phép lọc phải nằm ngay trong lớp này</b>:
 *
 * <ul>
 *   <li>công trình đã xoá mềm → loại ({@code findByDeletedAtIsNull});
 *   <li>công trình đã {@link LifecycleState#DA_THANH_LY} → loại: đây là danh mục giới thiệu
 *       năng lực, không phải sổ tài sản. {@link LifecycleState#NGUNG_MUA_VU} thì <b>giữ</b> —
 *       công trình ngừng theo mùa vụ vẫn thuộc quản lý và sẽ vận hành lại.
 * </ul>
 *
 * <h2>Vì sao DTO liệt kê từng trường</h2>
 *
 * <p>{@link Construction} có tổng vốn đầu tư, nhà thầu, đơn vị thiết kế, nhật ký trạng thái vận
 * hành. Bảy cột của CR-28 không có thứ nào trong đó. Trả entity — hoặc tái dùng
 * {@code ConstructionDtos.ConstructionRow} của màn hình quản trị — là để mỗi cột mới thêm vào bảng
 * lặng lẽ trở thành thông tin công bố của một doanh nghiệp nhà nước.
 */
@Service
public class PublicConstructionCatalogService {

    /**
     * Bộ so sánh chuỗi theo <b>tiếng Việt</b>, dùng để sắp tên đơn vị và tên công trình.
     *
     * <p>⚠⚠ {@code String.CASE_INSENSITIVE_ORDER} <b>sai</b> ở đây, và sai theo kiểu không ai soi
     * ra khi đọc mã: nó so từng đơn vị mã UTF-16, nên {@code 'Đ'} (U+0110) đứng <i>sau</i>
     * {@code 'N'} (U+004E) — "Ngừng mùa vụ" xếp trước "Đang hoạt động", còn trong tiếng Việt thì Đ
     * nằm ngay sau D. Bản đầu của lớp này dùng đúng bộ so sánh ấy; bài kiểm
     * {@code locVongDoi} là thứ tìm ra.
     *
     * <p>Cùng một sự thật đã trả giá ở tầng CSDL: cluster staging từng chạy {@code collate=en_US}
     * và cho ra {@code Anh < Đăng < Dung < Em}, phải dựng lại cả cluster với ICU {@code vi-VN} mới
     * đúng (T11.3-b). Danh sách trên cổng không đi qua {@code ORDER BY} của Postgres — nó được sắp
     * <i>ở đây</i> — nên chỗ này phải tự mang đúng quy tắc ấy.
     */
    private static final Collator TIENG_VIET = Collator.getInstance(java.util.Locale.of("vi", "VN"));

    /**
     * Loại chủ sở hữu của tệp tài liệu công trình.
     *
     * <p>Truyền xuống {@link AttachmentPort#readForPublic} làm danh sách CHO PHÉP, nên đường này
     * không mở được tệp thuộc loại khác kể cả khi ai đó đoán trúng {@code publicId} — hồ sơ nhân sự
     * (<code>EMPLOYEE</code>) nằm ngoài, và nằm ngoài một cách có kiểm chứng.
     */
    private static final List<String> LOAI_TEP_CONG_TRINH = List.of("CONSTRUCTION");

    private final ConstructionRepository constructions;
    private final OrgUnitPort orgUnits;
    private final AttachmentPort attachments;

    public PublicConstructionCatalogService(
            ConstructionRepository constructions, OrgUnitPort orgUnits, AttachmentPort attachments) {
        this.constructions = constructions;
        this.orgUnits = orgUnits;
        this.attachments = attachments;
    }

    /**
     * Một dòng của bảng 7 cột ở §5.1.
     *
     * @param location cột "Địa điểm" — CR-44 yêu cầu ghi theo địa giới hành chính cấp xã MỚI. Đó là
     *     ràng buộc <b>nhập liệu</b>, không phải ràng buộc mã: lớp này trả nguyên văn thứ đang có
     *     trong {@code constructions.address}. Ghi cứng một phép "chuẩn hoá tên xã" ở đây là đoán
     *     hộ khách một bảng ánh xạ mà không ai duyệt.
     * @param mainSpec cột "Thông tin chủ yếu" — dạng {@code Số máy × Lưu lượng 1 máy bơm}. Trả
     *     {@code null} khi thiếu bất kỳ vế nào; xem {@link #thongTinChuYeu}.
     * @param operatingProcedureFileId cột "Quy trình vận hành" — {@code null} = chưa có tệp, cổng
     *     hiện dấu gạch chứ không dựng liên kết rỗng
     * @param latitude toạ độ để cổng tự dựng liên kết Google Map cho cột "Vị trí". Không có cột
     *     {@code map_url} riêng: hai nguồn toạ độ cùng tồn tại là hai nguồn sẽ lệch (luật 13)
     */
    public record CatalogRow(
            String code,
            String name,
            String constructionType,
            String location,
            String mainSpec,
            UUID operatingProcedureFileId,
            UUID protectionPlanFileId,
            BigDecimal latitude,
            BigDecimal longitude) {}

    /** Một Xí nghiệp kèm danh sách công trình của nó — CR-27 yêu cầu gom theo Xí nghiệp. */
    public record UnitCatalog(String unitCode, String unitName, String unitShortName, List<CatalogRow> constructions) {}

    /**
     * Toàn bộ danh mục, gom theo Xí nghiệp quản lý.
     *
     * @return rỗng khi chưa nhập công trình nào. Danh mục công trình tổng thể thuộc <b>G8</b> —
     *     Công ty chưa gửi — nên rỗng là trạng thái đúng ở thời điểm này, và trang phải nói thẳng
     *     điều đó thay vì bịa ra vài trạm bơm cho bảng có nội dung (§10.54).
     */
    @Transactional(readOnly = true)
    public List<UnitCatalog> catalogByUnit() {
        List<Construction> congTrinh = constructions.findByDeletedAtIsNull().stream()
                .filter(c -> c.getLifecycleState() != LifecycleState.DA_THANH_LY)
                .toList();

        if (congTrinh.isEmpty()) {
            return List.of();
        }

        Map<Long, OrgUnitRef> donVi = orgUnits.findRefsByIds(
                congTrinh.stream().map(Construction::getOrgUnitId).distinct().toList());

        return congTrinh.stream().collect(Collectors.groupingBy(Construction::getOrgUnitId)).entrySet().stream()
                // Đơn vị không tra được ref (đã xoá khỏi sơ đồ tổ chức) vẫn phải hiện, dưới một
                // nhãn nói rõ là chưa phân đơn vị. Bỏ đi thì công trình biến mất khỏi danh mục mà
                // không dòng log nào báo — đúng loại mất mát không ai phát hiện.
                .map(e -> {
                    OrgUnitRef ref = donVi.get(e.getKey());
                    List<CatalogRow> dong = e.getValue().stream()
                            .sorted(Comparator.comparing(Construction::getName, TIENG_VIET))
                            .map(PublicConstructionCatalogService::thanhDong)
                            .toList();
                    return new UnitCatalog(
                            ref == null ? null : ref.code(),
                            ref == null ? "Chưa phân đơn vị quản lý" : ref.name(),
                            ref == null ? null : ref.shortName(),
                            dong);
                })
                .sorted(Comparator.comparing(UnitCatalog::unitName, TIENG_VIET))
                .toList();
    }

    /**
     * Nội dung một tệp tài liệu công trình <b>đã được công bố</b> — CR-28.
     *
     * <h2>Vì sao đường này tồn tại thay vì nới danh sách của cổng</h2>
     *
     * <p>{@code PublicPortalService.LOAI_TEP_CONG_KHAI} cố ý <b>không</b> chứa
     * {@code CONSTRUCTION}, và có bài kiểm đóng đinh điều đó. Nhưng màn hình quản trị lại hứa với
     * người dùng bằng chữ: <i>"Tệp được chọn sẽ hiện thành liên kết tải về trên cổng công khai"</i>,
     * và cổng thật sự dựng liên kết {@code /public/files/{id}} cho hai cột ấy. Hai vế đúng riêng
     * lẻ, ghép lại là <b>404 câm</b>: CSDL nói tệp tồn tại, DTO trả id, đường tải trả 404 — và chưa
     * ai thấy vì bảng công trình đang rỗng (G8). Đúng hình dạng §10.52.
     *
     * <p>Chốt: <b>không nới danh sách của cổng</b> (nới là mở cả kho tài liệu công trình, gồm hồ sơ
     * hoàn công và ảnh hiện trạng), mà mở một đường <b>hẹp</b> ở chính module sở hữu dữ liệu. Hai
     * lớp chặn, cả hai đều phải qua:
     *
     * <ol>
     *   <li>{@code publicId} phải là một trong <b>đúng hai</b> cột công bố của một công trình còn
     *       sống, chưa thanh lý — {@link ConstructionRepository#daCongBoTaiLieu};
     *   <li>tệp phải thuộc loại {@code CONSTRUCTION} — kiểm ở tầng đính kèm của Core.
     * </ol>
     *
     * @return rỗng khi không tồn tại, khi chưa được công bố, <b>hoặc</b> khi thuộc loại khác — cố ý
     *     không phân biệt, y như đường tệp của cổng: phân biệt được là nói cho người hỏi biết
     *     {@code publicId} nào có thật
     */
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> publishedDocument(UUID attachmentPublicId) {
        if (attachmentPublicId == null
                || !constructions.daCongBoTaiLieu(attachmentPublicId, LifecycleState.DA_THANH_LY)) {
            return Optional.empty();
        }
        return attachments.readForPublic(attachmentPublicId, LOAI_TEP_CONG_TRINH);
    }

    private static CatalogRow thanhDong(Construction c) {
        return new CatalogRow(
                c.getCode(),
                c.getName(),
                c.getConstructionType().name(),
                c.getAddress(),
                thongTinChuYeu(c),
                c.getOperatingProcedureAttachmentPublicId(),
                c.getProtectionPlanAttachmentPublicId(),
                c.getLatitude(),
                c.getLongitude());
    }

    /**
     * Cột "Thông tin chủ yếu" — {@code Số máy × Lưu lượng 1 máy bơm} (§5.1).
     *
     * <p>⛔ Trả {@code null} khi thiếu <b>bất kỳ</b> vế nào, thay vì ghép một nửa hoặc điền số 0.
     * Quy tắc 16: <i>số 0 là một câu khẳng định</i> — "0 máy" và "chưa nhập số máy" là hai chuyện
     * khác hẳn nhau trên hồ sơ một trạm bơm, còn "4 máy × " thì vừa sai vừa trông như đã có dữ
     * liệu. Ràng buộc ép ở đây, tại chỗ dữ liệu đi qua, chứ không ở nơi hiển thị (quy tắc 12).
     */
    static String thongTinChuYeu(Construction c) {
        Short soMay = c.getPumpCount();
        BigDecimal luuLuong = c.getFlowPerPumpM3s();
        if (soMay == null || luuLuong == null) {
            return null;
        }
        return "%d máy × %s m³/s".formatted(soMay, luuLuong.stripTrailingZeros().toPlainString());
    }
}
