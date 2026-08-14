package com.songnhue.core.common.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Nội dung một access token đã kiểm chữ ký xong.
 *
 * <p>Cố ý <b>không</b> mang theo danh sách quyền. Quyền nhét vào token thì mỗi lần Admin gỡ quyền
 * của ai đó, người đó vẫn giữ nguyên quyền cũ cho tới khi token hết hạn — tối đa 30 phút. Với hệ
 * thống có màn hình phân quyền chi tiết (MOD-05), khoảng trễ đó là không chấp nhận được. Quyền được
 * nạp từ DB ở {@code ScopeContextFilter}, có cache ngắn.
 *
 * @param subject {@code public_id} của người dùng — không dùng id chạy số (§4.2)
 * @param username chỉ để ghi log
 * @param tokenId {@code jti}, đối chiếu {@code token_denylist}
 * @param sessionFamilyId family của phiên; thu hồi family là access token chết theo
 * @param expiresAt hạn dùng
 */
public record AccessTokenClaims(UUID subject, String username, UUID tokenId, UUID sessionFamilyId, Instant expiresAt) {}
