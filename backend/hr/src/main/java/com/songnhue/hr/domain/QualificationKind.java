package com.songnhue.hr.domain;

/**
 * Loại mục lý lịch/chuyên môn — CN-04.3.
 *
 * <p>Năm loại nằm chung <b>một bảng</b> {@code employee_qualifications} với cột {@code kind}. Lý do
 * quyết định ⛔ không phải "cho gọn" mà là: <b>cảnh báo hết hiệu lực (M4.9) phải quét MỘT chỗ</b>.
 * Năm bảng nghĩa là năm câu {@code UNION}, và ngày ai đó thêm bảng thứ sáu thì cảnh báo bỏ sót đúng
 * loại mới — ⛔ không một dòng lỗi (luật 12).
 *
 * <p>⛔ Ba nơi phải khớp: enum này · {@code ck_employee_qualifications_kind} ({@code V202609101077})
 * · union TS {@code QualificationKind}. {@code EnumBaNoiTest} canh bộ ba ấy.
 */
public enum QualificationKind {
    /** Bằng cấp chuyên môn — có chuyên ngành, thường ⛔ không hết hiệu lực. */
    BANG_CAP,
    /** Chứng chỉ hành nghề / bồi dưỡng — đây là loại HAY hết hiệu lực nhất. */
    CHUNG_CHI,
    NGOAI_NGU,
    TIN_HOC,
    /** Phần mềm chuyên dụng (AutoCAD, HEC-RAS…) — đặc tả CN-04.3 nêu riêng. */
    PHAN_MEM
}
