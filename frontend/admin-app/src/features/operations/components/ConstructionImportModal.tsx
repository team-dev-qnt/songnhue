import { InboxOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Modal, Space, Table, Typography, Upload, Alert, Tag } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { type RcFile, type UploadChangeParam } from 'antd/es/upload';

import { type ImportReport, type RowError } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

interface Props {
  open: boolean;
  onClose: () => void;
}

export function ConstructionImportModal({ open, onClose }: Props) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<RcFile | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);

  const previewMutation = useMutation({
    mutationFn: (f: RcFile) => {
      const formData = new FormData();
      formData.append('file', f);
      return api.upload<ImportReport>('/ops/constructions/import/preview', formData);
    },
    onSuccess: (data) => {
      setReport(data);
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Lỗi xem trước tệp nhập');
      setReport(null);
    },
  });

  const importMutation = useMutation({
    mutationFn: (f: RcFile) => {
      const formData = new FormData();
      formData.append('file', f);
      return api.upload<ImportReport>('/ops/constructions/import', formData);
    },
    onSuccess: (data) => {
      if (data.errors.length > 0) {
        setReport(data);
        message.error(`Nhập thất bại, có ${data.errors.length} lỗi`);
      } else {
        message.success(`Đã nhập thành công ${data.totalRows} hồ sơ`);
        queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
        handleClose();
      }
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Lỗi nhập dữ liệu');
    },
  });

  const handleClose = () => {
    setFile(null);
    setReport(null);
    onClose();
  };

  const handleFileChange = (info: UploadChangeParam) => {
    // Only intercept the latest file
    let selectedFile: RcFile | null = null;
    if (info.fileList.length > 0) {
      selectedFile = info.fileList[info.fileList.length - 1].originFileObj as RcFile;
    }
    setFile(selectedFile);

    if (selectedFile) {
      previewMutation.mutate(selectedFile);
    } else {
      setReport(null);
    }
  };

  const errorColumns: ColumnsType<RowError> = [
    { title: 'Dòng', dataIndex: 'rowNumber', width: 80, align: 'center' },
    { title: 'Cột', dataIndex: 'column', width: 120, render: (val) => val || '-' },
    { title: 'Lỗi', dataIndex: 'message' },
  ];

  return (
    <Modal
      title="Nhập danh mục công trình từ Excel"
      open={open}
      onCancel={handleClose}
      width={700}
      footer={[
        <Button key="cancel" onClick={handleClose}>
          Huỷ
        </Button>,
        <Button
          key="import"
          type="primary"
          disabled={!file || !report || report.errors.length > 0}
          loading={importMutation.isPending}
          onClick={() => {
            if (file) {
              importMutation.mutate(file);
            }
          }}
        >
          Nhập dữ liệu
        </Button>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Typography.Paragraph type="secondary">
          Tải tệp Excel đúng biểu mẫu để nhập danh sách hồ sơ công trình vào hệ thống.
        </Typography.Paragraph>

        <Upload.Dragger
          accept=".xlsx,.xls"
          beforeUpload={() => false} // Do not auto upload
          onChange={handleFileChange}
          fileList={file ? [{ uid: '-1', name: file.name, status: 'done' }] : []}
          maxCount={1}
          disabled={previewMutation.isPending || importMutation.isPending}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">Nhấp hoặc kéo thả tệp vào đây</p>
          <p className="ant-upload-hint">Chỉ hỗ trợ tệp định dạng .xlsx, .xls</p>
        </Upload.Dragger>

        {previewMutation.isPending && <Alert message="Đang kiểm tra tệp..." type="info" showIcon />}

        {!previewMutation.isPending && report && (
          <div>
            <Typography.Title level={5}>Kết quả kiểm tra:</Typography.Title>
            <Space style={{ marginBottom: 16 }}>
              <Tag color="blue">Tổng cộng: {report.totalRows} dòng</Tag>
              <Tag color="green">Thêm mới: {report.toCreate}</Tag>
              <Tag color="orange">Cập nhật: {report.toUpdate}</Tag>
              {report.errors.length > 0 && <Tag color="red">Lỗi: {report.errors.length}</Tag>}
            </Space>

            {report.errors.length > 0 ? (
              <>
                <Alert
                  type="error"
                  message={`Không thể nhập dữ liệu vì có ${report.errors.length} lỗi`}
                  showIcon
                  style={{ marginBottom: 16 }}
                />
                <Table<RowError>
                  columns={errorColumns}
                  dataSource={report.errors}
                  rowKey={(r, i) => `${r.rowNumber}-${i}`}
                  pagination={false}
                  size="small"
                  scroll={{ y: 250 }}
                />
              </>
            ) : (
              <Alert type="success" message="Tệp hợp lệ, sẵn sàng để nhập." showIcon />
            )}
          </div>
        )}
      </Space>
    </Modal>
  );
}
