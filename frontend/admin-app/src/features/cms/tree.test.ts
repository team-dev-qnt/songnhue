import { describe, expect, it } from 'vitest';

import { buildTree, canDropInto, subtreeIds } from './tree';

interface Node {
  publicId: string;
  parentPublicId: string | null;
  name: string;
}

const cay: Node[] = [
  { publicId: 'a', parentPublicId: null, name: 'Tin tức' },
  { publicId: 'a1', parentPublicId: 'a', name: 'Tin hoạt động' },
  { publicId: 'a2', parentPublicId: 'a', name: 'Tin chuyên ngành' },
  { publicId: 'a1x', parentPublicId: 'a1', name: 'Tin nội bộ' },
  { publicId: 'b', parentPublicId: null, name: 'Giới thiệu' },
];

describe('buildTree', () => {
  it('dựng đúng hai cấp và giữ nguyên thứ tự backend trả về', () => {
    const roots = buildTree(cay);
    expect(roots.map((r) => r.key)).toEqual(['a', 'b']);
    expect(roots[0].children.map((c) => c.key)).toEqual(['a1', 'a2']);
    expect(roots[0].children[0].children.map((c) => c.key)).toEqual(['a1x']);
  });

  it('danh sách rỗng cho cây rỗng, không nổ', () => {
    expect(buildTree([])).toEqual([]);
  });

  /**
   * ⚠ Nút mồ côi được **nâng lên cấp gốc**, không bị bỏ. Mất một danh mục khỏi cây là thứ
   * không ai phát hiện (nó chỉ đơn giản không hiện ra); thừa một nút ở sai cấp thì thấy ngay.
   */
  it('nút có cha không tìm thấy được nâng lên cấp gốc chứ không biến mất', () => {
    const roots = buildTree([
      { publicId: 'a', parentPublicId: null, name: 'Gốc' },
      { publicId: 'x', parentPublicId: 'khong-ton-tai', name: 'Mồ côi' },
    ]);
    expect(roots.map((r) => r.key).sort()).toEqual(['a', 'x']);
  });
});

describe('subtreeIds', () => {
  it('gồm chính nút đó và toàn bộ con cháu', () => {
    expect([...subtreeIds(cay, 'a')].sort()).toEqual(['a', 'a1', 'a1x', 'a2']);
  });

  it('nút lá chỉ gồm chính nó', () => {
    expect([...subtreeIds(cay, 'a1x')]).toEqual(['a1x']);
  });
});

describe('canDropInto', () => {
  it('kéo sang nhánh khác thì được', () => {
    expect(canDropInto(cay, 'a1', 'b')).toBe(true);
  });

  it('thả ra vùng trống = đưa lên cấp gốc, luôn hợp lệ', () => {
    expect(canDropInto(cay, 'a1x', null)).toBe(true);
  });

  it('⛔ không kéo được vào chính nó', () => {
    expect(canDropInto(cay, 'a', 'a')).toBe(false);
  });

  it('⛔ không kéo được vào con trực tiếp — thao tác này cắt cả nhánh ra khỏi cây', () => {
    expect(canDropInto(cay, 'a', 'a1')).toBe(false);
  });

  it('⛔ và cũng không kéo được vào cháu — đây là chỗ phép kiểm một cấp sẽ bỏ sót', () => {
    expect(canDropInto(cay, 'a', 'a1x')).toBe(false);
  });
});
