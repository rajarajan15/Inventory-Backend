package com.example.inventory.service;

import com.example.inventory.dto.SubscriptionRequestCreateRequest;
import com.example.inventory.dto.SubscriptionRequestResponse;
import com.example.inventory.entity.SubscriptionRequest;
import com.example.inventory.entity.SubscriptionRequestStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.SubscriptionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionRequestService {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final NotificationService notificationService;

    public SubscriptionRequestService(SubscriptionRequestRepository subscriptionRequestRepository,
                                      NotificationService notificationService) {
        this.subscriptionRequestRepository = subscriptionRequestRepository;
        this.notificationService = notificationService;
    }

    /** Public "request StockWise for my organization" form. Emails StockWise and acknowledges the requester. */
    @Transactional
    public SubscriptionRequestResponse create(SubscriptionRequestCreateRequest request) {
        SubscriptionRequest entity = new SubscriptionRequest();
        entity.setOrganizationName(request.organizationName().trim());
        entity.setContactName(request.contactName().trim());
        entity.setContactEmail(AuthService.normalizeEmail(request.contactEmail()));
        entity.setContactPhone(request.contactPhone());
        entity.setHasExistingData(Boolean.TRUE.equals(request.hasExistingData()));
        entity.setMessage(request.message());

        SubscriptionRequest saved = subscriptionRequestRepository.save(entity);
        notificationService.subscriptionRequestReceived(saved);
        return SubscriptionRequestResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRequestResponse> list(SubscriptionRequestStatus status) {
        List<SubscriptionRequest> requests = status == null
                ? subscriptionRequestRepository.findAllByOrderByCreatedAtDesc()
                : subscriptionRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        return requests.stream().map(SubscriptionRequestResponse::fromEntity).toList();
    }

    @Transactional
    public SubscriptionRequestResponse reject(Long id, String note) {
        SubscriptionRequest request = findPending(id);
        request.setStatus(SubscriptionRequestStatus.REJECTED);
        request.setReviewNote(note);
        request.setReviewedAt(LocalDateTime.now());
        SubscriptionRequest saved = subscriptionRequestRepository.save(request);
        notificationService.subscriptionRequestRejected(saved);
        return SubscriptionRequestResponse.fromEntity(saved);
    }

    SubscriptionRequest findPending(Long id) {
        SubscriptionRequest request = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription request not found with id: " + id));
        if (request.getStatus() != SubscriptionRequestStatus.PENDING) {
            throw new BadRequestException("Subscription request " + id + " was already " + request.getStatus().name().toLowerCase());
        }
        return request;
    }
}
