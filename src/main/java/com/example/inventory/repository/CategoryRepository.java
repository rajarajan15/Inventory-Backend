package com.example.inventory.repository;

import com.example.inventory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Every query is scoped by organization id so one tenant can never read or modify another's categories.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByOrganizationId(Long organizationId);
    Optional<Category> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<Category> findByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
    boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
