package com.example.inventory.security;

import com.example.inventory.entity.Organization;
import com.example.inventory.entity.User;
import com.example.inventory.exception.ErrorResponse;
import com.example.inventory.repository.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the organization for every {@code /api/orgs/{slug}/**} request and enforces isolation:
 * <ul>
 *     <li>unknown slug → 404, suspended organization → 403</li>
 *     <li>an authenticated caller must belong to that organization → otherwise 403
 *         (this also keeps the super admin out of tenant data)</li>
 * </ul>
 * Registered inside the security chain after JWT authentication (deliberately not a {@code @Component},
 * so Spring Boot does not also register it as a plain servlet filter that would run before authentication).
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_PATH = Pattern.compile("^/api/orgs/([^/]+)(/.*)?$");

    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public TenantFilter(OrganizationRepository organizationRepository, ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Matcher matcher = TENANT_PATH.matcher(request.getRequestURI().substring(request.getContextPath().length()));
        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        String slug = matcher.group(1).toLowerCase();
        Organization organization = organizationRepository.findBySlug(slug).orElse(null);
        if (organization == null) {
            writeError(request, response, HttpServletResponse.SC_NOT_FOUND, "Not Found", "Organization not found: " + slug);
            return;
        }
        if (!organization.isActive()) {
            writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "This organization's StockWise access is suspended. Please contact StockWise support.");
            return;
        }

        User user = CurrentUser.get().orElse(null);
        if (user != null) {
            Organization userOrg = user.getOrganization();
            if (userOrg == null || !organization.getId().equals(userOrg.getId())) {
                writeError(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                        "Access denied: you are not a member of this organization");
                return;
            }
        }

        try {
            TenantContext.set(organization.getId(), organization.getSlug());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(status, error, message, request.getRequestURI()));
    }
}
