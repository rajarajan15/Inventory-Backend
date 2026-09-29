package com.example.inventory.service;

import com.example.inventory.dto.CsvImportResponse;
import com.example.inventory.dto.OrganizationResponse;
import com.example.inventory.dto.OrganizationSetupRequest;
import com.example.inventory.entity.Category;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.StockMovementType;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.InvitationRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The current organization's own profile, first-login setup and CSV onboarding.
 * Only that organization's admin can import data, and imported rows are always bound to that organization.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InvitationService invitationService;
    private final NotificationService notificationService;
    private final StockLedgerService stockLedger;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            InvitationRepository invitationRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            InvitationService invitationService,
            NotificationService notificationService,
            StockLedgerService stockLedger
    ) {
        this.stockLedger = stockLedger;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.invitationService = invitationService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentOrganization() {
        return toResponse(currentOrganization());
    }

    /**
     * NEW organization (no existing data): setup is complete and the admin starts creating data.
     * EXISTING data: setup stays open until the admin uploads a CSV.
     */
    @Transactional
    public OrganizationResponse setupOrganization(OrganizationSetupRequest request) {
        Organization org = currentOrganization();
        org.setSetupCompleted(!Boolean.TRUE.equals(request.hasExistingData()));
        return toResponse(organizationRepository.save(org));
    }

    @Transactional
    public CsvImportResponse importCsvData(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("The selected file is empty. Please choose a CSV file with data.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".csv")) {
            throw new BadRequestException("Please upload a .csv file. Excel files can be saved as CSV via File > Save As > CSV.");
        }

        Organization org = currentOrganization();
        int usersCount = 0;
        int categoriesCount = 0;
        int productsCount = 0;
        List<String> warnings = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                throw new BadRequestException("CSV file is empty");
            }

            // Remove UTF-8 BOM if present
            if (firstLine.startsWith("﻿")) {
                firstLine = firstLine.substring(1);
            }

            String[] header = parseCsvLine(firstLine);
            Map<String, Integer> colMap = buildColumnMap(header);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;

                String[] row = parseCsvLine(line);

                String recordType = getColValue(row, colMap, "type");
                if (recordType == null || recordType.isBlank()) {
                    // Smart detection based on available columns
                    if (colMap.containsKey("sku") || colMap.containsKey("price")) {
                        recordType = "PRODUCT";
                    } else if (colMap.containsKey("email") || colMap.containsKey("role")) {
                        recordType = "USER";
                    } else if (colMap.containsKey("category") || colMap.containsKey("categoryname")) {
                        recordType = "CATEGORY";
                    } else {
                        warnings.add("Line " + lineNumber + ": Unable to determine record type. Skipping line.");
                        continue;
                    }
                }

                switch (recordType.trim().toUpperCase()) {
                    case "USER", "TEAM", "MEMBER" -> {
                        if (processUserRow(org, row, colMap, lineNumber, warnings)) usersCount++;
                    }
                    case "CATEGORY" -> {
                        if (processCategoryRow(org, row, colMap, lineNumber, warnings)) categoriesCount++;
                    }
                    case "PRODUCT" -> {
                        if (processProductRow(org, row, colMap, lineNumber, warnings)) productsCount++;
                    }
                    default -> warnings.add("Line " + lineNumber + ": Unknown record type '" + recordType + "'. Skipping line.");
                }
            }

        } catch (IOException e) {
            throw new BadRequestException("The CSV file could not be read. Make sure it is a UTF-8 encoded .csv file.");
        }

        org.setSetupCompleted(true);
        organizationRepository.save(org);

        String msg = String.format("Successfully imported %d categories and %d products, and invited %d team members by email.",
                categoriesCount, productsCount, usersCount);

        return new CsvImportResponse(usersCount, categoriesCount, productsCount, msg, warnings);
    }

    /** Team members are invited by email to set their own password; no default passwords are ever created. */
    private boolean processUserRow(Organization org, String[] row, Map<String, Integer> colMap, int lineNum, List<String> warnings) {
        String name = getColValue(row, colMap, "name");
        String email = getColValue(row, colMap, "email");
        String roleStr = getColValue(row, colMap, "role");

        if (email == null || email.isBlank()) {
            warnings.add("Line " + lineNum + ": Missing email for user. Skipping user.");
            return false;
        }

        email = AuthService.normalizeEmail(email);
        if (userRepository.existsByEmail(email)) {
            warnings.add("Line " + lineNum + ": User with email '" + email + "' already exists. Skipping user.");
            return false;
        }
        if (invitationRepository.existsByEmailAndAcceptedAtIsNullAndExpiresAtAfter(email, LocalDateTime.now())) {
            warnings.add("Line " + lineNum + ": '" + email + "' already has a pending invitation. Skipping user.");
            return false;
        }

        Role role = roleStr != null && roleStr.equalsIgnoreCase("ADMIN") ? Role.ADMIN : Role.STAFF;
        invitationService.invite(org, name, email, role);
        return true;
    }

    private boolean processCategoryRow(Organization org, String[] row, Map<String, Integer> colMap, int lineNum, List<String> warnings) {
        String name = getColValue(row, colMap, "name");
        if (name == null || name.isBlank()) {
            name = getColValue(row, colMap, "categoryname");
        }
        String description = getColValue(row, colMap, "description");

        if (name == null || name.isBlank()) {
            warnings.add("Line " + lineNum + ": Missing category name. Skipping category.");
            return false;
        }

        name = name.trim();
        if (categoryRepository.existsByOrganizationIdAndNameIgnoreCase(org.getId(), name)) {
            warnings.add("Line " + lineNum + ": Category '" + name + "' already exists. Skipping category.");
            return false;
        }

        categoryRepository.save(new Category(name, description, org));
        return true;
    }

    private boolean processProductRow(Organization org, String[] row, Map<String, Integer> colMap, int lineNum, List<String> warnings) {
        String name = getColValue(row, colMap, "name");
        String sku = getColValue(row, colMap, "sku");
        String priceStr = getColValue(row, colMap, "price");
        String quantityStr = getColValue(row, colMap, "quantity");
        if (quantityStr == null) quantityStr = getColValue(row, colMap, "qty");
        String minStockStr = getColValue(row, colMap, "minimumstock");
        if (minStockStr == null) minStockStr = getColValue(row, colMap, "minstock");
        String categoryName = getColValue(row, colMap, "categoryname");
        if (categoryName == null) categoryName = getColValue(row, colMap, "category");
        String description = getColValue(row, colMap, "description");

        if (name == null || name.isBlank() || sku == null || sku.isBlank() || priceStr == null || priceStr.isBlank()) {
            warnings.add("Line " + lineNum + ": Missing required product fields (name, sku, or price). Skipping product.");
            return false;
        }

        sku = sku.trim().toUpperCase();
        if (productRepository.existsByOrganizationIdAndSku(org.getId(), sku)) {
            warnings.add("Line " + lineNum + ": Product with SKU '" + sku + "' already exists. Skipping product.");
            return false;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceStr.trim());
        } catch (NumberFormatException e) {
            warnings.add("Line " + lineNum + ": Invalid price '" + priceStr + "'. Skipping product.");
            return false;
        }
        if (price.signum() < 0) {
            warnings.add("Line " + lineNum + ": Negative price '" + priceStr + "'. Skipping product.");
            return false;
        }

        int quantity = parseNonNegativeInt(quantityStr, 0, "quantity", lineNum, warnings);
        int minimumStock = parseNonNegativeInt(minStockStr, 10, "minimum stock", lineNum, warnings);

        Category category = null;
        if (categoryName != null && !categoryName.isBlank()) {
            String catNameClean = categoryName.trim();
            category = categoryRepository.findByOrganizationIdAndNameIgnoreCase(org.getId(), catNameClean)
                    .orElseGet(() -> categoryRepository.save(new Category(catNameClean, "Imported category", org)));
        }

        Product product = new Product(name.trim(), description, sku, price, quantity, minimumStock, category);
        product.setOrganization(org);
        stockLedger.record(productRepository.save(product), StockMovementType.IMPORT, quantity, "Imported from CSV");
        return true;
    }

    private int parseNonNegativeInt(String value, int defaultValue, String field, int lineNum, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        warnings.add("Line " + lineNum + ": Invalid " + field + " '" + value + "', using " + defaultValue + ".");
        return defaultValue;
    }

    private Map<String, Integer> buildColumnMap(String[] header) {
        Map<String, Integer> colMap = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String col = header[i].trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            colMap.put(col, i);
        }
        return colMap;
    }

    private String getColValue(String[] row, Map<String, Integer> colMap, String key) {
        Integer index = colMap.get(key);
        if (index == null || index >= row.length) {
            return null;
        }
        return row[index].trim();
    }

    /** Minimal RFC 4180 line parser: handles quoted fields, commas inside quotes and escaped ("") quotes. */
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private Organization currentOrganization() {
        return organizationRepository.findById(TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private OrganizationResponse toResponse(Organization organization) {
        return OrganizationResponse.fromEntity(organization, notificationService.portalUrl(organization));
    }
}
