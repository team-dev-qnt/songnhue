package com.songnhue.hydro.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Liên kết điểm đo ↔ công trình, n–n <b>có vai trò</b> — chốt A2b.
 *
 * <h2>⛔ Không có khoá ngoại sang {@code constructions}</h2>
 *
 * <p>{@code constructions} thuộc module {@code operations}, {@code stations} thuộc {@code hydro}
 * ({@code conventions.md} §10.4). Một khoá ngoại xuyên module ở tầng CSDL là thứ ArchUnit không nhìn
 * thấy được: ranh giới module trông vẫn sạch trên mã nguồn trong khi hai lược đồ đã dính chặt vào
 * nhau, và ngày muốn tách ra thì phải sửa cả dữ liệu.
 *
 * <p>Đổi lại, <b>tính toàn vẹn phải do tầng dịch vụ giữ</b>: mọi thao tác tạo liên kết đi qua
 * {@code ConstructionLookupPort} (SPI của {@code operations}) để xác nhận công trình có thật và còn
 * sống. Không có bước đó thì {@link #getConstructionId()} chỉ là một con số trỏ vào khoảng không, và
 * nó sẽ trỏ vào khoảng không rất lâu trước khi có ai nhận ra.
 *
 * <p>Giữ <b>cả hai</b> định danh là cố ý: {@code constructionId} để join nhanh trong cùng một CSDL,
 * {@code constructionPublicId} là định danh ổn định dùng ở API và khi đối chiếu giữa hai module —
 * {@code id} chạy số không bao giờ được ra khỏi backend.
 *
 * <h2>⚠ {@link #isPrimary()} và {@code stations.position_role} phải khớp</h2>
 *
 * <p>Bản ghi chính của một điểm đo phải mang đúng vai trò ghi ở {@code stations.position_role}. CSDL
 * ép được vế "mỗi điểm đo tối đa một bản ghi chính" (chỉ mục một phần), nhưng vế "vai trò phải
 * trùng" là ràng buộc liên bảng — ép ở service, và có bài kiểm riêng.
 */
@Entity
@Table(name = "station_constructions")
@Audited(module = "hyd", entityType = "Liên kết điểm đo – công trình")
public class StationConstruction extends BaseEntity {

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    /** ⛔ Không {@code REFERENCES} — xem javadoc lớp. */
    @Column(name = "construction_id", nullable = false)
    private Long constructionId;

    /** Định danh ổn định của công trình, dùng ở API và khi đối chiếu giữa hai module. */
    @Column(name = "construction_public_id", nullable = false)
    private UUID constructionPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private PositionRole role;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    protected StationConstruction() {}

    public StationConstruction(Long stationId, Long constructionId, UUID constructionPublicId, PositionRole role) {
        this.stationId = stationId;
        this.constructionId = constructionId;
        this.constructionPublicId = constructionPublicId;
        this.role = role;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public Long getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(Long constructionId) {
        this.constructionId = constructionId;
    }

    public UUID getConstructionPublicId() {
        return constructionPublicId;
    }

    public void setConstructionPublicId(UUID constructionPublicId) {
        this.constructionPublicId = constructionPublicId;
    }

    public PositionRole getRole() {
        return role;
    }

    public void setRole(PositionRole role) {
        this.role = role;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
