package com.songnhue.core.spi;

/**
 * Cổng giao tiếp với phân hệ Thuỷ văn (MOD-03) — mở đường cho Phase 2.
 *
 * <p>SPI cố ý mỏng (coding-guide.md §1), chỉ khai đúng phương thức đang có người gọi.
 */
public interface HydroAlertPort {

    /**
     * Kiểm tra xem công trình có cảnh báo ngưỡng thuỷ văn nào đang xảy ra không.
     *
     * @param constructionId ID công trình
     * @return true nếu có cảnh báo chưa xử lý
     */
    boolean hasActiveAlert(Long constructionId);
}
