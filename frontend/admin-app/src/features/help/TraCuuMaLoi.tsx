import { Alert, Empty, Table, Tag } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useMemo } from 'react';

import { khopMoiTu } from '@/shared/boDau';
import { ERROR_CATALOG, type ErrorEntry } from '@/shared/error-map';

/**
 * **Tra cứu mã lỗi** — sinh từ `ERROR_CATALOG`, ⛔ một dòng nào viết tay.
 *
 * <h2>⭐⭐ Vì sao mục này phải có</h2>
 *
 * §11 của tài liệu dặn người dùng *"báo lỗi thì kèm mã lỗi"* — mà trước mục này, kho ⛔ có chỗ nào
 * tra một mã ra nghĩa. Người dùng đọc được `OPS-2003` trên màn hình rồi ⛔ biết làm gì với nó, nên
 * lời dặn ấy chỉ có tác dụng với người hỗ trợ, ⛔ với người gặp lỗi. Đúng nửa cặp đọc–ghi (luật 27).
 *
 * <h2>⭐ Sinh tự động ⇒ ⛔ có gì để bảo trì</h2>
 *
 * `error-map.test.ts` đã canh `ERROR_CATALOG` khớp `error-messages.properties` của backend. Thêm
 * một mã lỗi ở backend là nó **tự xuất hiện ở đây**, ⛔ ai phải nhớ cập nhật tài liệu — đúng thứ
 * một bảng viết tay ⛔ bao giờ làm được.
 */

/** Nhóm theo tiền tố mã (`OPS-2003` → `OPS`) để người tra biết mình đang ở vùng nghiệp vụ nào. */
const TEN_NHOM: Readonly<Record<string, string>> = {
  SYS: 'Hệ thống chung',
  AUTH: 'Đăng nhập & phiên',
  ADM: 'Quản trị hệ thống',
  CMS: 'Nội dung cổng',
  OPS: 'Vận hành công trình',
  HYD: 'Dữ liệu thuỷ văn',
  HR: 'Nhân sự',
};

/**
 * Việc người dùng nên làm, suy từ **cách FE xử lý** mã đó — ⛔ phải một câu viết tay cho từng mã.
 *
 * ⚠ Cố ý thô: `handling` là thứ quyết định hành vi thật của giao diện, nên câu ở đây ⛔ bao giờ
 * mâu thuẫn với thứ người dùng vừa nhìn thấy. Một bảng 136 câu viết tay thì sẽ.
 */
const VIEC_CAN_LAM: Readonly<Record<ErrorEntry['handling'], string>> = {
  toast: 'Thử lại. Còn lặp thì báo kèm mã lỗi và thời điểm.',
  form: 'Sửa lại ô nhập đang bị tô đỏ rồi lưu lại.',
  reauth: 'Phiên đã hết hạn — đăng nhập lại.',
  changePassword: 'Phải đổi mật khẩu trước khi làm việc khác.',
  maintenance: 'Hệ thống đang khôi phục dữ liệu, tạm thời không ghi được. Chờ và thử lại.',
  forbidden: 'Tài khoản không có quyền hoặc dữ liệu ngoài phạm vi đơn vị. Đề nghị quản trị viên.',
  retryCsrf: 'Hệ thống tự thử lại. Nếu vẫn lỗi thì tải lại trang.',
  caller: 'Làm theo thông báo hiện ngay cạnh ô nhập.',
};

interface Hang {
  key: string;
  ma: string;
  nhom: string;
  nghia: string;
  viec: string;
  severity: ErrorEntry['severity'];
}

const MOI_MA: Hang[] = Object.entries(ERROR_CATALOG as Record<string, ErrorEntry>)
  .map(([ma, e]) => ({
    key: ma,
    ma,
    nhom: TEN_NHOM[ma.split('-')[0]] ?? ma.split('-')[0],
    nghia: e.message,
    viec: VIEC_CAN_LAM[e.handling],
    severity: e.severity,
  }))
  .sort((a, b) => a.ma.localeCompare(b.ma));

const MAU: Readonly<Record<ErrorEntry['severity'], string>> = {
  info: 'default',
  warning: 'warning',
  error: 'error',
};

export function TraCuuMaLoi({ tuKhoa }: { tuKhoa: string }) {
  const hang = useMemo(() => {
    if (tuKhoa.trim().length === 0) {
      return MOI_MA;
    }
    // ⚠ Cùng phép khớp với phần tài liệu: đòi **mọi từ**, ⛔ đòi nguyên cụm. Hai ô tìm kiếm cư xử
    //   khác nhau trên cùng một từ khoá là thứ người dùng đọc thành "lúc được lúc không".
    return MOI_MA.filter((h) => khopMoiTu(`${h.ma} ${h.nhom} ${h.nghia} ${h.viec}`, tuKhoa));
  }, [tuKhoa]);

  const cot: ColumnsType<Hang> = [
    {
      title: 'Mã',
      dataIndex: 'ma',
      width: 118,
      render: (ma: string, r) => <Tag color={MAU[r.severity]}>{ma}</Tag>,
    },
    { title: 'Nhóm', dataIndex: 'nhom', width: 150 },
    { title: 'Nghĩa là gì', dataIndex: 'nghia' },
    { title: 'Nên làm gì', dataIndex: 'viec' },
  ];

  return (
    <section id="tra-cuu-ma-loi" className="sn-hd__sinh">
      <h2>Tra cứu mã lỗi</h2>
      <Alert
        type="info"
        showIcon
        title={
          <>
            Khi màn hình báo lỗi kèm một mã dạng <code>OPS-2003</code>, tra ở đây để biết nghĩa và
            việc cần làm. Gõ vào ô <b>Tìm trong tài liệu</b> ở trên để lọc bảng này.{' '}
            <b>{hang.length}</b>/{MOI_MA.length} mã.
          </>
        }
        style={{ marginBottom: 12 }}
      />
      {hang.length === 0 ? (
        <Empty description={`Không có mã lỗi nào khớp "${tuKhoa}"`} />
      ) : (
        <Table
          size="small"
          columns={cot}
          dataSource={hang}
          pagination={{ pageSize: 20, size: 'small', showSizeChanger: false }}
          scroll={{ x: 'max-content' }}
        />
      )}
    </section>
  );
}
