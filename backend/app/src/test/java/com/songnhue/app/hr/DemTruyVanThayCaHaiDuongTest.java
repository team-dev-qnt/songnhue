package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.DemTruyVan;
import com.songnhue.app.testsupport.IntegrationTestBase;

/**
 * <b>Bài tự-kiểm của {@link DemTruyVan}</b> — trả nợ T63.6.
 *
 * <h2>Vì sao một bộ đếm cũng cần bộ canh của chính nó</h2>
 *
 * Bản đầu (T63.5) chỉ đọc {@code Statistics} của Hibernate, nên một vòng lặp
 * {@code JdbcTemplate.queryForObject} đếm ra <b>0</b>. Một bộ đếm trả 0 cho một thao tác thật sự
 * chạy 5 câu lệnh ⛔ phải *"đếm thiếu"* — nó làm bộ canh N+1 <b>xanh trong đúng tình huống nó sinh
 * ra để bắt</b> (luật 7), và cái xanh ấy đọc như một lời bảo đảm cho <b>33 tệp</b> {@code src/main}
 * đi bằng {@code JdbcTemplate} — gồm cả {@code hydro/infra} và {@code hr/infra}.
 *
 * <p>⇒ Hai vế, và vế thứ hai mới là vế phân biệt được hai trạng thái (luật 9): một thao tác ⛔ chạm
 * CSDL phải cho <b>0</b>. Thiếu nó thì một bộ đếm hỏng theo kiểu *"luôn trả số to"* cũng đi lọt.
 */
class DemTruyVanThayCaHaiDuongTest extends IntegrationTestBase {

    private static final int SO_LUOT = 5;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory emf;

    @Test
    @DisplayName("⭐⭐ bộ đếm THẤY câu lệnh đi bằng JdbcTemplate — trước T63.6 vế này đếm ra 0")
    void demThayDuongJdbcTemplate() {
        DemTruyVan dem = new DemTruyVan(emf);

        long so = dem.dem(() -> {
            for (int i = 0; i < SO_LUOT; i++) {
                jdbc.queryForObject("SELECT count(*) FROM org_units", Long.class);
            }
        });

        assertThat(so)
                .as(
                        "⛔ Bộ đếm mù trước JdbcTemplate (lớp bean = %s)",
                        jdbc.getClass().getName())
                .isGreaterThanOrEqualTo(SO_LUOT);
    }

    @Test
    @DisplayName("⚠ vế phân biệt: thao tác ⛔ chạm CSDL phải đếm ra 0")
    void thaoTacKhongChamCsdlDemRaKhong() {
        DemTruyVan dem = new DemTruyVan(emf);

        assertThat(dem.dem(() -> {
                    /* ⛔ chạm CSDL — nếu vế này cũng ra số dương thì con số kia ⛔ nói lên điều gì. */
                }))
                .isZero();
    }

    @Test
    @DisplayName("⭐ bộ đếm vẫn THẤY đường Hibernate — nửa cũ ⛔ được mất khi nối thêm nửa mới")
    void demVanThayDuongHibernate() {
        DemTruyVan dem = new DemTruyVan(emf);

        long so = dem.dem(() -> {
            var em = emf.createEntityManager();
            try {
                for (int i = 0; i < SO_LUOT; i++) {
                    em.createQuery("SELECT count(o) FROM OrgUnit o", Long.class).getSingleResult();
                }
            } finally {
                em.close();
            }
        });

        assertThat(so).isGreaterThanOrEqualTo(SO_LUOT);
    }
}
