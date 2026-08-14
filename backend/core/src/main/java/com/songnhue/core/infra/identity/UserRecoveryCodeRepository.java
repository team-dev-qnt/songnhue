package com.songnhue.core.infra.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.UserRecoveryCode;

@Repository
public interface UserRecoveryCodeRepository extends JpaRepository<UserRecoveryCode, Long> {

    /**
     * Tra mã khôi phục chưa dùng.
     *
     * <p>Tra bằng <b>hash</b> chứ không duyệt danh sách rồi so từng cái: chỉ mục
     * {@code uq_user_recovery_codes_hash} khiến thời gian tra không phụ thuộc vào việc mã đúng hay
     * sai, nên không đo được gì qua thời gian phản hồi.
     */
    Optional<UserRecoveryCode> findByCodeHashAndUsedAtIsNull(String codeHash);

    long countByUserIdAndUsedAtIsNull(Long userId);

    void deleteByUserId(Long userId);
}
