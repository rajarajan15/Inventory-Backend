package com.example.inventory.service;

import com.example.inventory.dto.CategoryRequest;
import com.example.inventory.dto.CategoryResponse;
import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ProductRepository productRepository;

    private static final Long ORG_ID = 7L;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(ORG_ID, "acme");
        testCategory = new Category(1L, "Office Supplies", "Desk and paper products");
    }

    @Test
    void testGetAllCategories() {
        when(categoryRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(testCategory));

        List<CategoryResponse> list = categoryService.getAllCategories();

        assertEquals(1, list.size());
        assertEquals("Office Supplies", list.get(0).getName());
    }

    @Test
    void testGetCategoryById_Success() {
        when(categoryRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(testCategory));

        CategoryResponse response = categoryService.getCategoryById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Office Supplies", response.getName());
    }

    @Test
    void testGetCategoryById_NotFound() {
        when(categoryRepository.findByIdAndOrganizationId(99L, ORG_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(99L));
    }

    @Test
    void testCreateCategory_Success() {
        CategoryRequest request = new CategoryRequest("Furniture", "Desks and chairs");

        when(categoryRepository.existsByOrganizationIdAndNameIgnoreCase(ORG_ID, "Furniture")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(2L);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertNotNull(response);
        assertEquals("Furniture", response.getName());
        assertEquals(2L, response.getId());
    }

    @Test
    void testCreateCategory_DuplicateName() {
        CategoryRequest request = new CategoryRequest("Office Supplies", "Duplicate name");

        when(categoryRepository.existsByOrganizationIdAndNameIgnoreCase(ORG_ID, "Office Supplies")).thenReturn(true);

        assertThrows(ConflictException.class, () -> categoryService.createCategory(request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void testDeleteCategory_Success() {
        when(categoryRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(testCategory));

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(testCategory);
    }

    @Test
    void testDeleteCategory_HasProducts_ThrowsConflict() {
        when(categoryRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(testCategory));
        when(productRepository.countByCategoryId(1L)).thenReturn(3L);

        assertThrows(ConflictException.class, () -> categoryService.deleteCategory(1L));
        verify(categoryRepository, never()).delete(any());
    }
}
