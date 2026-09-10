package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;

import com.songnhue.core.common.audit.Audited;

/**
 * ⛔⛔ Trường bí mật ⛔ KHÔNG được chảy vào nhật ký kiểm toán — <b>T47.18</b>.
 *
 * <h2>Vì sao bộ canh này ra đời TRƯỚC dòng mã HRM đầu tiên</h2>
 *
 * <p>{@code AuditEventListener} ghi <b>giá trị cũ và mới</b> của mọi trường đổi vào
 * {@code audit_logs} — bảng lưu <b>5 năm</b> và <b>nhiều người xem được hơn</b> bảng gốc. Cơ chế
 * chặn đã có và đúng: {@code @Audited(excludeFields)} thay giá trị bằng {@code ***}.
 *
 * <p>Nhưng đo 10/09/2026: {@code grep excludeFields} trong <b>toàn bộ</b> thư mục kiểm = <b>0</b>.
 * Cơ chế <i>có</i>, có javadoc nêu đích danh <i>"hash mật khẩu, secret TOTP, khoá API"</i>, có
 * <b>2 người dùng thật</b> — mà ⛔ <b>không gì bắt entity TIẾP THEO phải dùng nó</b>. Đó là nửa cặp
 * đọc–ghi ở dạng nguy hiểm nhất: <i>một cơ chế đúng mà ⛔ không ai ép dùng</i> (luật 15, luật 27).
 *
 * <h2>⭐ Vì sao đọc BYTECODE, ⛔ không quét dòng — đo được HÔM NAY, ⛔ không phải lo xa</h2>
 *
 * <p>Bản đầu của chính bộ canh này quét mã nguồn. Nó chạy được, nhưng phép đo cho thấy đường ấy
 * đứng trên cát:
 *
 * <ul>
 *   <li>{@code grep "@Audited.*excludeFields"} trên {@code src/main} trả <b>2 dòng, CẢ HAI ở
 *       {@code ApiSource.java}</b> — và một trong hai là <b>javadoc</b>. Còn {@code User} — entity
 *       khai <b>ĐÚNG</b> — <b>vô hình</b>, vì Spotless đã ngắt annotation từ dòng 28 xuống dòng 34
 *       với <b>ba dòng {@code //} chen giữa</b>. Một bộ canh quét dòng sẽ báo {@code User} thiếu
 *       (đỏ giả) và đếm {@code ApiSource} hai lần. §11.13 + T46.7 hiện thực hoá <b>cùng lúc</b>.
 *   <li>Bộ lọc <b>KIỂU</b> chỉ có ở bytecode, và nó giết dương tính giả bằng <b>cấu trúc</b> thay vì
 *       bằng một dòng ngoại lệ: {@code User.passwordChangedAt} khớp mẫu tên nhưng là {@code Instant}
 *       — một <i>mốc thời gian</i>, và mốc ấy <b>cần</b> nằm trong nhật ký.
 * </ul>
 *
 * <p>{@code Audited} là {@code @Retention(RUNTIME)} ({@code Audited.java:23}) nên annotation đọc
 * được từ bytecode, và {@link ProductionClasses#ALL} là nơi <b>duy nhất</b> cả 5 module cùng trên
 * classpath.
 *
 * <h2>Hiện trạng đo được (10/09/2026)</h2>
 *
 * <p>41 lớp {@code @Entity} · 25 mang {@code @Audited} · <b>6</b> trường khớp mẫu tên, sau lọc kiểu
 * còn <b>5</b> trường bí mật thật:
 *
 * <ul>
 *   <li>{@code ApiSource.credential} — CÓ {@code @Audited}, đã loại trừ ✓
 *   <li>{@code User.passwordHash} — CÓ {@code @Audited}, đã loại trừ ✓
 *   <li>{@code UserSession.refreshTokenHash} · {@code UserTotp.secretEncrypted} ·
 *       {@code UserRecoveryCode.codeHash} — ⛔ không {@code @Audited} ⇒ ⛔ không rò
 * </ul>
 *
 * <p>⚠ {@code UserRecoveryCode.codeHash} (mã khôi phục 2FA đã băm) là trường mà <b>bản nháp mẫu tên
 * đầu tiên của tôi BỎ SÓT</b> — nó ⛔ không mang chữ {@code password}/{@code token}/{@code secret}.
 * Thêm {@code hash} vào mẫu bắt được nó và sinh <b>0</b> dương tính giả mới trên cả 41 entity
 * (chuỗi băm của {@code audit_logs} tính bằng <b>trigger CSDL</b>, ⛔ không có trường Java).
 *
 * <h2>⛔ PHẠM VI — luật 28, đọc trước khi tin cái xanh của nó</h2>
 *
 * <ol>
 *   <li>⭐⭐ <b>Khe mù trước HRM ĐÃ ĐÓNG ở WS-51 (10/09/2026) — bằng {@link #luat3}.</b> Bản trước
 *       của mục này viết: <i>"Nó MÙ trước HRM… trường 🔒 sẽ tên là {@code cccd}, {@code luong},
 *       {@code sucKhoe} — ⛔ không khớp mẫu nào"</i>, và dự đoán ấy <b>đúng</b>: tên thật là
 *       {@code nationalId}, {@code baseSalary}, {@code bankAccount}… — ⛔ <b>không tên nào</b> khớp
 *       {@link #TEN_BI_MAT}. Nếu chỉ có luật 1 thì {@code EmployeeSensitive} đi lọt trọn vẹn.
 *       ⇒ Luật 3 nhận diện bảng 🔒 bằng <b>ràng buộc CHECK dạng bản mã trong migration</b>, tức một
 *       dấu hiệu <b>cấu trúc</b> mà chính CSDL đang ép — ⛔ không phải một quy ước đặt tên, và ⛔
 *       không phải một danh sách ai đó phải nhớ cập nhật (luật 28: phạm vi do bộ canh <b>ĐO</b>).
 *   <li>Nó nhận diện bằng <b>quy ước đặt tên</b> + kiểu. Một bí mật đặt tên ⛔ không theo quy ước
 *       vẫn đi lọt.
 *   <li>Nó ⛔ không kiểm {@code excludeFields} có hiệu lực <b>lúc chạy</b> — việc ấy thuộc
 *       {@code AuditValueSerializer} và bài kiểm của nó. Nhưng {@link #excludeFieldsPhaiTroToiTruongCoThat}
 *       canh đúng cái khớp nối giữa hai bên.
 * </ol>
 */
class AuditRedactionRuleTest {

    /**
     * Quy ước đặt tên cho trường bí mật.
     *
     * <p>⛔ Cố ý ⛔ KHÔNG có {@code token} trần: nó khớp {@code AlertLevel.colorToken} — một
     * <i>design token</i> kiểu {@code String}, tức bộ lọc kiểu ⛔ không cứu được. Chỉ nhận
     * {@code refreshToken}/{@code accessToken}.
     *
     * <p>⭐ {@code hash} đứng một mình là CÓ CHỦ Ý — nó bắt {@code UserRecoveryCode.codeHash}, thứ
     * mà mọi mẫu hẹp hơn bỏ lọt. Đo trên 41 entity: <b>0</b> dương tính giả.
     */
    private static final Pattern TEN_BI_MAT = Pattern.compile(
            "(?i)(secret|credential|password|passphrase|encrypted|privatekey|apikey|refreshtoken|accesstoken|hash)");

    /** Kiểu có thể <b>chở</b> một bí mật. Mốc thời gian, cờ, số đếm thì ⛔ không. */
    private static final Set<String> KIEU_CHO_DUOC_BI_MAT = Set.of("java.lang.String", "byte[]", "char[]");

    /**
     * Trường khớp mẫu <b>và</b> khớp kiểu nhưng ⛔ không phải bí mật — khai <b>có tên</b> kèm lý do.
     *
     * <p>⭐ <b>RỖNG hôm nay, và đó là kết quả TỐT</b>: bộ lọc kiểu đã giết dương tính giả duy nhất
     * ({@code User.passwordChangedAt}, {@code Instant}) bằng <b>cấu trúc</b>. Một dương tính giả bị
     * loại bằng cấu trúc thì ⛔ không ai phải nhớ nó; loại bằng một dòng trong danh sách thì có.
     *
     * <p>⚠ {@link #ngoaiLeKhongDuocMoCoi} vì thế chạy trên tập RỖNG hôm nay (luật 7) — nó ⛔ chưa
     * chứng minh gì. Giữ cơ chế lại vì entity sau sẽ cần, và ghi ra đây để ⛔ không ai đọc cái xanh
     * của nó thành một bảo đảm.
     */
    private static final Map<String, String> NGOAI_LE = Map.of();

    // === Luật 1 ==============================================================

    @Test
    @DisplayName("⛔⛔ Entity có @Audited mà giữ trường bí mật PHẢI khai trong `excludeFields`")
    void moiTruongBiMatCuaEntityDuocAuditDeuBiLoaiTru() {
        List<JavaClass> entity = entityDuocAudit();

        // ⚠ VẾ CHỐNG TẬP RỖNG (luật 7). Một bộ canh anh em từng giải sai đường dẫn và quét 0 tệp —
        //   nó XANH TRỌN VẸN. Con số dưới đây đã ĐO, ⛔ không phải ước đoán.
        assertThat(entity)
                .as("⛔ 0 entity @Audited nghĩa là bộ canh ⛔ không canh gì — kiểm lại `ProductionClasses.ALL`")
                .hasSizeGreaterThanOrEqualTo(20);

        List<String> viPham = new ArrayList<>();
        int soDaKiem = 0;
        for (JavaClass lop : entity) {
            Set<String> loaiTru = Set.of(lop.getAnnotationOfType(Audited.class).excludeFields());
            for (JavaField truong : truongBiMat(lop)) {
                if (NGOAI_LE.containsKey(lop.getSimpleName() + "#" + truong.getName())) {
                    continue;
                }
                soDaKiem++;
                if (!loaiTru.contains(truong.getName())) {
                    viPham.add("%s#%s (%s)"
                            .formatted(
                                    lop.getSimpleName(),
                                    truong.getName(),
                                    truong.getRawType().getName()));
                }
            }
        }

        // ⚠ Vế chống tập rỗng THỨ HAI, chịu lực hơn vế trên: quét đủ 41 lớp mà MẪU NHẬN DIỆN hỏng
        //   thì `soDaKiem` = 0, vòng lặp ⛔ không so gì cả — và bài vẫn xanh.
        assertThat(soDaKiem)
                .as("⛔ 0 trường được đem ra so nghĩa là MẪU hoặc BỘ LỌC KIỂU đã hỏng, ⛔ không phải "
                        + "hệ thống đã sạch. Hôm nay phải thấy `ApiSource#credential` và `User#passwordHash`")
                .isGreaterThanOrEqualTo(2);

        assertThat(viPham)
                .as(
                        """
                        ⛔⛔ Trường bí mật đang chảy vào `audit_logs` — bảng lưu 5 NĂM và nhiều người \
                        xem được hơn bảng gốc. Vá bằng `@Audited(excludeFields = {"<tên trường>"})`. \
                        Nếu trường này KHÔNG phải bí mật thì khai vào `NGOAI_LE` kèm lý do đọc được \
                        — ⛔ đừng nới mẫu, vì nới mẫu là tắt bộ canh cho mọi trường cùng họ.""")
                .isEmpty();
    }

    // === Luật 2 ==============================================================

    /**
     * ⛔⛔ {@code excludeFields} phải trỏ tới một trường <b>CÓ THẬT</b>.
     *
     * <p>Cơ chế che là một phép <b>so chuỗi bằng nhau</b>: {@code AuditValueSerializer} hỏi
     * {@code excluded.contains(name)} với {@code name} lấy từ {@code getPropertyNames()} của
     * Hibernate — tức <b>tên trường Java</b>.
     *
     * <p>⇒ Đổi tên {@code passwordHash} thành {@code matKhauHash}: entity biên dịch sạch, cả nghìn
     * bài kiểm vẫn xanh, và <b>hash mật khẩu bắt đầu chảy vào nhật ký trong im lặng tuyệt đối</b>.
     * Đây là luật 14 (<i>hai nơi con người phải nhớ</i>) ở dạng đắt nhất — và nó ⛔ không rỗng: hôm
     * nay có <b>2</b> entity, <b>2</b> chuỗi phải khớp.
     */
    @Test
    @DisplayName("⛔⛔ Mỗi chuỗi trong `excludeFields` phải là tên một trường CÓ THẬT của lớp ấy")
    void excludeFieldsPhaiTroToiTruongCoThat() {
        List<String> treo = new ArrayList<>();
        int soChuoi = 0;
        for (JavaClass lop : entityDuocAudit()) {
            Set<String> tenTruong = new LinkedHashSet<>();
            for (JavaClass c = lop; c != null; c = c.getRawSuperclass().orElse(null)) {
                c.getFields().forEach(f -> tenTruong.add(f.getName()));
            }
            for (String khai : lop.getAnnotationOfType(Audited.class).excludeFields()) {
                soChuoi++;
                if (!tenTruong.contains(khai)) {
                    treo.add("%s#%s".formatted(lop.getSimpleName(), khai));
                }
            }
        }

        assertThat(soChuoi)
                .as("⛔ 0 chuỗi loại trừ nghĩa là bộ đọc annotation đã hỏng — hôm nay có ĐÚNG 2")
                .isGreaterThanOrEqualTo(2);
        assertThat(treo)
                .as(
                        """
                        ⛔⛔ `excludeFields` khai một tên ⛔ không còn là trường nào. Phép che là so \
                        CHUỖI BẰNG NHAU, nên một lượt đổi tên trường làm bí mật chảy vào nhật ký mà \
                        trình biên dịch ⛔ không nói gì và ⛔ không bài kiểm nào đỏ.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ `NGOAI_LE` ⛔ không được mồ côi (RỖNG hôm nay — xem javadoc, luật 7)")
    void ngoaiLeKhongDuocMoCoi() {
        Set<String> conSong = new LinkedHashSet<>();
        for (JavaClass lop : entityDuocAudit()) {
            truongBiMat(lop).forEach(f -> conSong.add(lop.getSimpleName() + "#" + f.getName()));
        }
        assertThat(conSong)
                .as("⛔ Một miễn trừ mồ côi sẽ hồi sinh trên một trường TƯƠNG LAI trùng tên, và trường "
                        + "ấy được miễn kiểm mà ⛔ không ai quyết định điều đó")
                .containsAll(NGOAI_LE.keySet());
    }

    // === Luật 3 ==============================================================

    /**
     * ⛔⛔ Bảng có cột mã hoá thì entity của nó phải loại trừ <b>ĐỦ</b> các cột ấy khỏi nhật ký.
     *
     * <h2>Vì sao luật 1 ⛔ không đủ, và đo được là ⛔ không đủ</h2>
     *
     * <p>Luật 1 nhận diện bí mật bằng <b>quy ước đặt tên</b> ({@code secret}, {@code password},
     * {@code hash}…). Trường 🔒 của hồ sơ nhân sự tên là {@code nationalId}, {@code baseSalary},
     * {@code bankAccount}, {@code taxCode}, {@code socialInsuranceNo} — <b>⛔ không tên nào khớp</b>.
     * Một quy ước đặt tên chỉ bắt được thứ người viết đã nghĩ tới lúc viết quy ước.
     *
     * <h2>Dấu hiệu CẤU TRÚC dùng thay: chính CSDL đang ép định dạng bản mã</h2>
     *
     * <p>Mọi cột mã hoá của kho đều mang một ràng buộc {@code CHECK} đòi tiền tố
     * {@code <key_id>:} — {@code ck_api_sources_credential_format} (WS-28) và
     * {@code ck_employee_sensitive_banma} (WS-51). Ràng buộc ấy tồn tại vì một lý do độc lập (một
     * chuỗi thiếu tiền tố là giá trị THÔ đã lọt vào bảng), nên nó là một dấu hiệu <b>khó giả</b>:
     * ⛔ không ai thêm được một cột mã hoá mà bỏ qua nó, vì bỏ qua nó là bỏ luôn phép chặn giá trị thô.
     *
     * <p>⇒ Phạm vi của luật này do bộ canh <b>ĐO từ migration</b> mỗi lượt chạy, ⛔ không do ai gõ
     * tay một danh sách — đúng điều luật 28 đòi sau <b>năm</b> lần cùng một hình dạng.
     *
     * <h2>⚠ Giới hạn phải khai (luật 28 áp cho chính nó)</h2>
     *
     * <ul>
     *   <li>Nó ⛔ <b>không</b> thấy một cột mã hoá <i>⛔ không</i> có CHECK định dạng. Đó là đánh đổi
     *       có ý thức: chỗ ấy đã có một bộ canh khác — thiếu CHECK thì giá trị thô ghi được vào
     *       bảng, và {@code HoSoNhanSuMaHoaTest} sẽ đỏ ở vế "CSDL từ chối chuỗi ⛔ không có tiền tố".
     *   <li>Nó ⛔ <b>không</b> kiểm bảng ⛔ không có entity JPA nào ánh xạ. Bảng như thế ⛔ không đi
     *       qua {@code AuditEventListener} nên ⛔ không rò được — nhưng nếu ai đó thêm entity sau,
     *       luật này bắt đầu áp <b>ngay lượt chạy kế</b>, ⛔ không cần ai nhớ bật.
     * </ul>
     */
    @Test
    @DisplayName("⛔⛔ Bảng 🔒 (có CHECK dạng bản mã) — entity phải @Audited và loại trừ ĐỦ mọi cột ấy")
    void luat3() {
        Map<String, Set<String>> cotMaHoa = cotMaHoaTheoBang();

        // ⚠ VẾ CHỐNG TẬP RỖNG (luật 7) — bộ đọc migration hỏng thì map rỗng và vòng lặp dưới ⛔
        //   không so gì cả, bài vẫn xanh. Hai con số này đã ĐO ngày 10/09/2026:
        //   `api_sources` 1 cột · `employee_sensitive` 9 cột.
        assertThat(cotMaHoa)
                .as("⛔ 0 bảng có CHECK dạng bản mã nghĩa là BỘ ĐỌC MIGRATION đã hỏng, ⛔ không phải "
                        + "kho ⛔ không có cột mã hoá nào. Phải thấy `api_sources` và `employee_sensitive`")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(cotMaHoa.values().stream().mapToInt(Set::size).sum())
                .as("⛔ Tổng số cột mã hoá tìm được quá ít — mẫu nhận diện CHECK đã hỏng")
                .isGreaterThanOrEqualTo(10);

        List<String> viPham = new ArrayList<>();
        int soCapDaKiem = 0;

        for (Map.Entry<String, Set<String>> bang : cotMaHoa.entrySet()) {
            Optional<JavaClass> entity = entityCuaBang(bang.getKey());
            if (entity.isEmpty()) {
                continue; // Bảng ⛔ không có entity ⇒ ⛔ không đi qua bộ ghi nhật ký. Xem javadoc.
            }
            JavaClass lop = entity.get();
            if (!lop.isAnnotatedWith(Audited.class)) {
                // ⛔ Thiếu @Audited ⛔ KHÔNG phải "an toàn". Nó nghĩa là mọi thay đổi trên bảng 🔒
                //    ⛔ không để lại dấu vết nào — mà NĐ 13/2023 đòi ngược lại.
                viPham.add(
                        "%s (bảng %s) — bảng 🔒 mà ⛔ KHÔNG có @Audited".formatted(lop.getSimpleName(), bang.getKey()));
                continue;
            }
            Set<String> loaiTru = Set.of(lop.getAnnotationOfType(Audited.class).excludeFields());
            for (String cot : bang.getValue()) {
                String truong = tenTruongCuaCot(lop, cot);
                if (truong == null) {
                    continue; // Cột ⛔ không được entity ánh xạ ⇒ Hibernate ⛔ không sinh sự kiện.
                }
                soCapDaKiem++;
                if (!loaiTru.contains(truong)) {
                    viPham.add("%s#%s (cột %s.%s)".formatted(lop.getSimpleName(), truong, bang.getKey(), cot));
                }
            }
        }

        // ⚠ Vế chống tập rỗng THỨ HAI, chịu lực hơn: tìm được bảng mà ⛔ không ánh xạ được cột nào
        //   sang tên trường Java thì vòng lặp ⛔ không so gì — và bài vẫn xanh.
        assertThat(soCapDaKiem)
                .as("⛔ 0 cặp cột↔trường được đem ra so nghĩa là phép ánh xạ @Column(name=…) đã hỏng, "
                        + "⛔ không phải hệ thống đã sạch")
                .isGreaterThanOrEqualTo(9);

        assertThat(viPham)
                .as(
                        """
                        ⛔⛔ Một cột MÃ HOÁ đang chảy vào `audit_logs` — bảng lưu 5 NĂM và nhiều                         người xem được hơn bảng gốc. Bản mã trong nhật ký ⛔ không vô hại: nó theo                         bản sao lưu ra khỏi phòng máy, và một vân tay xác định (kiểu                         `national_id_fingerprint`) còn cho phép NỐI các bản ghi của cùng một người.                         Vá bằng `@Audited(excludeFields = {...})` — liệt kê ĐỦ mọi cột trong CHECK                         dạng bản mã của bảng ấy.""")
                .isEmpty();
    }

    // === Bộ máy dùng chung ===================================================

    static List<JavaClass> entityDuocAudit() {
        return ProductionClasses.ALL.stream()
                .filter(c -> c.isAnnotatedWith("jakarta.persistence.Entity"))
                .filter(c -> c.isAnnotatedWith(Audited.class))
                .toList();
    }

    /** Trường vừa khớp mẫu tên, vừa mang kiểu có thể CHỞ một bí mật. */
    static List<JavaField> truongBiMat(JavaClass lop) {
        return lop.getFields().stream()
                // ⛔ Bỏ `static`: một HẰNG SỐ ⛔ không mang dữ liệu của từng hàng nên nó về nguyên
                //    tắc ⛔ không thể chảy vào `audit_logs` — Hibernate chỉ liệt kê thuộc tính bền
                //    vững. Đây là dương tính giả mà bản đọc MÃ NGUỒN giấu đi (regex của nó khớp
                //    `private <kiểu> <tên>;` nên `static final` rơi ra ngoài trong im lặng), còn
                //    bytecode thì phơi ra ngay lượt chạy đầu — `User#NO_PASSWORD`.
                //    ⭐ Loại bằng CẤU TRÚC, ⛔ không bằng một dòng `NGOAI_LE`: một hằng số thứ hai
                //    mang tên tương tự sẽ tự động đúng, ⛔ không cần ai nhớ thêm.
                .filter(f -> !f.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                .filter(f -> TEN_BI_MAT.matcher(f.getName()).find())
                .filter(f -> KIEU_CHO_DUOC_BI_MAT.contains(f.getRawType().getName()))
                .toList();
    }

    // === Bộ máy của luật 3 ===================================================

    /** Dấu hiệu cấu trúc của một cột mã hoá: CHECK đòi tiền tố {@code <key_id>:}. */
    private static final String MOC_BAN_MA = "~ '^[A-Za-z0-9_-]+:";

    /**
     * Bảng → tập cột mang CHECK dạng bản mã, <b>ĐO</b> trên mọi migration của kho.
     *
     * <p>⚠ Phạm vi quét liệt kê bằng {@code Files.list} chứ ⛔ không gõ tay tên module — cùng lý do
     * {@code MigrationNamingTest.thuMucDb()} phải sửa ở T49.1: một danh sách gõ tay bỏ sót
     * {@code db/seed/portal} suốt <b>16 ngày</b>, và trong khe mù ấy có một nạn nhân còn sống.
     */
    private static Map<String, Set<String>> cotMaHoaTheoBang() {
        Map<String, Set<String>> ket = new LinkedHashMap<>();
        for (Path tep : moiTepMigration()) {
            String bangHienTai = null;
            List<String> dong;
            try {
                dong = Files.readAllLines(tep);
            } catch (IOException e) {
                throw new IllegalStateException("⛔ Không đọc được migration " + tep, e);
            }
            for (String raw : dong) {
                String d = raw.replaceAll("\\s+", " ").trim();
                Matcher m = TAO_BANG.matcher(d);
                if (m.find()) {
                    bangHienTai = m.group(1);
                    continue;
                }
                if (bangHienTai == null) {
                    continue;
                }
                if (");".equals(d)) {
                    bangHienTai = null;
                    continue;
                }
                int moc = d.indexOf(MOC_BAN_MA);
                if (moc > 0) {
                    String cot = danhDinhTruoc(d, moc);
                    if (cot != null) {
                        ket.computeIfAbsent(bangHienTai, k -> new LinkedHashSet<>())
                                .add(cot);
                    }
                }
            }
        }
        return ket;
    }

    private static final Pattern TAO_BANG = Pattern.compile("^CREATE TABLE (?:IF NOT EXISTS )?([a-z0-9_]+) ?\\(");

    /** Định danh đứng ngay trước {@code ~} — tên cột bị ràng buộc. */
    private static String danhDinhTruoc(String dong, int viTriMoc) {
        int cuoi = viTriMoc;
        while (cuoi > 0 && dong.charAt(cuoi - 1) == ' ') {
            cuoi--;
        }
        int dau = cuoi;
        while (dau > 0 && (Character.isLetterOrDigit(dong.charAt(dau - 1)) || dong.charAt(dau - 1) == '_')) {
            dau--;
        }
        return dau == cuoi ? null : dong.substring(dau, cuoi);
    }

    /** Mọi tệp {@code .sql} dưới {@code <module>/src/main/resources/db} — phạm vi ĐO, ⛔ không gõ tay. */
    private static List<Path> moiTepMigration() {
        List<Path> ket = new ArrayList<>();
        try (Stream<Path> module = Files.list(Path.of(".."))) {
            for (Path m : module.filter(Files::isDirectory).sorted().toList()) {
                Path db = m.resolve("src/main/resources/db");
                if (!Files.isDirectory(db)) {
                    continue;
                }
                try (Stream<Path> tep = Files.walk(db)) {
                    tep.filter(Files::isRegularFile)
                            .filter(t -> t.getFileName().toString().endsWith(".sql"))
                            .sorted()
                            .forEach(ket::add);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("⛔ Không liệt kê được thư mục migration", e);
        }
        return ket;
    }

    /**
     * ⚠⚠ Đọc annotation JPA bằng <b>TÊN CHUỖI</b>, ⛔ không bằng {@code .class}.
     *
     * <p>{@code jakarta.persistence.*} ⛔ <b>không</b> nằm trên classpath test của module
     * {@code app} — {@code getAnnotationOfType(Table.class)} biên dịch <b>sạch</b> rồi ném
     * {@code NoClassDefFoundError} <b>lúc chạy</b>. Luật 1 ngay trên đã dùng dạng chuỗi
     * ({@code isAnnotatedWith("jakarta.persistence.Entity")}) vì đúng lý do ấy; bản đầu của luật 3
     * chép sang dạng {@code .class} và đỏ ngay lượt chạy đầu tiên — một lời nhắc rằng
     * <i>"biên dịch được" ⛔ không phải "qua cổng kiểm"</i> (§10.70).
     */
    private static final String TABLE = "jakarta.persistence.Table";

    private static final String COLUMN = "jakarta.persistence.Column";

    private static Optional<JavaClass> entityCuaBang(String tenBang) {
        return ProductionClasses.ALL.stream()
                .filter(c -> c.isAnnotatedWith("jakarta.persistence.Entity"))
                .filter(c -> c.isAnnotatedWith(TABLE))
                .filter(c -> tenBang.equalsIgnoreCase(thuocTinh(c.getAnnotationOfType(TABLE), "name")))
                .findFirst();
    }

    /** Tên trường Java ánh xạ vào một cột — đọc {@code @Column(name=…)} từ bytecode. */
    private static String tenTruongCuaCot(JavaClass lop, String cot) {
        for (JavaClass c = lop; c != null; c = c.getRawSuperclass().orElse(null)) {
            for (JavaField f : c.getFields()) {
                if (f.isAnnotatedWith(COLUMN)
                        && cot.equalsIgnoreCase(thuocTinh(f.getAnnotationOfType(COLUMN), "name"))) {
                    return f.getName();
                }
            }
        }
        return null;
    }

    private static String thuocTinh(JavaAnnotation<?> annotation, String ten) {
        return annotation.get(ten).map(Object::toString).orElse(null);
    }
}
