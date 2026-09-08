package com.songnhue.content.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Feedback;
import com.songnhue.content.domain.FeedbackStatus;
import com.songnhue.content.infra.FeedbackRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Kiểm duyệt phản hồi ở phía <b>quản trị</b> — CN-01.6, chốt <b>D1</b>.
 *
 * <h2>⛔ Trạng thái đổi DUY NHẤT qua {@link WorkflowPort}</h2>
 *
 * <p>⛔ Không có {@code switch} nào ở đây: hành động hợp lệ, quyền cần có, và "bước này có đòi lý
 * do không" đều nằm trong {@code workflow_transitions} — <b>dữ liệu</b>, ⛔ không phải mã. Thêm một
 * bước là một dòng migration, ⛔ không phải một lượt deploy.
 *
 * <h2>⭐⭐ Mọi bước chuyển đều XOÁ ĐỆM CỔNG — kể cả bước ⛔ không lên cổng</h2>
 *
 * <p>{@code APPROVE} và {@code SHOW} thêm một mục lên cổng; {@code HIDE} gỡ một mục <b>đang hiện
 * ra</b>. Ba bước ấy phải xoá đệm là hiển nhiên. {@code REJECT} thì ⛔ không đổi gì trên cổng — và
 * nó <b>vẫn</b> gọi, vì phân biệt ở đây đòi một bảng ánh xạ {@code action → có đổi cổng không},
 * tức một bản sao thứ hai của {@code workflow_transitions} sống trong mã Java. Bản sao ấy sẽ lệch
 * ngay lần đầu Công ty thêm một bước, và nó lệch <b>im lặng</b>: triệu chứng là "cổng ⛔ không đổi
 * gì" — đúng thứ ⛔ không ai báo lỗi. Một lượt dựng lại thừa mỗi vài ngày rẻ hơn hẳn.
 *
 * <h2>⚠ {@link #tongHop()} trả về một con số trung bình KÈM mẫu số của nó</h2>
 *
 * <p>Xem javadoc {@link ThongKe} — đây là chỗ luật 9 dễ vi phạm nhất của cả lượt này.
 */
@Service
public class FeedbackModerationService {

    private static final int TRANG_TOI_DA = 100;

    private final FeedbackRepository feedbacks;
    private final WorkflowPort workflow;
    private final FeedbackService congKhai;

    public FeedbackModerationService(FeedbackRepository feedbacks, WorkflowPort workflow, FeedbackService congKhai) {
        this.feedbacks = feedbacks;
        this.workflow = workflow;
        this.congKhai = congKhai;
    }

    @Transactional(readOnly = true)
    public Page<Feedback> danhSach(FeedbackStatus loc, int trang, int cor) {
        PageRequest yeuCau = PageRequest.of(Math.max(trang, 0), Math.min(Math.max(cor, 1), TRANG_TOI_DA));
        return loc == null
                ? feedbacks.findAllByDeletedAtIsNullOrderByCreatedAtDesc(yeuCau)
                : feedbacks.findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(loc, yeuCau);
    }

    /** Số mục đang chờ duyệt — cho huy hiệu trên thanh điều hướng quản trị. */
    @Transactional(readOnly = true)
    public long demChoDuyet() {
        return feedbacks.countByStatusAndDeletedAtIsNull(FeedbackStatus.CHO_DUYET);
    }

    /** Các nút được phép hiện — đã lọc theo quyền người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<AllowedAction> hanhDongChoPhep(UUID publicId) {
        return workflow.allowedActions(tim(publicId));
    }

    @Transactional
    public Feedback chuyenTrangThai(UUID publicId, String hanhDong, String lyDo) {
        Feedback f = tim(publicId);
        Feedback daLuu = feedbacks.save(workflow.execute(f, hanhDong, null, lyDo));
        congKhai.congDaDoi();
        return daLuu;
    }

    /**
     * Xoá mềm — ⛔ <b>không</b> chặn ở trạng thái nào.
     *
     * <p>⚠ Khác {@code contacts}, nơi {@code DANG_XU_LY} bị cấm xoá vì <i>có người dân đang chờ
     * một câu trả lời</i>. Ở đây ⛔ không ai chờ gì: một mục spam vừa gửi lên phải xoá được ngay,
     * và một mục đã duyệt xoá được là cách gỡ nội dung xuống <b>vĩnh viễn</b> sau khi đã có bước
     * {@code HIDE} nhẹ hơn đứng trước. Bản ghi vẫn còn trong CSDL kèm nhật ký kiểm toán.
     */
    @Transactional
    public void xoa(UUID publicId) {
        Feedback f = tim(publicId);
        f.markDeleted(Instant.now());
        feedbacks.save(f);
        congKhai.congDaDoi();
    }

    /**
     * Tổng hợp phục vụ báo cáo — CN-01.6.
     *
     * <p>⛔⛔ Tính ở <b>backend</b> (quy tắc 3), và bằng {@link BigDecimal} (quy tắc 2): kho ⛔ không
     * trả {@code avg()} vì JPQL cho ra {@code Double}, nên ở đây là một phép chia
     * {@code tổng / số phiếu} với cả tử lẫn mẫu nhìn thấy được.
     *
     * <h3>⭐ T28.54 — mẫu số là mục ĐÃ DUYỆT, và đó là quyết định NGHIỆP VỤ đã chốt</h3>
     *
     * <p>Chốt <b>08/09/2026</b>: điểm trung bình tính trên các mục {@code DA_DUYET} có chấm điểm, và
     * <b>trả kèm</b> {@code choDuyet}/{@code tuChoi} để phần bị loại nhìn thấy được.
     *
     * <p>Hai hướng đều có chỗ sai, nên đây là một <i>lựa chọn</i> chứ ⛔ không phải một mặc định:
     *
     * <ul>
     *   <li><b>Tính trên đã duyệt</b> — một người kiểm duyệt loại các phiếu tiêu cực sẽ làm số liệu
     *       đẹp lên. Đây là hướng đã chọn, và cái giá của nó trả bằng việc <b>công bố luôn số bị
     *       loại</b>: chênh lệch giữa hai con số tự nó là thước đo mức độ kiểm duyệt.
     *   <li><b>Tính trên tất cả</b> — spam đi thẳng vào số liệu công bố, mà reCAPTCHA còn chờ G13.
     * </ul>
     *
     * <p>Cơ sở: <b>chốt D1</b> (12/8/2026) — <i>"chưa duyệt thì chưa là dữ liệu"</i>. Một con số tính
     * trên thứ ⛔ chưa được công bố sẽ ⛔ không khớp với bất kỳ thứ gì người đọc thấy trên cổng.
     *
     * <p>⛔⛔ <b>ĐỪNG đổi công thức này</b> nếu ⛔ không có một lượt chốt mới: số liệu đã công bố mà
     * đổi cách tính thì hai kỳ báo cáo ⛔ không so được với nhau, và ⛔ không gì trong dữ liệu nói ra
     * chỗ đứt.
     */
    @Transactional(readOnly = true)
    public ThongKe tongHop() {
        long choDuyet = feedbacks.countByStatusAndDeletedAtIsNull(FeedbackStatus.CHO_DUYET);
        long daDuyet = feedbacks.countByStatusAndDeletedAtIsNull(FeedbackStatus.DA_DUYET);
        long tuChoi = feedbacks.countByStatusAndDeletedAtIsNull(FeedbackStatus.TU_CHOI);
        long an = feedbacks.countByStatusAndDeletedAtIsNull(FeedbackStatus.AN);
        long soCoDiem = feedbacks.countByRatingIsNotNullAndStatusAndDeletedAtIsNull(FeedbackStatus.DA_DUYET);
        Long tongDiem = feedbacks.tongDiemDaDuyet();

        // ⛔ Chưa phiếu nào có điểm ⇒ `null`, ⛔ KHÔNG phải 0. Quy tắc 16: số 0 là một khẳng định —
        //   "0 sao" nói rằng người dùng rất không hài lòng, còn "chưa ai chấm" thì ⛔ không nói gì.
        BigDecimal trungBinh = (soCoDiem == 0 || tongDiem == null)
                ? null
                : BigDecimal.valueOf(tongDiem).divide(BigDecimal.valueOf(soCoDiem), 2, RoundingMode.HALF_UP);

        return new ThongKe(choDuyet + daDuyet + tuChoi + an, choDuyet, daDuyet, tuChoi, an, soCoDiem, trungBinh);
    }

    /**
     * Số liệu tổng hợp — <b>và mọi phép lọc của nó đều nói ra</b>.
     *
     * <h2>⛔⛔ Vì sao {@code diemTrungBinh} ⛔ KHÔNG bao giờ đứng một mình</h2>
     *
     * <p>Nó tính trên <b>hai lớp lọc</b>: chỉ mục {@code DA_DUYET}, và trong đó chỉ mục <b>có chấm
     * điểm</b> ({@code rating} cho phép {@code null} — xem {@link Feedback}). Một con số 4,2 trả về
     * trần trụi ⛔ không phân biệt được <i>"4,2 trên 5 phiếu"</i> với <i>"4,2 trên 500 phiếu"</i>,
     * và cũng ⛔ không cho ai thấy rằng có 40 phiếu đang bị từ chối nằm ngoài phép tính — luật 9.
     *
     * <p>⇒ {@code soCoDiem} là <b>mẫu số</b>, và bốn con số trạng thái cho thấy phần đã bị lọc ra.
     * ⛔ Đừng thêm một endpoint chỉ trả mỗi số trung bình.
     *
     * <p>⚠ <b>Câu hỏi nghiệp vụ còn mở</b> (ghi ra thay vì tự quyết): thống kê nên tính trên mục
     * <i>đã duyệt</i> hay trên <i>mọi</i> mục gửi lên? Tính trên đã duyệt thì một người kiểm duyệt
     * loại các phiếu tiêu cực sẽ làm số liệu đẹp lên; tính trên tất cả thì spam làm hỏng số liệu.
     * Bản này chọn <b>đã duyệt</b> (bám đúng chốt D1: chưa duyệt thì chưa là dữ liệu), và trả kèm
     * {@code choDuyet}/{@code tuChoi} để phần bị loại nhìn thấy được.
     *
     * @param diemTrungBinh {@code null} khi ⛔ chưa phiếu đã duyệt nào có chấm điểm
     */
    public record ThongKe(
            long tong, long choDuyet, long daDuyet, long tuChoi, long an, long soCoDiem, BigDecimal diemTrungBinh) {}

    private Feedback tim(UUID publicId) {
        return feedbacks
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
