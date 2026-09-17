package com.songnhue.core.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.spi.MaHoaLaiPort;

/**
 * Hiện thực {@link MaHoaLaiPort} cho một bảng bằng JDBC — module chỉ việc KHAI cột. T61.11.
 *
 * <h2>Vì sao JDBC, ⛔ entity</h2>
 *
 * <ul>
 *   <li>Mã hoá lại là một lượt <b>kỹ thuật</b>, ⛔ phải một thay đổi nghiệp vụ: đi qua entity là sinh
 *       một dòng {@code audit_logs} rỗng cho mỗi hàng (các cột 🔒 đều nằm trong {@code excludeFields}),
 *       và đi qua bộ lọc phạm vi đơn vị — thứ job ⛔ có người dùng nào để suy ra.
 *   <li>⛔ Tăng {@code version}: làm thế là biến lượt đăng nhập TOTP hay lượt poll thuỷ văn đang chạy
 *       song song thành một {@code OptimisticLockException} — mà poll hỏng là mất số liệu vĩnh viễn
 *       (quy tắc 18). Đổi lại, một lượt lưu đè bằng entity cũ có thể ghi lại bản mã khoá cũ — vô hại
 *       (khoá cũ vẫn đang nạp) và vòng kế của job đếm lại nó.
 * </ul>
 *
 * <p>⚠ Tên bảng/cột là HẰNG do module khai trong mã, ⛔ bao giờ đến từ người dùng.
 */
public final class MaHoaLaiJdbc implements MaHoaLaiPort {

    private static final Logger log = LoggerFactory.getLogger(MaHoaLaiJdbc.class);

    private final String bang;
    private final List<String> cotBanMa;
    private final String cotVanTay;
    private final String cotNguonVanTay;
    private final String cotKhoaId;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate giaoDich;
    private final CryptoService crypto;

    /**
     * @param cotBanMa mọi cột bản mã {@code <key_id>:<base64>}
     * @param cotVanTay cột vân tay {@code <key_id>:<hex>}, hoặc {@code null}
     * @param cotNguonVanTay cột bản mã mà vân tay tính từ bản rõ của nó (phải nằm trong {@code cotBanMa})
     * @param cotKhoaId cột ghi riêng {@code key_id} (chỉ {@code user_totp} — cột chết, T51.0), hoặc {@code null}
     */
    public MaHoaLaiJdbc(
            String bang,
            List<String> cotBanMa,
            String cotVanTay,
            String cotNguonVanTay,
            String cotKhoaId,
            JdbcTemplate jdbc,
            PlatformTransactionManager tx,
            CryptoService crypto) {
        if (cotBanMa.isEmpty() || (cotVanTay != null && !cotBanMa.contains(cotNguonVanTay))) {
            throw new IllegalArgumentException("Khai cột mã hoá lại của " + bang + " sai");
        }
        this.bang = bang;
        this.cotBanMa = List.copyOf(cotBanMa);
        this.cotVanTay = cotVanTay;
        this.cotNguonVanTay = cotNguonVanTay;
        this.cotKhoaId = cotKhoaId;
        this.jdbc = jdbc;
        this.giaoDich = new TransactionTemplate(tx);
        this.giaoDich.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.crypto = crypto;
    }

    @Override
    public String bang() {
        return bang;
    }

    @Override
    public List<String> cot() {
        return tatCaCot();
    }

    @Override
    public long demConKhoaCu(String khoaDangDung) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM " + bang + " WHERE " + dieuKienKhoaCu(), Long.class, thamSoKhoa(khoaDangDung));
        return n == null ? 0 : n;
    }

    @Override
    public Lo maHoaLai(String khoaDangDung, long sauId, int toiDa) {
        List<String> cot = tatCaCot();
        Object[] thamSo = new Object[soCotCoKhoa() + 2];
        thamSo[0] = sauId;
        System.arraycopy(thamSoKhoa(khoaDangDung), 0, thamSo, 1, soCotCoKhoa());
        thamSo[thamSo.length - 1] = toiDa;

        List<Object[]> hang = jdbc.query(
                "SELECT id, " + String.join(", ", cot) + " FROM " + bang + " WHERE id > ? AND (" + dieuKienKhoaCu()
                        + ") ORDER BY id LIMIT ?",
                (rs, i) -> {
                    Object[] h = new Object[cot.size() + 1];
                    h[0] = rs.getLong(1);
                    for (int c = 0; c < cot.size(); c++) {
                        h[c + 1] = rs.getString(c + 2);
                    }
                    return h;
                },
                thamSo);

        int doi = 0;
        int hong = 0;
        long idCuoi = sauId;
        for (Object[] h : hang) {
            long id = (Long) h[0];
            idCuoi = Math.max(idCuoi, id);
            try {
                Integer n = giaoDich.execute(s -> ghiLai(id, cot, h));
                if (n != null && n == 1) {
                    doi++;
                }
                // n == 0 ⇒ có người lưu đè giữa chừng ⇒ không tính là hỏng; vòng sau của job đếm lại nó.
            } catch (RuntimeException e) {
                hong++;
                // ⛔ Không in e.getMessage(): lỗi ràng buộc duy nhất của Postgres in nguyên GIÁ TRỊ vân tay.
                log.error(
                        "Mã hoá lại {}#{} không được — {}",
                        bang,
                        id,
                        e.getClass().getSimpleName());
            }
        }
        return new Lo(hang.size(), doi, hong, idCuoi);
    }

    private int ghiLai(long id, List<String> cot, Object[] h) {
        List<Object> thamSo = new ArrayList<>();
        StringBuilder set = new StringBuilder();
        for (int c = 0; c < cotBanMa.size(); c++) {
            thamSo.add(crypto.reEncrypt((String) h[c + 1]));
            set.append(c == 0 ? "" : ", ").append(cotBanMa.get(c)).append(" = ?");
        }
        if (cotVanTay != null) {
            String banMaNguon = (String) h[cot.indexOf(cotNguonVanTay) + 1];
            thamSo.add(banMaNguon == null ? null : crypto.fingerprint(crypto.decrypt(banMaNguon)));
            set.append(", ").append(cotVanTay).append(" = ?");
        }
        if (cotKhoaId != null) {
            thamSo.add(crypto.keyIdOf((String) thamSo.get(0)));
            set.append(", ").append(cotKhoaId).append(" = ?");
        }
        thamSo.add(id);
        StringBuilder where = new StringBuilder(" WHERE id = ?");
        for (int c = 0; c < cot.size(); c++) {
            // Ghi có điều kiện: người dùng lưu đè giữa lượt đọc và lượt ghi ⇒ trúng 0 hàng, ⛔ đè dữ liệu mới.
            where.append(" AND ").append(cot.get(c)).append(" IS NOT DISTINCT FROM ?");
            thamSo.add(h[c + 1]);
        }
        return jdbc.update("UPDATE " + bang + " SET " + set + where, thamSo.toArray());
    }

    private List<String> tatCaCot() {
        List<String> cot = new ArrayList<>(cotBanMa);
        if (cotVanTay != null) {
            cot.add(cotVanTay);
        }
        return cot;
    }

    private int soCotCoKhoa() {
        return tatCaCot().size();
    }

    private String dieuKienKhoaCu() {
        return tatCaCot().stream()
                .map(c -> "(" + c + " IS NOT NULL AND split_part(" + c + ", ':', 1) <> ?)")
                .collect(Collectors.joining(" OR "));
    }

    private Object[] thamSoKhoa(String khoaDangDung) {
        Object[] ts = new Object[soCotCoKhoa()];
        java.util.Arrays.fill(ts, khoaDangDung);
        return ts;
    }
}
