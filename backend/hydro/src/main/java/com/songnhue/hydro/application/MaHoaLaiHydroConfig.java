package com.songnhue.hydro.application;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.common.util.MaHoaLaiJdbc;
import com.songnhue.core.spi.MaHoaLaiPort;

/** Mã số truy cập nguồn thuỷ văn khai với job xoay khoá — T61.11. */
@Configuration
class MaHoaLaiHydroConfig {

    @Bean
    MaHoaLaiPort maHoaLaiApiSources(JdbcTemplate jdbc, PlatformTransactionManager tx, CryptoService crypto) {
        return new MaHoaLaiJdbc("api_sources", List.of("credential"), null, null, null, jdbc, tx, crypto);
    }
}
