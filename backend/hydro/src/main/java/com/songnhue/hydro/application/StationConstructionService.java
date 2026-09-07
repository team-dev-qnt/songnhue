package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.ConstructionLookupPort;
import com.songnhue.core.spi.ConstructionRef;
import com.songnhue.hydro.domain.PositionRole;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationConstruction;
import com.songnhue.hydro.infra.StationConstructionRepository;
import com.songnhue.hydro.infra.StationRepository;

/**
 * ⭐⭐ Khai liên kết <b>điểm đo ↔ công trình</b> — <b>T28.19, đóng 03/09/2026</b>.
 *
 * <h2>Đây là chỗ hai luồng dữ liệu của hệ thống GẶP NHAU, và nó rỗng suốt từ WS-28</h2>
 *
 * <p>Hệ có hai luồng tách bạch: <b>(A)</b> tình hình vận hành <i>công trình</i> do người trực nhập
 * (CN-02.11) và <b>(B)</b> mực nước do API lấy về, vốn chỉ mang một mã {@code F#####}. Chúng đứng
 * cạnh nhau trên màn hình <b>vì cùng trỏ về một công trình</b>, ⛔ không phải vì được trộn vào một
 * bảng. Bảng {@code station_constructions} là <b>chỗ nối duy nhất</b> ấy.
 *
 * <p>Và cho tới hôm nay nó ⛔ <b>không có một đường ghi nào</b> — đo được bằng bốn phép độc lập:
 * {@code grep "new StationConstruction("} → 0, {@code links.save} → 0, không endpoint nào đụng tới,
 * màn hình chỉ <i>hiển thị</i> số liên kết. Lược đồ, entity, repository, 4 chỉ mục, ràng buộc
 * {@code is_primary} và cả mã lỗi {@code HYD-2005} đều đã có từ 31/08. Luật 27 ở dạng nặng nhất:
 * <b>cả một cơ chế chỉ có nửa đọc</b>, và nửa ấy trông y hệt một cơ chế hoàn chỉnh.
 *
 * <h2>Ba thứ chỉ chạy được sau khi bảng này có dữ liệu</h2>
 *
 * <ol>
 *   <li>{@code HYD-2005} — mã lỗi seed đủ ba tệp mà ⛔ <b>không lượt chạy nào chạm tới được</b>
 *       (luật 7). {@code StationService.kiemVaiTroKhopLienKetChinh} chỉ bắn khi đã có một liên kết
 *       <i>chính</i>, mà chưa ai tạo được liên kết nào.
 *   <li><b>Mắt xích 3</b> của {@code ConstructionStatusService.tinh()} — {@code hasActiveAlert} nhận
 *       {@code constructionId}, còn cảnh báo gắn với {@code station_id}: cầu nối là đúng bảng này.
 *       Thay {@code DummyHydroAlertService} bằng bản thật mà bảng vẫn rỗng thì nó trả {@code false}
 *       y hệt bản Dummy, và sổ được tick (luật 19).
 *   <li><b>Người nhận cảnh báo</b> (chốt G11) — chuỗi
 *       {@code station → station_constructions → constructions.org_unit_id → trưởng/phó đơn vị}.
 * </ol>
 *
 * <h2>⛔ Toàn vẹn do TẦNG NÀY giữ, vì CSDL cố ý không có khoá ngoại</h2>
 *
 * <p>{@code construction_id} ⛔ không {@code REFERENCES} — hai module, §10.4. Đổi lại, mọi lượt tạo
 * liên kết phải đi qua {@link ConstructionLookupPort} để xác nhận công trình <b>có thật và còn
 * sống</b>. Không có bước đó thì cột ấy là <i>"một cột số trỏ vào khoảng không"</i> — nguyên văn lời
 * cảnh báo trong chính migration đã dựng bảng.
 */
@Service
@Transactional(readOnly = true)
public class StationConstructionService {

    private static final Logger log = LoggerFactory.getLogger(StationConstructionService.class);

    private final StationConstructionRepository links;
    private final StationRepository stationRepository;
    private final StationService stations;
    private final ConstructionLookupPort constructions;

    public StationConstructionService(
            StationConstructionRepository links,
            StationRepository stationRepository,
            StationService stations,
            ConstructionLookupPort constructions) {
        this.links = links;
        this.stationRepository = stationRepository;
        this.stations = stations;
        this.constructions = constructions;
    }

    /**
     * Khai một liên kết.
     *
     * <p>Thứ tự kiểm là có chủ ý — mỗi bước loại đúng một loại sai, và bước rẻ nhất đứng trước:
     *
     * <ol>
     *   <li>điểm đo phải nằm trong phạm vi người gọi ({@code AUTH-3002} do {@code ScopeGuard} ném);
     *   <li>công trình phải có thật ({@code SYS-0004}) và chưa thanh lý ({@code OPS-2002});
     *   <li>nếu là liên kết <b>chính</b> thì vai trò phải trùng {@code stations.position_role}
     *       ({@code HYD-2005});
     *   <li>liên kết chính cũ — nếu có — bị hạ xuống <i>phụ</i> trong <b>cùng giao dịch</b>;
     *   <li>trùng cặp (điểm đo, công trình, vai trò) → {@code HYD-2008}.
     * </ol>
     *
     * @param laChinh ⚠ "chính" ⛔ không phải "quan trọng hơn": nó là liên kết mà
     *     {@code stations.position_role} nói về. Một điểm đo có <b>tối đa một</b> (chỉ mục một phần
     *     ép), và nó phải cùng vai trò — nếu không thì biểu tổng hợp xếp điểm đo vào nhầm cột TL/HL
     */
    @Transactional
    public StationConstruction lienKet(
            UUID diemDoPublicId, UUID congTrinhPublicId, PositionRole vaiTro, boolean laChinh) {

        Station diemDo = stations.get(diemDoPublicId);
        if (vaiTro == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("role", "BAT_BUOC", null);
        }

        ConstructionRef congTrinh = constructions
                .timTheoPublicId(congTrinhPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        // ⚠ Mượn mã của MOD-02 có chủ ý: câu chữ của OPS-2002 đúng nguyên văn luật ở đây, và dựng
        //   một mã HYD-xxxx nói cùng một điều là hai câu cho một luật (luật 14). Một công trình đã
        //   thanh lý ⛔ không nhận liên kết mới — số đo gắn vào nó sẽ không bao giờ hiện ở đâu.
        if ("DA_THANH_LY".equals(congTrinh.lifecycleState())) {
            throw new BusinessRuleException(ErrorCode.OPS_2002);
        }

        if (laChinh) {
            // ⭐ Đây là lượt gọi ĐẦU TIÊN trong lịch sử dự án có thể làm HYD-2005 bắn ra thật.
            if (vaiTro != diemDo.getPositionRole()) {
                throw new BusinessRuleException(ErrorCode.HYD_2005);
            }
            haLienKetChinhCu(diemDo.getId());
        }

        StationConstruction lienKet =
                new StationConstruction(diemDo.getId(), congTrinh.id(), congTrinh.publicId(), vaiTro);
        lienKet.setPrimary(laChinh);

        try {
            // ⚠ `saveAndFlush`, ⛔ không `save`: chỉ mục một phần chỉ bắn lúc câu INSERT thật sự
            //   chạm CSDL. Với `save` thì flush rơi vào lúc commit — tức là NGOÀI khối try này —
            //   và người dùng nhận 500 thay vì HYD-2008. Đây đúng hình dạng đã trả giá ở
            //   SoDoNhapTayService: kiểm trước là để nói VÌ SAO, bắt ở đây là để không bao giờ 500.
            StationConstruction daGhi = links.saveAndFlush(lienKet);
            log.info(
                    "Liên kết điểm đo {} ↔ công trình {} (vai trò {}, chính = {})",
                    diemDo.getCode(),
                    congTrinh.code(),
                    vaiTro,
                    laChinh);
            return daGhi;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorCode.HYD_2008, e);
        }
    }

    /** Bỏ một liên kết — xoá mềm, đúng quy tắc 9. */
    @Transactional
    public void boLienKet(UUID lienKetPublicId) {
        StationConstruction lienKet = links.findByPublicIdAndDeletedAtIsNull(lienKetPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        // ⛔ Đi qua StationService.get để lượt xoá cũng chịu phạm vi đơn vị: nếu không thì đường
        //    XOÁ rộng hơn đường TẠO, và tầng 3 phân quyền có một lỗ đúng bằng một endpoint.
        //    ⚠ `findById` một mình KHÔNG đi qua bộ lọc phạm vi (§4.2) — nó chỉ dùng để lấy publicId,
        //    rồi chính `stations.get(publicId)` mới là chốt chặn.
        Station diemDo = stationRepository
                .findById(lienKet.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        stations.get(diemDo.getPublicId());

        lienKet.markDeleted(Instant.now());
        links.save(lienKet);
        log.info("Bỏ liên kết {} của điểm đo #{}", lienKetPublicId, lienKet.getStationId());
    }

    /**
     * Tên và mã công trình cho một nhúm liên kết — ⛔ một lượt truy vấn, không N+1.
     *
     * <p>⚠ Vì sao cần: {@code StationConstructionView} trước đợt này chỉ mang
     * {@code constructionId} (một UUID), nên màn hình <b>chỉ hiện được một chuỗi 36 ký tự</b>. Đó là
     * nửa cặp đọc–ghi thứ hai của cùng cơ chế — dữ liệu có, người đọc không dùng được. T27.24 vừa gỡ
     * đúng lỗi ấy ở một màn hình khác (<i>"⛔ không bắt gõ UUID"</i>).
     *
     * @return khoá là {@code construction_id} nội bộ; công trình đã xoá mềm ⛔ không có mặt — màn
     *     hình phải hiện được "công trình đã xoá" chứ ⛔ không giấu cả dòng liên kết đi
     */
    public Map<Long, ConstructionRef> congTrinhCua(Collection<StationConstruction> lienKet) {
        return constructions.timTheoIds(
                lienKet.stream().map(StationConstruction::getConstructionId).toList());
    }

    public List<StationConstruction> cuaDiemDo(Station diemDo) {
        return links.findByStationIdAndDeletedAtIsNull(diemDo.getId());
    }

    /**
     * Hạ liên kết chính cũ xuống <i>phụ</i>.
     *
     * <p>⚠ Hạ <b>tự động</b> thay vì từ chối, và lý do là hình dạng của ràng buộc: chỉ mục một phần
     * {@code ux_station_constructions_mot_ban_ghi_chinh} sẽ ném {@code DataIntegrityViolation} — tức
     * là người dùng phải tự đoán ra rằng mình cần bỏ tick ở dòng kia trước. Đổi liên kết chính là
     * <b>một</b> thao tác trong đầu người vận hành, nên nó là một giao dịch ở đây.
     *
     * <p>⛔ Nhưng ⛔ không im lặng: lượt hạ được ghi log, và {@code AuditEventListener} bắt lệnh
     * UPDATE nên nó vào chuỗi băm cùng ai bấm và lúc nào.
     */
    private void haLienKetChinhCu(Long stationId) {
        Optional<StationConstruction> chinhCu = links.findByStationIdAndPrimaryTrueAndDeletedAtIsNull(stationId);
        chinhCu.ifPresent(cu -> {
            cu.setPrimary(false);
            links.saveAndFlush(cu);
            log.info("Hạ liên kết chính cũ {} của điểm đo #{} xuống phụ", cu.getPublicId(), stationId);
        });
    }
}
