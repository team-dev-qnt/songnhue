package com.songnhue.core.infra.identity;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.TokenDenylistEntry;

@Repository
public interface TokenDenylistRepository extends JpaRepository<TokenDenylistEntry, Long> {

    /**
     * Xoá bản ghi đã quá hạn.
     *
     * <p>Token hết hạn thì tự nó đã không dùng được nữa, giữ lại chỉ khiến bảng phình ra trong khi
     * bảng này bị đọc ở <i>mọi</i> request. Đây là lý do phải có job dọn dẹp chứ không để mặc.
     */
    @Modifying
    @Query("DELETE FROM TokenDenylistEntry d WHERE d.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
