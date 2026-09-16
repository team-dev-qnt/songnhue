package com.songnhue.app.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Label;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.MethodVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;

import com.songnhue.app.error.fixture.NoiNemMaLoiFixtures;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;

/**
 * <b>Số đối số ở nơi ném phải khớp số chỗ cắm {@code {n}} của câu thông báo</b> — T61.13 (nợ của T57.8).
 *
 * <h2>Hai chiều hỏng, cả hai IM LẶNG</h2>
 *
 * <ul>
 *   <li><b>Thừa đối số</b> — {@code new BusinessRuleException(ErrorCode.X, tenDonVi)} trỏ vào một câu
 *       ⛔ không có {@code {0}}. {@code MessageFormat} bỏ lặng đối số ⇒ người viết tưởng mình vừa nói
 *       cho người dùng <i>cái gì</i> sai, người dùng đọc một câu chung chung.
 *   <li><b>Thiếu đối số</b> — câu có {@code {0}} mà nơi ném ⛔ truyền gì. Spring trả nguyên văn câu
 *       khi mảng đối số rỗng ⇒ người dùng đọc <b>chữ {@code {0}}</b> trên màn hình.
 * </ul>
 *
 * <h2>⛔ Vì sao đọc BYTECODE, ⛔ quét văn bản</h2>
 *
 * T57.8 từ chối dựng bộ canh bằng regex (<i>"đếm đối số ở lời gọi bằng regex là canh văn bản"</i>) —
 * và đúng: {@code grep} thô ra 397 kết quả, ⛔ tách được đối số khỏi dấu phẩy trong một biểu thức lồng,
 * còn Spotless ngắt dòng lời gọi tuỳ độ dài (§11.13). Trong bytecode thì varargs là một hình dạng cố
 * định mà javac sinh ra: {@code GETSTATIC ErrorCode.X} → hằng {@code n} → {@code ANEWARRAY Object} →
 * … → {@code INVOKESPECIAL <init>(LErrorCode;…[Ljava/lang/Object;)V}. Kích thước mảng CHÍNH LÀ số
 * đối số người viết gõ.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <ul>
 *   <li>Đo mọi lời gọi {@code new} tới một lớp con của {@link AppException} trong mã sản phẩm
 *       {@code com.songnhue}. Dạng ⛔ có mã tường minh ({@code new ValidationException(a)}) được quy về
 *       mã mặc định <b>đọc từ chính bytecode</b> của hàm dựng ấy, ⛔ gõ tay.
 *   <li>⛔ Đo được khi mã lỗi hoặc mảng đối số đi qua một <b>biến</b> (VD
 *       {@code ErrorCode ma = …; throw new X(ma)}). Những nơi ấy được đếm vào {@code khongDoDuoc} và
 *       in ra; bài {@link #quetRaTapKhacRong()} chặn chúng phình lên che mất phép đo chính.
 *   <li>Dùng ASM <b>đóng gói sẵn trong ArchUnit</b> ({@code thirdparty}) để ⛔ thêm một phụ thuộc. Nếu
 *       ArchUnit đổi cách đóng gói thì tệp này hỏng BIÊN DỊCH — một lỗi ồn ào, ⛔ im lặng.
 * </ul>
 */
class DoiSoMaLoiKhopChoCamTest {

    private static final String ERROR_CODE = "com/songnhue/core/common/error/ErrorCode";
    private static final String APP_EXCEPTION = "com/songnhue/core/common/exception/AppException";

    /** Cùng tập với {@code architecture.ProductionClasses} (lớp ấy package-private ở gói khác). */
    private static final JavaClasses SAN_PHAM = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.songnhue");

    /** Một nơi ném đo được. */
    record NoiNem(String lop, String phuongThuc, int dong, String ma, int soDoiSo) {
        String viTri() {
            return lop.replace('/', '.') + "#" + phuongThuc + ":" + dong;
        }
    }

    /** Kết quả quét một tập lớp. */
    record KetQua(List<NoiNem> noiNem, List<String> khongDoDuoc) {}

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Mọi nơi ném truyền ĐÚNG số đối số mà câu thông báo có chỗ cắm")
    void soDoiSoKhopSoChoCam() {
        assertThat(viPham(quet(SAN_PHAM)))
                .as(
                        """
                        Những nơi ném này truyền số đối số KHÁC số chỗ cắm {n} trong \
                        `error-messages.properties`:
                          • THỪA ⇒ MessageFormat bỏ lặng đối số, người dùng ⛔ biết CÁI GÌ sai.
                          • THIẾU ⇒ người dùng đọc nguyên chữ "{0}" trên màn hình.
                        ⇒ Thêm chỗ cắm vào câu (nếu đối số giúp người dùng sửa được lỗi), HOẶC bỏ đối số \
                        (nếu nó chỉ là chi tiết kỹ thuật — chỗ của nó là log hoặc `withDetail`).""")
                .isEmpty();
    }

    @Test
    @DisplayName("Phải đo được ≥ 300 nơi ném, và nơi ⛔ đo được phải ≤ 5% — chặn xanh-trên-tập-rỗng")
    void quetRaTapKhacRong() {
        KetQua kq = quet(SAN_PHAM);
        assertThat(kq.noiNem()).as("Tập nơi ném đo được").hasSizeGreaterThanOrEqualTo(300);
        assertThat(kq.noiNem().stream().map(NoiNem::ma).distinct().count())
                .as("Số mã lỗi khác nhau có nơi ném")
                .isGreaterThanOrEqualTo(80);
        assertThat(kq.khongDoDuoc())
                .as("Nơi ném ⛔ đo được (mã/mảng đi qua biến) — phình lên là phép đo chính mù dần")
                .hasSizeLessThanOrEqualTo(kq.noiNem().size() / 20);
        assertThat(soChoCam()).as("Đọc được câu thông báo").hasSizeGreaterThanOrEqualTo(100);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: bộ đếm đọc đúng từng hình dạng lời gọi trong fixture (luật 9)")
    void tuKiemChung() throws IOException {
        // SYS_0009 có đúng MỘT chỗ cắm ({0} là trạng thái quét) — neo cho mọi vế dưới.
        assertThat(soChoCam()).containsEntry("SYS_0009", 1).containsEntry("SYS_0004", 0);

        KetQua kq = quet(new ClassFileImporter().importClasses(NoiNemMaLoiFixtures.class));
        Map<String, List<String>> theoPhuongThuc = kq.noiNem().stream()
                .collect(Collectors.groupingBy(
                        NoiNem::phuongThuc,
                        TreeMap::new,
                        Collectors.mapping(n -> n.ma() + "×" + n.soDoiSo(), Collectors.toList())));

        assertThat(theoPhuongThuc)
                .containsEntry("thuaDoiSo", List.of("SYS_0004×1"))
                .containsEntry("thieuDoiSo", List.of("SYS_0009×0"))
                .containsEntry("dungMotDoiSo", List.of("SYS_0009×1"))
                .containsEntry("dungKhongDoiSo", List.of("SYS_0004×0"))
                .as("Hàm dựng có nguyên nhân: mã đứng TRƯỚC Throwable, mảng đứng SAU")
                .containsEntry("coNguyenNhan", List.of("SYS_0009×1"))
                .as("Dạng ⛔ có mã tường minh quy về mã MẶC ĐỊNH đọc từ bytecode hàm dựng")
                .containsEntry("maMacDinh", List.of("SYS_0003×2"))
                .as("String.format bên trong đối số ⛔ được ghi đè kích thước mảng của ngoại lệ ngoài")
                .containsEntry("formatTrongDoiSo", List.of("SYS_0009×1"))
                .as("Ngoại lệ dựng BÊN TRONG đối số: hai nơi ném, hai số đếm, ⛔ lẫn nhau")
                .containsEntry("ngoaiLeLongNhau", List.of("SYS_0004×2", "SYS_0009×1"));
        assertThat(theoPhuongThuc).doesNotContainKey("maQuaBien");

        // Và LUẬT (⛔ chỉ bộ đếm) bắt đúng 4 nơi hỏng, tha đúng 5 nơi đúng — thiếu vế tha thì một luật
        // "từ chối tất cả" cũng xanh ở đây.
        assertThat(viPham(kq))
                .extracting(s -> s.replaceAll(":\\d+ — .*", "").replaceAll(".*#", ""))
                .containsExactlyInAnyOrder("maMacDinh", "ngoaiLeLongNhau", "thieuDoiSo", "thuaDoiSo");
        assertThat(kq.khongDoDuoc()).as("Mã đi qua biến").anyMatch(s -> s.contains("maQuaBien"));
    }

    // =========================================================================

    static List<String> viPham(KetQua kq) {
        Map<String, Integer> choCam = soChoCam();
        return kq.noiNem().stream()
                .filter(n -> n.soDoiSo() != choCam.getOrDefault(n.ma(), 0))
                .map(n -> "%s — %s: %d đối số, câu có %d chỗ cắm"
                        .formatted(n.viTri(), n.ma(), n.soDoiSo(), choCam.getOrDefault(n.ma(), 0)))
                .sorted()
                .toList();
    }

    /** Số chỗ cắm theo TÊN hằng ({@code SYS_0009}), đọc bằng chính {@link MessageFormat}. */
    static Map<String, Integer> soChoCam() {
        Properties p = new Properties();
        try (InputStream in = ErrorCode.class.getClassLoader().getResourceAsStream("error-messages.properties")) {
            assertThat(in).as("error-messages.properties trên classpath").isNotNull();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Integer> ket = new HashMap<>();
        for (ErrorCode c : ErrorCode.values()) {
            String cau = p.getProperty(c.messageKey());
            if (cau != null) {
                ket.put(c.name(), new MessageFormat(cau).getFormatsByArgumentIndex().length);
            }
        }
        return ket;
    }

    static KetQua quet(JavaClasses lop) {
        Map<String, String> cha = new HashMap<>();
        for (JavaClass c : lop) {
            c.getRawSuperclass().ifPresent(s -> cha.put(noi(c.getName()), noi(s.getName())));
        }
        // Lớp nền của core ⛔ nằm trong tập quét fixture — khai chuỗi cha của chúng từ classpath.
        for (Class<? extends AppException> con : List.of(
                com.songnhue.core.common.exception.AuthenticationException.class,
                com.songnhue.core.common.exception.BusinessRuleException.class,
                com.songnhue.core.common.exception.ConflictException.class,
                com.songnhue.core.common.exception.PermissionDeniedException.class,
                com.songnhue.core.common.exception.RateLimitException.class,
                com.songnhue.core.common.exception.ResourceNotFoundException.class,
                com.songnhue.core.common.exception.UpstreamException.class,
                com.songnhue.core.common.exception.ValidationException.class)) {
            cha.putIfAbsent(noi(con.getName()), APP_EXCEPTION);
        }
        Map<String, String> maMacDinh = new HashMap<>();
        for (String lopNgoaiLe : List.copyOf(cha.keySet())) {
            if (laNgoaiLe(lopNgoaiLe, cha)) {
                String md = maMacDinhCua(lopNgoaiLe);
                if (md != null) {
                    maMacDinh.put(lopNgoaiLe, md);
                }
            }
        }

        List<NoiNem> noiNem = new ArrayList<>();
        List<String> khongDoDuoc = new ArrayList<>();
        for (JavaClass c : lop) {
            if (laNgoaiLe(noi(c.getName()), cha)) {
                continue; // hàm dựng chuyển tiếp `super(code, args)` ⛔ phải một nơi ném
            }
            doc(c, new BoDem(noi(c.getName()), cha, maMacDinh, noiNem, khongDoDuoc));
        }
        return new KetQua(noiNem, khongDoDuoc);
    }

    private static boolean laNgoaiLe(String lop, Map<String, String> cha) {
        for (String k = lop; k != null; k = cha.get(k)) {
            if (APP_EXCEPTION.equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** Mã mặc định = hằng {@code ErrorCode} DUY NHẤT trong {@code <init>([Ljava/lang/Object;)V}. */
    private static String maMacDinhCua(String lop) {
        String[] ket = {null};
        try (InputStream in = moTepLop(lop)) {
            if (in == null) {
                return null;
            }
            new ClassReader(in)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(
                                        int acc, String ten, String moTa, String sig, String[] ex) {
                                    if (!"<init>".equals(ten) || !"([Ljava/lang/Object;)V".equals(moTa)) {
                                        return null;
                                    }
                                    return new MethodVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visitFieldInsn(int op, String owner, String name, String desc) {
                                            if (op == Opcodes.GETSTATIC && ERROR_CODE.equals(owner)) {
                                                ket[0] = name;
                                            }
                                        }
                                    };
                                }
                            },
                            ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ket[0];
    }

    private static void doc(JavaClass c, ClassVisitor v) {
        URI uri = c.getSource().orElseThrow().getUri();
        try (InputStream in = uri.toURL().openStream()) {
            new ClassReader(in).accept(v, ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được bytecode " + uri, e);
        }
    }

    private static InputStream moTepLop(String lop) {
        return DoiSoMaLoiKhopChoCamTest.class.getClassLoader().getResourceAsStream(lop + ".class");
    }

    private static String noi(String ten) {
        return ten.replace('.', '/');
    }

    /**
     * Mô phỏng NGĂN XẾP các lời gọi đang dựng dở: mỗi {@code NEW <ngoại lệ>} mở một khung; mã lỗi và
     * kích thước mảng varargs gắn vào khung TRÊN CÙNG chưa có giá trị ấy; {@code INVOKESPECIAL <init>}
     * đóng khung trên cùng. Nhờ vậy một ngoại lệ dựng BÊN TRONG đối số của ngoại lệ khác (hay một
     * {@code String.format} bên trong đối số) ⛔ ghi nhầm vào khung ngoài.
     */
    private static final class BoDem extends ClassVisitor {
        private final String lop;
        private final Map<String, String> cha;
        private final Map<String, String> maMacDinh;
        private final List<NoiNem> noiNem;
        private final List<String> khongDoDuoc;

        BoDem(
                String lop,
                Map<String, String> cha,
                Map<String, String> maMacDinh,
                List<NoiNem> noiNem,
                List<String> khongDoDuoc) {
            super(Opcodes.ASM9);
            this.lop = lop;
            this.cha = cha;
            this.maMacDinh = maMacDinh;
            this.noiNem = noiNem;
            this.khongDoDuoc = khongDoDuoc;
        }

        @Override
        public MethodVisitor visitMethod(int acc, String ten, String moTa, String sig, String[] ex) {
            return new MethodVisitor(Opcodes.ASM9) {
                final Deque<Khung> khung = new ArrayDeque<>();
                int dong = -1;
                Integer hangSoVuaNap;

                @Override
                public void visitLineNumber(int line, Label start) {
                    dong = line;
                }

                @Override
                public void visitTypeInsn(int op, String type) {
                    if (op == Opcodes.NEW && laNgoaiLe(type, cha)) {
                        khung.push(new Khung(type));
                    } else if (op == Opcodes.ANEWARRAY && "java/lang/Object".equals(type)) {
                        Khung k = khung.peek();
                        if (k != null && k.soDoiSo == null && !k.daCoMang) {
                            k.soDoiSo = hangSoVuaNap; // null ⇒ kích thước là biến ⇒ ⛔ đo được
                            k.daCoMang = true;
                        }
                    }
                    hangSoVuaNap = null;
                }

                @Override
                public void visitFieldInsn(int op, String owner, String name, String desc) {
                    Khung k = khung.peek();
                    if (op == Opcodes.GETSTATIC
                            && ERROR_CODE.equals(owner)
                            && k != null
                            && k.ma == null
                            && !k.daCoMang) {
                        k.ma = name;
                    }
                    hangSoVuaNap = null;
                }

                @Override
                public void visitInsn(int op) {
                    hangSoVuaNap = op >= Opcodes.ICONST_0 && op <= Opcodes.ICONST_5 ? op - Opcodes.ICONST_0 : null;
                }

                @Override
                public void visitIntInsn(int op, int operand) {
                    hangSoVuaNap = op == Opcodes.BIPUSH || op == Opcodes.SIPUSH ? operand : null;
                }

                @Override
                public void visitVarInsn(int op, int var) {
                    hangSoVuaNap = null;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    hangSoVuaNap = null;
                }

                @Override
                public void visitJumpInsn(int op, Label label) {
                    hangSoVuaNap = null;
                }

                @Override
                public void visitInvokeDynamicInsn(
                        String name,
                        String desc,
                        com.tngtech.archunit.thirdparty.org.objectweb.asm.Handle bsm,
                        Object... bsmArgs) {
                    hangSoVuaNap = null;
                }

                @Override
                public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                    hangSoVuaNap = null;
                    if (op != Opcodes.INVOKESPECIAL
                            || !"<init>".equals(name)
                            || khung.isEmpty()
                            || !khung.peek().lop.equals(owner)) {
                        return;
                    }
                    Khung k = khung.pop();
                    boolean coMaThamSo = desc.startsWith("(L" + ERROR_CODE + ";");
                    boolean coMang = desc.endsWith("[Ljava/lang/Object;)V");
                    if (!coMang) {
                        return; // hàm dựng riêng của một lớp con (⛔ varargs) — ⛔ thuộc phạm vi
                    }
                    String ma = coMaThamSo ? k.ma : maMacDinh.get(owner);
                    String viTri = lop.replace('/', '.') + "#" + ten + ":" + dong;
                    if (ma == null || k.soDoiSo == null) {
                        khongDoDuoc.add(viTri + (ma == null ? " (mã qua biến)" : " (mảng qua biến)"));
                        return;
                    }
                    noiNem.add(new NoiNem(lop, ten, dong, ma, k.soDoiSo));
                }
            };
        }
    }

    private static final class Khung {
        final String lop;
        String ma;
        Integer soDoiSo;
        boolean daCoMang;

        Khung(String lop) {
            this.lop = lop;
        }
    }
}
