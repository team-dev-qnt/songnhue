package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ConstructionTest {

    @Test
    void testDaSoHoaViTri() {
        Construction c = new Construction();
        assertThat(c.daSoHoaViTri()).isFalse();

        c.datToaDo(new BigDecimal("21.0"), null);
        assertThat(c.daSoHoaViTri()).isFalse();

        c.datToaDo(new BigDecimal("21.0"), new BigDecimal("105.0"));
        assertThat(c.daSoHoaViTri()).isTrue();
    }

    @Test
    void testApDungTrangThai() {
        Construction c = new Construction();
        assertThat(c.getOperationalStatus()).isEqualTo(OperationalStatus.BINH_THUONG);

        c.apDungTrangThai(OperationalStatus.SU_CO);
        assertThat(c.getOperationalStatus()).isEqualTo(OperationalStatus.SU_CO);
    }

    @Test
    void testGettersAndSetters() {
        Construction c = new Construction("C01", "Tram bom 1", ConstructionType.TRAM_BOM, 1L);
        c.setPurpose(ConstructionPurpose.TUOI);
        c.setManagementLevel(ManagementLevel.CONG_TY);
        c.setClusterId(2L);
        c.setAddress("Hanoi");
        c.setRiverName("Song Nhue");
        c.setChainage("K0+000");
        c.setBasinNote("Ghi chu");
        c.setBuiltYear((short) 2000);
        c.setCommissionedYear((short) 2001);
        c.setDesigner("Thiet ke");
        c.setContractor("Nha thau");
        c.setTotalInvestment(new BigDecimal("1000"));
        c.setDescription("Mo ta");
        c.setLifecycleState(LifecycleState.DANG_HOAT_DONG);

        c.setTotalPowerKw(new BigDecimal("100"));
        c.setPumpCount((short) 2);
        c.setStandbyPumpCount((short) 1);
        c.setFlowPerPumpM3s(new BigDecimal("10"));
        c.setHeadM(new BigDecimal("5"));
        c.setPowerSource("Dien");
        c.setVoltageKv(new BigDecimal("35"));
        c.setOperatingLevelMinM(new BigDecimal("1"));
        c.setOperatingLevelMaxM(new BigDecimal("10"));

        c.setSluiceType("Type");
        c.setBayCount((short) 3);
        c.setBayWidthM(new BigDecimal("2"));
        c.setSillElevationM(new BigDecimal("3"));
        c.setSluiceCrestElevationM(new BigDecimal("4"));
        c.setSluiceDesignFlowM3s(new BigDecimal("5"));
        c.setGateOperation("Gate");
        c.setUpstreamWarningLevelM(new BigDecimal("6"));
        c.setUpstreamDangerLevelM(new BigDecimal("7"));

        c.setLengthKm(new BigDecimal("10"));
        c.setStartChainage("K0+000");
        c.setEndChainage("K1+000");
        c.setLinearDesignFlowM3s(new BigDecimal("20"));
        c.setLinearCrestElevationM(new BigDecimal("5"));
        c.setTechnicalGrade("Grade");
        c.setCrossSection("Cross");
        c.setSpecNote("Note");

        assertThat(c.getCode()).isEqualTo("C01");
        assertThat(c.getName()).isEqualTo("Tram bom 1");
        assertThat(c.getConstructionType()).isEqualTo(ConstructionType.TRAM_BOM);
        assertThat(c.getOrgUnitId()).isEqualTo(1L);
        assertThat(c.getPurpose()).isEqualTo(ConstructionPurpose.TUOI);
        assertThat(c.getManagementLevel()).isEqualTo(ManagementLevel.CONG_TY);
        assertThat(c.getClusterId()).isEqualTo(2L);
        assertThat(c.getAddress()).isEqualTo("Hanoi");
        assertThat(c.getRiverName()).isEqualTo("Song Nhue");
        assertThat(c.getChainage()).isEqualTo("K0+000");
        assertThat(c.getBasinNote()).isEqualTo("Ghi chu");
        assertThat(c.getBuiltYear()).isEqualTo((short) 2000);
        assertThat(c.getCommissionedYear()).isEqualTo((short) 2001);
        assertThat(c.getDesigner()).isEqualTo("Thiet ke");
        assertThat(c.getContractor()).isEqualTo("Nha thau");
        assertThat(c.getTotalInvestment()).isEqualTo(new BigDecimal("1000"));
        assertThat(c.getDescription()).isEqualTo("Mo ta");
        assertThat(c.getLifecycleState()).isEqualTo(LifecycleState.DANG_HOAT_DONG);
        assertThat(c.getChainageM()).isNull(); // Generated column

        assertThat(c.getTotalPowerKw()).isEqualTo(new BigDecimal("100"));
        assertThat(c.getPumpCount()).isEqualTo((short) 2);
        assertThat(c.getStandbyPumpCount()).isEqualTo((short) 1);
        assertThat(c.getFlowPerPumpM3s()).isEqualTo(new BigDecimal("10"));
        assertThat(c.getHeadM()).isEqualTo(new BigDecimal("5"));
        assertThat(c.getPowerSource()).isEqualTo("Dien");
        assertThat(c.getVoltageKv()).isEqualTo(new BigDecimal("35"));
        assertThat(c.getOperatingLevelMinM()).isEqualTo(new BigDecimal("1"));
        assertThat(c.getOperatingLevelMaxM()).isEqualTo(new BigDecimal("10"));
        assertThat(c.getTotalFlowM3s()).isNull(); // Generated column

        assertThat(c.getSluiceType()).isEqualTo("Type");
        assertThat(c.getBayCount()).isEqualTo((short) 3);
        assertThat(c.getBayWidthM()).isEqualTo(new BigDecimal("2"));
        assertThat(c.getSillElevationM()).isEqualTo(new BigDecimal("3"));
        assertThat(c.getSluiceCrestElevationM()).isEqualTo(new BigDecimal("4"));
        assertThat(c.getSluiceDesignFlowM3s()).isEqualTo(new BigDecimal("5"));
        assertThat(c.getGateOperation()).isEqualTo("Gate");
        assertThat(c.getUpstreamWarningLevelM()).isEqualTo(new BigDecimal("6"));
        assertThat(c.getUpstreamDangerLevelM()).isEqualTo(new BigDecimal("7"));

        assertThat(c.getLengthKm()).isEqualTo(new BigDecimal("10"));
        assertThat(c.getStartChainage()).isEqualTo("K0+000");
        assertThat(c.getEndChainage()).isEqualTo("K1+000");
        assertThat(c.getLinearDesignFlowM3s()).isEqualTo(new BigDecimal("20"));
        assertThat(c.getLinearCrestElevationM()).isEqualTo(new BigDecimal("5"));
        assertThat(c.getTechnicalGrade()).isEqualTo("Grade");
        assertThat(c.getCrossSection()).isEqualTo("Cross");
        assertThat(c.getSpecNote()).isEqualTo("Note");
    }
}
