package com.example.inventory.config;

import com.example.inventory.entity.Category;
import com.example.inventory.entity.Product;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.repository.CategoryRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            logger.info("Database is empty. Initializing seed data...");

            // 1. Seed Users
            User admin = new User(
                    "System Admin",
                    "admin@inventory.com",
                    passwordEncoder.encode("Admin@123"),
                    Role.ADMIN
            );

            User staff = new User(
                    "John Staff",
                    "staff@inventory.com",
                    passwordEncoder.encode("Staff@123"),
                    Role.STAFF
            );

            userRepository.saveAll(Arrays.asList(admin, staff));
            logger.info("Seeded default users: admin@inventory.com (ADMIN), staff@inventory.com (STAFF)");

            // 2. Seed Categories
            Category electronics = new Category("Electronics", "Electronic devices, computers, and accessories");
            Category officeSupplies = new Category("Office Supplies", "Stationery, paper, pens, and general supplies");
            Category furniture = new Category("Furniture", "Office desks, ergonomic chairs, and filing cabinets");
            Category networking = new Category("Networking", "Cables, switches, and network hardware");

            List<Category> savedCategories = categoryRepository.saveAll(
                    Arrays.asList(electronics, officeSupplies, furniture, networking)
            );
            logger.info("Seeded {} categories", savedCategories.size());

            // 3. Seed Products
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

            productRepository.saveAll(products);
            logger.info("Seeded {} products (including low-stock items)", products.size());
            logger.info("Database initialization completed successfully!");
        } else {
            logger.info("Database already contains data. Skipping initial seeding.");
        }
    }
}
