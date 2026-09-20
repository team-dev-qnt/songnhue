package com.songnhue.hr.infra;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.UyQuyenDuyetPhep;

/** Uỷ quyền duyệt nghỉ phép — chốt B3 (CN-04.9). */
public interface UyQuyenDuyetPhepRepository extends JpaRepository<UyQuyenDuyetPhep, Long> {

    Optional<UyQuyenDuyetPhep> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * Danh sách của một đơn vị — màn hình quản lý.
     *
     * <p>⚠ Giữ cả bản <b>đã thu hồi</b> và bản đã hết hạn: một lượt duyệt tháng trước trỏ vào
     * chúng, và lịch sử phê duyệt phải đọc lại được. Đường này đi qua bộ lọc phạm vi, đúng ý.
     */
    @Query(
            """
            SELECT u FROM UyQuyenDuyetPhep u
            WHERE u.deletedAt IS NULL AND u.orgUnitId = :orgUnitId
            ORDER BY u.tuNgay DESC, u.id DESC
            """)
    List<UyQuyenDuyetPhep> cuaDonVi(@Param("orgUnitId") Long orgUnitId);

    /**
     * Lượt uỷ quyền <b>đang dùng được</b> của một người trên một <b>chuỗi đơn vị</b> — nguồn của câu
     * audit <i>"duyệt theo uỷ quyền của X"</i> (B3), và cũng là vế quyết định của cổng duyệt.
     *
     * <h2>⛔⛔ Vì sao NATIVE, ⛔ phải JPQL trên entity</h2>
     *
     * <p>JPQL trên {@link UyQuyenDuyetPhep} sẽ nhận {@code @Filter} phạm vi, mà bộ lọc ấy cắt theo
     * đơn vị của <b>người đang đăng nhập</b>. Người được uỷ quyền có thể đứng ở một đơn vị <b>⛔
     * phủ</b> đơn vị được uỷ quyền — đó đúng là công dụng của uỷ quyền. Đi qua bộ lọc thì câu này
     * <b>lặng lẽ</b> trả rỗng, và triệu chứng là <i>nút Duyệt ⛔ hiện, ⛔ một dòng lỗi</i>.
     *
     * <p>⚠ {@code ORDER BY} để hai lượt uỷ quyền chồng nhau cho ra kết quả <b>xác định</b>: lấy
     * lượt giao gần nhất. ⛔ Có nó thì cùng một dữ liệu cho hai câu audit khác nhau tuỳ kế hoạch
     * truy vấn.
     *
     * @param chuoiDonVi đơn vị của đơn <b>và mọi đơn vị cha</b> — {@code OrgUnitPort.chuoiDonViLen}
     * @return {@code null} khi ⛔ có lượt nào — nơi gọi phải phân biệt được <i>⛔ được uỷ quyền</i>
     *     với <i>được uỷ quyền nhưng đã hết hạn</i> (hai câu trả lời, hai việc phải làm)
     */
    @Query(
            value =
                    """
            SELECT d.id
              FROM leave_approval_delegations d
             WHERE d.delegate_user_id = :userId
               AND d.org_unit_id IN (:chuoiDonVi)
               AND d.deleted_at IS NULL
               AND d.revoked_at IS NULL
               AND :ngay BETWEEN d.from_date AND d.to_date
             ORDER BY d.from_date DESC, d.id DESC
             LIMIT 1
            """,
            nativeQuery = true)
    Long idUyQuyenDangDung(
            @Param("userId") Long userId,
            @Param("chuoiDonVi") Collection<Long> chuoiDonVi,
            @Param("ngay") LocalDate ngay);

    /**
     * Số lượt uỷ quyền <b>còn hiệu lực</b> của một đơn vị — chốt chặn giải thể đơn vị (CN-04.1).
     *
     * <p>⛔ Chỉ đếm bản <b>đang chạy</b>, ⛔ đếm bản đã thu hồi / đã hết hạn: cùng lý lẽ mà
     * {@code demDonConChoCuaDonVi} đã ghi — bản đã xong là <b>lịch sử</b>, và chặn theo nó thì sau
     * vài năm ⛔ đơn vị nào giải thể được nữa.
     *
     * <p>⚠⚠ <b>Native</b>, cố ý: JPQL trên entity sẽ nhận {@code @Filter} phạm vi. Nơi gọi là
     * {@code core} giữa một lượt <b>giải thể</b>, và một phép đếm bị lọc hụt ở đây ⇒ chốt chặn nói
     * <i>"⛔ ai thuộc đơn vị này"</i> trong khi vẫn còn — tức nó xanh ở đúng ca nó sinh ra để bắt.
     */
    @Query(
            value =
                    """
            SELECT count(*)
              FROM leave_approval_delegations d
             WHERE d.org_unit_id = :orgUnitId
               AND d.deleted_at IS NULL
               AND d.revoked_at IS NULL
               AND :ngay BETWEEN d.from_date AND d.to_date
            """,
            nativeQuery = true)
    long demUyQuyenConHieuLuc(@Param("orgUnitId") Long orgUnitId, @Param("ngay") LocalDate ngay);
}
