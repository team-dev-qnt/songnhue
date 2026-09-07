package com.songnhue.core.infra.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.UserTotp;

@Repository
public interface UserTotpRepository extends JpaRepository<UserTotp, Long> {

    Optional<UserTotp> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
