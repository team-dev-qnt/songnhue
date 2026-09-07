-- =============================================================================
-- Hàm bỏ dấu tiếng Việt dùng được trong chỉ mục (WS-13, dùng chung mọi module)
--
-- ⚠⚠ VÌ SAO `unaccent()` KHÔNG DÙNG THẲNG ĐƯỢC TRONG CHỈ MỤC.
--
-- Bản một tham số `unaccent(text)` tra từ điển mặc định **lúc chạy**, mà từ điển
-- có thể đổi, nên Postgres không coi nó là IMMUTABLE và từ chối đưa vào biểu thức
-- chỉ mục. Bản hai tham số `unaccent(regdictionary, text)` chỉ đích danh từ điển
-- nên bọc lại và khai IMMUTABLE được.
--
-- Thiếu hàm này thì mọi chỉ mục tìm kiếm bỏ dấu hỏng với thông báo
-- "functions in index expression must be marked IMMUTABLE".
--
-- ⚠ Đặt ở `core` chứ không ở `cms` dù người dùng đầu tiên là bài viết: tìm kiếm
--   bỏ dấu là nhu cầu của mọi module (công trình, hồ sơ nhân sự, điểm đo). Quan
--   trọng hơn — `CoreFunctionContributor` ở tầng Java khai kiểu trả về của hàm
--   này cho Hibernate, và Core không được phụ thuộc vào migration của module con.
--
-- ⚠ SỐ HIỆU PHẢI NHỎ HƠN migration nào tạo chỉ mục dùng hàm này. Flyway sắp xếp
--   theo số hiệu trên TOÀN BỘ các thư mục, nên `cms` bắt đầu từ 1016 là có chủ ý.
-- =============================================================================

CREATE FUNCTION sn_khong_dau(text) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$ SELECT lower(public.unaccent('public.unaccent', $1)) $$;

COMMENT ON FUNCTION sn_khong_dau(text) IS
    'Bỏ dấu + hạ chữ thường, IMMUTABLE nên dùng được trong chỉ mục. '
    'Gõ "de dieu" phải tìm ra "đê điều". Khai kiểu cho Hibernate ở CoreFunctionContributor.';
