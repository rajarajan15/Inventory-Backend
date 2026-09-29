package com.example.inventory.entity;

public enum StockMovementType {
    /** Opening quantity when a product is created. */
    INITIAL,
    STOCK_IN,
    STOCK_OUT,
    /** Quantity changed by editing the product. */
    ADJUSTMENT,
    /** Opening quantity from a CSV import. */
    IMPORT
}
