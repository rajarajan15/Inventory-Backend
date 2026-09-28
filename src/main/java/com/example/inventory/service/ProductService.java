package com.example.inventory.service;

import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(String search, Long categoryId) {
        List<Product> products;
        if ((search != null && !search.trim().isEmpty()) || categoryId != null) {
            String queryParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
            products = productRepository.searchProducts(queryParam, categoryId);
        } else {
            products = productRepository.findAll();
        }

        return products.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String cleanSku = request.getSku().trim().toUpperCase();
        if (productRepository.existsBySku(cleanSku)) {
            throw new BadRequestException("Product with SKU '" + cleanSku + "' already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Product product = new Product();
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setSku(cleanSku);
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        product.setMinimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : 10);
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        String cleanSku = request.getSku().trim().toUpperCase();
        if (productRepository.existsBySkuAndIdNot(cleanSku, id)) {
            throw new BadRequestException("Product with SKU '" + cleanSku + "' already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setSku(cleanSku);
        product.setPrice(request.getPrice());
        if (request.getQuantity() != null) {
            product.setQuantity(request.getQuantity());
        }
        product.setMinimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : 10);
        product.setCategory(category);

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
    }

    @Transactional
    public ProductResponse stockIn(Long id, Integer quantity, String notes) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Stock in quantity must be greater than zero");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        int newQuantity = product.getQuantity() + quantity;
        product.setQuantity(newQuantity);

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse stockOut(Long id, Integer quantity, String notes) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Stock out quantity must be greater than zero");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (product.getQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() + "' (SKU: " + product.getSku() + "). " +
                    "Available: " + product.getQuantity() + ", Requested reduction: " + quantity
            );
        }

        int newQuantity = product.getQuantity() - quantity;
        product.setQuantity(newQuantity);

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ProductResponse mapToResponse(Product product) {
        Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
        String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Uncategorized";

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice(),
                product.getQuantity(),
                product.getMinimumStock(),
                product.isLowStock(),
                categoryId,
                categoryName,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
