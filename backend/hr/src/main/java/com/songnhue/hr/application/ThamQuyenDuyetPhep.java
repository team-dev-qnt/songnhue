package com.songnhue.hr.application;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.hr.domain.LeaveRequest;
import com.songnhue.hr.domain.LeaveState;
import com.songnhue.hr.infra.UyQuyenDuyetPhepRepository;

/**
 * <b>Ai</b> được quyết một đơn nghỉ phép — vế QUAN HỆ của CN-04.9 (T80.1 → T80.5).
 *
 * <h2>⛔⛔ Hai cổng, và ⛔ cổng nào thay được cổng kia</h2>
 *
 * <ul>
 *   <li><b>Cổng năng lực</b> — {@code hr:leave:approve}, tĩnh, đọc từ token, ép ở
 *       {@code workflow_transitions.required_permission}. Nó trả lời <i>"tài khoản này có làm công
 *       việc duyệt phép ⛔"</i>. <b>Lớp này ⛔ kiểm lại nó</b>: kiểm hai lần ở hai nơi là hai nơi
 *       phải nhớ cùng một luật (luật 14).
 *   <li><b>Cổng quan hệ</b> — chính lớp này, <b>động</b>, đọc lại từ CSDL ở đúng khoảnh khắc bấm
 *       nút. Nó trả lời <i>"tài khoản này có phải người duyệt của ĐƠN NÀY ⛔"</i>.
 * </ul>
 *
 * <p>Trước 20/09/2026 chỉ có cổng thứ nhất cộng bộ lọc phạm vi, và {@code V202609141079} khai rằng
 * thế là đủ. Đo lại thì bộ lọc phạm vi cắt đúng <i>đơn vị nào</i> mà ⛔ nói gì về <i>ai</i> — hậu
 * quả là bốn trạng thái ở javadoc {@code ThamQuyenDuyetPhepHttpTest}, cả bốn trả 200.
 *
 * <h2>Vì sao đọc lại từ CSDL thay vì cộng quyền vào token</h2>
 *
 * <p>{@code AuthenticatedUser} cấm tường minh việc cộng quyền lúc chạy. Và kể cả nếu ⛔ cấm: access
 * token sống <b>30 phút</b>, nên một lượt <b>thu hồi</b> uỷ quyền sẽ trễ tới 30 phút. Đọc lại từ
 * CSDL cho <b>hiệu lực tức thì</b> — cùng cách bộ lọc phạm vi tầng 3 vẫn làm.
 *
 * <h2>⚠ Phạm vi của lớp này — nói ra để ⛔ ai đọc cái xanh của nó quá rộng (luật 28)</h2>
 *
 * <p>Nó quyết định <b>nút</b>, ⛔ quyết định <b>tầm nhìn</b>. Hộp <i>Chờ duyệt</i> vẫn cắt theo bộ
 * lọc phạm vi, nên một quản lý ⛔ giữ chức vụ vẫn <b>thấy</b> đơn của đơn vị mình — cố ý: đó là
 * danh sách việc của đơn vị, hữu ích để nắm quân số. Cái họ ⛔ có là nút. ⬜ Hệ quả còn lại (thư
 * báo vẫn tới họ, và màn hình chưa nói rõ ai phải bấm) là nợ <b>T80.7</b>.
 */
@Service
public class ThamQuyenDuyetPhep {

    private final OrgUnitPort orgUnits;
    private final UyQuyenDuyetPhepRepository uyQuyen;

    public ThamQuyenDuyetPhep(OrgUnitPort orgUnits, UyQuyenDuyetPhepRepository uyQuyen) {
        this.orgUnits = orgUnits;
        this.uyQuyen = uyQuyen;
    }

    /**
     * Vì sao một lượt quyết bị từ chối — <b>ba lý do, ba câu trả lời</b>.
     *
     * <p>Gộp cả ba vào một mã 403 chung thì người dùng đọc <i>"⛔ có quyền"</i> trong khi việc phải
     * làm khác hẳn nhau: nhờ cấp trên quyết · nhờ <b>người khác</b> quyết cấp 2 · hoặc ⛔ phải việc
     * của mình. Cùng lý lẽ đã dùng cho ba trạng thái *"báo cáo này ⛔ tải được"* (T59.0).
     */
    public enum LyDo {
        /** Ổn — được quyết. */
        DUOC,
        /** ⛔ Phải trưởng/phó của đơn vị hay cấp trên, cũng ⛔ được ai uỷ quyền. */
        KHONG_PHAI_NGUOI_DUYET,
        /** Đơn của chính mình. */
        TU_DUYET,
        /** Đã bấm ở cấp 1 rồi — cấp 2 phải người khác. */
        TRUNG_NGUOI_CAP_MOT
    }

    /**
     * @param uyQuyenId lượt uỷ quyền đã dùng, {@code null} = quyết với tư cách trưởng/phó của chính
     *     mình. Đây là nguồn của câu audit <i>"duyệt theo uỷ quyền của X"</i> (chốt B3), nên nó đi
     *     cùng kết quả chứ ⛔ được tra lại ở nơi khác — tra lại là hai lượt đọc có thể cho hai đáp
     *     án (uỷ quyền hết hạn đúng giữa hai câu lệnh).
     * @param duPhong {@code true} = quyết bằng đường dự phòng {@code hr:leave:delegate} vì cả chuỗi
     *     lãnh đạo của đơn vị ⛔ có trưởng/phó nào đang hoạt động
     */
    public record KetQua(LyDo lyDo, Long uyQuyenId, boolean duPhong) {

        public boolean duocPhep() {
            return lyDo == LyDo.DUOC;
        }

        static KetQua cam(LyDo lyDo) {
            return new KetQua(lyDo, null, false);
        }
    }

    /** Quyền dự phòng khi đơn vị chưa ai được giao chức vụ — xem {@code V202609201097} mục 2. */
    public static final String QUYEN_UY_QUYEN = "hr:leave:delegate";

    /**
     * Người này có quyết được đơn này (duyệt / từ chối / chuyển cấp) ⛔ — và <b>với tư cách gì</b>.
     *
     * <p>Thứ tự xét có chủ đích: hai vế <b>cấm</b> trước, vế <b>cho phép</b> sau. Ngược lại thì một
     * trưởng đơn vị tự nộp đơn sẽ nhận câu <i>"⛔ phải người duyệt"</i> — sai hướng, và nó giấu mất
     * thứ thật sự chặn.
     */
    @Transactional(readOnly = true)
    public KetQua xetQuyet(LeaveRequest don, AuthenticatedUser ai, LocalDate ngay) {
        if (ai == null || ai.userId() == null) {
            return KetQua.cam(LyDo.KHONG_PHAI_NGUOI_DUYET);
        }
        if (laNguoiNghi(don, ai)) {
            return KetQua.cam(LyDo.TU_DUYET);
        }
        if (don.trangThai() == LeaveState.CHO_DUYET_2 && Objects.equals(ai.userId(), don.getCap1By())) {
            return KetQua.cam(LyDo.TRUNG_NGUOI_CAP_MOT);
        }
        return thamQuyenTrenDonVi(don, ai, ngay);
    }

    /**
     * Người này có rút / huỷ được đơn này ⛔ — T80.4.
     *
     * <p>Hai nhóm, và <b>cả hai đều đúng</b>: người nộp tự rút đơn của mình, và người duyệt huỷ một
     * đơn đã quyết (bước chuyển ấy bắt buộc nêu lý do). Thứ ⛔ được là <b>người thứ ba</b> — trước
     * T80.4 bốn bước chuyển {@code CANCEL} chỉ đòi {@code hr:leave:request}, quyền mà chốt C3 cấp
     * cho <b>mọi CBNV</b>, nên một người ngồi cùng phòng huỷ được cả đơn đã duyệt của đồng nghiệp.
     *
     * <p>⚠ Ở đây <b>⛔ áp</b> vế cấm tự duyệt: rút đơn của chính mình đúng là việc của chính mình.
     */
    @Transactional(readOnly = true)
    public boolean huyDuoc(LeaveRequest don, AuthenticatedUser ai, LocalDate ngay) {
        if (ai == null || ai.userId() == null) {
            return false;
        }
        return laNguoiNop(don, ai) || thamQuyenTrenDonVi(don, ai, ngay).duocPhep();
    }

    /**
     * Người này có <b>giao</b> được thẩm quyền duyệt của một đơn vị ⛔ — T80.5.
     *
     * <p>⛔⛔ Cố ý <b>cùng vị từ</b> với đường 1 và 3 của {@link #thamQuyenTrenDonVi}: <i>⛔ ai
     * giao được thứ mình ⛔ có</i>. Viết lại luật ở service uỷ quyền thì hai nơi cùng phải nhớ một
     * luật, và ngày chúng lệch nhau là ngày một người ⛔ duyệt được lại uỷ quyền cho người khác
     * duyệt (luật 14).
     */
    @Transactional(readOnly = true)
    public boolean giaoDuoc(Long orgUnitId, AuthenticatedUser ai) {
        if (ai == null || ai.userId() == null || orgUnitId == null) {
            return false;
        }
        Set<Long> lanhDao = orgUnits.lanhDaoCuaChuoiDonVi(orgUnitId);
        return lanhDao.contains(ai.userId()) || (lanhDao.isEmpty() && ai.hasPermission(QUYEN_UY_QUYEN));
    }

    /** Đơn NÀY là của chính người đang thao tác — người sắp nghỉ, ⛔ phải người bấm hộ. */
    private static boolean laNguoiNghi(LeaveRequest don, AuthenticatedUser ai) {
        return don.getRequesterUserId() != null && Objects.equals(ai.userId(), don.getRequesterUserId());
    }

    /**
     * Người đã đứng tên nộp — người nghỉ, <b>hoặc</b> người đã nộp hộ (chốt C3).
     *
     * <p>⚠ Vế thứ hai ⛔ thừa: với CBNV ⛔ dùng máy tính thì {@code requesterUserId} là {@code null}
     * và người duy nhất theo dõi được lá đơn là người đã nộp hộ.
     */
    private static boolean laNguoiNop(LeaveRequest don, AuthenticatedUser ai) {
        return Objects.equals(ai.userId(), don.getRequesterUserId())
                || Objects.equals(ai.userId(), don.getCreatedForBy());
    }

    /**
     * Ba đường tới thẩm quyền trên <b>đơn vị</b> của đơn — và đúng ba.
     *
     * <ol>
     *   <li>trưởng/phó của đơn vị ấy hoặc của một đơn vị <b>cha</b>;
     *   <li>đang được <b>uỷ quyền</b> cho đơn vị ấy (hoặc một đơn vị cha) vào hôm nay;
     *   <li><b>dự phòng</b>: cả chuỗi lãnh đạo ⛔ có ai đang hoạt động, và người này giữ
     *       {@link #QUYEN_UY_QUYEN}.
     * </ol>
     *
     * <p>⛔⛔ Đường 3 ⛔ phải <i>"rơi về luật cũ"</i>. Luật cũ là <i>ai có
     * {@code hr:leave:approve} và phạm vi phủ</i> — nó khôi phục đúng hành vi rộng vừa bỏ, và cái
     * xanh của bộ canh khi ấy đọc như đã siết (quy tắc 7). Đường này hẹp hơn hẳn (đòi thêm một
     * quyền riêng), và nó <b>ghi lại</b> trên chính lá đơn ({@code duyet_du_phong}) — một ô hành
     * chính bỏ trống ⛔ được im lặng.
     */
    private KetQua thamQuyenTrenDonVi(LeaveRequest don, AuthenticatedUser ai, LocalDate ngay) {
        Set<Long> lanhDao = orgUnits.lanhDaoCuaChuoiDonVi(don.getOrgUnitId());
        if (lanhDao.contains(ai.userId())) {
            return new KetQua(LyDo.DUOC, null, false);
        }

        Set<Long> chuoi = orgUnits.chuoiDonViLen(don.getOrgUnitId());
        Long idUyQuyen = chuoi.isEmpty() ? null : uyQuyen.idUyQuyenDangDung(ai.userId(), chuoi, ngay);
        if (idUyQuyen != null) {
            return new KetQua(LyDo.DUOC, idUyQuyen, false);
        }

        if (lanhDao.isEmpty() && ai.hasPermission(QUYEN_UY_QUYEN)) {
            return new KetQua(LyDo.DUOC, null, true);
        }
        return KetQua.cam(LyDo.KHONG_PHAI_NGUOI_DUYET);
    }
}
