package com.songnhue.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Một dòng nhập tình hình vận hành — CN-02.11.
 *
 * <h2>⛔ Khoá công trình là {@code public_id}, không phải khoá nội bộ</h2>
 *
 * <p>Trường này từng là {@code Long constructionId}, nhận thẳng khoá tự tăng từ payload rồi tra bằng
 * {@code findById}. Ba thứ hỏng cùng lúc ở đó, và không thứ nào có triệu chứng:
 *
 * <ul>
 *   <li>Khoá tự tăng <b>đoán được</b> — gõ 1, 2, 3 là quét hết bảng công trình.
 *   <li>{@code findById} <b>không đi qua bộ lọc phạm vi</b> của Hibernate: {@code @Filter} áp cho
 *       truy vấn và collection, <i>không</i> áp cho lượt tra thẳng theo khoá chính. Nên tầng 3 của
 *       phân quyền vắng mặt hoàn toàn trên đường ghi này.
 *   <li>Bản ghi sinh ra <b>chép {@code org_unit_id} của nạn nhân</b>, nên nó nằm gọn trong phạm vi
 *       của họ và lật luôn trạng thái dẫn xuất công trình của họ. Người bị ảnh hưởng không có cách
 *       nào thấy được dòng đó đến từ đâu.
 * </ul>
 *
 * <p>{@code conventions.md} §4.2 đã cấm {@code findById} trần cho request người dùng từ WS-5. Đây là
 * đường ghi duy nhất trong toàn bộ MOD-02 lách được luật đó.
 */
public class OperationStatusBatchItemRequest {

    @NotNull
    private UUID constructionPublicId;

    @NotBlank
    private String operationCode;

    private BigDecimal parameterValue;

    private String note;

    @NotNull
    private OffsetDateTime effectiveAt;

    public UUID getConstructionPublicId() {
        return constructionPublicId;
    }

    public void setConstructionPublicId(UUID constructionPublicId) {
        this.constructionPublicId = constructionPublicId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public BigDecimal getParameterValue() {
        return parameterValue;
    }

    public void setParameterValue(BigDecimal parameterValue) {
        this.parameterValue = parameterValue;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}
