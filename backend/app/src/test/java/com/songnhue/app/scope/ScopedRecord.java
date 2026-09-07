package com.songnhue.app.scope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Entity thuộc phạm vi đơn vị, <b>chỉ dùng cho test</b> — T10.3.
 *
 * <p>Đóng vai "entity nghiệp vụ đầu tiên" của Phase 1 để tầng 3 phân quyền được kiểm chứng
 * đầu-cuối ngay bây giờ, thay vì chờ tới lúc có hồ sơ công trình thật rồi mới biết cơ chế có chạy
 * hay không. Khai báo ở đây <b>giống hệt</b> những gì một entity thật phải khai — đúng một dòng
 * {@code @Filter} dùng lại hai hằng số của {@link ScopedEntity}.
 *
 * <p>Bảng tương ứng nằm ở {@code app/src/test/resources/db/migration/test} và chỉ được nạp khi test
 * tự thêm thư mục đó vào {@code spring.flyway.locations}.
 */
@Entity
@Table(name = "test_scoped_records")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
public class ScopedRecord extends ScopedEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    protected ScopedRecord() {}

    public ScopedRecord(String title, Long orgUnitId) {
        this.title = title;
        setOrgUnitId(orgUnitId);
    }

    public String getTitle() {
        return title;
    }
}
