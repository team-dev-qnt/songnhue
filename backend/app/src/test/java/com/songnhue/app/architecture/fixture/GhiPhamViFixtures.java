package com.songnhue.app.architecture.fixture;

import java.util.Optional;
import java.util.UUID;

import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Mã cố ý sai cho {@code GhiPhamViRuleTest} — T74.8 · T74.9.
 *
 * <p>⛔ Cố ý ⛔ {@code @Entity}, ⛔ kế thừa {@code JpaRepository} — cùng lý do {@link PhamViFixtures}: một
 * {@code @Entity} dưới {@code com.songnhue} làm chết {@code EntityManagerFactory} (T48.5), một repository thật bị
 * Spring đăng ký thành bean. Bộ luật neo vào kiểu, ⛔ vào annotation.
 */
public final class GhiPhamViFixtures {

    private GhiPhamViFixtures() {}

    /** Entity phạm vi giả; hàm dựng có đơn vị TỰ đặt đơn vị — đúng hình dạng {@code Construction}. */
    public static class DonViGia extends ScopedEntity {
        public DonViGia() {}

        public DonViGia(Long donVi) {
            setOrgUnitId(donVi);
        }
    }

    /** Kho của entity phạm vi — nhận ra bằng {@code findByPublicId…} trả {@code Optional<ScopedEntity>}. */
    public interface DonViGiaRepo {
        Optional<DonViGia> findByPublicIdAndDeletedAtIsNull(UUID publicId);

        boolean existsByCodeAndDeletedAtIsNull(String code);
    }

    /** Kho của danh mục ⛔ phạm vi — kiểm trùng mã ở đây ⛔ cần {@code toanCongTy}. */
    public interface DanhMucGiaRepo {
        Optional<String> findByPublicIdAndDeletedAtIsNull(UUID publicId);

        boolean existsByCodeAndDeletedAtIsNull(String code);
    }

    /** ⛔ W1: đặt đơn vị lấy từ biểu mẫu mà ⛔ kiểm phạm vi ghi. */
    public static final class GhiKhongKiem {
        public void luu(DonViGia banGhi, Long donVi) {
            banGhi.setOrgUnitId(donVi);
        }
    }

    /** ⛔ W1 qua hàm dựng: {@code new DonViGia(donVi)} tự đặt đơn vị bên trong. */
    public static final class TaoQuaHamDungKhongKiem {
        public DonViGia tao(Long donVi) {
            return new DonViGia(donVi);
        }
    }

    /** ✅ W1: kiểm rồi mới ghi. */
    public static final class GhiCoKiem {
        private ScopeGuard guard;

        public void luu(DonViGia banGhi, Long donVi) {
            guard.requireWritableOrgUnit(donVi, DonViGia.class);
            banGhi.setOrgUnitId(donVi);
        }
    }

    /** ✅ W1: hàm dựng KHÔNG đặt đơn vị ⇒ ⛔ phải một chỗ ghi đơn vị. */
    public static final class TaoKhongDonVi {
        public DonViGia tao() {
            return new DonViGia();
        }
    }

    /** ⛔ W2: kiểm trùng mã của entity phạm vi QUA bộ lọc ⇒ mã của đơn vị khác vô hình. */
    public static final class KiemMaTrongPhamVi {
        private DonViGiaRepo repo;

        public boolean trung(String ma) {
            return repo.existsByCodeAndDeletedAtIsNull(ma);
        }
    }

    /** ✅ W2: hỏi toàn Công ty. */
    public static final class KiemMaToanCongTy {
        private DonViGiaRepo repo;
        private ScopeGuard guard;

        public boolean trung(String ma) {
            return guard.toanCongTy(() -> repo.existsByCodeAndDeletedAtIsNull(ma));
        }
    }

    /** ✅ W2: danh mục ⛔ phạm vi ⇒ kiểm thẳng là đúng. */
    public static final class KiemMaDanhMuc {
        private DanhMucGiaRepo repo;

        public boolean trung(String ma) {
            return repo.existsByCodeAndDeletedAtIsNull(ma);
        }
    }
}
