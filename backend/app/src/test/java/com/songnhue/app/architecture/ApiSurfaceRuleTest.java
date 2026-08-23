package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;

/**
 * Khoá nội bộ không được đi ra tới trình duyệt — {@code conventions.md} §4.2, chống IDOR.
 *
 * <h2>Vì sao luật này phải là luật cấu trúc, không phải một bài kiểm HTTP</h2>
 *
 * <p>WS-5 chốt rằng mọi lượt tra cứu theo yêu cầu người dùng đi bằng {@code public_id} (UUID ngẫu
 * nhiên), không bằng khoá tự tăng. Bảo đảm đó đúng ở <b>28/30 controller</b> suốt Phase 1 — và hai
 * chỗ còn lại không có triệu chứng nào: màn hình vẫn chạy, bài kiểm vẫn xanh, chỉ là đường dẫn mang
 * số 1, 2, 3 thay vì UUID.
 *
 * <p>Đây đúng khuôn luật 10 của dự án: <i>khi một bảo đảm phải đúng ở nhiều đường vào, đặt nó ở chỗ
 * dữ liệu đi qua; không đặt được thì phải có phép kiểm đếm đủ các đường vào.</i> Không có chỗ chung
 * nào để cài — mỗi controller tự khai tham số của mình — nên phần còn lại là đếm cho đủ.
 *
 * <p>Và luật 7: một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai. Bài kiểm bên dưới đòi hỏi
 * tập controller quét được phải <b>không rỗng</b>, nếu không thì nó xanh trên tập rỗng.
 */
class ApiSurfaceRuleTest {

    private static final Set<String> KIEU_SO =
            Set.of("long", "int", "short", "java.lang.Long", "java.lang.Integer", "java.lang.Short");

    /**
     * Ngoại lệ <b>duy nhất</b> được phép, khai đích danh.
     *
     * <p>{@code NotificationController.markRead} nhận khoá dòng người nhận, và
     * {@code NotificationService.markRead} tra bằng {@code findByIdAndUserId} — điều kiện sở hữu nằm
     * <i>trong câu truy vấn</i>, không nằm ở một lệnh {@code if} ai đó phải nhớ viết. Dò tuần tự khoá
     * chỉ nhận về 404, không lộ nội dung thông báo của người khác.
     *
     * <p>⚠ Danh sách này có xu hướng phình: thêm một dòng "cho xong lỗi build" là thao tác một dòng
     * và không ai phải giải thích gì. {@link #ngoaiLeVanConCanThiet()} canh chiều ngược lại.
     */
    private static final Set<String> NGOAI_LE = Set.of("com.songnhue.core.api.notification.NotificationController");

    /**
     * Ngoại lệ của luật DTO, cũng khai đích danh.
     *
     * <p>{@code AuditDtos.SearchRequest} là <b>bộ lọc tra cứu</b>, không phải lượt tra một tài
     * nguyên. Khác biệt quan trọng: người gọi không nhận về một bản ghi họ chưa được phép xem — cả
     * endpoint đã đứng sau {@code adm:audit:view}, và {@code audit_logs} lưu {@code entity_id} nội bộ
     * bên cạnh {@code entity_public_id} vì đúng nghiệp vụ truy vết cần nó (một entity đã bị xoá hẳn
     * thì chỉ còn khoá nội bộ để lần).
     */
    private static final Set<String> NGOAI_LE_DTO = Set.of("com.songnhue.core.api.audit.AuditDtos$SearchRequest");

    @Test
    @DisplayName("⛔ Không @PathVariable nào mang kiểu số — khoá tự tăng là giá trị đoán được")
    void noControllerTakesANumericPathVariable() {
        List<String> viPham = viPhamPathVariableKieuSo(false);

        assertThat(viPham)
                .as("⛔ Đường dẫn nhận khoá tự tăng thì người dùng gõ 1, 2, 3 là quét hết bảng. Đổi sang "
                        + "public_id (UUID) và tra bằng findByPublicIdAndDeletedAtIsNull, bọc ScopeGuard "
                        + "nếu entity thuộc phạm vi đơn vị.")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Ngoại lệ vẫn còn cần thiết — hết cần thì GỠ đi, đừng để lại một lỗ không ai nhớ vì sao")
    void ngoaiLeVanConCanThiet() {
        List<String> viPhamKeCaNgoaiLe = viPhamPathVariableKieuSo(true);

        assertThat(viPhamKeCaNgoaiLe)
                .as("không còn controller nào trong %s dùng @PathVariable kiểu số → gỡ NGOAI_LE đi", NGOAI_LE)
                .isNotEmpty();
        assertThat(viPhamKeCaNgoaiLe)
                .as("ngoại lệ đã bị nới ra ngoài danh sách khai báo")
                .allMatch(dong -> NGOAI_LE.stream().anyMatch(dong::startsWith));
    }

    @Test
    @DisplayName("⛔ Không DTO nhận nào có trường khoá kiểu số — payload cũng là một đường vào")
    void noRequestDtoCarriesANumericKey() {
        assertThat(viPhamDtoKhoaKieuSo(false))
                .as("⛔ Chặn ở đường dẫn mà bỏ quên thân yêu cầu là chặn nửa vời. Lỗ IDOR của WS-19 nằm "
                        + "đúng ở đây: OperationStatusBatchItemRequest.constructionId kiểu Long, tra bằng "
                        + "findById — thứ Hibernate KHÔNG áp bộ lọc phạm vi vào.")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Ngoại lệ DTO vẫn còn cần thiết")
    void ngoaiLeDtoVanConCanThiet() {
        List<String> trongNgoaiLe = viPhamDtoKhoaKieuSo(true);

        assertThat(trongNgoaiLe)
                .as("%s không còn trường khoá kiểu số → gỡ NGOAI_LE_DTO đi", NGOAI_LE_DTO)
                .isNotEmpty();
    }

    @Test
    @DisplayName("⚠ Luật trên phải quét được controller thật — xanh trên tập rỗng là xanh vô nghĩa")
    void theRuleActuallySeesControllers() {
        long soController = ProductionClasses.ALL.stream()
                .filter(ApiSurfaceRuleTest::laController)
                .count();

        assertThat(soController)
                .as("ArchUnit không nạp được lớp controller nào — luật 7: cơ chế chưa ai đi qua thì "
                        + "chưa biết nó đúng hay sai")
                .isGreaterThan(20);
    }

    // -------------------------------------------------------------------------

    private static List<String> viPhamDtoKhoaKieuSo(boolean chiNgoaiLe) {
        List<String> viPham = new ArrayList<>();

        for (JavaClass lop : ProductionClasses.ALL) {
            if (!lop.getPackageName().contains(".api")) {
                continue;
            }
            String ten = lop.getSimpleName();
            boolean laDtoNhan = ten.endsWith("Request") || ten.endsWith("Form") || ten.endsWith("Item");
            if (!laDtoNhan || NGOAI_LE_DTO.contains(lop.getName()) != chiNgoaiLe) {
                continue;
            }
            lop.getFields().stream()
                    .filter(truong -> KIEU_SO.contains(truong.getRawType().getName()))
                    .filter(truong ->
                            truong.getName().toLowerCase(java.util.Locale.ROOT).endsWith("id"))
                    .forEach(truong -> viPham.add(lop.getName() + "." + truong.getName()));
        }
        return viPham;
    }

    private static List<String> viPhamPathVariableKieuSo(boolean gomCaNgoaiLe) {
        List<String> viPham = new ArrayList<>();

        for (JavaClass lop : ProductionClasses.ALL) {
            if (!laController(lop)) {
                continue;
            }
            if (!gomCaNgoaiLe && NGOAI_LE.contains(lop.getName())) {
                continue;
            }
            if (gomCaNgoaiLe && !NGOAI_LE.contains(lop.getName())) {
                continue;
            }
            for (JavaMethod phuongThuc : lop.getMethods()) {
                for (JavaParameter thamSo : phuongThuc.getParameters()) {
                    boolean laPathVariable =
                            thamSo.isAnnotatedWith(org.springframework.web.bind.annotation.PathVariable.class);
                    if (laPathVariable && KIEU_SO.contains(thamSo.getRawType().getName())) {
                        viPham.add(lop.getName() + "." + phuongThuc.getName() + "(...)");
                    }
                }
            }
        }
        return viPham;
    }

    private static boolean laController(JavaClass lop) {
        return lop.isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                || lop.isAnnotatedWith(org.springframework.stereotype.Controller.class);
    }
}
