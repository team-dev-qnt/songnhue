import { Input, Space, Typography } from 'antd';

import { SEO_LIMITS, type SeoField as SeoFieldName, evaluateSeo, seoTextType } from './seo';

/**
 * Ô nhập SEO có bộ đếm ký tự — T20.3, CN-01.1 ("đếm ký tự + cảnh báo đỏ vượt ngưỡng").
 *
 * <h3>Bộ đếm nói hậu quả, không nói con số</h3>
 *
 * "142/160" không cho người soạn biết phải làm gì. "Quá 155 ký tự — công cụ tìm kiếm sẽ cắt
 * bớt phần đuôi" thì có. Đây là màn hình mà người dùng là cán bộ văn phòng, không phải người
 * làm SEO chuyên nghiệp, nên con số trần là thông tin chết.
 */
export function SeoInput({
  field,
  value,
  onChange,
  placeholder,
  textarea = false,
  disabled = false,
}: {
  field: SeoFieldName;
  value: string | null | undefined;
  onChange: (value: string) => void;
  placeholder?: string;
  textarea?: boolean;
  disabled?: boolean;
}) {
  const status = evaluateSeo(field, value);
  const { toiDa } = SEO_LIMITS[field];
  const Control = textarea ? Input.TextArea : Input;

  return (
    <Space direction="vertical" size={2} style={{ width: '100%' }}>
      <Control
        value={value ?? ''}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        // ⛔ Cố ý KHÔNG đặt `maxLength`. Cắt cụt trong lúc gõ làm mất chữ mà người dùng
        // không thấy vì sao — nhất là khi họ dán một đoạn dài. Cho gõ quá, rồi báo đỏ.
        status={status.level === 'vuot' ? 'error' : undefined}
        {...(textarea ? { autoSize: { minRows: 2, maxRows: 4 } } : {})}
      />
      <Typography.Text type={seoTextType(status.level)} style={{ fontSize: 12 }}>
        {status.length}/{toiDa} — {status.hint}
      </Typography.Text>
    </Space>
  );
}
