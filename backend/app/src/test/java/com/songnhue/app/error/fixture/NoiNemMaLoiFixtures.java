package com.songnhue.app.error.fixture;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Nơi ném giả cho {@code DoiSoMaLoiKhopChoCamTest#tuKiemChung} — T61.13.
 *
 * <p>Neo vào hai mã có số chỗ cắm biết trước: {@code SYS-0004} (0 chỗ) và {@code SYS-0009} (1 chỗ).
 * ⚠ Mỗi phương thức là MỘT hình dạng lời gọi mà javac sinh bytecode khác nhau — gộp hai hình dạng vào
 * một phương thức là để bài tự-kiểm ⛔ phân biệt được hỏng ở hình dạng nào.
 */
@SuppressWarnings("unused")
public final class NoiNemMaLoiFixtures {

    private NoiNemMaLoiFixtures() {}

    static RuntimeException thuaDoiSo() {
        return new ConflictException(ErrorCode.SYS_0004, "thừa");
    }

    static RuntimeException thieuDoiSo() {
        return new ConflictException(ErrorCode.SYS_0009);
    }

    static RuntimeException dungMotDoiSo() {
        return new ConflictException(ErrorCode.SYS_0009, "PENDING");
    }

    static RuntimeException dungKhongDoiSo() {
        return new ConflictException(ErrorCode.SYS_0004);
    }

    static RuntimeException coNguyenNhan(Throwable nguyenNhan) {
        return new BusinessRuleException(ErrorCode.SYS_0009, nguyenNhan, "PENDING");
    }

    static RuntimeException maMacDinh() {
        return new ValidationException("a", "b");
    }

    static RuntimeException formatTrongDoiSo(int n) {
        return new ConflictException(ErrorCode.SYS_0009, String.format("%s-%s-%s", n, n, n));
    }

    static RuntimeException ngoaiLeLongNhau() {
        return new BusinessRuleException(
                ErrorCode.SYS_0009, new ConflictException(ErrorCode.SYS_0004, "x", "y").getMessage());
    }

    static RuntimeException maQuaBien(boolean dieuKien) {
        ErrorCode ma = dieuKien ? ErrorCode.SYS_0004 : ErrorCode.SYS_0009;
        return new ConflictException(ma);
    }
}
