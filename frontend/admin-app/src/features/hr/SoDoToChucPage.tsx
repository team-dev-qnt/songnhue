import { DownloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Row, Space, Statistic, Typography, theme } from 'antd';
import { useMemo } from 'react';

import { BaseChart } from '@/components/charts/BaseChart';
import { api } from '@/shared/apiClient';

import { chieuCaoSoDo, optionSoDo, type SoDoView } from './soDoToChuc';
import { xuatSoDo } from './xuatSoDo';

/** Cây mở HẾT khi xuất tệp — xem `xuatSoDo`. */
const MO_HET = Number.MAX_SAFE_INTEGER;

/**
 * Sơ đồ tổ chức — CN-04.1 (SRS UC4.1, WS-58).
 *
 * <h2>⛔⛔ Mỗi nút mang HAI con số quân số, và cả hai đều cần</h2>
 *
 * Chỉ **trực tiếp** ⇒ một Xí nghiệp có 4 Tổ đội hiện **0 người** khi thu gọn nhánh. Chỉ **cả
 * nhánh** ⇒ tổng các nút con ⛔ không bằng nút cha và ⛔ không có chỗ nào nói vì sao. Nhãn hiện
 * `trực tiếp / cả nhánh` khi hai số khác nhau, một số khi chúng bằng nhau.
 *
 * <h2>⛔ Sơ đồ ⛔ KHÔNG cắt theo phạm vi đơn vị</h2>
 *
 * Quân số đếm toàn Công ty (`QuanSoRepository` đi JDBC thuần, ngoài tầm `@Filter`). Cắt theo phạm
 * vi thì mỗi người mở ra thấy **một bộ số khác nhau**, tất cả đều trông hợp lý và ⛔ không có gì
 * báo — `quanSoKhongCatTheoPhamViDonVi` canh đúng vế ấy.
 *
 * <h2>⛔ Trang này ⛔ KHÔNG sửa được cây</h2>
 *
 * Kéo–thả di chuyển nhánh, đổi tên, giải thể nằm ở **Quản trị › Sơ đồ đơn vị**
 * (`adm:org-unit:manage`). `hr:org-chart:view` được cấp cho 3 vai trò chỉ để **xem** — cho nó sửa
 * là cho ba vai trò ấy dựng lại cây tổ chức của cả Công ty.
 */
export function SoDoToChucPage() {
  const { token } = theme.useToken();
  const query = useQuery({
    queryKey: ['hr', 'so-do-to-chuc'],
    queryFn: () => api.get<SoDoView>('/hr/so-do-to-chuc'),
  });

  // ⚠ `?? []` dựng một MẢNG MỚI mỗi lượt render, nên nó ⛔ không dùng thẳng làm phụ thuộc của
  //   `useMemo` được — `option` sẽ tính lại mỗi lượt, và `BaseChart` gọi `setOption` mỗi lượt ấy
  //   (biểu đồ nhấp nháy, cây tự đóng lại sau mỗi lần người dùng mở một nhánh).
  const goc = useMemo(() => query.data?.goc ?? [], [query.data]);
  const option = useMemo(() => optionSoDo(goc), [goc]);
  const cao = useMemo(() => chieuCaoSoDo(goc), [goc]);

  const xuat = (dinhDang: 'png' | 'svg') => {
    // ⛔ Dựng option RIÊNG cho lượt xuất, cây mở HẾT — bản trên màn hình đang thu gọn từ cấp 3,
    //   và một tệp thiếu nhánh là thứ người nhận ⛔ không có cách nào biết là thiếu.
    xuatSoDo(
      optionSoDo(goc, MO_HET) as unknown as Record<string, unknown>,
      dinhDang,
      1600,
      Math.max(cao, 600),
      'so-do-to-chuc',
      token.colorBgContainer,
    );
  };

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={6}>
            <Statistic title="Tổng quân số" value={query.data?.tongNhanSu ?? '—'} suffix="người" />
          </Col>
          <Col xs={12} md={6}>
            <Statistic title="Số đơn vị" value={query.data?.soDonVi ?? '—'} />
          </Col>
        </Row>
      </Card>

      {/* ⛔⛔ Bình thường bằng 0. Khác 0 ⇒ có hồ sơ trỏ vào một đơn vị ⛔ không còn trên sơ đồ, và
          tổng quân số ở trên đang THIẾU đúng những người ấy. Im lặng ở đây là để một con số sai
          đứng trên màn hình Ban giám đốc mà ⛔ không ai đếm lại (quy tắc 16). */}
      {query.data && query.data.soNhanSuNgoaiSoDo > 0 ? (
        <Alert
          type="error"
          showIcon
          title={`${query.data.soNhanSuNgoaiSoDo} hồ sơ đang thuộc một đơn vị không còn trên sơ đồ`}
          description={
            <>
              Tổng quân số ở trên <b>chưa tính</b> những người này. Nguyên nhân thường gặp: một đơn
              vị bị xoá trong khi hồ sơ vẫn trỏ vào nó. Hãy mở <b>Quản trị › Sơ đồ đơn vị</b> và
              chuyển họ sang đơn vị khác.
            </>
          }
        />
      ) : null}

      <Card
        title="Sơ đồ tổ chức"
        extra={
          <Space>
            <Button
              icon={<DownloadOutlined />}
              disabled={goc.length === 0}
              onClick={() => xuat('png')}
            >
              Xuất PNG
            </Button>
            <Button
              icon={<DownloadOutlined />}
              disabled={goc.length === 0}
              onClick={() => xuat('svg')}
            >
              Xuất SVG
            </Button>
          </Space>
        }
      >
        <BaseChart
          option={option as unknown as Record<string, unknown>}
          empty={goc.length === 0}
          loading={query.isLoading}
          height={cao}
          emptyText="Chưa có đơn vị nào trong cây tổ chức"
        />

        <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
          Nhãn mỗi nút: <b>tên đơn vị</b> · <b>người đứng đầu</b> · <b>quân số</b>. Khi hai số quân
          số khác nhau, số trước là người thuộc <b>đúng</b> đơn vị đó, số sau là <b>cả nhánh</b>.
          Nhánh từ cấp 3 trở xuống được thu gọn sẵn — bấm vào nút để mở.
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Bản xuất luôn <b>mở hết</b> mọi nhánh, không phụ thuộc bạn đang thu gọn nhánh nào. Chưa
          xuất được <b>PDF A3</b> và <b>Excel</b>: hệ thống chưa có bộ kết xuất cho hai định dạng
          ấy, và chụp ảnh sơ đồ rồi đóng vào tệp PDF sẽ cho một bản in vỡ chữ ở khổ A3.
        </Typography.Paragraph>
      </Card>
    </Space>
  );
}
