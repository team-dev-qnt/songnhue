package com.songnhue.core.application.crypto;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.common.util.MaHoaLaiJdbc;
import com.songnhue.core.spi.MaHoaLaiPort;

/** Cột mã hoá của {@code core} khai với job xoay khoá — T61.11. */
@Configuration
class MaHoaLaiCoreConfig {

    /**
     * ⚠ {@code user_totp} ⛔ có CHECK dạng bản mã (bảng có từ Phase 0, trước khi luật ấy ra đời) nên bộ canh
     * phủ ⛔ tự tìm thấy nó — khai tay ở đây. {@code key_id} là cột CHẾT (T51.0) nhưng vẫn ghi cho khớp.
     */
    @Bean
    MaHoaLaiPort maHoaLaiUserTotp(JdbcTemplate jdbc, PlatformTransactionManager tx, CryptoService crypto) {
        return new MaHoaLaiJdbc("user_totp", List.of("secret_encrypted"), null, null, "key_id", jdbc, tx, crypto);
    }

    /** Bí mật tích hợp sửa trên giao diện — T61.44. ⛔ Quên khai ⇒ gỡ khoá cũ sau xoay khoá là mất bí mật. */
    @Bean
    MaHoaLaiPort maHoaLaiBiMatTichHop(JdbcTemplate jdbc, PlatformTransactionManager tx, CryptoService crypto) {
        return new MaHoaLaiJdbc("integration_secrets", List.of("ciphertext"), null, null, null, jdbc, tx, crypto);
    }
}
