package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lớp bản đồ GIS — CN-02.4 / M2.9.
 *
 * <p>⛔ Hai lớp dưới đây <b>có logic thật</b>, ⛔ không phải bao đựng dữ liệu: một phép suy loại
 * hình học sai làm cả lớp bản đồ vẽ bằng đúng kiểu sai (vùng thành chấm), và ⛔ không dòng lỗi nào.
 */
class GisLayerDomainTest {

    @Test
    @DisplayName("⭐ Một loại hình học duy nhất ⇒ đúng loại ấy; nhiều loại ⇒ HON_HOP")
    void suyLoaiTuTapKieu() {
        assertThat(GisGeometryType.tuTapKieu(Set.of("Point"))).isEqualTo(GisGeometryType.POINT);
        assertThat(GisGeometryType.tuTapKieu(Set.of("MultiPoint"))).isEqualTo(GisGeometryType.POINT);
        assertThat(GisGeometryType.tuTapKieu(Set.of("LineString"))).isEqualTo(GisGeometryType.LINE);
        assertThat(GisGeometryType.tuTapKieu(Set.of("MultiLineString"))).isEqualTo(GisGeometryType.LINE);
        assertThat(GisGeometryType.tuTapKieu(Set.of("Polygon"))).isEqualTo(GisGeometryType.POLYGON);
        assertThat(GisGeometryType.tuTapKieu(Set.of("MultiPolygon"))).isEqualTo(GisGeometryType.POLYGON);

        Set<String> tron = new LinkedHashSet<>(Set.of("LineString", "Polygon"));
        assertThat(GisGeometryType.tuTapKieu(tron)).isEqualTo(GisGeometryType.HON_HOP);
    }

    @Test
    @DisplayName("⭐ `Point` và `MultiPoint` cùng cho POINT — ⛔ KHÔNG phải HON_HOP")
    void pointVaMultiPointLaMotLoai() {
        // ⛔⛔ Gộp theo TÊN KIỂU thô thì tệp có cả `Point` lẫn `MultiPoint` ra `HON_HOP`, và giao
        //    diện vẽ nó bằng đường thay vì chấm. Phép gộp phải ở mức Ý NGHĨA, ⛔ không ở mức chuỗi.
        Set<String> ca = new LinkedHashSet<>(Set.of("Point", "MultiPoint"));
        assertThat(GisGeometryType.tuTapKieu(ca)).isEqualTo(GisGeometryType.POINT);
    }

    @Test
    @DisplayName("⛔ Tập RỖNG ⇒ ném — một tệp ⛔ không có hình học là một lớp vẽ ra bản đồ trống")
    void tapRongThiNem() {
        assertThatThrownBy(() -> GisGeometryType.tuTapKieu(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("⛔ không có đối tượng");
        assertThatThrownBy(() -> GisGeometryType.tuTapKieu(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⛔ Kiểu lạ ⇒ HON_HOP, ⛔ không ném — một GeometryCollection vẫn vẽ được")
    void kieuLaThiHonHop() {
        assertThat(GisGeometryType.tuTapKieu(Set.of("GeometryCollection"))).isEqualTo(GisGeometryType.HON_HOP);
    }

    @Test
    @DisplayName("⭐ `ganTep` đặt CẢ BA giá trị cùng lúc — hai cột tệp đi thành CẶP")
    void ganTepDatCaBa() {
        // ⛔ `ck_gis_layers_tep_va_so_doi_tuong` từ chối một bản ghi có tệp mà ⛔ không có số đối
        //   tượng. Hai setter riêng thì sẽ có lượt gọi đặt cột này mà quên cột kia, và CSDL từ chối
        //   ĐÚNG LÚC người dùng vừa chờ một lượt tải 20 MB xong.
        GisLayer layer = new GisLayer("Kênh mương", GisGeometryType.HON_HOP);
        assertThat(layer.getAttachmentPublicId()).isNull();
        assertThat(layer.getFeatureCount()).isNull();

        java.util.UUID tep = java.util.UUID.randomUUID();
        layer.ganTep(tep, 12, GisGeometryType.LINE);

        assertThat(layer.getAttachmentPublicId()).isEqualTo(tep);
        assertThat(layer.getFeatureCount()).isEqualTo(12);
        assertThat(layer.getGeometryType()).isEqualTo(GisGeometryType.LINE);
    }

    @Test
    @DisplayName("⭐ Giá trị mặc định khớp cột CSDL — độ mờ là PHẦN TRĂM NGUYÊN")
    void macDinhKhopCotCsdl() {
        GisLayer layer = new GisLayer("Ranh giới", GisGeometryType.POLYGON);
        // ⛔ 70 chứ ⛔ không 0.7: đặc tả nói "opacity 0–100%", và một phép đổi đơn vị giữa Java và
        //   CSDL là chỗ để `0.8` và `80` lẫn vào nhau.
        assertThat(layer.getOpacity()).isEqualTo((short) 70);
        assertThat(layer.isActive()).isTrue();
        assertThat(layer.getSortOrder()).isZero();
        assertThat(layer.getColor()).matches("^#[0-9a-fA-F]{6}$");
    }
}
