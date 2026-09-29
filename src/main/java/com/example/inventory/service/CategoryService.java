package com.example.inventory.service;

import com.example.inventory.dto.CategoryRequest;
import com.example.inventory.dto.CategoryResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final OrganizationRepository organizationRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, OrganizationRepository organizationRepository,
                           ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.organizationRepository = organizationRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        Long orgId = TenantContext.requireOrganizationId();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productRepository.countByCategory(orgId)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findByOrganizationId(orgId).stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return mapToResponse(findInOrganization(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Long orgId = TenantContext.requireOrganizationId();
        if (categoryRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, request.getName().trim())) {
            throw new ConflictException("Category already exists with name: " + request.getName());
        }

        Category category = new Category(request.getName().trim(), request.getDescription(),
                organizationRepository.getReferenceById(orgId));
        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findInOrganization(id);

        String newName = request.getName().trim();
        if (!category.getName().equalsIgnoreCase(newName)
                && categoryRepository.existsByOrganizationIdAndNameIgnoreCase(TenantContext.requireOrganizationId(), newName)) {
            throw new ConflictException("Category already exists with name: " + newName);
        }

        category.setName(newName);
        category.setDescription(request.getDescription());
        Category updatedCategory = categoryRepository.save(category);
        return mapToResponse(updatedCategory);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = findInOrganization(id);

        long productCount = productRepository.countByCategoryId(category.getId());
        if (productCount > 0) {
            throw new ConflictException("Cannot delete category because it contains " +
                    productCount + " products. Please reassign or delete the products first.");
        }

        categoryRepository.delete(category);
    }

    private Category findInOrganization(Long id) {
        return categoryRepository.findByIdAndOrganizationId(id, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private CategoryResponse mapToResponse(Category category) {
        return toResponse(category, category.getId() != null ? productRepository.countByCategoryId(category.getId()) : 0);
    }

    private CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), (int) productCount);
    }
}
