package com.songnhue.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;
import com.songnhue.core.application.audit.AuditArchiveHandler;
import com.songnhue.core.application.audit.AuditService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.StorageProperties;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.infra.storage.ObjectStorage;
import com.songnhue.core.spi.JobContext;

/**
 * ⛔⛔⛔ <b>209 dòng xoá dữ liệu ⛔ phục hồi được, và ⛔ lượt kiểm nào đi qua</b> — T85.5, trả
 * {@code T68.35}.
 *
 * <h2>Khoảng trống, đo ngày 23/09/2026</h2>
 *
 * <p>{@code grep AuditArchiveHandler} trên toàn bộ {@code backend/*&#47;src/test} trả về <b>đúng 1</b>
 * kết quả, và đó là một dòng <b>javadoc</b> ở {@code HydroMaintenanceSchedulerTest:41}. Lớp ấy là
 * <b>đường DUY NHẤT trong hệ có quyền xoá {@code audit_logs}</b> ({@code ArchiverJdbc}: <i>"đây là
 * đường duy nhất có quyền xoá nhật ký"</i>), nhật ký giữ <b>5 năm</b> theo chốt G7, và nó xoá bằng
 * một vai trò CSDL riêng.
 *
 * <p>⭐ Điều đáng chú ý ⛔ phải khoảng trống mà là <b>hạ tầng đã dựng sẵn từ lâu cho đúng bài kiểm
 * này</b>: {@code IntegrationTestBase:74-77} cấp vai trò {@code songnhue_archiver} kèm chú thích
 * <i>"Cấp đủ ở đây để đường đó cũng được chạy thật, thay vì chỉ tồn tại trên giấy"</i>. Lời hứa ấy
 * chưa ai thực hiện — một <b>ý định</b> ⛔ phải một cổng kiểm.
 *
 * <h2>⛔⛔ Và lượt đo đầu tiên tìm ra một khuyết tật: HAI VỊ TỪ khác nhau</h2>
 *
 * <p>{@code AuditArchiveHandler} <b>chọn</b> dòng bằng {@code occurred_at &lt; cutoff} nhưng
 * <b>xoá</b> bằng {@code seq BETWEEN fromSeq AND toSeq} — một <b>DẢI</b>. Hai vị từ ấy chỉ trùng
 * nhau chừng nào thứ tự {@code occurred_at} còn trùng thứ tự {@code seq}. Bất biến giữ điều đó
 * đúng hôm nay là: {@code AuditLogWriter} ⛔ hề ghi cột {@code occurred_at}, nên nó luôn nhận
 * {@code DEFAULT now()} — <b>một bất biến ⛔ ai viết ra và ⛔ gì ép</b>.
 *
 * <p>Ngày nào có một đường ghi khai {@code occurred_at} tường minh (nhập bù, đồng hồ lệch, một lượt
 * di trú), một dòng <b>chưa quá hạn</b> lọt vào giữa dải seq sẽ bị xoá <b>cùng lô</b>: im lặng,
 * ⛔ phục hồi được, và ⛔ dòng nào trong {@code audit_archive_anchors} nhắc tới nó. Bài
 * {@link #dongChuaQuaHanNamGiuaDaiSeqPhaiSongSot} dựng đúng trạng thái ấy và <b>đỏ trên mã chưa
 * vá</b>; bản vá thêm lại chính vị từ của lượt chọn vào lượt xoá, để hai lượt ⛔ còn có cách rời
 * nhau (cùng khuôn luật 12 — bỏ tham số đi thay vì dặn người ta nhớ).
 *
 * <h2>⚠ Vì sao phải ĐỔI {@code audit.retention-years}, ⛔ để nguyên</h2>
 *
 * <p>Seed ghi {@code '5'} và giá trị dự phòng trong Java cũng là {@code 5}. Một bài chạy ở mốc mặc
 * định <b>⛔ phân biệt được</b> một hệ đọc {@code settings} với một hệ ghi cứng số 5 — đúng bẫy
 * T48.7. Nên {@link #soNamLuuDocTuBangSettings} đặt <b>3</b> và dựng dòng ở mốc <b>4 năm</b>: quá
 * hạn theo 3, <b>chưa</b> quá hạn theo 5.
 */
class KetXuatNhatKyKiemToanTest extends IntegrationTestBase {

    /**
     * ⚠ {@code module} có {@code CHECK} chỉ nhận sáu giá trị ({@code core cms ops hyd hr adm}), nên
     * đồ gá ⛔ tự đặt được một tên riêng — lượt chạy đầu đỏ đúng ở đó. Dấu nhận biết dời sang
     * {@code entity_type}, cột ⛔ có ràng buộc.
     */
    private static final String MODULE = "core";

    /** Dấu nhận biết dòng đồ gá — dùng cho cả lượt dọn. */
    private static final String LOAI = "DoGaT85";

    /** Quá hạn với MỌI mức lưu đang xét (3 hoặc 5 năm). */
    private static final long NGAY_THAT_CU = 6 * 365L;

    /** Quá hạn với mức 3 năm, ⛔ quá hạn với mức 5 — vế phân biệt của {@code audit.retention-years}. */
    private static final long NGAY_BON_NAM = 4 * 365L;

    @Autowired
    private AuditArchiveHandler ketXuat;

    @Autowired
    private ObjectStorage kho;

    @Autowired
    private StorageProperties thamSoKho;

    @Autowired
    private SettingService thamSo;

    @Autowired
    private AuditService nhatKy;

    @Autowired
    private JdbcTemplate appJdbc;

    private String bucketGoc;

    @BeforeEach
    void dung() {
        bucketGoc = thamSoKho.getBucketAudit();
        don();
    }

    @AfterEach
    void donSau() {
        thamSoKho.setBucketAudit(bucketGoc);
        datSoNamLuu("5");
        don();

        // ⚠⚠ Vế tự canh, và nó phải hỏi ĐÚNG câu. Bản đầu hỏi `verifyChain(...).intact()` — SAI, vì
        //   nó kiểm QUÁ SỚM: xoá một khối ở đuôi ⛔ tạo lỗ nào, chuỗi còn lại vẫn liền. Lỗ chỉ hiện
        //   ra ở dòng được chèn TIẾP THEO, tức bởi một lớp kiểm khác, hàng phút sau. Đo được: với
        //   `traLaiDauChuoi` bị gỡ, khẳng định `intact()` vẫn XANH ở cả 6 lượt — đúng ca luật 9.
        // ⇒ Hỏi thẳng bất biến thật: đầu chuỗi phải trỏ vào ĐUÔI CÒN SỐNG. Lệch là dòng kế tiếp sẽ
        //   mang `prev_hash` của một bản ghi ⛔ còn tồn tại, và chuỗi gãy từ đó về sau.
        Map<String, Object> dau =
                ownerJdbc().queryForMap("SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1");
        List<Map<String, Object>> duoiCon =
                ownerJdbc().queryForList("SELECT seq, hash FROM audit_logs ORDER BY seq DESC LIMIT 1");
        if (duoiCon.isEmpty()) {
            // ⭐⭐ ĐÂY là lý do một lượt chạy NHẮM MỤC TIÊU ⛔ bao giờ thấy được khuyết tật này, và nó
            //   đo được chứ ⛔ phải suy: chạy riêng lớp này thì `audit_logs` RỖNG sau lượt dọn, nên
            //   đầu chuỗi lệch ⛔ hại gì — dòng chèn kế tiếp là dòng DUY NHẤT, `lag()` trả NULL và vế
            //   so `prev_hash` ⛔ chạy. Trong lượt `ci-local` đầy đủ thì hàng nghìn dòng của lớp khác
            //   đang nằm đó, và khi ấy dòng kế tiếp so với một bản ghi ⛔ còn tồn tại ⇒ chuỗi gãy ở
            //   một lớp hoàn toàn vô can, hàng phút sau (§11.19 · T48.8).
            return;
        }
        Map<String, Object> duoi = duoiCon.getFirst();
        assertThat(dau.get("last_hash"))
                .as(
                        "⛔⛔ `audit_chain_head` đang trỏ vào seq %s trong khi đuôi còn sống là seq %s. Lớp này "
                                + "là lớp DUY NHẤT của kho xoá dòng `audit_logs`, nên nó cũng là lớp duy nhất có thể "
                                + "để lại trạng thái ấy — và cái giá trả ở MỘT LỚP KHÁC, hàng phút sau (§11.19).",
                        dau.get("last_seq"), duoi.get("seq"))
                .isEqualTo(duoi.get("hash"));
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("⭐⭐ Kết xuất rồi MỚI xoá — tệp lên kho, checksum đối chiếu được, điểm neo có verified_at + purged_at")
    void ketXuatRoiMoiXoa() {
        List<Long> cu = themDongCu(3, NGAY_THAT_CU);
        long moi = themDongMoi();

        chay();

        Map<String, Object> neo = diemNeoDuyNhat();
        assertThat(((Number) neo.get("row_count")).intValue())
                .as("⛔ Điểm neo phải ghi ĐÚNG số dòng đã kết xuất — con số này là thứ duy nhất còn lại "
                        + "để đối chiếu sau khi dòng gốc biến mất.")
                .isEqualTo(3);
        assertThat(neo.get("verified_at"))
                .as("⛔ verified_at rỗng ⇒ chưa đối chiếu mà đã xoá")
                .isNotNull();
        assertThat(neo.get("purged_at")).isNotNull();

        byte[] tep = kho.get((String) neo.get("storage_bucket"), (String) neo.get("storage_key"));
        assertThat(HashUtils.sha256Hex(tep))
                .as("⛔⛔ Vế đắt nhất của cả lớp: tải NGƯỢC về từ kho rồi so checksum. Mạng đứt giữa chừng, "
                        + "đĩa đầy hay ghi đè nhầm object đều cho ra một tệp TỒN TẠI mà ⛔ dùng được — và lúc "
                        + "ấy dòng gốc đã bị xoá.")
                .isEqualTo(neo.get("checksum_sha256"));
        assertThat(tep.length)
                .as("⚠ Vế chống tập rỗng: một tệp 0 byte cũng có checksum khớp với chính nó.")
                .isGreaterThan(20);

        assertThat(demTheoSeq(cu))
                .as("⛔ ba dòng quá hạn phải biến mất khỏi bảng nóng")
                .isZero();
        assertThat(demTheoSeq(List.of(moi)))
                .as("⚠ Vế phân biệt (luật 9): một bản vá xoá SẠCH bảng cũng làm khẳng định trên xanh.")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Dòng CHƯA quá hạn nằm giữa dải seq phải SỐNG SÓT — chọn theo occurred_at mà xoá theo dải seq")
    void dongChuaQuaHanNamGiuaDaiSeqPhaiSongSot() {
        List<Long> truoc = themDongCu(2, NGAY_THAT_CU);
        long kep = themDongMoi();
        List<Long> sau = themDongCu(1, NGAY_THAT_CU);

        assertThat(kep)
                .as("⚠ TIỀN ĐỀ: dòng mới phải nằm GIỮA dải seq, ⛔ thì bài này ⛔ đo được gì.")
                .isGreaterThan(truoc.getLast())
                .isLessThan(sau.getFirst());

        chay();

        assertThat(demTheoSeq(List.of(kep)))
                .as("⛔⛔⛔ Dòng này CHƯA quá hạn lưu — lượt CHỌN của handler (`occurred_at < cutoff`) ⛔ hề "
                        + "lấy nó. Nhưng lượt XOÁ đi bằng `seq BETWEEN from AND to`, một DẢI, nên nó bị cuốn "
                        + "theo: im lặng, ⛔ phục hồi được, và ⛔ dòng nào trong `audit_archive_anchors` nhắc "
                        + "tới nó. Hai lượt phải dùng CÙNG một vị từ.")
                .isEqualTo(1);

        assertThat(demTheoSeq(truoc) + demTheoSeq(sau))
                .as("⚠ Vế phân biệt: một bản vá thôi xoá gì cả cũng làm khẳng định trên xanh.")
                .isZero();
    }

    @Test
    @DisplayName("⛔ Hỏng một bước ⇒ ⛔ xoá dòng nào và ⛔ để lại điểm neo nào")
    void hongMotBuocThiKhongXoaDongNao() {
        // ⚠⚠ VẾ TIỀN ĐỀ, và nó ⛔ phải trang trí. Bản đầu của bài này chỉ khẳng định
        //   `isInstanceOf(Exception.class)` rồi đếm dòng còn lại — và lượt kiểm chứng ngược đo ra nó
        //   XANH trên một handler hỏng HOÀN TOÀN (bản phá `Instant`): hỏng ở câu lệnh ĐẦU cũng ném,
        //   cũng ⛔ xoá dòng nào, cũng ⛔ để lại điểm neo. Một khẳng định ⛔ phân biệt được *hỏng ở
        //   bước tải lên* với *hỏng vì bất cứ lý do gì thì ⛔ khẳng định gì (luật 9). ⇒ Chạy MỘT lượt
        //   THÀNH CÔNG trước, để bài chỉ đọc được khi handler còn sống.
        List<Long> loDau = themDongCu(2, NGAY_THAT_CU);
        chay();
        assertThat(demTheoSeq(loDau))
                .as("⚠ TIỀN ĐỀ: handler phải kết xuất được BÌNH THƯỜNG thì phần dưới mới nói lên điều gì.")
                .isZero();
        assertThat(soDiemNeo()).isEqualTo(1);

        List<Long> loHai = themDongCu(2, NGAY_THAT_CU);
        thamSoKho.setBucketAudit("khong-ton-tai-" + UUID.randomUUID());

        assertThatThrownBy(this::chay)
                .as("⛔ Kho ⛔ nhận được tệp thì lượt này phải NÉM, ⛔ đi tiếp.")
                .isInstanceOf(Exception.class);

        assertThat(demTheoSeq(loHai))
                .as("⛔⛔ Toàn bộ giá trị của lớp ấy nằm ở THỨ TỰ: kết xuất → tải lên → đối chiếu → ghi neo "
                        + "→ MỚI xoá. Bất kỳ bước nào hỏng thì ⛔ dòng nào được xoá.")
                .isEqualTo(2);
        assertThat(soDiemNeo())
                .as("⛔ chưa đối chiếu được thì cũng ⛔ được ghi thêm điểm neo — vẫn đúng 1 của lô đầu.")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ Tắt `audit.archive-enabled` ⇒ ⛔ làm gì — một công tắc ⛔ ai đọc là một lỗi (luật 15)")
    void congTatThiKhongLamGi() {
        List<Long> cu = themDongCu(2, NGAY_THAT_CU);
        datThamSo("audit.archive-enabled", "false");

        chay();

        assertThat(demTheoSeq(cu)).isEqualTo(2);
        assertThat(soDiemNeo()).isZero();

        // Vế phân biệt: bật lại thì CHÍNH những dòng ấy bị kết xuất. ⛔ có vế này thì một handler
        // hỏng hoàn toàn cũng làm khẳng định trên xanh (luật 9).
        datThamSo("audit.archive-enabled", "true");
        chay();
        assertThat(demTheoSeq(cu)).isZero();
    }

    @Test
    @DisplayName(
            "⚠ Số năm lưu đọc TỪ BẢNG `settings` — seed và giá trị dự phòng Java cùng là 5, nên phải đổi mới đo được")
    void soNamLuuDocTuBangSettings() {
        List<Long> bonNam = themDongCu(2, NGAY_BON_NAM);

        chay();
        assertThat(demTheoSeq(bonNam))
                .as("⚠ TIỀN ĐỀ: ở mức 5 năm, dòng 4 năm tuổi CHƯA quá hạn ⇒ phải còn nguyên.")
                .isEqualTo(2);

        datSoNamLuu("3");
        chay();

        assertThat(demTheoSeq(bonNam))
                .as("⛔ Cùng một dữ liệu, chỉ đổi tham số trong `settings` ⇒ kết quả phải đổi. Đây là vế "
                        + "DUY NHẤT phân biệt được 'hệ đọc settings' với 'hệ ghi cứng số 5' — seed và giá trị "
                        + "dự phòng trong Java trùng khít nhau (bẫy T48.7).")
                .isZero();
    }

    @Test
    @DisplayName("⚠ Chuỗi hash vẫn kiểm được sau khi xoá — và ĐO xem thứ gì thật sự giữ nó")
    void chuoiHashChiKiemDuocKhiDongKhungTuDiemNeo() {
        themDongCu(3, NGAY_THAT_CU);
        themDongMoi();

        chay();

        Map<String, Object> neo = diemNeoDuyNhat();
        long toSeq = ((Number) neo.get("to_seq")).longValue();

        assertThat(nhatKy.verifyChain(toSeq + 1, null).intact())
                .as("⭐ Đây là cách dùng mà chính chú thích của `core_verify_audit_chain` kê ra: "
                        + "*p_from_seq cho phép verify từ điểm neo kết xuất trở đi*. Đóng khung như vậy thì "
                        + "mối nối do lượt xoá tạo ra nằm NGOÀI phạm vi, và phần lịch sử còn lại vẫn kiểm được.")
                .isTrue();

        assertThat(nhatKy.verifyChain(null, null).breaks())
                .as(
                        "⛔⛔ Một lượt kết xuất ⛔ được phép làm gãy chuỗi ở chỗ nào khác ngoài chính MỐI NỐI "
                                + "nó tạo ra. Mọi mắt gãy (nếu có) phải nằm SAU seq %d — có mắt nào ở trước nghĩa là "
                                + "lượt xoá đã chạm vào phần lịch sử đáng lẽ ⛔ đụng tới.",
                        toSeq)
                .allMatch(vet -> vet.seq() > toSeq);

        // ⚠⚠ ĐO, ⛔ suy — và phép đo này BÁC một câu trong javadoc của `AuditArchiveHandler`.
        //   Câu ấy nói: *"Điểm neo giữ `last_hash` của lô — NHỜ ĐÓ chuỗi hash vẫn kiểm tra được sau
        //   khi các dòng cũ biến mất"*. Vế nhân quả ấy SAI. Thứ thật sự giữ cho lượt verify xanh là
        //   ngữ nghĩa `lag(hash) OVER (ORDER BY seq)` của hàm SQL: vế so `prev_hash` chỉ chạy khi
        //   `expected_prev_hash IS NOT NULL`, tức dòng ĐẦU của phạm vi ⛔ bao giờ bị so với thứ gì.
        //   Điểm neo ⛔ tham gia: đo toàn kho 23/09, `audit_archive_anchors.last_hash` được GHI đúng
        //   1 nơi (`AuditArchiveHandler`) và ĐỌC **0 nơi**.
        //   ⇒ Hệ quả đo được ngay trong bài này: khi lô kết xuất ⛔ phải một TIỀN TỐ của chuỗi (ở đây
        //   lớp kiểm khác đã chèn dòng trước đó), `verifyChain(null, null)` báo gãy tại mối nối — và
        //   ⛔ có gì trong kho tự đóng khung hộ người vận hành. Dòng nợ: `T85.6`.
        assertThat(neo.get("last_hash"))
                .as("⚠ Điểm neo vẫn phải GHI `last_hash` — nó là dữ liệu DUY NHẤT cho phép chứng minh dòng "
                        + "còn sống đầu tiên nối đúng vào lô đã kết xuất. Hôm nay ⛔ ai đọc nó (`T85.6`), và "
                        + "đó là một dòng nợ có số đo chứ ⛔ phải một lý do để thôi ghi.")
                .isNotNull();
    }

    // ---- Trợ giúp -----------------------------------------------------------

    @Test
    @DisplayName("⛔⛔ T85.6 — xoá lén VƯỢT QUÁ điểm neo: hôm nay vô hình, điểm neo phải bắt được")
    void xoaLenVuotQuaDiemNeoPhaiBiBat() {
        themDongCu(3, NGAY_THAT_CU);
        long n1 = themDongMoi();
        long n2 = themDongMoi();

        chay();

        Map<String, Object> neo = diemNeoDuyNhat();
        long toSeq = ((Number) neo.get("to_seq")).longValue();

        // ── VẾ PHÂN BIỆT, và nó phải đứng TRƯỚC lượt phá. Thiếu nó thì một hàm báo gãy ở MỌI mối
        //    nối cũng qua được bài này, và lượt verify nào cũng đỏ (luật 9).
        assertThat(nhatKy.verifyChain(n1, null).intact())
                .as("⭐ Mối nối còn NGUYÊN — `prev_hash` của dòng còn sống đầu tiên đúng bằng `last_hash` "
                        + "của điểm neo — thì ⛔ được báo gãy. Đây là trạng thái bình thường sau MỌI lượt "
                        + "kết xuất hợp lệ.")
                .isTrue();

        // ── Xoá lén MỘT dòng nằm NGOÀI lô đã kết xuất. Đây là hình dạng tấn công mà điểm neo sinh
        //    ra để bắt, và là hình dạng DUY NHẤT hôm nay ⛔ ai thấy:
        //
        //    `core_verify_audit_chain` so `prev_hash` bằng `lag(hash) OVER (ORDER BY seq)` **trong
        //    phạm vi được hỏi**, mà vế so chỉ chạy khi `expected_prev_hash IS NOT NULL` ⇒ dòng ĐẦU
        //    của phạm vi ⛔ bao giờ bị so với thứ gì. Đóng khung từ điểm neo trở đi — đúng cách dùng
        //    mà chú thích của chính hàm ấy kê ra — thì dòng đầu ấy là chỗ mù.
        //
        //    ⚠ ⛔ sửa `prev_hash` để dựng ca hỏng: nó phá LUÔN hash tự thân (hash = f(payload,
        //    prev_hash)) nên hôm nay đã bắt được, và bài sẽ xanh vì LÝ DO SAI (luật 9 · §11.19).
        ownerJdbc().update("DELETE FROM audit_logs WHERE seq = ?", n1);

        List<com.songnhue.core.application.audit.ChainBreak> vet =
                nhatKy.verifyChain(n2, null).breaks();

        assertThat(vet)
                .as(
                        "⛔⛔ Một dòng đã biến mất khỏi khoảng GIỮA điểm neo (to_seq %d) và dòng còn sống kế "
                                + "tiếp (seq %d), mà lượt verify vẫn báo nguyên vẹn. Đó đúng là chỗ một lượt xoá lén "
                                + "trông y hệt một lượt kết xuất hợp lệ — và `last_hash` của điểm neo là dữ liệu DUY "
                                + "NHẤT phân biệt được hai thứ ấy (T85.6).",
                        toSeq, n2)
                .isNotEmpty();

        assertThat(vet.getFirst().seq())
                .as("Mắt gãy phải chỉ ĐÚNG dòng ở mối nối, ⛔ phải một chỗ nào khác — một bộ canh nói "
                        + "*'có gãy đâu đó'* thì người vận hành ⛔ biết bắt đầu từ đâu (luật 37).")
                .isEqualTo(n2);
    }

    @Test
    @DisplayName("⚠ T85.6 — hệ CHƯA từng kết xuất thì hành vi phải y như cũ")
    void chuaCoDiemNeoNaoThiGiuNguyenHanhVi() {
        // Ca này giữ hợp đồng cũ: thêm một phép so mới ⛔ được làm đỏ một hệ ⛔ có gì để so.
        // ⚠ `don()` ở `@BeforeEach` đã xoá sạch điểm neo, nên tiền đề dưới đây là một PHÉP ĐO chứ
        //   ⛔ phải một giả định.
        assertThat(soDiemNeo()).as("tiền đề: ⛔ điểm neo nào").isZero();

        long moi = themDongMoi();

        assertThat(nhatKy.verifyChain(moi, null).intact())
                .as("⛔ điểm neo nào ⇒ ⛔ có gì để đối chiếu ⇒ phải nguyên vẹn, y như trước bản vá.")
                .isTrue();
        assertThat(nhatKy.verifyChain(null, null).breaks())
                .as("⛔ mắt gãy nào được mang LÝ DO của phép so mới: ⛔ có điểm neo thì phép so ấy ⛔ có "
                        + "dữ liệu để chạy. (Lượt quét toàn bộ vẫn có thể mang mắt gãy CŨ do lớp kiểm khác "
                        + "để lại — bài này ⛔ khẳng định gì về chúng.)")
                .noneMatch(v -> v.reason() != null && v.reason().contains("điểm neo"));
    }

    private void chay() {
        try {
            ketXuat.handle(new JobContext(UUID.randomUUID(), JobTypes.AUDIT_ARCHIVE, "{}", null, i -> {}, s -> {}));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Dựng dòng nhật ký có {@code occurred_at} ở quá khứ.
     *
     * <p>⚠ Phải tạo phân mảnh của tháng ấy trước: ⛔ có thì dòng rơi vào {@code audit_logs_default},
     * mà chú thích của chính migration nói phân mảnh ấy <i>"bình thường phải luôn RỖNG"</i> — dựng
     * đồ gá ở một trạng thái bất thường là đo một hệ khác với hệ đang chạy.
     *
     * <p>{@code seq} và {@code hash} do trigger cấp, nên dòng dựng ở đây nằm trong chuỗi hash y hệt
     * một dòng thật.
     */
    private List<Long> themDongCu(int soDong, long ngayTruoc) {
        Instant moc = Instant.now().minus(ngayTruoc, ChronoUnit.DAYS);
        ownerJdbc()
                .queryForObject(
                        "SELECT core_create_audit_partition(?::date)",
                        Boolean.class,
                        moc.atZone(ZoneOffset.UTC).toLocalDate());

        java.util.List<Long> seqs = new java.util.ArrayList<>();
        for (int i = 0; i < soDong; i++) {
            seqs.add(appJdbc.queryForObject(
                    """
                    INSERT INTO audit_logs (occurred_at, module, entity_type, entity_id, action)
                    VALUES (?, ?, ?, ?, 'UPDATE')
                    RETURNING seq
                    """,
                    Long.class,
                    java.sql.Timestamp.from(moc.plusSeconds(i)),
                    MODULE,
                    LOAI,
                    (long) i));
        }
        return seqs;
    }

    /** Dòng của HÔM NAY — chưa quá hạn theo bất kỳ mức lưu nào bài này dùng. */
    private long themDongMoi() {
        return appJdbc.queryForObject(
                """
                INSERT INTO audit_logs (module, entity_type, entity_id, action)
                VALUES (?, ?, 999, 'CREATE')
                RETURNING seq
                """,
                Long.class,
                MODULE,
                LOAI);
    }

    private long demTheoSeq(List<Long> seqs) {
        if (seqs.isEmpty()) {
            return 0;
        }
        // ⚠ ⛔ dùng `seq = ANY (?)`: PgJDBC ⛔ ánh xạ một `Long[]` của Java thành mảng SQL, và lượt
        //   chạy đầu đỏ đúng ở đó ("column index is out of range"). Dựng chỗ cắm theo số phần tử.
        String choCam = String.join(", ", java.util.Collections.nCopies(seqs.size(), "?"));
        return appJdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE seq IN (" + choCam + ")", Long.class, seqs.toArray());
    }

    private int soDiemNeo() {
        return appJdbc.queryForObject(
                "SELECT count(*) FROM audit_archive_anchors WHERE storage_key LIKE 'audit/%'", Integer.class);
    }

    private Map<String, Object> diemNeoDuyNhat() {
        List<Map<String, Object>> hang = appJdbc.queryForList("SELECT * FROM audit_archive_anchors ORDER BY from_seq");
        assertThat(hang)
                .as("⚠ TIỀN ĐỀ: phải có ĐÚNG một điểm neo thì mọi khẳng định bên dưới mới đọc được.")
                .hasSize(1);
        return hang.getFirst();
    }

    private void datSoNamLuu(String giaTri) {
        datThamSo("audit.retention-years", giaTri);
    }

    private void datThamSo(String khoa, String giaTri) {
        ownerJdbc().update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        thamSo.invalidate(khoa);
    }

    /** Dọn cả hai phía: dòng đồ gá và mọi điểm neo lượt trước để lại, rồi VÁ LẠI đầu chuỗi. */
    private void don() {
        JdbcTemplate owner = ownerJdbc();
        owner.update("DELETE FROM audit_logs WHERE entity_type = ?", LOAI);
        owner.update("DELETE FROM audit_archive_anchors");
        traLaiDauChuoi(owner);
        datThamSo("audit.archive-enabled", "true");
    }

    /**
     * ⛔⛔⛔ Kéo {@code audit_chain_head} về đúng đuôi CÒN SỐNG — <b>vế mà lượt chạy nhắm mục tiêu
     * ⛔ bao giờ thấy</b>.
     *
     * <h3>Vì sao phải có</h3>
     *
     * <p>Trigger chuỗi hash gán {@code prev_hash} của dòng mới bằng {@code last_hash} của đầu chuỗi.
     * Xoá một khối dòng rồi để đầu chuỗi nguyên đó thì dòng tiếp theo trỏ {@code prev_hash} vào một
     * bản ghi <b>⛔ còn tồn tại</b> ⇒ {@code core_verify_audit_chain} báo gãy, <b>vĩnh viễn cho phần
     * còn lại của lượt chạy</b>.
     *
     * <p>Trong sản phẩm điều đó ⛔ xảy ra: lượt kết xuất luôn xoá các dòng CŨ NHẤT, tức một tiền tố
     * của chuỗi, và phép so {@code lag()} miễn cho dòng đầu phạm vi. Nhưng trong một context Spring
     * DÙNG CHUNG, đồ gá của lớp này nằm ở GIỮA chuỗi — nó chèn vào đầu hiện tại rồi lớp khác chèn
     * tiếp sau — nên xoá nó là khoét một lỗ ở giữa.
     *
     * <p>⚠⚠ Và đây đúng là thứ một lượt chạy nhắm mục tiêu <b>⛔ thể phát hiện</b>: chạy riêng lớp
     * này thì khối đồ gá luôn là ĐUÔI, ⛔ có ai chèn sau, nên 6/6 xanh. Lượt `ci-local` đầy đủ mới
     * lộ ra — và nó đỏ ở <b>`AuditChainTest`</b>, một lớp hoàn toàn vô can (§11.19 · T48.8: dọn dẹp
     * thiếu là rò trạng thái, và nó đỏ ở chỗ khác chỗ gây ra).
     *
     * <p>Phép vá <b>ĐO</b> đuôi thật thay vì khôi phục một bản chụp: nó đúng kể cả khi một lượt chạy
     * trước đó chết giữa chừng và để lại dòng thừa.
     */
    private void traLaiDauChuoi(JdbcTemplate owner) {
        owner.update(
                """
                UPDATE audit_chain_head
                   SET last_seq  = COALESCE((SELECT max(seq) FROM audit_logs), 0),
                       last_hash = (SELECT hash FROM audit_logs ORDER BY seq DESC LIMIT 1)
                 WHERE id = 1
                """);
    }

    /** Vai trò {@code songnhue_owner} — chỉ để dựng phân mảnh và dọn dữ liệu đồ gá. */
    private JdbcTemplate ownerJdbc() {
        return new JdbcTemplate((DataSource) new DriverManagerDataSource(
                SongnhuePostgres.instance().getJdbcUrl(), "songnhue_owner", SongnhuePostgres.password()));
    }
}
