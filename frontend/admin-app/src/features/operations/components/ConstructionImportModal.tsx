import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
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
      // ⚠ Nhánh này gần như ⛔ không bao giờ chạy: backend NÉM `OPS-2016` khi còn dòng lỗi, nên
      //   lượt nhập có lỗi rơi vào `onError`. Giữ lại vì nó vẫn là hợp đồng đúng của kiểu trả về —
      //   và ⛔ đừng đọc nó như "đã có màn hình cho lỗi từng dòng ở đường nhập thật": ⛔ chưa có,
      //   phần ấy nằm ở `onError` bên dưới.
      if (data.errors.length > 0) {
        setReport(data);
        message.error(`Nhập thất bại, có ${data.errors.length} lỗi`);
      } else {
        message.success(`Đã nhập thành công ${data.totalRows} hồ sơ`);
        queryClient.invalidateQueries({ queryKey: ['ops', 'constructions'] });
        handleClose();
      }
    },
    // ⭐ Lượt nhập thật lập LẠI kế hoạch, ⛔ không dùng kế hoạch của lượt xem trước — cố ý, vì giữa
    //   hai lượt có thể có người vừa thêm một công trình trùng mã. Hệ quả: xem trước sạch mà nhập
    //   vẫn có thể đỏ, và tới 09/09/2026 người dùng chỉ nhận **một dòng toast** cho trường hợp ấy —
    //   bảng lỗi từng dòng ngay bên dưới ⛔ không bao giờ được vẽ.
    // ⇒ Chạy lại xem trước để bảng lỗi nói ra DÒNG NÀO. Không phải phép thử lại: nó ⛔ không ghi gì.
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Lỗi nhập dữ liệu');
      if (file) {
        previewMutation.mutate(file);
      }
    },
  });

  /**
   * ⭐ Tệp mẫu do BACKEND sinh từ danh mục cột mà chính bộ đọc dùng.
   *
   * ⛔ Không `window.open`: tab mới ⛔ không mang `Authorization`, người dùng nhận một tab trắng.
   */
  const taiMauMutation = useMutation({
    mutationFn: async () => {
      const { blob, tenTep } = await api.getTep('/ops/constructions/import/template');
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = tenTep ?? 'mau-nhap-danh-muc-cong-trinh.csv';
      a.click();
      URL.revokeObjectURL(url);
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được tệp mẫu');
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
      title="Nhập danh mục công trình từ tệp bảng tính"
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
        <div>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            Nhập danh sách hồ sơ công trình từ tệp bảng tính. Tải tệp mẫu về để lấy đúng tên cột —
            dòng 2 của tệp mẫu mô tả quy cách từng ô, xoá dòng đó trước khi nhập.
          </Typography.Paragraph>
          <Button
            icon={<DownloadOutlined />}
            loading={taiMauMutation.isPending}
            onClick={() => taiMauMutation.mutate()}
          >
            Tải tệp mẫu (.csv)
          </Button>
        </div>

        {/*
          ⛔ `.xls` KHÔNG có ở đây dù bản cũ nhận nó: `SpreadsheetReader` nhận diện XLSX bằng chữ ký
             ZIP `PK\x03\x04`, còn `.xls` là định dạng OLE2 — nó rơi xuống nhánh đọc CSV và ra một
             dòng ký tự rác. Nhận một đuôi tệp mà bộ đọc ⛔ không đọc được là hứa rồi thất hứa.
          ⭐ `.csv` có ở đây vì bộ đọc xử lý CSV **đầy đủ** (RFC 4180, bỏ BOM) và tệp mẫu CHÍNH LÀ
             CSV — bản cũ chặn đúng định dạng mà nó vừa phát ra.
        */}
        <Upload.Dragger
          accept=".xlsx,.csv"
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
          <p className="ant-upload-hint">
            Hỗ trợ .xlsx và .csv — tối đa 5.000 dòng dữ liệu mỗi tệp. Định dạng .xls cũ không đọc
            được, hãy lưu lại thành .xlsx.
          </p>
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
                  scroll={{ x: 560, y: 250 }}
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
