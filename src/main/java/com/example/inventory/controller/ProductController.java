package com.example.inventory.controller;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.StockOperationRequest;
import com.example.inventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products & Inventory", description = "Endpoints for product CRUD, stock in/out, and low-stock monitoring")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List all products", description = "Accessible by ADMIN and STAFF. Supports optional search and category filter")
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(productService.getAllProducts(search, categoryId));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low-stock products", description = "Returns all products where current quantity is less than or equal to minimum_stock. Accessible by ADMIN and STAFF")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Accessible by ADMIN and STAFF")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create product", description = "Accessible only by ADMIN")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product", description = "Accessible only by ADMIN")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete product", description = "Accessible only by ADMIN")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stock/in")
    @Operation(summary = "Stock IN operation", description = "Increases product inventory quantity. Accessible by ADMIN and STAFF")
    public ResponseEntity<ProductResponse> stockIn(
            @PathVariable Long id,
            @Valid @RequestBody StockOperationRequest request) {
        return ResponseEntity.ok(productService.stockIn(id, request.getQuantity(), request.getNotes()));
    }

    @PostMapping("/{id}/stock/out")
    @Operation(summary = "Stock OUT operation", description = "Decreases product inventory quantity with insufficient-stock validation. Accessible by ADMIN and STAFF")
    public ResponseEntity<ProductResponse> stockOut(
            @PathVariable Long id,
            @Valid @RequestBody StockOperationRequest request) {
        return ResponseEntity.ok(productService.stockOut(id, request.getQuantity(), request.getNotes()));
    }
}
