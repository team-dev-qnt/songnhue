import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PaperClipOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Input, Space, Table, Tag, Typography } from 'antd';

import { formatBytes } from '@/shared/format';

import { type ArticleDocumentView } from './types';

/**
 * Khối "Tài liệu đính kèm" của màn hình soạn bài — WS-40, CN-01.1
 * (*"Tệp đính kèm | File | Nhiều tệp (pdf, docx, xlsx…)"*).
 *
 * <h3>⛔ Vì sao KHÔNG dùng `components/business/AttachmentPanel.tsx`</h3>
 *
 * Nó nhận `ownerId: number` trong khi CMS đi bằng UUID, và tới 04/09/2026 **không màn hình nào
 * dùng nó** — `ConstructionDocumentsPanel` đã ghi lại đúng lý do ấy. Khối này còn khác về bản
 * chất: nó **không tự tải lên và không tự lưu**. Nó chỉ sửa một danh sách trong bộ nhớ, và danh
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

export function ArticleDocumentsPanel({
  documents,
  onChange,
  onPick,
  disabled = false,
}: ArticleDocumentsPanelProps) {
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
                    ketQua[index] = { ...doc, label: e.target.value.trim() === '' ? null : e.target.value };
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
              width: 110,
              render: (_: unknown, __, index) => (
                <Space size={0}>
                  <Button
                    type="text"
                    size="small"
                    icon={<ArrowUpOutlined />}
                    disabled={disabled || index === 0}
                    onClick={() => doiCho(index, -1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    icon={<ArrowDownOutlined />}
                    disabled={disabled || index === documents.length - 1}
                    onClick={() => doiCho(index, 1)}
                  />
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    disabled={disabled}
                    onClick={() => onChange(documents.filter((_, i) => i !== index))}
                  />
                </Space>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}
