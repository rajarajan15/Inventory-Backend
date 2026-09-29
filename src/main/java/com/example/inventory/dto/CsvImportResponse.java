package com.example.inventory.dto;

import java.util.List;

public class CsvImportResponse {

    private int importedUsersCount;
    private int importedCategoriesCount;
    private int importedProductsCount;
    private String message;
    private List<String> warningsOrSkipped;

    public CsvImportResponse() {
    }

    public CsvImportResponse(int importedUsersCount, int importedCategoriesCount, int importedProductsCount, String message, List<String> warningsOrSkipped) {
        this.importedUsersCount = importedUsersCount;
        this.importedCategoriesCount = importedCategoriesCount;
        this.importedProductsCount = importedProductsCount;
        this.message = message;
        this.warningsOrSkipped = warningsOrSkipped;
    }

    public int getImportedUsersCount() {
        return importedUsersCount;
    }

    public void setImportedUsersCount(int importedUsersCount) {
        this.importedUsersCount = importedUsersCount;
    }

    public int getImportedCategoriesCount() {
        return importedCategoriesCount;
    }

    public void setImportedCategoriesCount(int importedCategoriesCount) {
        this.importedCategoriesCount = importedCategoriesCount;
    }

    public int getImportedProductsCount() {
        return importedProductsCount;
    }

    public void setImportedProductsCount(int importedProductsCount) {
        this.importedProductsCount = importedProductsCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarningsOrSkipped() {
        return warningsOrSkipped;
    }

    public void setWarningsOrSkipped(List<String> warningsOrSkipped) {
        this.warningsOrSkipped = warningsOrSkipped;
    }
}
