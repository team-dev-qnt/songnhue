import { PlusOutlined, FormOutlined, HistoryOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Space, Table, Typography, Tooltip } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { StatusBadge } from '@/components/business/StatusBadge';
import {
  CONSTRUCTION_STATUS,
  CONSTRUCTION_TYPE,
  MANAGEMENT_LEVEL,
} from '@/components/business/statusVocabulary';
import { type ConstructionRow, type PageResult } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

import { locTuDuongDan } from './constructionRules';
import { ConstructionChangeLogDrawer } from './components/ConstructionChangeLogDrawer';
import { ConstructionFilter, type ConstructionFilterValues } from './components/ConstructionFilter';
import { ConstructionImportModal } from './components/ConstructionImportModal';
import { ConstructionStatisticsBlock } from './components/ConstructionStatisticsBlock';
import { StatusBatchUpdateModal } from './components/StatusBatchUpdateModal';

export function ConstructionsPage() {
  const { hasPermission } = useAuth();
  const navigate = useNavigate();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState('updatedAt,desc');
  // ⭐ Nợ #71 — hai nửa của một chức năng, và nửa này từng thiếu.
  // Dashboard điều hành điều hướng sang `?status=SU_CO` / `?type=CONG` / `?level=XI_NGHIEP`. Trang
  // này trước đây KHÔNG đọc query string, nên lượt điều hướng mở ra một danh sách KHÔNG lọc — người
  // dùng bấm vào lát "Sự cố" và nhận về toàn bộ công trình. Không có lỗi nào báo ra, và triệu chứng
  // (danh sách hơi dài) trông y hệt dữ liệu thật.
  // Tên tham số cố ý trùng với tên ô lọc và với tham số của `GET /ops/constructions` — ba nơi cùng
  // một từ vựng thì không có chỗ nào để dịch sai.
  const [searchParams] = useSearchParams();
  const [filters, setFilters] = useState<ConstructionFilterValues>(() =>
    locTuDuongDan(searchParams),
  );

  const [batchModalOpen, setBatchModalOpen] = useState(false);
  const [historyDrawerId, setHistoryDrawerId] = useState<string | null>(null);
  const [importModalOpen, setImportModalOpen] = useState(false);

  const rivers = useQuery({
    queryKey: ['ops', 'constructions', 'rivers'],
    queryFn: () => api.get<string[]>('/ops/constructions/rivers'),
  });

  const constructions = useQuery({
    queryKey: ['ops', 'constructions', { page, size, sort, ...filters }],
    queryFn: () =>
      api.get<PageResult<ConstructionRow>>('/ops/constructions', {
        page,
        size,
        sort,
        ...filters,
      }),
  });

  const columns: ColumnsType<ConstructionRow> = [
    { title: 'Mã', dataIndex: 'code', width: 120 },
    {
      title: 'Tên công trình',
      dataIndex: 'name',
      render: (name: string, row) => (
        <a onClick={() => navigate(`/van-hanh/cong-trinh/${row.publicId}`)}>{name}</a>
      ),
    },
    {
      title: 'Loại',
      dataIndex: 'constructionType',
      width: 140,
      render: (val: string) => <StatusBadge value={val} vocabulary={CONSTRUCTION_TYPE} />,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'operationalStatus',
      width: 160,
      render: (val: string) => <StatusBadge value={val} vocabulary={CONSTRUCTION_STATUS} />,
    },
    {
      title: 'Cấp QL',
      dataIndex: 'managementLevel',
      width: 130,
      render: (val: string | null) =>
        val ? <StatusBadge value={val} vocabulary={MANAGEMENT_LEVEL} /> : '-',
    },
    { title: 'Đơn vị quản lý', dataIndex: 'orgUnitName', width: 200 },
    {
      title: 'Cập nhật lúc',
      dataIndex: 'updatedAt',
      width: 160,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 80,
      align: 'center',
      render: (_, row) => (
        <Tooltip title="Lịch sử thay đổi">
          <Button
            type="text"
            icon={<HistoryOutlined />}
            onClick={() => setHistoryDrawerId(row.publicId)}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Typography.Title level={4} style={{ margin: 0 }}>
        Hồ sơ công trình
      </Typography.Title>

      <ConstructionStatisticsBlock />

      <Card>
        <ConstructionFilter
          initialValues={filters}
          onFilter={(f) => {
            setFilters(f);
            setPage(1); // Reset page on filter
          }}
          rivers={rivers.data ?? []}
        />
      </Card>

      <Card
        extra={
          <Space>
            {/* Quyền phải khớp với endpoint mà nút này gọi (`POST /ops/operation-statuses/batch`),
                không phải quyền sửa hồ sơ công trình. Gate nhầm thì người có quyền sửa hồ sơ nhưng
                không được ghi tình hình vận hành vẫn thấy nút, bấm vào, và nhận 403. */}
            {hasPermission('ops:operation-status:update') && (
              <Button icon={<FormOutlined />} onClick={() => setBatchModalOpen(true)}>
                Nhập nhanh
              </Button>
            )}
            {hasPermission('ops:construction:create') && (
              <Button icon={<PlusOutlined />} onClick={() => setImportModalOpen(true)}>
                Nhập danh mục
              </Button>
            )}
            {hasPermission('ops:construction:create') && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => navigate('/van-hanh/cong-trinh/tao-moi')}
              >
                Thêm mới
              </Button>
            )}
          </Space>
        }
      >
        <Table<ConstructionRow>
          columns={columns}
          dataSource={constructions.data?.items ?? []}
          rowKey="publicId"
          loading={constructions.isLoading}
          scroll={{ x: 1200 }}
          pagination={{
            current: page,
            pageSize: size,
            total: constructions.data?.meta?.totalElements ?? 0,
            showSizeChanger: true,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
          onChange={(_pagination, _filters, sorter) => {
            if (!Array.isArray(sorter) && sorter.field) {
              const field = String(sorter.field);
              const order = sorter.order === 'ascend' ? 'asc' : 'desc';
              setSort(`${field},${order}`);
            }
          }}
        />
      </Card>

      <StatusBatchUpdateModal open={batchModalOpen} onClose={() => setBatchModalOpen(false)} />

      <ConstructionChangeLogDrawer
        publicId={historyDrawerId}
        open={!!historyDrawerId}
        onClose={() => setHistoryDrawerId(null)}
      />

      <ConstructionImportModal open={importModalOpen} onClose={() => setImportModalOpen(false)} />
    </Space>
  );
}
