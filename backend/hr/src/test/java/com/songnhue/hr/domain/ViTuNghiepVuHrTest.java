package com.songnhue.hr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ⛔⛔ <b>Bộ kiểm ĐẦU TIÊN của module {@code hr}</b> — T68.31.
 *
 * <h2>Vì sao lớp này tồn tại</h2>
 *
 * <p>Đo 20/09/2026: {@code backend/hr/src/test} có <b>0</b> tệp. Hệ quả ⛔ phải <i>"thiếu vài bài
 * kiểm"</i> mà là <b>cổng bao phủ JaCoCo của cả module biến mất trong im lặng</b> — log CI
 * {@code 35346347718} in {@code 'Skipping JaCoCo execution due to missing execution data file'} rồi
 * build **xanh**. Luật 7 ở dạng thuần khiết nhất: một cổng chưa ai đi qua ⛔ biết đúng sai, và luật
 * 31: <b>sự vắng mặt ⛔ đọc như màu đỏ</b>.
 *
 * <h2>⚠ Và ⛔ phải cứ thế viết bài kiểm cho đủ tỷ lệ</h2>
 *
 * <p>{@code backend/pom.xml} tự dặn ngay tại chỗ đặt ngưỡng: <i>"đặt ngưỡng cho cả dự án thì con số
 * bị pha loãng … và người ta viết test cho những chỗ dễ để kéo tỷ lệ lên thay vì cho những chỗ
 * đáng"</i>. Đo {@code hr/domain}: <b>20 lớp</b>, và phép đếm nhánh cho thấy gần như toàn bộ là
 * entity JPA + enum — getter, setter, hằng số. ⇒ Lớp này chỉ nhắm vào <b>năm</b> chỗ mang luật
 * nghiệp vụ thật, và <b>hai trong số đó là cặp luật 14</b> (con người phải nhớ hai nơi):
 *
 * <ul>
 *   <li>{@link EmploymentStatus#tenCacTrangThaiDaNghi()} — sáu nơi gọi ở {@code QuanSoRepository} ·
 *       {@code DanhBaRepository} · {@code EmployeeRepository} truyền danh sách này vào SQL. Vị từ sai
 *       ⇒ <b>danh bạ thiếu người</b> mà ⛔ ai đếm (T55.2).
 *   <li>{@link LeaveType#khoaHanMuc()} — bốn chuỗi khoá phải TỒN TẠI trong seed {@code settings}.
 *       Gõ sai một ký tự ⇒ hạn mức rơi về mặc định trong im lặng (luật 15 · T48.11).
 * </ul>
 */
class ViTuNghiepVuHrTest {

    @Nested
    @DisplayName("EmploymentStatus — vị từ nuôi SQU của danh bạ và quân số (T55.2)")
    class TrangThaiLamViec {

        @Test
        @DisplayName("⛔⛔ `daNghi()` đúng HAI giá trị — thêm một trạng thái là xoá người khỏi danh bạ")
        void daNghiDungHaiGiaTri() {
            assertThat(Arrays.stream(EmploymentStatus.values())
                            .filter(EmploymentStatus::daNghi)
                            .toList())
                    .as("⛔⛔ Đặc tả đối lập *đã nghỉ* với *còn làm việc*, ⛔ đối lập với thử việc / thai sản / "
                            + "nghỉ ⛔ lương. Kể thêm một giá trị vào đây là một quyết định nhân sự ⛔ AI duyệt, "
                            + "và triệu chứng là một danh bạ THIẾU NGƯỜI mà ⛔ ai đếm (T55.2).")
                    .containsExactly(EmploymentStatus.NGHI_VIEC, EmploymentStatus.NGHI_HUU);
        }

        @Test
        @DisplayName("⚠ `conLamViec()` là phần bù ĐÚNG của `daNghi()` — ⛔ phải một danh sách thứ hai")
        void conLamViecLaPhanBu() {
            for (EmploymentStatus tt : EmploymentStatus.values()) {
                assertThat(tt.conLamViec())
                        .as(
                                "%s: hai vị từ phải chia đôi tập giá trị — chồng lấn hay bỏ sót đều làm "
                                        + "*quân số* và *danh bạ* trả hai câu trả lời khác nhau về cùng một người",
                                tt)
                        .isEqualTo(!tt.daNghi());
            }
        }

        @Test
        @DisplayName("⛔⛔ VẾ CHỐNG TẬP RỖNG — danh sách rỗng làm `NOT IN (…)` nhận TẤT CẢ mọi người")
        void danhSachTenKhongDuocRong() {
            List<String> ten = EmploymentStatus.tenCacTrangThaiDaNghi();

            assertThat(ten)
                    .as("⛔⛔ Sáu nơi gọi nhét danh sách này vào `CAST(status AS string) NOT IN :daNghi`. "
                            + "Rỗng ⇒ vị từ luôn ĐÚNG ⇒ người đã nghỉ việc quay lại danh bạ và quân số, "
                            + "⛔ một dòng lỗi nào. Một bộ canh thiếu vế này xanh trong ĐÚNG tình huống "
                            + "nó sinh ra để bắt (luật 7).")
                    .isNotEmpty();

            assertThat(ten)
                    .as("chuỗi phải là TÊN HẰNG của enum — SQL so với cột `status` lưu đúng chuỗi ấy")
                    .containsExactlyInAnyOrder(EmploymentStatus.NGHI_VIEC.name(), EmploymentStatus.NGHI_HUU.name());
        }
    }

    @Nested
    @DisplayName("LeaveType — khoá hạn mức phải TRỎ VÀO một khoá settings CÓ THẬT (luật 14)")
    class LoaiNghi {

        /** Seed của nhóm `hr.leave.*`; đọc qua classpath nên ⛔ phụ thuộc thư mục làm việc. */
        private static final String SEED = "db/migration/core/V202608131009__core_seed_settings.sql";

        @Test
        @DisplayName("⛔⛔ Bốn khoá hạn mức đều CÓ trong seed — gõ sai một ký tự là rơi về mặc định trong im lặng")
        void khoaHanMucTonTaiTrongSeed() {
            String sql = docSeed();
            List<LeaveType> coHanMuc = Arrays.stream(LeaveType.values())
                    .filter(l -> !l.truVaoSoDuPhepNam())
                    .toList();

            assertThat(coHanMuc)
                    .as("⚠ vế chống tập rỗng: ⛔ loại nào có hạn mức thì vòng lặp dưới ⛔ khẳng định gì")
                    .isNotEmpty();

            for (LeaveType loai : coHanMuc) {
                String khoa = loai.khoaHanMuc();
                assertThat(sql)
                        .as(
                                "⛔⛔ `%s.khoaHanMuc()` trả `%s` — chuỗi ấy ⛔ có trong seed `%s`. "
                                        + "`SettingService.getInt(khoa, fallback)` nuốt lặng khoá thiếu và trả GIÁ TRỊ "
                                        + "DỰ PHÒNG, nên số ngày nghỉ chế độ sai mà màn hình ⛔ báo gì (luật 15).",
                                loai, khoa, SEED)
                        .contains(khoa);
            }
        }

        @Test
        @DisplayName("⚠ `PHEP_NAM` NÉM chứ ⛔ trả một khoá — phép năm là hàm của thâm niên, ⛔ một hằng")
        void phepNamKhongCoKhoaHanMuc() {
            assertThat(LeaveType.PHEP_NAM.truVaoSoDuPhepNam()).isTrue();
            assertThatThrownBy(LeaveType.PHEP_NAM::khoaHanMuc)
                    .as("Trả một khoá nào đó ở đây là mời người sau seed một hằng số cho phép năm, "
                            + "tức khoá chết Điều 113+114 vào bảng `settings` (T68.10).")
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("⚠ ĐÚNG MỘT loại trừ vào số dư phép năm")
        void dungMotLoaiTruSoDu() {
            assertThat(Arrays.stream(LeaveType.values())
                            .filter(LeaveType::truVaoSoDuPhepNam)
                            .toList())
                    .containsExactly(LeaveType.PHEP_NAM);
        }

        private static String docSeed() {
            try (InputStream in = LeaveType.class.getClassLoader().getResourceAsStream(SEED)) {
                assertThat(in)
                        .as(
                                "⚠ vế chống tập rỗng: ⛔ đọc được `%s` thì mọi khẳng định `contains` bên dưới "
                                        + "đỏ vì LÝ DO SAI — kiểm tệp có bị đổi tên ⛔ (§11.19)",
                                SEED)
                        .isNotNull();
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("⛔ đọc được seed " + SEED, e);
            }
        }
    }

    @Nested
    @DisplayName("LeaveState — trạng thái nào CHIẾM số dư phép (quy tắc 13)")
    class TrangThaiDon {

        @Test
        @DisplayName("⛔⛔ `conChiemSoDu()` phải PHỦ `dangChoDuyet()` — hở ra là cấp thừa ngày phép")
        void chiemSoDuPhuChoDuyet() {
            for (LeaveState tt : LeaveState.values()) {
                if (tt.dangChoDuyet()) {
                    assertThat(tt.conChiemSoDu())
                            .as(
                                    "%s: một đơn đang chờ duyệt mà ⛔ chiếm số dư ⇒ người dùng nộp tiếp đơn thứ "
                                            + "hai trên cùng số ngày, và cả hai đều được duyệt",
                                    tt)
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("⛔⛔ Từ chối / huỷ ⛔ được chiếm số dư — chiếm là GIỮ ngày phép của người ta vĩnh viễn")
        void tuChoiVaHuyTraLaiSoDu() {
            assertThat(LeaveState.TU_CHOI.conChiemSoDu()).isFalse();
            assertThat(LeaveState.DA_HUY.conChiemSoDu()).isFalse();
            assertThat(LeaveState.DA_DUYET.conChiemSoDu())
                    .as("vế đối chứng: đơn ĐÃ DUYỆT dĩ nhiên chiếm — ⛔ có vế này thì một bản vá trả "
                            + "`false` cho mọi trạng thái cũng làm hai khẳng định trên xanh (luật 9)")
                    .isTrue();
        }

        @Test
        @DisplayName("⚠ ĐÚNG HAI trạng thái là 'đang chờ duyệt' — quy trình hai cấp sinh ra `CHO_DUYET_2`")
        void dungHaiTrangThaiChoDuyet() {
            assertThat(Arrays.stream(LeaveState.values())
                            .filter(LeaveState::dangChoDuyet)
                            .toList())
                    .containsExactly(LeaveState.CHO_DUYET, LeaveState.CHO_DUYET_2);
        }
    }

    @Nested
    @DisplayName("UyQuyenDuyetPhep.coHieuLuc — bốn điều kiện, và BIÊN là chỗ hay sai (WS-80)")
    class HieuLucUyQuyen {

        private static final LocalDate TU = LocalDate.of(2026, 9, 10);
        private static final LocalDate DEN = LocalDate.of(2026, 9, 20);

        private static UyQuyenDuyetPhep moi() {
            return new UyQuyenDuyetPhep(1L, 10L, 20L, TU, DEN, "Đi công tác");
        }

        @Test
        @DisplayName("⛔⛔ HAI ĐẦU MÚT đều TRONG khoảng — lệch một ngày là người được uỷ quyền mất quyền đúng hôm cần")
        void haiDauMutNamTrongKhoang() {
            assertThat(moi().coHieuLuc(TU))
                    .as("ngày bắt đầu phải CÓ hiệu lực — `isBefore` ⛔ phải `!isAfter`")
                    .isTrue();
            assertThat(moi().coHieuLuc(DEN))
                    .as("ngày kết thúc phải CÓ hiệu lực — người uỷ quyền chọn 'đến hết ngày 20'")
                    .isTrue();
            assertThat(moi().coHieuLuc(TU.minusDays(1))).isFalse();
            assertThat(moi().coHieuLuc(DEN.plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("⛔⛔ THU HỒI cắt hiệu lực NGAY, kể cả giữa khoảng")
        void thuHoiCatHieuLuc() {
            UyQuyenDuyetPhep u = moi();
            LocalDate giua = TU.plusDays(3);
            assertThat(u.coHieuLuc(giua))
                    .as("⚠ tiền đề: trước khi thu hồi nó phải ĐANG có hiệu lực — ⛔ thì khẳng định dưới "
                            + "xanh vì lý do sai (§11.19)")
                    .isTrue();

            u.thuHoi(99L, Instant.parse("2026-09-13T02:00:00Z"));

            assertThat(u.coHieuLuc(giua))
                    .as("⛔⛔ Thu hồi mà vẫn duyệt được là một người ⛔ còn thẩm quyền vẫn ký duyệt đơn nghỉ.")
                    .isFalse();
        }

        @Test
        @DisplayName("⛔ XOÁ MỀM cũng cắt hiệu lực — ⛔ thì một bản ghi đã xoá vẫn cấp quyền")
        void xoaMemCatHieuLuc() {
            UyQuyenDuyetPhep u = moi();
            LocalDate giua = TU.plusDays(3);
            assertThat(u.coHieuLuc(giua)).isTrue();

            u.markDeleted(Instant.parse("2026-09-13T02:00:00Z"));

            assertThat(u.coHieuLuc(giua)).isFalse();
        }
    }

    @Nested
    @DisplayName("LeaveRequest — ba vị từ quyết định số dư phép và người nhận thư")
    class DonNghiPhep {

        private static final LocalDate TU = LocalDate.of(2026, 9, 10);
        private static final LocalDate DEN = LocalDate.of(2026, 9, 14);

        private static LeaveRequest don() {
            return new LeaveRequest(
                    7L, 1L, LeaveType.PHEP_NAM, TU, DEN, new java.math.BigDecimal("3.0"), LeaveState.CHO_DUYET.name());
        }

        @Test
        @DisplayName("⛔⛔ `ownerUserId()` rơi về NGƯỜI NỘP HỘ — ⛔ thì `notify_owner` bắn vào hư không")
        void chuDonRoiVeNguoiNopHo() {
            LeaveRequest tuNop = don();
            tuNop.setRequesterUserId(7L);
            assertThat(tuNop.ownerUserId()).isEqualTo(7L);

            // Chốt C3: nhân viên ⛔ dùng máy tính ⇒ quản lý đơn vị nộp hộ, `requesterUserId` NULL.
            LeaveRequest nopHo = don();
            nopHo.setCreatedForBy(88L);
            assertThat(nopHo.ownerUserId())
                    .as("⛔⛔ Trả `null` ở đây thì bước chuyển `notify_owner = TRUE` gửi cho ⛔ AI, và ⛔ ai "
                            + "biết đơn đã duyệt hay bị từ chối. Ràng buộc `ck_leave_requests_co_nguoi_nhan` "
                            + "ép ít nhất một trong hai cột khác null, nên hàm này ⛔ được trả null.")
                    .isEqualTo(88L);

            LeaveRequest caHai = don();
            caHai.setRequesterUserId(7L);
            caHai.setCreatedForBy(88L);
            assertThat(caHai.ownerUserId())
                    .as("cả hai cùng có ⇒ người NỘP thắng: đơn thuộc về họ")
                    .isEqualTo(7L);
        }

        @Test
        @DisplayName("⛔⛔ `chongKhoang` — năm ca biên, lệch một ngày là cho nghỉ trùng hoặc chặn oan")
        void chongKhoangDungBien() {
            LeaveRequest d = don(); // 10 → 14

            assertThat(d.chongKhoang(DEN, DEN.plusDays(2)))
                    .as("chạm đúng ngày CUỐI của đơn ⇒ CÓ chồng — phải `!isBefore`, ⛔ `isAfter`")
                    .isTrue();
            assertThat(d.chongKhoang(TU.minusDays(2), TU))
                    .as("chạm đúng ngày ĐẦU của đơn ⇒ CÓ chồng")
                    .isTrue();
            assertThat(d.chongKhoang(DEN.plusDays(1), DEN.plusDays(3)))
                    .as("sát ngay SAU ⇒ ⛔ chồng; sai vế này là chặn oan một đơn hợp lệ")
                    .isFalse();
            assertThat(d.chongKhoang(TU.minusDays(3), TU.minusDays(1)))
                    .as("sát ngay TRƯỚC ⇒ ⛔ chồng")
                    .isFalse();
            assertThat(d.chongKhoang(TU.plusDays(1), TU.plusDays(2)))
                    .as("nằm TRỌN bên trong ⇒ CÓ chồng — ca mà một phép so chỉ nhìn hai đầu mút bỏ sót")
                    .isTrue();
        }

        @Test
        @DisplayName("⚠ `daBatDau` — đúng NGÀY ĐẦU đã là bắt đầu, ⛔ huỷ được nữa")
        void daBatDauTinhCaNgayDau() {
            LeaveRequest d = don();
            assertThat(d.daBatDau(TU.minusDays(1))).isFalse();
            assertThat(d.daBatDau(TU))
                    .as("⛔⛔ Hôm nay là ngày đầu kỳ nghỉ thì người ta ĐANG nghỉ — cho huỷ ở đây là trả "
                            + "lại ngày phép đã tiêu")
                    .isTrue();
            assertThat(d.daBatDau(DEN.plusDays(10))).isTrue();
        }

        @Test
        @DisplayName("⛔⛔ `ghiCapMot` ⛔ được ghi đè `decidedBy` — dấu vết cấp 1 là thứ lượt rà đi tìm (T80.3)")
        void capMotVaQuyetDinhCuoiLaHaiCot() {
            LeaveRequest d = don();
            d.ghiCapMot(11L, Instant.parse("2026-09-08T01:00:00Z"));
            d.ghiQuyetDinh(22L, Instant.parse("2026-09-09T01:00:00Z"));

            assertThat(d.getCap1By()).isEqualTo(11L);
            assertThat(d.getDecidedBy())
                    .as("⛔⛔ Dùng chung một cột thì sau khi duyệt xong ⛔ còn dấu vết nào của người cấp 1 — "
                            + "đúng thứ một lượt rà soát phép năm mở lá đơn ra để tìm.")
                    .isEqualTo(22L);

            d.ghiTuCach(55L, true);
            assertThat(d.getUyQuyenId())
                    .as("⚠ KHOÁ NGOẠI, ⛔ phải chuỗi chép tên người giao — đổi tên tài khoản ⛔ được làm "
                            + "lịch sử nói sai")
                    .isEqualTo(55L);
            assertThat(d.isDuyetDuPhong()).isTrue();
        }
    }

    @Nested
    @DisplayName("MaBaoCaoNhanSu — một mã KHÔNG khả dụng phải NÓI RA VÌ SAO (T58.4)")
    class DanhMucBaoCao {

        @Test
        @DisplayName("⛔⛔ `khaDung = false` ⇒ bắt buộc có LÝ DO nguyên văn, ⛔ để lượt nghiệm thu tự đoán")
        void maKhongKhaDungPhaiCoLyDo() {
            List<MaBaoCaoNhanSu> tat = Arrays.stream(MaBaoCaoNhanSu.values())
                    .filter(m -> !m.khaDung())
                    .toList();

            assertThat(tat)
                    .as("⚠ vế chống tập rỗng: hôm nay BCNS-07 đang chờ mẫu 2C-BNV (G6). Tập này rỗng "
                            + "nghĩa là ai đó vừa bật nó lên mà ⛔ có mẫu — hoặc vòng lặp dưới ⛔ canh gì")
                    .isNotEmpty();

            for (MaBaoCaoNhanSu m : tat) {
                assertThat(m.lyDo())
                        .as(
                                "⛔⛔ `%s` tắt mà ⛔ nói vì sao ⇒ lượt nghiệm thu đếm nút rồi tick đủ, và câu hỏi "
                                        + "*'báo cáo này đâu?'* quay lại ở MỌI lượt sau (T58.4 · T59.0).",
                                m.ma())
                        .isNotNull()
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("⚠ VẾ ĐỐI CHỨNG — mã ĐANG khả dụng thì ⛔ mang lý do tắt")
        void maKhaDungKhongMangLyDo() {
            List<MaBaoCaoNhanSu> bat = Arrays.stream(MaBaoCaoNhanSu.values())
                    .filter(MaBaoCaoNhanSu::khaDung)
                    .toList();

            assertThat(bat).as("⚠ vế chống tập rỗng").isNotEmpty();
            for (MaBaoCaoNhanSu m : bat) {
                assertThat(m.lyDo())
                        .as(
                                "%s khả dụng mà vẫn mang câu *'chưa dựng được vì…'* là một dòng chú thích "
                                        + "hết đúng đang chờ tới lượt gây hiểu nhầm",
                                m.ma())
                        .isNull();
            }
        }

        @Test
        @DisplayName("⚠ Mã hiển thị là ĐỊNH DANH — `BCNS-01`, ⛔ phải tên hằng Java")
        void maGiuDungDinhDang() {
            for (MaBaoCaoNhanSu m : MaBaoCaoNhanSu.values()) {
                assertThat(m.ma())
                        .as(
                                "%s: mã này đi vào tên tệp kết xuất, phiếu hỗ trợ và ảnh chụp màn hình — "
                                        + "đổi dạng là làm mọi bản ghi cũ đọc sai nghĩa (T59.1)",
                                m)
                        .matches("BCNS-\\d{2}");
                assertThat(m.ten()).isNotBlank();
            }
        }
    }
}
