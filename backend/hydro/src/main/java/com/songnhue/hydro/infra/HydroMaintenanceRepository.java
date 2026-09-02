package com.songnhue.hydro.infra;

import java.sql.Date;
import java.sql.Timestamp;
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
     * Dọn {@code sync_logs} cũ.
     *
     * <p>Không phân mảnh vì mỗi ngày chỉ ~720 dòng; {@code DELETE} theo mốc là đủ và không cần thêm
     * một cơ chế bảo trì thứ hai để quên.
     */
    public int purgeSyncLogsBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM sync_logs WHERE started_at < ?", Timestamp.from(cutoff));
    }

    /**
     * Dọn {@code hydro_unmapped_readings} cũ.
     *
     * <p>⚠ Dùng hạn lưu <b>của số đo</b> (5 năm), ⛔ không phải hạn lưu của raw (90 ngày): đây là số
     * đo thật của những trạm có thật, chỉ thiếu mỗi phần khai báo. Xoá chúng theo nhịp của raw là
     * vứt đúng thứ mà cả bảng này sinh ra để giữ — nguồn không có API lịch sử, nên số đo mất là mất
     * vĩnh viễn kể cả sau khi Công ty trả lời G8.
     */
    public int purgeUnmappedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM hydro_unmapped_readings WHERE measured_at < ?", Timestamp.from(cutoff));
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
