package com.example.inventory.repository;

import com.example.inventory.entity.SubscriptionRequest;
import com.example.inventory.entity.SubscriptionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRequestRepository extends JpaRepository<SubscriptionRequest, Long> {
    List<SubscriptionRequest> findAllByOrderByCreatedAtDesc();
    List<SubscriptionRequest> findByStatusOrderByCreatedAtDesc(SubscriptionRequestStatus status);
}
