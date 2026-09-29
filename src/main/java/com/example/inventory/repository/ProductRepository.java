package com.example.inventory.repository;

import com.example.inventory.dto.ProductSummaryResponse;
import com.example.inventory.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Every query is scoped by organization id so one tenant can never read or modify another's products.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Row lock for stock changes, so concurrent stock in/out on one product are applied one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.organization.id = :organizationId")
    Optional<Product> findForUpdate(@Param("id") Long id, @Param("organizationId") Long organizationId);

    boolean existsByOrganizationIdAndSku(Long organizationId, String sku);
    boolean existsByOrganizationIdAndSkuAndIdNot(Long organizationId, String sku, Long id);

    @EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM Product p WHERE p.organization.id = :organizationId AND p.quantity <= p.minimumStock ORDER BY p.quantity ASC, p.name ASC")
    List<Product> findLowStockProducts(@Param("organizationId") Long organizationId);

    /** Paged search; the filters are built in ProductSpecifications so only the ones actually provided reach SQL. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Dashboard totals computed in the database instead of loading every product. */
    @Query("SELECT new com.example.inventory.dto.ProductSummaryResponse(" +
           "COUNT(p), COALESCE(SUM(p.quantity), 0), COALESCE(SUM(p.price * p.quantity), 0), " +
           "COALESCE(SUM(CASE WHEN p.quantity <= p.minimumStock THEN 1 ELSE 0 END), 0)) " +
           "FROM Product p WHERE p.organization.id = :organizationId")
    ProductSummaryResponse summarize(@Param("organizationId") Long organizationId);

    long countByOrganizationId(Long organizationId);

    long countByCategoryId(Long categoryId);

    /** Product count per category in one query: rows of [categoryId, count]. */
    @Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.organization.id = :organizationId AND p.category IS NOT NULL GROUP BY p.category.id")
    List<Object[]> countByCategory(@Param("organizationId") Long organizationId);
}
