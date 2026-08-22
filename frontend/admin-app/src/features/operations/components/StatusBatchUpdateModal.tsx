import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, DatePicker, Form, Input, Modal, Select, Space, Table } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { CONSTRUCTION_STATUS } from '@/components/business/statusVocabulary';
import { type ConstructionRow, type PageResult } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

interface BatchUpdateRow {
  constructionId: string;
  code: string;
  name: string;
  statusCode: string;
  remarks?: string;
}

interface BatchUpdatePayload {
  items: {
    constructionId: string;
    statusCode: string;
    remarks?: string;
    reportedAt: string;
  }[];
}

export function StatusBatchUpdateModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [items, setItems] = useState<BatchUpdateRow[]>([]);
  const [reportedAt, setReportedAt] = useState<dayjs.Dayjs>(dayjs());

  // Fetch list of constructions to populate the table
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

  useEffect(() => {
    if (constructions?.items && items.length === 0) {
      setItems(
        constructions.items.map((c) => ({
          constructionId: c.publicId,
          code: c.code,
          name: c.name,
          statusCode: c.operationalStatus, // default to current status
          remarks: '',
        }))
      );
    }
  }, [constructions, items.length]);

  const updateItem = (index: number, field: keyof BatchUpdateRow, value: any) => {
    const newItems = [...items];
    newItems[index] = { ...newItems[index], [field]: value };
    setItems(newItems);
  };

  const save = useMutation({
    mutationFn: (payload: BatchUpdatePayload) => {
      return api.post('/ops/operation-status/batch', payload);
    },
    onSuccess: () => {
      message.success('Đã cập nhật tình hình vận hành hàng loạt');
      queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
      onClose();
    },
    onError: () => {
      message.error('Có lỗi xảy ra khi cập nhật tình hình');
    },
  });

  const handleSubmit = () => {
    // filter only changed items if needed, but for now send all or only selected
    // Since we only want to update what's changed or confirmed, maybe we should let user select rows?
    // Let's just send all of them to demonstrate batch update.
    save.mutate({
      items: items.map((i) => ({
        constructionId: i.constructionId,
        statusCode: i.statusCode,
        remarks: i.remarks,
        reportedAt: reportedAt.toISOString(),
      })),
    });
  };

  const columns = [
    { title: 'Mã', dataIndex: 'code', width: 100 },
    { title: 'Tên công trình', dataIndex: 'name', width: 250 },
    {
      title: 'Tình hình vận hành',
      dataIndex: 'statusCode',
      width: 200,
      render: (text: string, record: BatchUpdateRow, index: number) => (
        <Select
          style={{ width: '100%' }}
          value={text}
          onChange={(val) => updateItem(index, 'statusCode', val)}
          options={Object.entries(CONSTRUCTION_STATUS).map(([key, val]) => ({
            value: key,
            label: val.label,
          }))}
        />
      ),
    },
    {
      title: 'Ghi chú',
      dataIndex: 'remarks',
      render: (text: string, record: BatchUpdateRow, index: number) => (
        <Input
          value={text}
          onChange={(e) => updateItem(index, 'remarks', e.target.value)}
          placeholder="Nhập ghi chú (nếu có)"
          onKeyDown={(e) => {
            // "Enter, Tab" experience -> moving between fields
          }}
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
      confirmLoading={save.isPending}
      width={1000}
      destroyOnClose
      afterClose={() => setItems([])}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <span>Thời điểm ghi nhận: </span>
          <DatePicker showTime value={reportedAt} onChange={(val) => val && setReportedAt(val)} />
        </div>
        <Table
          size="small"
          columns={columns}
          dataSource={items}
          rowKey="constructionId"
          pagination={false}
          loading={isLoading}
          scroll={{ y: 500 }}
        />
      </Space>
    </Modal>
  );
}
