import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Modal, Space, Table, Tag, Typography, Upload } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { type RcFile, type UploadChangeParam } from 'antd/es/upload';
import { useState } from 'react';

import { type ImportReport, type RowError } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

/**
 * ⚠ Trần số dòng backend đang ép — **một** con số cho toàn bộ giao diện.
 *
 * Bản sao thứ hai của `SpreadsheetReader.MAX_ROWS` (Java). Luật 14: chỗ nào con người phải nhớ hai
 * nơi thì chỗ đó cần một phép kiểm nhớ hộ — `importModal.test.tsx` đọc **thẳng tệp Java** và làm đỏ
 * khi hai bên lệch. ⛔ Đừng chép con số này vào từng nơi gọi.
 */
export const TRAN_DONG_NHAP = 5000;

/**
 * **Hộp thoại nhập tệp dùng chung** — một khuôn cho mọi màn hình nhập hàng loạt.
 *
 * ## ⭐ Vì sao dùng chung
 *
 * Tới 09/09/2026 chỉ danh mục công trình có màn hình nhập, và nó viết riêng. Mỗi mục dữ liệu bị
 * chặn ("chờ Công ty gửi danh sách") mà muốn có đường upload đều phải chép lại toàn bộ hộp thoại —
 * và bản chép sẽ lệch ở đúng nhánh ⛔ ít chạy nhất: nhánh **hiển thị lỗi từng dòng**. Đó chính là
 * nhánh đã chết ở bản đầu tiên (xem `onError` bên dưới).
 *
 * ⇒ Thêm một đường nhập mới nay là: khai `COT_MAU` ở backend + 3 endpoint + **một lời gọi component
 * này**. ⛔ Không viết lại giao diện nào.
 *
 * ## ⛔ Ba điều component này giữ, và đừng bỏ khi dùng lại
 *
 * 1. **Chạy khô trước, luôn luôn** — tệp do khách lập có hàng trăm dòng; nhập thẳng rồi phát hiện
 *    sai ở dòng 180 nghĩa là phải dọn tay 179 dòng đã vào.
 * 2. **Tệp mẫu do BACKEND sinh** — một tệp tĩnh trong `public/` sẽ lệch khỏi bộ đọc vào ngày ai đó
 *    thêm cột (luật 14).
 * 3. **Nhập thất bại ⇒ chạy lại xem trước** — backend NÉM khi còn dòng lỗi, nên nhánh `onSuccess`
 *    ⛔ không bao giờ thấy `errors`, và người dùng chỉ nhận một dòng toast ⛔ không nói dòng nào.
 */
export interface ImportModalProps {
  open: boolean;
  onClose: () => void;
  /** Tiêu đề hộp thoại, ví dụ "Nhập danh mục công trình từ tệp bảng tính". */
  title: string;
  /** Một câu mô tả việc nhập này làm gì — hiện ngay trên nút tải mẫu. */
  moTa: string;
  /** Đường dẫn API (không kèm `/api/v1`) của ba bước: xem trước · nhập thật · tải mẫu. */
  duongDan: { xemTruoc: string; nhap: string; mau: string };
  /** Tên tệp dự phòng khi phản hồi ⛔ không có `Content-Disposition`. */
  tenTepMau: string;
  /** Khoá cache cần làm mới sau khi nhập xong. */
  khoaCanLamMoi: readonly unknown[];
  /** Trần số dòng backend đang ép. Mặc định {@link TRAN_DONG_NHAP}. */
  tranDong?: number;
}

export function ImportModal({
  open,
  onClose,
  title,
  moTa,
  duongDan,
  tenTepMau,
  khoaCanLamMoi,
  tranDong = TRAN_DONG_NHAP,
}: ImportModalProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<RcFile | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);

  const xemTruoc = useMutation({
    mutationFn: (f: RcFile) => {
      const formData = new FormData();
      formData.append('file', f);
      return api.upload<ImportReport>(duongDan.xemTruoc, formData);
    },
    onSuccess: setReport,
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Lỗi xem trước tệp nhập');
      setReport(null);
    },
  });

  const nhap = useMutation({
    mutationFn: (f: RcFile) => {
      const formData = new FormData();
      formData.append('file', f);
      return api.upload<ImportReport>(duongDan.nhap, formData);
    },
    onSuccess: (data) => {
      // ⚠ Nhánh này gần như ⛔ không bao giờ chạy: backend NÉM khi còn dòng lỗi. Giữ lại vì nó vẫn
      //   là hợp đồng đúng của kiểu trả về — và ⛔ đừng đọc nó như "đã có màn hình cho lỗi từng
      //   dòng ở đường nhập thật": phần ấy nằm ở `onError` bên dưới.
      if (data.errors.length > 0) {
        setReport(data);
        message.error(`Nhập thất bại, có ${data.errors.length} lỗi`);
        return;
      }
      message.success(`Đã nhập thành công ${data.totalRows} dòng`);
      void queryClient.invalidateQueries({ queryKey: khoaCanLamMoi });
      dong();
    },
    // ⭐ Lượt nhập thật lập LẠI kế hoạch (cố ý — giữa hai lượt có thể có người vừa sửa dữ liệu), nên
    //   xem trước sạch mà nhập vẫn có thể đỏ. Chạy lại xem trước để bảng lỗi nói ra DÒNG NÀO.
    //   ⛔ Không phải phép thử lại: lượt xem trước ⛔ không ghi gì.
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Lỗi nhập dữ liệu');
      if (file) {
        xemTruoc.mutate(file);
      }
    },
  });

  /** ⛔ Không `window.open`: tab mới ⛔ không mang `Authorization`, người dùng nhận một tab trắng. */
  const taiMau = useMutation({
    mutationFn: async () => {
      const { blob, tenTep } = await api.getTep(duongDan.mau);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = tenTep ?? tenTepMau;
      a.click();
      URL.revokeObjectURL(url);
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tải được tệp mẫu');
    },
  });

  const dong = () => {
    setFile(null);
    setReport(null);
    onClose();
  };

  const doiTep = (info: UploadChangeParam) => {
    const chon =
      info.fileList.length > 0
        ? (info.fileList[info.fileList.length - 1].originFileObj as RcFile)
        : null;
    setFile(chon);
    if (chon) {
      xemTruoc.mutate(chon);
    } else {
      setReport(null);
    }
  };

  const cotLoi: ColumnsType<RowError> = [
    { title: 'Dòng', dataIndex: 'rowNumber', width: 80, align: 'center' },
    { title: 'Cột', dataIndex: 'column', width: 140, render: (v) => v || '-' },
    { title: 'Lỗi', dataIndex: 'message' },
  ];

  return (
    <Modal
      title={title}
      open={open}
      onCancel={dong}
      width={760}
      footer={[
        <Button key="huy" onClick={dong}>
          Huỷ
        </Button>,
        <Button
          key="nhap"
          type="primary"
          disabled={!file || !report || report.errors.length > 0}
          loading={nhap.isPending}
          onClick={() => file && nhap.mutate(file)}
        >
          Nhập dữ liệu
        </Button>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <div>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            {moTa} Tải tệp mẫu về để lấy đúng tên cột — dòng 2 của tệp mẫu mô tả quy cách từng ô,
            xoá dòng đó trước khi nhập.
          </Typography.Paragraph>
          <Button
            icon={<DownloadOutlined />}
            loading={taiMau.isPending}
            onClick={() => taiMau.mutate()}
          >
            Tải tệp mẫu (.csv)
          </Button>
        </div>

        {/*
          ⛔ `.xls` KHÔNG có ở đây: `SpreadsheetReader` nhận diện XLSX bằng chữ ký ZIP `PK\x03\x04`,
             còn `.xls` là định dạng OLE2 — nó rơi xuống nhánh đọc CSV và ra một dòng ký tự rác.
             Nhận một đuôi tệp mà bộ đọc ⛔ không đọc được là hứa rồi thất hứa.
          ⭐ `.csv` có ở đây vì bộ đọc xử lý CSV **đầy đủ** (RFC 4180, bỏ BOM) và tệp mẫu CHÍNH LÀ CSV.
        */}
        <Upload.Dragger
          accept=".xlsx,.csv"
          beforeUpload={() => false}
          onChange={doiTep}
          fileList={file ? [{ uid: '-1', name: file.name, status: 'done' }] : []}
          maxCount={1}
          disabled={xemTruoc.isPending || nhap.isPending}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">Nhấp hoặc kéo thả tệp vào đây</p>
          <p className="ant-upload-hint">
            Hỗ trợ .xlsx và .csv — tối đa {tranDong.toLocaleString('vi-VN')} dòng dữ liệu mỗi tệp.
            Định dạng .xls cũ không đọc được, hãy lưu lại thành .xlsx.
          </p>
        </Upload.Dragger>

        {xemTruoc.isPending && <Alert message="Đang kiểm tra tệp..." type="info" showIcon />}

        {!xemTruoc.isPending && report && (
          <div>
            <Typography.Title level={5}>Kết quả kiểm tra:</Typography.Title>
            <Space style={{ marginBottom: 16 }} wrap>
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
                  columns={cotLoi}
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

export default ImportModal;
