package com.songnhue.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;
import com.songnhue.core.application.audit.AuditService;
import com.songnhue.core.application.audit.ChainBreak;
import com.songnhue.core.application.org.OrgUnitService;
import com.songnhue.core.domain.org.OrgUnitType;

/**
 * Chuỗi hash của nhật ký kiểm toán — T10.4, <b>Definition of Done mục 10</b> ({@code conventions.md}
 * §4.3, chốt G7).
 *
 * <p>Bài kiểm này đứng ở chỗ giao nhau của ba cơ chế phải cùng đúng thì mới có giá trị:
 *
 * <ol>
 *   <li><b>Ghi tự động</b> — sửa dữ liệu qua service là có dòng nhật ký, không cần ai nhớ gọi.
 *   <li><b>Chuỗi hash</b> — sửa một dòng cũ là chuỗi đứt và {@code core_verify_audit_chain()} chỉ ra
 *       đúng chỗ.
 *   <li><b>Tách quyền ở tầng DB</b> — vai trò chạy hằng ngày <b>không có</b> UPDATE/DELETE trên
 *       {@code audit_logs}, nên kể cả khi mã ứng dụng bị chiếm quyền điều khiển thì nhật ký vẫn
 *       không sửa được.
 * </ol>
 *
 * <p>Thiếu điều 3 thì điều 2 chỉ là hình thức: kẻ sửa được dữ liệu cũng tính lại được hash. Thiếu
 * điều 2 thì điều 3 chỉ chặn được đường ứng dụng. Vì vậy chúng được kiểm cùng nhau, không tách rời.
 */
class AuditChainTest extends IntegrationTestBase {

    @Autowired
    private OrgUnitService orgUnits;

    @Autowired
    private AuditService audit;

    @Autowired
    private JdbcTemplate appJdbc;

    private UUID createdUnit;

    @AfterEach
    void cleanUp() {
        if (createdUnit != null) {
            ownerJdbc().update("DELETE FROM org_units WHERE public_id = ?", createdUnit);
            createdUnit = null;
        }
    }

    @Test
    @DisplayName("Sửa dữ liệu qua service là có dòng nhật ký, không cần ai nhớ gọi")
    void changesAreLoggedAutomatically() {
        long before = auditCount();

        createUnit("T104-AUTO");

        assertThat(auditCount())
                .as("ghi nhật ký phải bám vào vòng đời entity — dựa vào việc lập trình viên nhớ gọi "
                        + "thì chỗ nào quên là chỗ đó không có dấu vết, và không ai biết")
                .isGreaterThan(before);
    }

    @Test
    @DisplayName("Chuỗi hash nguyên vẹn trên dữ liệu thật")
    void chainIsIntact() {
        createUnit("T104-INTACT");

        AuditService.ChainVerification result = audit.verifyChain(null, null);

        assertThat(result.intact())
                .as("chuỗi phải nguyên vẹn khi không ai động vào")
                .isTrue();
        assertThat(result.totalRecords()).isPositive();
    }

    /**
     * ⚠ Chốt chặn ở tầng DB — {@code architecture-review.md} §9.3.
     *
     * <p>Đây là lớp bảo vệ <b>duy nhất</b> còn đứng vững khi mã ứng dụng đã bị chiếm quyền: kẻ tấn
     * công chạy được lệnh SQL tuỳ ý qua kết nối của ứng dụng vẫn không xoá được dấu vết của chính
     * mình, vì vai trò {@code songnhue_app} không có quyền đó.
     */
    @Test
    @DisplayName("⚠ Vai trò songnhue_app KHÔNG sửa và KHÔNG xoá được audit_logs")
    void runtimeRoleCannotTamperWithAuditLogs() {
        createUnit("T104-DENY");

        // Hai lớp chặn độc lập, và bài kiểm đòi hỏi CẢ HAI:
        //  · GRANT — vai trò runtime không hề có quyền UPDATE/DELETE;
        //  · trigger append-only — chặn kể cả vai trò có quyền (xem tamperingBreaksTheChain).
        // Chỉ kiểm "câu lệnh bị từ chối" là không phân biệt được hai lớp, nên nếu ai đó lỡ cấp
        // quyền cho songnhue_app thì bài kiểm vẫn xanh nhờ lớp còn lại — đúng kiểu che mất một
        // lớp bảo vệ đã mất.
        assertThat(hasPrivilege("songnhue_app", "UPDATE"))
                .as("cấp UPDATE cho tài khoản chạy hằng ngày là vô hiệu hoá ý nghĩa của chuỗi hash")
                .isFalse();
        assertThat(hasPrivilege("songnhue_app", "DELETE")).isFalse();

        assertThatThrownBy(() -> appJdbc.update(
                        "UPDATE audit_logs SET action = 'DELETE' WHERE seq = (SELECT max(seq) FROM audit_logs)"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> appJdbc.update("DELETE FROM audit_logs WHERE seq = (SELECT max(seq) FROM audit_logs)"))
                .isInstanceOf(DataAccessException.class);
    }

    /**
     * ⚠ Chuỗi hash phải <b>phát hiện được</b> sửa đổi, không chỉ tồn tại.
     *
     * <p>Dùng kết nối vai trò {@code songnhue_owner} để đóng vai người có quyền cao ở tầng DB — tình
     * huống mà việc tách vai trò không còn chặn được. Đúng lúc đó chuỗi hash mới là thứ duy nhất còn
     * nói lên sự thật, nên nó phải chỉ ra được đúng bản ghi bị đụng vào.
     */
    @Test
    @DisplayName("⚠ Sửa lén một dòng cũ → verify chain chỉ ra đúng chỗ đứt")
    void tamperingBreaksTheChain() {
        createUnit("T104-TAMPER");
        JdbcTemplate owner = ownerJdbc();

        Long seq = owner.queryForObject("SELECT max(seq) FROM audit_logs", Long.class);
        String original = owner.queryForObject("SELECT action FROM audit_logs WHERE seq = ?", String.class, seq);

        // Trigger append-only chặn UPDATE kể cả với chủ sở hữu bảng, nên phải tắt nó đi mới sửa
        // được — chính là điều một kẻ có quyền chủ sở hữu DB sẽ làm. Đây là kịch bản duy nhất mà
        // chuỗi hash còn là tuyến phòng thủ, nên nó phải được thử đúng như vậy.
        owner.execute("ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_deny_update");
        try {
            owner.update("UPDATE audit_logs SET action = 'DELETE' WHERE seq = ?", seq);

            AuditService.ChainVerification result = audit.verifyChain(null, null);

            assertThat(result.intact())
                    .as("sửa được mà chuỗi vẫn báo nguyên vẹn thì cả cơ chế chống giả mạo là vô nghĩa")
                    .isFalse();
            assertThat(result.breaks()).extracting(ChainBreak::seq).contains(seq);

            // Trả lại giá trị cũ → chuỗi liền lại, chứng minh hash tính trên NỘI DUNG chứ không
            // phải một cờ "đã bị sửa" nào đó.
            owner.update("UPDATE audit_logs SET action = ? WHERE seq = ?", original, seq);
            assertThat(audit.verifyChain(null, null).intact()).isTrue();
        } finally {
            owner.update("UPDATE audit_logs SET action = ? WHERE seq = ?", original, seq);
            owner.execute("ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_logs_deny_update");
        }
    }

    // -------------------------------------------------------------------------

    private void createUnit(String code) {
        createdUnit = orgUnits.create(code, "Đơn vị kiểm thử " + code, OrgUnitType.PHONG_BAN, rootPublicId())
                .getPublicId();
    }

    private UUID rootPublicId() {
        return appJdbc.queryForObject("SELECT public_id FROM org_units WHERE parent_id IS NULL", UUID.class);
    }

    private boolean hasPrivilege(String role, String privilege) {
        return Boolean.TRUE.equals(appJdbc.queryForObject(
                "SELECT has_table_privilege(?, 'public.audit_logs', ?)", Boolean.class, role, privilege));
    }

    private long auditCount() {
        return appJdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
    }

    /**
     * Kết nối bằng vai trò {@code songnhue_owner} — chỉ dùng để <i>đóng vai kẻ tấn công có quyền DB</i>
     * và để dọn dữ liệu test. Mã ứng dụng thật không bao giờ có kết nối này.
     */
    private JdbcTemplate ownerJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                SongnhuePostgres.instance().getJdbcUrl(), "songnhue_owner", SongnhuePostgres.password());
        return new JdbcTemplate((DataSource) dataSource);
    }
}
