package com.songnhue.hr.application;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.common.util.MaHoaLaiJdbc;
import com.songnhue.core.spi.MaHoaLaiPort;

/**
 * Trường 🔒 của hồ sơ CBNV khai với job xoay khoá — T61.11 (nợ T51.9).
 *
 * <p>⛔⛔ {@code national_id_fingerprint} tính lại CÙNG LƯỢT với {@code national_id}: chỉ mục
 * {@code uq_employee_sensitive_cccd} so trên cả chuỗi {@code <key_id>:<hex>}, nên cột mang hai khoá là chỉ
 * mục ⛔ bắt được trùng giữa hai nhóm.
 */
@Configuration
class MaHoaLaiHrConfig {

    @Bean
    MaHoaLaiPort maHoaLaiEmployeeSensitive(JdbcTemplate jdbc, PlatformTransactionManager tx, CryptoService crypto) {
        return new MaHoaLaiJdbc(
                "employee_sensitive",
                List.of(
                        "national_id",
                        "national_id_issued_on",
                        "national_id_issued_place",
                        "base_salary",
                        "salary_coefficient",
                        "bank_account",
                        "tax_code",
                        "social_insurance_no"),
                "national_id_fingerprint",
                "national_id",
                null,
                jdbc,
                tx,
                crypto);
    }
}
