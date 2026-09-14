import { describe, expect, it } from 'vitest';

import { chieuCaoSoDo, nhanNut, optionSoDo, type NutSoDo } from './soDoToChuc';

function nut(p: Partial<NutSoDo> & { name: string }): NutSoDo {
  return {
    publicId: p.publicId ?? p.name,
    code: p.code ?? p.name,
    name: p.name,
    shortName: p.shortName ?? null,
    unitType: 'XI_NGHIEP',
    depth: p.depth ?? 1,
    dangDung: p.dangDung ?? true,
    lanhDao: p.lanhDao ?? [],
    soNhanSuTrucTiep: p.soNhanSuTrucTiep ?? 0,
    soNhanSuCaNhanh: p.soNhanSuCaNhanh ?? 0,
    con: p.con ?? [],
  };
}

/**
 * Nhãn của một nút sơ đồ tổ chức — CN-04.1.
 *
 * ⛔⛔ Bài này canh **vế phân biệt** của một quyết định dễ làm sai theo cả hai chiều:
 *
 * - Luôn hiện **một** con số ⇒ một Xí nghiệp có 4 Tổ đội đọc thành *"0 người"* khi thu gọn nhánh.
 * - Luôn hiện **hai** con số ⇒ nút lá đọc thành *"2 / 2"*, một phân số vô nghĩa.
 *
 * Cả hai đều là câu trả lời **sai mà im lặng**: màn hình trông hoàn toàn bình thường.
 */
describe('nhanNut — ba dòng: tên · người đứng đầu · quân số', () => {
  it('⭐ hai số quân số KHÁC nhau ⇒ hiện "trực tiếp / cả nhánh"', () => {
    const nhan = nhanNut(nut({ name: 'Xí nghiệp A', soNhanSuTrucTiep: 1, soNhanSuCaNhanh: 12 }));
    expect(nhan).toContain('1 / 12 người');
  });

  it('⛔ hai số BẰNG nhau (nút lá) ⇒ hiện MỘT số — vế phân biệt', () => {
    const nhan = nhanNut(nut({ name: 'Tổ A1', soNhanSuTrucTiep: 2, soNhanSuCaNhanh: 2 }));
    expect(nhan).toContain('2 người');
    expect(nhan).not.toContain('2 / 2');
  });

  it('⭐ người đứng đầu lấy PHẦN TỬ ĐẦU của danh sách đã sắp — ⛔ KHÔNG so chuỗi chức danh', () => {
    // ⛔⛔ T50.6: `org_unit_leaders.title` là ô TỰ DO. Xếp *Chủ tịch > Tổng Giám đốc > Phó TGĐ*
    //    bằng so chuỗi là dựng một sơ đồ TRÔNG như dữ liệu trong khi nó là phỏng đoán — và sai
    //    lặng lẽ ngay lần Công ty đổi cách viết. Thứ tự đã do `sort_order` Công ty tự sắp quyết
    //    định ở backend; giao diện chỉ lấy phần tử đầu.
    const nhan = nhanNut(
      nut({
        name: 'Công ty',
        lanhDao: [
          { hoTen: 'Trần Văn A', chucDanh: 'Phó Giám đốc' },
          { hoTen: 'Lê Thị B', chucDanh: 'Giám đốc' },
        ],
      }),
    );
    expect(nhan).toContain('Phó Giám đốc: Trần Văn A');
    expect(nhan).not.toContain('Lê Thị B');
  });

  it('⛔ đơn vị đã TẮT vẫn hiện, kèm nhãn — ⛔ không được biến mất', () => {
    // Ẩn nó đi là làm một đơn vị biến khỏi sơ đồ trong khi hồ sơ vẫn trỏ vào nó — đúng triệu
    // chứng mà WS-56 phải dựng chốt chặn ADM-2004 để ngăn.
    expect(nhanNut(nut({ name: 'Tổ cũ', dangDung: false }))).toContain('(đã tắt)');
    expect(nhanNut(nut({ name: 'Tổ mới', dangDung: true }))).not.toContain('(đã tắt)');
  });

  it('tên viết tắt thắng tên đầy đủ khi có — nhãn trên sơ đồ phải ngắn', () => {
    expect(
      nhanNut(nut({ name: 'Xí nghiệp Thuỷ lợi Thanh Oai', shortName: 'XN Thanh Oai' })),
    ).toContain('XN Thanh Oai');
  });
});

describe('optionSoDo / chieuCaoSoDo', () => {
  it('⛔⛔ thu gọn từ cấp 3, và nút KHÔNG có con thì ⛔ không bao giờ bị thu gọn', () => {
    // Một nút lá mang `collapsed: true` là một nút bấm vào ⛔ không có gì mở ra.
    const cay = [
      nut({
        name: 'CTY',
        depth: 1,
        con: [nut({ name: 'XN', depth: 2, con: [nut({ name: 'To', depth: 3 })] })],
      }),
    ];
    const opt = JSON.stringify(optionSoDo(cay));
    expect(opt).toContain('"collapsed":false');
    expect(opt).not.toContain('"name":"To\\n0 người","value":0,"collapsed":true');
  });

  it('⭐ chiều cao suy từ SỐ LÁ, ⛔ không phải một hằng số', () => {
    // Một hằng số thì cây 40 đơn vị chồng chữ lên nhau, còn cây 2 đơn vị thành một dải mỏng.
    const nhieuLa = [
      nut({ name: 'CTY', con: Array.from({ length: 30 }, (_, i) => nut({ name: `X${i}` })) }),
    ];
    expect(chieuCaoSoDo(nhieuLa)).toBeGreaterThan(chieuCaoSoDo([nut({ name: 'CTY' })]));
    expect(chieuCaoSoDo([nut({ name: 'CTY' })])).toBeGreaterThanOrEqual(360);
  });
});
