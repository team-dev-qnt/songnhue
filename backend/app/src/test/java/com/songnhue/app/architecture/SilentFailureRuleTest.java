package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Filters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.persistence.WorkflowAware;

/**
 * Hai luật canh <b>loại hỏng không có triệu chứng</b> — sổ nợ liên WS mục 5 và 19.
 *
 * <p>Mọi luật khác trong gói này canh những vi phạm mà sớm muộn ai đó cũng nhận ra: gọi sai tầng thì
 * code khó đọc, dùng {@code double} thì có ngày con số lệch. Hai luật ở đây khác hẳn — vi phạm
 * chúng thì <b>hệ thống chạy hoàn hảo</b>:
 *
 * <ul>
 *   <li>Quên {@code @Filter} trên một {@code ScopedEntity}: màn hình hiện đủ dữ liệu, không lỗi,
 *       không cảnh báo. Chỉ có điều Xí nghiệp A đang xem được hồ sơ của Xí nghiệp B.
 *   <li>Gọi thẳng {@code applyState}: trạng thái đổi đúng như mong đợi. Chỉ là không ai được thông
 *       báo, không dòng nhật ký nào được ghi, và không có kiểm tra quyền nào chạy.
 * </ul>
 *
 * <p>Loại lỗi này chỉ lộ ra khi đã muộn — lúc có người báo thấy dữ liệu của đơn vị khác, hoặc lúc
 * cần tra lại ai đã duyệt hồ sơ mà nhật ký trống. Vì vậy chúng phải bị chặn từ lúc build.
 *
 * <p>⚠ <b>Cả hai luật hiện chưa có lớp nào để soi</b>: Phase 0 chưa có entity thuộc phạm vi đơn vị và
 * chưa có entity nào theo quy trình duyệt — hai thứ đó thuộc Phase 1. Luật viết trước là cố ý: viết
 * sau khi đã có mười entity thì luật đầu tiên chạy sẽ đỏ hàng loạt và áp lực lúc đó là nới luật chứ
 * không phải sửa entity. {@link SilentFailureRuleSelfCheckTest} chứng minh cả hai luật thật sự bắt
 * được vi phạm, để bộ luật rỗng này không phải là một lời hứa suông.
 */
class SilentFailureRuleTest {

    /** Nơi duy nhất được phép đổi trạng thái entity — quy tắc 4 của dự án. */
    static final String ENGINE_PACKAGE = "com.songnhue.core.application.workflow";

    /**
     * ⚠ Nhận nợ WS-5/T5.11 — luật quan trọng nhất của T10.2.
     *
     * <p>{@code @FilterDef} khai trên {@link ScopedEntity} mới chỉ <i>định nghĩa</i> bộ lọc. Hibernate
     * chỉ áp nó cho entity nào <b>tự khai {@code @Filter}</b>. Thiếu annotation đó thì bộ lọc vẫn tồn
     * tại, {@code ScopeFilterAspect} vẫn bật nó thành công, và truy vấn vẫn trả về mọi dòng của mọi
     * đơn vị — không có ngoại lệ, không có log.
     */
    /**
     * ⚠ <b>Danh sách hẹp các entity có cột phạm vi NULLable</b> — bộ canh tự khai phạm vi của chính
     * nó (luật 28).
     *
     * <p>{@code ScopedEntity.orgUnitId} là {@code nullable = false} cho mọi entity của dự án trừ
     * {@code Station}: 19 điểm đo được seed trước khi OI-05 chốt 7 hay 8 Xí nghiệp, nên
     * {@code org_unit_id} phải nhận {@code NULL} và bộ lọc phải có vế {@code IS NULL} — thiếu vế ấy
     * thì <b>19/19 điểm đo vô hình với TẤT CẢ</b>, kể cả SUPER_ADMIN, vì trong SQL {@code NULL IN
     * (…)} cho ra {@code NULL} chứ không phải {@code TRUE}.
     *
     * <p>⛔ Thêm tên vào đây là một quyết định, không phải một thao tác dọn dẹp: nó nới đúng một
     * entity ra khỏi bộ lọc phạm vi. Bản nới vẫn phải mang {@link ScopedEntity#ORG_UNIT_FILTER_CONDITION}
     * <b>nguyên văn</b> và chỉ được nới đúng dạng {@code (<cột> IS NULL OR <điều kiện chuẩn>)} —
     * xem {@link CarriesOrgUnitFilter#NOI_DUOC_PHEP}. Viết {@code (1=1 OR …)} thì luật vẫn đỏ.
     */
    static final Set<String> PHAM_VI_NULL_DUOC_PHEP = Set.of("Station");

    static final ArchRule SCOPED_ENTITY_BAT_BUOC_MANG_FILTER = classes()
            .that()
            .areAssignableTo(ScopedEntity.class)
            .and()
            .doNotHaveFullyQualifiedName(ScopedEntity.class.getName())
            .should(new CarriesOrgUnitFilter(PHAM_VI_NULL_DUOC_PHEP))
            .allowEmptyShould(true)
            .because(
                    """
                    @FilterDef chỉ định nghĩa bộ lọc; Hibernate chỉ áp nó cho entity có khai @Filter. Thiếu \
                    annotation này thì tầng 3 phân quyền im lặng không hoạt động cho entity đó — dữ liệu mọi \
                    Xí nghiệp lộ hết mà không có lỗi nào. Khai đúng một dòng:
                    @Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)""");

    /**
     * ⚠ Nhận nợ WS-6/T6.5 — quy tắc 4 của dự án.
     *
     * <p>{@link WorkflowAware} cố ý đặt tên phương thức là {@code applyState} chứ không phải
     * {@code setState}, nhưng tên gọi chỉ là lời nhắc. Chốt chặn thật là luật này: chỉ
     * {@code WorkflowEngine} được gọi, vì chỉ đi qua engine thì mới có kiểm tra
     * {@code (từ trạng thái, hành động, vai trò)}, mới bắn thông báo và mới ghi nhật ký.
     */
    static final ArchRule CHI_ENGINE_DUOC_GOI_APPLY_STATE = noClasses()
            .that()
            .resideOutsideOfPackage(ENGINE_PACKAGE)
            .should(new CallsApplyState())
            .because(
                    """
                    Quy tắc 4 của dự án: đổi trạng thái chỉ qua Workflow engine. Gọi thẳng applyState() là bỏ \
                    qua kiểm tra (từ trạng thái, hành động, vai trò), bỏ qua thông báo và bỏ qua nhật ký — \
                    trạng thái vẫn đổi đúng nên không ai phát hiện, tới lúc cần tra ai đã duyệt thì nhật ký \
                    trống. Gọi WorkflowEngine.execute(entity, action, title).""");

    /**
     * ⚠⚠ Luật thứ ba, thêm ở WS-16 sau khi <b>chính lỗi này sập lần thứ hai</b>.
     *
     * <p>Spring cài giao dịch bằng proxy: chú thích {@code @Transactional} chỉ có tác dụng khi lời gọi
     * <i>đi từ ngoài vào</i> qua proxy. Một phương thức của cùng lớp gọi sang phương thức
     * {@code @Transactional} bằng {@code this} thì lời gọi đó không chạm proxy, và giao dịch
     * <b>không mở</b>.
     *
     * <p>Triệu chứng thì tuỳ chỗ, nhưng luôn ở xa nơi gây ra:
     *
     * <ul>
     *   <li>{@code BackupService} (WS-7): dòng {@code RUNNING} không được commit trước khi
     *       {@code pg_dump} chạy — tức là mất đúng thứ cơ chế đó sinh ra để giữ.
     *   <li>{@code ViewCountService} (WS-16): bộ đếm lượt xem ném
     *       {@code TransactionRequiredException} mỗi phút một lần trong log của bộ hẹn giờ, và
     *       <b>chưa từng ghi được một lượt xem nào</b> — trong khi bài kiểm xanh, vì bài kiểm gọi
     *       thẳng hàm bên trong qua proxy, tức là đi một đường khác với đường production đi.
     * </ul>
     *
     * <p>Đây là luật rẻ nhất trong nhóm này: nó không cần biết nghiệp vụ, chỉ cần biết Spring hoạt
     * động thế nào. Cách chữa cũng đã có sẵn trong dự án — mở giao dịch bằng {@code TransactionTemplate}
     * ({@code JobService}, {@code SecurityEventService}, {@code BackupService}) để việc "có giao dịch
     * hay không" không phụ thuộc vào ai gọi từ đâu.
     */
    static final ArchRule KHONG_TU_GOI_HAM_TRANSACTIONAL = noClasses()
            .should(new SelfInvokesTransactionalMethod())
            .because(
                    """
                    Spring cài @Transactional bằng proxy, nên gọi bằng `this` sang một hàm @Transactional của \
                    CÙNG lớp thì giao dịch không mở — mã vẫn biên dịch, bài kiểm gọi thẳng hàm đó vẫn xanh, và \
                    chỉ production hỏng. Dùng TransactionTemplate cho hàm được gọi, hoặc tách nó sang một bean \
                    khác.""");

    @Test
    @DisplayName("⚠ Mọi lớp con ScopedEntity đều mang @Filter với điều kiện dùng chung")
    void everyScopedEntityCarriesTheFilter() {
        SCOPED_ENTITY_BAT_BUOC_MANG_FILTER.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("⚠ Chỉ WorkflowEngine được gọi applyState()")
    void onlyTheEngineChangesState() {
        CHI_ENGINE_DUOC_GOI_APPLY_STATE.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("⚠⚠ Không hàm nào tự gọi hàm @Transactional của chính lớp mình")
    void noSelfInvocationOfTransactionalMethods() {
        KHONG_TU_GOI_HAM_TRANSACTIONAL.check(ProductionClasses.ALL);
    }

    // -------------------------------------------------------------------------

    static final class CarriesOrgUnitFilter extends ArchCondition<JavaClass> {

        /**
         * Dạng nới <b>duy nhất</b> được chấp nhận: {@code (<cột> IS NULL OR <điều kiện chuẩn>)}.
         *
         * <p>Vì sao phải là một mẫu chặt chứ không phải {@code condition.contains(chuẩn)}: một phép
         * kiểm "có chứa" cho qua cả {@code (1=1 OR <điều kiện chuẩn>)} — điều kiện chuẩn vẫn nằm
         * nguyên trong chuỗi, mà bộ lọc phạm vi thì đã tắt hoàn toàn. Mẫu này bắt buộc vế nới phải là
         * <b>một cột đơn IS NULL</b>, tức là chỉ mở đúng những dòng chưa gán đơn vị.
         *
         * <p>Điều kiện chuẩn vẫn được {@code Pattern.quote} nguyên văn ⇒ bảo đảm "viết đúng một lần
         * cho cả dự án" không bị mất: không ai chép tay lại được biểu thức materialized path.
         */
        static final Pattern NOI_DUOC_PHEP = Pattern.compile(
                "^\\(\\w+ IS NULL OR " + Pattern.quote(ScopedEntity.ORG_UNIT_FILTER_CONDITION) + "\\)$");

        /** Tên đơn của những entity được phép dùng dạng nới trên — xem {@link #PHAM_VI_NULL_DUOC_PHEP}. */
        private final Set<String> phamViNullDuocPhep;

        CarriesOrgUnitFilter(Set<String> phamViNullDuocPhep) {
            super("khai @Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ORG_UNIT_FILTER_CONDITION)");
            this.phamViNullDuocPhep = phamViNullDuocPhep;
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            Optional<Filter> filter = orgUnitFilterOf(item);
            if (filter.isEmpty()) {
                events.add(SimpleConditionEvent.violated(
                        item,
                        "%s kế thừa ScopedEntity nhưng không khai @Filter(name = \"%s\") — tầng 3 phân quyền "
                                        .formatted(item.getSimpleName(), ScopedEntity.ORG_UNIT_FILTER)
                                + "không áp cho entity này, dữ liệu mọi đơn vị lộ ra mà không có lỗi nào"));
                return;
            }
            // Điều kiện SQL phải là đúng hằng số dùng chung. Tự viết lại một điều kiện "gần giống"
            // (VD so bằng org_unit_id thay vì so theo materialized path) là bản mà cấp trên không
            // thấy dữ liệu của cấp dưới — sai theo hướng ngược lại, và cũng im lặng như vậy.
            String dieuKien = filter.get().condition();
            if (ScopedEntity.ORG_UNIT_FILTER_CONDITION.equals(dieuKien)) {
                return;
            }
            // Ngoại lệ hẹp: entity có cột phạm vi NULLable được nới đúng dạng `(<cột> IS NULL OR <chuẩn>)`.
            // Hai vế phải cùng đúng — có tên trong danh sách mà viết sai dạng thì vẫn đỏ, và viết đúng
            // dạng mà không có tên trong danh sách cũng đỏ. Nới phạm vi lọc là quyết định phải khai ra.
            boolean duocNoi = phamViNullDuocPhep.contains(item.getSimpleName());
            if (duocNoi && NOI_DUOC_PHEP.matcher(dieuKien).matches()) {
                return;
            }
            events.add(SimpleConditionEvent.violated(
                    item,
                    duocNoi
                            ? ("%s nằm trong danh sách phạm vi NULLable nhưng điều kiện không đúng dạng nới "
                                            + "được phép `(<cột> IS NULL OR <điều kiện chuẩn>)`. Điều kiện đang khai: %s")
                                    .formatted(item.getSimpleName(), dieuKien)
                            : ("%s khai @Filter với điều kiện tự viết. Dùng hằng ScopedEntity.ORG_UNIT_FILTER_CONDITION "
                                            + "— điều kiện lọc theo materialized path được viết đúng một lần cho cả "
                                            + "dự án. Cần nới cho cột phạm vi NULLable thì thêm tên lớp vào "
                                            + "PHAM_VI_NULL_DUOC_PHEP và dùng đúng dạng `(<cột> IS NULL OR <chuẩn>)`")
                                    .formatted(item.getSimpleName())));
        }

        private static Optional<Filter> orgUnitFilterOf(JavaClass item) {
            Optional<Filter> single = item.tryGetAnnotationOfType(Filter.class)
                    .filter(f -> ScopedEntity.ORG_UNIT_FILTER.equals(f.name()));
            if (single.isPresent()) {
                return single;
            }
            // Khai nhiều @Filter thì trình biên dịch gói chúng vào @Filters, và tryGetAnnotationOfType
            // ở trên trả về rỗng — bỏ qua nhánh này là luật bị né chỉ bằng cách thêm bộ lọc thứ hai.
            return item.tryGetAnnotationOfType(Filters.class).stream()
                    .flatMap(filters -> Arrays.stream(filters.value()))
                    .filter(f -> ScopedEntity.ORG_UNIT_FILTER.equals(f.name()))
                    .findFirst();
        }
    }

    static final class SelfInvokesTransactionalMethod extends ArchCondition<JavaClass> {

        SelfInvokesTransactionalMethod() {
            super("tự gọi một hàm @Transactional của chính lớp mình (proxy Spring không chạm tới)");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            item.getMethodCallsFromSelf().stream()
                    // Cùng lớp = cùng đối tượng gốc = không đi qua proxy. Gọi trên một *thể hiện khác*
                    // của cùng lớp cũng vậy, nên không cần phân biệt `this.` với biến cục bộ.
                    .filter(call -> call.getTargetOwner().equals(item))
                    .forEach(call -> call.getTarget().resolveMember().ifPresent(target -> {
                        if (!laTransactional(target)) {
                            return;
                        }
                        // Người gọi cũng @Transactional (hoặc cả lớp là @Transactional) thì giao dịch
                        // đã mở sẵn từ lượt gọi ngoài vào — trừ khi hàm được gọi đòi một giao dịch
                        // RIÊNG, thứ mà tự gọi chắc chắn không tạo ra được.
                        if (laTransactional(call.getOrigin()) && !doiGiaoDichRieng(target)) {
                            return;
                        }
                        events.add(SimpleConditionEvent.satisfied(
                                item,
                                "%s.%s() gọi thẳng %s() — %s tại %s"
                                        .formatted(
                                                item.getSimpleName(),
                                                call.getOrigin().getName(),
                                                target.getName(),
                                                doiGiaoDichRieng(target)
                                                        ? "hàm đích đòi giao dịch riêng, tự gọi thì không mở được"
                                                        : "hàm đích là @Transactional, tự gọi thì giao dịch KHÔNG mở",
                                                call.getSourceCodeLocation())));
                    }));
        }

        /** Chú thích đặt trên lớp thì áp cho mọi hàm của lớp — bỏ qua vế này là luật báo nhầm hàng loạt. */
        private static boolean laTransactional(JavaCodeUnit unit) {
            return trucTiepMangTransactional(unit) || trucTiepMangTransactional(unit.getOwner());
        }

        private static boolean trucTiepMangTransactional(
                com.tngtech.archunit.core.domain.properties.HasAnnotations<?> entity) {
            return entity.getAnnotations().stream().anyMatch(a -> a.getRawType().isEquivalentTo(Transactional.class));
        }

        private static boolean doiGiaoDichRieng(JavaMethod method) {
            return method.tryGetAnnotationOfType(Transactional.class)
                    .map(t -> switch (t.propagation()) {
                        case REQUIRES_NEW, NOT_SUPPORTED, NEVER, NESTED -> true;
                        default -> false;
                    })
                    .orElse(false);
        }
    }

    static final class CallsApplyState extends ArchCondition<JavaClass> {

        CallsApplyState() {
            super("gọi WorkflowAware.applyState()");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            item.getMethodCallsFromSelf().stream()
                    .filter(call -> "applyState".equals(call.getName()))
                    .filter(call -> call.getTargetOwner().isAssignableTo(WorkflowAware.class))
                    .forEach(call -> events.add(SimpleConditionEvent.satisfied(
                            item,
                            "%s gọi %s.applyState() tại %s"
                                    .formatted(
                                            item.getSimpleName(),
                                            call.getTargetOwner().getSimpleName(),
                                            call.getSourceCodeLocation()))));
        }
    }
}
