package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.hr.domain.ContractType;
import com.songnhue.hr.domain.EducationLevel;
import com.songnhue.hr.domain.EmployeeEventType;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;
import com.songnhue.hr.domain.MaritalStatus;
import com.songnhue.hr.domain.QualificationKind;
import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Một danh sách giá trị hợp lệ sống ở <b>ba nơi</b> — enum Java, union TypeScript, ràng buộc
 * {@code CHECK} của CSDL — và không cơ chế nào bắt chúng lệch nhau.
 *
 * <h2>Lỗi đã có thật, đo được ngày 01/09/2026</h2>
 *
 * {@code StepBasicInfo.tsx} chào bốn lựa chọn "Mục đích sử dụng": {@code TUOI}, {@code TIEU},
 * <b>{@code TUOI_TIEU_KET_HOP}</b>, <b>{@code KHAC}</b>. Enum {@link ConstructionPurpose} có đúng
 * ba giá trị và {@code ck_constructions_purpose} cũng ba. Hậu quả:
 *
 * <ul>
 *   <li>chọn một trong hai giá trị ma ⇒ Jackson không giải được ⇒ <b>400, hỏng CẢ lượt lưu</b>,
 *       không riêng ô đó;
 *   <li>giá trị hợp lệ {@code HON_HOP} <b>không ô nào tạo ra được</b> — nó chỉ vào hệ thống qua
 *       bộ nhập Excel.
 * </ul>
 *
 * <p>{@code tsc} xanh trọn vẹn suốt thời gian ấy, vì {@code api-types.ts} khai đúng bộ bốn giá trị
 * sai. <b>Khai kiểu là một lời khẳng định, không phải một phép đo</b> — cùng bài học T27.22.
 *
 * <h2>Vì sao bài này nằm ở bộ BE</h2>
 *
 * Nguồn sự thật là enum Java. Bộ lọc CI cho job {@code backend} chạy khi {@code backend/} đổi, nên
 * một thay đổi ở enum — chính là thứ làm ba bên lệch — luôn đi qua bài này. Đặt ở bộ FE thì ngược
 * lại: sửa enum Java, job {@code frontend} bị bỏ qua, và {@code skipped} được GitHub tính là
 * <b>ĐẠT</b>. Cùng lý lẽ với {@code AllowedActionParityTest}.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * Bài này soi <b>đúng mười ba enum</b> đã liệt kê ở {@link #BO_BA}: năm của hồ sơ công trình,
 * {@code BackupTrigger}, bốn của hồ sơ CBNV (T51.10a) và <b>ba của lớp hồ sơ con</b> (WS-53). Nó <b>không</b> phủ:
 *
 * <ul>
 *   <li>{@code sluice_specs.sluice_type} và {@code gate_operation} — CSDL có {@code CHECK} liệt kê
 *       giá trị, nhưng giao diện là ô {@code <Input>} <b>chữ tự do</b>, không có union TS nào để
 *       đối chiếu. Gõ "Hộp" hay "van phẳng" vẫn cho ra <b>500</b>. Nợ để mở, không im lặng bỏ qua.
 *   <li>enum của các module khác ({@code cms}, {@code hyd}, {@code adm}).
 * </ul>
 *
 * <h2>⬜ Nợ CÓ SỐ ĐO — vì sao danh sách vẫn gõ tay (T51.10a, phần còn lại)</h2>
 *
 * <p>Đo 10/09/2026 trên toàn chuỗi migration: <b>39</b> ràng buộc {@code CHECK … IN (…)} <b>có tên</b>
 * tồn tại (39 + 3 của WS-53 = <b>42</b>); bảng này canh <b>13</b>. Con số 29 còn lại <b>không</b> phải 29 lỗ hổng — phần lớn là enum
 * chỉ sống ở backend ({@code ck_jobs_status}, {@code ck_audit_logs_action}…) và ⛔ không có nơi thứ hai
 * để mà lệch.
 *
 * <p>⇒ Bánh cóc đúng <b>không</b> phải "mọi CHECK phải có mặt ở đây", mà là <i>"mọi enum có
 * <b>CẢ</b> một union TS <b>VÀ</b> một CHECK thì phải có mặt"</i> — nó đo đúng cái rủi ro (ba nơi
 * mới lệch được). Chưa dựng vì nó cần một bộ dò union TS trên <b>cả cây</b> {@code frontend/}, và
 * một bộ dò sai sẽ <b>đỏ oan</b> hàng loạt hoặc tệ hơn là im lặng — xem T46.7, nơi một bộ đếm nhầm
 * chú thích là lời gọi làm bộ canh xanh trong đúng tình huống nó sinh ra để bắt.
 */
class EnumBaNoiTest {

    /**
     * Một dòng = một danh sách giá trị phải khớp ở cả ba nơi.
     *
     * @param tenTuVung tên hằng {@code StatusVocabulary} ở {@code statusVocabulary.ts}, hoặc
     *     {@code null} khi enum ấy chưa được canh ở nơi thứ tư — xem phạm vi ở javadoc lớp.
     */
    private static final Path API_TYPES = gocKho().resolve("frontend/admin-app/src/shared/api-types.ts");

    /**
     * ⭐ <b>T51.10(a)</b> — union TypeScript ⛔ không bắt buộc phải sống ở {@code api-types.ts}.
     *
     * <p>Bốn enum HR khai union ngay cạnh <b>nhãn tiếng Việt</b> của chúng trong
     * {@code features/hr/hrVocabulary.ts}, và đó là một thiết kế <b>tốt hơn</b>: giá trị và nhãn của
     * nó nằm cùng một tệp nên ⛔ không lệch được. Bản trước của bài này đọc <b>đúng một</b> tệp, nên
     * cách khai ấy làm cả bốn enum <b>vô hình</b> — bộ canh xanh, mà nó ⛔ không soi gì cả.
     *
     * <p>⇒ Nơi khai TS là <b>một thuộc tính của từng dòng</b>, ⛔ không phải một hằng của cả lớp.
     */
    private static final Path HR_TU_VUNG = gocKho().resolve("frontend/admin-app/src/features/hr/hrVocabulary.ts");

    /**
     * Một dòng = một danh sách giá trị phải khớp ở cả ba nơi.
     *
     * @param tenTuVung tên hằng {@code StatusVocabulary} ở {@code statusVocabulary.ts}, hoặc
     *     {@code null} khi enum ấy chưa được canh ở nơi thứ tư — xem phạm vi ở javadoc lớp.
     * @param tepTs tệp khai union TypeScript của enum này
     */
    private record BoBa(
            Class<? extends Enum<?>> enumJava, String tenKieuTs, String tenRangBuoc, String tenTuVung, Path tepTs) {

        /** Dạng gọn cho enum khai union ở {@code api-types.ts} — chỗ mặc định. */
        BoBa(Class<? extends Enum<?>> enumJava, String tenKieuTs, String tenRangBuoc, String tenTuVung) {
            this(enumJava, tenKieuTs, tenRangBuoc, tenTuVung, API_TYPES);
        }
    }

    private static final List<BoBa> BO_BA = List.of(
            new BoBa(ConstructionType.class, "ConstructionType", "ck_constructions_type", null),
            new BoBa(ConstructionPurpose.class, "ConstructionPurpose", "ck_constructions_purpose", null),
            new BoBa(ManagementLevel.class, "ManagementLevel", "ck_constructions_management_level", null),
            new BoBa(LifecycleState.class, "LifecycleState", "ck_constructions_lifecycle", null),
            new BoBa(OperationalStatus.class, "OperationalStatus", "ck_constructions_operational_status", null),
            // T11.34 — enum đầu tiên ngoài hồ sơ công trình, và enum đầu tiên canh đủ BỐN nơi.
            new BoBa(BackupTrigger.class, "BackupTrigger", "ck_system_backups_trigger", "BACKUP_TRIGGER"),
            // ⭐ T51.10(a) — bốn enum HRM (WS-51). Trước dòng này chúng ⛔ không được nơi nào đối chiếu.
            new BoBa(Gender.class, "Gender", "ck_employees_gender", null, HR_TU_VUNG),
            new BoBa(MaritalStatus.class, "MaritalStatus", "ck_employees_marital", null, HR_TU_VUNG),
            new BoBa(ContractType.class, "ContractType", "ck_employees_contract_type", null, HR_TU_VUNG),
            new BoBa(EmploymentStatus.class, "EmploymentStatus", "ck_employees_status", null, HR_TU_VUNG),
            // ⭐ WS-53 — ba enum của lớp hồ sơ con (CN-04.3 · CN-04.4).
            // ⛔ `HoSoThuMuc` KHÔNG có mặt ở đây và đó ⛔ không phải sơ suất: `attachments.purpose`
            //    là VARCHAR tự do dùng chung cho mọi module, nên nó ⛔ không có ràng buộc CHECK để
            //    đối chiếu. Bộ ba của nó là enum ↔ union TS ↔ **bảy khoá settings**, và
            //    `HoSoThuMucHttpTest` canh đúng bộ ba ấy.
            new BoBa(EducationLevel.class, "EducationLevel", "ck_employees_education_level", null, HR_TU_VUNG),
            new BoBa(QualificationKind.class, "QualificationKind", "ck_employee_qualifications_kind", null, HR_TU_VUNG),
            new BoBa(EmployeeEventType.class, "EmployeeEventType", "ck_employee_events_type", null, HR_TU_VUNG));

    private static final Path TU_VUNG =
            gocKho().resolve("frontend/admin-app/src/components/business/statusVocabulary.ts");

    /**
     * ⚠⚠ Đọc <b>toàn bộ</b> migration, không đọc một tệp.
     *
     * <p>Bản trước ghim đúng một tệp {@code V202608211026__ops_constructions.sql} — hẹp hơn nơi nó
     * phải chặn (luật 28), và ràng buộc của module khác thì vô hình với nó. Nặng hơn: một
     * {@code CHECK} có thể được <b>dựng lại</b> ở migration sau (T11.34 làm đúng thế), nên định
     * nghĩa đang chạy là định nghĩa ở tệp có <b>số hiệu lớn nhất</b> — đọc tệp gốc là đọc một sự thật
     * đã hết hạn.
     */
    private static List<Path> moiMigration() throws IOException {
        try (var duyet = Files.walk(gocKho().resolve("backend"))) {
            return duyet.filter(p -> p.toString().contains("/db/migration/"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    @Test
    @DisplayName("⭐⭐ Enum Java ↔ union TypeScript ↔ CHECK của CSDL — ba nơi cùng một bộ giá trị")
    void baNoiCungMotBoGiaTri() throws IOException {
        List<Path> migration = moiMigration();

        for (BoBa bo : BO_BA) {
            Set<String> java = giaTriJava(bo.enumJava());
            Set<String> typescript =
                    giaTriTypeScript(Files.readString(bo.tepTs(), StandardCharsets.UTF_8), bo.tenKieuTs());
            Set<String> csdl = giaTriCsdlMoiNoi(migration, bo.tenRangBuoc());

            assertThat(typescript)
                    .as(
                            """
                            `%s`: union TypeScript lệch enum Java.
                              Java (nguồn sự thật): %s
                              TypeScript          : %s
                            Thừa ở TS = giao diện chào một giá trị backend KHÔNG GIẢI ĐƯỢC ⇒ 400 hỏng cả lượt lưu.
                            Thiếu ở TS = một giá trị hợp lệ KHÔNG Ô NÀO tạo ra được.""",
                            bo.tenKieuTs(), java, typescript)
                    .isEqualTo(java);

            assertThat(csdl)
                    .as(
                            """
                            `%s`: ràng buộc `%s` của CSDL lệch enum Java.
                              Java: %s
                              CSDL: %s
                            Lệch ở đây không bị `tsc` hay trình biên dịch thấy — nó nổ thành 500 lúc ghi.""",
                            bo.tenKieuTs(), bo.tenRangBuoc(), java, csdl)
                    .isEqualTo(java);
        }
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: bộ đọc thật sự bóc được giá trị ở CẢ BA nguồn")
    void boDocKhongChayQuaTapRong() throws IOException {
        // Luật 7: một khẳng định chạy qua tập rỗng vẫn xanh trọn vẹn. Nếu ai đó đổi cách khai union
        // hay đổi tên ràng buộc, hai bộ đọc văn bản trả về rỗng — bài trên sẽ đỏ vì lệch với Java,
        // nhưng bài này nói thẳng nguyên nhân thay vì bắt người đọc tự suy.
        List<Path> migration = moiMigration();

        assertThat(BO_BA)
                .as("bảng đối chiếu rỗng thì bài trên không khẳng định gì")
                .hasSize(13);
        assertThat(BO_BA.stream().map(BoBa::tepTs).distinct().toList())
                .as("⭐ T51.10(a): phải có ÍT NHẤT hai tệp TS trong bảng. Thiếu vế này thì một lượt "
                        + "'dọn dẹp' gộp tất cả về api-types.ts sẽ làm bốn enum HR về rỗng — và bài "
                        + "trên đỏ vì lý do SAI (lệch enum) thay vì nói ra rằng nơi khai đã đổi")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(migration)
                .as("bộ đọc migration không thấy tệp nào — đường dẫn đổi? Nó sẽ khiến MỌI bộ giá trị "
                        + "CSDL về rỗng, và bài trên đỏ vì lý do sai")
                .hasSizeGreaterThanOrEqualTo(50);

        for (BoBa bo : BO_BA) {
            assertThat(giaTriJava(bo.enumJava()))
                    .as("enum %s không có hằng nào", bo.tenKieuTs())
                    .isNotEmpty();
            assertThat(giaTriTypeScript(Files.readString(bo.tepTs(), StandardCharsets.UTF_8), bo.tenKieuTs()))
                    .as("không bóc được giá trị nào của `%s` từ %s — union đổi cách khai?", bo.tenKieuTs(), bo.tepTs())
                    .isNotEmpty();
            assertThat(giaTriCsdlMoiNoi(migration, bo.tenRangBuoc()))
                    .as(
                            "không bóc được giá trị nào của `%s` trong %d tệp migration — ràng buộc đổi tên, "
                                    + "hay khối CHECK đổi cách xuống dòng?",
                            bo.tenRangBuoc(), migration.size())
                    .isNotEmpty();
        }
    }

    /**
     * Nơi thứ TƯ — bảng nhãn hiển thị {@code statusVocabulary.ts}.
     *
     * <p>Thiếu một nhãn thì giao diện in ra <b>mã trần</b> (`PRE_DEPLOY`) giữa những dòng tiếng
     * Việt: không lỗi, không log, chỉ xấu và khó hiểu với người vận hành.
     *
     * <h2>⚠ Phạm vi tự khai (luật 28) — vì sao chỉ MỘT enum</h2>
     *
     * Vì đối chiếu bằng-nhau <b>sai</b> với phần còn lại, và đây là số đo chứ không phải phỏng đoán:
     * {@code CONSTRUCTION_STATUS} <b>gộp hai enum</b> — nó chứa {@code DA_THANH_LY}, một giá trị của
     * {@link LifecycleState}, bên cạnh năm giá trị của {@link OperationalStatus}. Ép bằng nhau ở đó
     * là dựng một bài kiểm đỏ vĩnh viễn cho một thiết kế đúng.
     *
     * <p>⬜ Nợ để mở có số đo: 13 hằng {@code StatusVocabulary} tồn tại, bài này canh <b>1</b>.
     */
    @Test
    @DisplayName("Nơi thứ tư: mọi giá trị enum đều có nhãn tiếng Việt trong statusVocabulary.ts")
    void moiGiaTriDeuCoNhan() throws IOException {
        String tuVung = Files.readString(TU_VUNG, StandardCharsets.UTF_8);
        int daCanh = 0;

        for (BoBa bo : BO_BA) {
            if (bo.tenTuVung() == null) {
                continue;
            }
            daCanh++;
            Set<String> nhan = khoaTuVung(tuVung, bo.tenTuVung());
            assertThat(nhan)
                    .as("không bóc được khoá nào từ hằng `%s` — đổi cách khai?", bo.tenTuVung())
                    .isNotEmpty();
            assertThat(nhan)
                    .as(
                            """
                            `%s`: bảng nhãn lệch enum Java.
                              Java     : %s
                              Nhãn TS  : %s
                            Thiếu nhãn = giao diện in ra MÃ TRẦN giữa những dòng tiếng Việt.""",
                            bo.tenTuVung(), giaTriJava(bo.enumJava()), nhan)
                    .isEqualTo(giaTriJava(bo.enumJava()));
        }

        assertThat(daCanh)
                .as("không dòng nào khai tenTuVung ⇒ bài này không khẳng định gì")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ Bộ đọc CSDL lấy định nghĩa MỚI NHẤT, không lấy tệp gốc")
    void docDinhNghiaMoiNhatChuKhongPhaiTepGoc() throws IOException {
        // `ck_system_backups_trigger` được định nghĩa HAI lần trong chuỗi migration:
        //   V202608161010  →  3 giá trị  (SCHEDULED, MANUAL, PRE_RESTORE)
        //   V202609081072  →  4 giá trị  (+ PRE_DEPLOY, T11.34)
        // Đây là cặp trạng thái duy nhất trong kho phân biệt được "đọc tệp gốc" với "đọc bản đang
        // chạy". Không có khẳng định này thì một bộ đọc lấy tệp ĐẦU vẫn xanh ở mọi bài khác cho tới
        // ngày có ai đó nới một ràng buộc — rồi đỏ, trong khi CSDL thật hoàn toàn đúng (quy tắc 9).
        Set<String> dangChay = giaTriCsdlMoiNoi(moiMigration(), "ck_system_backups_trigger");

        assertThat(dangChay)
                .as("phải là bản 4 giá trị của V202609081072, không phải bản 3 giá trị của V202608161010")
                .hasSize(4)
                .contains("PRE_DEPLOY");
    }

    @Test
    @DisplayName("⛔ `HON_HOP` phải có ở CẢ BA nơi — đây là giá trị đã gây ra lỗi chặn")
    void honHopCoODuBaNoi() throws IOException {
        String ts = Files.readString(API_TYPES, StandardCharsets.UTF_8);
        List<Path> migration = moiMigration();

        assertThat(giaTriJava(ConstructionPurpose.class)).contains("HON_HOP");
        assertThat(giaTriTypeScript(ts, "ConstructionPurpose"))
                .as("gỡ khỏi TS là ô chọn không tạo ra được nhiệm vụ 'Tưới tiêu kết hợp' nữa")
                .contains("HON_HOP");
        assertThat(giaTriCsdlMoiNoi(migration, "ck_constructions_purpose")).contains("HON_HOP");

        assertThat(giaTriTypeScript(ts, "ConstructionPurpose"))
                .as("hai giá trị ma của bản cũ — chào chúng ra là 400 hỏng cả lượt lưu")
                .doesNotContain("TUOI_TIEU_KET_HOP", "KHAC");
    }

    // -------------------------------------------------------------------------

    private static Set<String> giaTriJava(Class<? extends Enum<?>> loai) {
        return Arrays.stream(loai.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Bóc {@code export type <Ten> = 'A' | 'B' | 'C';} — chấp nhận xuống dòng tuỳ ý giữa các giá
     * trị, nên đổi cách trình bày không làm bài đỏ, còn thêm/bớt một giá trị thì có.
     */
    private static Set<String> giaTriTypeScript(String noiDung, String tenKieu) {
        Matcher khai = Pattern.compile("export\\s+type\\s+" + Pattern.quote(tenKieu) + "\\s*=([^;]*);", Pattern.DOTALL)
                .matcher(noiDung);
        if (!khai.find()) {
            return Set.of();
        }
        return bocChuoiNhay(khai.group(1), '\'');
    }

    /**
     * Giá trị của một ràng buộc, lấy từ tệp migration có <b>số hiệu lớn nhất</b> định nghĩa nó.
     *
     * <p>⚠ Một {@code CHECK} có thể bị bỏ rồi dựng lại ở migration sau — T11.34 nới
     * {@code ck_system_backups_trigger} đúng như vậy. Đọc tệp gốc là đọc một sự thật đã hết hạn,
     * và bài kiểm sẽ đỏ trong khi CSDL thật hoàn toàn đúng.
     */
    private static Set<String> giaTriCsdlMoiNoi(List<Path> migration, String tenRangBuoc) throws IOException {
        Set<String> moiNhat = Set.of();
        for (Path tep : migration) {
            Set<String> tim = giaTriCsdl(Files.readString(tep, StandardCharsets.UTF_8), tenRangBuoc);
            if (!tim.isEmpty()) {
                moiNhat = tim;
            }
        }
        return moiNhat;
    }

    /** Bóc các khoá của {@code export const <TEN>: StatusVocabulary = { A: {...}, B: {...} };}. */
    private static Set<String> khoaTuVung(String noiDung, String tenHang) {
        Matcher khai = Pattern.compile(
                        "export\\s+const\\s+" + Pattern.quote(tenHang)
                                + "\\s*:\\s*StatusVocabulary\\s*=\\s*\\{(.*?)\\n\\};",
                        Pattern.DOTALL)
                .matcher(noiDung);
        if (!khai.find()) {
            return Set.of();
        }
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\s{2}([A-Z][A-Z_0-9]*)\\s*:", Pattern.MULTILINE)
                .matcher(khai.group(1));
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /**
     * Bóc danh sách trong {@code CONSTRAINT <ten> CHECK ( … IN ('A', 'B') )}.
     *
     * <p>⚠ Phải chịu được dạng {@code purpose IS NULL OR purpose IN (…)} — nên nó tìm cụm
     * {@code IN (…)} bên trong khối ràng buộc chứ không giả định ràng buộc chỉ có một mệnh đề.
     */
    private static Set<String> giaTriCsdl(String sql, String tenRangBuoc) {
        Matcher moKhoi = Pattern.compile("CONSTRAINT\\s+" + Pattern.quote(tenRangBuoc) + "\\s+CHECK\\s*\\(")
                .matcher(sql);
        if (!moKhoi.find()) {
            return Set.of();
        }
        String than = docTrongNgoac(sql, moKhoi.end() - 1);
        Matcher danhSach = Pattern.compile("IN\\s*\\(", Pattern.DOTALL).matcher(than);
        if (!danhSach.find()) {
            return Set.of();
        }
        return bocChuoiNhay(docTrongNgoac(than, danhSach.end() - 1), '\'');
    }

    /**
     * Nội dung giữa cặp ngoặc <b>cân bằng</b> bắt đầu tại {@code viTriMo}.
     *
     * <h2>⛔⛔ Vì sao ⛔ KHÔNG cắt bằng regex — §11.13 lần thứ hai</h2>
     *
     * <p>Bản trước chặn khối {@code CHECK} bằng {@code (.*?)\n\s*\)}, tức <i>"tới dòng đầu tiên
     * chỉ có dấu đóng ngoặc"</i>. Nó đúng khi danh sách {@code IN (…)} nằm gọn <b>một dòng</b> —
     * đúng cách {@code V202609101076} trình bày. Nhưng một ràng buộc có danh sách dài xuống dòng thì
     * dấu đóng ngoặc của {@code IN (…)} <b>chính là</b> dòng ấy, nên phần bóc được ⛔ không còn dấu
     * đóng nào và {@code IN\s*\(([^)]*)\)} tìm không thấy ⇒ trả <b>tập rỗng</b>.
     *
     * <p>⚠ Tập rỗng làm bài chính đỏ với câu *"CSDL lệch enum Java"* — một chẩn đoán <b>sai</b>: SQL
     * hoàn toàn đúng, chỉ bộ đọc mù. Đó là một bộ canh mà <b>cách xuống dòng</b> làm cho sai, đúng
     * hình dạng §11.13 (Spotless ngắt {@code @DisplayName}) và T46.7 (đếm chú thích là lời gọi).
     *
     * <p>⇒ Đếm ngoặc thay vì so mẫu. Bỏ qua ngoặc nằm trong chuỗi nháy đơn — một giá trị enum ⛔
     * không chứa ngoặc hôm nay, nhưng bộ đọc ⛔ không được phụ thuộc vào điều đó.
     */
    private static String docTrongNgoac(String s, int viTriMo) {
        int sau = 0;
        boolean trongNhay = false;
        for (int i = viTriMo; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                trongNhay = !trongNhay;
            } else if (!trongNhay) {
                if (c == '(') {
                    sau++;
                } else if (c == ')') {
                    sau--;
                    if (sau == 0) {
                        return s.substring(viTriMo + 1, i);
                    }
                }
            }
        }
        return "";
    }

    private static Set<String> bocChuoiNhay(String doan, char nhay) {
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile(nhay + "([A-Z_]+)" + nhay).matcher(doan);
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /** Đi ngược lên tới thư mục chứa {@code .claude} — chạy được cả từ module lẫn từ gốc repo. */
    private static Path gocKho() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("Không tìm thấy gốc repo (thư mục chứa .claude)");
        }
        return p;
    }
}
