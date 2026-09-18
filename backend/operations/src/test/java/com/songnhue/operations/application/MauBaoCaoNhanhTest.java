package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.export.DocxFiller;
import com.songnhue.operations.domain.Bang3SongNhue;

/**
 * ⭐⭐ Vân tay + hình học của mẫu Word Báo cáo nhanh.
 *
 * <p>{@link BaoCaoNhanhDocx} gọi ô bằng CHỈ SỐ. Ai thay tệp mẫu (Công ty gửi bản mới) mà ⛔ đối chiếu
 * lại toạ độ thì số sẽ rơi vào ô khác — văn bản vẫn mở được, vẫn trông đầy đủ. ⇒ Bài này đỏ NGAY khi tệp
 * đổi một byte, và người sửa phải đi qua từng khẳng định nhãn dưới đây. Cùng khuôn
 * {@code db-migration-checksums.txt}.
 */
class MauBaoCaoNhanhTest {

    /** SHA-256 của {@code docs_origin/bao-cao/Mẫu Báo cáo nhanh.docx} nhận ngày 18/09/2026. */
    private static final String VAN_TAY = "ae1adba137d1fda22c0dd9a8bc7592dceca78241cb1e5f64b04a7ea0b87f3c3c";

    @Test
    @DisplayName("⭐⭐ Vân tay mẫu — đổi tệp là phải đối chiếu lại MỌI toạ độ ô của BaoCaoNhanhDocx")
    void vanTayMau() throws Exception {
        String bam =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BaoCaoNhanhDocx.docMau()));
        assertThat(bam).isEqualTo(VAN_TAY);
    }

    @Test
    @DisplayName("⭐ Hình học: 9 bảng cấp gốc, số dòng từng bảng đúng như lúc dựng toạ độ")
    void hinhHoc() {
        DocxFiller f = DocxFiller.mo(BaoCaoNhanhDocx.docMau());
        assertThat(f.soBang()).isEqualTo(9);
        assertThat(IntStream.range(0, 9).map(f::soDong).toArray()).containsExactly(1, 7, 7, 1, 7, 234, 39, 39, 91);
    }

    @Test
    @DisplayName("⭐ Nhãn tại đúng các toạ độ BaoCaoNhanhDocx ghi vào — dòng Sông Nhuệ, cống, xã")
    void nhanTaiToaDo() {
        DocxFiller f = DocxFiller.mo(BaoCaoNhanhDocx.docMau());
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_MUC1, BaoCaoNhanhDocx.DONG_SONG_NHUE_MUC, 1))
                .contains("Sông Nhuệ");
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_MUC3, BaoCaoNhanhDocx.DONG_SONG_NHUE_MUC, 1))
                .contains("Sông Nhuệ");
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_1, BaoCaoNhanhDocx.DONG_SONG_NHUE_MUC, 1))
                .contains("Sông Nhuệ");
        assertThat(f.soO(BaoCaoNhanhDocx.BANG_1, BaoCaoNhanhDocx.DONG_SONG_NHUE_MUC))
                .as("TT + tên + trạm + máy + 9 cỡ + lưu lượng")
                .isEqualTo(14);
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_5, BaoCaoNhanhDocx.B5_DONG_SONG_NHUE, 1))
                .contains("Sông Nhuệ");
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_5, BaoCaoNhanhDocx.B5_DONG_SONG_NHUE + 1, 1)
                        .trim())
                .isEqualTo("Đông Ngạc");
        assertThat(f.docO(BaoCaoNhanhDocx.BANG_2, BaoCaoNhanhDocx.B2_KHUON_KHOI, 0))
                .isEqualTo("I");

        for (int i = 0; i < Bang3SongNhue.DONG.size(); i++) {
            Bang3SongNhue.Dong d = Bang3SongNhue.DONG.get(i);
            int dong = BaoCaoNhanhDocx.B3_DONG_DAU + 2 * i;
            assertThat(f.docO(BaoCaoNhanhDocx.BANG_3, dong, 1))
                    .as("cống dòng %d", dong)
                    .startsWith(d.nhanCong());
            assertThat(f.docO(BaoCaoNhanhDocx.BANG_3, dong, 2).trim()).isEqualTo(d.nhanTl());
            assertThat(f.docO(BaoCaoNhanhDocx.BANG_3, dong + 1, 1).trim()).isEqualTo(d.lyTrinh());
            assertThat(f.docO(BaoCaoNhanhDocx.BANG_3, dong + 1, 2).trim()).isEqualTo(d.nhanHl());
        }
    }
}
