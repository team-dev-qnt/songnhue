package com.songnhue.core.common.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Phân biệt "không có bản ghi đó" với "có, nhưng thuộc đơn vị khác" — mảnh cuối của tầng 3
 * ({@code conventions.md} §4.2, WS-5/T5.11).
 *
 * <p><b>Vì sao cần một lớp riêng.</b> {@link ScopeFilterAspect} bật bộ lọc phạm vi cho mọi truy vấn,
 * nên bản ghi của Xí nghiệp khác đơn giản là <i>không có trong kết quả</i>. Repository trả
 * {@code Optional.empty()}, service ném "không tìm thấy", và người dùng nhận 404. Nhìn thì có vẻ ổn —
 * dữ liệu đúng là không lọt ra. Nhưng có hai thứ mất đi:
 *
 * <ul>
 *   <li><b>Không ai biết chuyện đó vừa xảy ra.</b> Một người dò tuần tự {@code public_id} để tìm hồ sơ
 *       của đơn vị khác trông y hệt một người gõ nhầm đường dẫn. Đây chính là loại hành vi mà
 *       {@code security_events} sinh ra để ghi lại (M5.16), và 404 làm nó vô hình.
 *   <li><b>Mã lỗi {@code AUTH-3002} không bao giờ được dùng.</b> Nó có trong danh mục và trong tiêu
 *       chí nghiệm thu, nhưng không dòng mã nào ném ra — một mã lỗi chết.
 * </ul>
 *
 * <p><b>Vì sao lộ ra 403 mà không sợ rò rỉ thông tin.</b> Phân biệt 403/404 đúng là tiết lộ "id này
 * có tồn tại". Ở đây chấp nhận được vì người gọi <i>đã đăng nhập</i> và {@code public_id} là UUID
 * ngẫu nhiên — đoán trúng một cái là chuyện không xảy ra, nên thứ duy nhất họ biết thêm là điều họ
 * vốn đã biết. Đổi lại, mỗi lần chạm vào dữ liệu ngoài phạm vi đều để lại dấu vết. Với hệ nội bộ
 * 200 người dùng, đánh đổi đó nghiêng hẳn về phía có dấu vết.
 *
 * <p><b>Cách dùng ở tầng application:</b>
 *
 * <pre>
 * Construction c = scopeGuard.require(
 *         repository.findByPublicIdAndDeletedAtIsNull(publicId), Construction.class, publicId);
 * </pre>
 */
@Component
public class ScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(ScopeGuard.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final SecurityEventService securityEvents;

    public ScopeGuard(SecurityEventService securityEvents) {
        this.securityEvents = securityEvents;
    }

    /**
     * @param found kết quả tra cứu đã đi qua bộ lọc phạm vi
     * @param entityType lớp entity, để tra lại khi bộ lọc bị tắt
     * @param publicId định danh công khai mà người gọi gửi lên
     * @return bản ghi khi nó nằm trong phạm vi của người đăng nhập
     * @throws PermissionDeniedException {@code AUTH-3002} — bản ghi có thật nhưng thuộc đơn vị khác
     * @throws ResourceNotFoundException {@code SYS-0004} — không có bản ghi nào mang định danh đó
     */
    public <T> T require(Optional<T> found, Class<T> entityType, UUID publicId) {
        if (found.isPresent()) {
            return found.get();
        }
        if (existsOutsideScope(entityType, publicId)) {
            AuthenticatedUser user = AuthContext.current().orElse(null);
            log.warn(
                    "Chặn truy cập ngoài phạm vi đơn vị: {} hỏi {} {}",
                    user == null ? "?" : user.username(),
                    entityType.getSimpleName(),
                    publicId);
            securityEvents.record(
                    SecurityEventType.ACCESS_DENIED_SCOPE,
                    user == null ? null : user.username(),
                    user == null ? null : user.userId(),
                    null,
                    "{\"entity\":\"" + entityType.getSimpleName() + "\",\"publicId\":\"" + publicId + "\"}");
            throw new PermissionDeniedException(ErrorCode.AUTH_3002);
        }
        throw new ResourceNotFoundException(ErrorCode.SYS_0004);
    }

    /**
     * Tra lại đúng một lần với bộ lọc tắt.
     *
     * <p>Bộ lọc được <b>bật lại nguyên trạng trong khối {@code finally}</b>. Bỏ sót bước đó là mọi
     * truy vấn còn lại của transaction chạy không có lọc phạm vi — nghĩa là lớp sinh ra để bảo vệ
     * phạm vi lại trở thành cái chọc thủng nó, và không có lỗi nào báo ra.
     */
    private boolean existsOutsideScope(Class<?> entityType, UUID publicId) {
        Session session = entityManager.unwrap(Session.class);
        if (session.getEnabledFilter(ScopedEntity.ORG_UNIT_FILTER) == null) {
            // Bộ lọc không bật (job nền, lệnh bootstrap) → đã nhìn thấy toàn bộ dữ liệu ở lần tra
            // đầu, nên "không thấy" đúng nghĩa là không tồn tại.
            return false;
        }
        // Lấy lại tham số từ AuthContext chứ không hỏi bộ lọc: org.hibernate.Filter không cho đọc
        // ngược giá trị đã đặt, và đây đúng là nguồn mà ScopeFilterAspect dùng để bật nó.
        String pathPrefix = AuthContext.current()
                .map(AuthenticatedUser::orgUnitPath)
                .orElseThrow(() -> new IllegalStateException(
                        "Bộ lọc phạm vi đang bật nhưng không có người đăng nhập — không bật lại được"));
        session.disableFilter(ScopedEntity.ORG_UNIT_FILTER);
        try {
            return session.createQuery(
                                    "SELECT count(e) FROM " + entityType.getSimpleName()
                                            + " e WHERE e.publicId = :publicId",
                                    Long.class)
                            .setParameter("publicId", publicId)
                            .getSingleResult()
                    > 0;
        } finally {
            session.enableFilter(ScopedEntity.ORG_UNIT_FILTER)
                    .setParameter(ScopedEntity.ORG_UNIT_PATHS_PARAM, pathPrefix);
        }
    }

    /**
     * <b>Vế GHI của tầng 3</b> — T74.8 (ASVS 4.2.1). Bản ghi có phạm vi được tạo mới hoặc chuyển tới đơn vị
     * {@code orgUnitId} ⇒ đơn vị ấy phải nằm trong phạm vi của người đăng nhập, theo ĐÚNG điều kiện bộ lọc đọc
     * ({@link ScopedEntity#ORG_UNIT_FILTER_CONDITION}: {@code path LIKE <phạm vi> || '%'}).
     *
     * <p>Bộ lọc chỉ canh vế đọc; nơi ghi đơn vị lấy từ biểu mẫu trước WS-74b chỉ kiểm đơn vị TỒN TẠI — nên một tài
     * khoản ở Xí nghiệp A tạo được bản ghi vào Xí nghiệp B (rồi ⛔ đọc lại được chính thứ mình vừa ghi), ⛔ dòng
     * nhật ký bảo mật nào. {@code GhiPhamViRuleTest} buộc mọi chỗ ghi đơn vị ở tầng application gọi hàm này hoặc
     * khai miễn kèm lý do.
     *
     * <p>⚠ Câu tra đường dẫn chạy {@link FlushModeType#COMMIT}: ⛔ đẩy thay đổi dở dang của entity xuống CSDL.
     *
     * @param orgUnitId đơn vị đích; {@code null} ⇒ ⛔ kiểm (điểm đo chưa gán đơn vị — giới hạn khai ra)
     * @param entityType lớp entity, ghi vào nhật ký bảo mật
     * @throws PermissionDeniedException {@code AUTH-3002} kèm một dòng {@code ACCESS_DENIED_SCOPE}
     */
    public void requireWritableOrgUnit(Long orgUnitId, Class<?> entityType) {
        if (trongPhamVi(orgUnitId)) {
            return;
        }
        AuthenticatedUser user = AuthContext.current().orElseThrow();
        log.warn(
                "Chặn GHI ngoài phạm vi đơn vị: {} ghi {} vào đơn vị {}",
                user.username(),
                entityType.getSimpleName(),
                orgUnitId);
        securityEvents.record(
                SecurityEventType.ACCESS_DENIED_SCOPE,
                user.username(),
                user.userId(),
                null,
                "{\"entity\":\"" + entityType.getSimpleName() + "\",\"orgUnitId\":" + orgUnitId
                        + ",\"thaoTac\":\"GHI\"}");
        throw new PermissionDeniedException(ErrorCode.AUTH_3002);
    }

    /**
     * Đơn vị {@code orgUnitId} có nằm trong phạm vi của người đăng nhập ⛔ — <b>hỏi mà ⛔ ném, ⛔ ghi nhật ký</b>.
     *
     * <p>Dành cho nơi cần BÁO chứ ⛔ chặn: bộ nhập tệp phải trả về <i>một dòng lỗi chỉ đúng chỗ sai</i> thay vì để
     * lượt ghi thật ném {@code AUTH-3002} giữa chừng và từ chối cả tệp — và một tệp 200 dòng ⛔ được sinh 200 dòng
     * {@code ACCESS_DENIED_SCOPE} chỉ vì người lập tệp gõ nhầm một mã đơn vị.
     *
     * @return {@code true} khi {@code orgUnitId} null hoặc ⛔ có người đăng nhập (job nền) — đúng như bộ lọc đọc
     */
    public boolean trongPhamVi(Long orgUnitId) {
        if (orgUnitId == null) {
            return true;
        }
        Optional<AuthenticatedUser> user = AuthContext.current();
        if (user.isEmpty()) {
            // Job nền, lệnh bootstrap — ⛔ có người đăng nhập thì ⛔ có phạm vi để so, đúng như bộ lọc đọc.
            return true;
        }
        String phamVi = user.get().orgUnitPath();
        @SuppressWarnings("unchecked")
        List<String> dich = entityManager
                .createNativeQuery("SELECT path FROM org_units WHERE id = :id", String.class)
                .setParameter("id", orgUnitId)
                .setFlushMode(FlushModeType.COMMIT)
                .getResultList();
        return phamVi != null
                && !dich.isEmpty()
                && dich.get(0) != null
                && dich.get(0).startsWith(phamVi);
    }

    /**
     * Một câu hỏi <b>CÓ/KHÔNG</b> về tính duy nhất <b>toàn Công ty</b> — T74.9.
     *
     * <p>Mã CBNV · mã công trình · mã điểm đo · mã API là duy nhất toàn Công ty, còn phép kiểm trùng đi qua bộ lọc
     * phạm vi ⇒ mã đã có ở đơn vị khác là VÔ HÌNH với người tạo, lượt lưu rơi vào ràng buộc CSDL và người dùng nhận
     * {@code SYS-0005} <i>"Dữ liệu vừa được người khác thay đổi"</i> — một câu dẫn họ đi tìm một lượt sửa ⛔ hề có.
     *
     * <p>⛔ Chỉ nhận {@link BooleanSupplier}: câu trả lời có/không ⛔ mang dữ liệu của đơn vị khác ra ngoài — thứ duy
     * nhất lộ ra là <i>"mã này đã có người dùng"</i>, đúng điều thông báo lỗi phải nói. Bộ lọc bật lại nguyên trạng
     * trong {@code finally} — cùng kỷ luật với {@link #existsOutsideScope}.
     */
    public boolean toanCongTy(BooleanSupplier phepKiem) {
        Session session = entityManager.unwrap(Session.class);
        if (session.getEnabledFilter(ScopedEntity.ORG_UNIT_FILTER) == null) {
            return phepKiem.getAsBoolean();
        }
        String pathPrefix = AuthContext.current()
                .map(AuthenticatedUser::orgUnitPath)
                .orElseThrow(() -> new IllegalStateException(
                        "Bộ lọc phạm vi đang bật nhưng không có người đăng nhập — không bật lại được"));
        session.disableFilter(ScopedEntity.ORG_UNIT_FILTER);
        try {
            return phepKiem.getAsBoolean();
        } finally {
            session.enableFilter(ScopedEntity.ORG_UNIT_FILTER)
                    .setParameter(ScopedEntity.ORG_UNIT_PATHS_PARAM, pathPrefix);
        }
    }
}
