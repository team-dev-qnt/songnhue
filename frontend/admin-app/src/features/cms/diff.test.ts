import { describe, expect, it } from 'vitest';

import { diffBlocks, summarizeDiff, toBlocks } from './diff';

describe('toBlocks', () => {
  it('tách theo thẻ khối và chỉ giữ phần chữ', () => {
    const blocks = toBlocks(
      '<h2>Tiêu đề</h2><p>Đoạn <strong>một</strong></p><ul><li>Ý A</li></ul>',
    );
    expect(blocks).toEqual(['Tiêu đề', 'Đoạn một', 'Ý A']);
  });

  it('gộp khoảng trắng thừa — xuống dòng trong HTML không phải một thay đổi nội dung', () => {
    expect(toBlocks('<p>Một   hai\n\n  ba</p>')).toEqual(['Một hai ba']);
  });

  it('bỏ khối rỗng: một đoạn trống không phải nội dung để so sánh', () => {
    expect(toBlocks('<p></p><p>  </p><p>Có chữ</p>')).toEqual(['Có chữ']);
  });

  it('chữ trần không nằm trong thẻ khối nào vẫn so sánh được', () => {
    expect(toBlocks('Chỉ một dòng chữ')).toEqual(['Chỉ một dòng chữ']);
  });

  it('nội dung rỗng cho danh sách rỗng, không nổ', () => {
    expect(toBlocks(null)).toEqual([]);
    expect(toBlocks('')).toEqual([]);
    expect(toBlocks('   ')).toEqual([]);
  });
});

describe('diffBlocks', () => {
  it('hai bản giống nhau thì mọi dòng đều là "giữ"', () => {
    const rows = diffBlocks(['A', 'B'], ['A', 'B']);
    expect(rows.map((r) => r.kind)).toEqual(['giu', 'giu']);
    expect(summarizeDiff(rows).khongDoi).toBe(true);
  });

  it('thêm một đoạn ở giữa — phần còn lại vẫn nhận ra là không đổi', () => {
    const rows = diffBlocks(['A', 'C'], ['A', 'B', 'C']);
    expect(rows).toEqual([
      { kind: 'giu', text: 'A' },
      { kind: 'them', text: 'B' },
      { kind: 'giu', text: 'C' },
    ]);
  });

  it('bỏ một đoạn', () => {
    const rows = diffBlocks(['A', 'B', 'C'], ['A', 'C']);
    expect(rows.filter((r) => r.kind === 'bot').map((r) => r.text)).toEqual(['B']);
    expect(summarizeDiff(rows)).toMatchObject({ them: 0, bot: 1 });
  });

  it('sửa một đoạn = bỏ bản cũ rồi thêm bản mới, và bản cũ đứng trước', () => {
    const rows = diffBlocks(['A', 'Cũ', 'C'], ['A', 'Mới', 'C']);
    const giua = rows.slice(1, 3);
    expect(giua.map((r) => r.kind)).toEqual(['bot', 'them']);
    expect(giua.map((r) => r.text)).toEqual(['Cũ', 'Mới']);
  });

  it('bản trước rỗng — toàn bộ là thêm mới', () => {
    const rows = diffBlocks([], ['A', 'B']);
    expect(rows.every((r) => r.kind === 'them')).toBe(true);
    expect(summarizeDiff(rows)).toMatchObject({ them: 2, bot: 0 });
  });

  it('xoá sạch nội dung — toàn bộ là bỏ, và đó phải nhìn thấy được', () => {
    const rows = diffBlocks(['A', 'B'], []);
    expect(rows.every((r) => r.kind === 'bot')).toBe(true);
  });

  it('đảo thứ tự hai đoạn cho ra một cặp bỏ/thêm chứ không phải "không đổi"', () => {
    const rows = diffBlocks(['A', 'B'], ['B', 'A']);
    expect(summarizeDiff(rows).khongDoi).toBe(false);
  });
});
