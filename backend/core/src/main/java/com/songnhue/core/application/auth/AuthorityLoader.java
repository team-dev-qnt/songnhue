package com.songnhue.core.application.auth;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.infra.identity.UserAuthorityRepository;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Nạp quyền và phạm vi đơn vị của người dùng cho mỗi request (tầng 2 + tầng 3, §4.2).
 *
 * <p><b>Vì sao nạp lại từ DB mỗi phiên thay vì nhét vào token.</b> Nhét quyền vào token thì rẻ hơn,
 * nhưng khi Admin gỡ quyền của ai đó, người đó vẫn giữ nguyên quyền cũ tới lúc token hết hạn — tối
 * đa 30 phút. Với hệ thống có màn hình phân quyền chi tiết như MOD-05, "gỡ quyền rồi mà vẫn làm
 * được" là một lỗi nghiệm thu, không phải một sự đánh đổi.
 *
 * <p><b>Cache 30 giây</b> là mức thoả hiệp: một người dùng bấm liên tục vẫn chỉ tốn một truy vấn
 * mỗi nửa phút, còn thay đổi phân quyền thì chậm nhất nửa phút là có hiệu lực. Khi WS-6 làm màn
 * hình phân quyền, chỗ đó gọi {@link #invalidate(UUID)} để hiệu lực tức thì.
 *
 * <p>⚠ Cache trong tiến trình. Lên ≥2 node thì việc gọi {@code invalidate} chỉ tác dụng ở node nhận
 * request — node kia vẫn phải chờ hết TTL. Đây là một trong các thay đổi ghi ở
 * {@code architecture-review.md} §6.4.
 */
@Service
public class AuthorityLoader {

    private static final Logger log = LoggerFactory.getLogger(AuthorityLoader.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int CACHE_MAX_SIZE = 1_000;

    private final UserRepository users;
    private final UserAuthorityRepository authorities;

    private final Cache<UUID, Optional<Authorities>> cache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

    public AuthorityLoader(UserRepository users, UserAuthorityRepository authorities) {
        this.users = users;
        this.authorities = authorities;
    }

    /**
     * Dựng {@link AuthenticatedUser} từ định danh trong token.
     *
     * @return rỗng khi tài khoản đã bị xoá mềm, khoá, hoặc vô hiệu hoá kể từ lúc token được phát —
     *     token còn hạn nhưng người đứng sau nó đã không còn quyền vào hệ thống
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> load(UUID userPublicId, UUID sessionFamilyId, UUID tokenId) {
        return cache.get(userPublicId, this::fetch)
                .map(a -> new AuthenticatedUser(
                        a.userId(),
                        userPublicId,
                        a.username(),
                        a.fullName(),
                        a.orgUnitId(),
                        a.orgUnitPath(),
                        a.roles(),
                        a.permissions(),
                        a.mustChangePassword(),
                        sessionFamilyId,
                        tokenId));
    }

    /** Gọi sau khi đổi vai trò/quyền của một người dùng để thay đổi có hiệu lực ngay. */
    public void invalidate(UUID userPublicId) {
        cache.invalidate(userPublicId);
    }

    /** Gọi khi sửa quyền của một vai trò — không biết ai bị ảnh hưởng nên xoá sạch. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    // -------------------------------------------------------------------------

    private Optional<Authorities> fetch(UUID userPublicId) {
        Optional<User> found = users.findByPublicIdAndDeletedAtIsNull(userPublicId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User user = found.get();
        if (!user.canAuthenticate()) {
            log.debug("Tài khoản {} không còn ở trạng thái ACTIVE — từ chối token còn hạn", user.getUsername());
            return Optional.empty();
        }

        String orgUnitPath = authorities.findOrgUnitPath(user.getOrgUnitId()).orElse(null);
        if (orgUnitPath == null) {
            // Không có phạm vi thì không thể lọc dữ liệu an toàn → thà chặn còn hơn cho xem tất cả
            log.error("Tài khoản {} trỏ tới đơn vị {} không tồn tại", user.getUsername(), user.getOrgUnitId());
            return Optional.empty();
        }

        return Optional.of(new Authorities(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getOrgUnitId(),
                orgUnitPath,
                new LinkedHashSet<>(authorities.findRoleCodes(user.getId())),
                new LinkedHashSet<>(authorities.findPermissionCodes(user.getId())),
                user.isMustChangePassword()));
    }

    private record Authorities(
            Long userId,
            String username,
            String fullName,
            Long orgUnitId,
            String orgUnitPath,
            Set<String> roles,
            Set<String> permissions,
            boolean mustChangePassword) {}
}
