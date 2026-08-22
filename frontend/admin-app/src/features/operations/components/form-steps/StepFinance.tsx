import { Col, Form, Input, InputNumber, Row } from 'antd';

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
    </>
  );
}
