package com.example.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StockOperationRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100_000_000, message = "Quantity is too large")
    private Integer quantity;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;

    public StockOperationRequest() {
    }

    public StockOperationRequest(Integer quantity, String notes) {
        this.quantity = quantity;
        this.notes = notes;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
