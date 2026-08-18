package com.songnhue.core.application.notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        // LinkedHashSet: khử trùng lặp mà vẫn giữ thứ tự — thứ tự ổn định làm log dễ đối chiếu và
        // test không phụ thuộc thứ tự ngẫu nhiên của HashSet.
        // Hai nguồn, hai luật lọc khác nhau — xem ghi chú ở dưới.
        Set<Long> named = new LinkedHashSet<>(extraUserIds == null ? List.of() : extraUserIds);
        Set<Long> derived = new LinkedHashSet<>(executiveBoard());

        boolean includeOwner = settings.getBoolean(KEY_AUTO_INCLUDE_OWNER, true);
        if (includeOwner && relatedOrgUnitIds != null && !relatedOrgUnitIds.isEmpty()) {
            derived.addAll(orgUnits.findActiveHeadAndDeputyUserIds(relatedOrgUnitIds));
        }
        derived.removeAll(named);

        if (named.isEmpty() && derived.isEmpty()) {
            // Không phải lỗi kỹ thuật, nhưng là lỗi cấu hình đáng báo: một cảnh báo được sinh ra mà
            // không tới ai cả thì im lặng y như không có cảnh báo.
            log.warn("Không tìm được người nhận nào cho cảnh báo — kiểm tra nhóm '{}'", KEY_EXECUTIVE_BOARD);
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
     * <p>Giá trị hỏng thì trả rỗng và ghi log, <b>không ném</b>: một dòng cấu hình sai không được
     * phép chặn luôn việc gửi cảnh báo cho những người nhận còn lại (người phụ trách công trình).
     */
    private List<Long> executiveBoard() {
        String raw = settings.getString(KEY_EXECUTIVE_BOARD).orElse("[]");
        try {
            return List.of(objectMapper.readValue(raw, Long[].class));
        } catch (JsonProcessingException e) {
            log.error("Tham số '{}' không phải mảng JSON hợp lệ — bỏ qua nhóm này", KEY_EXECUTIVE_BOARD, e);
            return List.of();
        }
    }
}
