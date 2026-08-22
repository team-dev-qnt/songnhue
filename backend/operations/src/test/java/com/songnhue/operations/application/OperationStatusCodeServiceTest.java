package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.operations.api.dto.OperationStatusCodeCreateRequest;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

@ExtendWith(MockitoExtension.class)
class OperationStatusCodeServiceTest {

    @Mock
    private OperationStatusCodeRepository repository;

    @Mock
    private ConstructionOperationStatusRepository statusRepository;

    @Mock
    private ConstructionStatusService constructionStatusService;

    @InjectMocks
    private OperationStatusCodeService service;

    @Test
    void createTrungMaNenNemOPS2005() {
        OperationStatusCodeCreateRequest request = new OperationStatusCodeCreateRequest();
        request.setCode("MT");

        when(repository.existsByCodeAndDeletedAtIsNull("MT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPS_2005);
    }

    @Test
    void deleteDaDuocSuDungNenNemOPS2007() {
        OperationStatusCode entity = new OperationStatusCode();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(statusRepository.existsByOperationCodeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OPS_2007);
    }

    @Test
    void deleteChuaSuDungChiAnDi() {
        OperationStatusCode entity = new OperationStatusCode();
        org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(statusRepository.existsByOperationCodeId(1L)).thenReturn(false);

        service.delete(1L);

        assertThat(entity.getDeletedAt()).isNotNull();
        verify(repository).save(entity);
    }
}
