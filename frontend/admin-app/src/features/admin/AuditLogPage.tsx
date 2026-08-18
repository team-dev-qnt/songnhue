import { SafetyOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Result,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { DataTable } from '@/components/DataTable';
import { usePagination } from '@/components/usePagination';
import { DateRangeFilter, type DateRange } from '@/components/business/DateRangeFilter';
import { type AuditLogView, type ChainBreak, type ChainVerification } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTimeWithSeconds, formatInteger } from '@/shared/format';

/**
 * Nhật ký kiểm toán + kiểm tra chuỗi hash — M5.4.
 *
 * <h3>Nút "Kiểm tra tính toàn vẹn" không phải trang trí</h3>
 *
 * `audit_logs` là bảng **chỉ ghi thêm**, mỗi dòng mang `hash = SHA-256(nội dung + hash
 * dòng trước)`, và chuỗi đó do **trigger trong CSDL** tính chứ không phải ứng dụng — nên
 * ngay cả khi ai đó chiếm được quyền ứng dụng cũng không giả được một chuỗi hợp lệ. Vai
 * trò `songnhue_app` cũng không có quyền `DELETE` trên bảng này.
 *
 * Kiểm chuỗi là cách trả lời câu hỏi "có ai sửa nhật ký không" bằng bằng chứng, thay vì
 * bằng niềm tin. Kết quả **rỗng nghĩa là nguyên vẹn**.
 */
export function AuditLogPage() {
  const { hasPermission } = useAuth();
  const pagination = usePagination(20);
  const [range, setRange] = useState<DateRange>({});
  const [keyword, setKeyword] = useState('');
  const [detail, setDetail] = useState<AuditLogView | null>(null);
  const [verification, setVerification] = useState<ChainVerification | null>(null);

  const logs = useQuery({
    queryKey: ['audit-logs', range, keyword, pagination.page, pagination.size],
    queryFn: () =>
      api.getPage<AuditLogView>('/audit-logs', {
        ...pagination.params,
        from: range.from,
        to: range.to,
        entityType: keyword || undefined,
        sort: 'seq,desc',
      }),
  });

  const verify = useMutation({
    mutationFn: () => api.post<ChainVerification>('/audit-logs/verify'),
    onSuccess: setVerification,
  });

  const columns: ColumnsType<AuditLogView> = [
    { title: 'STT', dataIndex: 'seq', width: 90, render: (seq: number) => formatInteger(seq) },
    {
      title: 'Thời điểm',
      dataIndex: 'occurredAt',
      width: 180,
      render: (value: string) => formatDateTimeWithSeconds(value),
    },
    {
      title: 'Người thực hiện',
      dataIndex: 'actorUsername',
      width: 150,
      render: (value: string | null) => value ?? <Tag>Hệ thống</Tag>,
    },
    {
      title: 'Hành động',
      dataIndex: 'action',
      width: 150,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: 'Đối tượng', dataIndex: 'entityType', width: 180 },
    { title: 'Địa chỉ IP', dataIndex: 'ipAddress', width: 140 },
    {
      title: '',
      key: 'chi-tiet',
      width: 100,
      render: (_value, row) => (
        <Button type="link" onClick={() => setDetail(row)}>
          Chi tiết
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="Nhật ký kiểm toán"
      extra={
        hasPermission('adm:audit:verify') && (
          <Button
            icon={<SafetyOutlined />}
            loading={verify.isPending}
            onClick={() => verify.mutate()}
          >
            Kiểm tra tính toàn vẹn
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <DateRangeFilter
          value={range}
          onChange={(next) => {
            setRange(next);
            pagination.reset();
          }}
        />
        <Input.Search
          allowClear
          placeholder="Lọc theo loại đối tượng, VD User"
          style={{ width: 260 }}
          onSearch={(value) => {
            setKeyword(value.trim());
            pagination.reset();
          }}
        />
      </Space>

      {/*
        Mặc định của backend là 30 ngày gần nhất, KHÔNG phải "tất cả": bảng phân mảnh theo
        tháng, thiếu điều kiện thời gian là truy vấn quét mọi mảnh. Nói rõ để người dùng
        không tưởng nhật ký chỉ có ngần ấy.
      */}
      {!range.from && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Đang xem 30 ngày gần nhất. Chọn khoảng thời gian để tra xa hơn."
        />
      )}

      <DataTable<AuditLogView>
        rows={logs.data?.items}
        meta={logs.data?.meta}
        loading={logs.isLoading}
        error={logs.error}
        rowKey="seq"
        columns={columns}
        scrollX={1100}
        onPageChange={pagination.onPageChange}
        emptyText="Không có bản ghi nào trong khoảng đã chọn"
      />

      <Modal
        open={detail !== null}
        title={`Bản ghi #${detail?.seq ?? ''}`}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail && (
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Thời điểm">
              {formatDateTimeWithSeconds(detail.occurredAt)}
            </Descriptions.Item>
            <Descriptions.Item label="Người thực hiện">
              {detail.actorUsername ?? 'Hệ thống'}
            </Descriptions.Item>
            <Descriptions.Item label="Mô-đun">{detail.module ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Đối tượng">
              {detail.entityType} #{detail.entityId ?? '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Mã tra cứu">{detail.traceId ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Giá trị cũ">
              <JsonBlock value={detail.oldValue} />
            </Descriptions.Item>
            <Descriptions.Item label="Giá trị mới">
              <JsonBlock value={detail.newValue} />
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      <Modal
        open={verification !== null}
        title="Kết quả kiểm tra chuỗi hash"
        onCancel={() => setVerification(null)}
        footer={null}
        width={720}
      >
        {verification && <VerificationResult result={verification} />}
      </Modal>

      {verify.error != null && (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 16 }}
          message={
            verify.error instanceof ApiClientError
              ? verify.error.message
              : 'Không kiểm tra được chuỗi'
          }
        />
      )}
    </Card>
  );
}

function VerificationResult({ result }: { result: ChainVerification }) {
  if (result.intact) {
    return (
      <Result
        status="success"
        title="Chuỗi nguyên vẹn"
        subTitle={`Đã kiểm ${formatInteger(result.totalRecords)} bản ghi (từ #${result.minSeq} đến #${result.maxSeq}). Không phát hiện dấu hiệu sửa đổi.`}
      />
    );
  }

  const columns: ColumnsType<ChainBreak> = [
    { title: 'Bản ghi', dataIndex: 'seq', width: 110 },
    {
      title: 'Thời điểm',
      dataIndex: 'occurredAt',
      width: 180,
      render: (value: string) => formatDateTimeWithSeconds(value),
    },
    { title: 'Dấu hiệu', dataIndex: 'reason' },
  ];

  return (
    <>
      <Alert
        type="error"
        showIcon
        style={{ marginBottom: 16 }}
        message="Phát hiện đứt gãy chuỗi hash"
        description="Nhật ký kiểm toán có dấu hiệu bị can thiệp. Báo ngay quản trị hệ thống và giữ nguyên hiện trạng — xem docs/runbook/su-kien-bao-mat.md."
      />
      <Table<ChainBreak>
        columns={columns}
        dataSource={result.breaks}
        rowKey="seq"
        pagination={false}
        size="small"
      />
    </>
  );
}

/** Giá trị cũ/mới lưu dạng JSON — in đẹp ra cho đọc được, hỏng thì hiện nguyên văn. */
function JsonBlock({ value }: { value: string | null }) {
  if (!value) {
    return <Typography.Text type="secondary">—</Typography.Text>;
  }
  let text = value;
  try {
    text = JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    // Không phải JSON hợp lệ thì hiện nguyên văn — vẫn hơn là giấu đi.
  }
  return (
    <Typography.Paragraph style={{ marginBottom: 0 }}>
      <pre style={{ margin: 0, maxHeight: 240, overflow: 'auto' }}>{text}</pre>
    </Typography.Paragraph>
  );
}
