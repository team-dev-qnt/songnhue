package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.songnhue.app.architecture.fixture.GhiPhamViFixtures;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * <b>Vế GHI của phạm vi đơn vị — T74.8 · T74.9.</b>
 *
 * <h2>Hai luật</h2>
 *
 * <ul>
 *   <li><b>W1</b> — mọi chỗ ở tầng {@code application} ĐẶT đơn vị cho một {@link ScopedEntity} (gọi
 *       {@code setOrgUnitId}, hoặc gọi hàm dựng của entity mà bên trong tự {@code setOrgUnitId}) phải gọi
 *       {@link ScopeGuard#requireWritableOrgUnit} trong cùng phương thức — hoặc khai miễn kèm lý do (đơn vị SAO từ
 *       một bản ghi đã tra qua bộ lọc). Trước WS-74b ba module ghi đơn vị lấy từ biểu mẫu mà chỉ kiểm nó TỒN TẠI.
 *   <li><b>W2</b> — mọi {@code existsBy…Code…} trên kho của một {@link ScopedEntity} phải đi qua
 *       {@link ScopeGuard#toanCongTy}: mã là duy nhất toàn Công ty, hỏi qua bộ lọc thì mã của đơn vị khác vô hình
 *       và người dùng nhận {@code SYS-0005} thay vì <i>"mã đã tồn tại"</i>.
 * </ul>
 *
 * <h2>⚠ Phạm vi — nói ra (luật 28)</h2>
 *
 * <ul>
 *   <li>Canh <b>đồng hiện</b> trong cùng phương thức (lời gọi trong lambda tính cho phương thức bao — đo ở T73.1),
 *       ⛔ canh luồng dữ liệu: kiểm một đơn vị rồi ghi đơn vị khác sẽ lọt.
 *   <li>W2 nhận ra "kho của entity phạm vi" bằng một {@code findByPublicId…} trả {@code Optional<ScopedEntity>} — đúng
 *       tín hiệu T73.1 dùng; kho ⛔ có phương thức ấy nằm ngoài tầm. Và W2 chỉ soi tên chứa {@code Code}.
 *   <li>Đơn vị {@code null} (điểm đo chưa gán đơn vị) ⛔ bị {@code requireWritableOrgUnit} kiểm.
 * </ul>
 */
class GhiPhamViRuleTest {

    /** W1 — chỗ đặt đơn vị ĐƯỢC miễn: đơn vị SAO từ bản ghi đã tra qua bộ lọc. Lý do ≥ 40 ký tự. */
    static final Map<String, String> MIEN_W1 = Map.of(
            "DonNghiPhepService.nop",
                    "Đơn vị của đơn nghỉ SAO từ hồ sơ CBNV — hồ sơ tra qua EmployeeService.get (ScopeGuard) hoặc là chính mình",
            "MaintenanceLogService.create",
                    "Đơn vị của bản ghi sửa chữa SAO từ công trình — công trình tra qua ConstructionService.get (ScopeGuard)",
            "ConstructionOperationStatusService.ghi",
                    "Đơn vị của tình hình vận hành SAO từ công trình — công trình tra qua ConstructionService.get (ScopeGuard)");

    // =========================================================================
    // Luật — khai riêng để bài tự-kiểm chạy lại đúng nó
    // =========================================================================

    static ArchCondition<JavaClass> w1() {
        return new ArchCondition<>("đặt đơn vị cho entity phạm vi qua ScopeGuard.requireWritableOrgUnit") {
            @Override
            public void check(JavaClass lop, ConditionEvents events) {
                for (JavaAccess<?> ghi : choDatDonVi(lop)) {
                    JavaCodeUnit noiGoi = ghi.getOrigin();
                    if (MIEN_W1.containsKey(tenNgan(noiGoi)) || goi(noiGoi, "requireWritableOrgUnit")) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            lop,
                            ("%s đặt đơn vị (%s) mà ⛔ qua ScopeGuard.requireWritableOrgUnit — tài khoản ở Xí nghiệp A ghi "
                                            + "được vào Xí nghiệp B (T74.8). Đơn vị SAO từ bản ghi đã tra qua bộ lọc thì "
                                            + "khai miễn ở MIEN_W1 kèm lý do.")
                                    .formatted(
                                            noiGoi.getFullName(),
                                            ghi.getTarget().getFullName())));
                }
            }
        };
    }

    static ArchCondition<JavaClass> w2() {
        return new ArchCondition<>("kiểm trùng mã của entity phạm vi qua ScopeGuard.toanCongTy") {
            @Override
            public void check(JavaClass lop, ConditionEvents events) {
                for (JavaMethodCall goi : lop.getMethodCallsFromSelf()) {
                    if (!laKiemMaPhamVi(goi) || goi(goi.getOrigin(), "toanCongTy")) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            lop,
                            ("%s gọi %s QUA bộ lọc phạm vi — mã của đơn vị khác vô hình, lượt lưu rơi vào SYS-0005 "
                                            + "(T74.9). Bọc bằng scopeGuard.toanCongTy(() -> …).")
                                    .formatted(
                                            goi.getOrigin().getFullName(),
                                            goi.getTarget().getFullName())));
                }
            }
        };
    }

    /** Chỗ đặt đơn vị trong một lớp: lời gọi {@code setOrgUnitId} + lời gọi hàm dựng có tự đặt đơn vị. */
    static List<JavaAccess<?>> choDatDonVi(JavaClass lop) {
        List<JavaAccess<?>> ra = new java.util.ArrayList<>();
        for (JavaMethodCall goi : lop.getMethodCallsFromSelf()) {
            // ⚠ Lời gọi TỪ BÊN TRONG entity (hàm dựng, setter của chính nó) ⛔ phải chỗ ghi: nơi ghi là nơi gọi
            //   hàm dựng ấy — nhánh dưới bắt nó. Hai nhánh loại cùng một thứ, ⛔ thì đếm hai lần một chỗ.
            if ("setOrgUnitId".equals(goi.getName())
                    && goi.getTargetOwner().isAssignableTo(ScopedEntity.class)
                    && !goi.getOrigin().getOwner().isAssignableTo(ScopedEntity.class)) {
                ra.add(goi);
            }
        }
        for (JavaConstructorCall goi : lop.getConstructorCallsFromSelf()) {
            if (!goi.getTargetOwner().isAssignableTo(ScopedEntity.class)
                    || goi.getOrigin().getOwner().isAssignableTo(ScopedEntity.class)) {
                continue;
            }
            Optional<JavaConstructor> hamDung = goi.getTarget().resolveMember();
            if (hamDung.isPresent()
                    && hamDung.get().getMethodCallsFromSelf().stream()
                            .anyMatch(g -> "setOrgUnitId".equals(g.getName()))) {
                ra.add(goi);
            }
        }
        return ra;
    }

    /**
     * Một lượt <b>tra cứu theo mã</b> trên kho của entity phạm vi.
     *
     * <h2>⛔⛔ Vì sao vị từ cũ hẹp hơn nơi nó phải chặn (T81.4)</h2>
     *
     * <p>Bản trước hỏi {@code ten.startsWith("existsBy") && ten.contains("Code")}. Nó bắt đúng hình
     * dạng <b>đang có</b> lúc T74.9 viết ra, nhưng Spring Data sinh ba tiền tố tương đương —
     * {@code existsBy} · {@code findBy} · {@code countBy} — và cả ba đi qua {@code @Filter} y hệt
     * nhau. Một phương thức tên {@code findByCodeAndDeletedAtIsNull} hay
     * {@code countByCodeStartingWith} vì thế <b>vô hình</b> với luật, trong khi hậu quả của nó
     * <b>giống hệt</b>: mã của đơn vị khác ⛔ nhìn thấy ⇒ kế hoạch nói *"thêm mới"* ⇒ lượt ghi đâm
     * vào chỉ mục duy nhất giữa chừng.
     *
     * <p>⚠ Và phép so {@code contains("Code")} phân biệt hoa thường, nên {@code …codesStartingWith}
     * cũng lọt — đúng hình dạng luật 28 mà T57.9 đã trả giá ở {@code EnumBaNoiTest} (<i>phạm vi hụt
     * ⛔ ở tệp nào được quét mà ở ký tự nào được nhận</i>).
     *
     * <h2>⛔⛔ Và bản nới ĐẦU TIÊN của tôi có 3/6 dương tính giả — chỉ lộ ra vì CHẠY nó</h2>
     *
     * <p>Chữ {@code Code} trong tên một phương thức Spring Data xuất hiện ở <b>hai vai trò khác
     * hẳn nhau</b>: một <i>tiêu chí lọc</i> ({@code findByCodeAndDeletedAtIsNull}) và một
     * <i>mệnh đề sắp xếp</i> ({@code findByDeletedAtIsNullOrderByCodeAsc}). Vế sau ⛔ tra theo mã —
     * nó chỉ <b>sắp kết quả</b> theo mã, và lọc phạm vi ở đó là <b>ĐÚNG</b>: người dùng chỉ nên
     * thấy điểm đo của đơn vị mình.
     *
     * <p>Đo 23/09/2026: bản nới ⛔ phân biệt hai vai trò ấy lôi ra <b>6</b> nơi gọi, <b>3</b> trong
     * số đó là {@code OrderBy…Code…} hoàn toàn vô can ({@code StationService.list} ·
     * {@code StationService.soDiemDoCuaNguon} · {@code AlertRuleService.diemDoChuaCauHinh}).
     * ⇒ Cắt tên ở {@code OrderBy} và chỉ đọc phần <b>tiêu chí</b>.
     *
     * <p>⭐ Dòng nợ dặn <i>"chạy luật TRƯỚC khi chốt danh sách miễn"</i>, và đó là lời dặn đắt giá:
     * viết danh sách miễn trước thì tôi đã đúc <b>3 dòng miễn cho 3 thứ ⛔ hỏng</b> — tiếng ồn che
     * mất những dòng có nghĩa, đúng hình dạng đã đo ở T63.3.
     */
    static boolean laKiemMaPhamVi(JavaMethodCall goi) {
        String ten = goi.getName();
        boolean traCuu = ten.startsWith("existsBy") || ten.startsWith("findBy") || ten.startsWith("countBy");
        // Phần TRƯỚC `OrderBy` là tiêu chí lọc; phần sau chỉ nói thứ tự sắp xếp.
        String tieuChi = ten.split("OrderBy", 2)[0];
        return traCuu
                && tieuChi.toLowerCase(java.util.Locale.ROOT).contains("code")
                && laKhoPhamVi(goi.getTargetOwner());
    }

    private static boolean laKhoPhamVi(JavaClass kho) {
        return kho.getAllMethods().stream()
                .filter(m -> m.getName().startsWith("findByPublicId"))
                .map(JavaMethod::getReturnType)
                .anyMatch(GhiPhamViRuleTest::optionalPhamVi);
    }

    private static boolean optionalPhamVi(JavaType kieu) {
        return kieu instanceof JavaParameterizedType p
                && p.toErasure().isEquivalentTo(Optional.class)
                && p.getActualTypeArguments().size() == 1
                && p.getActualTypeArguments().get(0).toErasure().isAssignableTo(ScopedEntity.class);
    }

    private static boolean goi(JavaCodeUnit noiGoi, String tenHam) {
        return noiGoi.getMethodCallsFromSelf().stream()
                .anyMatch(g -> g.getTargetOwner().isEquivalentTo(ScopeGuard.class) && tenHam.equals(g.getName()));
    }

    static String tenNgan(JavaCodeUnit noiGoi) {
        return noiGoi.getOwner().getSimpleName() + "." + noiGoi.getName();
    }

    private static ArchRule tangApplication(ArchCondition<JavaClass> dk) {
        return classes().that().resideInAPackage("com.songnhue..application..").should(dk);
    }

    // =========================================================================
    // Chạy trên mã thật
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ W1 — đặt đơn vị cho entity phạm vi phải qua requireWritableOrgUnit, hoặc khai miễn (T74.8)")
    void w1TrenMaThat() {
        tangApplication(w1()).check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("⛔⛔ W2 — kiểm trùng mã của entity phạm vi phải hỏi toàn Công ty (T74.9)")
    void w2TrenMaThat() {
        tangApplication(w2()).check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName(
            "Chống tập rỗng + miễn còn sống: đo đủ chỗ ghi · đủ kiểm mã · mỗi dòng miễn trỏ tới một chỗ ghi CÓ THẬT")
    void phaiNhinThayDu() {
        JavaClasses tat = ProductionClasses.ALL;
        Set<String> choGhi = new TreeSet<>();
        Set<String> kiemMa = new TreeSet<>();
        for (JavaClass lop : tat) {
            if (!lop.getPackageName().contains(".application")) {
                continue;
            }
            choDatDonVi(lop).forEach(g -> choGhi.add(tenNgan(g.getOrigin())));
            lop.getMethodCallsFromSelf().stream()
                    .filter(GhiPhamViRuleTest::laKiemMaPhamVi)
                    .forEach(g -> kiemMa.add(tenNgan(g.getOrigin())));
        }
        // Đo 20/09/2026: Employee (apDung) · Construction (create · update · capNhatTuTepNhap) · Station (apDung) +
        // ba chỗ SAO đơn vị; kiểm mã: Employee 1 · Construction 3 · Station 2 phương thức.
        assertThat(choGhi).as("chỗ đặt đơn vị ở tầng application").hasSizeGreaterThanOrEqualTo(8);
        assertThat(kiemMa).as("phương thức kiểm trùng mã entity phạm vi").hasSizeGreaterThanOrEqualTo(5);
        assertThat(choGhi)
                .as("⛔ dòng miễn trỏ tới một chỗ ghi ⛔ còn tồn tại — gỡ nó, kẻo lượt sau dựng lại chỗ ấy mà ⛔ ai soi")
                .containsAll(MIEN_W1.keySet());
        MIEN_W1.forEach((k, v) -> assertThat(v.length()).as(k).isGreaterThanOrEqualTo(40));
    }

    // =========================================================================
    // Tự kiểm — luật phân biệt được đúng và sai trên đồ gá (luật 1 · luật 9)
    // =========================================================================

    @Test
    @DisplayName("Tự kiểm W1: bắt setter trần + hàm dựng tự đặt đơn vị; tha chỗ có kiểm và hàm dựng ⛔ đặt đơn vị")
    void tuKiemW1() {
        JavaClasses doGa = new ClassFileImporter().importClasses(GhiPhamViFixtures.class.getDeclaredClasses());
        EvaluationResult kq = classes().should(w1()).evaluate(doGa);
        String baoCao = String.join("\n", kq.getFailureReport().getDetails());
        assertThat(baoCao).contains("GhiKhongKiem.luu").contains("TaoQuaHamDungKhongKiem.tao");
        assertThat(baoCao).doesNotContain("GhiCoKiem").doesNotContain("TaoKhongDonVi");
        assertThat(kq.getFailureReport().getDetails()).hasSize(2);
    }

    @Test
    @DisplayName("Tự kiểm W2: bắt kiểm mã qua bộ lọc; tha toanCongTy (kể cả trong lambda) và kho ⛔ phạm vi")
    void tuKiemW2() {
        JavaClasses doGa = new ClassFileImporter().importClasses(GhiPhamViFixtures.class.getDeclaredClasses());
        EvaluationResult kq = classes().should(w2()).evaluate(doGa);
        String baoCao = String.join("\n", kq.getFailureReport().getDetails());
        assertThat(baoCao).contains("KiemMaTrongPhamVi.trung");
        assertThat(baoCao).doesNotContain("KiemMaToanCongTy").doesNotContain("KiemMaDanhMuc");
    }

    @Test
    @DisplayName("⛔⛔ Tự kiểm W2 (T81.4): bắt CẢ findBy/countBy theo mã — nhưng THA OrderBy…Code (sắp xếp)")
    void tuKiemW2BaTienToVaOrderBy() {
        JavaClasses doGa = new ClassFileImporter().importClasses(GhiPhamViFixtures.class.getDeclaredClasses());
        EvaluationResult kq = classes().should(w2()).evaluate(doGa);
        String baoCao = String.join("\n", kq.getFailureReport().getDetails());

        // Luật 29 — một khẳng định VỀ SỐ LƯỢNG ⛔ chia sẻ giả định nào với phép so chuỗi ở dưới.
        assertThat(kq.getFailureReport().getDetails())
                .as("đúng ba chỗ vi phạm trong đồ gá: existsBy · findBy · countBy")
                .hasSize(3);

        // Ba tiền tố Spring Data sinh ra cho CÙNG một lượt tra theo mã — cả ba đi qua `@Filter` y hệt.
        assertThat(baoCao)
                .as("`findBy…Code…` phải bị bắt — vị từ cũ chỉ nhận `existsBy` nên nó vô hình")
                .contains("TraMaBangFindBy.tra");
        assertThat(baoCao)
                .as("`countBy…Code…` phải bị bắt — tiền tố thứ ba, cùng một hậu quả")
                .contains("DemMaBangCountBy.dem");

        // ⭐ Vế PHÂN BIỆT, và nó là vế đắt nhất: bản nới đầu tiên của tôi ⛔ có nó và sinh 3/6 dương
        //    tính giả trên mã thật. Một luật phạt cả lượt liệt kê sắp-theo-mã sẽ đẻ ra ba dòng miễn
        //    trừ cho ba thứ ⛔ hỏng — tiếng ồn che mất dòng có nghĩa (luật 28 · T63.3).
        assertThat(baoCao)
                .as("`OrderBy…Code…` là SẮP XẾP, ⛔ phải tra theo mã — lọc phạm vi ở đó là ĐÚNG")
                .doesNotContain("LietKeSapTheoMa");
    }
}
