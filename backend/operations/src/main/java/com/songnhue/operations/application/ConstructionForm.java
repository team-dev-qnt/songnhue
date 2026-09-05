package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.ManagementLevel;

/**
 * Dữ liệu nhập của một hồ sơ công trình — CN-02.1.
 *
 * <h2>⛔ Không có trường trạng thái, và đó là điều quan trọng nhất của record này</h2>
 *
 * {@code operationalStatus} là giá trị dẫn xuất (quy tắc 4). Không đưa nó vào form nghĩa là <b>không
 * có đường nào để client gửi lên</b> — chặt hơn hẳn việc nhận rồi bỏ qua, vì "nhận rồi bỏ qua" là
 * loại bảo đảm sống bằng trí nhớ của người viết hàm gán. Controller vẫn kiểm tra và trả
 * {@code OPS-3001} nếu payload có khoá đó, để người tích hợp biết mình đang gửi thừa chứ không im
 * lặng nuốt.
 *
 * <p>{@code lifecycleState} cũng không nằm ở đây: đổi vòng đời là một quyết định có hệ quả (ngừng
 * nhận bản ghi bảo trì, đổi màu trên bản đồ, gửi thông báo), nên nó đi bằng endpoint riêng có nêu lý
 * do — không lẫn vào một lượt sửa địa chỉ.
 *
 * <p>Ba khối thông số kỹ thuật để {@code null} với loại công trình không dùng tới. Gửi khối sai loại
 * → {@code OPS-2009}: chấp nhận lặng lẽ thì CSDL sẽ có những cái cống mang số máy bơm, và không ai
 * biết con số đó từ đâu ra.
 */
public record ConstructionForm(
        String code,
        String name,
        ConstructionType constructionType,
        ConstructionPurpose purpose,
        UUID orgUnitPublicId,
        ManagementLevel managementLevel,
        UUID clusterPublicId,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String riverName,
        String chainage,
        /* Lưu vực / khu tưới tiêu — trường văn bản, chốt F3. */
        String basinNote,
        Short builtYear,
        Short commissionedYear,
        String designer,
        String contractor,
        /* Đơn vị VND — điểm nghiệp vụ 18. */
        BigDecimal totalInvestment,
        /*
          ⚠⚠ HAI cột này thêm ngày 27/08/2026 (`V202608271035`) rồi bị bỏ quên đúng một ngày:
          `PublicConstructionCatalogService` ĐỌC chúng để dựng cột "Quy trình vận hành" và
          "Phương án bảo vệ" của bảng 7 cột (CR-28), nhưng `SaveRequest` không mang chúng và
          biểu mẫu quản trị không có ô nào — hai setter chỉ có đúng một lời gọi trong toàn kho,
          và lời gọi ấy nằm trong một **bài kiểm**.

          Tức là hai cột đọc-được-mà-không-ghi-được. §10 của văn bản nghiệm thu có một dòng
          riêng cho hậu quả: *"các link Quyết định và Google Map hoạt động"* — hai liên kết ấy
          sẽ không bao giờ có gì để trỏ tới. Quy tắc 15, chiều ghi (xem thêm `OrgUnitDtos`).

          Kiểu là `UUID` của một `attachments.public_id` đã tải lên qua màn hình đính kèm —
          không phải một đường dẫn tự do: một URL gõ tay thì không ai bảo đảm tệp còn tồn tại,
          còn khoá ngoại `ON DELETE SET NULL` thì cột tự rỗng khi tệp bị gỡ.
        */
        UUID operatingProcedureAttachmentId,
        UUID protectionPlanAttachmentId,
        String description,
        PumpSpec pump,
        SluiceSpec sluice,
        LinearSpec linear) {

    /** Thông số trạm bơm. Tổng lưu lượng KHÔNG có ở đây — CSDL tự tính từ số máy × lưu lượng/máy. */
    public record PumpSpec(
            BigDecimal totalPowerKw,
            Short pumpCount,
            Short standbyPumpCount,
            BigDecimal flowPerPumpM3s,
            BigDecimal headM,
            String powerSource,
            BigDecimal voltageKv,
            BigDecimal operatingLevelMinM,
            BigDecimal operatingLevelMaxM) {}

    /** Thông số cống điều tiết. */
    public record SluiceSpec(
            String sluiceType,
            Short bayCount,
            BigDecimal bayWidthM,
            BigDecimal sillElevationM,
            BigDecimal crestElevationM,
            BigDecimal designFlowM3s,
            String gateOperation,
            BigDecimal upstreamWarningLevelM,
            BigDecimal upstreamDangerLevelM) {}

    /** Thông số công trình tuyến — kênh mương và đê điều dùng chung (A3 đợt 1). */
    public record LinearSpec(
            BigDecimal lengthKm,
            String startChainage,
            String endChainage,
            BigDecimal designFlowM3s,
            BigDecimal crestElevationM,
            String technicalGrade,
            String crossSection,
            String specNote) {}
}
