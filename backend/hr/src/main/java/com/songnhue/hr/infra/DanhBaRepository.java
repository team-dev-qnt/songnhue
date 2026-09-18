package com.songnhue.hr.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hr.application.DanhBaLoc;
import com.songnhue.hr.application.DanhBaMuc;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;

/**
 * Truy vấn danh bạ nội bộ — CN-04.6.
 *
 * <h2>⛔⛔ Vì sao SQL tay chứ ⛔ KHÔNG phải JPA — và đây là quyết định về BẢO MẬT, ⛔ không về tốc độ</h2>
 *
 * <p>Hai tính chất mà một truy vấn JPA trên {@code Employee} ⛔ <b>không</b> cho được ở đây:
 *
 * <ol>
 *   <li><b>Danh bạ phải thấy TOÀN Công ty.</b> {@code ScopeFilterAspect} bật
 *       {@code @Filter} phạm vi <b>quanh mọi</b> {@code @Transactional}, nên mọi truy vấn JPA trên
 *       {@code Employee} đều bị cắt theo đơn vị của người gọi. Một cuốn danh bạ chỉ thấy đơn vị
 *       mình là một cuốn danh bạ vô dụng — và quyền {@code hr:directory:view} được cấp cho
 *       <b>11/12</b> vai trò, tức đặc tả rõ ràng muốn ai cũng tra được. ⛔ Đường vòng <b>sai</b> là
 *       gọi {@code session.disableFilter(…)}: nó tắt cơ chế bảo vệ phạm vi cho <b>cả giao dịch</b>,
 *       và {@code ScopeGuard} đã ghi rằng bỏ sót lượt bật lại là biến lớp bảo vệ thành lớp chọc
 *       thủng. ⇒ Đi một đường <b>⛔ không có</b> cơ chế ấy: JDBC thuần.
 *   <li><b>⛔ Không thể vô tình mang theo trường nhạy cảm.</b> Danh sách cột ở đây viết <b>bằng
 *       tay</b>, nên một trường mới thêm vào {@code Employee} ⛔ không tự chảy ra danh bạ. Với
 *       {@code SELECT e} của JPA thì điều ngược lại đúng: lớp phòng thủ duy nhất là người dựng DTO
 *       nhớ ⛔ không lấy — đúng thứ quy tắc 5 cấm.
 * </ol>
 *
 * <p>⚠ Cái giá phải khai ra: mất kiểm tra kiểu lúc biên dịch. Bù lại bằng
 * {@code DanhBaKhongLoDuLieuCaNhanTest} (đọc {@code getRecordComponents}) và bằng
 * {@code DanhBaHttpTest} đo trên thân JSON thật.
 *
 * <h2>Lọc đơn vị khớp CẢ NHÁNH CON</h2>
 *
 * <p>Chọn <i>Xí nghiệp 3</i> phải ra mọi người thuộc Xí nghiệp 3 <b>và các Tổ đội của nó</b>. Khớp
 * đúng {@code org_unit_id} thì người dùng chọn một Xí nghiệp và thấy <b>1 người</b> (ông trưởng
 * đơn vị) rồi tin rằng đơn vị ấy có một người — một câu trả lời <b>sai mà im lặng</b>. ⇒ So theo
 * <i>materialized path</i>, chạy trên {@code ix_org_units_path}.
 */
@Repository
public class DanhBaRepository {

    /**
     * ⛔ Danh sách cột viết TAY — xem javadoc lớp. Thêm một dòng vào đây là <b>công bố</b> cột ấy
     * cho toàn Công ty.
     */
    private static final String CHON =
            """
            SELECT e.public_id, e.code, e.full_name, e.gender, e.phone, e.work_email, e.job_title,
                   p.name AS position_name, ou.public_id AS org_unit_public_id, ou.name AS org_unit_name
            """;

    private static final String TU =
            """
              FROM employees e
              JOIN org_units ou ON ou.id = e.org_unit_id
              LEFT JOIN positions p ON p.id = e.position_id
            """;

    /**
     * Vế <i>"còn làm việc"</i> — <b>một</b> chuỗi cho cả ba câu truy vấn của lớp này.
     *
     * <p>⛔⛔ Câu *"chỉ NV 'Đang làm'"* của đặc tả nghĩa là <b>đối lập với ĐÃ NGHỈ</b>, ⛔ không
     * phải {@code status = 'DANG_LAM'}: người đang nghỉ thai sản hay nghỉ ⛔ không lương vẫn là
     * người của Công ty và vẫn có số điện thoại. Loại họ khỏi danh bạ là một quyết định nhân sự ⛔
     * không ai duyệt. Tập giá trị suy từ {@link EmploymentStatus#daNghi()}, ⛔ không liệt kê tay.
     *
     * <p>⚠ Ba câu cùng cần vế này (danh sách · chi tiết · đồng nghiệp). Chép nó ba lần là ba chỗ
     * phải nhớ sửa cùng lúc — và triệu chứng khi quên là <b>một người đã nghỉ việc vẫn mở được
     * bằng liên kết cũ</b> dù ⛔ không còn trong danh sách (luật 14).
     */
    private static final String CON_LAM_VIEC = " e.deleted_at IS NULL AND e.status NOT IN (:daNghi) ";

    private final NamedParameterJdbcTemplate jdbc;

    public DanhBaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Một trang danh bạ, sắp theo họ tên (đối chiếu ICU {@code vi-VN} của CSDL). */
    public List<DanhBaMuc> timTrang(DanhBaLoc loc, int trang, int co) {
        MapSqlParameterSource thamSo = new MapSqlParameterSource();
        String dieuKien = dieuKien(loc, thamSo);
        thamSo.addValue("gioiHan", co).addValue("boQua", (long) trang * co);

        return jdbc.query(
                CHON + TU + dieuKien + " ORDER BY e.full_name, e.code LIMIT :gioiHan OFFSET :boQua",
                thamSo,
                DanhBaRepository::doc);
    }

    public long dem(DanhBaLoc loc) {
        MapSqlParameterSource thamSo = new MapSqlParameterSource();
        String dieuKien = dieuKien(loc, thamSo);
        Long so = jdbc.queryForObject("SELECT count(*)" + TU + dieuKien, thamSo, Long.class);
        return so == null ? 0L : so;
    }

    /**
     * Một người theo {@code publicId} — <b>mang đúng vế {@link #CON_LAM_VIEC}</b>.
     *
     * <p>⛔ Thiếu vế ấy thì một người vừa nghỉ việc biến khỏi danh sách nhưng <b>vẫn mở được bằng
     * liên kết cũ</b> — nửa cặp đọc–ghi ở dạng khó thấy nhất, vì màn hình danh sách trông đúng.
     */
    public java.util.Optional<DanhBaMuc> timTheoPublicId(UUID publicId) {
        MapSqlParameterSource thamSo = new MapSqlParameterSource()
                .addValue("id", publicId)
                .addValue("daNghi", EmploymentStatus.tenCacTrangThaiDaNghi());
        return jdbc
                .query(CHON + TU + " WHERE " + CON_LAM_VIEC + " AND e.public_id = :id", thamSo, DanhBaRepository::doc)
                .stream()
                .findFirst();
    }

    /** Đường dẫn đơn vị dạng bánh mì — <i>"vị trí trên sơ đồ"</i> mà đặc tả đòi ở màn hình chi tiết. */
    public List<String> duongDanDonVi(UUID employeePublicId) {
        return jdbc.queryForList(
                """
                SELECT cha.name
                  FROM employees e
                  JOIN org_units ou ON ou.id = e.org_unit_id
                  JOIN org_units cha ON ou.path LIKE cha.path || '%'
                 WHERE e.public_id = :id
                 ORDER BY cha.depth
                """,
                new MapSqlParameterSource("id", employeePublicId), String.class);
    }

    /**
     * Đồng nghiệp cùng đơn vị — <b>đúng đơn vị</b>, ⛔ không cả nhánh.
     *
     * <p>Khác chủ ý với bộ lọc đơn vị ở trên: <i>"đồng nghiệp cùng đơn vị"</i> nghĩa là người ngồi
     * cùng phòng, ⛔ không phải toàn bộ Xí nghiệp. Hai câu hỏi khác nhau thì hai phép so khác nhau.
     */
    public List<DanhBaMuc> dongNghiepCungDonVi(UUID employeePublicId, int gioiHan) {
        MapSqlParameterSource thamSo = new MapSqlParameterSource()
                .addValue("id", employeePublicId)
                .addValue("daNghi", EmploymentStatus.tenCacTrangThaiDaNghi())
                .addValue("gioiHan", gioiHan);

        return jdbc.query(
                CHON + TU + " WHERE " + CON_LAM_VIEC
                        + """
                           AND e.public_id <> :id
                           AND e.org_unit_id = (SELECT x.org_unit_id FROM employees x
                                                 WHERE x.public_id = :id)
                         ORDER BY e.full_name, e.code LIMIT :gioiHan
                        """,
                thamSo,
                DanhBaRepository::doc);
    }

    // -------------------------------------------------------------------------

    /**
     * Bộ đọc dòng — <b>một</b> nơi cho cả ba câu truy vấn.
     *
     * <p>Bản đầu chép nó ba lần. Ba bản chép của một phép ánh xạ 10 cột là ba chỗ phải nhớ sửa khi
     * {@link DanhBaMuc} đổi — và trình biên dịch <b>có</b> bắt được ở đây (record là kiểu), nhưng
     * thứ nó ⛔ không bắt được là hai bản chép đọc <b>khác cột</b> mà vẫn đủ tham số.
     */
    private static DanhBaMuc doc(java.sql.ResultSet rs, int dong) throws java.sql.SQLException {
        return new DanhBaMuc(
                UUID.fromString(rs.getString("public_id")),
                rs.getString("code"),
                rs.getString("full_name"),
                rs.getString("gender"),
                rs.getString("phone"),
                rs.getString("work_email"),
                rs.getString("job_title"),
                rs.getString("position_name"),
                UUID.fromString(rs.getString("org_unit_public_id")),
                rs.getString("org_unit_name"));
    }

    /**
     * Dựng mệnh đề {@code WHERE} — <b>mọi giá trị đi bằng tham số</b>, mã chỉ chọn <i>mảnh câu</i>.
     *
     * <p>⛔ ⛔ Không một ký tự nào của người dùng được nối vào chuỗi SQL. Đó là ràng buộc bắt buộc
     * của mọi câu SQL dựng động, và nó phải đọc ra được từ chính hình dạng hàm này.
     */
    private String dieuKien(DanhBaLoc loc, MapSqlParameterSource thamSo) {
        List<String> ve = new ArrayList<>();
        ve.add(CON_LAM_VIEC);
        thamSo.addValue("daNghi", EmploymentStatus.tenCacTrangThaiDaNghi());

        if (loc.tuKhoa() != null) {
            ve.add(
                    """
                   (sn_khong_dau(e.full_name) LIKE sn_khong_dau(:tuKhoa)
                     OR sn_khong_dau(e.code) LIKE sn_khong_dau(:tuKhoa)
                     OR sn_khong_dau(COALESCE(e.job_title, '')) LIKE sn_khong_dau(:tuKhoa))
                   """);
            thamSo.addValue("tuKhoa", "%" + loc.tuKhoa() + "%");
        }
        if (!loc.donViIds().isEmpty()) {
            // Khớp CẢ NHÁNH CON — xem javadoc lớp.
            ve.add(
                    """
                   EXISTS (SELECT 1 FROM org_units chon
                            WHERE chon.public_id IN (:donViIds) AND ou.path LIKE chon.path || '%')
                   """);
            thamSo.addValue("donViIds", loc.donViIds());
        }
        if (!loc.chucVuIds().isEmpty()) {
            ve.add("p.public_id IN (:chucVuIds)");
            thamSo.addValue("chucVuIds", loc.chucVuIds());
        }
        if (!loc.gioiTinh().isEmpty()) {
            ve.add("e.gender IN (:gioiTinh)");
            thamSo.addValue(
                    "gioiTinh", loc.gioiTinh().stream().map(Gender::name).toList());
        }
        return " WHERE " + String.join(" AND ", ve);
    }
}
