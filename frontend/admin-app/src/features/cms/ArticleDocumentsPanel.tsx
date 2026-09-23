import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  PaperClipOutlined,
} from '@ant-design/icons';
import { App, Button, Card, Empty, Input, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';

import { XemTruocTep } from '@/components/business/XemTruocTep';
import { xemTruocDuoc } from '@/components/business/xemTruocDuoc';
import { ApiClientError } from '@/shared/apiClient';
import { formatBytes } from '@/shared/format';

import { cmsApi } from './api';
import { type ArticleDocumentView } from './types';

/**
 * Khối "Tài liệu đính kèm" của màn hình soạn bài — WS-40, CN-01.1
 * (*"Tệp đính kèm | File | Nhiều tệp (pdf, docx, xlsx…)"*).
 *
 * <h3>⛔ Vì sao không có một khối đính kèm "dùng chung"</h3>
 *
 * Từng có `components/business/AttachmentPanel.tsx`, và nó **đã bị xoá 04/09/2026** (nợ T27.29):
 * 0 nơi import, nhận `ownerId: number` trong khi CMS lẫn MOD-02 đều đi bằng UUID, và gọi thẳng
 * `POST`/`DELETE /api/v1/attachments` — đúng cặp endpoint chung mà A1 vừa vá quyền. Một component
 * không ai dùng nhưng **dùng được**, trỏ vào đường rộng nhất, là một lời mời.
 *
 * <p>Khối này còn khác về bản chất: nó **không tự tải lên và không tự lưu**. Nó chỉ sửa một danh sách trong bộ nhớ, và danh
 * sách ấy đi cùng lượt bấm **Lưu** của cả bài — vì tài liệu là *nội dung*, phải qua đúng quy
 * trình duyệt như tiêu đề và nội dung bài.
 *
 * <h3>Sắp thứ tự bằng hai nút, không kéo-thả</h3>
 *
 * Kéo-thả cần một thư viện nữa, và số tài liệu một bài đếm bằng đơn vị. Hai nút mũi tên làm đúng
 * việc ấy, không thêm phụ thuộc, và dùng được bằng bàn phím — thứ kéo-thả thường bỏ quên.
 */
export interface ArticleDocumentsPanelProps {
  documents: ArticleDocumentView[];
  onChange: (documents: ArticleDocumentView[]) => void;
  onPick: () => void;
  disabled?: boolean;
}

/**
 * Tên gọi được của một tài liệu đính kèm — dùng cho `aria-label` của ba nút chỉ-có-icon (T63.9).
 *
 * ⚠ `label` là *"tên gợi nhớ, `null` = chưa đặt"* (xem `ArticleDocumentView`), nên rơi về
 * `originalName` đúng như cổng công khai làm. ⛔ Sinh một nhãn mặc định kiểu *"Tài liệu 1"*: trình
 * đọc màn hình sẽ đọc ba nút của ba hàng khác nhau thành ba câu ⛔ phân biệt được — tức đúng cái
 * khuyết tật T63.9 sinh ra để bắt, chỉ đổi từ *"⛔ có tên"* sang *"có tên mà vô nghĩa"*.
 */
function tenTaiLieu(doc: ArticleDocumentView): string {
  return doc.label ?? doc.originalName;
}

export function ArticleDocumentsPanel({
  documents,
  onChange,
  onPick,
  disabled = false,
}: ArticleDocumentsPanelProps) {
  const { message } = App.useApp();
  /**
   * ⛔ Hộp thoại xem trước chỉ TỒN TẠI khi đang mở (`xemTruoc ? <…/> : null`), ⛔ dựng sẵn rồi
   * bật/tắt bằng `open`: khi ấy `<iframe>` của tệp A còn trong DOM lúc mở tệp B — và URL ấy sống
   * 10 phút, ⛔ đi kèm phiên đăng nhập. Cùng cơ chế tường minh đã vá T51.12 · T53.7.
   */
  const [xemTruoc, setXemTruoc] = useState<ArticleDocumentView | null>(null);
  const [dangTai, setDangTai] = useState<string | null>(null);

  /**
   * Tải một tệp về máy.
   *
   * ⚠ Mở presigned URL ở tab mới chứ ⛔ `luuTep`: URL ấy đã mang sẵn
   * `Content-Disposition: attachment; filename=…` do máy chủ **ký cùng chữ ký** (T40.27), nên
   * trình duyệt tự lưu đúng tên gốc. Kéo byte về rồi dựng Blob là đi qua tiến trình Node/máy khách
   * một lần nữa ⛔ để làm gì. Cùng khuôn nút Tải của `HoSoConDrawer`.
   */
  const taiVe = async (doc: ArticleDocumentView) => {
    setDangTai(doc.publicId);
    try {
      const { url } = await cmsApi.fileUrl(doc.publicId);
      window.open(url, '_blank', 'noopener');
    } catch (caught) {
      // ⛔ Thiếu nhánh này thì bấm Tải ⇒ ⛔ có gì xảy ra và ⛔ có gì báo (`moiLuotGhiPhaiBaoLoi`).
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không lấy được đường dẫn tải',
      );
    } finally {
      setDangTai(null);
    }
  };

  const doiCho = (i: number, buoc: number) => {
    const j = i + buoc;
    if (j < 0 || j >= documents.length) {
      return;
    }
    const ketQua = [...documents];
    [ketQua[i], ketQua[j]] = [ketQua[j], ketQua[i]];
    onChange(ketQua);
  };

  return (
    <Card
      size="small"
      title="Tài liệu đính kèm"
      style={{ marginBottom: 16 }}
      extra={
        <Button size="small" icon={<PaperClipOutlined />} disabled={disabled} onClick={onPick}>
          Chọn từ Kho tài liệu
        </Button>
      }
    >
      {documents.length === 0 ? (
        // ⛔ Rỗng thì nói rỗng — không dựng sẵn một dòng giữ chỗ trông như đã có dữ liệu
        //    (quy tắc 16). Câu này còn nói ra ràng buộc mà người biên tập cần biết TRƯỚC.
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Chưa đính tệp nào. Tài liệu chỉ tải về được từ cổng sau khi bài đã xuất bản.
            </Typography.Text>
          }
        />
      ) : (
        <Table<ArticleDocumentView>
          size="small"
          rowKey="publicId"
          pagination={false}
          dataSource={documents}
          // ⭐ Hai cột cố định cộng lại 370px (Tệp 260 + nút 110). Cột "Tên hiển thị" mang một
          //   `<Input>`, cột "Tệp" mang TÊN TỆP GỐC — hai chuỗi dài nhất của bảng này. Không khai
          //   `scroll.x` thì `rc-table` chạy `tableLayout: 'auto'` và `overflow-wrap: break-word`
          //   bóp tên tệp về một ký tự mỗi dòng thay vì cuộn ngang (bộ canh `bangCuonNgang`).
          //   720 = 370 cố định + 350 tối thiểu cho ô nhập nhãn.
          //   ⚠ 790 = 720 + 70: cột thao tác đi từ 110 lên 180 khi thêm hai nút (T84.7). Quên
          //   cộng là `bangCuonNgang` đỏ, và nếu bộ canh ấy ⛔ có thì tên tệp bị bóp một ký tự/dòng.
          scroll={{ x: 790 }}
          columns={[
            {
              title: 'Tên hiển thị trên cổng',
              dataIndex: 'label',
              render: (_: unknown, doc, index) => (
                <Input
                  size="small"
                  disabled={disabled}
                  value={doc.label ?? ''}
                  // ⭐ Rỗng ⇒ `null`, KHÔNG phải chuỗi rỗng: `null` nghĩa là *chưa đặt*, và cổng
                  //   rơi về tên gốc. Để `''` lọt xuống là dựng ra một trạng thái thứ ba mà cả
                  //   hai phía đều phải nhớ xử lý.
                  onChange={(e) => {
                    const ketQua = [...documents];
                    ketQua[index] = {
                      ...doc,
                      label: e.target.value.trim() === '' ? null : e.target.value,
                    };
                    onChange(ketQua);
                  }}
                  placeholder={doc.originalName}
                />
              ),
            },
            {
              title: 'Tệp',
              dataIndex: 'originalName',
              width: 260,
              // ⚠ Tên gốc LUÔN hiện, kể cả khi đã đặt nhãn: ba dòng cùng mang chữ "Xem quyết
              //   định ở đây" thì không truy được cái nào là cái nào.
              render: (_: unknown, doc) => (
                <Space size={4} wrap>
                  <Typography.Text style={{ fontSize: 12 }} ellipsis>
                    {doc.originalName}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                    {formatBytes(doc.sizeBytes)}
                  </Typography.Text>
                  {/* Trạng thái quét hiện THẬT, không giấu: tệp chưa quét xong sẽ không ra cổng,
                      và người biên tập phải thấy điều đó trước khi gửi duyệt. */}
                  {!doc.downloadable && (
                    <Tag color="orange" style={{ margin: 0 }}>
                      đang quét
                    </Tag>
                  )}
                </Space>
              ),
            },
            {
              title: '',
              width: 180,
              render: (_: unknown, row: ArticleDocumentView, index) => (
                <Space size={0}>
                  {/* ⛔⛔ ẨN HẲN khi tệp chưa quét xong, ⛔ `disabled`. Tiền lệ ba panel
                      (`MaintenanceAttachmentsPanel:110`, `ConstructionDocumentsPanel:156`): một nút
                      xám mà bấm vào vẫn ra 409 `SYS-0009` được người dùng đọc là *hệ thống hỏng*.
                      Danh sách loại xem trước được do `xemTruocDuoc` quyết — ⛔ suy từ đuôi tên tệp. */}
                  {xemTruocDuoc(row.contentType, row.downloadable) && (
                    <Button
                      type="text"
                      size="small"
                      icon={<EyeOutlined />}
                      aria-label={`Xem trước "${tenTaiLieu(row)}"`}
                      onClick={() => setXemTruoc(row)}
                    />
                  )}
                  {row.downloadable && (
                    <Button
                      type="text"
                      size="small"
                      icon={<DownloadOutlined />}
                      aria-label={`Tải "${tenTaiLieu(row)}" về máy`}
                      loading={dangTai === row.publicId}
                      onClick={() => void taiVe(row)}
                    />
                  )}
                  <Button
                    type="text"
                    size="small"
                    icon={<ArrowUpOutlined />}
                    aria-label={`Đưa "${tenTaiLieu(row)}" lên trên`}
                    disabled={disabled || index === 0}
                    onClick={() => doiCho(index, -1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<ArrowDownOutlined />}
                    aria-label={`Đưa "${tenTaiLieu(row)}" xuống dưới`}
                    disabled={disabled || index === documents.length - 1}
                    onClick={() => doiCho(index, 1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`Gỡ "${tenTaiLieu(row)}" khỏi bài viết`}
                    disabled={disabled}
                    onClick={() => onChange(documents.filter((_, i) => i !== index))}
                  />
                </Space>
              ),
            },
          ]}
        />
      )}

      {xemTruoc && (
        <XemTruocTep
          tenHienThi={tenTaiLieu(xemTruoc)}
          contentType={xemTruoc.contentType}
          khoaDem={['cms', 'tai-lieu', 'xem-truoc', xemTruoc.publicId]}
          layUrl={async () => (await cmsApi.fileInlineUrl(xemTruoc.publicId)).url}
          onDong={() => setXemTruoc(null)}
        />
      )}
    </Card>
  );
}
