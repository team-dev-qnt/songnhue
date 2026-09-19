package com.songnhue.operations.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.CoMayBom;

/** 9 cỡ máy của Bảng 1 Báo cáo nhanh — ⛔ phạm vi đơn vị, đây là cấu trúc biểu mẫu. */
public interface CoMayBomRepository extends JpaRepository<CoMayBom, Long> {

    List<CoMayBom> findByDeletedAtIsNullOrderBySortOrderAsc();
}
