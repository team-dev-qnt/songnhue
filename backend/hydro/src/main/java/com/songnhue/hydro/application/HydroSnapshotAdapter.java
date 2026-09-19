package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.HydroSnapshotPort;
import com.songnhue.hydro.infra.HydroSnapshotRepository;

/** Cài đặt {@link HydroSnapshotPort} — xem javadoc cổng. */
@Service
public class HydroSnapshotAdapter implements HydroSnapshotPort {

    private final HydroSnapshotRepository repository;

    public HydroSnapshotAdapter(HydroSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MucNuoc> mucNuocTaiThoiDiem(List<String> apiCodes, Instant thoiDiem) {
        Map<String, HydroSnapshotRepository.Dong> co = repository.ganNhatTruoc(apiCodes, thoiDiem);
        return apiCodes.stream()
                .map(ma -> {
                    HydroSnapshotRepository.Dong d = co.get(ma);
                    return d == null
                            ? new MucNuoc(ma, null, null, false)
                            : new MucNuoc(ma, d.giaTriM(), d.mocDo(), d.mocDo().equals(thoiDiem));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiemDoVe> diemDoMucNuocCuaCongTrinh(Collection<Long> constructionIds) {
        return repository.diemDoCuaCongTrinh(constructionIds).stream()
                .map(l -> new DiemDoVe(l.constructionId(), l.vaiTro(), l.apiCode(), l.chinh()))
                .toList();
    }
}
