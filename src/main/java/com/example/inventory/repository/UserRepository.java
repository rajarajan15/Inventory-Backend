package com.example.inventory.repository;

import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);

    List<User> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    List<User> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, UserStatus status);
    List<User> findByOrganizationIdAndRoleAndStatus(Long organizationId, Role role, UserStatus status);
    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);
    long countByOrganizationId(Long organizationId);
}
