package com.example.inventory;

import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock ledger, concurrency safety, paging, dashboard totals and operational endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrityTest {

    private static final String ORG = "/api/orgs/integrity-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        Organization org = organizationRepository.findBySlug("integrity-org")
                .orElseGet(() -> organizationRepository.save(new Organization("Integrity Org", "integrity-org", null, null)));
        if (!userRepository.existsByEmail("admin@integrity.test")) {
            userRepository.save(new User("Ida Admin", "admin@integrity.test", passwordEncoder.encode("Admin@1234"), Role.ADMIN, UserStatus.ACTIVE, org));
        }
        token = json(postJson(ORG + "/auth/login", Map.of("email", "admin@integrity.test", "password", "Admin@1234"))
                .andExpect(status().isOk())).get("accessToken").asText();
        categoryId = json(postJson(ORG + "/categories", Map.of("name", "Cat " + UUID.randomUUID()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void stockChangesAreRecordedInTheLedger() throws Exception {
        long id = createProduct("LEDGER", 10);

        postJson(ORG + "/products/" + id + "/stock/in", Map.of("quantity", 5, "notes", "Delivery #42")).andExpect(status().isOk());
        postJson(ORG + "/products/" + id + "/stock/out", Map.of("quantity", 3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12));

        mockMvc.perform(get(ORG + "/products/" + id + "/movements").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("STOCK_OUT"))
                .andExpect(jsonPath("$.content[0].quantityChange").value(-3))
                .andExpect(jsonPath("$.content[0].quantityAfter").value(12))
                .andExpect(jsonPath("$.content[0].performedBy").value("Ida Admin"))
                .andExpect(jsonPath("$.content[1].type").value("STOCK_IN"))
                .andExpect(jsonPath("$.content[1].notes").value("Delivery #42"))
                .andExpect(jsonPath("$.content[2].type").value("INITIAL"))
                .andExpect(jsonPath("$.content[2].quantityAfter").value(10));
    }

    @Test
    void editingQuantityRecordsAnAdjustment() throws Exception {
        long id = createProduct("ADJ", 10);
        Map<String, Object> body = productBody("ADJ-" + id, 7);
        body.put("version", productVersion(id));
        putJson(ORG + "/products/" + id, body).andExpect(status().isOk());

        mockMvc.perform(get(ORG + "/products/" + id + "/movements").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].quantityChange").value(-3));
    }

    @Test
    void staleEditIsRejectedWithConflict() throws Exception {
        long id = createProduct("STALE", 10);
        long version = productVersion(id);

        Map<String, Object> first = productBody("STALE-" + id, 10);
        first.put("version", version);
        first.put("name", "First edit");
        putJson(ORG + "/products/" + id, first).andExpect(status().isOk());

        Map<String, Object> second = productBody("STALE-" + id, 10);
        second.put("version", version); // based on the old version
        second.put("name", "Second edit");
        putJson(ORG + "/products/" + id, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(matchesPattern(".*changed by someone else.*")));
    }

    @Test
    void concurrentStockOutNeverOversells() throws Exception {
        long id = createProduct("RACE", 5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                calls.add(() -> postJson(ORG + "/products/" + id + "/stock/out", Map.of("quantity", 1))
                        .andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(5);
            assertThat(statuses).allMatch(s -> s == 200 || s == 400 || s == 409);
        } finally {
            pool.shutdownNow();
        }
        mockMvc.perform(get(ORG + "/products/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.quantity").value(0));
        mockMvc.perform(get(ORG + "/products/" + id + "/movements?size=100").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(6)); // INITIAL + 5 successful stock-outs
    }

    @Test
    void productListIsPagedAndSortable() throws Exception {
        String prefix = "PG" + System.nanoTime();
        for (int i = 0; i < 3; i++) {
            createProductWithSku(prefix + "-" + i, i);
        }
        mockMvc.perform(get(ORG + "/products?search=" + prefix + "&size=2&sort=quantity,desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].quantity").value(2));

        mockMvc.perform(get(ORG + "/products?sort=password").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(matchesPattern("Cannot sort by 'password'.*")));
        mockMvc.perform(get(ORG + "/products?size=1000").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be at most 100"));
    }

    @Test
    void summaryIsComputedInTheDatabase() throws Exception {
        JsonNode before = json(mockMvc.perform(get(ORG + "/products/summary").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
        createProductWithSku("SUM" + System.nanoTime(), 4); // price 2.50, minimum stock 5 -> low stock
        mockMvc.perform(get(ORG + "/products/summary").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalProducts").value(before.get("totalProducts").asLong() + 1))
                .andExpect(jsonPath("$.totalUnits").value(before.get("totalUnits").asLong() + 4))
                .andExpect(jsonPath("$.lowStockCount").value(before.get("lowStockCount").asLong() + 1))
                .andExpect(jsonPath("$.inventoryValue").value(before.get("inventoryValue").decimalValue().add(new java.math.BigDecimal("10.00")).doubleValue()));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get(ORG + "/products/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get(ORG + "/products").header("X-Request-Id", "gateway-trace-1234"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "gateway-trace-1234"))
                .andExpect(jsonPath("$.requestId").value("gateway-trace-1234"));

        // Unsafe incoming IDs are replaced, never reflected
        mockMvc.perform(get("/api/health").header("X-Request-Id", "<script>alert(1)</script>"))
                .andExpect(header().string("X-Request-Id", matchesPattern("^[0-9a-f-]{36}$")));
    }

    @Test
    void healthProbesArePublicAndRevealNothing() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private long createProduct(String skuPrefix, int quantity) throws Exception {
        return createProductWithSku(skuPrefix + "-" + System.nanoTime(), quantity);
    }

    private long createProductWithSku(String sku, int quantity) throws Exception {
        return json(postJson(ORG + "/products", productBody(sku, quantity)).andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> productBody(String sku, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Product " + sku);
        body.put("sku", sku);
        body.put("price", "2.50");
        body.put("quantity", quantity);
        body.put("minimumStock", 5);
        body.put("categoryId", categoryId);
        return body;
    }

    private long productVersion(long id) throws Exception {
        return json(mockMvc.perform(get(ORG + "/products/" + id).header("Authorization", "Bearer " + token))).get("version").asLong();
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return mockMvc.perform(put(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
