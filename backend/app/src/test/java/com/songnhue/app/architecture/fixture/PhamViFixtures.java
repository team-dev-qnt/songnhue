package com.songnhue.app.architecture.fixture;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Mã cố ý sai cho {@code TraCuuPhamViRuleSelfCheckTest} — T73.1.
 *
 * <p>⛔ <b>Cố ý ⛔ {@code @Entity}, ⛔ kế thừa {@code JpaRepository}.</b> Gói này nằm dưới {@code com.songnhue}: một
 * {@code @Entity} ở đây làm chết {@code EntityManagerFactory} của mọi bài tích hợp (T48.5), một interface kế thừa
 * {@code JpaRepository} bị Spring đăng ký thành bean thật. Bộ luật neo vào <b>kiểu trả về</b> của phương thức được
 * gọi ({@code Optional<X>} với X là {@code ScopedEntity}), nên đồ gá ⛔ cần là một repository thật.
 */
public final class PhamViFixtures {

    private PhamViFixtures() {}

    /** Entity phạm vi giả — kế thừa {@link ScopedEntity} là đủ để bộ luật nhận ra. */
    public static class DonViGia extends ScopedEntity {}

    /** Entity ⛔ phạm vi — tra cứu nó ⛔ cần {@code ScopeGuard}. */
    public static class DanhMucGia {}

    public interface DonViGiaRepo {
        Optional<DonViGia> findByPublicIdAndDeletedAtIsNull(UUID publicId);
    }

    public interface DanhMucGiaRepo {
        Optional<DanhMucGia> findByPublicIdAndDeletedAtIsNull(UUID publicId);
    }

    /** ⛔ Vi phạm: bản ghi của đơn vị khác ra 404 im lặng — đúng sáu chỗ T73.1 vá. */
    public static final class Tra404ImLang {

        private final DonViGiaRepo repo;

        public Tra404ImLang(DonViGiaRepo repo) {
            this.repo = repo;
        }

        public DonViGia lay(UUID publicId) {
            return repo.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow();
        }
    }

    /** ✅ Đúng chuẩn — khuôn trong javadoc của {@link ScopeGuard}. */
    public static final class TraQuaScopeGuard {

        private final DonViGiaRepo repo;
        private final ScopeGuard scopeGuard;

        public TraQuaScopeGuard(DonViGiaRepo repo, ScopeGuard scopeGuard) {
            this.repo = repo;
            this.scopeGuard = scopeGuard;
        }

        public DonViGia lay(UUID publicId) {
            return scopeGuard.require(repo.findByPublicIdAndDeletedAtIsNull(publicId), DonViGia.class, publicId);
        }
    }

    /**
     * ✅ Lượt tra nằm trong LAMBDA, {@code ScopeGuard} ở phương thức bao. Ca này tồn tại để đo xem ArchUnit gán lời
     * gọi trong lambda cho phương thức nào — gán cho phương thức tổng hợp {@code lambda$…} thì bộ luật báo nhầm. Đo
     * 19/09/2026 (ArchUnit 1.5.0): gán cho phương thức BAO ⇒ ⛔ bị báo.
     */
    public static final class TraTrongLambda {

        private final DonViGiaRepo repo;
        private final ScopeGuard scopeGuard;

        public TraTrongLambda(DonViGiaRepo repo, ScopeGuard scopeGuard) {
            this.repo = repo;
            this.scopeGuard = scopeGuard;
        }

        public DonViGia lay(UUID publicId) {
            Supplier<Optional<DonViGia>> tra = () -> repo.findByPublicIdAndDeletedAtIsNull(publicId);
            return scopeGuard.require(tra.get(), DonViGia.class, publicId);
        }
    }

    /** ✅ Entity ⛔ phạm vi: ⛔ bị bắt. */
    public static final class TraDanhMuc {

        private final DanhMucGiaRepo repo;

        public TraDanhMuc(DanhMucGiaRepo repo) {
            this.repo = repo;
        }

        public DanhMucGia lay(UUID publicId) {
            return repo.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow();
        }
    }
}
