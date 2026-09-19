import { DownloadOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, DatePicker, List, Space, Tag, Typography } from 'antd';
import { type Dayjs } from 'dayjs';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type MucBaoCaoView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { luuTep } from '@/shared/luuTep';

/**
 * Báo cáo vận hành công trình — CN-02.10 (WS-59).
 *
 * <h2>⛔⛔ Bốn mã BỎ VĨNH VIỄN hiện ra, và chúng ⛔ KHÔNG cùng nhãn với "chưa làm được"</h2>
 *
 * BC-01/02/03 mất nguồn (nhật ký vận hành loại khỏi phạm vi — B1/F1, xác nhận bởi G2), BC-04 mất
 * nguồn (kế hoạch vụ mùa — A1). Ẩn chúng đi thì câu hỏi *"BC-01 đâu?"* quay lại ở mọi lượt nghiệm
 * thu, mỗi lần lại phải đi tra tài liệu.
 *
 * ⚠ Và đây là **trạng thái khác** với `BCNS-07` của báo cáo nhân sự: mã kia *chưa làm được* (chờ
 * G6 — **sẽ** có), bốn mã ở đây *không bao giờ làm*. Nhãn phân biệt hai câu ấy.
 *
 * <h2>⛔ Kỳ báo cáo để trống = TOÀN BỘ dữ liệu, và bản CSV ghi rõ điều đó</h2>
 *
 * Một bản báo cáo ⛔ không ghi kỳ là một bản ⛔ không đối chiếu được với bản nào khác.
 */
export function BaoCaoVanHanhPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const [ky, setKy] = useState<[Dayjs, Dayjs] | null>(null);
  const coXuat = hasPermission('ops:report:export');

  const danhMuc = useQuery({
    queryKey: ['ops', 'bao-cao', 'danh-muc'],
    queryFn: () => api.get<MucBaoCaoView[]>('/ops/bao-cao/danh-muc'),
  });

  const tai = async (ma: string) => {
    try {
      const params = ky
        ? `?tu=${ky[0].format('YYYY-MM-DD')}&den=${ky[1].format('YYYY-MM-DD')}`
        : '';
      const { blob, tenTep } = await api.getTep(`/ops/bao-cao/xuat/${ma}${params}`);
      luuTep(blob, tenTep ?? `${ma}.csv`);
    } catch (caught: unknown) {
      message.error(caught instanceof ApiClientError ? caught.message : `Không tải được ${ma}`);
    }
  };

  return (
    <Card title="Báo cáo vận hành công trình" loading={danhMuc.isLoading}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="Bốn báo cáo đã bỏ khỏi phạm vi vẫn hiện ở đây"
          description={
            <>
              BC-01/02/03 và BC-04 <b>mất nguồn dữ liệu</b> sau khi nhật ký vận hành và kế hoạch vụ
              mùa được loại khỏi phạm vi. Chúng hiện ra kèm lý do thay vì biến mất, để câu hỏi
              “BC-01 đâu?” không phải tra lại tài liệu ở mỗi lượt nghiệm thu.
            </>
          }
        />

        <Space wrap>
          <Typography.Text>Kỳ báo cáo:</Typography.Text>
          <DatePicker.RangePicker
            format="DD/MM/YYYY"
            value={ky}
            onChange={(v) => setKy(v as [Dayjs, Dayjs] | null)}
          />
          <Typography.Text type="secondary">
            Để trống = toàn bộ dữ liệu. BC-10 không dùng kỳ (nó là ảnh chụp hiện trạng).
          </Typography.Text>
        </Space>

        <List
          dataSource={danhMuc.data ?? []}
          renderItem={(muc) => (
            <List.Item
              actions={[
                muc.khaDung ? (
                  <Button
                    key="tai"
                    icon={<DownloadOutlined />}
                    disabled={!coXuat}
                    onClick={() => void tai(muc.ma)}
                  >
                    {coXuat ? 'Tải CSV' : 'Không có quyền xuất'}
                  </Button>
                ) : (
                  <Button key="bo" icon={<StopOutlined />} disabled>
                    Đã bỏ khỏi phạm vi
                  </Button>
                ),
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Tag color={muc.khaDung ? 'blue' : 'default'}>{muc.ma}</Tag>
                    {muc.ten}
                  </Space>
                }
                description={
                  <>
                    <div>{muc.moTa}</div>
                    {/* ⛔⛔ Lý do hiện NGUYÊN VĂN, và nó phân biệt *bỏ vĩnh viễn* với *chưa làm
                        được*. Một dòng bị vô hiệu mà ⛔ không nói vì sao đọc như lỗi hệ thống. */}
                    {muc.lyDo ? <Typography.Text type="warning">{muc.lyDo}</Typography.Text> : null}
                  </>
                }
              />
            </List.Item>
          )}
        />

        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Tệp kết xuất ở định dạng <b>CSV</b>, mở trực tiếp bằng Excel. Bản in <b>PDF</b> chưa có:
          bố cục in của các báo cáo này còn chờ Công ty duyệt.
        </Typography.Paragraph>
      </Space>
    </Card>
  );
}
