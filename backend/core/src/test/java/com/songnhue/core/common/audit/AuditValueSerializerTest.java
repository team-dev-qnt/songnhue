package com.songnhue.core.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AuditValueSerializerTest {

    private final AuditValueSerializer serializer = new AuditValueSerializer(new ObjectMapper());

    @Test
    @DisplayName("Ghi giá trị vô hướng dưới dạng JSON")
    void writesScalars() {
        String json = serializer.toJson(
                new String[] {"name", "count", "active"}, new Object[] {"Xí nghiệp A", 3, true}, Set.of());

        assertThat(json)
                .contains("\"name\":\"Xí nghiệp A\"")
                .contains("\"count\":3")
                .contains("\"active\":true");
    }

    @Test
    @DisplayName("⚠ Trường nhạy cảm chỉ hiện tên, giá trị bị che")
    void redactsExcludedFields() {
        // Nhật ký lưu 5 năm và nhiều người xem được hơn bảng gốc — hash mật khẩu không được lọt vào.
        // Nhưng vẫn phải thấy là trường đó CÓ đổi, nếu không thì "ai đó vừa đổi mật khẩu của người
        // khác" trở thành thao tác không để lại dấu vết.
        String json = serializer.toJson(
                new String[] {"username", "passwordHash"},
                new Object[] {"superadmin", "$2a$12$abcdefghijklmnopqrstuv"},
                Set.of("passwordHash"));

        assertThat(json).contains("passwordHash").contains(AuditValueSerializer.REDACTED);
        assertThat(json).doesNotContain("$2a$12$");
    }

    @Test
    @DisplayName("Bỏ qua quan hệ tới entity khác — không đi theo đồ thị đối tượng")
    void skipsNonScalarValues() {
        // Chạm vào quan hệ lazy giữa lúc Hibernate đang flush sẽ sinh truy vấn giữa chừng, và với
        // quan hệ hai chiều là đệ quy không đáy.
        Object relation = new Object();
        String json = serializer.toJson(new String[] {"code", "parent"}, new Object[] {"XN-A", relation}, Set.of());

        assertThat(json).contains("code").doesNotContain("parent");
    }

    @Test
    @DisplayName("Thời điểm, UUID và enum chuỗi hoá ổn định để 5 năm sau vẫn đọc được")
    void normalizesTemporalAndUuid() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Instant when = Instant.parse("2026-08-14T03:15:00Z");

        String json = serializer.toJson(
                new String[] {"publicId", "createdAt", "status"}, new Object[] {id, when, Thread.State.NEW}, Set.of());

        assertThat(json)
                .contains(id.toString())
                .contains("2026-08-14T03:15:00Z")
                .contains("NEW");
    }

    @Test
    @DisplayName("Không trường nào ghi được thì trả null, không phải JSON rỗng")
    void returnsNullWhenNothingToWrite() {
        assertThat(serializer.toJson(new String[] {"parent"}, new Object[] {new Object()}, Set.of()))
                .isNull();
        assertThat(serializer.toJson(null, null, Set.of())).isNull();
    }

    @Test
    @DisplayName("Giá trị null vẫn được ghi — 'xoá trắng một trường' là thay đổi cần thấy")
    void keepsNulls() {
        String json = serializer.toJson(new String[] {"email"}, new Object[] {null}, Set.of());
        assertThat(json).isEqualTo("{\"email\":null}");
    }
}
