-- =============================================================================
-- T85.6 — `core_verify_audit_chain` đối chiếu MỐI NỐI với điểm neo kết xuất
-- =============================================================================
--
-- ⛔⛔ Khe hở, đo được 23/09/2026 và đỏ được bằng bài kiểm:
--
--   Hàm bản cũ so `prev_hash` bằng `lag(hash) OVER (ORDER BY seq)` **trong phạm vi được hỏi**, và
--   vế so chỉ chạy khi `expected_prev_hash IS NOT NULL`. ⇒ **Dòng ĐẦU của phạm vi ⛔ bao giờ bị so
--   với thứ gì.** Đóng khung từ điểm neo trở đi — đúng cách dùng mà chú thích của chính hàm ấy kê
--   ra (*"p_from_seq cho phép verify từ điểm neo kết xuất trở đi"*) — thì dòng đầu ấy là chỗ mù.
--
--   Trong sản phẩm lượt kết xuất luôn xoá các dòng CŨ NHẤT, tức một **tiền tố** của chuỗi, nên
--   dòng còn sống đầu tiên LUÔN rơi vào đúng chỗ mù ấy. Một lượt xoá lén VƯỢT QUÁ lô đã kết xuất
--   vì thế trông y hệt một lượt kết xuất hợp lệ.
--
-- ⭐ Thứ phân biệt được hai trạng thái ấy đã nằm sẵn trong kho từ WS-6 và **⛔ ai đọc**:
--   `audit_archive_anchors.last_hash` — hash của dòng CUỐI lô đã kết xuất, ghi 1 nơi
--   (`AuditArchiveHandler`), đọc **0 nơi** (đo toàn kho 23/09). Cùng họ `user_totp.key_id` (T51.0):
--   một cột chết đọc y hệt một quyết định thiết kế — và ở đây nó còn được một javadoc **bênh vực**
--   bằng một vế nhân quả SAI (*"nhờ điểm neo mà chuỗi vẫn kiểm được"*; thứ giữ cho lượt verify xanh
--   là ngữ nghĩa của `lag`, ⛔ phải điểm neo).
--
-- ⛔ Vì sao vá ở SQL chứ ⛔ ở `AuditService`: `COMMENT` của chính hàm này khai *"API verify (T6.12)
--   gọi hàm này, **cấm cài lại ở Java**"*. Tách một nửa phép kiểm sang Java là dựng đúng thứ luật
--   14 cấm — hai nơi con người phải nhớ.
--
-- ⚠ Migration đã phát hành là BẤT BIẾN (§10.65), nên đây là một tệp MỚI dùng `CREATE OR REPLACE`
--   chứ ⛔ sửa `V202608131004`.
--
-- Bộ canh: `KetXuatNhatKyKiemToanTest#xoaLenVuotQuaDiemNeoPhaiBiBat` (ca hỏng) và
--          `#chuaCoDiemNeoNaoThiGiuNguyenHanhVi` (hệ chưa từng kết xuất ⇒ hành vi y như cũ).
-- =============================================================================

CREATE OR REPLACE FUNCTION core_verify_audit_chain(
    p_from_seq BIGINT DEFAULT NULL,
    p_to_seq   BIGINT DEFAULT NULL
) RETURNS TABLE (
    broken_seq    BIGINT,
    broken_id     BIGINT,
    occurred_at   timestamptz,
    reason        TEXT
)
LANGUAGE sql STABLE AS $$
    WITH scoped AS (
        SELECT a.id, a.seq, a.occurred_at, a.actor_user_id, a.module, a.entity_type,
               a.entity_id, a.action, a.old_value, a.new_value, a.prev_hash, a.hash,
               lag(a.hash) OVER (ORDER BY a.seq) AS expected_prev_hash
          FROM audit_logs a
         WHERE (p_from_seq IS NULL OR a.seq >= p_from_seq)
           AND (p_to_seq   IS NULL OR a.seq <= p_to_seq)
    ),
    -- Mối nối: dòng còn sống đầu tiên NGAY SAU một lô đã kết xuất-và-xoá.
    --
    -- ⚠ Chỉ xét dòng có `expected_prev_hash IS NULL`, tức ĐÚNG chỗ mù — phép so cũ đã phủ mọi chỗ
    --   còn lại, và nếu xét cả thì một dòng hỏng sẽ được báo HAI lần với hai lý do.
    --
    -- ⚠⚠ `NOT EXISTS` là vế bắt buộc, ⛔ phải cho chặt tay: ⛔ có nó thì một lượt gọi đóng khung
    --   giữa chuỗi (ví dụ `verifyChain(5000, …)` trên bảng còn nguyên từ seq 1) sẽ đem dòng 5000 so
    --   với một điểm neo cũ cách đó hàng nghìn dòng ⇒ **đỏ giả**. Điểm neo chỉ nói được về mối nối
    --   của CHÍNH nó, tức khi giữa nó và dòng đang xét ⛔ còn dòng nào sống sót.
    moi_noi AS (
        SELECT s.seq, s.id, s.occurred_at, s.prev_hash, neo.last_hash
          FROM scoped s
          CROSS JOIN LATERAL (
              SELECT n.last_hash, n.to_seq
                FROM audit_archive_anchors n
               WHERE n.purged_at IS NOT NULL
                 AND n.to_seq < s.seq
               ORDER BY n.to_seq DESC
               LIMIT 1
          ) neo
         WHERE s.expected_prev_hash IS NULL
           AND NOT EXISTS (
                   SELECT 1
                     FROM audit_logs t
                    WHERE t.seq > neo.to_seq
                      AND t.seq < s.seq)
    )
    SELECT s.seq, s.id, s.occurred_at,
           CASE
               WHEN s.hash <> core_audit_hash(
                        core_audit_canonical_payload(s.seq, s.occurred_at, s.actor_user_id,
                            s.module, s.entity_type, s.entity_id, s.action,
                            s.old_value, s.new_value),
                        s.prev_hash)
                   THEN 'Nội dung bản ghi không khớp hash — đã bị sửa'
               ELSE 'prev_hash không khớp bản ghi liền trước — có bản ghi bị xóa hoặc chèn'
           END
      FROM scoped s
     WHERE s.hash <> core_audit_hash(
               core_audit_canonical_payload(s.seq, s.occurred_at, s.actor_user_id,
                   s.module, s.entity_type, s.entity_id, s.action, s.old_value, s.new_value),
               s.prev_hash)
        OR (s.expected_prev_hash IS NOT NULL AND s.prev_hash IS DISTINCT FROM s.expected_prev_hash)
    UNION ALL
    SELECT m.seq, m.id, m.occurred_at,
           'prev_hash không khớp last_hash của điểm neo kết xuất — có bản ghi bị xóa ngoài lô đã kết xuất'
      FROM moi_noi m
     WHERE m.prev_hash IS DISTINCT FROM m.last_hash
     ORDER BY 1;
$$;

COMMENT ON FUNCTION core_verify_audit_chain IS
    'Trả về các mắt xích gãy; rỗng = toàn vẹn. Kiểm 3 thứ: hash tự thân, prev_hash khớp bản ghi '
    'liền trước, và mối nối với last_hash của điểm neo kết xuất (T85.6). API verify (T6.12) gọi '
    'hàm này, cấm cài lại ở Java';
