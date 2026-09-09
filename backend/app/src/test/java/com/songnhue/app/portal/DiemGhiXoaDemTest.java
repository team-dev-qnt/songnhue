package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * <b>Mọi điểm ghi có thể đổi nội dung cổng đều phải được PHÂN LOẠI</b> — T41.20.
 *
 * <h2>Vì sao cần bài này khi đã có {@code PortalCacheInvalidationTest}</h2>
 *
 * Bài kia là bộ canh <b>hành vi</b>: nó gọi thật sáu đường ghi và <i>đếm</i> việc dựng lại cổng được
 * đặt vào hàng đợi. Nó chứng minh sáu đường ấy chạy đúng — và <b>không nói gì</b> về đường thứ bảy.
 * Đó chính là hình dạng đã lặp lại <b>năm lần</b> (§10.70, T27.7): nợ đệm cổng được trả ở những điểm
 * ghi đã biết, rồi điểm ghi tiếp theo ra đời mà không ai nối.
 *
 * <p>Lần thứ năm là {@code ArticleService.delete} — nó sống từ WS-12 tới WS-41. Hai điểm ghi anh em
 * của nó trong <i>cùng một tệp</i> đều gọi {@code portalCache.articleChanged}; riêng nó thì không.
 * Triệu chứng luôn giống nhau và luôn im lặng: màn hình báo <i>đã xoá</i>, cổng vẫn phục vụ bài ấy
 * cho tới hết TTL.
 *
 * <h2>⛔ Bài này KHÔNG suy đoán — nó đòi mỗi phương thức tự khai bằng chứng</h2>
 *
 * Lượt dựng bài này đã thử con đường "phân tích bắc cầu xem hàm nào rốt cuộc có gọi
 * {@code portalCache}" và <b>đo sai ba lượt liên tiếp</b>: 17 → 8 → 7 phương thức, mỗi lượt sai vì
 * một chuyện khác (không theo hàm phụ · không hiểu {@code @TransactionalEventListener} · mù trước
 * tham chiếu phương thức {@code this::recompute}). Một bộ canh mà phép phân tích của nó sai thì tệ
 * hơn không có bộ canh: cái xanh của nó đọc như một lời bảo đảm.
 *
 * <p>Nên thiết kế đảo lại: {@link #BANG_KHAI} liệt kê <b>từng</b> phương thức ghi kèm <b>cách</b> nó
 * bảo đảm đệm được xoá, và bài kiểm chỉ đi xác minh đúng bằng chứng mà lời khai ấy chỉ ra — mỗi
 * kiểm chứng là một phép so chuỗi trên <b>một</b> thân hàm, không có bước suy luận nào.
 *
 * <p>Giá trị thật nằm ở {@link #moiDiemGhiDeuPhaiDuocKhaiBao()}: thêm một phương thức ghi mà không
 * khai vào bảng ⇒ <b>đỏ</b>. Người thêm buộc phải trả lời câu hỏi <i>"cổng có đổi theo không"</i> —
 * đúng câu hỏi mà năm lần trước không ai hỏi.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <ul>
 *   <li>Chỉ soi lớp có <b>trường</b> {@code PortalCache} / {@code PortalCachePort}. Một lớp đổi dữ
 *       liệu lên cổng mà <i>không giữ</i> tham chiếu nào tới đệm thì nằm ngoài tầm bài này — và đó
 *       là một khuyết tật bài này ⛔ không thấy được.
 *   <li>Chỉ soi phương thức {@code public} có {@code @Transactional} không {@code readOnly}. Đường
 *       ghi đi bằng {@code JdbcTemplate} trong một lớp không đánh dấu giao dịch sẽ lọt.
 *   <li>Với phương thức nạp chồng, bằng chứng chỉ cần có ở <b>một</b> bản — bảng khai theo tên,
 *       không theo chữ ký.
 * </ul>
 */
class DiemGhiXoaDemTest {

    /** Cách một điểm ghi bảo đảm đệm cổng được xoá. */
    private enum Cach {
        /** Thân hàm gọi thẳng {@code portalCache.…}. */
        TRUC_TIEP,
        /** Thân hàm gọi một hàm khác <b>có tên</b> trong cùng lớp, và hàm ấy gọi {@code portalCache.…}. */
        UY_QUYEN,
        /** Lớp có một {@code @EventListener} / {@code @TransactionalEventListener} gọi {@code portalCache.…}. */
        SU_KIEN,
        /** ⛔ Không cần xoá đệm — {@code chiTiet} phải nói RÕ vì sao. */
        MIEN_TRU
    }

    private record Khai(Cach cach, String chiTiet) {}

    private static Khai trucTiep() {
        return new Khai(Cach.TRUC_TIEP, "");
    }

    private static Khai uyQuyen(String ten) {
        return new Khai(Cach.UY_QUYEN, ten);
    }

    private static Khai suKien(String lyDo) {
        return new Khai(Cach.SU_KIEN, lyDo);
    }

    private static Khai mienTru(String lyDo) {
        return new Khai(Cach.MIEN_TRU, lyDo);
    }

    /**
     * Mỗi phương thức ghi của mọi lớp giữ đệm cổng — đo 08/09/2026, <b>33 mục</b>.
     *
     * <p>⚠ Bảng này là <b>tài liệu</b>, không phải danh sách miễn trừ. Bốn nhóm khác nhau về nghĩa:
     * ba nhóm đầu khẳng định <i>có xoá, bằng đường này</i> và đều bị đi xác minh; chỉ
     * {@link Cach#MIEN_TRU} là <i>không cần xoá</i>, và mỗi dòng phải mang lý do đọc được.
     */
    private static final Map<String, Khai> BANG_KHAI = new LinkedHashMap<>();

    static {
        // ---- content: bài viết -------------------------------------------------
        BANG_KHAI.put("ArticleService.update", trucTiep());
        BANG_KHAI.put("ArticleService.execute", trucTiep());
        BANG_KHAI.put("ArticleService.delete", trucTiep());
        BANG_KHAI.put(
                "ArticleService.create",
                mienTru("Bài mới luôn ở BAN_NHAP — chưa có `publishedVersionId` nên cổng chưa từng phục vụ nó."));
        BANG_KHAI.put(
                "ArticleService.restoreVersion",
                mienTru("Phục hồi ghi vào BẢN SỬA; `publishedVersionId` không đổi ⇒ nội dung công khai giữ nguyên."));

        // ---- content: phản hồi / cấu hình giao diện ----------------------------
        BANG_KHAI.put(
                "FeedbackService.tiepNhan",
                mienTru("Mục gửi lên luôn là CHO_DUYET (chốt D1); cổng chỉ đọc mục DA_DUYET."));
        BANG_KHAI.put(
                "SiteConfigService.update",
                suKien(
                        "Cùng dòng `settings` còn sửa được từ MOD-05 và từ bản nhập cấu hình — chỉ sự kiện phủ đủ ba đường."));
        BANG_KHAI.put(
                "SiteConfigService.uploadBrandImage", suKien("Ghi qua `settings.updateInGroup`, cùng sự kiện trên."));

        // ---- core: sơ đồ tổ chức ----------------------------------------------
        BANG_KHAI.put("OrgUnitService.create", uyQuyen("bienDongToChuc"));
        BANG_KHAI.put("OrgUnitService.update", uyQuyen("bienDongToChuc"));
        BANG_KHAI.put("OrgUnitService.delete", uyQuyen("bienDongToChuc"));
        BANG_KHAI.put("OrgUnitService.move", uyQuyen("bienDongToChuc"));
        BANG_KHAI.put("OrgUnitService.reorder", uyQuyen("bienDongToChuc"));
        BANG_KHAI.put("OrgUnitLeaderService.them", trucTiep());
        BANG_KHAI.put("OrgUnitLeaderService.sua", trucTiep());
        BANG_KHAI.put("OrgUnitLeaderService.doiTrangThai", trucTiep());
        BANG_KHAI.put("OrgUnitLeaderService.xoa", trucTiep());

        // ---- operations: công trình -------------------------------------------
        BANG_KHAI.put("ConstructionService.create", uyQuyen("bienDongCongTrinh"));
        BANG_KHAI.put("ConstructionService.update", uyQuyen("bienDongCongTrinh"));
        // ⛔ T47.16 — đường ghi RIÊNG cho tệp nhập hàng loạt: nó đổi tên/tuyến sông/lý trình/
        //   toạ độ, tức đúng thứ cổng công khai phục vụ. Xem javadoc `capNhatTuTepNhap`.
        BANG_KHAI.put("ConstructionService.capNhatTuTepNhap", uyQuyen("bienDongCongTrinh"));
        BANG_KHAI.put("ConstructionService.delete", uyQuyen("bienDongCongTrinh"));
        BANG_KHAI.put("ConstructionService.changeLifecycle", uyQuyen("bienDongCongTrinh"));
        BANG_KHAI.put("ConstructionStatusService.recomputeFor", uyQuyen("recompute"));
        BANG_KHAI.put("ConstructionOperationStatusService.batchCreate", trucTiep());
        BANG_KHAI.put("OperationStatusCodeService.update", trucTiep());
        BANG_KHAI.put(
                "OperationStatusCodeService.create",
                mienTru("Mã vừa tạo chưa công trình nào tham chiếu ⇒ không ô nào trên cổng đổi."));
        BANG_KHAI.put(
                "OperationStatusCodeService.delete",
                mienTru("Ném OPS-2007 nếu mã đang được dùng ⇒ mã xoá được là mã không ai tham chiếu."));

        // ---- hydro: điểm đo, ngưỡng, sơ đồ nhập tay ----------------------------
        BANG_KHAI.put("StationService.create", trucTiep());
        BANG_KHAI.put("StationService.update", trucTiep());
        BANG_KHAI.put("StationService.delete", trucTiep());
        BANG_KHAI.put("AlertLevelService.create", trucTiep());
        BANG_KHAI.put("AlertLevelService.update", trucTiep());
        BANG_KHAI.put("AlertLevelService.delete", trucTiep());
        BANG_KHAI.put("SoDoNhapTayService.ghi", trucTiep());
    }

    private static final Pattern TRUONG_DEM =
            Pattern.compile("private\\s+final\\s+(?:PortalCache|PortalCachePort)\\s+(\\w+)\\s*;");

    /** {@code @Transactional} (không {@code readOnly}) đứng trước một phương thức {@code public}. */
    private static final Pattern PHUONG_THUC_GHI = Pattern.compile(
            "@Transactional(\\([^)]*\\))?\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public\\s+[\\w<>,\\[\\]\\s.]+?\\s+(\\w+)\\s*\\(");

    private static final Pattern NGHE_SU_KIEN = Pattern.compile("@(?:Transactional)?EventListener");

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Mọi điểm ghi phải được khai — thêm một đường ghi mà quên đệm cổng là ĐỎ")
    void moiDiemGhiDeuPhaiDuocKhaiBao() {
        Map<String, String> timDuoc = diemGhi();

        assertThat(timDuoc.keySet())
                .as(
                        """
                        Có phương thức ghi CHƯA được khai trong `BANG_KHAI`.

                        Đây là câu hỏi bắt buộc trả lời, không phải một dòng thêm cho hết đỏ: \
                        *thao tác này có đổi thứ gì cổng công khai đang phục vụ không?*
                          · CÓ  → gọi `portalCache.…` (hoặc uỷ quyền cho hàm đã có) rồi khai TRUC_TIEP / UY_QUYEN.
                          · KHÔNG → khai MIEN_TRU kèm lý do đọc được, ví dụ "bản ghi mới luôn ở trạng thái \
                        chờ duyệt nên cổng chưa phục vụ".

                        ⚠ Năm lần trước không ai hỏi câu ấy, và cả năm lần đều im lặng — xem §10.70.""")
                .isSubsetOf(BANG_KHAI.keySet());

        assertThat(BANG_KHAI.keySet())
                .as("`BANG_KHAI` còn dòng cho phương thức KHÔNG còn tồn tại — bảng khai đã mục, phải gỡ")
                .isSubsetOf(timDuoc.keySet());
    }

    @Test
    @DisplayName("Khai TRUC_TIEP phải đúng sự thật — thân hàm có gọi `portalCache.`")
    void khaiTrucTiepPhaiDungSuThat() {
        kiemTungKhai(Cach.TRUC_TIEP, (ten, khai, nguon, truong) -> {
            String than = thanGop(nguon, tenPhuongThuc(ten));
            assertThat(than)
                    .as("%s khai TRUC_TIEP mà thân hàm không gọi `%s.` — sửa mã hoặc sửa lời khai", ten, truong)
                    .contains(truong + ".");
        });
    }

    @Test
    @DisplayName("⭐ Khai UY_QUYEN phải đúng CẢ HAI vế — có gọi hàm được nêu, và hàm ấy xoá đệm")
    void khaiUyQuyenPhaiDungSuThat() {
        kiemTungKhai(Cach.UY_QUYEN, (ten, khai, nguon, truong) -> {
            String uyThac = khai.chiTiet();
            String than = thanGop(nguon, tenPhuongThuc(ten));

            // Vế 1 — có nhắc tới hàm được uỷ quyền. Chấp cả lời gọi `f(` lẫn tham chiếu `this::f`:
            // `ConstructionStatusService.recomputeFor` dùng dạng thứ hai, và một phép dò chỉ biết
            // dạng thứ nhất đã báo nhầm nó là khuyết tật ở lượt đo đầu.
            assertThat(than)
                    .as("%s khai uỷ quyền cho `%s` mà thân hàm không nhắc tới hàm ấy", ten, uyThac)
                    .containsPattern("(?:\\b|::)" + Pattern.quote(uyThac) + "\\s*[(),;\\s]");

            // Vế 2 — hàm được uỷ quyền thật sự xoá đệm. Thiếu vế này thì lời khai chỉ chứng minh
            // "có gọi một hàm", không chứng minh điều ta cần.
            assertThat(thanGop(nguon, uyThac))
                    .as("`%s` được %s uỷ quyền, nhưng chính nó không gọi `%s.`", uyThac, ten, truong)
                    .contains(truong + ".");
        });
    }

    @Test
    @DisplayName("Khai SU_KIEN phải đúng sự thật — lớp có listener và listener ấy xoá đệm")
    void khaiSuKienPhaiDungSuThat() {
        kiemTungKhai(Cach.SU_KIEN, (ten, khai, nguon, truong) -> {
            assertThat(NGHE_SU_KIEN.matcher(nguon).find())
                    .as("%s khai SU_KIEN mà lớp không có @EventListener nào", ten)
                    .isTrue();
            assertThat(nguon)
                    .as("%s khai SU_KIEN mà cả lớp không chỗ nào gọi `%s.`", ten, truong)
                    .contains(truong + ".");
            assertThat(khai.chiTiet())
                    .as("%s: khai SU_KIEN phải nói vì sao không gọi thẳng", ten)
                    .isNotBlank();
        });
    }

    @Test
    @DisplayName("⭐ Chiều ngược: dòng MIEN_TRU phải còn đúng — hàm nào bắt đầu xoá đệm thì phải đổi lời khai")
    void mienTruPhaiConDungLyDo() {
        // Khuôn `RbacMatrixTest#ngoaiLeQuyenPhaseSauVanConDung`. Không có vế này thì `BANG_KHAI` mục
        // dần: một hàm được nối đệm về sau vẫn nằm mãi ở nhóm "không cần", và lời khai thành sai.
        kiemTungKhai(Cach.MIEN_TRU, (ten, khai, nguon, truong) -> {
            assertThat(khai.chiTiet())
                    .as("%s: MIEN_TRU phải mang lý do đọc được, ⛔ không để trống", ten)
                    .isNotBlank();
            assertThat(thanGop(nguon, tenPhuongThuc(ten)))
                    .as("%s khai MIEN_TRU nhưng thân hàm ĐÃ gọi `%s.` — đổi lời khai sang TRUC_TIEP", ten, truong)
                    .doesNotContain(truong + ".");
        });
    }

    @Test
    @DisplayName("Phải quét ra ít nhất 10 lớp và 25 phương thức — chặn xanh-trên-tập-rỗng")
    void quetRaTapKhacRong() {
        // conventions.md §1.5. Đổi cách khai trường (ví dụ tiêm qua hàm dựng không giữ `private final`)
        // làm `TRUONG_DEM` trả rỗng, và cả bốn bài trên xanh mà không so gì.
        Map<String, String> ghi = diemGhi();
        assertThat(ghi).hasSizeGreaterThanOrEqualTo(25);
        assertThat(new TreeSet<>(
                        ghi.values().stream().map(DiemGhiXoaDemTest::tenLop).toList()))
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(BANG_KHAI).hasSizeGreaterThanOrEqualTo(25);
    }

    // =========================================================================

    /**
     * ⭐ Bốn bài trên chỉ có nghĩa nếu bộ đọc mã nguồn thật sự thấy thứ nó tưởng đang thấy.
     *
     * <p>Luật 29: bài kiểm chứng ngược do cùng người viết có thể sai theo đúng cách thứ nó kiểm đang
     * sai — nên khối này chạy trên <b>chuỗi nguồn dựng tay</b> có sẵn câu trả lời đúng, thay vì trên
     * kho thật.
     */
    @Nested
    @DisplayName("Tự kiểm chứng bộ đọc mã nguồn")
    class TuKiemChung {

        private static final String NGUON_GIA =
                """
                package x;
                class Thu {
                    private final PortalCache dem;

                    @Transactional(readOnly = true)
                    public List<X> doc() { return null; }

                    @Transactional
                    public void coXoa() { dem.articleChanged("a"); }

                    @Transactional
                    public void khongXoa() { repo.save(x); }

                    @Transactional
                    public void uyThac() { return q.map(this::phu).orElse(null); }

                    private void phu() { dem.layoutChanged(); }
                }
                """;

        @Test
        @DisplayName("Bỏ qua `readOnly = true`, bắt đúng ba phương thức ghi")
        void chiBatPhuongThucGhi() {
            assertThat(tenPhuongThucGhi(NGUON_GIA)).containsExactlyInAnyOrder("coXoa", "khongXoa", "uyThac");
        }

        @Test
        @DisplayName("⭐ Phân biệt được hàm CÓ xoá đệm với hàm KHÔNG — nếu không, bài TRUC_TIEP vô nghĩa")
        void phanBietDuocHaiTrangThai() {
            // Luật 9: một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì.
            assertThat(thanGop(NGUON_GIA, "coXoa")).contains("dem.");
            assertThat(thanGop(NGUON_GIA, "khongXoa")).doesNotContain("dem.");
        }

        @Test
        @DisplayName("⭐ Thấy tham chiếu phương thức `this::phu` — chỗ phép đo đầu tiên đã sai")
        void thayThamChieuPhuongThuc() {
            assertThat(thanGop(NGUON_GIA, "uyThac")).containsPattern("(?:\\b|::)phu\\s*[(),;\\s]");
            assertThat(thanGop(NGUON_GIA, "phu")).contains("dem.");
        }

        @Test
        @DisplayName("Đọc được tên trường đệm, và thân hàm dừng đúng dấu ngoặc đóng của nó")
        void docDuocTruongVaThanHam() {
            Matcher m = TRUONG_DEM.matcher(NGUON_GIA);
            assertThat(m.find()).isTrue();
            assertThat(m.group(1)).isEqualTo("dem");
            // `coXoa` không được nuốt sang thân `khongXoa` — brace-matching sai là mọi bài trên vô nghĩa.
            assertThat(thanGop(NGUON_GIA, "coXoa")).doesNotContain("repo.save");
        }
    }

    // =========================================================================

    @FunctionalInterface
    private interface PhepKiem {
        void kiem(String ten, Khai khai, String nguon, String truongDem);
    }

    private void kiemTungKhai(Cach cach, PhepKiem phep) {
        Map<String, String> ghi = diemGhi();
        int daKiem = 0;
        for (Map.Entry<String, Khai> muc : BANG_KHAI.entrySet()) {
            if (muc.getValue().cach() != cach) {
                continue;
            }
            String duongDan = ghi.get(muc.getKey());
            if (duongDan == null) {
                continue; // `moiDiemGhiDeuPhaiDuocKhaiBao` là nơi báo chuyện này, không phải ở đây.
            }
            String nguon = doc(Paths.get(duongDan));
            Matcher truong = TRUONG_DEM.matcher(nguon);
            assertThat(truong.find())
                    .as("%s: không đọc được tên trường đệm", muc.getKey())
                    .isTrue();
            phep.kiem(muc.getKey(), muc.getValue(), nguon, truong.group(1));
            daKiem++;
        }
        assertThat(daKiem)
                .as("Không mục nào thuộc nhóm %s được kiểm — bài này đang xanh trên tập rỗng", cach)
                .isGreaterThan(0);
    }

    /** {@code "Lớp.phươngThức"} → đường dẫn tệp nguồn, cho mọi lớp giữ đệm cổng. */
    private static Map<String, String> diemGhi() {
        Map<String, String> ket = new TreeMap<>();
        Path backend = timTuGocKho("backend");

        try {
            Files.walkFileTree(backend, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path thuMuc, BasicFileAttributes a) {
                    String ten = thuMuc.getFileName().toString();
                    return "target".equals(ten) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path tep, BasicFileAttributes a) {
                    String s = tep.toString();
                    if (!s.endsWith(".java") || !s.contains("src" + java.io.File.separator + "main")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String nguon = doc(tep);
                    // Chính `PortalCache` khai trường `JobPort`, không phải trường đệm — nó là bộ đệm,
                    // không phải một điểm ghi.
                    if (!TRUONG_DEM.matcher(nguon).find()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String lop = tep.getFileName().toString().replace(".java", "");
                    for (String pt : tenPhuongThucGhi(nguon)) {
                        ket.put(lop + "." + pt, s);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path tep, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Không quét được " + backend, e);
        }
        return ket;
    }

    private static List<String> tenPhuongThucGhi(String nguon) {
        List<String> ket = new ArrayList<>();
        Matcher m = PHUONG_THUC_GHI.matcher(nguon);
        while (m.find()) {
            String thamSo = m.group(1) == null ? "" : m.group(1);
            if (thamSo.contains("readOnly") && thamSo.contains("true")) {
                continue;
            }
            ket.add(m.group(2));
        }
        return ket;
    }

    /**
     * Thân của <b>mọi</b> bản nạp chồng mang tên này, nối lại.
     *
     * <p>Nối thay vì lấy bản đầu: bảng khai đánh theo tên chứ không theo chữ ký, nên câu hỏi đúng là
     * <i>"có bản nào mang bằng chứng không"</i>. Giới hạn ấy đã khai ở javadoc của lớp.
     */
    private static String thanGop(String nguon, String tenPhuongThuc) {
        Pattern khaiBao = Pattern.compile("(?:public|private|protected)\\s+[\\w<>,\\[\\]\\s.]*?\\b"
                + Pattern.quote(tenPhuongThuc)
                + "\\s*\\([^;{]*?\\)\\s*\\{");
        Matcher m = khaiBao.matcher(nguon);
        StringBuilder gop = new StringBuilder();
        while (m.find()) {
            int mo = nguon.indexOf('{', m.end() - 1);
            int sau = 0;
            for (int i = mo; i < nguon.length(); i++) {
                char c = nguon.charAt(i);
                if (c == '{') {
                    sau++;
                } else if (c == '}') {
                    sau--;
                    if (sau == 0) {
                        gop.append(nguon, mo, i + 1);
                        break;
                    }
                }
            }
        }
        if (gop.isEmpty()) {
            return fail("Không tìm thấy thân hàm `%s` — bộ đọc mã nguồn đã lạc hậu".formatted(tenPhuongThuc));
        }
        return gop.toString();
    }

    private static String tenPhuongThuc(String khoa) {
        return khoa.substring(khoa.indexOf('.') + 1);
    }

    private static String tenLop(String duongDan) {
        return duongDan.substring(duongDan.lastIndexOf(java.io.File.separatorChar) + 1);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
