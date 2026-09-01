package com.songnhue.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code ArticleVersion.snapshotOf} phải chép <b>MỌI</b> trường nội dung — quy tắc 14.
 *
 * <h2>Vì sao bài này không đếm hai trường mà đếm bằng PHẢN CHIẾU</h2>
 *
 * Thêm một cột nội dung vào bài viết là ba nơi con người phải nhớ cùng lúc: cột ở
 * {@code articles}, cột ở {@code article_versions}, và một dòng trong {@code snapshotOf}. Quên
 * dòng thứ ba thì <b>không bài kiểm nào đỏ</b> — bài vẫn xuất bản được, bản chụp vẫn được ghi, chỉ
 * là một ô trên cổng lặng lẽ rỗng sau lượt duyệt đầu tiên. Đúng hình dạng "làm xong nửa đường
 * trông y hệt làm xong" (quy tắc 19), và triệu chứng chỉ lộ ra khi có người nhập giá trị thật.
 *
 * <p>Nên bài này không liệt kê tên trường. Nó <b>đọc lược đồ của chính lớp {@code ArticleVersion}</b>
 * rồi đòi mỗi trường nội dung phải khác {@code null} sau một lượt chụp từ một {@code Article} đã
 * điền đủ. Trường nội dung thứ tư mà ai đó thêm ngày mai sẽ bị bắt mà không phải sửa bài này.
 *
 * <p>⛔ Danh sách loại trừ dưới đây là <b>đường ranh, không phải chỗ để dọn</b>: mỗi tên trong đó
 * đều có lý do riêng ghi kèm. Thêm một tên vào để làm bài kiểm xanh trở lại là gỡ đúng cái bẫy này.
 */
class ArticleVersionSnapshotTest {

    /**
     * Những trường KHÔNG phải nội dung của bài — chúng có nguồn khác, không đến từ {@code Article}.
     *
     * <ul>
     *   <li>{@code id}, {@code publicId} — do CSDL / hàm dựng sinh ra.
     *   <li>{@code articleId}, {@code versionNo}, {@code note}, {@code createdAt}, {@code createdBy}
     *       — siêu dữ liệu của chính bản chụp, {@code snapshotOf} nhận qua tham số hoặc đặt sau.
     * </ul>
     */
    private static final Set<String> KHONG_PHAI_NOI_DUNG =
            Set.of("id", "publicId", "articleId", "versionNo", "note", "createdAt", "createdBy");

    /** Một bài viết đã điền ĐỦ mọi trường nội dung — không trường nào để {@code null}. */
    private static Article baiDayDu() {
        Article a = new Article();
        a.setTitle("Quy chế quản lý công trình thuỷ lợi");
        a.setSlug("quy-che-quan-ly-cong-trinh-thuy-loi");
        a.setSummary("Trích yếu của văn bản");
        a.setContent("<p>Nội dung</p>");
        a.setCoverAttachmentPublicId(UUID.randomUUID());
        a.setMetaTitle("Meta");
        a.setMetaDescription("Mô tả");
        a.setMetaKeywords("thuỷ lợi");
        a.setDocNumber("43/2015/NĐ-CP");
        a.setDocIssuedDate(LocalDate.of(2015, 5, 4));
        a.setSource("Cổng TTĐT");
        a.setPublishedAt(Instant.parse("2026-08-28T03:00:00Z"));
        return a;
    }

    @Test
    @DisplayName("⭐⭐ MỌI trường nội dung của ArticleVersion đều được snapshotOf điền")
    void moiTruongNoiDungDeuDuocChep() throws IllegalAccessException {
        ArticleVersion ban = ArticleVersion.snapshotOf(baiDayDu(), 1, "Xuất bản");

        List<String> boSot = new java.util.ArrayList<>();
        for (Field f : ArticleVersion.class.getDeclaredFields()) {
            if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (KHONG_PHAI_NOI_DUNG.contains(f.getName())) {
                continue;
            }
            f.setAccessible(true);
            if (f.get(ban) == null) {
                boSot.add(f.getName());
            }
        }

        assertThat(boSot)
                .as(
                        """
                        Những trường này có trong `ArticleVersion` nhưng `snapshotOf` KHÔNG chép: %s

                        Hệ quả: cổng công khai đọc nội dung từ BẢN ĐÃ DUYỆT, nên ô tương ứng sẽ rỗng \
                        vĩnh viễn sau lượt duyệt đầu tiên — không lỗi nào, không dấu vết nào.
                        Chọn một: chép nó trong `snapshotOf` (và phục hồi trong `restoreInto`), \
                        hoặc khai nó vào KHONG_PHAI_NOI_DUNG kèm lý do.""",
                        boSot)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ TIỀN ĐỀ: phép phản chiếu thật sự soi được một tập KHÔNG rỗng")
    void phanChieuKhongSoiTapRong() {
        // Luật 7. Nếu bộ lọc loại trừ quét sạch mọi trường thì bài trên xanh trọn vẹn mà không
        // khẳng định gì — và số 8 dưới đây sẽ tụt xuống 0 mà không ai nhìn ra.
        long soTruongNoiDung = java.util.Arrays.stream(ArticleVersion.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> !KHONG_PHAI_NOI_DUNG.contains(f.getName()))
                .count();
        assertThat(soTruongNoiDung)
                .as("bộ lọc loại trừ đã nuốt hết trường nội dung ⇒ bài kiểm trên là trang trí")
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("⭐ restoreInto trả nội dung về bài — trừ slug, cố ý")
    void phucHoiTraLaiNoiDung() {
        Article goc = baiDayDu();
        ArticleVersion ban = ArticleVersion.snapshotOf(goc, 1, "Xuất bản");

        Article sau = new Article();
        sau.setSlug("dia-chi-cong-khai-khong-duoc-doi");
        ban.restoreInto(sau);

        assertThat(sau.getDocNumber()).isEqualTo("43/2015/NĐ-CP");
        assertThat(sau.getDocIssuedDate()).isEqualTo(LocalDate.of(2015, 5, 4));
        assertThat(sau.getSummary()).isEqualTo(goc.getSummary());
        // ⚠ Slug là địa chỉ công khai — phục hồi nội dung KHÔNG được đổi nó.
        assertThat(sau.getSlug()).isEqualTo("dia-chi-cong-khai-khong-duoc-doi");
    }

    @Test
    @DisplayName("⭐⭐ KIỂM CHỨNG NGƯỢC: một bản chụp bỏ sót trường PHẢI bị bắt")
    void kiemChungNguoc() throws IllegalAccessException {
        // Luật 1 + 29. Mô phỏng đúng lỗi cần bắt: chụp từ một bài CHƯA điền số ký hiệu và ngày
        // ban hành — hệt như một `snapshotOf` quên chép hai dòng ấy.
        Article thieu = baiDayDu();
        thieu.setDocNumber(null);
        thieu.setDocIssuedDate(null);
        ArticleVersion ban = ArticleVersion.snapshotOf(thieu, 1, "Xuất bản");

        List<String> boSot = new java.util.ArrayList<>();
        for (Field f : ArticleVersion.class.getDeclaredFields()) {
            if (f.isSynthetic()
                    || java.lang.reflect.Modifier.isStatic(f.getModifiers())
                    || KHONG_PHAI_NOI_DUNG.contains(f.getName())) {
                continue;
            }
            f.setAccessible(true);
            if (f.get(ban) == null) {
                boSot.add(f.getName());
            }
        }

        // Khẳng định về SỐ LƯỢNG, không chỉ về sự-có-mặt: nó không chia sẻ giả định nào với
        // vòng lặp ở bài trên.
        assertThat(boSot)
                .as("phép phản chiếu không phát hiện được trường bị bỏ sót ⇒ bài kiểm chính vô nghĩa")
                .hasSize(2)
                .containsExactlyInAnyOrder("docNumber", "docIssuedDate");
    }
}
