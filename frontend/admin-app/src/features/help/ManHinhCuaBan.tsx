import { Alert, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { Link } from 'react-router-dom';

import { MAN_HINH, type NhomMuc } from './mucTaiLieu';

/**
 * **Màn hình của bạn** — danh sách màn hình mở được, sinh từ `MENU` + quyền THẬT của người đang
 * đăng nhập.
 *
 * <h2>⭐⭐ Vì sao bảng này giá trị hơn cả §3 của tài liệu</h2>
 *
 * §3 là một bảng viết tay: nó liệt kê **mọi** màn hình của hệ thống, nên người đọc vẫn phải tự
 * đoán mục nào thuộc về mình. Bảng này trả lời thẳng — và nó **⛔ thể lệch**, vì đọc đúng hai thứ
 * mà thanh điều hướng và route guard đang đọc: `MENU` và `permissions` trong token.
 *
 * <h2>⛔ Đây ⛔ phải một cơ chế bảo mật</h2>
 *
 * Nó là tầng 1 (§4.2), y hệt việc ẩn menu: giúp người dùng khỏi đâm vào bức tường họ ⛔ nhìn thấy.
 * Chốt chặn thật vẫn ở `@RequirePermission` của backend.
 */
export function ManHinhCuaBan({
  quyen,
  nhom,
  tenBoLoc,
  laVaiTroCuaToi,
}: {
  quyen: ReadonlySet<string>;
  nhom: readonly NhomMuc[];
  tenBoLoc: string;
  laVaiTroCuaToi: boolean;
}) {
  const hang = [...MAN_HINH.entries()]
    .filter(([, v]) => v.quyen.length === 0 || v.quyen.some((c) => quyen.has(c)))
    .map(([duongDan, v]) => ({
      key: duongDan,
      duongDan,
      nhan: v.nhan,
      muc: timMuc(nhom, duongDan),
    }));

  const cot: ColumnsType<(typeof hang)[number]> = [
    {
      title: 'Màn hình',
      dataIndex: 'nhan',
      render: (nhan: string, r) =>
        // ⚠ Chỉ dẫn được tới màn hình khi đây là quyền của CHÍNH người đang đăng nhập. Đang xem
        //   góc nhìn của vai trò khác mà bấm vào thì nhận 403 — một liên kết chắc chắn hỏng tệ
        //   hơn hẳn một dòng chữ (T23.8).
        laVaiTroCuaToi ? <Link to={r.duongDan}>{nhan}</Link> : <span>{nhan}</span>,
    },
    { title: 'Đường dẫn', dataIndex: 'duongDan', render: (d: string) => <code>{d}</code> },
    {
      title: 'Hướng dẫn',
      dataIndex: 'muc',
      render: (muc: string | undefined) =>
        muc ? (
          <a href={`#${muc}`}>Xem mục</a>
        ) : (
          // Về nguyên tắc ⛔ bao giờ rơi vào đây: `phuManHinh.test.ts` làm CI đỏ khi một màn hình
          // ⛔ có mục hướng dẫn. Giữ nhánh này để nó hỏng TO chứ ⛔ hỏng khẽ (quy tắc 16).
          <Typography.Text type="secondary">chưa có</Typography.Text>
        ),
    },
  ];

  return (
    <section id="man-hinh-cua-ban" className="sn-hd__sinh">
      <h2>Màn hình của bạn</h2>
      <Alert
        type="info"
        showIcon
        title={
          <>
            Danh sách này sinh từ phân quyền thật của <Tag>{tenBoLoc}</Tag>, không phải viết tay —
            nên nó luôn khớp với menu bên trái.{' '}
            <b>
              {hang.length}/{MAN_HINH.size}
            </b>{' '}
            màn hình.
          </>
        }
        style={{ marginBottom: 12 }}
      />
      <Table
        size="small"
        columns={cot}
        dataSource={hang}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </section>
  );
}

function timMuc(nhom: readonly NhomMuc[], duongDan: string): string | undefined {
  for (const n of nhom) {
    for (const m of [...n.con, n.muc]) {
      if (m.duongDan.includes(duongDan)) {
        return m.id;
      }
    }
  }
  return undefined;
}
