package com.songnhue.hr.infra;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hr.domain.EmploymentStatus;

/**
 * Quân số theo đơn vị — CN-04.1 (sơ đồ tổ chức) và CN-04.8 (báo cáo nhân sự).
 *
 * <h2>⛔⛔ SQL tay, và đây là quyết định về PHẠM VI chứ ⛔ không về tốc độ</h2>
 *
 * <p>Cùng lý lẽ với {@link DanhBaRepository}: {@code ScopeFilterAspect} bật {@code @Filter} quanh
 * <b>mọi</b> {@code @Transactional}, nên một câu JPA trên {@code Employee} sẽ đếm <b>cắt theo đơn
 * vị của người đang xem</b>. Hệ quả trên một sơ đồ tổ chức là thứ nguy hiểm nhất: mỗi người mở ra
 * thấy <b>một bộ số khác nhau</b>, tất cả đều trông hợp lý, và ⛔ không có gì báo. Trưởng Xí nghiệp
 * 3 sẽ thấy toàn Công ty có đúng số người của Xí nghiệp 3.
 *
 * <p>⚠ Đây <b>⛔ không</b> phải nới lỏng bảo mật: quân số của một phòng ban ⛔ không phải dữ liệu
 * nhạy cảm, và CN-04.6 đã công bố <b>danh sách tên</b> của cả Công ty cho 11/12 vai trò. Một con số
 * tổng thì hẹp hơn thế. Điều phải khai ra là <b>nó cố ý toàn Công ty</b>, ⛔ không phải nó quên bị
 * cắt.
 *
 * <h2>⛔ "Còn làm việc" ⛔ KHÔNG phải {@code status = 'DANG_LAM'}</h2>
 *
 * <p>Người thử việc, nghỉ thai sản, nghỉ ⛔ không lương <b>vẫn là quân số</b> của đơn vị. Đối lập
 * của *"còn làm"* là *"đã nghỉ"*, ⛔ không phải *"⛔ không đang làm"* — T55.2 đã trả giá cho đúng
 * câu này ở danh bạ, và một sơ đồ tổ chức đếm thiếu người nghỉ thai sản là một sơ đồ **sai mà ⛔
 * không ai đếm lại**. ⇒ Vị từ suy từ {@link EmploymentStatus#daNghi()}.
 */
@Repository
public class QuanSoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public QuanSoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Số người <b>còn làm việc</b> của từng đơn vị — khớp ĐÚNG {@code org_unit_id}, ⛔ không cộng
     * dồn nhánh con.
     *
     * <p>⚠ Cộng dồn nhánh con là việc của tầng dựng cây ({@code SoDoToChucService}), ⛔ không phải
     * của SQL: cây đã nằm trong bộ nhớ ở đó, và một câu SQL đệ quy sẽ trả lời một câu hỏi khác nếu
     * ai đó lọc bớt nhánh trước khi vẽ.
     *
     * @return chỉ chứa đơn vị <b>có ít nhất một người</b> — bên gọi tự rơi về 0
     */
    public Map<Long, Long> demTheoDonVi() {
        Map<Long, Long> ket = new HashMap<>();
        jdbc.query(
                """
                SELECT e.org_unit_id AS don_vi, count(*) AS so_nguoi
                FROM employees e
                WHERE e.deleted_at IS NULL AND e.status NOT IN (:daNghi)
                GROUP BY e.org_unit_id
                """,
                new MapSqlParameterSource("daNghi", EmploymentStatus.tenCacTrangThaiDaNghi()),
                rs -> {
                    ket.put(rs.getLong("don_vi"), rs.getLong("so_nguoi"));
                });
        return ket;
    }

    /**
     * Tổng số người còn làm việc <b>⛔ không nằm trong danh sách đơn vị đã cho</b>.
     *
     * <h2>⛔⛔ Vì sao con số này phải ra tới màn hình</h2>
     *
     * <p>Bình thường nó là <b>0</b>. Khác 0 nghĩa là có hồ sơ trỏ vào một đơn vị đã bị xoá mềm —
     * đúng khuyết tật mà WS-56 phải dựng chốt chặn `ADM-2004` để ngăn. Triệu chứng của nó **im
     * lặng tuyệt đối**: sơ đồ vẫn vẽ đẹp, tổng quân số vẫn ra một số, chỉ là số ấy **thiếu** đúng
     * những người đó — và ⛔ không ai đếm lại bằng tay để phát hiện.
     *
     * <p>⇒ Quy tắc 16: một con số ⛔ không đi một mình. Sơ đồ trả kèm nó, và giao diện chỉ nói ra
     * khi nó khác 0.
     *
     * @param donViCoTrongSoDo id các đơn vị đang có mặt trên sơ đồ; rỗng ⇒ đếm toàn bộ
     */
    public long demNgoaiSoDo(List<Long> donViCoTrongSoDo) {
        if (donViCoTrongSoDo == null || donViCoTrongSoDo.isEmpty()) {
            return demTheoDonVi().values().stream().mapToLong(Long::longValue).sum();
        }
        Long so = jdbc.queryForObject(
                """
                SELECT count(*) FROM employees e
                WHERE e.deleted_at IS NULL AND e.status NOT IN (:daNghi)
                  AND e.org_unit_id NOT IN (:donVi)
                """,
                new MapSqlParameterSource("daNghi", EmploymentStatus.tenCacTrangThaiDaNghi())
                        .addValue("donVi", donViCoTrongSoDo),
                Long.class);
        return so == null ? 0L : so;
    }
}
