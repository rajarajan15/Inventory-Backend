package com.example.inventory.service;

import com.example.inventory.dto.PageResponse;
import com.example.inventory.dto.ProductRequest;
import com.example.inventory.dto.ProductResponse;
import com.example.inventory.dto.ProductSummaryResponse;
import com.example.inventory.dto.StockMovementResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.StockMovementType;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.ProductSpecifications;
import com.example.inventory.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_QUANTITY = 1_000_000_000;

    /** Sort keys the API accepts, mapped to entity properties (anything else is rejected, not passed to JPA). */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "sku", "sku",
            "price", "price",
            "quantity", "quantity",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrganizationRepository organizationRepository;
    private final StockLedgerService stockLedger;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          OrganizationRepository organizationRepository,
                          StockLedgerService stockLedger) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.organizationRepository = organizationRepository;
        this.stockLedger = stockLedger;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(String search, Long categoryId, int page, int size, String sort) {
        String query = search != null && !search.isBlank() ? search.trim() : null;
        var pageable = PageRequest.of(page, size, parseSort(sort));
        var spec = ProductSpecifications.inOrganization(TenantContext.requireOrganizationId())
                .and(ProductSpecifications.nameOrSkuContains(query))
                .and(ProductSpecifications.inCategory(categoryId));
        return PageResponse.of(productRepository.findAll(spec, pageable), this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ProductSummaryResponse getSummary() {
        return productRepository.summarize(TenantContext.requireOrganizationId());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return mapToResponse(findInOrganization(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Long orgId = TenantContext.requireOrganizationId();
        String cleanSku = request.getSku().trim().toUpperCase();
        if (productRepository.existsByOrganizationIdAndSku(orgId, cleanSku)) {
            throw new ConflictException("Product with SKU '" + cleanSku + "' already exists");
        }

        Product product = new Product();
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setSku(cleanSku);
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        product.setMinimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : 10);
        product.setCategory(findCategoryInOrganization(request.getCategoryId()));
        product.setOrganization(organizationRepository.getReferenceById(orgId));

        Product saved = productRepository.save(product);
        stockLedger.record(saved, StockMovementType.INITIAL, saved.getQuantity(), "Opening stock");
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Long orgId = TenantContext.requireOrganizationId();
        Product product = lockInOrganization(id);
        if (request.getVersion() != null && !Objects.equals(request.getVersion(), product.getVersion())) {
            throw new ConflictException("This product was changed by someone else while you were editing. "
                    + "Reload it to see the latest details, then apply your changes again.");
        }

        String cleanSku = request.getSku().trim().toUpperCase();
        if (productRepository.existsByOrganizationIdAndSkuAndIdNot(orgId, cleanSku, id)) {
            throw new ConflictException("Product with SKU '" + cleanSku + "' already exists");
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setSku(cleanSku);
        product.setPrice(request.getPrice());
        product.setMinimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : 10);
        product.setCategory(findCategoryInOrganization(request.getCategoryId()));

        if (request.getQuantity() != null && !request.getQuantity().equals(product.getQuantity())) {
            int change = request.getQuantity() - product.getQuantity();
            product.setQuantity(request.getQuantity());
            stockLedger.record(product, StockMovementType.ADJUSTMENT, change, "Quantity edited on the product");
        }

        return mapToResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.delete(findInOrganization(id));
    }

    @Transactional
    public ProductResponse stockIn(Long id, Integer quantity, String notes) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Stock in quantity must be greater than zero");
        }
        Product product = lockInOrganization(id);
        if ((long) product.getQuantity() + quantity > MAX_QUANTITY) {
            throw new BadRequestException("Stock in would exceed the maximum quantity of " + MAX_QUANTITY + " for one product.");
        }
        product.setQuantity(product.getQuantity() + quantity);
        stockLedger.record(product, StockMovementType.STOCK_IN, quantity, notes);
        return mapToResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse stockOut(Long id, Integer quantity, String notes) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Stock out quantity must be greater than zero");
        }
        Product product = lockInOrganization(id);
        if (product.getQuantity() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() + "' (SKU: " + product.getSku() + "). " +
                    "Available: " + product.getQuantity() + ", Requested reduction: " + quantity
            );
        }
        product.setQuantity(product.getQuantity() - quantity);
        stockLedger.record(product, StockMovementType.STOCK_OUT, -quantity, notes);
        return mapToResponse(productRepository.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovements(Long id, int page, int size) {
        findInOrganization(id);
        return stockLedger.history(id, page, size);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts(TenantContext.requireOrganizationId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("name").ascending().and(Sort.by("id"));
        }
        String[] parts = sort.split(",", 2);
        String property = SORTABLE.get(parts[0].trim());
        if (property == null) {
            throw new BadRequestException("Cannot sort by '" + parts[0].trim() + "'. Allowed: " + String.join(", ", SORTABLE.keySet().stream().sorted().toList()) + ".");
        }
        boolean descending = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
        Sort primary = descending ? Sort.by(property).descending() : Sort.by(property).ascending();
        return primary.and(Sort.by("id"));
    }

    private Product findInOrganization(Long id) {
        return productRepository.findByIdAndOrganizationId(id, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Product lockInOrganization(Long id) {
        return productRepository.findForUpdate(id, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Category findCategoryInOrganization(Long categoryId) {
        return categoryRepository.findByIdAndOrganizationId(categoryId, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
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
        ).withVersion(product.getVersion());
    }
}
