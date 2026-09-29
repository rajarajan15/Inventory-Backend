package com.example.inventory.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Append-only stock ledger: one row per change to a product's quantity.
 * Product and user details are copied onto the row so the history stays readable after either is deleted.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    /** Null once the product has been deleted (ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    private Product product;

    @Column(name = "product_sku", nullable = false, updatable = false)
    private String productSku;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private StockMovementType type;

    /** Signed: positive for stock added, negative for stock removed. */
    @Column(name = "quantity_change", nullable = false, updatable = false)
    private Integer quantityChange;

    @Column(name = "quantity_after", nullable = false, updatable = false)
    private Integer quantityAfter;

    @Column(length = 500, updatable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", updatable = false)
    private User performedBy;

    @Column(name = "performed_by_name", updatable = false)
    private String performedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, StockMovementType type, int quantityChange, String notes,
                         User performedBy, String performedByName) {
        this.organization = product.getOrganization();
        this.product = product;
        this.productSku = product.getSku();
        this.productName = product.getName();
        this.type = type;
        this.quantityChange = quantityChange;
        this.quantityAfter = product.getQuantity();
        this.notes = notes;
        this.performedBy = performedBy;
        this.performedByName = performedByName;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public StockMovementType getType() {
        return type;
    }

    public Integer getQuantityChange() {
        return quantityChange;
    }

    public Integer getQuantityAfter() {
        return quantityAfter;
    }

    public String getNotes() {
        return notes;
    }

    public String getPerformedByName() {
        return performedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
