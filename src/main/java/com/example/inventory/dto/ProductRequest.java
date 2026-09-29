package com.example.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must be at most 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "SKU may only contain letters, numbers, dots, dashes and underscores")
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Price can have at most 10 digits and 2 decimal places")
    private BigDecimal price;

    @Min(value = 0, message = "Initial quantity cannot be negative")
    @Max(value = 100_000_000, message = "Initial quantity is too large")
    private Integer quantity = 0;

    @NotNull(message = "Minimum stock threshold is required")
    @Min(value = 0, message = "Minimum stock threshold cannot be negative")
    private Integer minimumStock = 10;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    /** Version the client edited (from ProductResponse). When sent, the update is rejected if someone changed the product since. */
    private Long version;

    public ProductRequest() {
    }

    public ProductRequest(String name, String description, String sku, BigDecimal price,
                          Integer quantity, Integer minimumStock, Long categoryId) {
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
        this.minimumStock = minimumStock;
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(Integer minimumStock) {
        this.minimumStock = minimumStock;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
