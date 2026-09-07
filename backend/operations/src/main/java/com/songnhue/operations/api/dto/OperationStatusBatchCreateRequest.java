package com.songnhue.operations.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class OperationStatusBatchCreateRequest {

    @NotEmpty
    @Valid
    private List<OperationStatusBatchItemRequest> items;

    public List<OperationStatusBatchItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OperationStatusBatchItemRequest> items) {
        this.items = items;
    }
}
