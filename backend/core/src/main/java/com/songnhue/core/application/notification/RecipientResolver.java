package com.songnhue.core.application.notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;
import com.songnhue.core.spi.ChinhSachNguoiNhan;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Tìm người nhận cảnh báo theo <b>chốt G11</b> — T6.7.
 *
 * <p>Luật khách đã chốt: người nhận = nhóm <i>"Ban điều hành"</i> ∪ <i>người phụ trách công trình
 * liên quan</i>. Hai nguồn này chồng lấn nhiều (trưởng Xí nghiệp thường cũng nằm trong Ban điều
 * hành), nên khử trùng lặp không phải chi tiết kỹ thuật mà là yêu cầu nghiệp vụ: nhận hai email
 * giống hệt nhau cho một sự cố làm người ta bắt đầu bỏ qua cảnh báo.
 *
 * <p><b>Hai nguồn người nhận, và chúng KHÔNG lọc giống nhau:</b>
 *
 * <ul>
 *   <li><b>Suy ra từ nhóm</b> — nhóm "Ban điều hành" đọc từ {@code settings} (danh sách có CRUD,
 *       không phải vai trò cứng trong mã — quy tắc 16) và người đứng đầu/phó của đơn vị liên quan.
 *       Nhóm này lọc bỏ tài khoản đã khoá: hệ thống <i>đoán</i> ai nên biết, mà gửi cho tài khoản
 *       chết là cảnh báo rơi vào khoảng không trong khi bảng vẫn ghi "đã gửi".
 *   <li><b>Chỉ định đích danh</b> — nơi gọi nêu tên cụ thể (người được giao việc, chủ tài khoản vừa
 *       bị khoá). Nhóm này chỉ lọc "chưa bị xoá". Đây là quyết định nghiệp vụ của nơi gọi, không
 *       phải suy đoán của hệ thống, nên không được tự ý bỏ bớt.
 * </ul>
 *
 * <p>⚠ Phân biệt này đến từ một lỗi thật lúc chạy thử WS-6: lọc {@code ACTIVE} cho cả hai nguồn làm
 * thư "tài khoản của bạn vừa bị khoá" <b>không bao giờ tới nơi</b> — chính thao tác khoá đã loại
 * người nhận duy nhất ra khỏi danh sách.
 */
@Component
public class RecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolver.class);

    /** Danh sách id người dùng thuộc nhóm Ban điều hành, lưu dạng mảng JSON. */
    public static final String KEY_EXECUTIVE_BOARD = "notification.alert-group.executive-board";

    /** Có tự thêm người phụ trách đơn vị của công trình liên quan hay không. */
    public static final String KEY_AUTO_INCLUDE_OWNER = "notification.alert-group.auto-include-construction-owner";

    private final SettingService settings;
    private final OrgUnitRepository orgUnits;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public RecipientResolver(
            SettingService settings, OrgUnitRepository orgUnits, UserRepository users, ObjectMapper objectMapper) {
        this.settings = settings;
        this.orgUnits = orgUnits;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    /**
     * Tìm người nhận. <b>Chính sách do nơi gọi KHAI RA, ⛔ do hàm này suy từ hình dạng dữ liệu</b>
     * — T74.7, 20/09/2026.
     *
     * <h3>⛔⛔⛔ Vì sao chính sách phải là một THAM SỐ</h3>
     *
     * <p>Tới 20/09/2026 hàm này <b>suy</b> chính sách: ⛔ có {@code targetPermission} thì rơi thẳng
     * về {@link #executiveBoard()}. Nhánh ấy đúng cho cảnh báo vận hành (G11), nhưng <b>ba</b> nơi
     * gọi khác cũng rơi vào đó trong khi chúng <i>biết chính xác</i> người nhận là ai:
     *
     * <ul>
     *   <li>{@code WorkflowEngine} — <b>17 hàng</b> {@code workflow_transitions} mang
     *       {@code notify_owner = TRUE} và {@code notify_permission IS NULL} (đếm 20/09: ARTICLE 5 ·
     *       LEAVE_REQUEST 4 · MAINTENANCE_LOG 4 · MAINTENANCE_INCIDENT 4);
     *   <li>{@code UserAdminService.notifyStatusChange} — {@code ACCOUNT_DISABLED}/{@code _ENABLED};
     *   <li>{@code CanhBaoTaiKhoanService.bao} — bốn mã sự kiện an ninh của <b>một cá nhân</b>.
     * </ul>
     *
     * <p>⇒ Thư <i>"tài khoản của bạn đã bị khoá"</i> cộng thêm cả Ban điều hành. Hôm nay ⛔ ai thấy
     * vì khoá {@link #KEY_EXECUTIVE_BOARD} seed {@code '[]'} và chưa ai điền được (T76.3) — một
     * <b>khuyết tật đang ngủ vì một khuyết tật khác</b>, thức dậy trên cả sáu mã sự kiện cùng lúc
     * đúng ngày Công ty điền nhóm ấy.
     *
     * <p>⛔ Cách vá rẻ hơn — <i>"⛔ dùng {@code executiveBoard()} khi ⛔ có đơn vị nào được nêu"</i> —
     * <b>SAI</b>: nhóm cố định chính là nhánh <b>dự phòng</b> cho ca danh sách đơn vị RỖNG, và 4/19
     * điểm đo {@code MN_SONG} ⛔ thuộc công trình nào theo thiết kế (T33.8) nên ca ấy là ca THẬT.
     * Suy chính sách từ hình dạng dữ liệu lần nữa chỉ đổi một lỗi im lặng lấy một lỗi im lặng khác.
     *
     * <p>Bộ canh: {@code NhomCanhBaoKhongLanSangThuCaNhanTest} — ba bài, trong đó bài thứ ba là vế
     * phân biệt (luật 9): gỡ hẳn {@code executiveBoard()} cũng làm hai bài đầu xanh.
     *
     * <h3>⚠⚠ Đây là chỗ luật G11 KHÔNG áp dụng được, và biết điều đó là quan trọng</h3>
     *
     * Hai bài toán ngược nhau:
     *
     * <ul>
     *   <li><b>Cảnh báo vận hành</b> (G11) — hệ thống <i>đoán</i> ai nên biết: Ban điều hành ∪ người
     *       phụ trách công trình. Không ai "sở hữu" một mực nước vượt ngưỡng.
     *   <li><b>Quy trình duyệt</b> — nơi gọi <i>biết chính xác</i> ai cần biết: người có quyền duyệt,
     *       và người đã gửi lên. Cộng thêm Ban điều hành vào đây nghĩa là mỗi lần một biên tập viên
     *       bấm "Gửi duyệt" thì toàn bộ ban lãnh đạo Công ty nhận một email. Vài tuần sau là không
     *       ai đọc thông báo nữa — và lúc đó cảnh báo sự cố thật cũng chết theo.
     * </ul>
     *
     * <p>Nên khi có {@code targetPermission}, nhóm suy ra <b>thay thế</b> Ban điều hành chứ không
     * cộng dồn.
     *
     * <h3>⭐ Ca THỨ BA (T28.51, 08/09/2026) — nhắm đích <i>và</i> nêu đích danh đơn vị</h3>
     *
     * Nhắc SLA liên hệ cần cả hai: nhóm giữ quyền xử lý (cho việc <b>chưa</b> giao cho ai) <b>và</b>
     * người đứng đầu đơn vị <b>đang</b> giữ việc. Đó là phép <b>thu hẹp</b> — thêm đúng người có
     * trách nhiệm cụ thể — chứ ⛔ không phải nới lỏng luật ở trên, vốn nói về việc cộng cả một nhóm
     * mà ⛔ không ai trong đó có việc phải làm.
     *
     * <p>⚠ Vì thế điều kiện đổi từ <i>"⛔ không nhắm đích"</i> sang <i>"có đơn vị được nêu"</i>. Phép
     * đổi ấy <b>giữ nguyên</b> mọi hành vi cũ: {@code NotifyRequest.targeted} ghi cứng
     * {@code List.of()}, nên nhánh này chưa từng chạy cho quy trình duyệt và nay vẫn vậy.
     *
     * <p>⛔ Và công tắc {@link #KEY_AUTO_INCLUDE_OWNER} ⛔ <b>không</b> áp cho ca này: nó nói về phép
     * <i>đoán</i> của G11, ⛔ không nói về một danh sách mà nơi gọi đưa ra tường minh.
     *
     * <h3>⭐ Ca THỨ TƯ (T57.15, 20/09/2026) — nhắm đích theo quyền <b>trong phạm vi đơn vị</b></h3>
     *
     * <p>Người nhận = người có {@code targetPermission} mà phạm vi dữ liệu (đơn vị của tài khoản) PHỦ một trong
     * {@code relatedOrgUnitIds} — đúng người bộ lọc phạm vi tầng 3 cho THẤY bản ghi. ⛔ Cộng trưởng/phó riêng:
     * họ có quyền và phạm vi thì đã nằm trong tập, ⛔ có thì nhận thư về việc họ ⛔ làm được. Ba ca cũ ⛔ đổi
     * hành vi — ca này chỉ vào qua {@code NotifyRequest.targetedInUnitScope}.
     *
     * <h3>⚠ T85.4 (23/09/2026) — bốn ca trên nay là bốn HẰNG, ⛔ phải hai {@code boolean}</h3>
     *
     * <p>Hai tham số {@code trongPhamVi} và {@code nhomCanhBao} cùng kiểu và đứng cạnh nhau, nên một
     * lượt hoán vị biên dịch sạch và ⛔ cổng kiểm nào đỏ ở 6/12 nơi dựng (T82.2). Nay là một
     * {@link ChinhSachNguoiNhan}, và {@code switch} dưới đây <b>vét cạn</b> — thêm một ca thứ năm là
     * một lỗi biên dịch ở đúng chỗ phải quyết định, ⛔ phải một nhánh mặc định im lặng.
     *
     * @param relatedOrgUnitIds đơn vị liên quan tới sự kiện (VD đơn vị quản lý công trình có sự cố)
     * @param extraUserIds người nhận chỉ định thêm, VD người được giao việc hoặc chủ bản ghi
     * @param targetPermission mã quyền; {@code null} = ⛔ nhắm đích theo quyền. ⚠ Tính khớp giữa nó và
     *     {@code chinhSach} đã được ép ở hàm dựng hai record, xem {@link ChinhSachNguoiNhan#kiemKhopVoiQuyen}
     * @param chinhSach cách chọn người nhận, do nơi gọi KHAI RA
     * @return danh sách id người dùng đang hoạt động, đã khử trùng lặp, giữ thứ tự ổn định
     */
    @Transactional(readOnly = true)
    public List<Long> resolve(
            List<Long> relatedOrgUnitIds,
            List<Long> extraUserIds,
            String targetPermission,
            ChinhSachNguoiNhan chinhSach) {

        // LinkedHashSet: khử trùng lặp mà vẫn giữ thứ tự — thứ tự ổn định làm log dễ đối chiếu và
        // test không phụ thuộc thứ tự ngẫu nhiên của HashSet.
        // Hai nguồn, hai luật lọc khác nhau — xem ghi chú ở dưới.
        Set<Long> named = new LinkedHashSet<>(extraUserIds == null ? List.of() : extraUserIds);
        boolean coDonViDuocNeu = relatedOrgUnitIds != null && !relatedOrgUnitIds.isEmpty();

        // ⛔⛔ T74.7 — ca DICH_DANH trả RỖNG là bản vá. Trước 20/09/2026 mọi lượt gọi ⛔ nhắm đích
        //   đều rơi về `executiveBoard()`, kể cả thư "tài khoản của bạn đã bị khoá". Xem khối
        //   javadoc ⛔⛔⛔ ở trên.
        Set<Long> derived = new LinkedHashSet<>(
                switch (chinhSach) {
                    case THEO_QUYEN_TRONG_PHAM_VI ->
                        coDonViDuocNeu
                                ? users.findActiveIdsByPermissionCoveringOrgUnits(targetPermission, relatedOrgUnitIds)
                                : List.<Long>of();
                    case THEO_QUYEN -> users.findActiveIdsByPermission(targetPermission);
                    case NHOM_CANH_BAO -> executiveBoard();
                    case DICH_DANH -> List.<Long>of();
                });
        themNguoiDungDau(derived, relatedOrgUnitIds, coDonViDuocNeu, chinhSach);

        return locNguoiNhan(named, derived, chinhSach.nhamTheoQuyen(), targetPermission);
    }

    private void themNguoiDungDau(
            Set<Long> derived, List<Long> relatedOrgUnitIds, boolean coDonViDuocNeu, ChinhSachNguoiNhan chinhSach) {

        // ⭐⭐ T40/T28.51 — ca THỨ BA, và nó ⛔ không phải một ngoại lệ của luật trên.
        //
        // Hai ca cũ: `alert` (G11 — hệ thống ĐOÁN ai nên biết, nên cộng Ban điều hành và cấu hình
        // tắt được) và `targeted` (quy trình duyệt — nơi gọi biết chính xác, nên nhóm suy ra THAY
        // THẾ Ban điều hành). Cả hai đều để `relatedOrgUnitIds` rỗng ở nhánh nhắm đích.
        //
        // Ca mới: nhắm đích theo quyền **VÀ** nêu đích danh đơn vị đang chịu trách nhiệm. Đó là
        // phép THU HẸP người nhận, ⛔ không phải mở rộng — và cấu hình `auto-include-owner` ⛔ không
        // áp cho nó, vì nó nói về phép ĐOÁN của G11 chứ ⛔ không nói về một danh sách nơi gọi đưa ra.
        //
        // ⚠ Đổi từ `!nhamDich` sang "có đơn vị được nêu" GIỮ NGUYÊN mọi hành vi cũ: `targeted()`
        //   ghi cứng `List.of()`, nên nhánh này chưa từng chạy cho quy trình duyệt và nay vẫn vậy.
        //
        // ⚠⚠ Đính chính 20/09/2026 (T74.6): "THU HẸP" ở trên nghĩa là *thêm ÍT người có trách nhiệm*,
        //   ⛔ phải *bớt người* — tập suy ra vẫn là MỌI người có quyền trên toàn Công ty, cộng trưởng/phó.
        //   Ca cần BỚT người (chỉ ai phạm vi phủ đơn vị) là ca thứ tư, `trongPhamVi` (T57.15).
        // ⛔⛔ T74.7 (20/09, viết theo hai cờ CŨ) — vế `!nhamDich` đòi thêm `nhomCanhBao`; nay là hai
        //   hằng `NHOM_CANH_BAO` / `DICH_DANH` ở bộ `switch` dưới. Trưởng/phó đơn vị là **nửa thứ hai
        //   của phép ĐOÁN G11**, ⛔ phải một phần của "báo cho chủ bản ghi": một lá thư chỉ dành cho
        //   tác giả ⛔ có lý do gì đi kèm trưởng đơn vị của tác giả. Vế `nhamDich` (ca thứ ba,
        //   T28.51) giữ nguyên — ở đó nơi gọi nêu đơn vị một cách tường minh.
        //   ⚠ Đo 20/09: phép đổi này ⛔ đụng hành vi nào đang chạy — mọi lượt `!nhamDich` có đơn vị
        //   được nêu hôm nay đều đến từ `alert(...)`, và `alert(...)` khai nhóm cảnh báo.
        //
        // ⚠ T85.4 — bốn nhánh dưới đây là bốn ca CŨ viết lại nguyên hành vi, ⛔ một phép đổi luật:
        //   THEO_QUYEN ↔ `nhamDich` · NHOM_CANH_BAO ↔ `!nhamDich && nhomCanhBao` ·
        //   DICH_DANH ↔ `!nhamDich && !nhomCanhBao` (vế cũ cho `false`, vì `false || (false && …)`) ·
        //   THEO_QUYEN_TRONG_PHAM_VI thì trước nay ⛔ bao giờ gọi tới hàm này.
        boolean themNguoiDungDau = coDonViDuocNeu
                && switch (chinhSach) {
                    case THEO_QUYEN -> true;
                    case NHOM_CANH_BAO -> settings.getBoolean(KEY_AUTO_INCLUDE_OWNER, true);
                    case THEO_QUYEN_TRONG_PHAM_VI, DICH_DANH -> false;
                };
        if (themNguoiDungDau) {
            derived.addAll(orgUnits.findActiveHeadAndDeputyUserIds(relatedOrgUnitIds));
        }
    }

    private List<Long> locNguoiNhan(Set<Long> named, Set<Long> derived, boolean nhamDich, String targetPermission) {
        derived.removeAll(named);

        if (named.isEmpty() && derived.isEmpty()) {
            // Không phải lỗi kỹ thuật, nhưng là lỗi cấu hình đáng báo: một cảnh báo được sinh ra mà
            // không tới ai cả thì im lặng y như không có cảnh báo.
            if (nhamDich) {
                log.warn("Không tài khoản nào đang hoạt động có quyền '{}' — thông báo không tới ai", targetPermission);
            } else {
                log.warn("Không tìm được người nhận nào cho cảnh báo — kiểm tra nhóm '{}'", KEY_EXECUTIVE_BOARD);
            }
            return List.of();
        }

        // ⚠ Người nhận ĐÍCH DANH chỉ lọc "chưa bị xoá"; người nhận SUY RA TỪ NHÓM lọc thêm "đang
        // hoạt động". Ban đầu lọc ACTIVE cho cả hai, và thư "tài khoản của bạn vừa bị khoá" không
        // bao giờ tới nơi — chính thao tác khoá làm người nhận duy nhất bị loại khỏi danh sách.
        // Nơi gọi nêu tên cụ thể là một quyết định nghiệp vụ, không phải suy đoán của hệ thống.
        Set<Long> allowed = new LinkedHashSet<>();
        if (!named.isEmpty()) {
            allowed.addAll(users.findNotDeletedIdsIn(new ArrayList<>(named)));
        }
        if (!derived.isEmpty()) {
            List<Long> active = users.findActiveIdsIn(new ArrayList<>(derived));
            if (active.size() < derived.size()) {
                log.info("Bỏ {} người nhận suy ra từ nhóm do tài khoản đã khoá", derived.size() - active.size());
            }
            allowed.addAll(active);
        }

        // Giữ đúng thứ tự đã dựng (đích danh trước, nhóm sau) thay vì thứ tự DB trả về.
        Set<Long> ordered = new LinkedHashSet<>(named);
        ordered.addAll(derived);
        return ordered.stream().filter(allowed::contains).toList();
    }

    /**
     * Nhóm "Ban điều hành" từ bảng {@code settings}.
     *
     * <h2>⚠⚠ Giá trị là mảng {@code publicId} (UUID) — WS-33 sửa một lệch kiểu đã sống 21 ngày</h2>
     *
     * <p>Seed {@code V202608131009} mô tả khoá này là <i>"Danh sách publicId tài khoản, Admin sửa
     * (chốt G11)"</i>. Bản đầu của hàm này lại đọc {@code Long[].class}. Hai vế lệch nhau ở đúng chỗ
     * <b>không ai nhìn</b>, và triệu chứng thì im lặng hoàn hảo: Admin nhập đúng như nhãn dặn ⇒
     * Jackson ném ⇒ khối {@code catch} bên dưới nuốt thành một dòng {@code log.error} ⇒ nhóm rỗng ⇒
     * màn hình báo <i>lưu thành công</i> ⇒ mọi cảnh báo tới <b>0 người</b>, trong khi bảng
     * {@code notifications} vẫn đầy dòng.
     *
     * <p>Giá trị chưa từng khác {@code '[]'} nên chưa ai cắn phải — và đó chính là lý do nó sống
     * được tới hôm nay: một lỗi chỉ nổ khi tính năng bắt đầu được dùng thật.
     *
     * <p>⛔ Chữa ở phía mã, ⛔ không sửa nhãn: id nội bộ ⛔ không được lộ ra giao diện, và ô chọn
     * người dùng vốn gửi {@code publicId}.
     *
     * <p>Giá trị hỏng thì trả rỗng và ghi log, <b>không ném</b>: một dòng cấu hình sai không được
     * phép chặn luôn việc gửi cảnh báo cho những người nhận còn lại (người phụ trách công trình).
     */
    private List<Long> executiveBoard() {
        String raw = settings.getString(KEY_EXECUTIVE_BOARD).orElse("[]");
        List<UUID> publicIds;
        try {
            publicIds = List.of(objectMapper.readValue(raw, UUID[].class));
        } catch (JacksonException | IllegalArgumentException e) {
            log.error(
                    "Tham số '{}' không phải mảng publicId (UUID) hợp lệ — bỏ qua nhóm này. Giá trị: {}",
                    KEY_EXECUTIVE_BOARD,
                    raw,
                    e);
            return List.of();
        }
        if (publicIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.findIdsByPublicIds(publicIds);
        if (ids.size() < publicIds.size()) {
            // ⚠ Không ném: nhóm còn lại vẫn phải nhận được. Nhưng cũng ⛔ không im lặng — một
            //   publicId trỏ vào khoảng không nghĩa là ai đó đã bị xoá khỏi hệ thống mà danh sách
            //   này chưa được dọn, và không ai biết cho tới khi có người đếm.
            log.warn(
                    "Nhóm '{}' khai {} tài khoản nhưng chỉ tra được {} — có publicId không còn tồn tại",
                    KEY_EXECUTIVE_BOARD,
                    publicIds.size(),
                    ids.size());
        }
        return ids;
    }
}
