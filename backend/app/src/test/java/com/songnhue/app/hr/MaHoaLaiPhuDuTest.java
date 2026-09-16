package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.spi.MaHoaLaiPort;

/**
 * <b>Mọi cột mã hoá trên CSDL THẬT đều được job xoay khoá đổi</b> — T61.11.
 *
 * <p>Một cột mã hoá mới mà ⛔ khai {@link MaHoaLaiPort} thì job báo XANH (nó ⛔ biết cột ấy tồn tại), người
 * vận hành gỡ khoá cũ theo runbook, và cột ấy ⛔ bao giờ đọc lại được — kể cả từ bản sao lưu.
 *
 * <p>⭐ Phạm vi do bộ canh ĐO trên {@code pg_constraint} của CSDL đã chạy đủ migration (luật 3 — giá trị
 * ĐÃ GIẢI), ⛔ đọc văn bản migration: dấu hiệu là CHECK đòi tiền tố {@code <key_id>:}, cùng dấu hiệu
 * {@code AuditRedactionRuleTest} luật 3 dùng.
 *
 * <p>⚠ Giới hạn tự khai (luật 28): cột mã hoá ⛔ có CHECK ấy thì bộ canh ⛔ thấy — {@code user_totp} là
 * trường hợp đó (bảng có từ Phase 0) và được khai tay ở {@code MaHoaLaiCoreConfig}.
 */
class MaHoaLaiPhuDuTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<MaHoaLaiPort> daKhai;

    @Test
    @DisplayName("⛔⛔ Mọi cột mang CHECK dạng bản mã đều nằm trong một MaHoaLaiPort")
    void moiCotMaHoaDeuDuocKhai() {
        Set<String> tren = cotCoCheckBanMa();
        assertThat(tren)
                .as("chống tập rỗng — 8 cột 🔒 + vân tay + mã số nguồn; ít hơn là truy vấn pg_constraint hỏng")
                .hasSizeGreaterThanOrEqualTo(10)
                .contains("employee_sensitive.national_id_fingerprint", "api_sources.credential");

        Set<String> khai = new TreeSet<>();
        daKhai.forEach(p -> p.cot().forEach(c -> khai.add(p.bang() + "." + c)));

        assertThat(khai)
                .as(
                        """
                        Những cột này mang dữ liệu mã hoá mà job CRYPTO_REENCRYPT ⛔ đổi. Gỡ khoá cũ sau một lượt \
                        xoay khoá là mất chúng VĨNH VIỄN. ⇒ Khai bằng một @Bean MaHoaLaiJdbc trong module sở hữu bảng.""")
                .containsAll(tren);
        assertThat(khai).as("user_totp ⛔ có CHECK nên phải khai tay").contains("user_totp.secret_encrypted");
    }

    private Set<String> cotCoCheckBanMa() {
        return new TreeSet<>(jdbc.queryForList(
                """
                SELECT DISTINCT c.conrelid::regclass::text || '.' || a.attname
                  FROM pg_constraint c
                  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                 WHERE c.contype = 'c'
                   AND position('^[A-Za-z0-9_-]+:' in pg_get_constraintdef(c.oid)) > 0
                   AND position(a.attname in pg_get_constraintdef(c.oid)) > 0
                """,
                String.class));
    }
}
