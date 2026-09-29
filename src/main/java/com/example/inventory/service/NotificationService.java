package com.example.inventory.service;

import com.example.inventory.entity.Invitation;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.SubscriptionRequest;
import com.example.inventory.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Composes the StockWise notification emails. Links point at the frontend:
 * <ul>
 *     <li>{base}/o/{slug}/...  – an organization's portal</li>
 *     <li>{base}/invite/{token} – invitation acceptance</li>
 *     <li>{base}/platform/...   – super admin portal</li>
 * </ul>
 */
@Service
public class NotificationService {

    private final EmailService emailService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.platform.notification-email:}")
    private String platformNotificationEmail;

    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    public String portalUrl(Organization organization) {
        return baseUrl() + "/o/" + organization.getSlug();
    }

    public void subscriptionRequestReceived(SubscriptionRequest request) {
        emailService.send(platformNotificationEmail,
                "New StockWise subscription request: " + request.getOrganizationName(),
                "A new organization has requested access to StockWise.\n\n"
                        + "Organization: " + request.getOrganizationName() + "\n"
                        + "Contact: " + request.getContactName() + " <" + request.getContactEmail() + ">\n"
                        + "Phone: " + nullToDash(request.getContactPhone()) + "\n"
                        + "Has existing data to import: " + (request.isHasExistingData() ? "Yes" : "No") + "\n"
                        + "Message: " + nullToDash(request.getMessage()) + "\n\n"
                        + "Review it in the super admin portal: " + baseUrl() + "/platform/requests\n");

        emailService.send(request.getContactEmail(),
                "We received your StockWise request",
                "Hi " + request.getContactName() + ",\n\n"
                        + "Thanks for your interest in StockWise. We have received the request for "
                        + request.getOrganizationName() + " and will get back to you shortly.\n\n"
                        + "— The StockWise team\n");
    }

    public void subscriptionRequestRejected(SubscriptionRequest request) {
        emailService.send(request.getContactEmail(),
                "Update on your StockWise request",
                "Hi " + request.getContactName() + ",\n\n"
                        + "Unfortunately we are unable to set up StockWise for " + request.getOrganizationName()
                        + " at this time.\n"
                        + (request.getReviewNote() != null && !request.getReviewNote().isBlank()
                            ? "\nNote from StockWise: " + request.getReviewNote() + "\n" : "")
                        + "\n— The StockWise team\n");
    }

    public void invitation(Invitation invitation) {
        Organization org = invitation.getOrganization();
        String roleLabel = invitation.getRole() == Role.ADMIN ? "an administrator" : "a team member";
        emailService.send(invitation.getEmail(),
                "You're invited to " + org.getName() + " on StockWise",
                "Hi" + (invitation.getName() != null ? " " + invitation.getName() : "") + ",\n\n"
                        + "You have been invited to join " + org.getName() + " on StockWise as " + roleLabel + ".\n\n"
                        + "Set your password and activate your account here:\n"
                        + baseUrl() + "/invite/" + invitation.getToken() + "\n\n"
                        + "This link expires on " + invitation.getExpiresAt().toLocalDate() + ".\n"
                        + "After activating, sign in at: " + portalUrl(org) + "/login\n\n"
                        + "— The StockWise team\n");
    }

    public void userRegistrationPending(User user, Organization org, Collection<String> adminEmails) {
        emailService.sendToAll(adminEmails,
                "New sign-up awaiting approval: " + user.getName(),
                user.getName() + " <" + user.getEmail() + "> has registered for " + org.getName()
                        + " on StockWise and is waiting for approval.\n\n"
                        + "Review pending users: " + portalUrl(org) + "/users?status=PENDING\n");

        emailService.send(user.getEmail(),
                "Your StockWise registration is pending approval",
                "Hi " + user.getName() + ",\n\n"
                        + "Your registration for " + org.getName() + " was received. "
                        + "An administrator of your organization will review it, and you will get an email once you can sign in.\n\n"
                        + "— The StockWise team\n");
    }

    public void userApproved(User user, Organization org) {
        emailService.send(user.getEmail(),
                "Your StockWise account is approved",
                "Hi " + user.getName() + ",\n\n"
                        + "Your account for " + org.getName() + " has been approved. You can sign in now:\n"
                        + portalUrl(org) + "/login\n\n"
                        + "— The StockWise team\n");
    }

    public void userRejected(User user, Organization org) {
        emailService.send(user.getEmail(),
                "Your StockWise registration was not approved",
                "Hi " + user.getName() + ",\n\n"
                        + "Your registration for " + org.getName() + " was not approved. "
                        + "Please contact your organization's administrator if you think this is a mistake.\n\n"
                        + "— The StockWise team\n");
    }

    private String baseUrl() {
        return frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
