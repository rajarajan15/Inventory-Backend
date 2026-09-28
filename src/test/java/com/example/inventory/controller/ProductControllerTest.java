package com.example.inventory.controller;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.StockOperationRequest;
import com.example.inventory.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void testGetProducts_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void testGetProducts_StaffRole_Success() throws Exception {
        ProductResponse p = new ProductResponse(1L, "Mouse", "Desc", "SKU-01",
                new BigDecimal("29.99"), 10, 5, false, 1L, "Electronics", null, null);

        when(productService.getAllProducts(any(), any())).thenReturn(List.of(p));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mouse"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void testCreateProduct_StaffRole_Returns403Forbidden() throws Exception {
        ProductRequest request = new ProductRequest("New Item", "Desc", "NEW-01",
                new BigDecimal("49.99"), 10, 5, 1L);

        mockMvc.perform(post("/api/products")
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

        mockMvc.perform(post("/api/products")
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
        ProductResponse response = new ProductResponse(1L, "Mouse", "Desc", "SKU-01",
                new BigDecimal("29.99"), 15, 5, false, 1L, "Electronics", null, null);

        when(productService.stockIn(eq(1L), eq(5), any())).thenReturn(response);

        mockMvc.perform(post("/api/products/1/stock/in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
    }
}
