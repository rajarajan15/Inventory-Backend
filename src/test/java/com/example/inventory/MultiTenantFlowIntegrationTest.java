package com.example.inventory;

import com.example.inventory.entity.Invitation;
import com.example.inventory.repository.InvitationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end StockWise onboarding over HTTP against the real services and (H2) database:
 * subscription request → super admin creates org + invites admin → admin accepts invite → admin imports CSV →
 * a user self-registers → stays blocked until the org admin approves → tenant isolation holds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiTenantFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvitationRepository invitationRepository;

    @Test
    void fullOnboardingFlow() throws Exception {
        // 1. A prospective client submits the public subscription request form
        postJson("/api/public/subscription-requests", null, Map.of(
                "organizationName", "Flow Mart",
                "contactName", "Fiona",
                "contactEmail", "fiona@flowmart.test",
                "hasExistingData", true))
                .andExpect(status().isCreated());

        // 2. Super admin logs in to the platform portal and sees the pending request
        String superToken = accessToken(postJson("/api/platform/auth/login", null,
                Map.of("email", "owner@stockwise.test", "password", "Owner@12345")));

        JsonNode requests = json(mockMvc.perform(get("/api/platform/subscription-requests?status=PENDING")
                .header("Authorization", bearer(superToken))).andExpect(status().isOk()));
        long requestId = -1;
        for (JsonNode r : requests) {
            if ("Flow Mart".equals(r.get("organizationName").asText())) {
                requestId = r.get("id").asLong();
            }
        }

        // 3. Super admin creates the organization (its URL) and invites Fiona as org admin
        postJson("/api/platform/organizations", superToken, Map.of(
                "name", "Flow Mart",
                "slug", "flow-mart",
                "adminName", "Fiona",
                "adminEmail", "fiona@flowmart.test",
                "subscriptionRequestId", requestId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("flow-mart"))
                .andExpect(jsonPath("$.portalUrl").value("http://localhost:5173/o/flow-mart"));

        // An org admin cannot reach the platform, and the slug cannot be reused
        postJson("/api/platform/organizations", superToken, Map.of("name", "Dup", "slug", "flow-mart"))
                .andExpect(status().isBadRequest());

        // 4. Fiona opens the emailed invitation link and sets her password
        String inviteToken = invitationToken("fiona@flowmart.test");
        mockMvc.perform(get("/api/public/invitations/" + inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.organizationSlug").value("flow-mart"));
        postJson("/api/public/invitations/" + inviteToken + "/accept", null,
                Map.of("name", "Fiona Admin", "password", "Fiona@1234"))
                .andExpect(status().isOk());
        postJson("/api/public/invitations/" + inviteToken + "/accept", null,
                Map.of("name", "Fiona Admin", "password", "Fiona@1234"))
                .andExpect(status().isBadRequest()); // single use

        // 5. Fiona logs in on her organization's portal
        String adminToken = accessToken(postJson("/api/orgs/flow-mart/auth/login", null,
                Map.of("email", "fiona@flowmart.test", "password", "Fiona@1234")));
        mockMvc.perform(get("/api/platform/organizations").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        // 6. Existing data: Fiona imports her CSV (products + a team member invitation)
        String csv = "type,name,sku,price,quantity,category,email,role\n"
                + "PRODUCT,Widget,W-1,9.99,3,Parts,,\n"
                + "PRODUCT,\"Gadget, large\",G-1,19.50,40,Parts,,\n"
                + "USER,Sam,,,,,sam@flowmart.test,STAFF\n";
        mockMvc.perform(multipart("/api/orgs/flow-mart/organization/import-csv")
                        .file(new MockMultipartFile("file", "data.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedProductsCount").value(2))
                .andExpect(jsonPath("$.importedCategoriesCount").value(0))
                .andExpect(jsonPath("$.importedUsersCount").value(1));
        mockMvc.perform(get("/api/orgs/flow-mart/products").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/orgs/flow-mart/organization").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.setupCompleted").value(true));

        // 7. A new user self-registers on the portal: pending, cannot log in yet
        postJson("/api/orgs/flow-mart/auth/register", null,
                Map.of("name", "Pat", "email", "pat@flowmart.test", "password", "Pat@12345"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
        postJson("/api/orgs/flow-mart/auth/login", null, Map.of("email", "pat@flowmart.test", "password", "Pat@12345"))
                .andExpect(status().isForbidden());

        // 8. Fiona approves Pat; Pat can now log in
        JsonNode pending = json(mockMvc.perform(get("/api/orgs/flow-mart/users?status=PENDING")
                .header("Authorization", bearer(adminToken))).andExpect(status().isOk()));
        long patId = pending.get(0).get("id").asLong();
        mockMvc.perform(post("/api/orgs/flow-mart/users/" + patId + "/approve").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        String patToken = accessToken(postJson("/api/orgs/flow-mart/auth/login", null,
                Map.of("email", "pat@flowmart.test", "password", "Pat@12345")));

        // Staff can read inventory but cannot manage users or import data
        mockMvc.perform(get("/api/orgs/flow-mart/products").header("Authorization", bearer(patToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orgs/flow-mart/users").header("Authorization", bearer(patToken)))
                .andExpect(status().isForbidden());

        // 9. Isolation: a second organization cannot see Flow Mart, and Flow Mart users cannot log in there
        postJson("/api/platform/organizations", superToken, Map.of(
                "name", "Other Shop", "slug", "other-shop", "adminEmail", "olga@other.test"))
                .andExpect(status().isCreated());
        postJson("/api/public/invitations/" + invitationToken("olga@other.test") + "/accept", null,
                Map.of("name", "Olga", "password", "Olga@12345"))
                .andExpect(status().isOk());
        String olgaToken = accessToken(postJson("/api/orgs/other-shop/auth/login", null,
                Map.of("email", "olga@other.test", "password", "Olga@12345")));

        mockMvc.perform(get("/api/orgs/other-shop/products").header("Authorization", bearer(olgaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/orgs/flow-mart/products").header("Authorization", bearer(olgaToken)))
                .andExpect(status().isForbidden());
        postJson("/api/orgs/other-shop/auth/login", null, Map.of("email", "fiona@flowmart.test", "password", "Fiona@1234"))
                .andExpect(status().isUnauthorized());

        // Names are unique per organization, not globally: Flow Mart already has a "Parts" category
        postJson("/api/orgs/other-shop/categories", olgaToken, Map.of("name", "Parts"))
                .andExpect(status().isCreated());

        // Super admin cannot read tenant inventory
        mockMvc.perform(get("/api/orgs/flow-mart/products").header("Authorization", bearer(superToken)))
                .andExpect(status().isForbidden());

        // 10. Suspending an organization blocks its portal
        JsonNode orgs = json(mockMvc.perform(get("/api/platform/organizations").header("Authorization", bearer(superToken))));
        long otherShopId = -1;
        for (JsonNode o : orgs) {
            if ("other-shop".equals(o.get("slug").asText())) {
                otherShopId = o.get("id").asLong();
            }
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/platform/organizations/" + otherShopId + "/status")
                        .header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orgs/other-shop/products").header("Authorization", bearer(olgaToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/public/organizations/other-shop"))
                .andExpect(status().isNotFound());
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request);
    }

    private String accessToken(ResultActions result) throws Exception {
        return json(result.andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String invitationToken(String email) {
        return invitationRepository.findAll().stream()
                .filter(i -> i.getEmail().equals(email) && i.getAcceptedAt() == null)
                .map(Invitation::getToken)
                .findFirst()
                .orElseThrow();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
