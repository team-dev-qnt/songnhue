package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.WorkflowPort;
import com.songnhue.hydro.domain.ChanDoanChatLuong;
import com.songnhue.hydro.domain.HydroReading;
import com.songnhue.hydro.domain.LyDoNghiNgo;
import com.songnhue.hydro.domain.MeasurementType;
import com.songnhue.hydro.domain.PhanLoaiChatLuong;
import com.songnhue.hydro.domain.QuyTacNghiNgo;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.SoDoNghiNgo;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.infra.HydroLatestRecomputer;
import com.songnhue.hydro.infra.HydroReadingRepository;
import com.songnhue.hydro.infra.MeasurementTypeRepository;
import com.songnhue.hydro.infra.StationRepository;
import com.songnhue.hydro.infra.SuspectReadingRepository;

/**
 * Màn hình <b>Dữ liệu nghi ngờ</b> và hai bước chuyển của nó — T32.5 · T32.6 · T32.8.
 *
 * <h2>⭐⭐ Thứ tự bắt buộc: KIỂM QUY TẮC → {@code execute} → DỰNG LẠI {@code hydro_latest}</h2>
 *
 * <p>Cả ba bước đều có lý do nằm ở một sự cố đã xảy ra:
 *
 * <ol>
 *   <li><b>Kiểm TRƯỚC {@code execute}</b> — §10.34. Kiểm <i>sau</i> thì lượt kiểm ấy
 *       <b>không bao giờ chạy tới</b>: engine đổi trạng thái trên entity đang được quản lý, Hibernate
 *       flush nó xuống CSDL ở cuối giao dịch (hoặc sớm hơn, trước một câu truy vấn), và ràng buộc
 *       {@code CHECK} bắn trước khi dòng kiểm của ta được thực thi. Người dùng nhận một lỗi CSDL thô
 *       thay vì mã lỗi nghiệp vụ.
 *   <li><b>{@code execute}</b> là <b>chốt chặn thật</b> — quyền, tính hợp lệ của bước chuyển, và
 *       "phải nêu lý do" đều ở đó. Lượt kiểm ở bước 1 là <i>bổ sung</i>, ⛔ không thay thế.
 *   <li><b>Dựng lại {@code hydro_latest}</b> — lượt UPSERT của poller chỉ biết tiến, còn một lượt
 *       duyệt đi ngược chiều ấy. Bỏ bước này thì widget cổng và GIS tiếp tục hiện <i>một con số cũ
 *       trông rất bình thường</i>. Xem {@link HydroLatestRecomputer}.
 * </ol>
 *
 * <h2>⚠ Vì sao {@code allowedActions()} chỉ dùng để BIẾT ĐÍCH, ⛔ không dùng để CHẶN</h2>
 *
 * <p>{@code allowedActions()} đã lọc theo quyền. Nếu ta chặn bằng nó thì một người thiếu quyền nhận
 * <i>"hành động không hợp lệ"</i> ({@code SYS-0008}) thay vì <i>"không có quyền"</i>
 * ({@code AUTH-3001}) — thông báo sai hướng, và mất luôn khả năng phân biệt hai tình huống khác hẳn
 * nhau. Chính {@code WorkflowEngine.resolveInitialState} đã ghi bài học ấy vào javadoc của nó.
 *
 * <p>⇒ Ở đây: tra được đích thì kiểm quy tắc; ⛔ <b>tra không được thì không ném</b> — đi tiếp và để
 * engine nói. Lượt kiểm bổ sung ⛔ không bao giờ được nói thay chốt chặn thật.
 */
@Service
public class HydroReviewService {

    private static final Logger log = LoggerFactory.getLogger(HydroReviewService.class);

    private final HydroReadingRepository readings;
    private final SuspectReadingRepository danhSach;
    private final HydroLatestRecomputer latest;
    private final MeasurementTypeRepository loaiChiSo;
    private final StationRepository stations;
    private final HydroSettings settings;
    private final WorkflowPort workflow;

    // CHECKSTYLE.OFF: ParameterNumber - 7 cộng tác viên là số BƯỚC của một lượt duyệt (nạp · liệt kê
    // · dựng lại latest · tra loại chỉ số · tra điểm đo · đọc quy tắc · máy trạng thái). Gom bừa vào
    // một facade chỉ giấu số đó đi mà không giảm một phụ thuộc nào.
    public HydroReviewService(
            HydroReadingRepository readings,
            SuspectReadingRepository danhSach,
            HydroLatestRecomputer latest,
            MeasurementTypeRepository loaiChiSo,
            StationRepository stations,
            HydroSettings settings,
            WorkflowPort workflow) {
        // CHECKSTYLE.ON: ParameterNumber
        this.readings = readings;
        this.danhSach = danhSach;
        this.latest = latest;
        this.loaiChiSo = loaiChiSo;
        this.stations = stations;
        this.settings = settings;
        this.workflow = workflow;
    }

    // ------------------------------------------------------------------ đọc

    /**
     * Hàng chờ duyệt.
     *
     * <p>⚠ Đếm rồi mới lấy trang, và ⛔ bỏ hẳn lượt lấy khi tổng bằng 0: một câu {@code LIMIT/OFFSET}
     * trên bảng phân mảnh lớn nhất hệ thống không đáng chạy để trả về danh sách rỗng.
     */
    @Transactional(readOnly = true)
    public Page<SoDoNghiNgo> hangCho(
            ReadingQuality trangThai, UUID diemDoPublicId, Instant tu, Instant den, Pageable trang) {
        long tong = danhSach.dem(trangThai, diemDoPublicId, tu, den);
        List<SoDoNghiNgo> dong = tong == 0
                ? List.of()
                : danhSach.trang(trangThai, diemDoPublicId, tu, den, trang.getOffset(), trang.getPageSize());
        return new PageImpl<>(dong, trang, tong);
    }

    /**
     * Các nút giao diện được phép hiện cho một bản ghi — đã lọc theo quyền của người đang đăng nhập.
     *
     * <p>⛔ Giao diện ⛔ không tự suy: luật nằm ở {@code workflow_transitions}, và cờ
     * {@code requiresReason} đi cùng đường ấy nên hộp thoại nhập lý do ⛔ không thể lệch với chốt
     * chặn của máy chủ.
     */
    @Transactional(readOnly = true)
    public List<AllowedAction> nutChoPhep(KhoaBanGhi khoa) {
        return workflow.allowedActions(nap(khoa));
    }

    /**
     * ⚠ Câu trả lời cho <i>"bảng rỗng nghĩa là gì"</i> — quy tắc 16.
     *
     * <p>Khi chưa cấu hình quy tắc nghi ngờ (hoặc cấu hình hỏng), bộ phân loại <b>không kiểm gì</b>
     * và hàng chờ luôn rỗng. Một bảng rỗng hiện như <i>"không có gì đáng ngờ"</i> trong khi bộ phân
     * loại đang tắt là một câu khẳng định sai — nên trạng thái ấy phải đi ra tới màn hình.
     *
     * @return {@code empty} khi quy tắc đọc được; ngược lại là câu lỗi để hiện lên
     */
    @Transactional(readOnly = true)
    public TinhTrangQuyTac tinhTrangQuyTac() {
        return new TinhTrangQuyTac(
                settings.quyTacNghiNgo().coKiemGiKhong(),
                settings.loiQuyTacNghiNgo().orElse(null));
    }

    /**
     * @param dangKiem bộ phân loại có thật sự kiểm gì không — {@code false} ⇒ mọi bản ghi mới sẽ là
     *     {@code HOP_LE} và hàng chờ ⛔ không bao giờ có thêm dòng nào
     * @param loiCauHinh {@code null} khi cấu hình đọc được; khác {@code null} nghĩa là JSON hỏng —
     *     ⚠ phân biệt được với "chưa cấu hình", và hai thứ ấy cần hai cách xử lý khác nhau
     */
    public record TinhTrangQuyTac(boolean dangKiem, String loiCauHinh) {}

    // ------------------------------------------------------------------ ghi

    /**
     * Thực hiện một bước chuyển trên bản ghi — {@code DUYET} hoặc {@code XOA}.
     *
     * @param reason bắt buộc với {@code XOA} ({@code requires_reason = TRUE} trong migration); engine
     *     ném {@code SYS-0003} kèm {@code field = "reason"} nếu thiếu
     */
    @Transactional
    public HydroReading xuLy(KhoaBanGhi khoa, String action, String reason) {
        HydroReading banGhi = nap(khoa);

        // ⭐ Bước 1 — tra ĐÍCH ĐẾN để biết phải kiểm gì. ⛔ Không ném khi không tra được: xem javadoc
        //   lớp. Chốt chặn thật là `workflow.execute` ở bước 3.
        dichDen(banGhi, action)
                .filter(toState -> ReadingQuality.HOP_LE.name().equals(toState))
                .ifPresent(toState -> kiemTruocKhiDuyet(banGhi));

        // ⭐ Bước 2 — chốt chặn thật: quyền · bước chuyển hợp lệ · phải nêu lý do · ghi lý do vào
        //   `review_note` · nhật ký kiểm toán (AuditEventListener bắt lệnh UPDATE).
        workflow.execute(banGhi, action, null, reason);

        // ⭐ Bước 3 — dựng lại bảng "hiện tại". ⚠ Sau execute, và trong CÙNG giao dịch: dòng
        //   `hydro_latest` sai là widget cổng hiện một số cũ trông rất bình thường.
        //   ⚠ Hibernate phải flush trước, nếu không câu SQL thuần đọc lại trạng thái CŨ và dựng ra
        //   đúng dòng latest mà ta vừa muốn sửa — một lượt "cập nhật" không đổi gì, im lặng.
        readings.flush();
        boolean conDuLieu = latest.dungLai(banGhi.getStationId(), banGhi.getMeasurementTypeId());

        log.info(
                "Duyệt số đo #{} (điểm đo {}, mốc {}): {} → {}{}",
                banGhi.getId(),
                banGhi.getStationId(),
                banGhi.getMeasuredAt(),
                action,
                banGhi.currentState(),
                conDuLieu ? "" : " — ⚠ cặp (điểm đo × chỉ số) này không còn bản ghi dùng được nào");
        return banGhi;
    }

    /**
     * ⭐⭐ Chốt chặn của bước <b>Duyệt</b> — nối {@code HYD-2001} vào đường chạy thật (T32.8).
     *
     * <p>Chạy lại bộ phân loại trên <b>quy tắc hiện hành</b> và từ chối nếu giá trị vẫn nằm ngoài
     * khoảng vật lý.
     *
     * <h2>⛔ Chỉ chặn {@link LyDoNghiNgo#NGOAI_KHOANG_VAT_LY}, ⛔ KHÔNG chặn nhảy quá nhanh</h2>
     *
     * <p>Hai lý do nghi ngờ đòi hai cách xử lý ngược nhau, và đây là chỗ sự khác biệt ấy thành hành
     * vi:
     *
     * <ul>
     *   <li><b>Nhảy quá nhanh</b> — con số <i>có thể</i> đúng. Mở cống, xả lũ, bơm tiêu đều làm mực
     *       nước đổi rất nhanh trong ít phút, và <b>đó chính là lý do người duyệt tồn tại</b>: máy
     *       không phân biệt được "cảm biến nhiễu" với "vừa mở cống", người trực thì biết. Chặn ở đây
     *       là biến nút Duyệt thành một nút không bao giờ bấm được đúng lúc cần nhất.
     *   <li><b>Ngoài khoảng vật lý</b> — con số <i>không thể</i> đúng ở bất kỳ tình huống vận hành
     *       nào. Duyệt nó lên hợp lệ là đưa một số vô nghĩa vào mọi báo cáo, mọi biểu đồ và mọi phép
     *       so ngưỡng — và từ lúc ấy nó ⛔ không còn dấu hiệu nào để nhận ra.
     * </ul>
     *
     * <p>⚠ Truyền {@code null} cho mốc so sánh là <b>cố ý</b>: hàm này chỉ hỏi câu hỏi <i>tĩnh</i>
     * ("con số này có thể là số thật không"), và câu hỏi <i>động</i> ("nó nhảy nhanh không") đã được
     * người duyệt trả lời bằng chính cú bấm nút.
     */
    private void kiemTruocKhiDuyet(HydroReading banGhi) {
        QuyTacNghiNgo quyTac = loaiChiSo
                .findById(banGhi.getMeasurementTypeId())
                .map(t -> settings.quyTacNghiNgo().cho(t.getCode()))
                .orElse(QuyTacNghiNgo.KHONG_KIEM);

        ChanDoanChatLuong lai =
                PhanLoaiChatLuong.danhGia(banGhi.getReadingValue(), banGhi.getMeasuredAt(), quyTac, null);
        if (lai.lyDo() == LyDoNghiNgo.NGOAI_KHOANG_VAT_LY) {
            // HYD-2001 — "Giá trị đo ngoài khoảng vật lý cho phép". Mã này khai từ Phase 0 và tới
            // 02/09/2026 chưa lượt chạy nào ném nó; đây là đường chạy thật đầu tiên.
            throw new BusinessRuleException(ErrorCode.HYD_2001, lai.moTa());
        }
    }

    /**
     * Đích đến của {@code action} ở trạng thái hiện tại — {@code empty} khi không tra được.
     *
     * <p>⚠ {@code empty} có <b>hai</b> nguyên nhân không phân biệt được ở đây: bước chuyển không tồn
     * tại, hoặc người dùng thiếu quyền. Đó chính là lý do nơi gọi ⛔ không được ném dựa trên nó.
     */
    private Optional<String> dichDen(HydroReading banGhi, String action) {
        return workflow.allowedActions(banGhi).stream()
                .filter(a -> a.action().equals(action))
                .map(AllowedAction::toState)
                .findFirst();
    }

    /**
     * ⭐⭐ Địa chỉ của <b>một</b> số đo — khoá tự nhiên, ⛔ không phải khoá tự tăng.
     *
     * <p>Đường duyệt ⛔ không nhận {@code id} trên URL: {@code ApiSurfaceRuleTest} cấm mọi
     * {@code @PathVariable} kiểu số (gõ 1, 2, 3 là quét hết bảng), và một cột {@code public_id} trên
     * bảng phân mảnh ⛔ không ép được duy nhất toàn cục — xem
     * {@code HydroReadingRepository.findByStationIdAndMeasurementTypeIdAndMeasuredAt}.
     *
     * <p>⭐ Bộ khoá này <b>trùng khít</b> bộ khoá của ô nhập tay. Một địa chỉ dùng chung cho cả hai
     * đường ghi là một chỗ để nhớ thay vì hai (luật 14).
     */
    public record KhoaBanGhi(UUID diemDoId, String maLoaiChiSo, Instant mocDo) {}

    private HydroReading nap(KhoaBanGhi khoa) {
        Long stationId = stations.findByPublicIdAndDeletedAtIsNull(khoa.diemDoId())
                .map(Station::getId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        Long typeId = loaiChiSo
                .findByCodeAndDeletedAtIsNull(khoa.maLoaiChiSo())
                .map(MeasurementType::getId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        return readings.findByStationIdAndMeasurementTypeIdAndMeasuredAt(stationId, typeId, khoa.mocDo())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
