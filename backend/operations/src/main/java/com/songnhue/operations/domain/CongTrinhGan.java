package com.songnhue.operations.domain;

import java.util.UUID;

/**
 * Công trình gắn được vào một vị trí của mẫu Báo cáo nhanh — đọc TOÀN Công ty, ⛔ lọc phạm vi.
 *
 * <p>Ở {@code domain} chứ ⛔ ở {@code infra}: tầng {@code api} dựng view từ nó, và {@code LayeringTest}
 * cấm {@code api} chạm {@code infra}.
 *
 * @param loai {@code constructions.construction_type} — {@code CONG} | {@code TRAM_BOM} | …
 */
public record CongTrinhGan(Long id, UUID publicId, String ma, String ten, String loai) {}
