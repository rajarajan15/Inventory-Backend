package com.example.inventory.controller;

import com.example.inventory.dto.CsvImportResponse;
import com.example.inventory.dto.OrganizationResponse;
import com.example.inventory.dto.OrganizationSetupRequest;
import com.example.inventory.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/orgs/{orgSlug}/organization")
@Tag(name = "Organization", description = "The current organization's profile, first-login setup and CSV data onboarding")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "Get organization", description = "Current organization profile and setup status. Accessible by ADMIN and STAFF")
    public ResponseEntity<OrganizationResponse> getOrganization() {
        return ResponseEntity.ok(organizationService.getCurrentOrganization());
    }

    @PostMapping("/setup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Setup organization", description = "Admin chooses to start fresh (hasExistingData=false) or import existing data via CSV (hasExistingData=true)")
    public ResponseEntity<OrganizationResponse> setupOrganization(@Valid @RequestBody OrganizationSetupRequest request) {
        return ResponseEntity.ok(organizationService.setupOrganization(request));
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Import CSV data", description = "Admin uploads the organization's existing categories, products and team members (invited by email) as CSV")
    public ResponseEntity<CsvImportResponse> importCsvData(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(organizationService.importCsvData(file));
    }
}
