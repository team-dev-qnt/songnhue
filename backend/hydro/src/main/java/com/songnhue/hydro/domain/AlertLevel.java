package com.songnhue.hydro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Mức cảnh báo ngưỡng — <b>T33.1</b>.
 *
 * <h2>⛔ Đây là DANH MỤC CÓ CRUD, ⛔ không phải enum</h2>
 *
 * <p>Quy tắc 16 của {@code CLAUDE.md}: <i>"danh mục do khách vận hành là dữ liệu có CRUD — thêm mã
 * mới không được đòi deploy"</i>. Bộ mức thật là <b>G9-a</b>, Công ty chưa chốt, và ngày họ chốt thì
 * họ có thể đưa 3 mức (BĐ I/II/III theo thông lệ phòng chống lụt bão) hoặc 5. Một enum Java ở đây
 * biến câu <i>"thêm một mức"</i> thành một lượt phát hành.
 *
 * <p>⛔ <b>Bảng cố ý RỖNG</b> khi hệ thống khởi động lần đầu, và migration ⛔ không seed dòng nào.
 * Rỗng là trạng thái hợp lệ: điểm đo chưa có ngưỡng thì mang nhãn <i>"chưa cấu hình ngưỡng"</i> và
 * ⛔ không phát cảnh báo nào ({@code HYD-2003}, T33.6). Seed sẵn một bộ mức cho đẹp là bịa ra những
 * con số mà sau đó không ai phân biệt được với số thật.
 *
 * <h2>{@code colorToken} ⛔ không phải mã hex</h2>
 *
 * <p>Là <b>khoá</b> trong {@code design-tokens} (VD {@code alert-level-1}). Ràng buộc
 * {@code ck_alert_levels_color_token} chặn {@code #RRGGBB} ngay ở tầng CSDL — dự án đang mang nợ
 * T25.23 vì 29 mã màu ghi cứng lọt vào {@code admin-app}, và cách rẻ nhất để không sinh thêm là làm
 * cho giá trị sai <b>không lưu được</b>, thay vì viết một lời dặn.
 *
 * <h2>{@code severityRank} là DUY NHẤT — và đó là một luật nghiệp vụ</h2>
 *
 * <p>Một điểm đo vượt đồng thời BĐ I và BĐ II thì cảnh báo phải mang mức <b>nặng hơn</b>. Hai mức
 * cùng hạng làm câu hỏi ấy không có câu trả lời, và kết quả rơi vào thứ tự DB trả về — đúng hình
 * dạng luật 13 (<i>kết quả phụ thuộc ai bấm F5 sau cùng</i>).
 */
@Entity
@Table(name = "alert_levels")
@Audited(module = "hyd", entityType = "Mức cảnh báo ngưỡng")
public class AlertLevel extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "color_token", nullable = false, length = 60)
    private String colorToken;

    @Column(name = "severity_rank", nullable = false)
    private Integer severityRank;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "description", length = 500)
    private String description;

    protected AlertLevel() {}

    public AlertLevel(String code, String name, String colorToken, int severityRank) {
        this.code = code;
        this.name = name;
        this.colorToken = colorToken;
        this.severityRank = severityRank;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColorToken() {
        return colorToken;
    }

    public void setColorToken(String colorToken) {
        this.colorToken = colorToken;
    }

    public Integer getSeverityRank() {
        return severityRank;
    }

    public void setSeverityRank(Integer severityRank) {
        this.severityRank = severityRank;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
