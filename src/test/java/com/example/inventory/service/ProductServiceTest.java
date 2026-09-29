package com.example.inventory.service;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.StockMovementType;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Category testCategory;
    private Product testProduct;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StockLedgerService stockLedger;

    private static final Long ORG_ID = 7L;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(ORG_ID, "acme");
        testCategory = new Category(1L, "Electronics", "Devices and gadgets");
        testProduct = new Product(1L, "Test Mouse", "Wireless mouse", "TEST-MOU-01",
                new BigDecimal("29.99"), 10, 5, testCategory);
    }

    @Test
    void testGetProductById_Success() {
        when(productRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(testProduct));

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals("Test Mouse", response.getName());
        assertEquals("TEST-MOU-01", response.getSku());
        assertEquals(10, response.getQuantity());
        assertFalse(response.isLowStock());
    }

    @Test
    void testGetProductById_NotFound() {
        when(productRepository.findByIdAndOrganizationId(99L, ORG_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(99L));
    }

    @Test
    void testCreateProduct_Success() {
        ProductRequest request = new ProductRequest("New Keyboard", "Mechanical keyboard",
                "NEW-KEY-01", new BigDecimal("79.99"), 15, 5, 1L);

        when(productRepository.existsByOrganizationIdAndSku(ORG_ID, "NEW-KEY-01")).thenReturn(false);
        when(categoryRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("New Keyboard", response.getName());
        assertEquals("NEW-KEY-01", response.getSku());
        assertEquals(15, response.getQuantity());
        verify(stockLedger).record(any(Product.class), eq(StockMovementType.INITIAL), eq(15), any());
    }

    @Test
    void testCreateProduct_DuplicateSku() {
        ProductRequest request = new ProductRequest("New Keyboard", "Mechanical keyboard",
                "TEST-MOU-01", new BigDecimal("79.99"), 15, 5, 1L);

        when(productRepository.existsByOrganizationIdAndSku(ORG_ID, "TEST-MOU-01")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void testStockIn_Success() {
        when(productRepository.findForUpdate(1L, ORG_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.stockIn(1L, 5, "Received new shipment");

        assertNotNull(response);
        assertEquals(15, response.getQuantity()); // 10 + 5
        verify(stockLedger).record(testProduct, StockMovementType.STOCK_IN, 5, "Received new shipment");
    }

    @Test
    void testStockOut_Success() {
        when(productRepository.findForUpdate(1L, ORG_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.stockOut(1L, 4, "Sold 4 items");

        assertNotNull(response);
        assertEquals(6, response.getQuantity()); // 10 - 4
        verify(stockLedger).record(testProduct, StockMovementType.STOCK_OUT, -4, "Sold 4 items");
    }

    @Test
    void testStockOut_InsufficientStock() {
        when(productRepository.findForUpdate(1L, ORG_ID)).thenReturn(Optional.of(testProduct));

        // Available is 10, requesting 15 -> should throw InsufficientStockException
        assertThrows(InsufficientStockException.class, () ->
                productService.stockOut(1L, 15, "Sale request exceeds available"));

        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stockLedger);
    }

    @Test
    void testLowStockDetection() {
        Product lowStockProduct = new Product(2L, "Low Item", "Desc", "LOW-01",
                new BigDecimal("9.99"), 3, 5, testCategory);

        when(productRepository.findLowStockProducts(ORG_ID)).thenReturn(List.of(lowStockProduct));

        List<ProductResponse> lowStock = productService.getLowStockProducts();

        assertEquals(1, lowStock.size());
        assertTrue(lowStock.get(0).isLowStock());
        assertEquals("LOW-01", lowStock.get(0).getSku());
    }
}
