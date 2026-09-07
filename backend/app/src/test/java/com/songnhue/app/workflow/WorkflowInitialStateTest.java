package com.songnhue.app.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.persistence.WorkflowAware;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Nhiều trạng thái khởi tạo cho một quy trình — WS-12/T12.5, trên CSDL thật.
 *
 * <p><b>Nghiệp vụ đứng sau</b> (CN-02.2): ghi nhận một sự cố <i>đang</i> xảy ra thì bản ghi bắt đầu
 * ở {@code MOI}; nhập một công việc đã làm xong từ tuần trước thì bắt đầu thẳng ở {@code DA_XU_LY}.
 * Trước T12.5 thì {@code workflow_definitions} chỉ có đúng một {@code initial_state}.
 *
 * <p>⛔ Cách lách hiển nhiên — tạo ở {@code MOI} rồi chạy transition tới {@code DA_XU_LY} — bị cấm vì
 * nó ghi vào nhật ký kiểm toán một chuỗi sự việc chưa từng xảy ra, mà nhật ký ấy có hash chain.
 *
 * <p>Bài kiểm chạy trên CSDL thật chứ không mock repository, vì <b>một nửa cơ chế nằm ở tầng CSDL</b>:
 * hai ràng buộc CHECK chặn trạng thái-giả {@code __NEW__} lọt vào vế {@code to_state}. Mock đi thì
 * phần đó không được kiểm gì cả (conventions.md §1.5).
 */
class WorkflowInitialStateTest extends IntegrationTestBase {

    private static final String ENTITY_TYPE = "TEST_MAINTENANCE_LOG";
    private static final String QUYEN_NHAP_VIEC_DA_XONG = "ops:maintenance:create";

    @Autowired
    private WorkflowPort workflow;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedQuyTrinh() {
        jdbc.update("DELETE FROM workflow_definitions WHERE entity_type = ?", ENTITY_TYPE);
        jdbc.update(
                """
                INSERT INTO workflow_definitions (code, entity_type, name, initial_state)
                VALUES (?, ?, ?, 'MOI')
                """,
                ENTITY_TYPE,
                ENTITY_TYPE,
                "Lịch sử sửa chữa (bài kiểm)");

        Long definitionId = jdbc.queryForObject(
                "SELECT id FROM workflow_definitions WHERE entity_type = ?", Long.class, ENTITY_TYPE);

        // Đường vào thứ hai: nhập một công việc đã hoàn thành. Có quyền riêng.
        jdbc.update(
                """
                INSERT INTO workflow_transitions
                    (definition_id, from_state, action, to_state, required_permission, label, sort_order)
                VALUES (?, ?, 'CREATE_DONE', 'DA_XU_LY', ?, 'Nhập công việc đã hoàn thành', 1)
                """,
                definitionId,
                WorkflowPort.CREATION_STATE,
                QUYEN_NHAP_VIEC_DA_XONG);
    }

    @AfterEach
    void donDep() {
        AuthContext.clear();
        jdbc.update("DELETE FROM workflow_definitions WHERE entity_type IN (?, 'SAI')", ENTITY_TYPE);
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Không chỉ định gì → nhận trạng thái mặc định")
    void macDinhKhiKhongChiDinh() {
        dangNhapVoiQuyen(QUYEN_NHAP_VIEC_DA_XONG);

        assertThat(workflow.resolveInitialState(ENTITY_TYPE, null)).isEqualTo("MOI");
        assertThat(workflow.initialState(ENTITY_TYPE)).isEqualTo("MOI");
    }

    @Test
    @DisplayName("Có quyền → tạo thẳng được ở trạng thái khởi tạo thứ hai")
    void duongVaoThuHaiKhiCoQuyen() {
        dangNhapVoiQuyen(QUYEN_NHAP_VIEC_DA_XONG);

        assertThat(workflow.resolveInitialState(ENTITY_TYPE, "DA_XU_LY")).isEqualTo("DA_XU_LY");
    }

    @Test
    @DisplayName("⛔ Thiếu quyền → AUTH-3001, KHÔNG phải 'trạng thái không hợp lệ'")
    void thieuQuyenThiBaoDungLoai() {
        dangNhapVoiQuyen("cms:article:view");

        assertThatThrownBy(() -> workflow.resolveInitialState(ENTITY_TYPE, "DA_XU_LY"))
                .as(
                        """
                        Hai tình huống khác hẳn nhau — 'trạng thái đó không tồn tại trong quy trình' và \
                        'có tồn tại nhưng bạn không được phép'. Gộp làm một là người dùng đi sửa nhầm chỗ.""")
                .hasMessageContaining("AUTH-3001");
    }

    @Test
    @DisplayName("⛔ Trạng thái bịa → SYS-0008, kể cả khi nó là trạng thái hợp lệ ở giữa quy trình")
    void trangThaiKhongPhaiDuongVao() {
        dangNhapVoiQuyen(QUYEN_NHAP_VIEC_DA_XONG);

        assertThatThrownBy(() -> workflow.resolveInitialState(ENTITY_TYPE, "DANG_XU_LY"))
                .as("DANG_XU_LY là trạng thái có thật nhưng không phải đường vào đời — vẫn phải chặn")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SYS-0008");
    }

    @Test
    @DisplayName("Ô chọn của giao diện dựng từ API, và đã lọc theo quyền")
    void oChonLocTheoQuyen() {
        dangNhapVoiQuyen(QUYEN_NHAP_VIEC_DA_XONG);
        assertThat(workflow.initialActions(ENTITY_TYPE))
                .extracting(AllowedAction::toState)
                .containsExactly("MOI", "DA_XU_LY");

        dangNhapVoiQuyen("cms:article:view");
        assertThat(workflow.initialActions(ENTITY_TYPE))
                .as("thiếu quyền thì đường vào thứ hai không được hiện ra để bấm")
                .extracting(AllowedAction::toState)
                .containsExactly("MOI");
    }

    @Test
    @DisplayName("Trạng thái khởi tạo KHÔNG lọt vào danh sách nút của bản ghi đang sống")
    void duongVaoDoiKhongPhaiNutBamSauNay() {
        dangNhapVoiQuyen(QUYEN_NHAP_VIEC_DA_XONG);

        assertThat(workflow.allowedActions(banGhiOTrangThai("MOI")))
                .as(
                        """
                        Dòng __NEW__ là 'được phép tạo mới ở trạng thái này', không phải một bước chuyển. \
                        Lọt vào đây thì bản ghi đang ở MOI sẽ mọc ra nút nhảy thẳng sang DA_XU_LY.""")
                .isEmpty();
    }

    // ---- Hai ràng buộc nằm ở tầng CSDL --------------------------------------

    @Test
    @DisplayName("⛔ CSDL chặn transition ĐI VÀO trạng thái-giả __NEW__")
    void csdlChanToStateLaTrangThaiGia() {
        Long definitionId = jdbc.queryForObject(
                "SELECT id FROM workflow_definitions WHERE entity_type = ?", Long.class, ENTITY_TYPE);

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO workflow_transitions
                            (definition_id, from_state, action, to_state, label, sort_order)
                        VALUES (?, 'MOI', 'HUY', ?, 'Sai — quay về trạng thái giả', 9)
                        """,
                        definitionId,
                        WorkflowPort.CREATION_STATE))
                .as("vào được __NEW__ nghĩa là có bản ghi thật mang nó, và nó nhận cả các bước dành cho lúc tạo mới")
                .hasMessageContaining("ck_workflow_transitions_to_state_not_sentinel");
    }

    @Test
    @DisplayName("⛔ CSDL chặn definition lấy trạng thái-giả làm trạng thái mặc định")
    void csdlChanInitialStateLaTrangThaiGia() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO workflow_definitions (code, entity_type, name, initial_state)
                        VALUES ('SAI', 'SAI', 'Sai', ?)
                        """,
                        WorkflowPort.CREATION_STATE))
                .hasMessageContaining("ck_workflow_definitions_initial_state_not_sentinel");
    }

    // -------------------------------------------------------------------------

    private static void dangNhapVoiQuyen(String... permissions) {
        AuthContext.set(new AuthenticatedUser(
                999L,
                UUID.randomUUID(),
                "t12-probe",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("ADMIN"),
                Set.of(permissions),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    private static WorkflowAware banGhiOTrangThai(String state) {
        return new WorkflowAware() {
            @Override
            public String workflowEntityType() {
                return ENTITY_TYPE;
            }

            @Override
            public String currentState() {
                return state;
            }

            @Override
            public void applyState(String newState) {
                throw new UnsupportedOperationException("bài kiểm này không đổi trạng thái");
            }

            @Override
            public Long entityId() {
                return 1L;
            }
        };
    }
}
