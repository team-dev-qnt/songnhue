import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  DatePicker,
  Form,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { type ConstructionDocument, type ConstructionDocumentList } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatBytes, formatDateTime } from '@/shared/format';

/**
 * Tài liệu của một công trình — CN-02.3, T21.4.
 *
 * ⛔ **Không có "khối đính kèm dùng chung".** Từng có `components/business/AttachmentPanel.tsx`;
 * nó **đã bị xoá 04/09/2026** (nợ T27.29) sau khi đo được 0 nơi import. Component ấy nhận
 * `ownerId: number` và gọi `GET /attachments?ownerId=…`, nơi backend khai
 * `@RequestParam Long ownerId`. Bản trước của màn hình này truyền `publicId` (UUID) qua một lượt ép
 * kiểu `as unknown as number`, kèm chú thích tự hỏi *"backend expects UUID for construction?"* —
 * nghĩa là Spring không bind được và **mọi lượt mở tab trả 400**. Tab hiện ra, bảng rỗng, không có
 * gì báo sai.
 *
 * MOD-02 đã có sẵn bộ endpoint riêng đi bằng `publicId` và mang đúng ba quyền `ops:document:*`
 * (xem / tải lên / xoá) thay vì mượn `ops:construction:update`. Dùng đúng nó.
 */

/** Hạn mức mỗi công trình — khớp `attachments.owner-quota` phía backend (500MB). */
const HAN_MUC_BYTE = 500 * 1024 * 1024;

const LOAI_TAI_LIEU = [
  { value: 'HO_SO_THIET_KE', label: 'Hồ sơ thiết kế' },
  { value: 'BAN_VE_HOAN_CONG', label: 'Bản vẽ hoàn công' },
  { value: 'BIEN_BAN_NGHIEM_THU', label: 'Biên bản nghiệm thu' },
  { value: 'GIAY_PHEP', label: 'Giấy phép' },
  { value: 'ANH_HIEN_TRANG', label: 'Ảnh hiện trạng' },
  { value: 'KHAC', label: 'Khác' },
];

const NHAN_LOAI = new Map(LOAI_TAI_LIEU.map((l) => [l.value, l.label]));

export function ConstructionDocumentsPanel({ publicId }: { publicId: string }) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<{
    docType: string;
    issuedDate?: dayjs.Dayjs;
    expiryDate?: dayjs.Dayjs;
  }>();
  const [dangTai, setDangTai] = useState(false);

  const queryKey = ['ops', 'constructions', publicId, 'documents'];

  const { data, isLoading } = useQuery({
    queryKey,
    queryFn: () => api.get<ConstructionDocumentList>(`/ops/constructions/${publicId}/documents`),
  });

  const xoa = useMutation({
    mutationFn: (attachmentId: string) =>
      api.delete<void>(`/ops/constructions/${publicId}/documents/${attachmentId}`),
    onSuccess: () => {
      message.success('Đã xoá tài liệu');
      queryClient.invalidateQueries({ queryKey });
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được tài liệu'),
  });

  const taiVe = async (doc: ConstructionDocument) => {
    // Đường dẫn tải có hạn, cấp riêng từng lượt — không nhúng sẵn vào bảng. Nhúng sẵn thì mỗi lần
    // mở tab là sinh N đường dẫn còn sống, phần lớn không ai bấm.
    const { url } = await api.get<{ url: string }>(
      `/ops/constructions/${publicId}/documents/${doc.publicId}/download-url`,
    );
    window.open(url, '_blank', 'noopener');
  };

  const daDung = data?.usedBytes ?? 0;
  const phanTram = Math.min(100, Math.round((daDung / HAN_MUC_BYTE) * 100));

  const columns: ColumnsType<ConstructionDocument> = [
    {
      title: 'Tên tệp',
      dataIndex: 'originalName',
      render: (ten: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{ten}</span>
          {row.fileVersion > 1 && <Tag color="blue">Phiên bản {row.fileVersion}</Tag>}
        </Space>
      ),
    },
    {
      title: 'Loại',
      dataIndex: 'docType',
      width: 180,
      render: (loai: string) => NHAN_LOAI.get(loai) ?? loai,
    },
    {
      title: 'Dung lượng',
      dataIndex: 'sizeBytes',
      width: 110,
      align: 'right',
      render: (bytes: number) => formatBytes(bytes),
    },
    {
      title: 'Ngày ban hành',
      dataIndex: 'issuedDate',
      width: 130,
      render: (ngay: string | null) => (ngay ? dayjs(ngay).format('DD/MM/YYYY') : '—'),
    },
    {
      title: 'Hết hiệu lực',
      dataIndex: 'expiryDate',
      width: 130,
      render: (ngay: string | null) => {
        if (!ngay) return '—';
        const hetHan = dayjs(ngay).isBefore(dayjs(), 'day');
        return (
          <Typography.Text type={hetHan ? 'danger' : undefined}>
            {dayjs(ngay).format('DD/MM/YYYY')}
            {hetHan && ' (đã hết)'}
          </Typography.Text>
        );
      },
    },
    {
      title: 'Tải lên lúc',
      dataIndex: 'uploadedAt',
      width: 160,
      render: (luc: string) => formatDateTime(luc),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      align: 'center',
      render: (_, row) => (
        <Space>
          {/* `downloadable` do backend quyết định — tệp chưa quét virus xong thì chưa cho tải.
              Ẩn nút thay vì để bấm rồi nhận lỗi. */}
          {row.downloadable && hasPermission('ops:document:view') && (
            <Button type="text" icon={<DownloadOutlined />} onClick={() => void taiVe(row)} />
          )}
          {hasPermission('ops:document:delete') && (
            <Popconfirm
              title="Xoá tài liệu này?"
              description="Tệp được xoá mềm, vẫn truy vết được trong nhật ký."
              onConfirm={() => xoa.mutate(row.publicId)}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space direction="vertical" size={0} style={{ minWidth: 260 }}>
          <Typography.Text type="secondary">
            Đã dùng {formatBytes(daDung)} / {formatBytes(HAN_MUC_BYTE)}
          </Typography.Text>
          <Progress
            percent={phanTram}
            size="small"
            status={phanTram >= 90 ? 'exception' : 'normal'}
          />
        </Space>

        {hasPermission('ops:document:upload') && (
          <Form form={form} layout="inline" initialValues={{ docType: 'HO_SO_THIET_KE' }}>
            <Form.Item name="docType" label="Loại">
              <Select style={{ width: 190 }} options={LOAI_TAI_LIEU} />
            </Form.Item>
            <Form.Item name="issuedDate" label="Ban hành">
              <DatePicker format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item name="expiryDate" label="Hết hiệu lực">
              <DatePicker format="DD/MM/YYYY" />
            </Form.Item>
            <Form.Item>
              <Upload
                showUploadList={false}
                customRequest={({ file, onSuccess, onError }) => {
                  const values = form.getFieldsValue();
                  // `docType` / ngày đi ở query string, tệp đi ở multipart — đúng chữ ký của
                  // endpoint. Trộn hết vào multipart thì Spring không bind được các tham số này.
                  const query = new URLSearchParams({ docType: values.docType });
                  if (values.issuedDate)
                    query.set('issuedDate', values.issuedDate.format('YYYY-MM-DD'));
                  if (values.expiryDate)
                    query.set('expiryDate', values.expiryDate.format('YYYY-MM-DD'));

                  const body = new FormData();
                  body.append('file', file as File);

                  setDangTai(true);
                  api
                    .upload<ConstructionDocument>(
                      `/ops/constructions/${publicId}/documents?${query.toString()}`,
                      body,
                    )
                    .then((result) => {
                      message.success('Đã tải tài liệu lên');
                      queryClient.invalidateQueries({ queryKey });
                      onSuccess?.(result);
                    })
                    .catch((error: unknown) => onError?.(error as Error))
                    .finally(() => setDangTai(false));
                }}
              >
                <Button icon={<UploadOutlined />} loading={dangTai}>
                  Tải tài liệu
                </Button>
              </Upload>
            </Form.Item>
          </Form>
        )}
      </Space>

      <Table<ConstructionDocument>
        size="small"
        columns={columns}
        dataSource={data?.items ?? []}
        rowKey="publicId"
        loading={isLoading}
        // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
        // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
        scroll={{ x: 1010 }}
        pagination={false}
        locale={{ emptyText: 'Chưa có tài liệu nào cho công trình này' }}
      />
    </Space>
  );
}
