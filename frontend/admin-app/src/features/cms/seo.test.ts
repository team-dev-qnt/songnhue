import { describe, expect, it } from 'vitest';

import { SEO_LIMITS, evaluateSeo, seoTextType, suggestSlug } from './seo';

describe('evaluateSeo', () => {
  it('ô trống thì nói hậu quả của việc để trống, không báo lỗi', () => {
    const status = evaluateSeo('metaTitle', '');
    expect(status.level).toBe('trong');
    expect(status.length).toBe(0);
    expect(status.hint).toContain('tiêu đề bài');
  });

  it('dưới ngưỡng khuyến nghị thì báo còn bao nhiêu chỗ', () => {
    const status = evaluateSeo('metaTitle', 'Thông báo lịch tưới vụ đông xuân');
    expect(status.level).toBe('tot');
    expect(status.hint).toContain('Còn');
  });

  it('quá ngưỡng khuyến nghị nhưng chưa quá giới hạn cột → cảnh báo bị cắt đuôi', () => {
    const value = 'x'.repeat(SEO_LIMITS.metaTitle.khuyenNghi + 1);
    const status = evaluateSeo('metaTitle', value);

    expect(status.level).toBe('canhBao');
    expect(status.hint).toContain('cắt bớt');
  });

  it('quá giới hạn cột → báo đỏ và nói rõ máy chủ sẽ từ chối', () => {
    const value = 'x'.repeat(SEO_LIMITS.metaTitle.toiDa + 5);
    const status = evaluateSeo('metaTitle', value);

    expect(status.level).toBe('vuot');
    expect(status.hint).toContain('từ chối');
  });

  /**
   * ⚠ Bài kiểm quan trọng nhất của nhóm này, và nó **đã bắt được một lỗi thật**: bản đầu của
   * `evaluateSeo` đếm bằng `Array.from` kèm một dòng tài liệu khẳng định như vậy là đủ. Không
   * đủ — `Array.from` tách theo *điểm mã*, mà mỗi dấu tổ hợp là một điểm mã riêng.
   *
   * Đây là tình huống có thật chứ không phải giả định: chữ dán từ Word thường ở dạng NFD.
   */
  it('đếm theo cụm hiển thị, không theo điểm mã cũng không theo đơn vị UTF-16', () => {
    // Dạng dấu tổ hợp, viết bằng mã thoát để trình soạn thảo không tự dựng sẵn lại:
    // Đ + e + dấu mũ (U+0302) + dấu huyền (U+0300) → mắt thấy 2 chữ.
    const daukethop = '\u0110e\u0302\u0300';

    expect(daukethop.length).toBe(4);
    expect(Array.from(daukethop).length).toBe(4);
    expect(evaluateSeo('metaTitle', daukethop).length).toBe(2);
  });

  it('cùng chữ ở dạng dựng sẵn cho cùng con số — nếu không thì bộ đếm nhảy lúc dán', () => {
    const dungSan = '\u0110\u1EC1';
    expect(dungSan.length).toBe(2);
    expect(evaluateSeo('metaTitle', dungSan).length).toBe(2);
  });

  it('emoji ngoài mặt phẳng cơ bản tính là một ký tự, không phải hai', () => {
    expect(evaluateSeo('metaTitle', '\u{1F30A}').length).toBe(1);
  });

  it('cắt khoảng trắng hai đầu trước khi đếm', () => {
    expect(evaluateSeo('metaTitle', '   abc   ').length).toBe(3);
  });
});

describe('seoTextType', () => {
  it('mỗi mức có đúng một màu', () => {
    expect(seoTextType('trong')).toBe('secondary');
    expect(seoTextType('tot')).toBe('success');
    expect(seoTextType('canhBao')).toBe('warning');
    expect(seoTextType('vuot')).toBe('danger');
  });
});

describe('suggestSlug', () => {
  it('bỏ dấu tiếng Việt và đổi đ thành d', () => {
    expect(suggestSlug('Đề án tưới tiêu Sông Nhuệ')).toBe('de-an-tuoi-tieu-song-nhue');
  });

  it('gộp ký tự lạ thành một dấu gạch, không để gạch thừa ở hai đầu', () => {
    expect(suggestSlug('  Thông báo:  Lịch **tưới** vụ 2026!  ')).toBe(
      'thong-bao-lich-tuoi-vu-2026',
    );
  });

  it('chuỗi không có chữ cái nào cho ra chuỗi rỗng chứ không phải một dãy gạch', () => {
    expect(suggestSlug('!!! ???')).toBe('');
  });
});
