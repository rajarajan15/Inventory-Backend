# Inventory Management System - Backend

Enterprise-grade Spring Boot backend for the Inventory Management System, built according to the prototype blueprint with PostgreSQL persistence, Spring Security with JWT authentication, role-based authorization (ADMIN / STAFF), and Swagger OpenAPI documentation.

---

## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Framework** | Spring Boot 3.3.4 | Core REST API & Business Logic |
| **Language** | Java 21 / 25 | Modern Java LTS |
| **Database** | PostgreSQL 17 | Relational persistence |
| **Security** | Spring Security + JJWT 0.12 | JWT Authentication & RBAC |
| **ORM** | Spring Data JPA / Hibernate | Entity mapping & repositories |
| **Validation** | Jakarta Bean Validation | Request payload validation |
| **Documentation**| SpringDoc OpenAPI 2.6.0 | Swagger UI interactive docs |
| **Build Tool** | Maven 3.9.9 | Dependency management & packaging |

---

## 🔐 Roles & Permissions

| Operation | Endpoint | ADMIN | STAFF |
|---|---|:---:|:---:|
| **Register & Login** | `POST /api/auth/**` | Public | Public |
| **View Products** | `GET /api/products` | ✅ | ✅ |
| **View Product Details** | `GET /api/products/{id}` | ✅ | ✅ |
| **Create Product** | `POST /api/products` | ✅ | ❌ |
| **Update Product** | `PUT /api/products/{id}` | ✅ | ❌ |
| **Delete Product** | `DELETE /api/products/{id}` | ✅ | ❌ |
| **Stock IN (Add inventory)** | `POST /api/products/{id}/stock/in` | ✅ | ✅ |
| **Stock OUT (Reduce inventory)** | `POST /api/products/{id}/stock/out` | ✅ | ✅ |
| **Low-Stock Detection** | `GET /api/products/low-stock` | ✅ | ✅ |
| **View Categories** | `GET /api/categories` | ✅ | ✅ |
| **Manage Categories** | `POST/PUT/DELETE /api/categories/**`| ✅ | ❌ |
| **View System Users** | `GET /api/users` | ✅ | ❌ |

---

## 🗄️ Database Schema

### Users (`users`)
- `id` (BIGSERIAL PRIMARY KEY)
- `name` (VARCHAR NOT NULL)
- `email` (VARCHAR UNIQUE NOT NULL)
- `password` (VARCHAR BCrypt hashed)
- `role` (VARCHAR: `ADMIN` or `STAFF`)
- `created_at` (TIMESTAMP)

### Categories (`categories`)
- `id` (BIGSERIAL PRIMARY KEY)
- `name` (VARCHAR UNIQUE NOT NULL)
- `description` (TEXT)

### Products (`products`)
- `id` (BIGSERIAL PRIMARY KEY)
- `name` (VARCHAR NOT NULL)
- `description` (TEXT)
- `sku` (VARCHAR UNIQUE NOT NULL)
- `price` (NUMERIC(12,2) NOT NULL)
- `quantity` (INTEGER NOT NULL DEFAULT 0)
- `minimum_stock` (INTEGER NOT NULL DEFAULT 10)
- `category_id` (BIGINT REFERENCES categories(id))
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)

---

## 🚀 Pre-seeded Accounts & Test Data

On the first application run, the system automatically initializes:

- **Admin Account**:
  - **Email**: `admin@inventory.com`
  - **Password**: `Admin@123`
  - **Role**: `ADMIN`
- **Staff Account**:
  - **Email**: `staff@inventory.com`
  - **Password**: `Staff@123`
  - **Role**: `STAFF`
- **4 Categories**: Electronics, Office Supplies, Furniture, Networking
- **8 Products**: Includes items above threshold as well as low-stock items for immediate testing.

---

## 🏃 Running the Application

### 1. Configure PostgreSQL
Ensure PostgreSQL is running on port 5432 and create the database:
```sql
CREATE DATABASE inventory_db;
```
Configure your credentials in `src/main/resources/application.properties` or set environment variables:
```bash
set DB_USERNAME=postgres
set DB_PASSWORD=your_password
```

### 2. Build and Run with Maven
```bash
# Run unit tests
.\mvnw.cmd test

# Run application locally
.\mvnw.cmd spring-boot:run
```

### 3. Swagger UI & Interactive Documentation
Once started, explore and test all endpoints interactively:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
