'use client';

import { useEffect, useRef } from 'react';

import { brandColors, neutralColors, statusColors } from '@songnhue/design-tokens';

import type { BieuDoCongTrinh, OLuoi } from '@/lib/api';

import { THEME, echarts } from './setup';

interface BieuDoDienBienProps {
  bieuDo: BieuDoCongTrinh;
}

/** Chỉ tiêu → chuỗi ô, đã căn theo trục thời gian. `null` khi công trình ⛔ không có chỉ tiêu ấy. */
function chuoi(bieuDo: BieuDoCongTrinh, chiTieu: string): OLuoi[] | null {
  return bieuDo.congTrinh?.dong.find((d) => d.chiTieu === chiTieu)?.o ?? null;
}

/**
 * ⛔⛔ Đảo trục thời gian về **cũ → mới** — T87.7.
 *
 * <h2>Khuyết tật</h2>
 *
 * Backend dựng `moc` theo thứ tự **mới-nhất-trước** (`CheDoXemLuoi.dungLuoi` →
 * `chot.minus(buoc * i)`). Đó là thứ tự **CỘT của bảng** (§6.1.2) và **đúng** cho bảng ở trang chi
 * tiết. Biểu đồ dùng lại đúng mảng ấy ⇒ thời gian chạy **phải → trái**: trục đọc
 * `10:30 · 09:30 · 08:30 …`, và một đường đang **lên** trông như đang **xuống**.
 *
 * <h2>⛔ Vì sao ⛔ đảo ở BACKEND</h2>
 *
 * `HydroGridService` lấy `moc.get(0)` làm mốc **đến** và `moc.get(size-1)` làm mốc **từ** của cửa
 * sổ truy vấn — đảo ở đó là **lật ngược cửa sổ**. Và cùng mảng ấy nuôi bảng ở trang chi tiết, nơi
 * mới-nhất-trước là đúng. ⇒ Sửa ở **người tiêu thụ cần chiều khác**, ⛔ ở nguồn chung.
 *
 * <h2>⛔ Vì sao đảo ở ĐÂY chứ ⛔ ở sáu chỗ bên dưới</h2>
 *
 * `nhan`, `vTl`, `vHl`, `nen`, `daiDuong`, `daiAm` và `markLine` đều khớp nhau **theo chỉ số**.
 * Đảo từng cái là sáu cơ hội để một cái bị quên, và cái bị quên sẽ **lệch dữ liệu** chứ ⛔ báo lỗi.
 * Đảo một lần ở chỗ **dữ liệu đi qua** (luật 12) thì mọi thứ phía sau tự đúng theo.
 *
 * ⭐ Tiền lệ có sẵn kèm lý do viết sẵn: `HydroChartService:207-212` (biểu đồ admin) đảo đúng một
 * lần với chú thích *"`dungLuoi` trả mới-nhất-trước (thứ tự CỘT của bảng §6.1.2); biểu đồ đường thì
 * cần cũ → mới"*. Cổng công khai chỉ đang **thiếu** lượt đảo ấy.
 *
 * <h2>⛔ SẮP theo mốc, ⛔ `reverse()` mù</h2>
 *
 * `reverse()` đứng trên một tiền đề **⛔ đo được ở đây**: *"backend luôn trả mới-nhất-trước"*. Tiền
 * đề ấy hôm nay đúng và sống trong một câu javadoc ở `api.ts` — tức hai chỗ phải nhớ giống nhau
 * (luật 14). Ngày ai đó đổi `CheDoXemLuoi`, `reverse()` **lặng lẽ đảo ngược lần thứ hai** và biểu
 * đồ hỏng y như cũ, ⛔ một lượt đỏ nào.
 *
 * ⇒ Sắp theo **chính mốc thời gian** — một sự thật đọc được từ dữ liệu, ⛔ phải một hợp đồng phải
 * nhớ. Bất biến ở đây nói thẳng điều nó cần: *biểu đồ đường vẽ từ cũ sang mới*.
 *
 * ⚠ Trả về một **bản sao**, ⛔ sắp tại chỗ: `bieuDo` là prop của React và cũng là thứ bảng ở trang
 * chi tiết đang đọc — `sort()`/`reverse()` sửa mảng gốc và sẽ lặng lẽ đảo luôn thứ tự hàng của bảng.
 */
function theoChieuThoiGian(bieuDo: BieuDoCongTrinh): BieuDoCongTrinh {
  if (!bieuDo.congTrinh) return bieuDo;

  // Thứ tự chỉ số sau khi sắp mốc tăng dần — mọi hàng `o[]` khớp `moc` THEO CHỈ SỐ nên phải đi
  // theo cùng một hoán vị. Dựng hoán vị một lần rồi áp cho tất cả: sắp riêng từng mảng là cách
  // chắc chắn nhất để một hàng lệch khỏi trục mà ⛔ ai thấy.
  const thuTu = bieuDo.moc
    .map((m, i) => [Date.parse(m), i] as const)
    .sort((a, b) => a[0] - b[0])
    .map(([, i]) => i);

  const sapTheo = <T,>(mang: T[]): T[] => thuTu.map((i) => mang[i]!);

  return {
    ...bieuDo,
    moc: sapTheo(bieuDo.moc),
    congTrinh: {
      ...bieuDo.congTrinh,
      dong: bieuDo.congTrinh.dong.map((d) => ({ ...d, o: sapTheo(d.o) })),
    },
  };
}

/** Cửa sổ có trải qua hơn một ngày (giờ VN) ⛔ — quyết định nhãn trục có kèm ngày ⛔. */
function coQuaMotNgay(moc: string[]): boolean {
  if (moc.length < 2) return false;
  const ngay = (m: string) =>
    new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short' }).format(
      new Date(m),
    );
  return ngay(moc[0]!) !== ngay(moc[moc.length - 1]!);
}

/**
 * Giá trị cho **đường chính** — `null` ở mốc ⛔ không có số **và** ở mốc NGHI_NGO.
 *
 * ⛔ Trả `null` ở điểm nghi ngờ là **cố ý**, ⛔ không phải bỏ sót: §7.1 ghi *"điểm SUSPECT vẽ rỗng,
 * nét đứt, ⛔ **không nối liền** vào đường chính"*. Nối nó vào là công bố một số đo mà chính hệ
 * thống ⛔ không tin, dưới hình dạng ⛔ không phân biệt được với số đo tốt.
 */
function duongChinh(o: OLuoi[] | null): (number | null)[] {
  return (o ?? []).map((x) =>
    x.giaTri === null || x.chatLuong === 'NGHI_NGO' ? null : Number(x.giaTri),
  );
}

/** Chỉ các điểm NGHI_NGO — vẽ rời, rỗng ruột, ⛔ không nối. */
function diemNghiNgo(o: OLuoi[] | null): (number | null)[] {
  return (o ?? []).map((x) =>
    x.chatLuong === 'NGHI_NGO' && x.giaTri !== null ? Number(x.giaTri) : null,
  );
}

/**
 * Biểu đồ diễn biến mực nước một công trình — spec **§7.1**, WS-45.
 *
 * <h2>Dải chênh lệch: BA chuỗi xếp chồng, ⛔ không phải `markArea`</h2>
 *
 * §7.1 đòi tô nền **giữa hai đường**, bề dày chính là chênh lệch tại từng mốc, và **đổi màu theo
 * dấu** (đỏ nhạt khi TL &gt; HL, xanh nhạt khi HL &gt; TL). `markArea` ⛔ không làm được: nó tô một
 * hình chữ nhật có mép trên/dưới **cố định**. Cách đúng là xếp chồng:
 *
 * <ol>
 *   <li>`nen` = min(TL, HL) — đường **vô hình**, chỉ để nâng dải lên đúng chỗ;
 *   <li>`daiDuong` = TL − HL khi dương, ⛔ **0** khi âm — dải đỏ nhạt;
 *   <li>`daiAm` = HL − TL khi dương, ⛔ **0** khi âm — dải xanh nhạt.
 * </ol>
 *
 * ⚠ Dùng **0** chứ ⛔ không `null` ở hai dải: ECharts coi `null` trong chuỗi xếp chồng là một lỗ
 * làm sụp cả cột chồng phía trên, còn một dải dày 0 thì vô hình đúng như mong muốn.
 *
 * <h2>⚠ A11y — đỏ/xanh là cặp khó phân biệt nhất</h2>
 *
 * Chính spec §7.1 nêu lo ngại này. Nên thượng lưu **nét liền**, hạ lưu **nét đứt**: người rối loạn
 * sắc giác vẫn đọc được hai đường mà ⛔ không cần phân biệt màu. Quy ước màu của Công ty giữ nguyên.
 *
 * <h2>⛔ Trục Y phụ = chênh lệch</h2>
 *
 * Mực nước ~0–5 m còn chênh lệch ~0–1 m. Vẽ chung một trục thì đường chênh lệch bị dí sát đáy và
 * ⛔ không đọc được — đó là lý do spec đề nghị tách trục, và người viết spec đã hỏi lại Công ty
 * đúng điểm này.
 */
/**
 * Dựng **toàn bộ** option ECharts của biểu đồ — tách khỏi `useEffect` ở **T45.11**.
 *
 * <h2>⛔⛔ Vì sao phải tách: đường ngưỡng là nhánh ECharts hỏng trong IM LẶNG</h2>
 *
 * Ba đường ngang BĐ1/BĐ2/BĐ3 của §7.1 đi bằng `markLine`. Quên đăng ký `MarkLineComponent` thì
 * ECharts **⛔ ném, ⛔ cảnh báo** — nó chỉ đơn giản ⛔ vẽ, và cái mất là đúng thứ nói cho người đọc
 * biết mực nước đã vượt báo động hay chưa (xem `setup.ts`).
 *
 * <p>Trước lượt này thứ duy nhất canh nhánh ấy là hai phép so **VĂN BẢN** —
 * `expect(setup).toContain('MarkLineComponent')` và `expect(nguon).toContain('markLine')`. Chúng
 * chứng minh *hai chuỗi có mặt trong mã nguồn*, ⛔ phải *một đường ngang có ra hình*. Dòng nợ nói
 * đúng chỗ đau: *"thứ chưa ai NHÌN là ECharts có vẽ đường ngang ra hay ⛔"*.
 *
 * <p>Option nằm trong thân `useEffect` thì ⛔ có cách nào hỏi tới nó mà ⛔ dựng DOM, còn dựng DOM
 * thì cần canvas — jsdom ⛔ có. Tách ra hàm thuần thì bộ kiểm **render SSR ra SVG** và đo được
 * hình thật, ⛔ cần trình duyệt lẫn CSDL (tiền đề *"⛔ dựng được trạng thái có ngưỡng"* của dòng nợ
 * gốc đã hết đúng từ lượt đo 19/09).
 *
 * <p>⚠ Cùng lý lẽ với `features/hr/xuatSoDo.ts`: ở đó lượt xuất dựng **thực thể tạm** thay vì đọc
 * biểu đồ đang hiện, vì biểu đồ trên màn hình đang thu gọn. Ở đây là vế còn lại của cùng một
 * nguyên tắc — *option là dữ liệu, ⛔ phải một hiệu ứng phụ*.
 */
export function optionBieuDo(gocBieuDo: BieuDoCongTrinh): Record<string, unknown> {
  const bieuDo = theoChieuThoiGian(gocBieuDo);

  const tl = chuoi(bieuDo, 'Thượng lưu');
  const hl = chuoi(bieuDo, 'Hạ lưu');
  const chenh = chuoi(bieuDo, 'Chênh lệch');

  // ⛔⛔ Cửa sổ 24 giờ in `HH:mm` HAI LẦN cho mỗi mốc đồng hồ, và ⛔ gì phân biệt hai nửa —
  //    người đọc ⛔ biết `03:00` là sáng nay hay sáng qua. Thêm ngày khi cửa sổ vượt một ngày.
  const nhieuNgay = coQuaMotNgay(bieuDo.moc);
  const nhan = bieuDo.moc.map((m) =>
    new Intl.DateTimeFormat('vi-VN', {
      ...(nhieuNgay ? { day: '2-digit', month: '2-digit' } : {}),
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone: 'Asia/Ho_Chi_Minh',
    }).format(new Date(m)),
  );

  const vTl = duongChinh(tl);
  const vHl = duongChinh(hl);
  const coDai = tl !== null && hl !== null;

  // ⛔ Dải chỉ dựng khi CÓ ĐỦ hai vế — một công trình một chỉ tiêu ⛔ không có gì để tô giữa.
  const nen = coDai
    ? vTl.map((a, i) => (a === null || vHl[i] === null ? 0 : Math.min(a, vHl[i]!)))
    : [];
  const daiDuong = coDai
    ? vTl.map((a, i) => (a === null || vHl[i] === null ? 0 : Math.max(0, a - vHl[i]!)))
    : [];
  const daiAm = coDai
    ? vTl.map((a, i) => (a === null || vHl[i] === null ? 0 : Math.max(0, vHl[i]! - a)))
    : [];

  /** Đường ngưỡng của một chỉ tiêu — §7.1, nét đứt ngang. */
  const nguongCua = (chiTieu: string) => ({
    silent: true,
    symbol: 'none' as const,
    lineStyle: { type: 'dashed' as const, width: 1 },
    label: { formatter: '{b}', position: 'insideEndTop' as const, fontSize: 10 },
    data: bieuDo.nguong
      .filter((n) => n.chiTieu === chiTieu)
      .map((n) => ({ name: n.tenMuc, yAxis: Number(n.giaTri) })),
  });

  const series: Record<string, unknown>[] = [];

  if (coDai) {
    series.push(
      {
        name: 'nền dải',
        type: 'line',
        stack: 'dai',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        silent: true,
        tooltip: { show: false },
        data: nen,
      },
      {
        name: 'Chênh dương (TL > HL)',
        type: 'line',
        stack: 'dai',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: statusColors.danger, opacity: 0.15 },
        tooltip: { show: false },
        data: daiDuong,
      },
      {
        name: 'Chênh âm (HL > TL)',
        type: 'line',
        stack: 'dai',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: brandColors.info, opacity: 0.15 },
        tooltip: { show: false },
        data: daiAm,
      },
    );
  }

  if (tl) {
    series.push(
      {
        name: 'Thượng lưu',
        type: 'line',
        // ⛔ `connectNulls: false` — mốc mất dữ liệu phải NGẮT đường (§7.1). Đặt `true` là nội
        //    suy một đoạn số liệu chưa ai đo, trên đúng biểu đồ người ta đọc để ra quyết định.
        connectNulls: false,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: statusColors.danger, width: 2 },
        itemStyle: { color: statusColors.danger },
        markLine: nguongCua('Thượng lưu'),
        data: vTl,
      },
      {
        name: 'Thượng lưu (nghi ngờ)',
        type: 'line',
        connectNulls: false,
        symbol: 'emptyCircle',
        symbolSize: 8,
        lineStyle: { opacity: 0 },
        itemStyle: { color: statusColors.danger, borderType: 'dashed' },
        data: diemNghiNgo(tl),
      },
    );
  }

  if (hl) {
    series.push(
      {
        name: 'Hạ lưu',
        type: 'line',
        connectNulls: false,
        symbol: 'circle',
        symbolSize: 4,
        // ⚠ NÉT ĐỨT là phân biệt thứ hai bên cạnh màu — §7.1 tự nêu lo ngại về cặp đỏ/xanh.
        lineStyle: { color: brandColors.info, width: 2, type: 'dashed' },
        itemStyle: { color: brandColors.info },
        markLine: nguongCua('Hạ lưu'),
        data: vHl,
      },
      {
        name: 'Hạ lưu (nghi ngờ)',
        type: 'line',
        connectNulls: false,
        symbol: 'emptyCircle',
        symbolSize: 8,
        lineStyle: { opacity: 0 },
        itemStyle: { color: brandColors.info, borderType: 'dashed' },
        data: diemNghiNgo(hl),
      },
    );
  }

  if (chenh) {
    series.push({
      name: 'Chênh lệch',
      type: 'line',
      yAxisIndex: 1,
      connectNulls: false,
      symbol: 'none',
      // ⛔ MÀU XÁM là đặc tả, ⛔ không phải thẩm mỹ: §7.1 ghi "trục Y phụ … vẽ dạng đường mảnh
      //    MÀU XÁM hoặc cột nhạt ở nền". Để ECharts tự gán màu theo bảng chủ đề thì đường chênh
      //    lệch nhận một sắc xanh-tím ⛔ không phân biệt được với đường hạ lưu — và một phép đo
      //    pixel sẽ đếm nhầm nó thành đường hạ lưu (đúng lỗi lượt đo đầu của WS-45 tìm ra).
      lineStyle: { width: 1, type: 'dotted', color: statusColors.unknown },
      itemStyle: { color: statusColors.unknown },
      data: duongChinh(chenh),
    });
  }

  return {
    // ⛔⛔ `top: 72` — dải chú giải phải có CHỖ RIÊNG. Xem khối "chữ chồng chữ" ở javadoc trên:
    //    tên trục vẽ ở `grid.top − nameGap`, nên `top: 40` đặt nó vào giữa dải chú giải.
    // ⚠ `left`/`right` nới ra vì tên trục nay NẰM DỌC ở mép (xem `nameRotate` bên dưới).
    // ⚠ `containLabel` khai TƯỜNG MINH thay vì trông chờ theme (luật 3 — giá trị ĐÃ GIẢI).
    grid: { left: 64, right: 72, top: 72, bottom: 48, containLabel: true },
    // ⛔ Chú giải BẮT BUỘC (§7.1) — và nó cũng là công tắc bật/tắt từng thành phần.
    // ⚠ 7 mục trên một hàng cuộn; `top: 8` + `bottom` của grid giữ nó tách hẳn khỏi tên trục.
    legend: {
      type: 'scroll',
      top: 8,
      data: series.map((s) => s.name).filter((n) => n !== 'nền dải'),
    },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => (v === null || v === undefined ? '' : Number(v).toFixed(2)),
    },
    xAxis: {
      type: 'category',
      data: nhan,
      boundaryGap: false,
      // ⚠ Để ECharts TỰ bỏ bớt nhãn khi chúng chạm nhau. Cửa sổ mặc định 144 mốc, và ở bề ngang
      //   375px thì nhãn `dd/MM HH:mm` chồng lên nhau nếu ⛔ ai bỏ bớt.
      axisLabel: { hideOverlap: true },
    },
    yAxis: [
      {
        type: 'value',
        name: `Mực nước (${bieuDo.meta.donVi})`,
        // ⛔⛔ Ba thuộc tính này là BẢN VÁ của "chữ chồng chữ" — ⛔ để mặc định.
        //    Mặc định của ECharts cho trục giá trị là `nameLocation: 'end'` + `nameGap: 15`, tức
        //    tên vẽ NGANG, PHÍA TRÊN lưới, ở `y ≈ grid.top − 15` — rơi đúng vào dải chú giải.
        // ⚠ `middle` + `nameRotate` đặt tên DỌC ở mép trái, nơi ⛔ có gì khác chiếm.
        nameLocation: 'middle',
        nameRotate: 90,
        nameGap: 44,
        scale: true,
      },
      {
        type: 'value',
        name: 'Chênh lệch',
        nameLocation: 'middle',
        // ⚠ −90 chứ ⛔ 90: ở mép PHẢI, xoay cùng chiều với trục trái làm chữ đọc ngược từ dưới lên.
        nameRotate: -90,
        nameGap: 52,
        scale: true,
        splitLine: { show: false },
      },
    ],
    series,
  };
}

export function BieuDoDienBien({ bieuDo }: BieuDoDienBienProps) {
  const khung = useRef<HTMLDivElement>(null);
  const doThi = useRef<ReturnType<typeof echarts.init> | null>(null);

  useEffect(() => {
    if (!khung.current || !bieuDo.congTrinh) {
      return undefined;
    }
    const bd = echarts.init(khung.current, THEME, { renderer: 'canvas' });
    doThi.current = bd;

    bd.setOption(optionBieuDo(bieuDo));

    const doLai = () => bd.resize();
    window.addEventListener('resize', doLai);

    // ⭐ `ResizeObserver` — T87.8. `window.resize` chỉ bắt lượt đổi kích thước CỬA SỔ, nên mọi
    //   lượt khung co giãn mà cửa sổ ⛔ đổi đều bị bỏ qua: font web nạp xong, thanh cuộn hiện ra,
    //   một khối phía trên giãn cao. Khi ấy canvas giữ kích thước CŨ và ECharts vẽ lên một khung
    //   sai cỡ — chữ chồng nhau trở lại, đúng thứ vừa vá ở trên.
    // ⚠ Khuôn lấy từ `admin-app/src/components/charts/BaseChart.tsx`, nơi javadoc đã ghi vì sao
    //   `window.resize` ⛔ đủ. ⛔ Dựng bản thứ hai của lập luận ấy ở đây.
    const theoDoi =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => bd.resize());
    theoDoi?.observe(khung.current);

    return () => {
      theoDoi?.disconnect();
      window.removeEventListener('resize', doLai);
      bd.dispose();
      doThi.current = null;
    };
  }, [bieuDo]);

  // ⛔ §7.3 — "⛔ không vẽ biểu đồ trống". Backend ép "hoặc CÓ công trình, hoặc CÓ lý do" ở hàm
  //    dựng, nên câu chữ ở đây luôn là câu THẬT của backend, ⛔ không phải chuỗi dự phòng.
  if (!bieuDo.congTrinh) {
    return (
      <p className="px-3.5 py-10 text-center text-[13px] text-surface-textSecondary">
        {bieuDo.lyDoTrong}
      </p>
    );
  }

  return (
    <div>
      <div ref={khung} className="h-[360px] w-full" role="img" aria-label={nhanA11y(bieuDo)} />
      <div className="mt-2 flex flex-wrap justify-end gap-2">
        <button
          type="button"
          onClick={() => taiPng(doThi.current, bieuDo)}
          className="rounded-lg border border-surface-border px-3 py-1.5 text-[12px] font-medium text-surface-textBase hover:bg-surface-bgLayout"
        >
          Tải ảnh PNG
        </button>
        <button
          type="button"
          onClick={() => taiCsv(bieuDo)}
          className="rounded-lg border border-surface-border px-3 py-1.5 text-[12px] font-medium text-surface-textBase hover:bg-surface-bgLayout"
        >
          Tải dữ liệu CSV
        </button>
      </div>
    </div>
  );
}

/**
 * Nhãn thay thế cho người dùng trình đọc màn hình.
 *
 * ⛔ Một `<canvas>` ⛔ không có nội dung nào để đọc lên. Thiếu nhãn này thì cả biểu đồ là một ô
 * trống với họ — và nút "Tải dữ liệu CSV" ngay bên dưới mới là đường thật để họ lấy số.
 */
function nhanA11y(b: BieuDoCongTrinh): string {
  return `Biểu đồ diễn biến mực nước ${b.congTrinh?.tenCongTrinh ?? ''} qua ${b.moc.length} mốc đo. Dùng nút Tải dữ liệu CSV để lấy số liệu dạng bảng.`;
}

function taiPng(bd: ReturnType<typeof echarts.init> | null, b: BieuDoCongTrinh) {
  if (!bd) {
    return;
  }
  // ⛔ Nền TRẮNG là bắt buộc và nó lấy từ token: `getDataURL` mặc định nền TRONG SUỐT, và một PNG
  //    nền trong suốt dán vào văn bản Word ra chữ trắng trên nền trắng — ảnh trông như hỏng.
  taiVe(
    bd.getDataURL({ pixelRatio: 2, backgroundColor: neutralColors.bgContainer }),
    `${tenTep(b)}.png`,
  );
}

/**
 * Xuất CSV — §7.3.
 *
 * ⚠ Ô ⛔ không có số để **rỗng**, ⛔ không phải `0`. Cùng một luật với bảng: `0.00` trong một tệp
 * CSV là một số liệu, và nó sẽ được ai đó cộng vào một bảng tính (quy tắc 16).
 *
 * ⚠ `\uFEFF` ở đầu tệp là BOM — thiếu nó thì Excel bản tiếng Việt mở CSV UTF-8 ra thành chữ rác,
 * và người nhận sẽ báo là "hệ thống xuất sai".
 */
function taiCsv(b: BieuDoCongTrinh) {
  const dong = b.congTrinh?.dong ?? [];
  const dau = ['Thời điểm', ...dong.map((d) => d.chiTieu), 'Chất lượng'];
  const hang = b.moc.map((m, i) => {
    const o = dong.map((d) => d.o[i]);
    const chatLuong = o.some((x) => x?.chatLuong === 'NGHI_NGO')
      ? 'Nghi ngờ'
      : o.some((x) => x?.giaTri)
        ? 'Tốt'
        : '';
    return [new Date(m).toISOString(), ...o.map((x) => x?.giaTri ?? ''), chatLuong];
  });
  const noiDung = [dau, ...hang]
    .map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(','))
    .join('\n');
  taiVe(
    `data:text/csv;charset=utf-8,${encodeURIComponent(`\uFEFF${noiDung}`)}`,
    `${tenTep(b)}.csv`,
  );
}

function tenTep(b: BieuDoCongTrinh): string {
  return `muc-nuoc-${b.congTrinh?.maCongTrinh ?? 'khong-ro'}`;
}

function taiVe(url: string, ten: string) {
  const a = document.createElement('a');
  a.href = url;
  a.download = ten;
  a.click();
}

/**
 * ⚠ Mở ra cho bài kiểm — ba hàm này quyết định **điểm nào vào đường chính, điểm nào tách ra**, và
 * đó là phần chịu lực của cả §7.1. Để chúng nằm ẩn trong `useEffect` thì thứ duy nhất kiểm được là
 * ảnh chụp màn hình.
 */
export const _test = { chuoi, duongChinh, diemNghiNgo };
