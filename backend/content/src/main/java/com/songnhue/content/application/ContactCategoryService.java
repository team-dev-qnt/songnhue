package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.ContactCategory;
import com.songnhue.content.infra.ContactCategoryRepository;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;

/**
 * Danh mục phân loại liên hệ — CN-01.4, quy tắc 16.
 *
 * <h2>Vì sao xoá là chuyện KHÓ, còn tắt là chuyện dễ</h2>
 *
 * <p>Một phân loại đã gán cho 300 liên hệ là một phần <b>lịch sử</b>. Xoá nó rồi tự gỡ khỏi 300 bản
 * ghi ấy là sửa dữ liệu của người khác trong im lặng, và báo cáo theo phân loại của quý trước sẽ
 * đổi kết quả mà ⛔ không ai hiểu vì sao. Nên: xoá được khi ⛔ <b>chưa</b> ai dùng, còn dùng rồi thì
 * đường đúng là {@code active = false} — biến mất khỏi ô chọn, còn nguyên trong lịch sử.
 */
@Service
public class ContactCategoryService {

    private final ContactCategoryRepository danhMuc;
    private final ContactRepository lienHe;

    public ContactCategoryService(ContactCategoryRepository danhMuc, ContactRepository lienHe) {
        this.danhMuc = danhMuc;
        this.lienHe = lienHe;
    }

    @Transactional(readOnly = true)
    public List<ContactCategory> danhSach() {
        return danhMuc.findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public ContactCategory tao(String maSo, String ten, int thuTu) {
        String ma = chuanHoaMa(maSo);
        String t = batBuoc(ten, "name");
        if (danhMuc.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new BusinessRuleException(ErrorCode.CMS_2019, ma);
        }
        return danhMuc.save(new ContactCategory(ma, t, thuTu));
    }

    /**
     * ⚠ ⛔ Không cho đổi {@code code}.
     *
     * <p>Mã là thứ bản xuất Excel và các báo cáo về sau tham chiếu tới. Đổi mã sau khi đã có dữ liệu
     * là làm mọi bản xuất cũ nói về một thứ ⛔ không còn tồn tại. Cần một mã khác thì tạo phân loại
     * mới và tắt cái cũ.
     */
    @Transactional
    public ContactCategory capNhat(UUID publicId, String ten, boolean dangDung, int thuTu) {
        ContactCategory c = tim(publicId);
        c.apDung(batBuoc(ten, "name"), dangDung, thuTu);
        return danhMuc.save(c);
    }

    @Transactional
    public void xoa(UUID publicId) {
        ContactCategory c = tim(publicId);
        long dangDung = lienHe.countByCategoryIdAndDeletedAtIsNull(c.getId());
        if (dangDung > 0) {
            throw new BusinessRuleException(ErrorCode.CMS_2020, String.valueOf(dangDung));
        }
        c.markDeleted(Instant.now());
        danhMuc.save(c);
    }

    private ContactCategory tim(UUID publicId) {
        return danhMuc.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** Khớp {@code ck_contact_categories_code}. Ràng buộc ở CSDL vẫn còn — đây là tầng trả lời được. */
    private static String chuanHoaMa(String maSo) {
        String ma = maSo == null ? "" : maSo.trim().toUpperCase();
        if (!ma.matches("^[A-Z][A-Z0-9_]{1,39}$")) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("code", "SAI_DINH_DANG", "^[A-Z][A-Z0-9_]{1,39}$");
        }
        return ma;
    }

    private static String batBuoc(String giaTri, String truong) {
        String v = giaTri == null ? "" : giaTri.trim();
        if (v.isEmpty()) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "BAT_BUOC", "");
        }
        return v;
    }
}
