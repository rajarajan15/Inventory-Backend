package com.example.inventory.repository;

import com.example.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByOrganizationIdAndProductIdOrderByCreatedAtDescIdDesc(Long organizationId, Long productId, Pageable pageable);
}
