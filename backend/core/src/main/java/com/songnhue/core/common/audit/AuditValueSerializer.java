package com.songnhue.core.common.audit;

import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Biến giá trị trường của entity thành JSON để lưu vào {@code audit_logs.old_value/new_value}.
 *
 * <p><b>Chỉ ghi giá trị vô hướng.</b> Quan hệ tới entity khác bị bỏ qua thay vì đi theo: chạm vào
 * một quan hệ lazy trong lúc Hibernate đang flush sẽ kích hoạt truy vấn giữa chừng, và trong trường
 * hợp xấu là đệ quy không đáy qua quan hệ hai chiều. Nhật ký cần biết "trường nào đổi từ gì sang
 * gì", không cần ảnh chụp cả đồ thị đối tượng.
 *
 * <p>Trường nằm trong {@code excludeFields} vẫn được ghi tên (để biết <i>có</i> thay đổi) nhưng giá
 * trị thay bằng {@value #REDACTED} — xem {@link Audited#excludeFields()}.
 */
@Component
public class AuditValueSerializer {

    private static final Logger log = LoggerFactory.getLogger(AuditValueSerializer.class);

    public static final String REDACTED = "***";

    private final ObjectMapper objectMapper;

    public AuditValueSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param names tên các trường
     * @param values giá trị tương ứng
     * @param excluded tên trường phải che giá trị
     * @return chuỗi JSON, hoặc {@code null} nếu không có trường nào ghi được
     */
    public String toJson(String[] names, Object[] values, Set<String> excluded) {
        if (names == null || values == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < names.length && i < values.length; i++) {
            String name = names[i];
            if (excluded.contains(name)) {
                map.put(name, REDACTED);
            } else if (isScalar(values[i])) {
                map.put(name, normalize(values[i]));
            }
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // Không ném: hỏng ở khâu biểu diễn giá trị không đáng để làm hỏng cả thao tác nghiệp vụ,
            // và dòng nhật ký vẫn còn (ai, lúc nào, sửa đối tượng nào) — chỉ thiếu chi tiết trường.
            log.warn("Không tuần tự hoá được giá trị cho nhật ký kiểm toán", e);
            return null;
        }
    }

    private static boolean isScalar(Object value) {
        return value == null
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Temporal
                || value instanceof UUID
                || value instanceof Enum<?>;
    }

    private static Object normalize(Object value) {
        // Jackson không tuần tự hoá thẳng Temporal/UUID/enum theo cách ổn định giữa các phiên bản
        // cấu hình khác nhau. Chuỗi hoá ở đây để nhật ký 5 năm tới vẫn đọc được như lúc ghi.
        if (value instanceof Temporal || value instanceof UUID || value instanceof Enum<?>) {
            return value.toString();
        }
        return value;
    }
}
