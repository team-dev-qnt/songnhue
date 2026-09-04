package com.songnhue.operations.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.operations.api.dto.OperationStatusBatchCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusBatchItemRequest;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

/**
 * Ghi nhận tình hình vận hành cống — CN-02.11, WS-19.
 *
 * <h2>Đường ghi này từng đứng ngoài cả ba tầng phân quyền</h2>
 *
 * <p>Tầng 1 (giao diện) và tầng 2 ({@code @RequirePermission}) vẫn có, nhưng tầng 3 — phạm vi đơn vị
 * — thì không. Chi tiết cách nó lọt nằm ở {@link OperationStatusBatchItemRequest}. Bản vá không thêm
 * một phép kiểm mới mà <b>đi lại đúng con đường mà mọi đường ghi khác của MOD-02 đã đi</b>:
 * {@link ConstructionService#get(java.util.UUID)} → {@code ScopeGuard.require}.
 *
 * <p>Chọn cách đó thay vì gọi {@code scopeGuard.require} tại chỗ là có chủ ý. Gọi tại chỗ thì lớp này
 * phải tự nhớ ba điều kiện — {@code public_id}, {@code deleted_at IS NULL}, và bọc bằng
 * {@code ScopeGuard} — mà đó chính là kiểu "người viết phải nhớ" mà quy tắc 5 cấm. Đi qua
 * {@code ConstructionService} thì ba điều kiện nằm ở một chỗ duy nhất trong toàn module.
 *
 * <h2>Cả lô hoặc không dòng nào</h2>
 *
 * <p>{@link #batchCreate} là một giao dịch. Một dòng sai mã thì cả lô bị huỷ — cùng nguyên tắc với
 * {@code OPS-2016} ở đường nhập tệp. Với màn hình trực ban nhập một lượt vài chục cống, ghi được nửa
 * lô rồi báo lỗi là tệ hơn: không ai biết nửa nào đã vào.
 */
@Service
public class ConstructionOperationStatusService {

    /** Whitelist sắp xếp — {@code PageUtils} từ chối mọi cột ngoài tập này ({@code §2.5}). */
    private static final Set<String> SAP_XEP_CHO_PHEP = Set.of("effectiveAt", "createdAt");

    /**
     * ⭐ Dung sai cận trên của {@code effective_at} — V3, 02/09/2026.
     *
     * <p><b>Vì sao ⛔ không dùng {@code @PastOrPresent} trên DTO.</b> Mốc này do <i>đồng hồ trình
     * duyệt</i> sinh ra ({@code StatusBatchUpdateModal} khởi tạo bằng {@code dayjs()}), rồi được
     * đối chiếu với <i>đồng hồ máy chủ</i>. Hai đồng hồ ấy luôn lệch nhau vài giây, nên một cận
     * trên đúng bằng "bây giờ" sẽ từ chối những lượt nhập hoàn toàn hợp lệ, ngẫu nhiên, và chỉ ở
     * một số máy — đúng loại lỗi không tái hiện được. Năm phút đủ nuốt mọi lệch đồng hồ thực tế mà
     * vẫn chặn được thứ V3 nói tới: một dòng đề <i>ngày mai</i>.
     *
     * <p>⚠ Cùng con số và cùng lý do với {@code SoDoNhapTayService.kiemMocDo} của MOD-03 — hai
     * module không chia sẻ hằng số, nên chép thì phải chép cả lý do, ⛔ không chỉ chép con số.
     */
    private static final long DUNG_SAI_DONG_HO_GIAY = 300;

    private final ConstructionOperationStatusRepository repository;
    private final OperationStatusCodeRepository codeRepository;
    private final ConstructionService constructions;
    private final ConstructionStatusService statusService;

    /**
     * ⚠ Thêm 01/09/2026 — đường ghi này <b>chạm cổng công khai</b> kể từ T27.16/T27.17.
     *
     * <p>Trước đó bảng {@code construction_operation_status} chỉ hiện trong màn hình quản trị, nên
     * không xoá đệm cổng cũng không ai thấy. Từ 31/08 bản ghi mới nhất đi thẳng ra khối "Vận hành
     * công trình" trên trang chủ và trang Vận hành công trình — mà đường ghi vẫn không báo cho
     * {@code PortalCache}. Hệ quả đúng bằng §10.62: <b>trực ban bấm Lưu, cổng vẫn hiện mã cũ tới 5
     * phút</b>, tức là đúng cái nợ T25.22 mà T27.7 vừa đi trả, tái phát ở một đường ghi khác.
     */
    private final PortalCachePort portalCache;

    public ConstructionOperationStatusService(
            ConstructionOperationStatusRepository repository,
            OperationStatusCodeRepository codeRepository,
            ConstructionService constructions,
            ConstructionStatusService statusService,
            PortalCachePort portalCache) {
        this.repository = repository;
        this.codeRepository = codeRepository;
        this.constructions = constructions;
        this.statusService = statusService;
        this.portalCache = portalCache;
    }

    public static Set<String> allowedSortFields() {
        return SAP_XEP_CHO_PHEP;
    }

    /** Danh mục mã đang dùng — cho màn hình nhập, không đòi quyền quản trị danh mục. */
    @Transactional(readOnly = true)
    public List<OperationStatusCode> activeCodes() {
        return codeRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCodeAsc();
    }

    /**
     * Lịch sử của một công trình, mới nhất trước.
     *
     * <p>⚠ Ở đây câu tra <b>có</b> đi qua bộ lọc phạm vi, và đó là chủ ý — ngược với
     * {@link ConstructionStatusService#tinh}. Khác biệt nằm ở câu hỏi đang được trả lời: đây là *"cho
     * tôi xem những dòng tôi được xem"*, còn bên kia là *"công trình này đang ở trạng thái nào"* —
     * một sự thật về công trình, không phụ thuộc người đang nhìn.
     */
    @Transactional(readOnly = true)
    public Page<ConstructionOperationStatus> lichSu(UUID constructionPublicId, Pageable pageable) {
        Construction construction = constructions.get(constructionPublicId);
        return repository.lichSu(construction.getId(), pageable);
    }

    /**
     * Ghi cả lô — <b>kiểm hết trước, ghi sau</b>.
     *
     * <h2>Vì sao hai pha thay vì ghi dần rồi để giao dịch cuộn lại</h2>
     *
     * <p>Giao dịch bảo đảm "không dòng nào được ghi", nhưng nó dừng ở <b>dòng lỗi đầu tiên</b>. Với
     * màn hình trực ban nhập một lượt vài chục cống, điều đó nghĩa là sửa một dòng, gửi lại, lại
     * hỏng ở dòng khác — mỗi vòng một lỗi. Pha kiểm chạy hết mọi dòng rồi trả về <b>toàn bộ</b> lỗi
     * kèm số thứ tự dòng, để người dùng sửa một lượt.
     *
     * <p>⚠ <b>Lỗi phạm vi đơn vị KHÔNG bị gom.</b> {@link ConstructionService#get} ném
     * {@code AUTH-3002} và {@code ScopeGuard} ghi một sự kiện an ninh — gom nó vào danh sách "dòng
     * lỗi" là biến một tín hiệu an ninh thành một lời nhắc nhập liệu, và làm mất luôn mã 403 mà
     * giao diện dùng để đưa người dùng sang trang không-có-quyền.
     */
    @Transactional
    public void batchCreate(OperationStatusBatchCreateRequest request) {
        List<OperationStatusBatchItemRequest> items = request.getItems();
        List<DongDaKiem> hopLe = new ArrayList<>(items.size());
        ValidationException loi = new ValidationException(ErrorCode.OPS_2019);
        boolean coLoi = false;

        for (int i = 0; i < items.size(); i++) {
            OperationStatusBatchItemRequest item = items.get(i);

            // ⛔ Đi qua ConstructionService, KHÔNG dùng constructionRepository.findById: xem javadoc
            //    lớp. Ném thẳng ra ngoài, không gom — xem javadoc hàm này.
            Construction construction = constructions.get(item.getConstructionPublicId());

            String viPham = kiemMotDong(construction, item);
            if (viPham != null) {
                coLoi = true;
                loi.withDetail("items[" + i + "]", viPham, item.getOperationCode());
                continue;
            }
            hopLe.add(new DongDaKiem(
                    construction,
                    codeRepository
                            .findByCodeAndDeletedAtIsNull(item.getOperationCode())
                            .orElseThrow(),
                    item));
        }

        if (coLoi) {
            throw loi;
        }
        hopLe.forEach(this::ghi);

        // ⚠ Một lần cho cả lô, SAU khi mọi dòng đã ghi — không phải trong `ghi()`. Nhập nhanh 20
        // cống là một thao tác của người dùng, không phải 20; đặt trong vòng lặp thì hàng đợi nhận
        // 20 việc dựng lại cùng một trang (dedup gộp lại, nhưng đó là may chứ không phải thiết kế).
        if (!hopLe.isEmpty()) {
            portalCache.constructionsChanged();
        }
    }

    /**
     * Kiểm một dòng, trả về <b>mã lỗi</b> hoặc {@code null} nếu hợp lệ.
     *
     * <p>Trả mã lỗi chứ không ném: nơi gọi cần chạy tiếp để gom cho đủ các dòng còn lại.
     */
    private String kiemMotDong(Construction construction, OperationStatusBatchItemRequest item) {
        // Công trình đã thanh lý không có "tình hình vận hành" nào để ghi — cùng luật với OPS-2002
        // ở lịch sử sửa chữa. Không có nhánh này thì hồ sơ đã đóng vẫn nhận được dòng mới, và
        // ConstructionStatusService sẽ đọc nó ở mắt xích 4 rồi bỏ qua vì mắt xích 0 chặn trước —
        // nghĩa là dữ liệu vào được nhưng vĩnh viễn không có tác dụng gì.
        if (construction.getLifecycleState() == LifecycleState.DA_THANH_LY) {
            return ErrorCode.OPS_2002.code();
        }

        OperationStatusCode code = codeRepository
                .findByCodeAndDeletedAtIsNull(item.getOperationCode())
                .orElse(null);
        if (code == null) {
            return ErrorCode.SYS_0004.code();
        }

        // Mã đã ẩn thì không nhận dòng mới. OPS-2007 chỉ cho phép ẩn chứ không cho xoá mã đang được
        // dùng — nếu ẩn rồi mà vẫn ghi được thì "ẩn" không có nghĩa gì.
        if (!code.isActive()) {
            return ErrorCode.OPS_2018.code();
        }

        if (code.isHasParameter() != (item.getParameterValue() != null)) {
            return ErrorCode.OPS_2006.code();
        }

        // ⭐ V3 — cận trên của `effective_at`. Cận DƯỚI cố ý không có: bù nhật ký cho quãng đã qua
        //   là việc thật của trực ban, và đã có bài kiểm giữ chiều lùi ngày mở.
        if (item.getEffectiveAt().toInstant().isAfter(Instant.now().plusSeconds(DUNG_SAI_DONG_HO_GIAY))) {
            return ErrorCode.OPS_2020.code();
        }
        return null;
    }

    private void ghi(DongDaKiem dong) {
        ConstructionOperationStatus status = new ConstructionOperationStatus();
        status.setConstructionId(dong.construction().getId());
        status.setOrgUnitId(dong.construction().getOrgUnitId());
        status.setOperationCode(dong.code());
        status.setParameterValue(dong.item().getParameterValue());
        status.setNote(dong.item().getNote());
        status.setEffectiveAt(dong.item().getEffectiveAt());

        repository.save(status);

        // Tính lại trạng thái công trình ngay lập tức
        statusService.recompute(dong.construction());
    }

    /** Một dòng đã qua pha kiểm — giữ sẵn entity để pha ghi không phải tra lại. */
    private record DongDaKiem(
            Construction construction, OperationStatusCode code, OperationStatusBatchItemRequest item) {}
}
