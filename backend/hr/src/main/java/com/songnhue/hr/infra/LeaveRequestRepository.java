package com.songnhue.hr.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.LeaveRequest;
import com.songnhue.hr.domain.LeaveType;

/**
 * Đơn nghỉ phép — CN-04.9.
 *
 * <p>⛔⛔ Mọi câu ở đây đi qua <b>bộ lọc phạm vi</b> {@code @Filter} trên {@link LeaveRequest}, và
 * đó là <b>một nửa</b> của bảo đảm *"Quản lý ĐƠN VỊ MÌNH duyệt"*. Nửa kia là {@code @RequirePermission}
 * — xem javadoc {@code V202609141079} mục 3: một mã quyền ⛔ không diễn đạt được một quan hệ.
 */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Optional<LeaveRequest> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Đơn của một người, mới nhất trước — màn hình *"Đơn của tôi"*. */
    Page<LeaveRequest> findByEmployeeIdAndDeletedAtIsNullOrderByFromDateDesc(Long employeeId, Pageable pageable);

    /** Hộp chờ duyệt — bộ lọc phạm vi tự cắt theo đơn vị của người đang đăng nhập. */
    @Query(
            """
            SELECT r FROM LeaveRequest r
            WHERE r.deletedAt IS NULL AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2')
            ORDER BY r.fromDate ASC, r.id ASC
            """)
    Page<LeaveRequest> hopChoDuyet(Pageable pageable);

    /**
     * Mọi đơn <b>còn chiếm số dư</b> của một người trong một năm — <b>MỘT</b> nguồn cho cả phép
     * tính số dư.
     *
     * <p>⛔⛔ Bản đầu của {@code SoDuPhepService} lấy *đã tiêu* bằng một câu {@code SUM} lọc
     * {@code fromDate BETWEEN} rồi trừ đi *đang chờ* lấy bằng một câu khác lọc theo <b>chồng
     * khoảng</b> — hai vị từ khác nhau, và hiệu của chúng là một con số ⛔ không ai định nghĩa
     * được. Đó đúng hình dạng <b>quy tắc 13</b> (*một cột dẫn xuất trộn hai nguồn khác chiều lọc*)
     * mà lớp ấy đang viết javadoc để cảnh báo.
     *
     * <p>⇒ Một câu, một vị từ, chia nhóm ở Java.
     */
    @Query(
            """
            SELECT r FROM LeaveRequest r
            WHERE r.deletedAt IS NULL
              AND r.employeeId = :employeeId
              AND r.leaveType = :loai
              AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2', 'DA_DUYET')
              AND r.fromDate BETWEEN :dauNam AND :cuoiNam
            """)
    List<LeaveRequest> donChiemSoDuTrongNam(
            @Param("employeeId") Long employeeId,
            @Param("loai") LeaveType loai,
            @Param("dauNam") LocalDate dauNam,
            @Param("cuoiNam") LocalDate cuoiNam);

    /**
     * Đơn <b>còn hiệu lực</b> của một người chồng lên một khoảng ngày — chặn nộp trùng.
     *
     * <p>⛔ Hai đơn chồng ngày nhau là đếm <b>hai lần</b> cùng những ngày ấy vào số dư. Người nộp ⛔
     * không cố ý — họ chỉ sửa lại ngày rồi nộp đơn mới thay vì rút đơn cũ.
     */
    @Query(
            """
            SELECT r FROM LeaveRequest r
            WHERE r.deletedAt IS NULL
              AND r.employeeId = :employeeId
              AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2', 'DA_DUYET')
              AND r.fromDate <= :den AND r.toDate >= :tu
            """)
    List<LeaveRequest> donChongKhoang(
            @Param("employeeId") Long employeeId, @Param("tu") LocalDate tu, @Param("den") LocalDate den);

    /**
     * Số đơn <b>còn chờ quyết</b> của một đơn vị — chốt chặn giải thể đơn vị (CN-04.1).
     *
     * <p>⛔⛔ Chỉ đếm đơn <b>đang chờ</b>, ⛔ không đếm đơn đã quyết. Hai lý do ngược nhau và cả
     * hai đều cần:
     *
     * <ul>
     *   <li>Đơn <b>đang chờ</b> phải chặn: giải thể đơn vị là <b>xoá mất người duyệt</b> của nó, và
     *       người lao động ở lại với một đơn ⛔ không ai quyết được nữa — nó biến khỏi mọi hộp chờ
     *       vì bộ lọc phạm vi ⛔ không còn thấy đơn vị ấy.
     *   <li>Đơn <b>đã quyết</b> ⛔ không được chặn: nó là lịch sử, và chặn theo nó thì sau vài năm
     *       vận hành ⛔ không đơn vị nào giải thể được nữa — đúng lý lẽ đã dùng cho
     *       {@code maintenance_logs.performer_org_unit_id}.
     * </ul>
     */
    @Query(
            """
            SELECT count(r) FROM LeaveRequest r
            WHERE r.deletedAt IS NULL
              AND r.orgUnitId = :orgUnitId
              AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2')
            """)
    long demDonConChoCuaDonVi(@Param("orgUnitId") Long orgUnitId);

    /**
     * Số <b>người</b> của một đơn vị đang nghỉ chồng lên một khoảng — cảnh báo trùng lịch.
     *
     * <p>⚠ {@code COUNT(DISTINCT employeeId)} chứ ⛔ không đếm <i>đơn</i>: một người nộp ba đơn rời
     * nhau trong cùng tuần ⛔ không phải ba người vắng mặt. Đếm đơn là thổi phồng tỉ lệ rồi bắn
     * cảnh báo giả — và một cảnh báo giả lặp lại là cách dạy người dùng bỏ qua nó.
     */
    @Query(
            """
            SELECT COUNT(DISTINCT r.employeeId) FROM LeaveRequest r
            WHERE r.deletedAt IS NULL
              AND r.orgUnitId = :orgUnitId
              AND r.employeeId <> :tru
              AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2', 'DA_DUYET')
              AND r.fromDate <= :den AND r.toDate >= :tu
            """)
    long soNguoiNghiCungLuc(
            @Param("orgUnitId") Long orgUnitId,
            @Param("tru") Long truEmployeeId,
            @Param("tu") LocalDate tu,
            @Param("den") LocalDate den);

    /**
     * Mọi đơn của một đơn vị <b>chạm vào</b> một khoảng — lịch nghỉ đơn vị (CN-04.9, T57.18 vế b).
     *
     * <h2>⛔⛔ Vì sao là câu THỨ TƯ chứ ⛔ dùng lại một trong ba câu đã có</h2>
     *
     * <p>Sổ nợ khai <i>"dữ liệu đã đủ, chỉ thiếu màn hình"</i>. Đo lại (WS-81) thì cả ba câu
     * <i>gần giống</i> đều sai <b>đúng một vị từ</b>, và mỗi cái sai một kiểu khác nhau:
     *
     * <ul>
     *   <li>{@link #soNguoiNghiCungLuc} trả một {@code long} và <b>cố ý TRỪ người nộp</b>
     *       ({@code r.employeeId <> :tru}) — nó tồn tại để trả lời <i>"ngoài tôi ra còn mấy người"</i>.
     *       Vẽ lịch bằng nó cho ra một tháng chỉ có con số, và <b>thiếu đúng một người</b>.
     *   <li>{@link #donChongKhoang} lọc {@code r.employeeId = :employeeId} — một người.
     *   <li>{@link #hopChoDuyet} ⛔ lọc ngày và <b>bỏ {@code DA_DUYET}</b> ⇒ lịch sẽ thiếu đúng
     *       những người <b>chắc chắn</b> nghỉ.
     * </ul>
     *
     * <p>⚠ Vị từ chồng khoảng ({@code fromDate <= :den AND toDate >= :tu}) và bộ ba trạng thái ở
     * đây <b>trùng khít</b> {@link #soNguoiNghiCungLuc} — và phải thế: lịch và cảnh báo trùng lịch
     * đang trả lời <b>cùng một câu hỏi</b> ở hai màn hình. Lệch một vị từ là người nộp thấy cảnh
     * báo <i>"đã có 4/10 người nghỉ"</i> rồi mở lịch ra đếm được 3 (quy tắc 13).
     *
     * <p>⚠ {@code orgUnitId} nêu tường minh <b>bên cạnh</b> bộ lọc phạm vi, y như câu trên: người
     * xem chọn <b>một</b> đơn vị, còn bộ lọc chỉ nói đơn vị nào họ <i>được phép</i> chọn. Hai câu
     * hỏi khác nhau ⇒ hai điều kiện, và {@code ScopeGuard.requireReadableOrgUnit} nối chúng lại để
     * một mã đơn vị ngoài phạm vi ra <b>403</b> chứ ⛔ phải một lịch rỗng.
     */
    @Query(
            """
            SELECT r FROM LeaveRequest r
            WHERE r.deletedAt IS NULL
              AND r.orgUnitId = :orgUnitId
              AND r.state IN ('CHO_DUYET', 'CHO_DUYET_2', 'DA_DUYET')
              AND r.fromDate <= :den AND r.toDate >= :tu
            ORDER BY r.fromDate ASC, r.id ASC
            """)
    List<LeaveRequest> lichNghiCuaDonVi(
            @Param("orgUnitId") Long orgUnitId, @Param("tu") LocalDate tu, @Param("den") LocalDate den);
}
