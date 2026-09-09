'use client';

import { useEffect, useRef } from 'react';

import { brandColors, neutralColors, statusColors } from 'design-tokens';

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
export function BieuDoDienBien({ bieuDo }: BieuDoDienBienProps) {
  const khung = useRef<HTMLDivElement>(null);
  const doThi = useRef<ReturnType<typeof echarts.init> | null>(null);

  useEffect(() => {
    if (!khung.current || !bieuDo.congTrinh) {
      return undefined;
    }
    const bd = echarts.init(khung.current, THEME, { renderer: 'canvas' });
    doThi.current = bd;

    const tl = chuoi(bieuDo, 'Thượng lưu');
    const hl = chuoi(bieuDo, 'Hạ lưu');
    const chenh = chuoi(bieuDo, 'Chênh lệch');

    const nhan = bieuDo.moc.map((m) =>
      new Intl.DateTimeFormat('vi-VN', {
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

    bd.setOption({
      grid: { left: 48, right: 56, top: 40, bottom: 48 },
      // ⛔ Chú giải BẮT BUỘC (§7.1) — và nó cũng là công tắc bật/tắt từng thành phần.
      legend: {
        type: 'scroll',
        top: 4,
        data: series.map((s) => s.name).filter((n) => n !== 'nền dải'),
      },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (v: unknown) => (v === null || v === undefined ? '' : Number(v).toFixed(2)),
      },
      xAxis: { type: 'category', data: nhan, boundaryGap: false },
      yAxis: [
        { type: 'value', name: `Mực nước (${bieuDo.meta.donVi})`, scale: true },
        { type: 'value', name: 'Chênh lệch', scale: true, splitLine: { show: false } },
      ],
      series,
    });

    const doLai = () => bd.resize();
    window.addEventListener('resize', doLai);
    return () => {
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
