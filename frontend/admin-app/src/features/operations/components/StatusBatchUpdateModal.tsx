import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  DatePicker,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';

import {
  type ConstructionRow,
  type OperationStatusBatchItem,
  type OperationStatusCode,
  type PageResult,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';

import { dungPayloadNhapNhanh } from '../constructionRules';

/**
 * Nhập nhanh tình hình vận hành nhiều công trình — CN-02.11.
 *
 * Bản trước của màn hình này chưa từng ghi được một dòng nào, và hỏng ở năm chỗ độc lập:
 *
 * 1. Gọi `/ops/operation-status/batch` (số ít) trong khi backend phục vụ `/ops/operation-statuses`
 *    → mọi lượt bấm Lưu nhận 404.
 * 2. Gửi `constructionId` là UUID, backend đọc nó thành khoá nội bộ kiểu số.
 * 3. Tên trường lệch hết: `statusCode`/`remarks`/`reportedAt` ↔ `operationCode`/`note`/`effectiveAt`.
 * 4. ⛔ Ô chọn lấy từ `CONSTRUCTION_STATUS` — tức là **trạng thái dẫn xuất**, thứ mà quy tắc 4 cấm
 *    người dùng đặt tay. Đúng ra phải lấy từ danh mục `operation_status_codes` do Công ty quản lý.
 * 5. Gửi **toàn bộ** danh sách công trình ở mỗi lượt lưu, mỗi dòng mang sẵn trạng thái hiện tại —
 *    một lượt bấm sinh ra hàng trăm bản ghi nhật ký không ai nhập.
 *
 * Bản này chỉ gửi những dòng người dùng thực sự chọn mã.
 */
export function StatusBatchUpdateModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  /** publicId công trình → dòng đang nhập. Dòng không có trong map = không gửi. */
  const [draft, setDraft] = useState<
    Record<string, { code?: string; value?: string; note?: string }>
  >({});
  const [effectiveAt, setEffectiveAt] = useState<dayjs.Dayjs>(dayjs());

  const { data: constructions, isLoading } = useQuery({
    queryKey: ['ops', 'constructions', 'all'],
    queryFn: () =>
      api.get<PageResult<ConstructionRow>>('/ops/constructions', {
        page: 1,
        size: 1000,
        sort: 'name,asc',
      }),
    enabled: open,
  });

  // Danh mục mã — endpoint /active đòi `ops:operation-status:view`, đúng quyền của người trực ban.
  // Gọi đường quản trị danh mục ở đây là buộc phải cấp quyền quản trị cho toàn bộ người nhập liệu.
  const { data: codes } = useQuery({
    queryKey: ['ops', 'operation-status-codes', 'active'],
    queryFn: () => api.get<OperationStatusCode[]>('/ops/operation-status-codes/active'),
    enabled: open,
  });

  const codeByValue = useMemo(() => new Map((codes ?? []).map((c) => [c.code, c])), [codes]);

  // Dọn nháp ở `afterClose` của Modal chứ không ở useEffect theo dõi `open`: đặt state ngay trong
  // thân effect làm React dựng lại cây thêm một lượt, và eslint chặn đúng chỗ đó.
  const donNhap = () => {
    setDraft({});
    setEffectiveAt(dayjs());
  };

  const capNhat = (publicId: string, field: 'code' | 'value' | 'note', value?: string) => {
    setDraft((truoc) => {
      const dong = { ...(truoc[publicId] ?? {}), [field]: value };
      // Bỏ chọn mã thì xoá luôn giá trị kèm theo — giữ lại thì lần lưu sau gửi một tham số mồ côi.
      if (field === 'code' && !value) {
        return { ...truoc, [publicId]: {} };
      }
      return { ...truoc, [publicId]: dong };
    });
  };

  const items: OperationStatusBatchItem[] = useMemo(
    () => dungPayloadNhapNhanh(draft, effectiveAt.toISOString()),
    [draft, effectiveAt],
  );

  const save = useMutation({
    mutationFn: (payload: { items: OperationStatusBatchItem[] }) =>
      api.post('/ops/operation-statuses/batch', payload),
    onSuccess: () => {
      message.success(`Đã ghi nhận tình hình vận hành cho ${items.length} công trình`);
      queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
      queryClient.invalidateQueries({ queryKey: ['ops', 'operation-statuses'] });
      onClose();
    },
    // Không nuốt lỗi bằng một câu chung: apiClient đã tra error-map và hiện đúng mã lỗi
    // (OPS-2006 thiếu tham số, OPS-2018 mã đã ẩn, AUTH-3002 ngoài phạm vi đơn vị).
  });

  const handleSubmit = () => {
    if (items.length === 0) {
      message.warning('Chưa chọn tình hình vận hành cho công trình nào');
      return;
    }
    save.mutate({ items });
  };

  const columns = [
    { title: 'Mã', dataIndex: 'code', width: 110 },
    { title: 'Tên công trình', dataIndex: 'name', width: 240 },
    {
      title: 'Tình hình vận hành',
      key: 'operationCode',
      width: 220,
      render: (_: unknown, row: ConstructionRow) => (
        <Select
          style={{ width: '100%' }}
          allowClear
          placeholder="Chưa ghi nhận"
          value={draft[row.publicId]?.code}
          onChange={(val?: string) => capNhat(row.publicId, 'code', val)}
          options={(codes ?? []).map((c) => ({
            value: c.code,
            label: (
              <Space size={4}>
                <Tag color={c.colorHex} style={{ marginInlineEnd: 0 }}>
                  {c.code}
                </Tag>
                {c.name}
              </Space>
            ),
          }))}
        />
      ),
    },
    {
      title: 'Giá trị',
      key: 'parameterValue',
      width: 150,
      render: (_: unknown, row: ConstructionRow) => {
        const ma = codeByValue.get(draft[row.publicId]?.code ?? '');
        if (!ma?.hasParameter) {
          return <Typography.Text type="secondary">—</Typography.Text>;
        }
        return (
          <InputNumber
            style={{ width: '100%' }}
            min={0}
            // Chuỗi, không phải number: cột NUMERIC(10,2) ở CSDL, và số thực JS làm tròn sai
            // ở phần thập phân — quy tắc 2 cấm float cho mọi số đo.
            stringMode
            value={draft[row.publicId]?.value}
            onChange={(val) =>
              capNhat(row.publicId, 'value', val == null ? undefined : String(val))
            }
            addonAfter={ma.parameterUnit ?? undefined}
          />
        );
      },
    },
    {
      title: 'Ghi chú',
      key: 'note',
      render: (_: unknown, row: ConstructionRow) => (
        <Input
          value={draft[row.publicId]?.note}
          onChange={(e) => capNhat(row.publicId, 'note', e.target.value)}
          placeholder="Nhập ghi chú (nếu có)"
          disabled={!draft[row.publicId]?.code}
        />
      ),
    },
  ];

  return (
    <Modal
      title="Nhập nhanh tình hình vận hành"
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText={items.length > 0 ? `Ghi nhận ${items.length} công trình` : 'Ghi nhận'}
      confirmLoading={save.isPending}
      width={1100}
      destroyOnClose
      afterClose={donNhap}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Space>
          <span>Thời điểm có hiệu lực:</span>
          <DatePicker
            showTime
            format="DD/MM/YYYY HH:mm"
            value={effectiveAt}
            onChange={(val) => val && setEffectiveAt(val)}
            allowClear={false}
          />
          <Typography.Text type="secondary">
            Chỉ những dòng đã chọn mã mới được ghi. Cả lô ghi cùng lúc — một dòng lỗi thì không dòng
            nào được ghi.
          </Typography.Text>
        </Space>
        <Table<ConstructionRow>
          size="small"
          columns={columns}
          dataSource={constructions?.items ?? []}
          rowKey="publicId"
          pagination={false}
          loading={isLoading}
          scroll={{ y: 460 }}
        />
      </Space>
    </Modal>
  );
}
