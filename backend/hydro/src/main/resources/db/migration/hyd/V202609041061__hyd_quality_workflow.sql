-- =============================================================================
-- WS-32 — Chất lượng số đo: phân loại NGHI_NGO + quy trình duyệt/loại bỏ
--   T32.1  quy tắc nghi ngờ đọc từ `hydro.quality.suspect-rule` (khoá seed 13/8,
--          tới nay CHƯA AI ĐỌC — luật 15 treo 20 ngày)
--   T32.3  bản ghi NGHI_NGO **vẫn ghi vào bảng chính**, kèm lý do đọc được
--   T32.5  quy trình `HYDRO_READING`: NGHI_NGO → HOP_LE | XOA
--
-- ⚠ Số hiệu `V<yyyyMMdd><nnnn>`: `nnnn` là **số thứ tự chạy toàn kho**, ⛔ KHÔNG
--   phải giờ-phút. Đỉnh trước migration này là `V202609041060` ⇒ `1054`.
--   §10.66 đã làm đỏ hai lượt CD liên tiếp vì đúng chỗ này.
-- =============================================================================


-- =============================================================================
-- 1. Hai cột mới trên `hydro_readings` — và vì sao là HAI chứ không phải một
--
-- `quality_reason`  MÁY nói: vì sao bộ phân loại đánh dấu dòng này lúc ingest.
-- `review_note`     NGƯỜI nói: vì sao người duyệt loại bỏ / duyệt lên hợp lệ.
--
-- ⛔ Gộp một cột thì lượt duyệt sẽ ghi đè lời chẩn đoán của máy, và câu hỏi
--    *"hôm ấy nó bị bắt vì lý do gì"* mất câu trả lời — trong khi đó chính là
--    câu duy nhất giúp phân biệt "cảm biến hỏng" với "vừa mở cống".
--
-- ⚠ `note` sẵn có KHÔNG dùng được cho việc này: `ck_hydro_readings_nguoi_nhap`
--   cấm mọi dòng `source <> 'MANUAL'` mang `note` — cố ý, vì `note` là lời của
--   người NHẬP, không phải lời của người DUYỆT.
--
-- ⭐ `review_note` còn bịt một lỗ có thật của Workflow engine dùng chung: tham
--    số `reason` của `WorkflowPort.execute` được **validate rồi vứt đi** — không
--    cột nào, không bảng nào giữ nó (đã kiểm: `audit_logs` không có cột lý do).
--    Nghĩa là `requires_reason = TRUE` hôm nay chỉ bắt người dùng gõ một câu rồi
--    ném đi. Ở đây lý do được ghi vào **một cột của chính entity**, nên
--    `AuditEventListener` bắt được lệnh UPDATE và nó đi vào chuỗi băm cùng với
--    ai bấm + lúc nào — đúng thứ chốt F2 đòi ("soft delete + audit ai xoá, lý do").
-- =============================================================================
ALTER TABLE hydro_readings
    ADD COLUMN quality_reason VARCHAR(200),
    ADD COLUMN review_note    VARCHAR(500);

COMMENT ON COLUMN hydro_readings.quality_reason IS
    'MÁY nói: vì sao bộ phân loại đánh dấu NGHI_NGO lúc ingest. Giữ nguyên sau khi duyệt.';
COMMENT ON COLUMN hydro_readings.review_note IS
    'NGƯỜI nói: lý do người duyệt loại bỏ / duyệt lên hợp lệ. Bắt buộc với bước XOA.';


-- =============================================================================
-- 2. `XOA` — trạng thái thứ ba của cột `quality`, và vì sao KHÔNG phải `deleted_at`
--
-- Chốt F2 đòi bước "Xoá (soft delete + audit ai xoá, lý do)". Hai cách làm:
--
--   (a) cột `deleted_at` riêng          → HAI cột cùng trả lời *"dòng này còn
--                                          dùng được không"*, nên mọi truy vấn
--                                          báo cáo phải nhớ HAI vế. Luật 14 gọi
--                                          tên: chỗ nào con người phải nhớ hai
--                                          nơi thì chỗ đó sẽ lệch.
--   (b) `quality = 'XOA'`   ← CHỌN       → bộ lọc `quality = 'HOP_LE'` mà quy tắc
--                                          14 vốn đã bắt mọi truy vấn phải có
--                                          loại nó ra **miễn phí**. Một bất biến,
--                                          một bộ canh (T32.4).
--
-- ⛔ `XOA` KHÔNG phải "mức chất lượng thứ ba": câu cấm ở `ReadingQuality` nói về
--    THANG chất lượng — giữa HOP_LE và NGHI_NGO vẫn cấm chèn mức nào. `XOA` là
--    bia mộ, nằm ngoài thang.
--
-- ⚠⚠ `hydro_latest.last_quality` CỐ Ý KHÔNG nhận `XOA`. Đó là một bất biến, không
--    phải một chỗ quên: bảng "hiện tại" ⛔ không bao giờ được trỏ vào một bản ghi
--    đã bị loại bỏ. `HydroLatestRecomputer` lọc `quality <> 'XOA'` trước khi ghi,
--    nên ràng buộc hẹp hơn ở đây là **lưới an toàn** cho lượt ghi ấy —
--    `HydroEnumSchemaTest` khai khoảng chênh này **có tên** (luật 28).
-- =============================================================================
ALTER TABLE hydro_readings DROP CONSTRAINT ck_hydro_readings_quality;
ALTER TABLE hydro_readings
    ADD CONSTRAINT ck_hydro_readings_quality CHECK (quality IN ('HOP_LE', 'NGHI_NGO', 'XOA'));

-- Một cờ đỏ không nói được vì sao là một cờ đỏ không hành động được: người duyệt
-- mở màn hình, thấy nhãn, và không biết nên bấm Duyệt hay Loại bỏ — hai việc
-- ngược nhau. `ChanDoanChatLuong` đã ép ở hàm dựng; ép lại ở đây vì đường ghi
-- không chỉ có một (luật 12: đặt bảo đảm ở chỗ dữ liệu đi qua).
ALTER TABLE hydro_readings
    ADD CONSTRAINT ck_hydro_readings_nghi_ngo_co_ly_do CHECK (
        quality <> 'NGHI_NGO' OR quality_reason IS NOT NULL
    );

-- Bước XOA khai `requires_reason = TRUE` ở tầng quy trình; ép lại ở tầng dữ liệu
-- vì cờ ấy chỉ chặn được đường đi qua engine.
ALTER TABLE hydro_readings
    ADD CONSTRAINT ck_hydro_readings_xoa_co_ly_do CHECK (
        quality <> 'XOA' OR review_note IS NOT NULL
    );

-- -----------------------------------------------------------------------------
-- ⭐ Chỉ mục của màn hình "Dữ liệu nghi ngờ" phải hẹp lại cùng lúc.
--
-- Bản cũ là `WHERE quality <> 'HOP_LE'` — viết lúc cột chỉ có HAI giá trị, nên nó
-- ĐÚNG BẰNG `= 'NGHI_NGO'`. Thêm `XOA` vào làm nó lặng lẽ rộng ra: chỉ mục nuôi
-- một màn hình chỉ xem hàng chờ duyệt sẽ mang theo toàn bộ bản ghi đã loại bỏ,
-- và số ấy chỉ tăng, không bao giờ giảm.
--
-- 📌 Cùng hình dạng luật 3 (*canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH*)
--    dịch sang SQL: một vị từ viết bằng phủ định là vị từ có nghĩa THAY ĐỔI khi
--    tập giá trị thay đổi. Viết thẳng giá trị muốn lấy thì không có chuyện đó.
-- -----------------------------------------------------------------------------
DROP INDEX ix_hydro_readings_nghi_ngo;
CREATE INDEX ix_hydro_readings_nghi_ngo ON hydro_readings (measured_at DESC)
    WHERE quality = 'NGHI_NGO';


-- =============================================================================
-- 3. Quy trình `HYDRO_READING` — T32.5
--
-- ⭐ Tách vai trò nằm ở DỮ LIỆU: cả hai bước đòi `hyd:measurement:review`, và
--    quyền ấy chỉ TECHNICIAN có (cộng SUPER_ADMIN/ADMIN qua CROSS JOIN). Đo được
--    ở `V202608131007`: XN_MANAGER · XN_OPERATOR · DUTY_OFFICER · EXECUTIVE ·
--    VIEWER đều có `hyd:measurement:view` mà KHÔNG có `:review` — họ xem được
--    hàng chờ, ⛔ không bấm được. Đó là chủ ý: duyệt một số đo nghi ngờ là quyết
--    định kỹ thuật về cảm biến, không phải quyết định vận hành.
--
-- ⚠ `initial_state = 'HOP_LE'` ở đây ghi lại MẶC ĐỊNH CỦA CỘT, ⛔ không phải một
--   đường chạy: lượt ingest ghi ~28 dòng bằng MỘT câu INSERT, 2 phút/lần, và ⛔
--   không đi qua engine (đi qua nghĩa là 28 lượt nạp entity + 28 lượt tra quy
--   trình cho mỗi lượt polling). Quy tắc 4 nói về ĐỔI trạng thái, và mọi lượt đổi
--   trạng thái của bảng này đều đi qua `HydroReviewService` → engine.
--   Nói ra thay vì để người sau đọc `initial_state` như một bảo đảm (luật 28).
--
-- ⛔ Không có `notify_event` ở cả hai bước. Thông báo đáng gửi nằm ở lúc PHÁT
--    HIỆN (T32.3, gửi cho người có `hyd:measurement:review`), không ở lúc chính
--    người ấy vừa bấm nút. Một thông báo báo cho người vừa hành động biết họ vừa
--    hành động là tiếng ồn, và tiếng ồn làm hỏng những chuông còn lại.
-- =============================================================================
INSERT INTO workflow_definitions (code, entity_type, name, initial_state, description)
VALUES ('HYDRO_READING', 'HYDRO_READING', 'Số đo thuỷ văn', 'HOP_LE',
        'Bản ghi bị bộ phân loại đánh dấu NGHI_NGO chờ người có hyd:measurement:review '
        'duyệt lên HOP_LE hoặc loại bỏ (XOA, bắt buộc nêu lý do). XOA là trạng thái cuối.');

INSERT INTO workflow_transitions (
    definition_id, from_state, action, to_state,
    required_permission, notify_event, notify_permission, notify_owner,
    requires_reason, label, sort_order
)
SELECT d.id, v.from_state, v.action, v.to_state,
       v.required_permission, NULL, NULL, FALSE,
       v.requires_reason, v.label, v.sort_order
FROM workflow_definitions d,
     (VALUES
         -- Duyệt: số liệu bất thường nhưng người trực xác nhận là thật (mở cống,
         -- xả lũ). ⛔ Không đòi lý do — hành động này KHÔI PHỤC dữ liệu về mặc
         -- định, và bắt gõ lý do cho việc thường xuyên nhất là dạy người dùng gõ
         -- bừa, làm hỏng luôn ô lý do của bước bên cạnh.
         ('NGHI_NGO', 'DUYET', 'HOP_LE', 'hyd:measurement:review', FALSE,
          'Duyệt là số liệu thật', 10),

         -- Loại bỏ: ⭐ BẮT BUỘC lý do. Đây là bước duy nhất làm một số đo có thật
         -- biến mất khỏi mọi báo cáo, và nguồn không có API lịch sử — không ai
         -- dựng lại được bối cảnh của quyết định này về sau nếu không ghi ngay.
         ('NGHI_NGO', 'XOA', 'XOA', 'hyd:measurement:review', TRUE,
          'Loại bỏ (nêu lý do)', 20)
     ) AS v(from_state, action, to_state, required_permission, requires_reason, label, sort_order)
WHERE d.entity_type = 'HYDRO_READING';


-- =============================================================================
-- 4. Quyền `hyd:measurement:create` — nhập tay số đo khi API gián đoạn (T32.7)
--
-- ⚠⚠ QUYỀN MỚI, và nó tồn tại vì một phép ĐO chứ không vì thẩm mỹ danh mục.
--
-- Danh mục quyền thuỷ văn hôm nay chỉ có `:view` và `:review`. Nếu gác màn hình
-- nhập tay bằng `:review` thì chỉ TECHNICIAN dùng được — trong khi CN-03.2 nói
-- rõ chức năng này là để "hỗ trợ khi API gián đoạn", tức việc của người ĐANG
-- TRỰC: DUTY_OFFICER, XN_OPERATOR, XN_MANAGER. Đo trên `V202608131007`: cả ba
-- vai trò ấy có `hyd:measurement:view` và KHÔNG có `:review`.
--
-- ⇒ Gác bằng `:review` là dựng đúng hình dạng T27.20/T28.25 lần thứ ba: một
--   biểu mẫu mà vai trò sở hữu công việc ấy không mở được. Thà thêm một quyền
--   có tên còn hơn để một màn hình "đã dựng xong" mà không ai dùng được (luật
--   27: đếm màn hình đã dựng là đếm sai đơn vị).
--
-- ⛔ Cấp cho VIEWER và EXECUTIVE: KHÔNG. Họ đọc, không ghi.
--
-- ⚠⚠ SUPER_ADMIN và ADMIN PHẢI ĐƯỢC CẤP TƯỜNG MINH — bản đầu của migration này
--    ghi ngược lại: *"`V202608131007` cấp cho hai vai trò ấy bằng CROSS JOIN
--    permissions nên mọi quyền mới tự có"*. Câu đó SAI, và
--    `RbacMatrixTest.superAdminHoldsEveryPermission` bắt được ngay lượt chạy đầu.
--    `CROSS JOIN` ấy chạy MỘT LẦN, lúc seed — nó là một lượt ghi dữ liệu, ⛔ không
--    phải một luật còn hiệu lực. Mọi quyền sinh ra sau ngày 13/8 đều phải tự đi
--    xin chữ ký. Cùng họ luật 17: *tham số chỉ có hiệu lực MỘT LẦN thì tệp cấu
--    hình không còn là bằng chứng*.
-- =============================================================================
INSERT INTO permissions (code, module, resource, action, name)
VALUES ('hyd:measurement:create', 'hyd', 'measurement', 'create', 'Nhập tay số đo thuỷ văn')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE p.code = 'hyd:measurement:create'
   AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'TECHNICIAN', 'DUTY_OFFICER', 'XN_MANAGER', 'XN_OPERATOR')
ON CONFLICT DO NOTHING;

-- Chốt hạ: quyền mới phải tới được ít nhất một vai trò. Một câu INSERT chọn theo
-- mã vai trò mà gõ sai mã thì ghi 0 dòng và KHÔNG lệnh nào báo sai —
-- `RbacMatrixTest.noPermissionIsOrphaned` sẽ đỏ ở một lớp khác với một thông điệp
-- chẳng liên quan. Bắt ngay tại đây (§10.66: seed ghi vào một khoá chưa tồn tại,
-- 0 hàng, không một dòng log).
DO $$
DECLARE
    v_so INT;
BEGIN
    SELECT count(*) INTO v_so
      FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'hyd:measurement:create';

    IF v_so < 6 THEN
        RAISE EXCEPTION 'hyd:measurement:create mới tới % vai trò, chờ 6 — kiểm lại mã vai trò', v_so;
    END IF;
END $$;


-- =============================================================================
-- 5. Nối vế ĐỌC cho `hydro.quality.suspect-rule` — T32.1
--
-- ⚠⚠ ĐÂY LÀ VỎ BỌC SAI HỎNG CẢM BIẾN, ⛔ KHÔNG PHẢI NGƯỠNG CẢNH BÁO (G9-a).
--    Hai thứ nghe giống nhau: cái này trả lời *"số này có thể là số thật không"*,
--    cái kia trả lời *"mực nước này có đáng lo không"*. Ngưỡng cảnh báo vẫn chờ
--    Công ty và vẫn nằm ở WS-33.
--
-- ⭐ Con số suy được từ PHÉP ĐO, ⛔ không đoán:
--    · giá trị quan sát 01/09/2026 nằm trong **1,57 … 4,93 m** (28 mã, 4 lượt gọi);
--    · vỏ bọc [-10 … 30] rộng gấp ~6 lần biên trên quan sát được.
--    · ⭐ Nó bắt được đúng chế độ hỏng đắt nhất đã lường trước: **quên chia 100**.
--      Nguồn trả cm; adapter chia 100. Một bản adapter đánh mất phép chia ấy cho
--      ra 157…493 — mọi dòng vượt 30 và bị đánh dấu NGHI_NGO NGAY LƯỢT ĐẦU, thay
--      vì trôi vào báo cáo dưới dạng "mực nước 4,93 mét → 493 mét" vẽ vẫn đẹp.
--    · Sentinel âm của thiết bị đo (-999 / -9999) rơi dưới -10.
--
-- ⛔ CỐ Ý KHÔNG seed `deltaToiDaMoiGio`. Tốc độ đổi hợp lệ ⛔ không suy được từ
--    một lượt đo: mở cống, xả lũ, bơm tiêu đều làm mực nước nhảy nhanh một cách
--    ĐÚNG. Đặt một con số đoán vào đó là biến mọi lượt vận hành bình thường thành
--    "dữ liệu nghi ngờ", và sau vài ngày người trực thôi đọc nhãn ấy — lúc đó nhãn
--    hỏng thật cũng không ai thấy. Ô nhập vẫn có trên màn hình Cấu hình để bật
--    khi đã tích đủ chuỗi số đo (quy tắc 12).
--
-- ⛔ CỐ Ý KHÔNG seed `LUONG_MUA` / `LUU_LUONG`: chưa có nguồn nào (G3-a). Seed
--    tham số cho tính năng chưa dựng là đúng thứ luật 15 cấm.
--
-- ⚠ `V202608131009` dặn: *"migration sau CẤM ghi đè giá trị Admin đã sửa"*. Nên
--   `setting_value` chỉ được ghi khi nó CÒN RỖNG; `default_value` và `description`
--   là siêu dữ liệu của khoá nên cập nhật vô điều kiện.
-- =============================================================================
UPDATE settings
   SET default_value = '{"MUC_NUOC":{"min":-10,"max":30}}',
       description   = 'Vỏ bọc phát hiện HỎNG CẢM BIẾN theo từng loại chỉ số (chốt F2) — '
                       'KHÔNG phải ngưỡng cảnh báo (G9-a). JSON: {"<mã loại chỉ số>":'
                       '{"min":…,"max":…,"deltaToiDaMoiGio":…}}, đơn vị là đơn vị CHUẨN HOÁ. '
                       'Bỏ trống một khoá = không kiểm mục đó. Bản ghi vượt vỏ bọc vẫn được GHI, '
                       'chỉ mang cờ NGHI_NGO — mọi truy vấn báo cáo/cảnh báo phải lọc quality = HOP_LE.'
 WHERE setting_key = 'hydro.quality.suspect-rule';

UPDATE settings
   SET setting_value = '{"MUC_NUOC":{"min":-10,"max":30}}'
 WHERE setting_key = 'hydro.quality.suspect-rule'
   AND coalesce(setting_value, '') = '';
