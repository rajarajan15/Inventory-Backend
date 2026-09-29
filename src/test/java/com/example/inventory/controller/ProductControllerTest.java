package com.example.inventory.controller;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.PageResponse;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.StockOperationRequest;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtService;
import com.example.inventory.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    private static final String PRODUCTS = "/api/orgs/pc-acme/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ProductService productService;

    private Organization acme;
    private Organization globex;

    @BeforeEach
    void setUp() {
        acme = organizationRepository.findBySlug("pc-acme")
                .orElseGet(() -> organizationRepository.save(new Organization("Acme", "pc-acme", null, null)));
        globex = organizationRepository.findBySlug("pc-globex")
                .orElseGet(() -> organizationRepository.save(new Organization("Globex", "pc-globex", null, null)));
    }

    private User user(String email, Role role, UserStatus status, Organization org) {
        return userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(new User("Test " + role, email, "{noop}unused", role, status, org)));
    }

    private ProductResponse sampleProduct(int quantity) {
        return new ProductResponse(1L, "Mouse", "Desc", "SKU-01",
                new BigDecimal("29.99"), quantity, 5, false, 1L, "Electronics", null, null);
    }

    @Test
    void testGetProducts_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get(PRODUCTS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnknownOrganization_Returns404() throws Exception {
        mockMvc.perform(get("/api/orgs/does-not-exist/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void testGetProducts_StaffRole_Success() throws Exception {
        when(productService.searchProducts(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleProduct(10)), 0, 20, 1, 1));

        mockMvc.perform(get(PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mouse"));
    }

    @Test
    void testGetProducts_MemberBearerToken_Success() throws Exception {
        User staff = user("pc-staff@acme.test", Role.STAFF, UserStatus.ACTIVE, acme);
        when(productService.searchProducts(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleProduct(10)), 0, 20, 1, 1));

        mockMvc.perform(get(PRODUCTS)
                        .header("Authorization", "Bearer " + jwtService.generateToken(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mouse"));
    }

    @Test
    void testGetProducts_OtherOrganizationMember_Returns403() throws Exception {
        User globexAdmin = user("pc-admin@globex.test", Role.ADMIN, UserStatus.ACTIVE, globex);

        mockMvc.perform(get(PRODUCTS)
                        .header("Authorization", "Bearer " + jwtService.generateToken(globexAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProducts_SuperAdmin_Returns403() throws Exception {
        User superAdmin = userRepository.findByEmail("owner@stockwise.test").orElseThrow();

        mockMvc.perform(get(PRODUCTS)
                        .header("Authorization", "Bearer " + jwtService.generateToken(superAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProducts_PendingUserToken_Returns401() throws Exception {
        User pending = user("pc-pending@acme.test", Role.STAFF, UserStatus.PENDING, acme);

        mockMvc.perform(get(PRODUCTS)
                        .header("Authorization", "Bearer " + jwtService.generateToken(pending)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void testCreateProduct_StaffRole_Returns403Forbidden() throws Exception {
        ProductRequest request = new ProductRequest("New Item", "Desc", "NEW-01",
                new BigDecimal("49.99"), 10, 5, 1L);

        mockMvc.perform(post(PRODUCTS)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateProduct_AdminRole_Success() throws Exception {
        ProductRequest request = new ProductRequest("New Item", "Desc", "NEW-01",
                new BigDecimal("49.99"), 10, 5, 1L);
        ProductResponse response = new ProductResponse(10L, "New Item", "Desc", "NEW-01",
                new BigDecimal("49.99"), 10, 5, false, 1L, "Electronics", null, null);

        when(productService.createProduct(any(ProductRequest.class))).thenReturn(response);

        mockMvc.perform(post(PRODUCTS)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.sku").value("NEW-01"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void testStockIn_StaffRole_Success() throws Exception {
        StockOperationRequest request = new StockOperationRequest(5, "Received inventory");

        when(productService.stockIn(eq(1L), eq(5), any())).thenReturn(sampleProduct(15));

        mockMvc.perform(post(PRODUCTS + "/1/stock/in")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testPlatformEndpoints_OrgAdmin_Returns403() throws Exception {
        mockMvc.perform(get("/api/platform/organizations"))
                .andExpect(status().isForbidden());
    }
}
