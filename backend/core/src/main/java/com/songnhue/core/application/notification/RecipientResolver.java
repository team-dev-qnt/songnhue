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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;

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
     * @param relatedOrgUnitIds đơn vị liên quan tới sự kiện (VD đơn vị quản lý công trình có sự cố)
     * @param extraUserIds người nhận chỉ định thêm, VD người được giao việc
     * @return danh sách id người dùng đang hoạt động, đã khử trùng lặp, giữ thứ tự ổn định
     */
    @Transactional(readOnly = true)
    public List<Long> resolve(List<Long> relatedOrgUnitIds, List<Long> extraUserIds) {
        return resolve(relatedOrgUnitIds, extraUserIds, null);
    }

    /**
     * Bản có <b>nhắm đích theo quyền</b> — thêm ở WS-13 khi bài viết là bản ghi đầu tiên đi qua một
     * quy trình duyệt thật.
     *
     * <p>⚠⚠ <b>Đây là chỗ luật G11 KHÔNG áp dụng được, và biết điều đó là quan trọng.</b> Hai chữ ký
     * trên trông gần giống nhau nhưng phục vụ hai bài toán ngược nhau:
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
     * @param targetPermission mã quyền; {@code null} = giữ nguyên luật G11
     */
    @Transactional(readOnly = true)
    public List<Long> resolve(List<Long> relatedOrgUnitIds, List<Long> extraUserIds, String targetPermission) {
        boolean nhamDich = targetPermission != null && !targetPermission.isBlank();

        // LinkedHashSet: khử trùng lặp mà vẫn giữ thứ tự — thứ tự ổn định làm log dễ đối chiếu và
        // test không phụ thuộc thứ tự ngẫu nhiên của HashSet.
        // Hai nguồn, hai luật lọc khác nhau — xem ghi chú ở dưới.
        Set<Long> named = new LinkedHashSet<>(extraUserIds == null ? List.of() : extraUserIds);
        Set<Long> derived =
                new LinkedHashSet<>(nhamDich ? users.findActiveIdsByPermission(targetPermission) : executiveBoard());

        boolean includeOwner = !nhamDich && settings.getBoolean(KEY_AUTO_INCLUDE_OWNER, true);
        if (includeOwner && relatedOrgUnitIds != null && !relatedOrgUnitIds.isEmpty()) {
            derived.addAll(orgUnits.findActiveHeadAndDeputyUserIds(relatedOrgUnitIds));
        }
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
        } catch (JsonProcessingException | IllegalArgumentException e) {
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
