package com.example.inventory.dto;

public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private int productCount;

    public CategoryResponse() {
    }

    public CategoryResponse(Long id, String name, String description, int productCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.productCount = productCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }
}
