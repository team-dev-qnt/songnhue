package com.songnhue.core.common.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⭐ Tự kiểm hai cơ chế canh của bộ nhập dùng chung — {@code conventions.md} §1.5.
 *
 * <p>{@link CotMau} và {@link BieuMauCsv} <b>đều ném</b> ở những trạng thái nhất định, và luật 1 của
 * dự án nói: mọi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm. Kho này đã có
 * <b>5 cơ chế xanh mà chưa từng chạy</b>, nên một {@code throw} ⛔ không có bài kiểm là một lời hứa,
 * ⛔ không phải một cổng kiểm.
 */
class BieuMauCsvTest {

    private static final List<CotMau> MAU =
            List.of(new CotMau("ma", true, "BẮT BUỘC · mã duy nhất"), new CotMau("ten", false, "Bỏ trống được"));

    @Test
    @DisplayName("⭐ Tệp mẫu = tiêu đề + đúng MỘT dòng mô tả, kèm BOM UTF-8")
    void templateIsHeaderPlusOneDescriptionRow() {
        String van = new String(BieuMauCsv.dung(MAU), StandardCharsets.UTF_8);

        assertThat(van.charAt(0))
                .as("⛔ Thiếu BOM ⇒ Excel bản Windows mở ra tiếng Việt vỡ dấu, và người dùng sẽ "
                        + "'sửa lỗi phông' bằng cách lưu lại ở một bảng mã khác")
                .isEqualTo('﻿');

        String[] dong = van.split("\r\n|\n");
        assertThat(dong).as("tiêu đề + đúng một dòng mô tả").hasSize(2);
        assertThat(dong[0]).contains("ma").contains("ten");
        assertThat(dong[1])
                .as("⭐ Dòng 2 là MÔ TẢ, ⛔ không phải một bản ghi hợp lệ — mẫu gồm dòng ví dụ hợp lệ "
                        + "sẽ im lặng tạo ra đúng bản ghi ví dụ ấy khi ai đó tải về rồi nhập lại")
                .contains("BẮT BUỘC")
                .contains("Bỏ trống được");
    }

    @Test
    @DisplayName("⭐⭐ Cột thiếu MÔ TẢ bị TỪ CHỐI — dòng mô tả LÀ tài liệu của tệp mẫu")
    void aColumnWithoutADescriptionIsRejected() {
        assertThatThrownBy(() -> new CotMau("ma", true, "  "))
                .as("⛔ Bỏ trống mô tả là trả người lập tệp về đúng chỗ phải đoán tên cột — chính "
                        + "thứ tệp mẫu sinh ra để chữa")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mô tả");

        assertThatThrownBy(() -> new CotMau(null, true, "có mô tả"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tên");
    }

    @Test
    @DisplayName("⛔ Danh mục cột RỖNG bị TỪ CHỐI — một tệp mẫu trắng vẫn mở được trong Excel")
    void anEmptyColumnCatalogueIsRejected() {
        assertThatThrownBy(() -> BieuMauCsv.dung(List.of()))
                .as("⛔ Tệp mẫu rỗng tải về trông như một tệp hợp lệ chưa điền — ném ở lúc GỌI thì "
                        + "lỗi hiện ra cho lập trình viên, ⛔ không hiện ra ở bàn người dùng")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BieuMauCsv.dung(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⚠ `tenBatBuoc` giữ ĐÚNG thứ tự khai và chỉ lấy cột bắt buộc")
    void requiredColumnNamesKeepDeclarationOrder() {
        List<CotMau> ba = List.of(
                new CotMau("a", true, "mô tả a"), new CotMau("b", false, "mô tả b"), new CotMau("c", true, "mô tả c"));

        assertThat(CotMau.tenBatBuoc(ba))
                .as("⚠ Thứ tự là thứ tự người lập tệp thấy — đảo nó là đảo thông báo 'tệp thiếu cột'")
                .containsExactly("a", "c");
    }
}
