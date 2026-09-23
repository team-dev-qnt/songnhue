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

import com.songnhue.content.domain.ContactStatus;
import com.songnhue.content.domain.FeedbackStatus;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.domain.job.JobStatus;
import com.songnhue.hr.domain.ContractType;
import com.songnhue.hr.domain.EducationLevel;
import com.songnhue.hr.domain.EmployeeEventType;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;
import com.songnhue.hr.domain.LeaveState;
import com.songnhue.hr.domain.LeaveType;
import com.songnhue.hr.domain.MaritalStatus;
import com.songnhue.hr.domain.QualificationKind;
import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.AlertConditionType;
import com.songnhue.hydro.domain.AlertEventStatus;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.domain.PositionRole;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.GateOperation;
import com.songnhue.operations.domain.GisGeometryType;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.MaintenanceType;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.domain.SluiceType;
import com.songnhue.operations.domain.TrangThaiBaoCaoNhanh;

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
 * <h2>⭐⭐ Phạm vi nay do bộ canh ĐO, ⛔ do javadoc khai (T52.7 · T81.1 — 23/09/2026)</h2>
 *
 * <p><b>⛔ Đừng chép một con số ở đây.</b> Ba con số trong bản javadoc trước đều đã hết hạn cùng
 * lúc — <i>"mười ba enum"</i> khi {@link #BO_BA} đã có 16, <i>"39 ràng buộc"</i> khi phép đếm ngoặc
 * cân bằng ra 62, <i>"bảng này canh 18"</i> khi nó canh 20. Đó là lần thứ <b>ba</b> cùng một tệp tự
 * khai sai phạm vi của mình, và mỗi lần cái xanh của nó lại <b>đọc như một lời bảo đảm</b> cho một
 * vùng nó ⛔ soi (luật 28).
 *
 * <p>⇒ Nay <b>vế trái đo từ đĩa</b> và mỗi phần tử phải được <b>XẾP LOẠI</b>, đúng khuôn đã dùng ở
 * {@code CotPhase2CoDocGhiTest} (module) · {@code VongKhuHoiDuPhamViTest} (endpoint) ·
 * {@code CoreSettingsReadTest} (khoá settings):
 *
 * <ul>
 *   <li>{@link #moiRangBuocDeuDuocXepLoai} — <b>mọi</b> {@code CONSTRAINT … CHECK … IN (…)} có tên
 *       trong chuỗi migration phải hoặc nằm ở {@link #BO_BA}, hoặc ở {@link #DUOC_MIEN} kèm lý do
 *       <b>đo được</b> ≥ 40 ký tự. Một ràng buộc mới ra đời ⇒ <b>CI đỏ, gọi đích danh</b>.
 *   <li>{@link #coUnionTsThiKhongDuocMien} — <b>bánh cóc thật</b>, và nó ⛔ nhận lời tự khai: nếu
 *       <b>đo được</b> một union TypeScript khai đúng tập giá trị của ràng buộc, thì dòng xin miễn
 *       bị <b>TỪ CHỐI</b>. Ba nơi mới lệch được, nên chỉ ràng buộc ⛔ có nơi thứ hai mới được miễn —
 *       và phép đo phán xử điều đó, ⛔ phải người viết.
 *   <li>{@link #moiHangTuVungDeuDuocXepLoai} — cùng khuôn cho <b>nơi thứ tư</b>
 *       ({@code statusVocabulary.ts}).
 *   <li>{@link #phamViDoTuDiaChuKhongGoTay} — giữ chính phạm vi ấy: ⛔ nuốt bản sao {@code target/},
 *       ⛔ bỏ sót {@code db/seed/}.
 * </ul>
 *
 * <p>Số đo ngày <b>23/09/2026</b> (đọc từ chính bộ đọc của bài này, ⛔ chép): <b>62</b> ràng buộc
 * có tên · {@link #BO_BA} canh <b>41</b> · {@link #DUOC_MIEN} <b>21</b> · nơi thứ tư <b>14</b> hằng,
 * canh <b>11</b>, miễn <b>3</b>. ⭐ Lượt thêm 21 dòng ấy <b>xanh ngay</b> ⇒ ⛔ enum ba-nơi nào đang
 * lệch hôm nay; thứ thiếu suốt thời gian qua là <b>cơ chế</b>, ⛔ phải một bản vá.
 *
 * <h2>⬜ Câu hỏi bài này KHÔNG hỏi — {@code T85.11}</h2>
 *
 * <p>Bài chính hỏi <i>"ba nơi có cùng một bộ giá trị ⛔"</i>. Với ràng buộc <b>⛔ có</b> union TS,
 * vẫn còn <b>hai</b> nơi — enum Java và {@code CHECK} — và hai nơi <b>vẫn lệch được</b>; lệch ở đó
 * nổ thành <b>500 lúc ghi</b>. Đo 23/09: <b>8</b> dòng trong {@link #DUOC_MIEN} có enum Java thật
 * ({@code AttachmentStatus} · {@code ScanStatus} · {@code AuditAction} · {@code OrgUnitType} ·
 * {@code NotificationSeverity} · {@code NotificationChannel} · {@code RecipientStatus} ·
 * {@code AcceptanceResult}). Câu ấy hiện <b>⛔ ai hỏi</b> — nó nằm ở {@code T85.11} kèm số đo,
 * ⛔ giấu sau một dòng miễn trừ.
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
     * Nơi khai union thứ <b>ba</b> — bốn enum của {@code content} (T52.7).
     *
     * <p>⛔⛔ Hằng này phải khai <b>TRƯỚC</b> {@link #BO_BA}. Java khởi tạo trường tĩnh theo
     * <b>thứ tự văn bản</b>, nên một hằng {@code Path} đặt dưới bảng sẽ còn {@code null} lúc bảng
     * dựng — bộ đọc khi ấy ném {@code NullPointerException} ở lượt chạy đầu, hoặc tệ hơn là trả tập
     * rỗng nếu có ai đó "vá" bằng một lần kiểm {@code null}.
     */
    private static final Path CMS_TYPES = gocKho().resolve("frontend/admin-app/src/features/cms/types.ts");

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
            new BoBa(ConstructionType.class, "ConstructionType", "ck_constructions_type", "CONSTRUCTION_TYPE"),
            new BoBa(ConstructionPurpose.class, "ConstructionPurpose", "ck_constructions_purpose", null),
            new BoBa(ManagementLevel.class, "ManagementLevel", "ck_constructions_management_level", "MANAGEMENT_LEVEL"),
            new BoBa(LifecycleState.class, "LifecycleState", "ck_constructions_lifecycle", null),
            // ⛔⛔ T81.1 (23/09/2026) — `tenTuVung` ở dòng này BÁC một lý do miễn trừ đã nằm trong
            //    javadoc của `moiGiaTriDeuCoNhan` từ T11.34. Lý do cũ khai: *"`CONSTRUCTION_STATUS`
            //    gộp HAI enum — nó chứa `DA_THANH_LY`, một giá trị của `LifecycleState`, bên cạnh
            //    NĂM giá trị của `OperationalStatus`; ép bằng nhau là dựng một bài kiểm đỏ vĩnh viễn
            //    cho một thiết kế đúng"*. Đo 23/09: `OperationalStatus` có **SÁU** hằng và
            //    `DA_THANH_LY` là một trong số đó — `NGUNG_MUA_VU` cũng có ở cả hai enum. Lời tiên
            //    đoán *"đỏ vĩnh viễn"* SAI, và nó đã giữ nơi thứ tư ở mức 1/13 suốt một tháng.
            //    ⇒ Lần thứ BA trong PR này một lý do miễn trừ hoá ra là dự đoán chưa kiểm.
            new BoBa(
                    OperationalStatus.class,
                    "OperationalStatus",
                    "ck_constructions_operational_status",
                    "CONSTRUCTION_STATUS"),
            // ⭐ T68.28 (23/09/2026) — hai enum của `sluice_specs`. Javadoc lớp này TỪNG khai chúng là nợ
            //    kèm lý do *"⛔ có union TS nào để đối chiếu"*; lý do ấy hết đúng khi ô `<Input>` chữ tự do
            //    thành `<Select>` đọc nhãn từ `statusVocabulary`.
            new BoBa(SluiceType.class, "SluiceType", "ck_sluice_specs_type", "SLUICE_TYPE"),
            new BoBa(GateOperation.class, "GateOperation", "ck_sluice_specs_gate", "GATE_OPERATION"),
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
            new BoBa(EmployeeEventType.class, "EmployeeEventType", "ck_employee_events_type", null, HR_TU_VUNG),
            // ⭐ WS-57 — hai enum của nghỉ phép (CN-04.9).
            // ⚠ `LeaveState` khai ở đây <b>⛔ không</b> làm nó thành nguồn sự thật của LUỒNG: nguồn
            //   ấy là `workflow_transitions` (quy tắc 4). Bộ canh này chỉ hỏi *ba nơi có cùng một
            //   BỘ GIÁ TRỊ ⛔ không* — một câu hỏi khác, và cả hai đều cần.
            new BoBa(LeaveType.class, "LeaveType", "ck_leave_requests_type", null, HR_TU_VUNG),
            new BoBa(LeaveState.class, "LeaveState", "ck_leave_requests_state", null, HR_TU_VUNG),
            // ⭐ WS-59 — lớp bản đồ GIS (CN-02.4 / M2.9). Union khai ở `api-types.ts` như mọi enum
            //   của `operations`.
            new BoBa(GisGeometryType.class, "GisGeometryType", "ck_gis_layers_geometry_type", null),
            // ⭐⭐ T68.33 (20/09/2026) — hai enum của `core`, và là enum `core` thứ hai lọt vào đây sau
            //    `BackupTrigger`. Lượt thêm này **ĐỎ NGAY**: `UserStatus` của Java và `ck_users_status`
            //    đều có BỐN giá trị, union TS chỉ khai BA — lệch suốt 38 ngày mà `tsc` xanh trọn vẹn,
            //    đúng bài học ở javadoc lớp này (*một union là một lời khẳng định, ⛔ phải phép đo*).
            //    Nơi thứ tư cũng thiếu ⇒ tài khoản `DISABLED` rơi vào nhánh dự phòng của `StatusBadge`
            //    và hiện ra chữ `DISABLED` thô cho người dùng.
            new BoBa(UserStatus.class, "UserStatus", "ck_users_status", "USER_STATUS"),
            new BoBa(JobStatus.class, "JobStatus", "ck_jobs_status", "JOB_STATUS"),
            // ⭐⭐ T52.7 (23/09/2026) — 21 dòng dưới đây ⛔ phải một lượt "cho đầy bảng". Chúng là
            //    ĐÚNG tập mà phép đo của `coUnionTsThiKhongDuocMien` TỪ CHỐI cho xin miễn: mỗi cái có
            //    một union TypeScript khai **đúng tập giá trị** của ràng buộc, tức đã đủ ba nơi để
            //    lệch. Trước lượt này vế trái của bộ canh là một danh sách GÕ TAY nên cả 21 vô hình.
            //
            //    ⚠ Bốn enum của `content` khai union ở `features/cms/types.ts`, ⛔ ở `api-types.ts` —
            //    đúng tiền lệ T51.10(a) (bốn enum HR khai cạnh nhãn của chúng). Một bộ dò một-tệp sẽ
            //    kết luận *"⛔ có nơi thứ hai"* cho chính những enum có tới ba.
            //    ⚠⚠ `MenuLinkType` khai ở **hai** tệp TS (`features/cms/types.ts` và
            //    `public-web/src/lib/api.ts`) ⇒ nó có **BỐN** nơi; dòng này mới canh ba — xem `T85.12`.
            new BoBa(ContactStatus.class, "ContactStatus", "ck_contacts_status", null, CMS_TYPES),
            new BoBa(FeedbackStatus.class, "FeedbackStatus", "ck_feedbacks_status", null, CMS_TYPES),
            new BoBa(MenuLinkType.class, "MenuLinkType", "ck_menu_items_link_type", null, CMS_TYPES),
            new BoBa(MenuPosition.class, "MenuPosition", "ck_menu_items_position", null, CMS_TYPES),
            new BoBa(BackupStatus.class, "BackupStatus", "ck_system_backups_status", "BACKUP_STATUS"),
            new BoBa(MaintenanceType.class, "MaintenanceType", "ck_maintenance_logs_work_type", "MAINTENANCE_TYPE"),
            new BoBa(IncidentSeverity.class, "IncidentSeverity", "ck_maintenance_logs_severity", "INCIDENT_SEVERITY"),
            new BoBa(TrangThaiBaoCaoNhanh.class, "TrangThaiBaoCaoNhanh", "ck_bao_cao_nhanh_trang_thai", null),
            // ⚠ `ck_operation_status_codes_mapped` là ràng buộc trên bảng **danh mục mã tình hình vận
            //   hành** (quy tắc 16 — dữ liệu có CRUD): nó ghim cột *ánh xạ sang trạng thái nào*, nên
            //   nó phải khớp CÙNG enum với `ck_constructions_operational_status`. Hai ràng buộc, một
            //   enum — và đó chính là chỗ chúng có thể lệch nhau.
            new BoBa(OperationalStatus.class, "OperationalStatus", "ck_operation_status_codes_mapped", null),
            new BoBa(AdapterType.class, "AdapterType", "ck_api_sources_adapter", null),
            new BoBa(ApiSourceStatus.class, "ApiSourceStatus", "ck_api_sources_status", null),
            new BoBa(AlertConditionType.class, "AlertConditionType", "ck_alert_rules_condition", null),
            new BoBa(AlertEventStatus.class, "AlertEventStatus", "ck_alert_events_status", null),
            new BoBa(SyncStatus.class, "SyncStatus", "ck_sync_logs_status", null),
            new BoBa(SyncFailureKind.class, "SyncFailureKind", "ck_sync_logs_failure_kind", null),
            // ⛔⛔ Quy tắc 14 ở dạng đắt nhất: **ba** ràng buộc dưới đây dùng chung hai enum, nên một
            //    lượt thêm giá trị mà quên một tệp migration là lệch NGAY — và lệch ở đây nổ thành
            //    500 lúc ghi số liệu thuỷ văn, tức quy tắc 18 (*mất dữ liệu là vĩnh viễn*).
            new BoBa(ReadingQuality.class, "ReadingQuality", "ck_hydro_readings_quality", null),
            new BoBa(ReadingQuality.class, "ReadingQuality", "ck_hydro_agg_daily_quality", null),
            new BoBa(ReadingSource.class, "ReadingSource", "ck_hydro_readings_source", null),
            new BoBa(ReadingSource.class, "ReadingSource", "ck_hydro_latest_source", null),
            new BoBa(PositionRole.class, "PositionRole", "ck_stations_position_role", null),
            new BoBa(PositionRole.class, "PositionRole", "ck_station_constructions_role", null));

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
     *
     * <h2>⛔⛔ Hai khe mù đo được ngày 23/09/2026 (T52.7)</h2>
     *
     * <p><b>(1) Bản sao trong {@code target/}.</b> Vị từ cũ {@code contains("/db/migration/")} khớp
     * <b>cả</b> {@code backend/&lt;m&gt;/target/classes/db/migration/…}. Số đo: <b>180</b> tệp khớp,
     * trong đó <b>90</b> là bản sao. Hôm nay nó vô hại vì hai bản cùng tên, cùng nội dung — nhưng
     * phép sắp chỉ so <b>tên tệp</b>, nên khi một bản {@code target/} <b>cũ</b> còn nằm lại (đúng
     * chuyện đã xảy ra ở WS-62 với {@code .class} và {@code jacoco.exec}), <i>"định nghĩa mới nhất"</i>
     * có thể là định nghĩa <b>đã hết hạn</b> — và thứ tự giữa hai tệp trùng tên là <b>⛔ xác định</b>.
     *
     * <p><b>(2) {@code db/seed/} ⛔ nằm trong tầm quét.</b> {@code content} có
     * {@code db/seed/portal/} với <b>3</b> tệp {@code V*.sql}, và đó là <b>một vị trí Flyway THẬT</b>
     * — chung {@code flyway_schema_history}, chung không gian số hiệu. Đây đúng hình dạng đã trả giá
     * ở <b>T49.1</b>: {@code MigrationNamingTest} liệt kê tay 5 thư mục nên {@code db/seed/portal}
     * ở ngoài tầm quét <b>16 ngày</b>, và trong khe mù ấy có một nạn nhân còn sống của §10.66.
     * Đo 23/09: 3 tệp seed có <b>0</b> dòng {@code CONSTRAINT} ⇒ hôm nay khe mù này che <b>0</b> ràng
     * buộc. Vá nó vì <i>một lỗ chưa gây hại vẫn là một lỗ</i>, ⛔ phải vì đã có nạn nhân.
     *
     * <p>⇒ Phạm vi nay là <b>mọi</b> {@code V*.sql} dưới {@code src/main/resources/db/} — đo từ đĩa,
     * ⛔ liệt kê tay thư mục con. Bài {@link #phamViDoTuDiaChuKhongGoTay} giữ điều đó.
     */
    private static List<Path> moiMigration() throws IOException {
        try (var duyet = Files.walk(gocKho().resolve("backend"))) {
            return duyet.filter(p -> p.toString().contains("/src/main/resources/db/"))
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
                .hasSize(41);
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
     * <h2>⛔⛔ Lý do "vì sao chỉ MỘT enum" ở bản trước là một DỰ ĐOÁN, và nó SAI (T81.1)</h2>
     *
     * <p>Bản javadoc trước khai — kèm chữ <i>"đây là số đo chứ ⛔ phải phỏng đoán"</i> — rằng
     * {@code CONSTRUCTION_STATUS} <b>gộp hai enum</b>: nó chứa {@code DA_THANH_LY}, <i>"một giá trị
     * của {@link LifecycleState}, bên cạnh <b>năm</b> giá trị của {@link OperationalStatus}"</i>,
     * nên ép bằng nhau là <i>"dựng một bài kiểm đỏ vĩnh viễn cho một thiết kế đúng"</i>.
     *
     * <p>Đo lại <b>23/09/2026</b>: {@link OperationalStatus} có <b>SÁU</b> hằng, và
     * {@code DA_THANH_LY} là một trong số đó ({@code NGUNG_MUA_VU} cũng có ở <b>cả hai</b> enum).
     * Hai tập <b>bằng nhau</b> ⇒ lời tiên đoán <i>"đỏ vĩnh viễn"</i> sai, và nó đã một mình giữ nơi
     * thứ tư ở mức <b>1/13</b> suốt hơn một tháng.
     *
     * <p>⇒ Đây là lần thứ <b>ba</b> trong cùng một PR một <b>lý do miễn trừ</b> hoá ra là dữ liệu
     * chưa kiểm (sau dòng {@code core} của {@code CotPhase2CoDocGhiTest} và lý do
     * <i>"⛔ có union TS"</i> của hai enum {@code sluice_specs}). Một dòng miễn trừ viết bằng giọng
     * chắc chắn <b>đọc y hệt</b> một phép đo — nên nay {@link #moiHangTuVungDeuDuocXepLoai} bắt
     * <b>mọi</b> hằng phải được xếp loại, và một lý do muốn đứng vững thì phải nói ra <b>lệnh đo</b>
     * của nó (xem {@link #TU_VUNG_DUOC_MIEN}).
     *
     * <p>Số đo 23/09: <b>14</b> hằng {@code StatusVocabulary} · canh <b>11</b> · miễn <b>3</b>
     * ({@code HEALTH_STATUS} · {@code KPI_TONE} · {@code MAINTENANCE_STATUS} — cả ba <b>⛔ có</b>
     * enum Java, đo bằng {@code grep} ghi thẳng trong lý do).
     *
     * <p>⚠ Mẫu số từng đi 13 → 12 vì {@code SCAN_STATUS} bị <b>gỡ</b> (T68.33): nó có <b>đúng một</b>
     * lượt xuất hiện toàn kho — chính định nghĩa của nó. <b>Thêm {@code ERROR} vào nó cho "khớp enum
     * Java" là đánh bóng một hằng ⛔ ai đọc</b> rồi ghi sổ rằng đã trả nợ (luật 15). Rồi 12 → 14 khi
     * T68.28 thêm {@code SLUICE_TYPE}/{@code GATE_OPERATION}.
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

        // ⚠ Vế chống tập rỗng (luật 7 + 29), và nó là một **trần chỉ-được-TĂNG**: con số ở đây đếm
        //    bao nhiêu enum đã được canh ở nơi thứ tư. Nó bắt đúng lượt sửa T68.33 ở lần chạy đầu
        //    (1 → 3) — nghĩa là gỡ một `tenTuVung` đi cũng sẽ đỏ, thay vì im lặng thu hẹp phạm vi.
        //
        // ⛔⛔ Và một bánh cóc chỉ cắn khi con số ĐI THEO phạm vi thật. T68.28 (23/09) nâng phạm vi
        //    3 → 5 (`SLUICE_TYPE` · `GATE_OPERATION`) mà **quên nâng sàn** ⇒ suốt lượt ấy gỡ hai
        //    `tenTuVung` đi vẫn XANH. Một trần chỉ-được-tăng mà ⛔ ai tăng thì nó chỉ còn là một
        //    con số. ⇒ Nay `moiHangTuVungDeuDuocXepLoai` giữ vế *ĐỦ*, còn dòng này giữ vế *⛔ TỤT*;
        //    sửa `BO_BA` mà quên đây là một lượt CI đỏ, ⛔ phải một khe im lặng.
        assertThat(daCanh)
                .as(
                        "⛔ số enum được canh ở nơi thứ tư TỤT xuống %d — gỡ một `tenTuVung` là thu hẹp "
                                + "phạm vi trong im lặng (luật 28). Thêm thì sửa số này LÊN, ⛔ đừng hạ.",
                        daCanh)
                .isGreaterThanOrEqualTo(11);
    }

    @Test
    @DisplayName("⛔⛔ Tự-kiểm: bộ đọc phải thấy giá trị có CHỮ SỐ — mẫu cũ `[A-Z_]+` thì ⛔ không")
    void boDocThayGiaTriCoChuSo() {
        // ⛔⛔ Ca hỏng THẬT: `CHO_DUYET_2` là giá trị đầu tiên của kho mang một chữ số. Với mẫu cũ,
        //    nó biến mất ở CẢ BA nguồn cùng lúc ⇒ ba tập vẫn BẰNG NHAU ⇒ bài chính XANH trong khi
        //    một cấp duyệt có thể đã biến mất khỏi một trong ba nơi (luật 7, luật 28).
        assertThat(bocChuoiNhay("'CHO_DUYET' | 'CHO_DUYET_2' | 'DA_DUYET'", '\''))
                .containsExactly("CHO_DUYET", "CHO_DUYET_2", "DA_DUYET");

        // Vế phân biệt: ⛔ không được nuốt thứ KHÔNG phải hằng enum — chuỗi thường và số trần.
        assertThat(bocChuoiNhay("'CHO_DUYET_2', 'abc', '2026', 'Ghi chú'", '\''))
                .containsExactly("CHO_DUYET_2");
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

    // ========================= T52.7 · T81.1 — VẾ TRÁI ĐO TỪ ĐĨA =========================

    /**
     * Ràng buộc {@code CHECK … IN (…)} <b>có tên</b> mà bài này cố ý ⛔ đối chiếu, kèm lý do.
     *
     * <p>⛔⛔ <b>Một dòng ở đây ⛔ phải một lời miễn trừ tuỳ ý.</b> Bài
     * {@link #coUnionTsThiKhongDuocMien} <b>ĐO</b> xem ràng buộc ấy có một union TypeScript nào khai
     * đúng tập giá trị của nó ⛔; có thì dòng miễn trừ bị <b>từ chối</b>. Nói cách khác: chỉ ràng
     * buộc <b>⛔ có nơi thứ hai để mà lệch</b> mới được nằm đây, và điều đó do phép đo phán xử chứ
     * ⛔ do người viết tự nhận.
     *
     * <p>⚠ Lý do phải ≥ 40 ký tự — cùng ràng buộc với {@code MaLoiCoNoiNemTest} và
     * {@code VongKhuHoiDuPhamViTest}. Nó bắt đúng dòng miễn trừ 33 ký tự của chính tôi ở §10.69.
     */
    private static final java.util.Map<String, String> DUOC_MIEN = java.util.Map.ofEntries(
            // ── Nhóm A: có enum Java + CHECK, nhưng ⛔ union TS ⇒ HAI nơi, ⛔ phải ba ────────────
            // ⚠⚠ HAI nơi vẫn lệch được, và lệch ở đó nổ thành 500 lúc GHI. Bài này chỉ hỏi câu
            //    *"ba nơi có cùng một bộ giá trị ⛔"*; câu *"Java ↔ SQL có khớp ⛔"* là một câu KHÁC
            //    và hiện ⛔ ai hỏi — đã mở `T85.11` kèm số đo, ⛔ giấu sau một dòng miễn trừ.
            java.util.Map.entry(
                    "ck_attachments_status",
                    "Enum `AttachmentStatus` có thật, nhưng 0 union TS nào khai đúng bộ "
                            + "UPLOADING/READY/QUARANTINED/FAILED — vòng đời tệp là việc của backend, "
                            + "giao diện chỉ đọc `SYS-0009`. Hai nơi ⇒ câu hỏi Java↔SQL, xem T85.11"),
            java.util.Map.entry(
                    "ck_attachments_scan_status",
                    "Enum `ScanStatus` có thật; 0 union TS khai bộ PENDING/CLEAN/INFECTED/ERROR/SKIPPED. "
                            + "ClamAV chưa chạy ở môi trường nào (T61.4) nên SKIPPED là giá trị thực tế "
                            + "duy nhất — giao diện ⛔ có ô nào chọn nó. Xem T85.11"),
            java.util.Map.entry(
                    "ck_audit_logs_action",
                    "Enum `AuditAction` có thật với 16 giá trị; 0 union TS khai chúng — màn hình nhật ký "
                            + "kiểm toán lọc bằng chuỗi máy chủ trả về, ⛔ bằng một danh sách gõ ở FE. "
                            + "Hai nơi ⇒ xem T85.11"),
            java.util.Map.entry(
                    "ck_notifications_severity",
                    "Enum `NotificationSeverity` có thật (INFO/WARNING/DANGER/CRITICAL); 0 union TS. "
                            + "Mức độ đi kèm sẵn nhãn trong tải trọng thông báo nên FE ⛔ cần danh sách"),
            java.util.Map.entry(
                    "ck_notification_recipients_channel",
                    "Enum `NotificationChannel` có thật (IN_APP/EMAIL/SMS/WEB_PUSH); 0 union TS. SMS và "
                            + "WEB_PUSH chưa có nơi gửi nào — đo 10/09: EMAIL 154 FAILED, IN_APP 242/1 đọc"),
            java.util.Map.entry(
                    "ck_notification_recipients_status",
                    "Enum `RecipientStatus` có thật (PENDING/SENT/FAILED/SKIPPED); 0 union TS. Đây là "
                            + "trạng thái GIAO của một dòng người nhận — đo 10/09 trên kênh EMAIL: 154 "
                            + "FAILED · 88 SKIPPED · 0 SENT, và ⛔ màn hình nào bày bộ ấy ra. Xem T85.11"),
            java.util.Map.entry(
                    "ck_org_units_unit_type",
                    "Enum `OrgUnitType` có thật (CONG_TY/XI_NGHIEP/PHONG_BAN/TO_DOI/KHAC); 0 union TS khai "
                            + "đúng bộ ấy. Cây đơn vị dựng từ dữ liệu máy chủ trả về, ⛔ từ hằng ở FE"),
            // ── Nhóm B: ⛔ có enum Java — CHECK là nơi DUY NHẤT giữ danh sách ────────────────────
            java.util.Map.entry(
                    "ck_articles_status",
                    "⛔ có enum Java: trạng thái bài viết đổi QUA Workflow engine (quy tắc 4) và nguồn sự "
                            + "thật là `workflow_transitions`, ⛔ phải một enum. Ép một enum vào đây là "
                            + "dựng nguồn sự thật thứ hai cho cùng một luồng"),
            java.util.Map.entry(
                    "ck_maintenance_logs_status",
                    "⛔ có enum Java — `MaintenanceLog.status` là `String` khởi tạo bằng hằng "
                            + "`MaintenanceState.MOI`, cố ý theo quy tắc 4 (trạng thái sống ở "
                            + "`workflow_transitions`). Cùng lý lẽ với `ck_articles_status`"),
            java.util.Map.entry(
                    "ck_sessions_revoked_reason",
                    "⛔ có enum Java cho 8 lý do thu hồi phiên (ROTATED/REUSE_DETECTED/…): chúng là nhãn "
                            + "chẩn đoán ghi vào bảng phiên, ⛔ phải một giá trị nghiệp vụ ai đó chọn. "
                            + "Màn hình phiên hiện câu tiếng Việt máy chủ dựng sẵn"),
            java.util.Map.entry(
                    "ck_security_events_severity",
                    "⛔ có enum Java; bốn mức trùng khít `ck_notifications_severity` nhưng là bảng KHÁC "
                            + "(`security_events`). Gộp chúng là trộn hai vòng đời dữ liệu khác nhau — "
                            + "nhật ký bảo mật giữ 5 năm, thông báo thì ⛔"),
            java.util.Map.entry(
                    "ck_settings_value_type",
                    "⛔ có enum Java cho 14 kiểu giá trị (STRING/HTML/CRON/COLOR/…): đây là siêu dữ liệu "
                            + "của chính bảng `settings`, đọc bởi màn hình cấu hình động chứ ⛔ bởi một "
                            + "kiểu TS — thêm kiểu mới là thêm một ô nhập, ⛔ một nhánh mã"),
            java.util.Map.entry(
                    "ck_maintenance_logs_acceptance",
                    "Enum `AcceptanceResult` có thật (DAT/CHUA_DAT/DANG_THEO_DOI); 0 union TS khai bộ ấy. "
                            + "Kết quả nghiệm thu chỉ hiện ở bản in và ô chọn dựng từ dữ liệu máy chủ"),
            java.util.Map.entry(
                    "ck_menu_items_target",
                    "Chỉ HAI giá trị (URL/EXTERNAL_DOC) và chúng là **tập con** của `MenuLinkType` đã nằm "
                            + "trong BO_BA — ràng buộc này chỉ nói *link_type nào được phép mở tab mới*, "
                            + "⛔ phải một danh sách giá trị thứ hai"),
            java.util.Map.entry(
                    "ck_employees_terminated_pairs",
                    "⛔ phải một danh sách giá trị mà là một luật CẶP: nó bắt `terminated_at` phải có khi "
                            + "trạng thái là NGHI_VIEC/NGHI_HUU. Hai giá trị ấy là tập con của "
                            + "`EmploymentStatus` — enum ấy đã được canh ở dòng riêng của nó"),
            java.util.Map.entry(
                    "ck_hydro_latest_quality",
                    "Tập con CỐ Ý của `ReadingQuality`: bảng `hydro_latest` chỉ giữ HOP_LE/NGHI_NGO, ⛔ giữ "
                            + "XOA — một bản ghi đã xoá thì ⛔ còn là *số mới nhất* của trạm nào cả. "
                            + "Ép bằng nhau ở đây là dựng bài kiểm đỏ cho một thiết kế đúng"),
            java.util.Map.entry(
                    "ck_hydro_raw_logs_failure_kind",
                    "Tập con CỐ Ý của `SyncFailureKind`: `hydro_raw_logs` ⛔ có THIEU_MA_SO vì lượt gọi "
                            + "thiếu mã số hỏng TRƯỚC khi mở HTTP nên ⛔ sinh được dòng nhật ký thô nào "
                            + "(đúng cơ chế đã cho 3323 lượt hỏng / 0 byte — T50.1)"),
            // ── Nhóm C: giá trị ⛔ phải hằng enum ────────────────────────────────────────────────
            java.util.Map.entry(
                    "ck_permissions_module",
                    "Giá trị VIẾT THƯỜNG ('cms','ops','hyd','hr','adm') — chúng là tiền tố mã quyền, ⛔ "
                            + "phải hằng enum Java. `RbacMatrixTest` mới là nơi canh danh mục quyền, và "
                            + "nó hỏi một câu khác hẳn: mã quyền nào có đầu nhận"),
            java.util.Map.entry(
                    "ck_audit_logs_module",
                    "Giá trị VIẾT THƯỜNG và có thêm 'core' so với `ck_permissions_module` — đúng vì `core` "
                            + "ghi nhật ký nhưng ⛔ phát hành mã quyền nào. Hai danh sách gần giống nhau "
                            + "mà KHÁC nhau có chủ đích; gộp là xoá mất phân biệt ấy"),
            // ── Nhóm D: dữ liệu của Công ty, ⛔ phải enum của mã ─────────────────────────────────
            java.util.Map.entry(
                    "ck_don_vi_hanh_chinh_cong_ty",
                    "Bốn đơn vị hành chính trong mẫu Báo cáo nhanh của Công ty (SONG_NHUE/SONG_DAY/"
                            + "SONG_TICH/HA_NOI) — chúng đến từ BỐ CỤC tờ mẫu (CN-02.12), ⛔ từ mã. Mẫu đổi "
                            + "thì đổi migration, ⛔ đổi một enum"),
            java.util.Map.entry(
                    "ck_bcn_vi_tri_loai",
                    "Hai loại vị trí đo của Báo cáo nhanh (CONG/TRAM_BOM) là tập con CỐ Ý của "
                            + "`ConstructionType` — tờ mẫu chỉ có hai khối ấy. `ConstructionType` đầy đủ "
                            + "đã được canh ở dòng riêng của nó"));

    /**
     * Hằng {@code StatusVocabulary} cố ý ⛔ đối chiếu với một enum Java, kèm lý do đo được.
     *
     * <p>⛔ Nơi thứ tư ⛔ phải lúc nào cũng có nơi thứ nhất: vài bảng nhãn mô tả một <b>giá trị dẫn
     * xuất</b> backend tính ra rồi gửi xuống, ⛔ phải một enum có thật trong mã.
     */
    private static final java.util.Map<String, String> TU_VUNG_DUOC_MIEN = java.util.Map.ofEntries(
            java.util.Map.entry(
                    "HEALTH_STATUS",
                    "UP/DOWN/OUT_OF_SERVICE/UNKNOWN là hằng của Spring Boot Actuator, ⛔ phải enum của "
                            + "kho: `grep -rl OUT_OF_SERVICE backend --include=*.java` ⇒ 0 tệp. Đúc một "
                            + "enum để bộ canh này có cái đối chiếu là dựng nguồn sự thật thứ hai cho "
                            + "thứ thư viện định nghĩa"),
            java.util.Map.entry(
                    "KPI_TONE",
                    "NORMAL/WARNING/DANGER/UNKNOWN là sắc thái HIỂN THỊ của ô KPI, ⛔ phải một giá trị "
                            + "lưu ở đâu cả: `grep -rln KpiTone backend --include=*.java` ⇒ 0 tệp, và ⛔ "
                            + "ràng buộc CHECK nào mang bộ ấy. Nó là kiểu của giao diện"),
            java.util.Map.entry(
                    "MAINTENANCE_STATUS",
                    "MOI/DANG_XU_LY/DA_XU_LY có ràng buộc `ck_maintenance_logs_status` nhưng ⛔ có enum "
                            + "Java — `MaintenanceLog.status` là `String` theo quy tắc 4 (trạng thái đổi "
                            + "qua Workflow engine). ⛔ có enum thì ⛔ có vế trái để đối chiếu"));

    @Test
    @DisplayName("⭐⭐ T52.7: mọi ràng buộc CHECK…IN có tên ĐỀU phải được xếp loại — vế trái ĐO từ đĩa")
    void moiRangBuocDeuDuocXepLoai() throws IOException {
        Set<String> trenDia = moiRangBuocCoTen(moiMigration());

        assertThat(trenDia)
                .as("⛔ bộ đọc ràng buộc trả tập rỗng/quá nhỏ — đổi cách khai `CONSTRAINT … CHECK`? "
                        + "Một bài chạy qua tập rỗng vẫn xanh trọn vẹn (luật 7)")
                .hasSizeGreaterThanOrEqualTo(60);

        Set<String> daCanh = BO_BA.stream().map(BoBa::tenRangBuoc).collect(Collectors.toSet());

        assertThat(DUOC_MIEN.keySet())
                .as("⛔ dòng miễn trừ trỏ tới ràng buộc ⛔ CÒN TỒN TẠI — nó đã MỤC. Một dòng miễn "
                        + "trừ chết đọc y hệt một quyết định thiết kế (T51.0)")
                .isSubsetOf(trenDia);
        assertThat(DUOC_MIEN.keySet())
                .as("⛔ một ràng buộc vừa nằm trong BO_BA vừa xin miễn — hai câu trả lời cho cùng một câu hỏi")
                .doesNotContainAnyElementsOf(daCanh);

        DUOC_MIEN.forEach((ten, lyDo) -> assertThat(lyDo.length())
                .as("lý do miễn `%s` chỉ %d ký tự — một câu quá ngắn ⛔ mang được phép ĐO nào", ten, lyDo.length())
                .isGreaterThanOrEqualTo(40));

        Set<String> chuaXep = new java.util.TreeSet<>(trenDia);
        chuaXep.removeAll(daCanh);
        chuaXep.removeAll(DUOC_MIEN.keySet());

        assertThat(chuaXep)
                .as(
                        """
                        ⛔ %d ràng buộc `CHECK … IN (…)` có tên ⛔ được xếp loại.
                        Mỗi cái phải hoặc vào `BO_BA` (đối chiếu ba nơi), hoặc vào `DUOC_MIEN` kèm lý do ĐO ĐƯỢC ≥ 40 ký tự.
                        ⛔ Đây ⛔ phải thủ tục giấy tờ: vế trái của bộ canh này trước 23/09/2026 là một danh sách GÕ TAY,
                        nên một ràng buộc mới ra đời là vô hình — đúng hình dạng đã để `hr` nằm ngoài tầm quét bốn ngày (T63.7).
                        Danh sách: %s""",
                        chuaXep.size(), chuaXep)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ T52.7: ràng buộc CÓ union TypeScript khớp thì ⛔ được xin miễn — đó là rủi ro THẬT")
    void coUnionTsThiKhongDuocMien() throws IOException {
        // Bánh cóc ĐÚNG ⛔ phải *"mọi CHECK phải có mặt"* mà là *"mọi enum có CẢ union TS VÀ CHECK
        // thì phải có mặt"* — chỉ khi có nơi thứ hai thì mới có chỗ để lệch. Vị từ ấy nay do phép ĐO
        // phán xử, ⛔ do người viết dòng miễn trừ tự nhận.
        List<Path> migration = moiMigration();
        java.util.Map<String, Set<String>> union = moiUnionTs();

        assertThat(union)
                .as("⛔ ⛔ quét được union TypeScript nào dưới frontend/ — đổi cách khai `export type`? "
                        + "Tập rỗng ở đây làm bài này XANH với MỌI dòng miễn trừ (luật 7)")
                .hasSizeGreaterThanOrEqualTo(30);

        for (var muc : DUOC_MIEN.entrySet()) {
            Set<String> csdl = giaTriCsdlMoiNoi(migration, muc.getKey());
            List<String> khop = union.entrySet().stream()
                    .filter(u -> u.getValue().equals(csdl) && !csdl.isEmpty())
                    .map(java.util.Map.Entry::getKey)
                    .sorted()
                    .toList();

            assertThat(khop)
                    .as(
                            """
                            ⛔ `%s` xin miễn với lý do "%s"
                            — nhưng CÓ union TypeScript khai ĐÚNG tập giá trị của nó: %s
                            Ba nơi ⇒ có chỗ để lệch ⇒ phải vào `BO_BA`, ⛔ phải xin miễn.""",
                            muc.getKey(), muc.getValue(), khop)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("⭐⭐ T81.1: mọi hằng StatusVocabulary ĐỀU phải được xếp loại — vế trái ĐO từ đĩa")
    void moiHangTuVungDeuDuocXepLoai() throws IOException {
        Set<String> trenDia = tenHangTuVung(Files.readString(TU_VUNG, StandardCharsets.UTF_8));

        assertThat(trenDia)
                .as("⛔ ⛔ bóc được hằng `StatusVocabulary` nào — đổi cách khai?")
                .hasSizeGreaterThanOrEqualTo(12);

        Set<String> daBuoc = BO_BA.stream()
                .map(BoBa::tenTuVung)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(daBuoc)
                .as("⛔ một dòng `tenTuVung` trỏ tới hằng ⛔ CÒN TỒN TẠI ở statusVocabulary.ts")
                .isSubsetOf(trenDia);
        assertThat(TU_VUNG_DUOC_MIEN.keySet())
                .as("⛔ dòng miễn trừ trỏ tới hằng ⛔ còn tồn tại — nó đã MỤC")
                .isSubsetOf(trenDia);

        TU_VUNG_DUOC_MIEN.forEach((ten, lyDo) -> assertThat(lyDo.length())
                .as("lý do miễn `%s` chỉ %d ký tự", ten, lyDo.length())
                .isGreaterThanOrEqualTo(40));

        Set<String> chuaXep = new java.util.TreeSet<>(trenDia);
        chuaXep.removeAll(daBuoc);
        chuaXep.removeAll(TU_VUNG_DUOC_MIEN.keySet());

        assertThat(chuaXep)
                .as(
                        """
                        ⛔ %d hằng `StatusVocabulary` ⛔ được nơi nào đối chiếu với enum Java.
                        Thiếu một nhãn thì `StatusBadge` rơi vào nhánh dự phòng và IN MÃ TRẦN giữa những dòng tiếng Việt
                        — đúng thứ đã đo được ở `USER_STATUS` (T68.33), im lặng suốt 38 ngày.
                        Mỗi hằng phải hoặc được một dòng `BO_BA` trỏ tới qua `tenTuVung`, hoặc vào `TU_VUNG_DUOC_MIEN`
                        kèm lý do ĐO ĐƯỢC ≥ 40 ký tự. Danh sách: %s""",
                        chuaXep.size(), chuaXep)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Tự-kiểm: phạm vi migration ĐO từ đĩa, ⛔ gõ tay — và ⛔ nuốt bản sao ở target/")
    void phamViDoTuDiaChuKhongGoTay() throws IOException {
        List<Path> migration = moiMigration();

        assertThat(migration)
                .as("⛔ tệp nào dưới `src/main/resources/db/` — đường dẫn đổi? Nó làm MỌI tập giá trị "
                        + "CSDL về rỗng và bài chính đỏ vì lý do SAI")
                .hasSizeGreaterThanOrEqualTo(80);
        assertThat(migration.stream().map(Path::toString).filter(p -> p.contains("/target/")))
                .as("⛔⛔ quét trúng bản sao trong `target/` — hai tệp TRÙNG TÊN thì phép sắp theo tên "
                        + "⛔ xác định thứ tự, nên *'định nghĩa mới nhất'* có thể là bản ĐÃ HẾT HẠN "
                        + "(đúng chuyện `.class`/`jacoco.exec` cũ đã gây ra ở WS-62)")
                .isEmpty();
        assertThat(migration.stream().map(Path::toString).filter(p -> p.contains("/db/seed/")))
                .as("⛔⛔ `db/seed/` rơi ra ngoài tầm quét — nó là MỘT VỊ TRÍ FLYWAY THẬT (chung "
                        + "`flyway_schema_history`, chung không gian số hiệu). Đúng khe mù đã giấu một "
                        + "nạn nhân còn sống suốt 16 ngày ở T49.1")
                .isNotEmpty();
    }

    @Test
    @DisplayName("⛔⛔ Tự-kiểm: bộ đọc ràng buộc chịu được chú thích `--` và danh sách xuống dòng")
    void boDocRangBuocKhongMuTruocChuThich() {
        String sql =
                """
                -- CONSTRAINT ck_trong_chu_thich CHECK (x IN ('A'))
                ALTER TABLE t ADD CONSTRAINT ck_that CHECK (
                    x IN (
                        'A',
                        'B'
                    )
                );
                ALTER TABLE t ADD CONSTRAINT ck_khong_co_in CHECK (n > 0);
                """;
        Set<String> tim = rangBuocTrong(sql);

        // Vế phân biệt phải đủ BA chiều, vì một bộ đọc sai theo bất kỳ chiều nào cũng cho tập KHÁC:
        assertThat(tim)
                .as("phải thấy ràng buộc có danh sách XUỐNG DÒNG (§11.13)")
                .contains("ck_that");
        assertThat(tim)
                .as("⛔ được đếm một ràng buộc nằm trong CHÚ THÍCH — T46.7: đếm chú thích là đếm nhầm")
                .doesNotContain("ck_trong_chu_thich");
        assertThat(tim)
                .as("⛔ được đếm ràng buộc ⛔ có `IN (…)` — bài này chỉ nói về danh sách giá trị")
                .doesNotContain("ck_khong_co_in");
        assertThat(tim).hasSize(1);
    }

    @Test
    @DisplayName("⛔ Tự-kiểm: bộ đọc union TypeScript phân biệt được union giá trị với kiểu khác")
    void boDocUnionTsPhanBietDuoc() {
        var tim = unionTrong(
                """
                export type Mau = 'A' | 'B' | 'C';
                export type MotGiaTri = 'CHI_MOT';
                export type Ham = (x: number) => string;
                type KhongExport = 'X' | 'Y';
                """);

        assertThat(tim).containsKey("Mau");
        assertThat(tim.get("Mau")).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(tim)
                .as("union MỘT giá trị ⛔ phải một danh sách — nhận nó vào là mời dương tính giả")
                .doesNotContainKey("MotGiaTri");
        assertThat(tim).as("kiểu hàm ⛔ phải union giá trị").doesNotContainKey("Ham");
        assertThat(tim)
                .as("⛔ `export` thì ⛔ nơi nào ngoài tệp đọc được — ⛔ phải nơi thứ hai")
                .doesNotContainKey("KhongExport");
    }

    // -------------------------------------------------------------------------

    /** Tên mọi ràng buộc {@code CONSTRAINT &lt;ten&gt; CHECK (… IN (…))} trong cả chuỗi migration. */
    private static Set<String> moiRangBuocCoTen(List<Path> migration) throws IOException {
        Set<String> ket = new java.util.TreeSet<>();
        for (Path tep : migration) {
            ket.addAll(rangBuocTrong(Files.readString(tep, StandardCharsets.UTF_8)));
        }
        return ket;
    }

    /**
     * Tên các ràng buộc có danh sách {@code IN (…)} trong một tệp SQL.
     *
     * <p>⚠ Bỏ chú thích {@code --} <b>trước</b> khi tìm, nhưng <b>giữ nguyên chuỗi nháy đơn</b>:
     * cắt thô theo {@code --} sẽ nuốt phần sau một giá trị chứa dấu gạch. Cùng bài học T54.8, nơi
     * một bộ canh khớp trúng chuỗi trong chú thích rồi <i>phạt đúng người viết tài liệu tử tế</i>.
     */
    private static Set<String> rangBuocTrong(String sql) {
        String sach = boChuThichSql(sql);
        Set<String> ket = new java.util.TreeSet<>();
        Matcher m = Pattern.compile("CONSTRAINT\\s+([a-z_][a-z0-9_]*)\\s+CHECK\\s*\\(", Pattern.CASE_INSENSITIVE)
                .matcher(sach);
        while (m.find()) {
            String than = docTrongNgoac(sach, m.end() - 1);
            if (Pattern.compile("\\bIN\\s*\\(", Pattern.CASE_INSENSITIVE)
                    .matcher(than)
                    .find()) {
                ket.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return ket;
    }

    /** Bỏ chú thích {@code --} mà giữ nguyên chuỗi nháy đơn (kể cả {@code ''} thoát). */
    private static String boChuThichSql(String sql) {
        StringBuilder ra = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            if (sql.startsWith("--", i)) {
                int xuong = sql.indexOf('\n', i);
                i = xuong < 0 ? sql.length() : xuong;
            } else if (sql.charAt(i) == '\'') {
                int j = i + 1;
                while (j < sql.length()) {
                    if (sql.charAt(j) == '\'') {
                        if (j + 1 < sql.length() && sql.charAt(j + 1) == '\'') {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                ra.append(sql, i, Math.min(j + 1, sql.length()));
                i = j + 1;
            } else {
                ra.append(sql.charAt(i));
                i++;
            }
        }
        return ra.toString();
    }

    /**
     * Mọi union giá trị {@code export type X = 'A' | 'B';} trong <b>cả cây</b> {@code frontend/}.
     *
     * <p>⛔⛔ Quét cả cây chứ ⛔ hai tệp: T51.10(a) đã đo được rằng union ⛔ bắt buộc sống ở
     * {@code api-types.ts} — bốn enum HR khai cạnh nhãn của chúng, và đo 23/09 thì
     * {@code MenuLinkType} còn khai ở <b>hai</b> nơi ({@code admin-app/features/cms/types.ts} và
     * {@code public-web/src/lib/api.ts}). Một bộ dò một-tệp sẽ kết luận *"⛔ có nơi thứ hai"* cho
     * đúng những enum có tới ba.
     */
    private static java.util.Map<String, Set<String>> moiUnionTs() throws IOException {
        java.util.Map<String, Set<String>> ket = new java.util.LinkedHashMap<>();
        try (var duyet = Files.walk(gocKho().resolve("frontend"))) {
            List<Path> tep = duyet.filter(p -> !p.toString().contains("/node_modules/"))
                    .filter(p -> !p.toString().contains("/dist/"))
                    .filter(p -> {
                        String t = p.getFileName().toString();
                        return t.endsWith(".ts") || t.endsWith(".tsx");
                    })
                    .sorted()
                    .toList();
            for (Path p : tep) {
                unionTrong(Files.readString(p, StandardCharsets.UTF_8)).forEach(ket::putIfAbsent);
            }
        }
        return ket;
    }

    /** Union giá trị khai trong một tệp TS — chỉ nhận union có <b>từ hai giá trị trở lên</b>. */
    private static java.util.Map<String, Set<String>> unionTrong(String noiDung) {
        java.util.Map<String, Set<String>> ket = new java.util.LinkedHashMap<>();
        Matcher m = Pattern.compile("export\\s+type\\s+(\\w+)\\s*=([^;]*);", Pattern.DOTALL)
                .matcher(noiDung);
        while (m.find()) {
            Set<String> gt = bocChuoiNhay(m.group(2), '\'');
            if (gt.size() >= 2) {
                ket.putIfAbsent(m.group(1), gt);
            }
        }
        return ket;
    }

    /** Tên mọi hằng {@code export const X: StatusVocabulary = {…}} — vế trái của T81.1. */
    private static Set<String> tenHangTuVung(String noiDung) {
        Set<String> ket = new java.util.TreeSet<>();
        Matcher m = Pattern.compile("export\\s+const\\s+([A-Z][A-Z_0-9]*)\\s*:\\s*StatusVocabulary\\b")
                .matcher(noiDung);
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
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

    /**
     * Bóc mọi hằng dạng {@code 'TEN_HANG'} trong một đoạn — dùng cho <b>cả</b> union TypeScript
     * <b>lẫn</b> danh sách {@code IN (...)} của SQL.
     *
     * <h2>⛔⛔ Mẫu cũ {@code [A-Z_]+} MÙ trước mọi giá trị có CHỮ SỐ</h2>
     *
     * <p>Đo ngày 14/09/2026: {@code LeaveState.CHO_DUYET_2} là giá trị <b>đầu tiên</b> của kho mang
     * một chữ số, và bộ đọc ⛔ không thấy nó — ở <b>cả ba</b> nơi nó đọc. Lượt này nó đỏ đúng (TS
     * thiếu một giá trị mà TS thật ra <b>có</b>), nhưng hãy đọc ca ngược lại: nếu <b>Java</b> mất
     * {@code CHO_DUYET_2} trong khi TS và SQL vẫn còn, thì cả ba tập bóc ra đều thiếu nó như nhau
     * ⇒ <b>ba tập bằng nhau</b> ⇒ bộ canh <b>XANH</b> trong đúng tình huống nó sinh ra để bắt.
     *
     * <p>⇒ Đây là luật 28 ở dạng tinh vi nhất: phạm vi hụt ⛔ không nằm ở <i>tệp nào được quét</i>
     * mà ở <i>ký tự nào được nhận</i>. Chữ số ⛔ không phải một cách đặt tên lạ — mọi quy trình có
     * <b>hai cấp duyệt</b> đều sinh ra một trạng thái như vậy.
     */
    private static Set<String> bocChuoiNhay(String doan, char nhay) {
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = Pattern.compile(nhay + "([A-Z][A-Z0-9_]*)" + nhay).matcher(doan);
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
