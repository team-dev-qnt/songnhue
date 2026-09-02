package com.songnhue.hydro.infra;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Gọi các hàm bảo trì time-series đã cài trong CSDL ({@code V202609011052}).
 *
 * <p>⚠ Lớp này <b>không tự viết DDL bằng Java</b>. Việc tạo và xoá partition sống trong hai hàm
 * plpgsql, vì chúng phải làm ba việc mà mã ứng dụng làm được nhưng làm sai thì rất khó thấy: siết
 * quyền trên từng partition mới (quyền <b>không</b> kế thừa từ bảng cha khi truy vấn thẳng vào
 * partition), xử lý đúng tình huống partition {@code DEFAULT} đang giữ bản ghi của tháng cần tạo,
 * và chặn mọi mốc cắt quá gần hiện tại. Cùng lý lẽ với {@code AuditMaintenanceRepository}: công
 * thức nằm ở một nơi, lớp Java chỉ gọi.
 */
@Repository
public class HydroMaintenanceRepository {

    /**
     * Hai bảng phân mảnh — <b>danh sách trắng đối xứng</b> với danh sách trong hai hàm plpgsql.
     *
     * <p>Tên bảng ở đây đi thẳng vào tham số của hàm, và hàm xoá partition chạy
     * {@code SECURITY DEFINER}. Kiểm ở cả hai phía là cố ý: phía CSDL chặn mọi đường gọi kể cả
     * đường chưa ai nghĩ tới, phía Java cho lỗi ở đúng dòng mã sai.
     */
    private static final Set<String> BANG_PHAN_MANH = Set.of("hydro_raw_logs", "hydro_readings");

    private final JdbcTemplate jdbc;

    public HydroMaintenanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tạo trước partition cho {@code monthsAhead} tháng tới, cho <b>cả hai</b> bảng.
     *
     * <p>Idempotent: gọi lại không tạo thêm. Hết runway thì bản ghi rơi vào partition
     * {@code DEFAULT} — vẫn ghi được, nhưng đó là dấu hiệu job này đã chết.
     *
     * @return số partition vừa tạo thêm; {@code 0} nghĩa là đã đủ, ⛔ không phải lỗi
     */
    public int ensurePartitions(int monthsAhead) {
        Integer created =
                jdbc.queryForObject("SELECT hyd_ensure_time_series_partitions(?)", Integer.class, monthsAhead);
        return created == null ? 0 : created;
    }

    /**
     * Số bản ghi lọt vào partition {@code DEFAULT} — bình thường phải luôn bằng 0.
     *
     * <p>⚠ Đây là <b>chỉ số duy nhất</b> cho biết job bảo trì partition đã chết: mọi thứ khác vẫn
     * chạy bình thường, dữ liệu vẫn ghi được, không có lỗi nào. Cùng cơ chế lưới an toàn của
     * {@code audit_logs_default}.
     */
    public long countInDefaultPartition(String table) {
        kiemTenBang(table);
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + "_default", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Xoá hẳn các partition tháng đã nằm trọn trước {@code cutoff}.
     *
     * <p>⛔ Không phục hồi được. Hàm trong CSDL có <b>sàn an toàn 7 ngày</b> và sẽ ném ngoại lệ nếu
     * mốc cắt mới hơn thế — một lỗi đơn vị (ngày ↔ tháng) ở đây khi ấy chỉ làm job đỏ, chứ không xoá
     * mất dữ liệu của tuần này.
     *
     * <p>⚠ Vì xoá theo <b>tháng trọn vẹn</b>, hạn lưu thực tế luôn <i>dài hơn</i> con số cấu hình —
     * tối đa thêm một tháng. Đó là đánh đổi có chủ đích: {@code DROP TABLE} là O(1) và không để lại
     * bloat, còn {@code DELETE} hàng triệu dòng rồi chờ autovacuum là cách chắc chắn để một job dọn
     * dẹp trở thành một sự cố hiệu năng.
     *
     * @return số partition đã xoá
     */
    public int dropPartitionsBefore(String table, LocalDate cutoff) {
        kiemTenBang(table);
        Integer dropped = jdbc.queryForObject(
                "SELECT hyd_drop_month_partitions_before(?, ?)", Integer.class, table, Date.valueOf(cutoff));
        return dropped == null ? 0 : dropped;
    }

    /**
     * ⭐⭐ "Hôm nay" theo <b>chính phiên CSDL này</b> — ⛔ không phải theo đồng hồ JVM.
     *
     * <h2>Khuyết tật thật, đo được 02/09/2026 — vì sao hàm này tồn tại</h2>
     *
     * <p>Bản đầu của {@code HydroRetentionHandler} tính mốc cắt bằng
     * {@code LocalDate.now(Clock.system(ZONE_VN))}, trong khi sàn an toàn của
     * {@code hyd_drop_month_partitions_before} so với {@code current_date} của phiên CSDL. Hai thứ
     * ấy <b>không cùng một ngày</b>: container ứng dụng chạy {@code -Duser.timezone=UTC}
     * ({@code compose.prod.yml:326} và {@code backend.Dockerfile:81}), pgjdbc gửi múi giờ mặc định
     * của JVM làm múi giờ phiên, còn job chạy lúc <b>04:30 giờ VN = 21:30 UTC ngày HÔM TRƯỚC</b>.
     *
     * <p>Nên {@code homNay} phía Java luôn bằng {@code current_date + 1} — <b>tất định, mọi đêm</b>,
     * không phải một cuộc đua hiếm (Việt Nam không đổi giờ). Hệ quả đo được: đặt
     * {@code hydro.raw-retention-days = 7} — đúng biên dưới mà chính migration khai là hợp lệ —
     * cho ra mốc cắt {@code current_date - 6}, sàn từ chối, job đỏ. Và vì đó là lời gọi <b>đầu
     * tiên</b> của handler, toàn bộ phần dọn còn lại không chạy.
     *
     * <p>⇒ Một quyết định, một cái đồng hồ. Mốc cắt và sàn an toàn phải đọc cùng một
     * {@code current_date}, và cách rẻ nhất để bảo đảm điều đó là hỏi đúng nơi đang phán xét.
     */
    public LocalDate ngayHienTai() {
        return jdbc.queryForObject("SELECT current_date", LocalDate.class);
    }

    /**
     * Dọn {@code sync_logs} cũ.
     *
     * <p>Không phân mảnh vì mỗi ngày chỉ ~720 dòng; {@code DELETE} theo mốc là đủ và không cần thêm
     * một cơ chế bảo trì thứ hai để quên.
     *
     * <p>⚠ Nhận {@link LocalDate}, ⛔ không nhận {@link Instant}: quy đổi ngày → khoảnh khắc phía
     * Java là mời lại đúng cái lệch múi giờ mà {@link #ngayHienTai()} vừa gỡ. Để CSDL tự quy đổi
     * bằng múi giờ của chính phiên nó.
     */
    public int purgeSyncLogsBefore(LocalDate cutoff) {
        return jdbc.update("DELETE FROM sync_logs WHERE started_at < ?", Date.valueOf(cutoff));
    }

    /**
     * Dọn {@code hydro_unmapped_readings} cũ.
     *
     * <p>⚠ Dùng hạn lưu <b>của số đo</b> (5 năm), ⛔ không phải hạn lưu của raw (90 ngày): đây là số
     * đo thật của những trạm có thật, chỉ thiếu mỗi phần khai báo. Xoá chúng theo nhịp của raw là
     * vứt đúng thứ mà cả bảng này sinh ra để giữ — nguồn không có API lịch sử, nên số đo mất là mất
     * vĩnh viễn kể cả sau khi Công ty trả lời G8.
     */
    public int purgeUnmappedBefore(LocalDate cutoff) {
        return jdbc.update("DELETE FROM hydro_unmapped_readings WHERE measured_at < ?", Date.valueOf(cutoff));
    }

    /** Tên partition tháng hiện có của một bảng — dùng cho bài kiểm và cho màn hình bảo trì. */
    public List<String> partitionNames(String table) {
        kiemTenBang(table);
        return jdbc.queryForList(
                """
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname ~ ('^' || ? || '_p[0-9]{6}$')
                 ORDER BY c.relname
                """,
                String.class, table);
    }

    private static void kiemTenBang(String table) {
        if (!BANG_PHAN_MANH.contains(table)) {
            throw new IllegalArgumentException(
                    "Bảng " + table + " không phải bảng phân mảnh của hydro — chỉ nhận " + BANG_PHAN_MANH);
        }
    }
}
