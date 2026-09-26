package com.songnhue.app.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.domain.notification.NotificationSeverity;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;

/**
 * ⛔⛔ <b>Chính sách người nhận phải là MỘT GIÁ TRỊ CÓ TÊN, ⛔ phải hai {@code boolean} cạnh nhau</b>
 * — T85.4, trả {@code T82.2}.
 *
 * <h2>Khuyết tật, đo ngày 23/09/2026 trên {@code origin/dev} {@code 25964e4e}</h2>
 *
 * <p>{@code NotifyRequest} và {@code NotificationRequest} mỗi cái khai hai {@code boolean} <b>đứng
 * liền nhau</b> ở cuối danh sách thành phần: {@code permissionScopedToUnits} rồi {@code nhomCanhBao}.
 * Trình biên dịch ép <b>có mặt</b> — thêm tham số bắt buộc làm mọi nơi dựng thô phải điền — nhưng nó
 * ⛔ ép <b>đúng</b>, vì hai tham số cùng kiểu thì hoán vị nhau vẫn biên dịch sạch.
 *
 * <p>Đo toàn kho cùng ngày: <b>12</b> nơi dựng thô trong {@code src/main} (6 là factory của chính hai
 * record, 6 là nơi gọi thật) + <b>1</b> trong {@code src/test}. Trong đó <b>6/12</b> nơi ở
 * {@code src/main} — {@code targeted} · {@code chiNhungNguoiNay} · {@code targetedWithUnits} ·
 * {@code NotificationController:90} · {@code UserAdminService:548} · {@code CanhBaoTaiKhoanService:79}
 * — khai <b>cả hai cờ {@code false}</b>, cộng nơi dựng duy nhất của {@code src/test}. Ở đúng bảy chỗ
 * ấy một lượt đảo chỗ là <b>phép ⛔ làm gì</b>: ⛔ một cổng kiểm nào của kho đỏ được.
 *
 * <p>Và rủi ro thật ⛔ nằm ở bảy chỗ đã viết đúng, nó nằm ở chỗ <b>thứ mười ba</b>: một nơi gọi mới
 * điền {@code true} vào ô sai sẽ lặng lẽ cộng cả nhóm <i>"Ban điều hành"</i> vào một lá thư riêng —
 * đúng khuyết tật mà T74.7 đã mất nhiều tuần mới thấy, và nó <b>đang ngủ</b> chứ ⛔ đã chết (khoá
 * {@code notification.alert-group.executive-board} seed {@code '[]'}, xem
 * {@link NhomCanhBaoKhongLanSangThuCaNhanTest}).
 *
 * <h2>Vì sao bài này đọc bằng PHẢN CHIẾU thay vì gọi thẳng tên kiểu mới</h2>
 *
 * <p>Một bài kiểm gọi thẳng {@code ChinhSachNguoiNhan} sẽ <b>⛔ biên dịch được</b> trên cây chưa vá,
 * tức lượt kiểm chứng ngược cho ra một <i>lỗi biên dịch</i> chứ ⛔ một <i>lượt đỏ đọc được</i> — đúng
 * cái bẫy T49.3 (một bản hỏng chưa được nạp in ra <i>⛔ có báo cáo nào</i>, ⛔ phải một dòng đỏ).
 * Đọc bằng phản chiếu thì cùng một tệp chạy được ở <b>cả hai</b> phía bản vá, nên nó đỏ đúng chỗ
 * trước khi vá và xanh sau khi vá — luật 10.
 *
 * <h2>Vì sao BA bài, ⛔ phải một</h2>
 *
 * <ul>
 *   <li><b>(1)</b> canh <i>hình dạng</i>: ⛔ còn thành phần {@code boolean} nào ⇒ ⛔ còn cặp nào để
 *       đảo. Đây là vế đỏ trên khuyết tật thật.
 *   <li><b>(2)</b> canh <i>một giá trị, ⛔ phải hai</i>: đúng MỘT thành phần mang chính sách. ⛔ có
 *       nó thì cách vá rẻ nhất — đổi hai {@code boolean} thành hai {@code enum} hai hằng — cũng làm
 *       bài (1) xanh mà cặp đảo được vẫn còn nguyên.
 *   <li><b>(3)</b> là <b>vế phân biệt</b> (luật 9 + luật 15): mọi hằng của kiểu ấy phải có ít nhất
 *       một factory sinh ra nó. ⛔ có nó thì một kiểu ba hằng dùng-hai cũng đi lọt, và hằng thứ ba
 *       là một công tắc ⛔ ai đọc.
 * </ul>
 */
class ChinhSachNguoiNhanLaMotKieuTest {

    /**
     * Hai record cùng mang chính sách người nhận: bản SPI mà module nghiệp vụ cầm, và bản
     * {@code application} mà {@code NotificationService} dịch sang.
     */
    private static final List<Class<?>> HAI_RECORD = List.of(NotifyRequest.class, NotificationRequest.class);

    /**
     * ⚠ Hai kiểu liệt kê <b>⛔ thuộc</b> phép đo này: chúng có mặt từ WS-6, mang <i>mức nặng</i> chứ
     * ⛔ mang chính sách người nhận. Loại trừ đích danh — ⛔ loại bằng tên thành phần — vì tên thành
     * phần đổi được trong một lượt dọn dẹp còn kiểu thì ⛔ (T46.7: một bộ canh bám vào văn bản là một
     * bộ canh đang canh văn bản).
     */
    private static final Set<Class<?>> KIEU_LIET_KE_KHAC = Set.of(NotifySeverity.class, NotificationSeverity.class);

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⛔⛔ (1) ⛔ record nào còn thành phần `boolean` — còn một cặp là còn một lượt đảo chỗ ⛔ ai thấy")
    void khongConThanhPhanBoolean() {
        for (Class<?> record : HAI_RECORD) {
            List<RecordComponent> coNao = thanhPhan(record);

            assertThat(coNao)
                    .as(
                            "⚠ TIỀN ĐỀ (luật 29): `%s` phải là một record ĐANG CÓ thành phần. Một tập rỗng ở "
                                    + "đây làm mọi khẳng định bên dưới xanh mà ⛔ khẳng định gì — đúng ca luật 7.",
                            record.getSimpleName())
                    .hasSizeGreaterThan(8);

            assertThat(coNao.stream()
                            .filter(c -> c.getType() == boolean.class)
                            .map(RecordComponent::getName)
                            .toList())
                    .as(
                            "⛔⛔ `%s` còn thành phần `boolean`. Hai cờ cạnh nhau thì hoán vị nhau vẫn biên dịch "
                                    + "sạch, và đo 23/09/2026 có 6/12 nơi dựng ở `src/main` khai CẢ HAI `false` ⇒ đảo "
                                    + "chỗ ở đó là phép ⛔ làm gì. Chính sách người nhận phải là MỘT giá trị có tên "
                                    + "(T85.4 ← T82.2).",
                            record.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("⚠ (2) Đúng MỘT thành phần mang chính sách — hai kiểu liệt kê cạnh nhau vẫn là một cặp đảo được")
    void dungMotThanhPhanMangChinhSach() {
        for (Class<?> record : HAI_RECORD) {
            assertThat(thanhPhanChinhSach(record))
                    .as(
                            "⛔ `%s` phải mang ĐÚNG MỘT thành phần kiểu liệt kê ngoài mức nặng. Hai thì cặp đảo "
                                    + "chỗ vẫn còn — chỉ là ⛔ còn mang kiểu `boolean`; ⛔ có thì chính sách lại quay về "
                                    + "chỗ phải SUY từ hình dạng dữ liệu, đúng thứ T74.7 đã trả giá.",
                            record.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("⚠ VẾ PHÂN BIỆT (luật 9 · luật 15) — mọi hằng chính sách phải có factory sinh ra nó")
    void moiHangDeuCoNoiSinhRa() {
        List<RecordComponent> mangChinhSach = thanhPhanChinhSach(NotifyRequest.class);

        // ⚠ Khẳng định tiền đề TRƯỚC khi bóc phần tử: `getFirst()` trên tập rỗng ném
        //   `NoSuchElementException` trần, và một bộ canh đỏ mà ⛔ nói được vì sao thì gần như ⛔ có
        //   (§11.20). Đo được ở lượt chạy đầu của chính lớp này, trên cây chưa vá.
        assertThat(mangChinhSach)
                .as("⚠ TIỀN ĐỀ: `NotifyRequest` phải mang đúng một thành phần kiểu liệt kê ngoài mức nặng "
                        + "thì bài này mới đo được gì. Đỏ ở đây nghĩa là bài (2) cũng đang đỏ — đọc nó trước.")
                .hasSize(1);

        RecordComponent coChinhSach = mangChinhSach.getFirst();
        Class<?> kieu = coChinhSach.getType();

        Set<Object> daDung = new LinkedHashSet<>();
        for (NotifyRequest yeuCau : namFactory()) {
            daDung.add(docChinhSach(coChinhSach, yeuCau));
        }

        assertThat(daDung)
                .as(
                        "⛔ Một hằng ⛔ factory nào sinh ra là một công tắc ⛔ ai đọc (luật 15), và nó làm bộ "
                                + "`switch` của `RecipientResolver` mang một nhánh chưa lượt nào đi qua (luật 7). "
                                + "Năm factory của `NotifyRequest` phải phủ ĐỦ %d hằng của `%s`.",
                        kieu.getEnumConstants().length, kieu.getSimpleName())
                .containsExactlyInAnyOrder(kieu.getEnumConstants());

        assertThat(daDung)
                .as("⛔ Một kiểu mà mọi factory cùng trả một giá trị ⛔ phân biệt được hai trạng thái nào "
                        + "(luật 9) — nó đọc như một chính sách mà thật ra là một hằng số.")
                .hasSizeGreaterThanOrEqualTo(3);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private static List<RecordComponent> thanhPhan(Class<?> record) {
        RecordComponent[] coNao = record.getRecordComponents();
        return coNao == null ? List.of() : List.of(coNao);
    }

    /** Thành phần kiểu liệt kê, trừ hai kiểu <i>mức nặng</i> vốn ⛔ liên quan tới người nhận. */
    private static List<RecordComponent> thanhPhanChinhSach(Class<?> record) {
        return thanhPhan(record).stream()
                .filter(c -> c.getType().isEnum())
                .filter(c -> !KIEU_LIET_KE_KHAC.contains(c.getType()))
                .toList();
    }

    /**
     * ⚠ Gọi factory với <b>cùng một bộ đối số</b> ở cả năm chỗ: thứ duy nhất được phép khác nhau giữa
     * năm kết quả là chính sách người nhận. Khác đối số thì bài (3) vẫn xanh trong khi nó đang đo một
     * thứ khác.
     */
    private static List<NotifyRequest> namFactory() {
        final String suKien = "T85_DO_CHINH_SACH";
        final String tieuDe = "Bài đo chính sách người nhận";
        final String than = "⛔ gửi đi đâu — record dựng ở bộ nhớ, ⛔ qua NotificationService.";
        final NotifySeverity nang = NotifySeverity.INFO;
        final List<Long> donVi = List.of(1L);
        final List<Long> nguoi = List.of(2L);
        final String quyen = "adm:notification:send";

        return List.of(
                NotifyRequest.alert(suKien, tieuDe, than, nang, donVi),
                NotifyRequest.targeted(suKien, tieuDe, than, nang, quyen, nguoi),
                NotifyRequest.chiNhungNguoiNay(suKien, tieuDe, than, nang, nguoi),
                NotifyRequest.targetedWithUnits(suKien, tieuDe, than, nang, quyen, donVi, nguoi),
                NotifyRequest.targetedInUnitScope(suKien, tieuDe, than, nang, quyen, donVi, nguoi));
    }

    private static Object docChinhSach(RecordComponent co, NotifyRequest yeuCau) {
        try {
            return co.getAccessor().invoke(yeuCau);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("⛔ đọc được thành phần '%s'".formatted(co.getName()), e);
        }
    }
}
