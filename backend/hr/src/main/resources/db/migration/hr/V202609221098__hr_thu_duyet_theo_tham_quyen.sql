-- ============================================================================
-- T85.3 — Thư của quy trình nghỉ phép đi theo THẨM QUYỀN, ⛔ theo PHẠM VI
-- ============================================================================
--
-- Bốn hàng `workflow_transitions` của `LEAVE_REQUEST` khai `notify_permission =
-- 'hr:leave:approve'`. `WorkflowEngine` đọc cột ấy và gửi cho **mọi tài khoản có
-- quyền mà phạm vi dữ liệu phủ đơn vị của đơn**. T80.7 đã ĐO rằng tập ấy rộng hơn
-- tập người bấm được nút: một tài khoản ở đơn vị gốc có quyền và có phạm vi, nhưng
-- ⛔ phải trưởng/phó và ⛔ được uỷ quyền ⇒ *thấy đơn, nhận thư, mà ⛔ có nút*.
--
-- ⛔⛔ Và engine ⛔ THỂ sửa để đúng: nó sống ở `core`, mà quy tắc 6 cấm `core` import
--    `hr` ⇒ nó ⛔ nhìn thấy `UyQuyenDuyetPhep` (bỏ sót đúng người được uỷ quyền) lẫn
--    vế phân tách trách nhiệm (`TRUNG_NGUOI_CAP_MOT` — người vừa duyệt cấp 1 ⛔ được
--    duyệt cấp 2). Đây là lý do dòng nợ T82.5 tự kê ra một cách vá BẤT KHẢ.
--
-- ⇒ Gỡ cột thông báo khỏi bốn hàng ấy; `DonNghiPhepService.baoBuocChuyen` phát tường
--   minh bằng danh sách ĐÍCH DANH dựng ở `hr`. Cùng khuôn `LEAVE_SUBMITTED` đã dùng
--   từ T57.6, và vì cùng một lý do.
--
-- ⚠⚠ HAI nhóm, ⛔ phải một — chỗ này là chỗ dễ vá hỏng nhất của cả lượt:
--
--   (a) BA hàng có `notify_owner = FALSE` (chuyển cấp 2 · rút đơn ×2): người duy nhất
--       cần biết là NGƯỜI DUYỆT ⇒ gỡ CẢ CẶP `notify_event` + `notify_permission`.
--       Ràng buộc `ck_workflow_transitions_notify_target_needs_event` đòi gỡ cả cặp
--       chứ ⛔ cho để `notify_permission` mồ côi.
--
--   (b) MỘT hàng có `notify_owner = TRUE` (*huỷ đơn ĐÃ DUYỆT*): ngoài người duyệt còn
--       **chính người lao động** phải được báo rằng phép đã duyệt của mình bị huỷ và
--       số dư được hoàn. ⇒ GIỮ `notify_event` + `notify_owner`, chỉ bỏ
--       `notify_permission`. Gỡ cả cặp ở hàng này — đúng như dòng nợ T82.5 kê — là
--       **tắt lặng lẽ thư báo cho người lao động**, một khuyết tật nặng hơn hẳn cái
--       đang sửa, và ⛔ bài kiểm nào hiện có bắt được.
--
-- Số hàng được khẳng định ngay tại đây: seed gốc (`V202609141079`) có thể bị sửa tay
-- ở một môi trường nào đó, và một lượt `UPDATE` khớp 0 hàng thì im lặng hoàn toàn.

DO $$
DECLARE
    so_dong INT;
BEGIN
    -- (a) Thư chỉ dành cho người duyệt ⇒ engine thôi phát hẳn.
    UPDATE workflow_transitions t
       SET notify_event = NULL,
           notify_permission = NULL
      FROM workflow_definitions d
     WHERE t.definition_id = d.id
       AND d.entity_type = 'LEAVE_REQUEST'
       AND t.notify_permission = 'hr:leave:approve'
       AND t.notify_owner = FALSE;
    GET DIAGNOSTICS so_dong = ROW_COUNT;
    IF so_dong <> 3 THEN
        RAISE EXCEPTION
            'T85.3: chờ 3 hàng LEAVE_REQUEST (notify_permission=hr:leave:approve, notify_owner=FALSE), đo được %. '
            'Seed V202609141079 đã bị sửa? ⛔ nới con số này — đi đọc lại bảng trước.', so_dong;
    END IF;

    -- (b) Người lao động VẪN phải được báo ⇒ giữ notify_event + notify_owner.
    UPDATE workflow_transitions t
       SET notify_permission = NULL
      FROM workflow_definitions d
     WHERE t.definition_id = d.id
       AND d.entity_type = 'LEAVE_REQUEST'
       AND t.notify_permission = 'hr:leave:approve'
       AND t.notify_owner = TRUE;
    GET DIAGNOSTICS so_dong = ROW_COUNT;
    IF so_dong <> 1 THEN
        RAISE EXCEPTION
            'T85.3: chờ 1 hàng *huỷ đơn đã duyệt* (notify_owner=TRUE), đo được %.', so_dong;
    END IF;

    -- Vế CHỐNG TẬP RỖNG (luật 7): sau hai lượt trên, ⛔ hàng LEAVE_REQUEST nào còn
    -- gửi theo quyền. Thiếu khẳng định này thì một lượt chạy lại trên CSDL đã vá sẽ
    -- đỏ ở (a) vì 0 hàng — đúng, nhưng nó ⛔ nói được là *đã vá rồi* hay *seed hỏng*.
    SELECT count(*) INTO so_dong
      FROM workflow_transitions t
      JOIN workflow_definitions d ON d.id = t.definition_id
     WHERE d.entity_type = 'LEAVE_REQUEST'
       AND t.notify_permission IS NOT NULL;
    IF so_dong <> 0 THEN
        RAISE EXCEPTION 'T85.3: còn % hàng LEAVE_REQUEST gửi theo quyền sau khi vá.', so_dong;
    END IF;
END $$;

COMMENT ON COLUMN workflow_transitions.notify_permission IS
    'Gửi cho mọi tài khoản có quyền này mà PHẠM VI dữ liệu phủ đơn vị liên quan. '
    '⚠ T85.3 (22/09/2026): phạm vi ⛔ phải thẩm quyền. Với quy trình nào mà "ai được bấm" '
    'phụ thuộc chức vụ / uỷ quyền / phân tách trách nhiệm thì cột này gửi RỘNG HƠN tập bấm '
    'được — khi ấy để trống và cho module nghiệp vụ phát danh sách đích danh (xem '
    'DonNghiPhepService.baoBuocChuyen). LEAVE_REQUEST đã chuyển hẳn sang đường ấy.';
