package com.songnhue.hr.application;

import java.time.LocalDate;
import java.util.LinkedHashSet;
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
        LyDo cam = veCam(don, ai);
        if (cam != null) {
            return KetQua.cam(cam);
        }
        return thamQuyenTrenDonVi(don, ai, ngay);
    }

    /**
     * Hai vế <b>CẤM</b> — tách ra vì {@link #donQuyetDuoc} phải áp <b>đúng</b> chúng.
     *
     * <p>⛔⛔ Chép lại ba dòng này ở nơi tính cờ cho giao diện là luật 14 ở dạng đắt nhất: ngày hai
     * bản lệch nhau là ngày màn hình bày một cái nút mà máy chủ từ chối — hoặc <b>giấu</b> một cái
     * nút đáng ra bấm được, và trạng thái thứ hai thì ⛔ ai báo.
     *
     * <h2>⭐ Nhận {@code Long userId}, ⛔ phải {@link AuthenticatedUser} — T85.3 (22/09/2026)</h2>
     *
     * <p>Cả ba vế trên <b>chỉ đọc {@code userId}</b>, ⛔ chạm tới quyền hay phạm vi. Trước lượt này
     * chữ ký đòi một {@code AuthenticatedUser}, nên đường tính <b>người NHẬN THƯ</b> — vốn chỉ có
     * trong tay một danh sách id — ⛔ gọi được nó và sẽ phải chép lại ba dòng ấy. Chính javadoc ngay
     * trên đây đã gọi tên cái giá của việc chép. ⇒ Hạ tham số xuống đúng thứ nó dùng, và
     * {@link #nguoiQuyetDuocDon} dùng chung <b>một</b> bản.
     *
     * @param userId {@code null} ⇒ ⛔ có ai đang thao tác
     * @return {@code null} khi ⛔ vế cấm nào chạm tới
     */
    private static LyDo veCam(LeaveRequest don, Long userId) {
        if (userId == null) {
            return LyDo.KHONG_PHAI_NGUOI_DUYET;
        }
        if (Objects.equals(userId, don.getRequesterUserId())) {
            return LyDo.TU_DUYET;
        }
        if (don.trangThai() == LeaveState.CHO_DUYET_2 && Objects.equals(userId, don.getCap1By())) {
            return LyDo.TRUNG_NGUOI_CAP_MOT;
        }
        return null;
    }

    /** Bản tiện cho nơi gọi đang cầm một {@link AuthenticatedUser}. */
    private static LyDo veCam(LeaveRequest don, AuthenticatedUser ai) {
        return veCam(don, ai == null ? null : ai.userId());
    }

    /**
     * Ai quyết được <b>ĐÚNG lá đơn này, ngay lúc này</b> — T85.3.
     *
     * <h2>⛔⛔ Vì sao ⛔ dùng thẳng {@link #nguoiQuyetDuoc}</h2>
     *
     * <p>{@link #nguoiQuyetDuoc} trả lời câu <i>"ai có thẩm quyền trên ĐƠN VỊ này"</i> — một câu hỏi
     * về <b>cơ cấu</b>, ⛔ về lá đơn. Nó ⛔ biết người nộp là ai, và ⛔ biết ai đã duyệt cấp 1. Gửi
     * thư <i>"có đơn chờ bạn duyệt cấp 2"</i> cho chính người vừa duyệt cấp 1 là mời họ làm một việc
     * mà máy chủ sẽ từ chối bằng {@link LyDo#TRUNG_NGUOI_CAP_MOT} — và phân tách trách nhiệm là lý do
     * cấp 2 tồn tại.
     *
     * <p>⇒ Hàm này = {@link #nguoiQuyetDuoc} <b>trừ</b> đúng những ai {@link #veCam} đang cấm. Cùng
     * một bản luật với {@link #xetQuyet} và {@link #donQuyetDuoc}, nên tập <i>nhận thư</i> ⛔ thể lệch
     * khỏi tập <i>bấm được nút</i>.
     *
     * <p>⚠ Tính theo trạng thái <b>hiện thời</b> của {@code don}. Nơi gọi phải chọn đúng thời điểm:
     * với {@code ESCALATE} là <b>SAU</b> bước chuyển (lúc ấy mới có {@code cap1By}), với
     * {@code CANCEL} là <b>TRƯỚC</b> (sau đó đơn đã sang {@code DA_HUY}, ⛔ còn ai "đang giữ" nó).
     *
     * @return rỗng ⇒ đơn vị ⛔ có lãnh đạo lẫn người được uỷ quyền; nơi gọi phải đi đường dự phòng,
     *     ⛔ phải im lặng
     */
    @Transactional(readOnly = true)
    public Set<Long> nguoiQuyetDuocDon(LeaveRequest don, LocalDate ngay) {
        if (don == null) {
            return Set.of();
        }
        Set<Long> ketQua = new LinkedHashSet<>(nguoiQuyetDuoc(don.getOrgUnitId(), ngay));
        ketQua.removeIf(userId -> veCam(don, userId) != null);
        return ketQua;
    }

    /**
     * Trong một TRANG đơn, những đơn nào người này quyết được — T80.7 vế (a).
     *
     * <h2>⛔⛔ Vì sao nhận cả TRANG thay vì hỏi từng dòng</h2>
     *
     * <p>Kho đã có {@code GET /{publicId}/hanh-dong} trả đúng câu trả lời cho MỘT đơn. Gọi nó cho
     * từng dòng của hộp chờ là <b>N+1</b> trên một màn hình có phân trang — đúng thứ
     * {@code DemTruyVan} và bộ canh độ dốc (T58.18) sinh ra để bắt. Ở đây số câu lệnh bằng
     * <b>số đơn vị PHÂN BIỆT</b> trong trang (thường 1–3), ⛔ phải số dòng.
     *
     * <p>⚠ Và nó áp <b>cùng</b> {@link #veCam} với {@link #xetQuyet}, nên cờ hiện trên màn hình và
     * câu trả lời của máy chủ ⛔ thể lệch nhau.
     */
    @Transactional(readOnly = true)
    public Set<Long> donQuyetDuoc(java.util.List<LeaveRequest> dons, AuthenticatedUser ai, LocalDate ngay) {
        if (ai == null || ai.userId() == null || dons == null || dons.isEmpty()) {
            return Set.of();
        }
        Set<Long> donVi = new LinkedHashSet<>();
        for (LeaveRequest d : dons) {
            if (d.getOrgUnitId() != null) {
                donVi.add(d.getOrgUnitId());
            }
        }
        Set<Long> donViDuoc = donViQuyetDuoc(donVi, ai, ngay);

        Set<Long> ketQua = new LinkedHashSet<>();
        for (LeaveRequest d : dons) {
            if (veCam(d, ai) == null && donViDuoc.contains(d.getOrgUnitId())) {
                ketQua.add(d.getId());
            }
        }
        return ketQua;
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

    /**
     * <b>Những ai</b> quyết được một đơn của đơn vị này hôm nay — T80.7.
     *
     * <h2>Vì sao phải có, và vì sao nó nằm ở ĐÂY</h2>
     *
     * <p>Thư <i>"đơn mới chờ duyệt"</i> trước 20/09/2026 đi theo <b>phạm vi</b>: mọi tài khoản có
     * {@code hr:leave:approve} mà phạm vi dữ liệu phủ đơn vị của đơn. Tập ấy <b>rộng hơn</b> tập
     * người bấm được nút — đo được trên đồ gá của {@code NghiPhepHttpTest}: một tài khoản ở đơn vị
     * gốc có quyền và có phạm vi, nhưng ⛔ phải trưởng/phó và ⛔ được uỷ quyền ⇒ <i>thấy đơn, nhận
     * thư, mà ⛔ có nút</i>.
     *
     * <p>⛔⛔ Cách vá mà dòng nợ kê ra — <i>"ca thứ năm của {@code RecipientResolver}"</i> — <b>SAI</b>:
     * {@code core} ⛔ được import {@code hr} (quy tắc 6), nên nó ⛔ nhìn thấy {@code UyQuyenDuyetPhep}
     * và sẽ <b>bỏ sót đúng người được uỷ quyền</b> — tức tập mới vừa hẹp lại vừa THIẾU, và nó thiếu
     * đúng ở ca mà uỷ quyền sinh ra để phục vụ. ⇒ Danh sách phải dựng ở {@code hr} rồi truyền sang
     * như một danh sách <b>đích danh</b>.
     *
     * <p>⚠ Trả <b>tập rỗng</b> khi đơn vị ⛔ có lãnh đạo lẫn người được uỷ quyền. Nơi gọi phải phân
     * biệt được <i>rỗng</i> với <i>có người</i>: rỗng nghĩa là đường <b>dự phòng</b>
     * ({@link #QUYEN_UY_QUYEN}) đang mở, và thư khi ấy phải đi theo quyền ấy — ⛔ phải ⛔ gửi cho ai.
     *
     * @return id tài khoản, đã khử trùng lặp, giữ thứ tự ổn định. <b>Chưa</b> trừ người nộp đơn —
     *     nơi gọi biết ai nộp, lớp này thì ⛔.
     */
    @Transactional(readOnly = true)
    public Set<Long> nguoiQuyetDuoc(Long orgUnitId, LocalDate ngay) {
        if (orgUnitId == null) {
            return Set.of();
        }
        Set<Long> ketQua = new LinkedHashSet<>(orgUnits.lanhDaoCuaChuoiDonVi(orgUnitId));
        Set<Long> chuoi = orgUnits.chuoiDonViLen(orgUnitId);
        if (!chuoi.isEmpty()) {
            ketQua.addAll(uyQuyen.nguoiDangDuocUyQuyen(chuoi, ngay));
        }
        return ketQua;
    }

    /**
     * Người này quyết được đơn vị nào trong số {@code orgUnitIds} — dùng cho cờ mỗi dòng của hộp
     * <i>Chờ duyệt</i> (T80.7 vế a).
     *
     * <p>⚠⚠ Nhận CẢ TẬP chứ ⛔ từng đơn vị một, và đó là điểm mấu chốt: hỏi từng dòng là <b>N+1</b>
     * trên một màn hình có phân trang — đúng thứ {@code DemTruyVan} sinh ra để bắt. Một trang thường
     * chỉ có 1–3 đơn vị khác nhau, nên số câu lệnh thật bằng <b>số đơn vị PHÂN BIỆT</b>, ⛔ phải số
     * dòng.
     */
    @Transactional(readOnly = true)
    public Set<Long> donViQuyetDuoc(Set<Long> orgUnitIds, AuthenticatedUser ai, LocalDate ngay) {
        if (ai == null || ai.userId() == null || orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> duoc = new LinkedHashSet<>();
        for (Long donVi : orgUnitIds) {
            if (donVi == null) {
                continue;
            }
            Set<Long> nguoi = nguoiQuyetDuoc(donVi, ngay);
            // ⚠ Nhánh dự phòng phải giống HỆT `thamQuyenTrenDonVi`, ⛔ phải "gần giống": hai nơi
            //   cùng phải nhớ một luật là luật 14, và ngày chúng lệch nhau là ngày màn hình bày một
            //   cái nút mà máy chủ từ chối (hoặc giấu một cái nút đáng ra bấm được).
            boolean lanhDaoRong = orgUnits.lanhDaoCuaChuoiDonVi(donVi).isEmpty();
            if (nguoi.contains(ai.userId()) || (lanhDaoRong && ai.hasPermission(QUYEN_UY_QUYEN))) {
                duoc.add(donVi);
            }
        }
        return duoc;
    }

    // ⚠ `laNguoiNghi` đã GỠ 22/09/2026 (T85.3): sau khi `veCam` hạ tham số xuống `Long userId`, nó
    //   còn đúng MỘT nơi gọi rồi thành 0. Giữ lại một hàm ⛔ ai đọc là đúng thứ luật 15 cấm — và ở
    //   đây nó còn tệ hơn mức thường: nó mang một vị từ về THẨM QUYỀN, nên lượt rà sau sẽ đọc nó
    //   thành *"luật này đang được áp ở đâu đó"*. Vị từ ấy nay nằm nguyên trong `veCam`.

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
