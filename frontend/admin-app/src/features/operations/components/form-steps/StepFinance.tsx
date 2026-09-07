import { useQuery } from '@tanstack/react-query';
import { Col, Form, Input, InputNumber, Row, Select } from 'antd';
import { useParams } from 'react-router-dom';

import { type ConstructionDocumentList } from '@/shared/api-types';
import { api } from '@/shared/apiClient';

export function StepFinance() {
  const form = Form.useFormInstance();
  const totalInvestment = Form.useWatch('totalInvestment', form);

  // Helper to display in millions/billions
  const renderInvestmentHelper = (val: number | undefined) => {
    if (!val) return null;
    if (val >= 1_000_000_000) {
      return `~ ${(val / 1_000_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} tỷ VNĐ`;
    }
    if (val >= 1_000_000) {
      return `~ ${(val / 1_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 2 })} triệu VNĐ`;
    }
    return `${val.toLocaleString('vi-VN')} VNĐ`;
  };

  return (
    <>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="builtYear" label="Năm bắt đầu xây dựng">
            <InputNumber style={{ width: '100%' }} min={1900} max={2100} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="commissionedYear" label="Năm đưa vào sử dụng">
            <InputNumber style={{ width: '100%' }} min={1900} max={2100} />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="designer" label="Đơn vị thiết kế">
            <Input />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="contractor" label="Đơn vị thi công">
            <Input />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="totalInvestment"
            label="Tổng mức đầu tư (VNĐ)"
            extra={
              <span style={{ color: '#165bb6', fontWeight: 500 }}>
                {renderInvestmentHelper(totalInvestment)}
              </span>
            }
          >
            <InputNumber
              style={{ width: '100%' }}
              formatter={(value) => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
              parser={(value) => Number(value!.replace(/\$\s?|(,*)/g, ''))}
            />
          </Form.Item>
        </Col>
      </Row>

      <OTaiLieuCongBo />
    </>
  );
}

/**
 * Hai tài liệu **công bố ra cổng công khai** — cột "Quy trình vận hành" và "Phương án bảo vệ" của
 * bảng 7 cột (CR-28, §5.1).
 *
 * <h2>⚠⚠ Hai cột này từng đọc-được-mà-không-ghi-được</h2>
 *
 * `V202608271035` (27/8) thêm hai cột, `PublicConstructionCatalogService` đọc chúng để dựng hai
 * liên kết trên cổng — nhưng không biểu mẫu nào có ô nhập, nên hai setter chỉ có **một** lời gọi
 * trong toàn kho và lời gọi ấy nằm trong một bài kiểm. §10 của văn bản nghiệm thu đòi *"các link
 * Quyết định và Google Map hoạt động"*; hai liên kết ấy sẽ không bao giờ có gì để trỏ tới.
 *
 * <h2>Vì sao là ô CHỌN từ tệp đã tải lên, không phải ô gõ đường dẫn</h2>
 *
 * Giá trị là `attachments.public_id` — khoá ngoại `ON DELETE SET NULL`. Chọn từ danh sách thì
 * không có cách nào trỏ vào một tệp không tồn tại, và gỡ tệp là cột tự rỗng. Một ô gõ URL tự do
 * thì cổng công khai sẽ công bố một liên kết chết mà không ai biết (luật 16: một liên kết hỏng
 * tệ hơn hẳn một ô trống).
 *
 * ⛔ Chỉ hiện khi SỬA. Lúc tạo mới chưa có công trình thì cũng chưa có tệp nào để chọn — dựng ô
 *    chọn rỗng ở đó là bày ra một điều khiển không dùng được (cùng lý lẽ với nút không hành vi ở
 *    thanh điều hướng cũ của cổng).
 */
function OTaiLieuCongBo() {
  const { publicId } = useParams<{ publicId: string }>();

  const { data } = useQuery({
    queryKey: ['ops', 'constructions', publicId, 'documents'],
    queryFn: () => api.get<ConstructionDocumentList>(`/ops/constructions/${publicId}/documents`),
    enabled: !!publicId,
  });

  if (!publicId) return null;

  const luaChon = (data?.items ?? []).map((tep) => ({
    value: tep.publicId,
    // Kèm loại tài liệu vào nhãn: một công trình có thể có nhiều tệp cùng tên gốc
    // ("QĐ.pdf"), và người chọn cần phân biệt được chúng trước khi công bố ra cổng.
    label: `${tep.originalName} · ${tep.docType}`,
  }));

  const chuaCoTep =
    luaChon.length === 0
      ? 'Chưa có tệp nào — tải lên ở tab "Tài liệu đính kèm" trước.'
      : 'Tệp được chọn sẽ hiện thành liên kết tải về trên cổng công khai.';

  return (
    <Row gutter={16}>
      <Col span={12}>
        <Form.Item
          name="operatingProcedureAttachmentId"
          label="Quy trình vận hành (công bố trên cổng)"
          extra={chuaCoTep}
        >
          <Select allowClear options={luaChon} placeholder="Chọn từ tài liệu đã tải lên" />
        </Form.Item>
      </Col>
      <Col span={12}>
        <Form.Item
          name="protectionPlanAttachmentId"
          label="Phương án bảo vệ (công bố trên cổng)"
          extra={chuaCoTep}
        >
          <Select allowClear options={luaChon} placeholder="Chọn từ tài liệu đã tải lên" />
        </Form.Item>
      </Col>
    </Row>
  );
}
