package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.hydro.domain.AlertEventStatus;
import com.songnhue.hydro.domain.CanhBaoRow;
import com.songnhue.hydro.infra.AlertEventQueryRepository;
import com.songnhue.hydro.infra.AlertEventWriter;

/**
 * Lịch sử cảnh báo và thao tác đóng bằng tay — <b>T33.11</b>.
 *
 * <h2>⛔ Đóng một cảnh báo ⛔ KHÔNG tạo bản ghi khắc phục</h2>
 *
 * <p>T33.10: cảnh báo là một <b>quan sát</b>; bản ghi bảo trì / khắc phục sự cố là một <b>quyết
 * định của con người</b>. Tự sinh {@code maintenance_logs} từ mỗi cảnh báo là đổ rác vào đúng bảng
 * mà cả MOD-02 dùng làm sổ gốc — và mỗi dòng rác ấy còn kéo theo một lượt tính lại trạng thái công
 * trình. Màn hình có nút <i>"Tạo bản ghi khắc phục"</i>, nút ấy <b>điền sẵn</b>
 * {@code alertEventPublicId} và để người dùng bấm.
 */
@Service
public class AlertEventService {

    private static final Logger log = LoggerFactory.getLogger(AlertEventService.class);

    private final AlertEventQueryRepository events;
    private final AlertEventWriter writer;

    public AlertEventService(AlertEventQueryRepository events, AlertEventWriter writer) {
        this.events = events;
        this.writer = writer;
    }

    /**
     * ⚠ Đếm trước, và bỏ hẳn lượt lấy trang khi tổng bằng 0 — cùng khuôn với
     * {@code HydroReviewService.hangCho}: một câu {@code LIMIT/OFFSET} với bốn phép {@code JOIN}
     * không đáng chạy để trả về danh sách rỗng.
     */
    @Transactional(readOnly = true)
    public Page<CanhBaoRow> trang(UUID diemDoPublicId, Boolean dangMo, Instant tu, Instant den, Pageable trang) {
        long tong = events.dem(diemDoPublicId, dangMo, tu, den);
        if (tong == 0) {
            return new PageImpl<>(List.of(), trang, 0);
        }
        List<CanhBaoRow> ds =
                events.trang(diemDoPublicId, dangMo, tu, den, trang.getPageSize(), (int) trang.getOffset());
        return new PageImpl<>(ds, trang, tong);
    }

    /**
     * Đóng bằng tay — người trực xác nhận đã xử lý, hoặc bác bỏ là báo động giả.
     *
     * <p>⚠ {@code UPDATE … WHERE status = 'DANG_XAY_RA'} trả <b>0 dòng</b> khi ai đó đã đóng trước
     * (hai người trực cùng mở màn hình là chuyện bình thường), hoặc khi máy vừa tự đóng vì giá trị
     * đã về dưới ngưỡng. ⛔ Không được nuốt thành một thông báo thành công giả — {@code HYD-2011}.
     *
     * @param laBaoDongGia {@code true} ⇒ {@code FALSE_ALARM}; ⚠ đây là một <b>phán xét nghiệp
     *     vụ</b>, khác hẳn {@code FALSE_ALARM} do máy đặt (điều kiện hết trước lúc xác nhận). Hai
     *     nguồn phân biệt được bằng {@code resolved_by}
     */
    @Transactional
    public void dong(UUID publicId, boolean laBaoDongGia, String ghiChu) {
        long id = events.khoaNoiBo(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        AlertEventStatus ket = laBaoDongGia ? AlertEventStatus.FALSE_ALARM : AlertEventStatus.DA_XU_LY;
        boolean daDong =
                writer.dong(id, ket, Instant.now(), AuthContext.require().userId(), ghiChu);
        if (!daDong) {
            throw new ConflictException(ErrorCode.HYD_2011);
        }
        log.info(
                "Đóng cảnh báo {} → {} bởi {}",
                publicId,
                ket,
                AuthContext.require().username());
    }
}
