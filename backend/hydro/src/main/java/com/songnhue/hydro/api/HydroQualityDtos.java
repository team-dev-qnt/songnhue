package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.songnhue.hydro.application.HydroReviewService;
import com.songnhue.hydro.domain.HydroReading;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SoDoNghiNgo;

/** Hợp đồng dây của màn hình <i>Dữ liệu nghi ngờ</i> và ô nhập tay — WS-32. */
public final class HydroQualityDtos {

    private HydroQualityDtos() {}

    /**
     * Một dòng hàng chờ duyệt.
     *
     * <p>⭐⭐ {@code @JsonFormat(shape = STRING)} cho {@code giaTri} — ⛔ <b>không</b> để nó ra dây
     * dưới dạng số. Đây là lỗi đã trả giá <b>hai lần</b> (T28.27 ở cổng công khai, rồi V2 ở đường
     * quản trị): {@code BigDecimal("2.300")} tuần tự hoá thành số JSON là {@code 2.3}, và
     * {@code 2.30} với {@code 2.3} là hai chuỗi khác nhau trên màn hình dù bằng nhau về giá trị.
     * Với mực nước thì chữ số thập phân thứ ba là <b>milimét</b> — thứ mà toàn bộ ngưỡng cảnh báo
     * treo lên.
     *
     * <p>⛔ ⛔ <b>Không có trường {@code id}.</b> Khoá tự tăng của {@code hydro_readings} ⛔ không ra
     * tới dây: địa chỉ của một bản ghi là bộ ba {@code (diemDoId, loaiChiSoCode, mocDo)} — cùng bộ
     * khoá mà {@link ReviewRequest} và {@link ManualEntryRequest} dùng. Giao diện lấy đúng bộ ba ấy
     * làm {@code rowKey}. Để lộ khoá tự tăng ra JSON là mời người sau dựng một endpoint
     * {@code /{id}} mà {@code ApiSurfaceRuleTest} cấm.
     */
    public record SuspectRow(
            Instant mocDo,
            UUID diemDoId,
            String diemDoCode,
            String diemDoName,
            String loaiChiSoCode,
            String loaiChiSoName,
            String donVi,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal giaTri,
            ReadingQuality trangThai,
            String lyDoMay,
            String lyDoNguoi,
            ReadingSource nguon,
            Instant mocGhi,
            Long rawLogId) {

        public static SuspectRow cua(SoDoNghiNgo d) {
            return new SuspectRow(
                    d.mocDo(),
                    d.diemDoPublicId(),
                    d.diemDoCode(),
                    d.diemDoName(),
                    d.loaiChiSoCode(),
                    d.loaiChiSoName(),
                    d.donVi(),
                    d.giaTri(),
                    d.trangThai(),
                    d.lyDoMay(),
                    d.lyDoNguoi(),
                    d.nguon(),
                    d.mocGhi(),
                    d.rawLogId());
        }
    }

    /**
     * ⚠ Câu trả lời cho <i>"bảng rỗng nghĩa là gì"</i> — quy tắc 16.
     *
     * <p>Ba trạng thái <b>phân biệt được</b>, và cả ba đều cho ra một bảng rỗng: bộ phân loại đang
     * chạy mà không có gì đáng ngờ ({@code dangKiem = true}) · chưa ai cấu hình quy tắc
     * ({@code dangKiem = false}, {@code loiCauHinh = null}) · cấu hình có mà <b>hỏng</b>
     * ({@code loiCauHinh != null}). ⛔ Giao diện phải nói ra cái nào — một bảng rỗng hiện như "không
     * có gì đáng ngờ" trong khi bộ phân loại đang tắt là một câu khẳng định sai.
     */
    public record QualityRuleStatus(boolean dangKiem, String loiCauHinh) {

        public static QualityRuleStatus cua(HydroReviewService.TinhTrangQuyTac t) {
            return new QualityRuleStatus(t.dangKiem(), t.loiCauHinh());
        }
    }

    /**
     * Kết quả một bước chuyển — giao diện cập nhật dòng tại chỗ, ⛔ không tải lại cả bảng.
     *
     * <p>⛔ Trả về {@code mocDo} chứ ⛔ không {@code id}, cùng lý do với {@link SuspectRow}.
     */
    public record ReviewResult(Instant mocDo, ReadingQuality trangThai, String lyDoNguoi) {

        public static ReviewResult cua(HydroReading r) {
            return new ReviewResult(r.getMeasuredAt(), r.getQuality(), r.getReviewNote());
        }
    }

    /**
     * Yêu cầu thực hiện một bước chuyển.
     *
     * <p>⭐ Ba trường đầu là <b>địa chỉ</b> của bản ghi — khoá tự nhiên, ⛔ không phải khoá tự tăng
     * trên URL. Cùng bộ khoá với {@link ManualEntryRequest}: một địa chỉ dùng chung cho cả hai đường
     * ghi là một chỗ để nhớ thay vì hai (luật 14).
     *
     * <p>⚠ {@code reason} ⛔ không {@code @NotBlank}: bước {@code DUYET} cố ý không đòi lý do. Luật
     * "bước nào cần lý do" sống ở <b>một chỗ</b> — {@code workflow_transitions.requires_reason} — và
     * {@code WorkflowEngine} là nơi ép. Thêm một {@code @NotBlank} ở đây là dựng bản sao thứ hai của
     * cùng một luật, rồi một hôm hai bản nói khác nhau (luật 14).
     */
    public record ReviewRequest(
            @NotNull UUID diemDoId,
            @NotBlank @Size(max = 30) String maLoaiChiSo,
            @NotNull Instant mocDo,
            @NotBlank @Size(max = 50) String action,
            @Size(max = 500) String reason) {}

    /**
     * Ô nhập tay — T32.7.
     *
     * <p>⚠⚠ {@code giaTri} nhận <b>chuỗi</b> ({@code BigDecimal} + {@code @JsonFormat STRING} ở
     * chiều ra, và Jackson đọc được chuỗi ở chiều vào). ⛔ Không dùng {@code double} ở bất kỳ khâu
     * nào — quy tắc 2, và với mực nước thì sai số nhị phân rơi đúng vào chữ số milimét.
     *
     * <p>⚠ {@code @Digits(integer = 9, fraction = 3)} khớp {@code NUMERIC(12,3)} của cột. Thiếu nó
     * thì một giá trị tràn cột cho ra <b>500</b> thay vì một dòng đỏ dưới ô — đúng cột "thiếu ràng
     * buộc phía client" mà lượt rà biểu mẫu đã đếm ra.
     *
     * <p>⛔ Khoảng vật lý ⛔ <b>không</b> khai ở đây: nó là <b>tham số cấu hình</b> theo loại chỉ số
     * ({@code hydro.quality.suspect-rule}), và ghi cứng một cận vào annotation là dựng bản sao thứ
     * hai của một giá trị người vận hành sửa được. Chốt chặn ấy nằm ở service và trả {@code HYD-2001}.
     */
    public record ManualEntryRequest(
            @NotNull UUID diemDoId,
            @NotBlank @Size(max = 30) String maLoaiChiSo,
            @NotNull Instant mocDo,
            @NotNull
                    @Digits(integer = 9, fraction = 3)
                    @DecimalMin(value = "-999999999")
                    @DecimalMax(value = "999999999")
                    BigDecimal giaTri,
            @Size(max = 500) String ghiChu) {}
}
