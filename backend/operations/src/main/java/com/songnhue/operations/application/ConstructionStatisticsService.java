package com.songnhue.operations.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Thống kê danh mục công trình — CN-02.6 / T17.10.
 *
 * <h2>Đếm ở CSDL, không đếm trong Java</h2>
 *
 * Quy tắc 3 của dự án nói mọi giá trị tính toán tính ở BE. Ở đây "ở BE" còn phải hiểu chặt hơn: đếm
 * bằng {@code GROUP BY} chứ không tải danh sách về rồi lặp. Tải về rồi lặp thì con số vẫn đúng, cho
 * tới ngày danh mục đủ lớn để phân trang — lúc đó nó lặng lẽ chỉ còn đếm một trang.
 *
 * <h2>⚠ Con số ở đây đã bị lọc theo phạm vi đơn vị</h2>
 *
 * Truy vấn chạy trong transaction nên đi qua {@code ScopeFilterAspect}: người của Xí nghiệp A thấy
 * tổng số công trình của Xí nghiệp A, người cấp Công ty thấy tổng toàn hệ thống. Đó là <b>hành vi
 * đúng</b> và cũng là lý do không được đưa những con số này ra cổng công khai mà không nghĩ lại —
 * cùng một endpoint sẽ trả hai kết quả khác nhau cho hai người.
 */
@Service
public class ConstructionStatisticsService {

    private final ConstructionRepository constructions;
    private final OrgUnitPort orgUnits;

    public ConstructionStatisticsService(ConstructionRepository constructions, OrgUnitPort orgUnits) {
        this.constructions = constructions;
        this.orgUnits = orgUnits;
    }

    /**
     * @param key mã (loại / trạng thái / cấp) hoặc tên đơn vị
     * @param count số công trình
     */
    public record Bucket(String key, String label, long count) {}

    /**
     * @param total tổng số công trình trong phạm vi người xem
     * @param withoutLocation số hồ sơ chưa số hoá toạ độ — nhắc việc, không phải số liệu trang trí
     */
    public record Statistics(
            long total,
            long withoutLocation,
            List<Bucket> byType,
            List<Bucket> byStatus,
            List<Bucket> byOrgUnit,
            List<Bucket> byManagementLevel) {}

    @Transactional(readOnly = true)
    public Statistics summary() {
        return new Statistics(
                constructions.countByDeletedAtIsNull(),
                constructions.countByLatitudeIsNullAndDeletedAtIsNull(),
                enumBuckets(constructions.countByType()),
                enumBuckets(constructions.countByStatus()),
                orgUnitBuckets(constructions.countByOrgUnit()),
                enumBuckets(constructions.countByManagementLevel()));
    }

    private static List<Bucket> enumBuckets(List<Object[]> rows) {
        List<Bucket> ket = new ArrayList<>();
        for (Object[] row : rows) {
            String ma = row[0] == null ? "" : row[0].toString();
            ket.add(new Bucket(ma, ma, ((Number) row[1]).longValue()));
        }
        return List.copyOf(ket);
    }

    /**
     * Dịch khoá đơn vị sang tên.
     *
     * <p>Cây đơn vị chỉ vài chục dòng và đã nằm trong bộ nhớ đệm của {@code OrgUnitService}, nên tra
     * từng dòng ở đây không phải bài toán N+1 đáng lo. Đơn vị không tra được thì để nguyên khoá thay
     * vì bỏ dòng: bỏ dòng làm tổng các nhóm nhỏ hơn tổng chung, và người đọc sẽ đi tìm nguyên nhân ở
     * chỗ khác.
     */
    private List<Bucket> orgUnitBuckets(List<Object[]> rows) {
        List<Bucket> ket = new ArrayList<>();
        for (Object[] row : rows) {
            Long id = row[0] == null ? null : ((Number) row[0]).longValue();
            String ten = id == null
                    ? "(chưa gán đơn vị)"
                    : orgUnits.findRefById(id).map(ref -> ref.name()).orElse("#" + id);
            ket.add(new Bucket(String.valueOf(id), ten, ((Number) row[1]).longValue()));
        }
        return List.copyOf(ket);
    }
}
