package com.songnhue.core.infra.security;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.security.SecurityEvent;

/**
 * Bảng append-only: role {@code songnhue_app} chỉ có INSERT/SELECT (migration V…1006).
 *
 * <p>Vì vậy ở đây <b>không</b> có phương thức sửa/xoá — có viết ra thì DB cũng từ chối, và đó là chủ
 * ý: một lỗi lập trình cũng không xoá được dấu vết tấn công.
 */
@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    /**
     * Đếm số lần đăng nhập sai gần đây từ một địa chỉ IP — nguồn cho cảnh báo M5.16.
     *
     * <p>Đếm theo IP chứ không chỉ theo tài khoản: kẻ dò mật khẩu thường thử lần lượt nhiều tài
     * khoản khác nhau, mỗi tài khoản chỉ vài lần để không chạm ngưỡng khoá. Nhìn theo tài khoản thì
     * hoàn toàn không thấy gì bất thường.
     */
    @Query(
            value =
                    """
                    SELECT count(*) FROM security_events e
                     WHERE e.event_type = 'LOGIN_FAILED'
                       AND e.ip_address = CAST(:ipAddress AS inet)
                       AND e.occurred_at >= :since
                    """,
            nativeQuery = true)
    long countRecentFailuresFromIp(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
}
