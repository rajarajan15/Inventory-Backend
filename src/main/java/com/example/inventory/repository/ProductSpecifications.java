package com.example.inventory.repository;

import com.example.inventory.entity.Product;
import org.springframework.data.jpa.domain.Specification;

/**
 * Product search filters. Each one returns null when its input is empty, so an absent filter adds no SQL at all
 * (no "? IS NULL" parameters, which PostgreSQL cannot type when the value is null).
 */
public final class ProductSpecifications {

    private static final char ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> inOrganization(Long organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    /** Case-insensitive "contains" on name or SKU; % and _ typed by the user are matched literally. */
    public static Specification<Product> nameOrSkuContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(text.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("sku")), pattern, ESCAPE));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
