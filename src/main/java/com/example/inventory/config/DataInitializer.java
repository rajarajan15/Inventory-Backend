package com.example.inventory.config;

import com.example.inventory.entity.Category;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.validation.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds the single StockWise super admin (from SUPER_ADMIN_* settings) and, when app.seed.demo-data=true,
 * a "demo" organization with an admin, a staff user, categories and products for local development.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.email}")
    private String superAdminEmail;

    @Value("${app.super-admin.password:}")
    private String superAdminPassword;

    @Value("${app.super-admin.name:StockWise Owner}")
    private String superAdminName;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    public DataInitializer(UserRepository userRepository,
                           OrganizationRepository organizationRepository,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedSuperAdmin();
        if (seedDemoData && organizationRepository.count() == 0) {
            seedDemoOrganization();
        }
    }

    private void seedSuperAdmin() {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            return;
        }
        if (superAdminPassword == null || superAdminPassword.isBlank()) {
            throw new IllegalStateException("SUPER_ADMIN_PASSWORD is required to create the StockWise super admin on first start.");
        }
        List<String> problems = PasswordPolicy.violations(superAdminPassword);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("SUPER_ADMIN_PASSWORD is too weak. " + PasswordPolicy.describe(problems));
        }
        User superAdmin = new User(
                superAdminName,
                superAdminEmail.trim().toLowerCase(),
                passwordEncoder.encode(superAdminPassword),
                Role.SUPER_ADMIN,
                UserStatus.ACTIVE,
                null
        );
        userRepository.save(superAdmin);
        logger.info("Seeded StockWise super admin: {}", superAdmin.getEmail());
    }

    private void seedDemoOrganization() {
        logger.info("Seeding demo organization (app.seed.demo-data=true)...");

        Organization demo = new Organization("Demo Store", "demo", "Sample organization for local development", "admin@demo.com");
        demo.setSetupCompleted(true);
        demo = organizationRepository.save(demo);

        userRepository.saveAll(Arrays.asList(
                new User("Demo Admin", "admin@demo.com", passwordEncoder.encode("Admin@123"), Role.ADMIN, UserStatus.ACTIVE, demo),
                new User("Demo Staff", "staff@demo.com", passwordEncoder.encode("Staff@123"), Role.STAFF, UserStatus.ACTIVE, demo)
        ));
        logger.info("Seeded demo users: admin@demo.com (ADMIN), staff@demo.com (STAFF)");

        Category electronics = new Category("Electronics", "Electronic devices, computers, and accessories", demo);
        Category officeSupplies = new Category("Office Supplies", "Stationery, paper, pens, and general supplies", demo);
        Category furniture = new Category("Furniture", "Office desks, ergonomic chairs, and filing cabinets", demo);
        Category networking = new Category("Networking", "Cables, switches, and network hardware", demo);
        categoryRepository.saveAll(Arrays.asList(electronics, officeSupplies, furniture, networking));

        List<Product> products = Arrays.asList(
                new Product(null, "Dell UltraSharp 27 Monitor", "27-inch 4K IPS monitor with USB-C hub",
                        "ELEC-MON-001", new BigDecimal("349.99"), 15, 5, electronics),
                new Product(null, "Logitech MX Master 3S Mouse", "Ergonomic wireless precision mouse",
                        "ELEC-MOU-002", new BigDecimal("99.99"), 4, 8, electronics), // Low stock
                new Product(null, "Mechanical Keyboard (Cherry MX)", "Tenkeyless mechanical keyboard with backlight",
                        "ELEC-KEY-003", new BigDecimal("129.50"), 22, 10, electronics),
                new Product(null, "Ergonomic Mesh Chair", "High-back breathable mesh chair with lumbar support",
                        "FURN-CHR-001", new BigDecimal("249.00"), 2, 5, furniture), // Low stock
                new Product(null, "Adjustable Standing Desk", "Motorized dual-motor standing desk 140x70cm",
                        "FURN-DSK-002", new BigDecimal("499.00"), 8, 3, furniture),
                new Product(null, "Gel Ink Pens (Box of 12)", "0.5mm smooth gel black ink pens",
                        "OFF-PEN-001", new BigDecimal("14.99"), 45, 15, officeSupplies),
                new Product(null, "A4 Copy Paper (5 Reams)", "80gsm bright white multipurpose paper",
                        "OFF-PAP-002", new BigDecimal("32.50"), 5, 10, officeSupplies), // Low stock
                new Product(null, "Cat6 Ethernet Cable 10m", "High speed Gigabit RJ45 patch cable",
                        "NET-CAB-001", new BigDecimal("12.00"), 50, 10, networking)
        );
        for (Product product : products) {
            product.setOrganization(demo);
        }
        productRepository.saveAll(products);
        logger.info("Seeded {} demo products (including low-stock items)", products.size());
    }
}
