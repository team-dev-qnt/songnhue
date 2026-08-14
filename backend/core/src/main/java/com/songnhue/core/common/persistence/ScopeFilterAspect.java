package com.songnhue.core.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.security.AuthContext;

/**
 * Tầng 3 — bật bộ lọc phạm vi đơn vị cho mọi truy vấn trong một transaction (T5.11, §4.2).
 *
 * <p><b>Vì sao là aspect chứ không phải mỗi repository tự thêm điều kiện.</b> Quy tắc 5 của dự án:
 * <i>lọc phạm vi phải nằm ở tầng repository, không dựa vào việc lập trình viên nhớ thêm
 * {@code WHERE}</i>. Quên một chỗ là rò rỉ dữ liệu giữa các Xí nghiệp, mà loại lỗi này không tự biểu
 * hiện — hệ thống vẫn chạy đúng, chỉ là ai cũng xem được dữ liệu của đơn vị khác. Bật ở một chỗ duy
 * nhất thì không có chỗ nào để quên.
 *
 * <p><b>Vì sao gắn vào {@code @Transactional}.</b> Hibernate bật/tắt bộ lọc theo {@code Session},
 * mà vòng đời {@code Session} trùng với transaction (dự án đặt {@code open-in-view: false}). Quy tắc
 * kiến trúc "{@code @Transactional} chỉ ở tầng application" do ArchUnit canh (T10.2) đảm bảo không
 * có truy vấn nào chạy ngoài phạm vi mà aspect này phủ.
 *
 * <p><b>Không có người đăng nhập thì không bật lọc</b> — job nền, lệnh bootstrap, poller thủy văn
 * cần nhìn toàn bộ dữ liệu. Đây là quyết định có cân nhắc: những đường đó không nhận đầu vào từ
 * người dùng, còn nếu bật lọc thì job sẽ âm thầm bỏ sót dữ liệu của các đơn vị.
 *
 * <p>Chạy với {@code Ordered.LOWEST_PRECEDENCE - 1} để nằm <b>bên trong</b> aspect quản lý
 * transaction của Spring: lúc aspect này chạy thì {@code Session} đã mở, không thì
 * {@code enableFilter} rơi vào một session khác rồi bị vứt đi.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class ScopeFilterAspect {

    private static final Logger log = LoggerFactory.getLogger(ScopeFilterAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Bộ lọc đã được Hibernate đăng ký chưa — tính một lần rồi giữ.
     *
     * <p>{@code @FilterDef} khai trên {@link ScopedEntity} là một {@code @MappedSuperclass}, mà
     * Hibernate <b>chỉ xử lý lớp cha khi có ít nhất một entity kế thừa nó</b>. Ở Phase 0 chưa có
     * entity nào thuộc phạm vi đơn vị (công trình, lịch sử sửa chữa, hồ sơ nhân viên… đều thuộc
     * Phase 1+), nên bộ lọc chưa tồn tại và mọi lời gọi {@code enableFilter} đều ném
     * {@code UnknownFilterException} — tức là <i>mọi</i> request đều lỗi 500.
     *
     * <p>Vì vậy: chưa có entity nào cần lọc thì không có gì để bật, và đó là trạng thái hợp lệ chứ
     * không phải lỗi. Ngay khi entity đầu tiên kế thừa {@code ScopedEntity} xuất hiện, cờ này thành
     * {@code true} và bộ lọc hoạt động mà không phải sửa dòng nào ở đây.
     *
     * <p>⚠ Ràng buộc còn thiếu, để lại cho <b>WS-10 / T10.2</b>: luật ArchUnit bắt mọi lớp con của
     * {@code ScopedEntity} phải mang {@code @Filter}. Thiếu annotation đó thì bộ lọc <i>có</i> tồn
     * tại nhưng không áp cho entity ấy — dữ liệu của mọi đơn vị lộ ra hết mà không có lỗi nào.
     */
    private volatile Boolean filterRegistered;

    // CHECKSTYLE.OFF: IllegalThrows - ProceedingJoinPoint.proceed() khai báo throws Throwable;
    // bọc lại thành RuntimeException ở đây sẽ nuốt mất kiểu exception gốc và làm
    // GlobalExceptionHandler mất khả năng ánh xạ đúng mã lỗi nghiệp vụ.
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object applyScopeFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (isFilterRegistered()) {
            AuthContext.current().ifPresent(user -> enable(user.orgUnitPath()));
        }
        return joinPoint.proceed();
    }
    // CHECKSTYLE.ON: IllegalThrows

    private void enable(String orgUnitPath) {
        if (orgUnitPath == null || orgUnitPath.isBlank()) {
            // AuthorityLoader đã từ chối những tài khoản không tra được đơn vị, tới đây là bất thường
            log.error("Người dùng không có materialized path đơn vị — không bật được lọc phạm vi");
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(ScopedEntity.ORG_UNIT_FILTER).setParameter(ScopedEntity.ORG_UNIT_PATHS_PARAM, orgUnitPath);
    }

    private boolean isFilterRegistered() {
        Boolean cached = filterRegistered;
        if (cached == null) {
            cached = entityManager
                    .getEntityManagerFactory()
                    .unwrap(SessionFactory.class)
                    .getDefinedFilterNames()
                    .contains(ScopedEntity.ORG_UNIT_FILTER);
            filterRegistered = cached;
            if (Boolean.TRUE.equals(cached)) {
                log.info("Bộ lọc phạm vi đơn vị đã sẵn sàng — mọi truy vấn trên ScopedEntity sẽ được lọc");
            } else {
                log.info("Chưa có entity nào thuộc phạm vi đơn vị — bỏ qua lọc tầng 3 (Phase 0)");
            }
        }
        return cached;
    }
}
