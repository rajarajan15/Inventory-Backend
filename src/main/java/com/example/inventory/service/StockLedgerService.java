package com.example.inventory.service;

import com.example.inventory.dto.PageResponse;
import com.example.inventory.dto.StockMovementResponse;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.StockMovement;
import com.example.inventory.entity.StockMovementType;
import com.example.inventory.repository.StockMovementRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.CurrentUser;
import com.example.inventory.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes and reads the append-only stock ledger. */
@Service
public class StockLedgerService {

    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;

    public StockLedgerService(StockMovementRepository stockMovementRepository, UserRepository userRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.userRepository = userRepository;
    }

    /**
     * Records a change that has already been applied to {@code product} (its quantity is the resulting quantity).
     * Runs inside the caller's transaction, so the ledger and the product never disagree.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Product product, StockMovementType type, int quantityChange, String notes) {
        if (quantityChange == 0 && type != StockMovementType.INITIAL && type != StockMovementType.IMPORT) {
            return;
        }
        var user = CurrentUser.get();
        var performedBy = user.map(u -> userRepository.getReferenceById(u.getId())).orElse(null);
        String cleanNotes = notes == null || notes.isBlank() ? null : notes.trim();
        stockMovementRepository.save(new StockMovement(product, type, quantityChange, cleanNotes,
                performedBy, user.map(u -> u.getName()).orElse(null)));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> history(Long productId, int page, int size) {
        var movements = stockMovementRepository.findByOrganizationIdAndProductIdOrderByCreatedAtDescIdDesc(
                TenantContext.requireOrganizationId(), productId, PageRequest.of(page, size));
        return PageResponse.of(movements, StockMovementResponse::from);
    }
}
