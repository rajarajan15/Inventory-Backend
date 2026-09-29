package com.example.inventory.dto;

import com.example.inventory.entity.StockMovement;
import com.example.inventory.entity.StockMovementType;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        StockMovementType type,
        int quantityChange,
        int quantityAfter,
        String notes,
        String performedBy,
        Instant createdAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getType(), movement.getQuantityChange(),
                movement.getQuantityAfter(), movement.getNotes(), movement.getPerformedByName(), movement.getCreatedAt());
    }
}
