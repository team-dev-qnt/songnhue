package com.songnhue.core.application.system;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.core.application.secret.BiMatTichHopService;
import com.songnhue.core.application.secret.BiMatTichHopService.Nguon;
import com.songnhue.core.spi.MucCauHinh;
import com.songnhue.core.spi.MucCauHinh.MucDo;
import com.songnhue.core.spi.MucCauHinh.TrangThai;
import com.songnhue.core.spi.NguonTinhTrangCauHinh;

/** Gom tình trạng cấu hình của mọi module — T61.41. */
@Service
public class TinhTrangCauHinhService {

    private static final Logger log = LoggerFactory.getLogger(TinhTrangCauHinhService.class);

    private final List<NguonTinhTrangCauHinh> nguon;
    private final BiMatTichHopService biMat;

    public TinhTrangCauHinhService(List<NguonTinhTrangCauHinh> nguon, BiMatTichHopService biMat) {
        this.nguon = nguon;
        this.biMat = biMat;
    }

    /** @param soChan số mục CHẶN đang cần làm · @param soCanhBao số mục CẢNH BÁO đang cần làm */
    public record TongQuan(List<MucCauHinh> muc, long soChan, long soCanhBao) {}

    public record TomTat(long soChan, long soCanhBao) {}

    public TongQuan tongQuan() {
        List<MucCauHinh> tatCa = new ArrayList<>();
        for (NguonTinhTrangCauHinh n : nguon) {
            try {
                tatCa.addAll(n.mucCauHinh());
            } catch (RuntimeException e) {
                // ⛔ Một nguồn hỏng ⛔ được làm trắng cả màn hình — và ⛔ được biến mất lặng lẽ: nó hiện thành dòng SAI.
                log.error("Nguồn tình trạng cấu hình {} hỏng", n.getClass().getSimpleName(), e);
                tatCa.add(new MucCauHinh(
                        "NGUON_LOI_" + n.getClass().getSimpleName(),
                        "Khác",
                        "⛔ ⛔ đọc được tình trạng của " + n.getClass().getSimpleName(),
                        TrangThai.SAI,
                        MucDo.CANH_BAO,
                        "Ứng dụng",
                        "—",
                        "Xem log ứng dụng."));
            }
        }
        for (BiMatTichHopService.TinhTrang t : biMat.danhSach()) {
            TrangThai tt =
                    !t.giaiMaDuoc() ? TrangThai.SAI : (t.nguon() == Nguon.CHUA_CO ? TrangThai.THIEU : TrangThai.DAT);
            String ghiChu =
                    switch (t.nguon()) {
                        case GIAO_DIEN ->
                            t.giaiMaDuoc()
                                    ? (t.coGiaTriMoi()
                                            ? "Đặt trên giao diện. ⚠ .env còn giá trị mồi — gỡ đi để chỉ còn một nguồn."
                                            : "Đặt trên giao diện.")
                                    : "⛔ Bản mã ⛔ khớp khoá AES hiện có (CSDL nhân bản từ môi trường khác?) — đặt lại.";
                        case MOI_TRUONG -> "Đang dùng giá trị mồi ở .env — đặt trên giao diện để mã hoá trong CSDL.";
                        case CHUA_CO -> t.moTa();
                    };
            tatCa.add(new MucCauHinh(
                    "BI_MAT_" + t.loai().name(),
                    "Bí mật tích hợp",
                    t.ten(),
                    tt,
                    tt == TrangThai.SAI ? MucDo.CANH_BAO : MucDo.THONG_TIN,
                    "Ứng dụng",
                    "Giao diện — mục Bí mật tích hợp bên dưới (mồi: .env "
                            + t.loai().name() + ")",
                    ghiChu));
        }
        return new TongQuan(tatCa, dem(tatCa, MucDo.CHAN), dem(tatCa, MucDo.CANH_BAO));
    }

    public TomTat tomTat() {
        TongQuan t = tongQuan();
        return new TomTat(t.soChan(), t.soCanhBao());
    }

    private static long dem(List<MucCauHinh> muc, MucDo mucDo) {
        return muc.stream()
                .filter(MucCauHinh::canChuY)
                .filter(m -> m.mucDo() == mucDo)
                .count();
    }
}
