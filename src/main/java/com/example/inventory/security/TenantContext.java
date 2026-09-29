package com.example.inventory.security;

/**
 * Holds the organization resolved for the current request by {@link TenantFilter}.
 * Services read the organization id from here, never from client input, so every
 * query stays inside the caller's own organization.
 */
public final class TenantContext {

    public record Tenant(Long organizationId, String slug) {
    }

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long organizationId, String slug) {
        CURRENT.set(new Tenant(organizationId, slug));
    }

    public static Tenant get() {
        return CURRENT.get();
    }

    public static Long requireOrganizationId() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No organization bound to the current request");
        }
        return tenant.organizationId();
    }

    public static String requireSlug() {
        Tenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No organization bound to the current request");
        }
        return tenant.slug();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
