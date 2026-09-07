package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Phân tầng {@code api / application / domain / infra / spi} trong từng module —
 * {@code conventions.md} §1.1.
 *
 * <p>Ba luật ở đây bảo vệ ba thứ khác nhau, và không luật nào thay được luật nào:
 *
 * <ul>
 *   <li><b>{@code api} không chạm {@code infra}</b> — controller gọi thẳng repository là bỏ qua toàn
 *       bộ tầng application: không có ranh giới transaction, nên {@code ScopeFilterAspect} (tầng 3
 *       phân quyền) <i>không chạy</i>. Dữ liệu ra ngoài phạm vi đơn vị mà không có lỗi nào.
 *   <li><b>Entity không nằm trong chữ ký của controller</b> — bản dịch thi hành được của câu
 *       "entity không bao giờ ra khỏi tầng application". Xem ghi chú bên dưới.
 *   <li><b>{@code domain} không phụ thuộc tầng nào khác</b> — giữ cho quy tắc nghiệp vụ kiểm thử
 *       được mà không cần dựng Spring context.
 * </ul>
 */
class LayeringTest {

    /**
     * ⚠ <b>Ranh giới giữa `api` và `infra`.</b>
     *
     * <p>Không phải chuyện gọn gàng. Repository gọi từ controller chạy <i>ngoài</i>
     * {@code @Transactional}, mà {@code ScopeFilterAspect} lại bám vào chính annotation đó để bật bộ
     * lọc phạm vi đơn vị. Kết quả: truy vấn chạy không có bộ lọc, trả về dữ liệu của mọi Xí nghiệp,
     * và không có ngoại lệ nào được ném ra.
     */
    private static final ArchRule API_KHONG_GOI_THANG_REPOSITORY = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infra..")
            .because(
                    """
                    Controller gọi thẳng repository là chạy ngoài ranh giới transaction, nên \
                    ScopeFilterAspect (tầng 3) không bật được bộ lọc phạm vi đơn vị — dữ liệu liên đơn vị \
                    lộ ra mà không có lỗi nào. Đưa truy vấn xuống một service ở tầng application.""");

    /**
     * <b>Bản thi hành được của "entity không bao giờ ra khỏi tầng application".</b>
     *
     * <p>Câu chữ trong {@code conventions.md} §1.1 nếu hiểu theo nghĩa đen (cấm mọi lớp trong
     * {@code api} nhắc tới entity) thì cấm luôn cả hàm dựng DTO {@code UserView.of(User)} — thứ vốn
     * là cách <i>đúng</i> để entity dừng lại ở biên. Cấm như vậy chỉ đẩy việc ánh xạ xuống tầng
     * application và bắt tầng đó phải biết tới DTO của tầng trên, tức là đổi một phụ thuộc sai chiều
     * lấy một phụ thuộc sai chiều khác.
     *
     * <p>Thiệt hại thật nằm ở <b>chữ ký</b>: entity làm kiểu trả về hoặc tham số của endpoint thì
     * Jackson tuần tự hoá thẳng entity ấy — lộ trường nội bộ (điển hình là {@code passwordHash}), bung
     * quan hệ lazy ngoài transaction, và bất kỳ ai thêm một cột mới vào bảng đều vô tình thêm nó vào
     * hợp đồng API công khai. Vì vậy luật canh đúng chữ ký của <b>phương thức có ánh xạ HTTP</b>, và
     * ghi chú này là lý do. Bản đầu bắt mọi hàm public trong {@code api} — nó đỏ ngay ở 7 hàm dựng
     * DTO, đúng những chỗ mà quy ước muốn khuyến khích.
     *
     * <p>{@code allowEmptyShould(false)} là cố ý: khớp bằng {@code @RequestMapping} ở mức meta, nên
     * ngày Spring đổi cách gắn annotation cho {@code @GetMapping} thì luật sẽ không còn khớp phương
     * thức nào — và im lặng xanh. Bắt phải có ít nhất một phương thức khớp là cách phát hiện.
     */
    private static final ArchRule ENTITY_KHONG_NAM_TRONG_CHU_KY_ENDPOINT = methods()
            .that()
            .areMetaAnnotatedWith(RequestMapping.class)
            .should(new NoEntityInSignature())
            .allowEmptyShould(false)
            .because(
                    """
                    Entity trong chữ ký endpoint bị Jackson tuần tự hoá thẳng: lộ trường nội bộ \
                    (passwordHash…), bung quan hệ lazy ngoài transaction, và mỗi cột mới thêm vào bảng \
                    lặng lẽ trở thành một phần của hợp đồng API. Trả về record DTO.""");

    /** Entity chỉ sống ở {@code domain} — {@code conventions.md} §1.1. */
    private static final ArchRule ENTITY_CHI_NAM_O_DOMAIN = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..domain..")
            .because("api/application/infra là nơi dùng entity, không phải nơi định nghĩa entity");

    /**
     * {@code domain} là tầng trong cùng: không biết gì về web, về repository, về service.
     *
     * <p>Giữ được điều này thì quy tắc nghiệp vụ kiểm thử được bằng test đơn vị thuần — không Spring
     * context, không DB. Mất nó thì mọi bài kiểm nghiệp vụ đều phải dựng cả ứng dụng, và người ta
     * ngừng viết test nghiệp vụ.
     */
    private static final ArchRule DOMAIN_KHONG_PHU_THUOC_TANG_NGOAI = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infra..", "org.springframework.web..")
            .because("domain là tầng trong cùng — phụ thuộc ra ngoài làm quy tắc nghiệp vụ không kiểm thử được nữa");

    /**
     * Ranh giới transaction chỉ đặt ở tầng application — {@code conventions.md} §1.1.
     *
     * <p>Hai ngoại lệ có chủ đích, đều nằm trong {@code core.common}: {@code CodeGenerator} cần
     * {@code REQUIRES_NEW} để bộ đếm mã nghiệp vụ không bị cuốn theo rollback của giao dịch bao ngoài,
     * và {@code ScopeFilterAspect} chỉ <i>nhắc tên</i> annotation trong biểu thức pointcut. Common
     * Platform là hạ tầng dùng chung chứ không phải tầng nghiệp vụ, nên nó tự chịu trách nhiệm về
     * ranh giới transaction của mình.
     *
     * <p>Điều luật này thật sự chặn: {@code @Transactional} trên controller (đóng giao dịch quanh cả
     * thời gian tuần tự hoá response) và trên repository (mỗi lời gọi một giao dịch — mất tính nguyên
     * tử của cả use-case).
     */
    private static final ArchRule TRANSACTIONAL_CHI_O_APPLICATION = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages("..application..", "com.songnhue.core.common..")
            .should()
            .beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because(
                    """
                    @Transactional ở controller kéo dài giao dịch qua cả lúc tuần tự hoá response; \
                    ở repository thì mỗi lời gọi thành một giao dịch riêng, use-case mất tính nguyên tử. \
                    Ranh giới transaction thuộc về tầng application.""");

    @Test
    @DisplayName("Controller không gọi thẳng repository — mất ranh giới transaction là mất luôn lọc phạm vi")
    void controllersDoNotTouchRepositories() {
        API_KHONG_GOI_THANG_REPOSITORY.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Entity không nằm trong chữ ký endpoint")
    void endpointsExposeDtosOnly() {
        ENTITY_KHONG_NAM_TRONG_CHU_KY_ENDPOINT.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Entity chỉ định nghĩa ở tầng domain")
    void entitiesLiveInDomain() {
        ENTITY_CHI_NAM_O_DOMAIN.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Tầng domain không phụ thuộc api/application/infra/web")
    void domainStaysInnermost() {
        DOMAIN_KHONG_PHU_THUOC_TANG_NGOAI.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("@Transactional chỉ đặt ở tầng application (ngoại lệ: core.common)")
    void transactionBoundaryBelongsToApplication() {
        TRANSACTIONAL_CHI_O_APPLICATION.check(ProductionClasses.ALL);
    }

    // -------------------------------------------------------------------------

    private static final class NoEntityInSignature extends ArchCondition<JavaMethod> {

        private NoEntityInSignature() {
            super("không nhận và không trả về entity JPA");
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            report(method, method.getRawReturnType(), "trả về", events);
            method.getRawParameterTypes().forEach(parameter -> report(method, parameter, "nhận", events));
        }

        private void report(JavaMethod method, JavaClass type, String role, ConditionEvents events) {
            if (type.isAnnotatedWith(Entity.class)) {
                events.add(SimpleConditionEvent.violated(
                        method,
                        "%s.%s() %s entity %s — phải dùng record DTO"
                                .formatted(
                                        method.getOwner().getSimpleName(),
                                        method.getName(),
                                        role,
                                        type.getSimpleName())));
            }
        }
    }
}
