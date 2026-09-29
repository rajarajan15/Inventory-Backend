package com.example.inventory.dto;

import java.math.BigDecimal;

/** Inventory totals for the dashboard. */
public record ProductSummaryResponse(
        long totalProducts,
        long totalUnits,
        BigDecimal inventoryValue,
        long lowStockCount
) {
    public ProductSummaryResponse(Long totalProducts, Long totalUnits, BigDecimal inventoryValue, Long lowStockCount) {
        this(nz(totalProducts), nz(totalUnits), inventoryValue != null ? inventoryValue : BigDecimal.ZERO, nz(lowStockCount));
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }
}
