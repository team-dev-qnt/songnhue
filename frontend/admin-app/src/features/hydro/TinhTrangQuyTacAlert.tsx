import { Alert, Typography } from 'antd';

import { type QualityRuleStatus } from '@/shared/api-types';

/**
 * ⚠⚠ Câu trả lời cho *"bảng rỗng nghĩa là gì"* — quy tắc 16.
 *
 * Tách thành component riêng vì nó là **một khẳng định**, không phải trang trí: một bảng trống
 * trong khi bộ phân loại đang tắt trông y hệt một hệ thống sạch dữ liệu xấu, và người vận hành sẽ
 * yên tâm sai suốt nhiều tháng.
 */
export function TinhTrangQuyTacAlert({
  tinhTrang,
  soDong,
}: {
  tinhTrang?: QualityRuleStatus;
  soDong: number;
}) {
  if (!tinhTrang) {
    return null;
  }
  if (tinhTrang.loiCauHinh) {
    return (
      <Alert
        type="error"
        showIcon
        style={{ marginBottom: 16 }}
        message="Quy tắc phân loại đang HỎNG — bộ phân loại đã tắt"
        description={
          <>
            Khoá <Typography.Text code>hydro.quality.suspect-rule</Typography.Text> không đọc được,
            nên <b>mọi số đo mới đều được ghi là Hợp lệ mà không qua kiểm tra nào</b>. Sửa ở{' '}
            <b>Quản trị › Cấu hình hệ thống</b>, nhóm HYDRO.
            <br />
            Lỗi: <Typography.Text code>{tinhTrang.loiCauHinh}</Typography.Text>
          </>
        }
      />
    );
  }
  if (!tinhTrang.dangKiem) {
    return (
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="Chưa cấu hình quy tắc phân loại — bảng dưới sẽ RỖNG VĨNH VIỄN"
        description={
          <>
            ⛔ Bảng trống ở đây <b>không</b> có nghĩa là dữ liệu sạch: chưa có quy tắc nào thì không
            bản ghi nào bị đánh dấu. Khai khoảng vật lý theo từng loại chỉ số ở{' '}
            <b>Quản trị › Cấu hình hệ thống</b>, nhóm HYDRO.
          </>
        }
      />
    );
  }
  if (soDong === 0) {
    return (
      <Alert
        type="success"
        showIcon
        style={{ marginBottom: 16 }}
        message="Bộ phân loại đang chạy và không có số đo nào đáng ngờ"
      />
    );
  }
  return (
    <Alert
      type="warning"
      showIcon
      style={{ marginBottom: 16 }}
      message={`${soDong} số đo đang chờ duyệt trên trang này`}
      description={
        <>
          Các bản ghi này <b>vẫn nằm trong CSDL</b> — chúng chỉ bị loại khỏi báo cáo, biểu đồ và
          cảnh báo ngưỡng cho tới khi có người xử lý.
          <br />⚠ Đọc cột <b>Máy nói</b> trước khi bấm: <i>ngoài khoảng vật lý</i> gần như luôn là
          cảm biến hỏng, còn <i>nhảy quá nhanh</i> thì rất có thể là vừa mở cống — hai việc ngược
          nhau.
        </>
      }
    />
  );
}
