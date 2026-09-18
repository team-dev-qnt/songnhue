package com.songnhue.hr.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.hr.application.CanhBaoHetHanService;
import com.songnhue.hr.application.HoSoTaiLieuService;
import com.songnhue.hr.domain.EmployeeEvent;
import com.songnhue.hr.domain.EmployeeEventType;
import com.songnhue.hr.domain.EmployeeQualification;
import com.songnhue.hr.domain.HoSoThuMuc;
import com.songnhue.hr.domain.QualificationKind;

/**
 * DTO của ba lớp hồ sơ con — CN-04.3 (lý lịch), CN-04.4 (timeline), CN-04.5 (tài liệu).
 *
 * <p>⛔ Tách khỏi {@code HrDtos} vì lý do <b>phạm vi</b>, ⛔ không phải vì độ dài: {@code HrDtos}
 * mang hợp đồng của hồ sơ chính và có một bài kiểm cấu trúc khẳng định nó ⛔ không chứa tên trường
 * 🔒 nào. Trộn thêm DTO của ba màn hình khác vào đó làm bài kiểm ấy nói về một tập rộng hơn thứ nó
 * thật sự canh.
 */
public final class HoSoConDtos {

    private HoSoConDtos() {}

    // ==== CN-04.3 — Lý lịch & chuyên môn ====================================

    /**
     * @param expiresOn {@code null} = ⛔ không hết hiệu lực (bằng đại học). ⛔ Đừng để giao diện tự
     *     điền một ngày xa — chuông M4.9 sẽ kêu vào năm 2099 (quy tắc 3)
     */
    public record LyLichRequest(
            @NotNull QualificationKind kind,
            @NotNull @Size(max = 255) String name,
            @Size(max = 100) String grade,
            @Size(max = 255) String major,
            @Size(max = 255) String institution,
            @Size(max = 100) String certificateNo,
            LocalDate issuedOn,
            LocalDate expiresOn,
            String note) {}

    public record LyLichView(
            UUID publicId,
            QualificationKind kind,
            String name,
            String grade,
            String major,
            String institution,
            String certificateNo,
            LocalDate issuedOn,
            LocalDate expiresOn,
            String note,
            Instant updatedAt) {

        public static LyLichView of(EmployeeQualification q) {
            return new LyLichView(
                    q.getPublicId(),
                    q.getKind(),
                    q.getName(),
                    q.getGrade(),
                    q.getMajor(),
                    q.getInstitution(),
                    q.getCertificateNo(),
                    q.getIssuedOn(),
                    q.getExpiresOn(),
                    q.getNote(),
                    q.getUpdatedAt() == null ? q.getCreatedAt() : q.getUpdatedAt());
        }
    }

    // ==== CN-04.4 — Timeline công tác =======================================

    /**
     * @param effectiveOn ngày quyết định <b>có hiệu lực</b> — trục timeline, ⛔ khác ngày ký
     */
    public record SuKienRequest(
            @NotNull EmployeeEventType eventType,
            @NotNull LocalDate effectiveOn,
            @Size(max = 100) String decisionNo,
            LocalDate decisionDate,
            @NotNull @Size(max = 255) String title,
            String detail) {}

    public record SuKienView(
            UUID publicId,
            EmployeeEventType eventType,
            LocalDate effectiveOn,
            String decisionNo,
            LocalDate decisionDate,
            String title,
            String detail,
            Instant updatedAt) {

        public static SuKienView of(EmployeeEvent e) {
            return new SuKienView(
                    e.getPublicId(),
                    e.getEventType(),
                    e.getEffectiveOn(),
                    e.getDecisionNo(),
                    e.getDecisionDate(),
                    e.getTitle(),
                    e.getDetail(),
                    e.getUpdatedAt() == null ? e.getCreatedAt() : e.getUpdatedAt());
        }
    }

    // ==== CN-04.5 — Tài liệu ================================================

    /**
     * @param thuMuc một trong 7 thư mục cố định
     * @param phienBan tải lại cùng thư mục ⇒ phiên bản kế tiếp, bản cũ <b>giữ nguyên</b> (đặc tả đòi
     *     *"versioning ⛔ không ghi đè"*) — đây là hành vi sẵn có của {@code AttachmentService}
     * @param taiDuoc {@code false} khi tệp ⛔ chưa quét virus xong hoặc đã bị cách ly. Giao diện phải
     *     hiện đúng trạng thái ấy thay vì đưa ra một nút tải sẽ bị từ chối
     */
    public record TaiLieuView(
            UUID publicId,
            HoSoThuMuc thuMuc,
            String tenGoc,
            String kieuNoiDung,
            long soByte,
            int phienBan,
            boolean taiDuoc,
            LocalDate hieuLucTu,
            LocalDate hetHan,
            Instant taiLuc) {

        /** @param thuMuc đã giải từ {@code purpose}; {@code null} khi giá trị ⛔ không giải được */
        public static TaiLieuView of(AttachmentRef ref, HoSoThuMuc thuMuc) {
            return new TaiLieuView(
                    ref.publicId(),
                    thuMuc,
                    ref.originalName(),
                    ref.contentType(),
                    ref.sizeBytes(),
                    ref.fileVersion(),
                    ref.downloadable(),
                    ref.validFrom(),
                    ref.validUntil(),
                    ref.createdAt());
        }
    }

    /**
     * @param daCauHinh Công ty đã khai thư mục bắt buộc chưa. ⛔⛔ <b>Cờ này tồn tại vì
     *     {@code phanTram} một mình ⛔ KHÔNG đủ</b>: cấu hình Jackson của dự án <b>bỏ hẳn</b> trường
     *     {@code null} khỏi thân JSON, nên *"chưa cấu hình"* tới giao diện dưới dạng một trường
     *     <b>vắng mặt</b> — thứ mà một biểu thức {@code phanTram === null} ở TypeScript đọc thành
     *     {@code false}. Một khác biệt quan trọng ⛔ không được phép phụ thuộc vào việc bộ tuần tự
     *     hoá có bỏ null hay không (luật 9: hai trạng thái phải phân biệt được, và phân biệt được
     *     <b>một cách bền</b>)
     * @param phanTram ⛔ {@code null}/vắng mặt = <b>chưa cấu hình</b>, ⛔ không phải 0%. Quy tắc 16:
     *     số 0 là một khẳng định, và ở đây nó sẽ khẳng định *"hồ sơ này thiếu tài liệu"* đúng vào
     *     lúc ⛔ chưa ai định nghĩa thế nào là đủ
     */
    public record TinhTrangHoSoView(
            boolean daCauHinh,
            Integer phanTram,
            List<HoSoThuMuc> batBuoc,
            List<HoSoThuMuc> conThieu,
            Map<HoSoThuMuc, Long> soTepTheoThuMuc,
            long dungLuongDaDungByte) {

        public static TinhTrangHoSoView of(HoSoTaiLieuService.TinhTrangHoSo t, long daDung) {
            return new TinhTrangHoSoView(
                    t.phanTram() != null, t.phanTram(), t.batBuoc(), t.conThieu(), t.soTepTheoThuMuc(), daDung);
        }
    }

    // ==== M4.9 — Cảnh báo hết hạn ===========================================

    /**
     * @param nguongNgayHopDong giá trị ĐANG hiệu lực của {@code hr.contract.expiry-warning-days} —
     *     trả ra API để màn hình nói *"sắp hết hạn trong 30 ngày"* thay vì ghi cứng con số ấy ở giao
     *     diện. Hai nơi cùng một sự thật thì sẽ lệch (luật 14)
     */
    public record CanhBaoHetHanView(
            int nguongNgayHopDong, int nguongNgayChungChi, List<MucCanhBao> hopDong, List<MucCanhBao> chungChi) {

        public static CanhBaoHetHanView of(CanhBaoHetHanService.KetQua kq) {
            return new CanhBaoHetHanView(
                    kq.nguongNgayHopDong(),
                    kq.nguongNgayChungChi(),
                    kq.hopDong().stream().map(MucCanhBao::of).toList(),
                    kq.chungChi().stream().map(MucCanhBao::of).toList());
        }
    }

    /**
     * @param soNgayCon <b>âm nghĩa là đã hết hạn</b> — giao diện tô đỏ. ⛔ Đừng kẹp về 0 ở FE: mất
     *     đúng thông tin *"quá hạn bao lâu rồi"*, thứ quyết định việc nào làm trước
     */
    public record MucCanhBao(
            UUID hoSoPublicId, String maCanBo, String hoTen, String moTa, LocalDate hetHan, long soNgayCon) {

        public static MucCanhBao of(CanhBaoHetHanService.Muc m) {
            return new MucCanhBao(m.hoSoPublicId(), m.maCanBo(), m.hoTen(), m.moTa(), m.hetHan(), m.soNgayCon());
        }
    }

    // === Hồ sơ của tôi (CN-04.7 vế hai, T51.8) ================================

    /**
     * Hồ sơ CBNV của chính người đang đăng nhập, <b>kèm giá trị 🔒 đã giải mã</b>.
     *
     * <h2>⚠ Vì sao ở đây trường 🔒 đi CHUNG một phản hồi, trong khi ở màn hình quản trị thì ⛔ không</h2>
     *
     * <p>{@code HrDtos.SensitiveView} có một javadoc nói thẳng: nó <b>cố ý ⛔ không</b> nằm chung
     * đường với {@code EmployeeDetail}, vì gộp lại là biến "quyền xem trường 🔒" thành một nhánh
     * {@code if} bên trong một endpoint mà ai cũng gọi được. Lập luận ấy <b>đúng ở đó</b> và ⛔
     * <b>không</b> áp vào đây, vì nó nói về một endpoint <b>có nhiều hạng người gọi</b>.
     *
     * <p>Đường này chỉ có <b>một</b> hạng người gọi: chủ nhân của hồ sơ. ⛔ Không có nhánh nào để
     * sai — ⛔ không quyền để kiểm, ⛔ không id để so. Tách đôi ở đây chỉ tạo ra hai lượt gọi cho
     * cùng một màn hình và hai dòng {@code security_events} cho cùng một lượt xem.
     *
     * @param hoSo phần hồ sơ thường — {@code sensitive} bên trong vẫn là <b>cờ ô nào có dữ liệu</b>,
     *     ⛔ không phải giá trị; giá trị nằm ở {@code truongBaoMat}
     * @param truongBaoMat giá trị 🔒 đã giải mã của chính mình
     */
    public record HoSoCuaToiView(HrDtos.EmployeeDetail hoSo, HrDtos.SensitiveView truongBaoMat) {}

    // === Danh bạ nội bộ (CN-04.6) ============================================

    /**
     * Một người trên danh bạ — <b>chỉ liên hệ công vụ</b>.
     *
     * <p>⛔⛔ Record này ra ngoài cho <b>11/12 vai trò</b> và ⛔ <b>không</b> qua bộ lọc phạm vi đơn
     * vị. Thêm một trường vào đây là <b>công bố nó cho toàn Công ty</b>. Bất biến ấy được canh bởi
     * {@code DanhBaKhongLoDuLieuCaNhanTest} — nó đọc {@code getRecordComponents()} chứ ⛔ không đọc
     * mã nguồn, cùng khuôn {@code HoSoNhanSuKhongLoTruongKinTest}.
     */
    public record DanhBaView(
            UUID publicId,
            String code,
            String fullName,
            String gender,
            String phone,
            String workEmail,
            String jobTitle,
            String positionName,
            UUID orgUnitId,
            String orgUnitName) {

        static DanhBaView of(com.songnhue.hr.application.DanhBaMuc m) {
            return new DanhBaView(
                    m.publicId(),
                    m.code(),
                    m.fullName(),
                    m.gender(),
                    m.phone(),
                    m.workEmail(),
                    m.jobTitle(),
                    m.positionName(),
                    m.orgUnitId(),
                    m.orgUnitName());
        }
    }

    /**
     * @param tong tổng số người khớp bộ lọc — giao diện cần nó để nói *"tìm thấy N người"*, và
     *     ⛔ không suy được từ {@code muc.size()} vì đó chỉ là một trang
     */
    public record DanhBaTrangView(List<DanhBaView> muc, long tong, int trang, int co) {}

    /**
     * @param duongDanDonVi từ gốc xuống đơn vị của người này — *"vị trí trên sơ đồ"* mà đặc tả đòi.
     *     Dựng từ {@code org_units.path}, nên nó có <b>trước</b> CN-04.1 và ⛔ không chờ ai
     */
    public record DanhBaChiTietView(DanhBaView muc, List<String> duongDanDonVi, List<DanhBaView> dongNghiep) {}
}
