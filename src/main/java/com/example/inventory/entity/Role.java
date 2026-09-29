package com.example.inventory.entity;

public enum Role {
    /** StockWise platform owner. Exactly one exists; seeded at startup, never created through the API. */
    SUPER_ADMIN,
    /** Organization admin: manages one organization's data and approves its users. */
    ADMIN,
    /** Organization staff member. */
    STAFF
}
