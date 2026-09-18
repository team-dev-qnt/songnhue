package com.songnhue.app.architecture.fixture;

import java.time.Instant;

import com.songnhue.core.common.audit.Audited;

/**
 * Entity giả cho {@code AuditRedactionRuleSelfCheckTest} — <b>T47.18</b>.
 *
 * <p>⛔ Chúng ⛔ KHÔNG bao giờ được nạp bởi Hibernate: gói này nằm dưới {@code src/test} và
 * {@code ProductionClasses.ALL} khai {@code DoNotIncludeTests()}. Bài tự-kiểm nạp chúng bằng một
 * {@code ClassFileImporter} riêng.
 *
 * <h2>⛔⛔ Vì sao chúng ⛔ KHÔNG mang `@Entity` — một hồi quy đã đo được</h2>
 *
 * <p>Bản đầu của tệp này dùng `@Entity` thật cho đúng vị từ của luật. Hậu quả đo ngay lượt chạy kế:
 * <b>MỌI bài kiểm tích hợp chết</b> — {@code AnnotationException: Entity
 * 'AuditRedactionFixtures$ChiLaMocThoiGian' has no identifier}. Hibernate quét cả {@code src/test}
 * trên classpath, nên một lớp gắn {@code @Entity} mà thiếu {@code @Id} làm hỏng
 * {@code EntityManagerFactory} của cả ứng dụng, ⛔ không riêng bài tự-kiểm.
 *
 * <p>⇒ Fixture chỉ mang {@code @Audited}. Bài tự-kiểm gọi thẳng
 * {@code AuditRedactionRuleTest.truongBiMat(...)} và đọc {@code @Audited}, ⛔ không đi qua
 * {@code entityDuocAudit()} — nên nó ⛔ không cần vị từ {@code @Entity}.
 *
 * <p>⚠ <b>Cái giá phải khai</b> (luật 28): vì thế bài tự-kiểm ⛔ <b>không</b> chứng minh bộ lọc
 * {@code @Entity} của luật thật là đúng. Vế ấy được che bởi hai khẳng định chống-tập-rỗng của luật
 * thật (≥ 20 entity {@code @Audited}, ≥ 2 trường đem ra so) — ⛔ không phải bởi tệp này.
 *
 * <p>⚠ Bốn lớp, và <b>ba trong bốn phải KHÔNG bị báo vi phạm</b>. Thiếu ba vế đối chứng ấy thì bài
 * tự-kiểm vẫn xanh với một luật viết kiểu <i>"từ chối tất cả"</i> — thứ làm mọi entity thật đỏ và
 * do đó bị người ta tắt đi (§10.62).
 */
public final class AuditRedactionFixtures {

    private AuditRedactionFixtures() {}

    /** ⛔ VI PHẠM luật 1: giữ một bí mật, có {@code @Audited}, ⛔ không loại trừ. */
    @Audited(module = "fixture", entityType = "Rò bí mật")
    public static class RoBiMat {
        private String credential;

        public String getCredential() {
            return credential;
        }
    }

    /** ⛔ VI PHẠM luật 2: loại trừ một cái tên ⛔ không còn là trường nào — ca đổi tên im lặng. */
    @Audited(
            module = "fixture",
            entityType = "Loại trừ treo",
            excludeFields = {"tenCu"})
    public static class LoaiTruTreo {
        private String secretMoi;

        public String getSecretMoi() {
            return secretMoi;
        }
    }

    /** ⭐ ĐỐI CHỨNG 1 — khai đúng, phải được THA. */
    @Audited(
            module = "fixture",
            entityType = "Khai đúng",
            excludeFields = {"passwordHash"})
    public static class KhaiDung {
        private String passwordHash;

        public String getPasswordHash() {
            return passwordHash;
        }
    }

    /**
     * ⭐ ĐỐI CHỨNG 2 — trường khớp MẪU TÊN nhưng mang KIỂU ⛔ không chở được bí mật.
     *
     * <p>Đây là vế chứng minh bộ lọc kiểu đang làm việc: {@code passwordChangedAt} là một mốc thời
     * gian, và mốc ấy <b>cần</b> nằm trong nhật ký — một lượt đổi mật khẩu ⛔ không ai ghi lại là
     * thứ điều tra sự cố cần nhất.
     */
    @Audited(module = "fixture", entityType = "Mốc thời gian")
    public static class ChiLaMocThoiGian {
        private Instant passwordChangedAt;

        public Instant getPasswordChangedAt() {
            return passwordChangedAt;
        }
    }
}
