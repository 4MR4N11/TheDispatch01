# Spring Boot Essentials: Auto-Configuration and Properties

> **Foundation**: Understand how Spring Boot automatically configures your application based on properties and dependencies.

---

## Table of Contents
1. [Spring Boot vs Spring Framework](#1-spring-boot-vs-spring-framework)
2. [Auto-Configuration Magic](#2-auto-configuration-magic)
3. [Understanding application.properties](#3-understanding-applicationproperties)
4. [Environment Variables and Defaults](#4-environment-variables-and-defaults)
5. [Database Configuration](#5-database-configuration)
6. [Security Configuration Properties](#6-security-configuration-properties)
7. [Logging Configuration](#7-logging-configuration)
8. [File Upload Configuration](#8-file-upload-configuration)
9. [Profiles (Dev vs Production)](#9-profiles-dev-vs-production)
10. [How Auto-Configuration Works](#10-how-auto-configuration-works)

---

## 1. Spring Boot vs Spring Framework

### 1.1 Plain Spring Framework (Without Spring Boot)

Before Spring Boot, you had to configure EVERYTHING manually:

```xml
<!-- datasource-config.xml -->
<bean id="dataSource" class="org.postgresql.ds.PGSimpleDataSource">
    <property name="serverName" value="localhost"/>
    <property name="databaseName" value="blog"/>
    <property name="user" value="blog"/>
    <property name="password" value="blog"/>
</bean>

<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="dataSource" ref="dataSource"/>
    <property name="packagesToScan" value="_blog.blog.entity"/>
    <!-- ... 20 more configuration lines -->
</bean>

<bean id="transactionManager" class="org.springframework.orm.jpa.JpaTransactionManager">
    <property name="entityManagerFactory" ref="entityManagerFactory"/>
</bean>

<!-- ... 100+ lines of XML configuration -->
```

**Problems:**
- Hundreds of lines of XML
- Easy to misconfigure
- Every project needs same boilerplate
- Hard to maintain

### 1.2 Spring Boot (Your Code)

**`pom.xml:33-36`** (Dependencies)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**`application.properties:1-4`** (Configuration)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/blog
spring.datasource.username=blog
spring.datasource.password=blog
spring.datasource.driver-class-name=org.postgresql.Driver
```

**That's it!** Spring Boot auto-configures:
- DataSource
- EntityManager
- TransactionManager
- Hibernate
- Connection pooling

**Spring Boot = Spring Framework + Auto-Configuration + Embedded Server + Opinionated Defaults**

---

## 2. Auto-Configuration Magic

### 2.1 How Spring Boot Knows What to Configure

**Your pom.xml dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

**Spring Boot thinks:**
```
1. "I see spring-boot-starter-web"
   → Auto-configure Tomcat web server
   → Auto-configure Spring MVC (REST controllers)
   → Auto-configure JSON serialization (Jackson)

2. "I see spring-boot-starter-data-jpa"
   → Auto-configure EntityManager
   → Auto-configure Hibernate
   → Auto-configure Transaction management
   → Auto-configure Spring Data JPA repositories

3. "I see postgresql dependency"
   → Auto-configure PostgreSQL DataSource
   → Use PostgreSQL dialect for Hibernate

4. "I see spring-boot-starter-security"
   → Auto-configure Spring Security
   → Enable authentication/authorization
   → Create SecurityFilterChain
```

### 2.2 What Gets Auto-Configured?

**When your application starts:**

```
SpringApplication.run(BlogApplication.class, args);
     ↓
┌────────────────────────────────────────────────────────┐
│         Spring Boot Auto-Configuration                │
├────────────────────────────────────────────────────────┤
│  DataSourceAutoConfiguration                          │
│    ✓ Creates DataSource bean (HikariCP pool)          │
│    ✓ Connects to PostgreSQL                           │
│                                                        │
│  HibernateJpaAutoConfiguration                        │
│    ✓ Creates EntityManagerFactory                     │
│    ✓ Configures Hibernate                             │
│    ✓ Scans @Entity classes                            │
│                                                        │
│  JpaRepositoriesAutoConfiguration                     │
│    ✓ Enables Spring Data JPA                          │
│    ✓ Implements repository interfaces                 │
│                                                        │
│  SecurityAutoConfiguration                            │
│    ✓ Enables Spring Security                          │
│    ✓ Creates default SecurityFilterChain              │
│                                                        │
│  WebMvcAutoConfiguration                              │
│    ✓ Starts embedded Tomcat (port 8080)               │
│    ✓ Configures @RestController endpoints             │
│    ✓ Enables JSON serialization                       │
│                                                        │
│  ValidationAutoConfiguration                          │
│    ✓ Enables @Valid annotation                        │
│    ✓ Configures Hibernate Validator                   │
│                                                        │
│  ... (100+ auto-configuration classes)                │
└────────────────────────────────────────────────────────┘
```

---

## 3. Understanding application.properties

### 3.1 What is application.properties?

**Location**: `backend/src/main/resources/application.properties`

This file configures your Spring Boot application. Spring Boot reads it at startup and uses values to configure beans.

**Your application.properties (complete):**

```properties
# Database Configuration
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/blog}
spring.datasource.username=${DB_USERNAME:blog}
spring.datasource.password=${DB_PASSWORD:blog}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=${SHOW_SQL:false}
spring.jpa.properties.hibernate.format_sql=${FORMAT_SQL:false}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# JWT Security Configuration
security.jwt.secret-key=${JWT_SECRET_KEY}
security.jwt.expiration-time=${JWT_EXPIRATION:3600000}

# Cookie Security
app.security.cookie.secure=${COOKIE_SECURE:true}
app.security.cookie.same-site=${COOKIE_SAME_SITE:Strict}

# Logging Configuration
logging.level.org.springframework.web=${LOG_LEVEL_WEB:WARN}
logging.level.org.springframework.security=${LOG_LEVEL_SECURITY:WARN}
logging.level._blog.blog=${LOG_LEVEL_APP:INFO}

# Server Configuration
server.tomcat.max-http-post-size=10MB
server.tomcat.max-swallow-size=10MB

# Actuator (Health Checks)
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always

# File Upload
upload.path=uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### 3.2 Property Naming Convention

Spring Boot uses hierarchical property names:

```properties
spring.datasource.url=...
  │      │         │
  │      │         └─ Property: url
  │      └─ Component: datasource
  └─ Namespace: spring
```

**Common Namespaces:**
- `spring.*` - Spring Framework configuration
- `server.*` - Web server (Tomcat) configuration
- `logging.*` - Logging configuration
- `management.*` - Actuator endpoints
- Custom: `app.*`, `security.*` - Your application-specific properties

---

## 4. Environment Variables and Defaults

### 4.1 The `${VAR:default}` Syntax

**Your application.properties:1**
```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/blog}
```

**How it works:**
```
${DB_URL:jdbc:postgresql://localhost:5432/blog}
  │       │
  │       └─ Default value (used if DB_URL not set)
  └─ Environment variable name
```

**Lookup Order:**
1. Environment variable: `DB_URL`
2. System property: `-DDB_URL=...`
3. Default value: `jdbc:postgresql://localhost:5432/blog`

### 4.2 Environment Variables in Action

**Development (Local):**
```bash
# No environment variables set
# Uses defaults:
# - DB_URL → jdbc:postgresql://localhost:5432/blog
# - DB_USERNAME → blog
# - DB_PASSWORD → blog
# - SHOW_SQL → false

./mvnw spring-boot:run
```

**Production (Docker):**
```bash
# Environment variables set in docker-compose.yml
export DB_URL=jdbc:postgresql://postgres:5432/blog
export DB_USERNAME=prod_user
export DB_PASSWORD=super_secure_password
export JWT_SECRET_KEY=very_long_random_secret
export COOKIE_SECURE=true
export SHOW_SQL=false

java -jar blog.jar
```

**Your docker-compose.yml:**
```yaml
services:
  backend:
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/blog
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET_KEY: ${JWT_SECRET_KEY}
      COOKIE_SECURE: true
      LOG_LEVEL_WEB: WARN
```

### 4.3 Custom Properties - JWT Configuration

**Your application.properties:11-13**
```properties
security.jwt.secret-key=${JWT_SECRET_KEY}
security.jwt.expiration-time=${JWT_EXPIRATION:3600000}
```

**How to Use Custom Properties in Code:**

```java
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;
    //      ↑ Spring injects value from application.properties

    @Value("${security.jwt.expiration-time}")
    private Long expirationTime;
    //      ↑ Injected: 3600000 (1 hour in milliseconds)

    public String generateToken(String username) {
        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
            //                                                    ↑ Uses property
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            //        ↑ Uses secretKey property
            .compact();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

**Alternative - @ConfigurationProperties (Better for Multiple Properties):**

```java
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String secretKey;
    private Long expirationTime;

    // Getters and setters
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public Long getExpirationTime() { return expirationTime; }
    public void setExpirationTime(Long expirationTime) { this.expirationTime = expirationTime; }
}

@Service
public class JwtService {
    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(String username) {
        return Jwts.builder()
            .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationTime()))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }
}
```

---

## 5. Database Configuration

### 5.1 DataSource Configuration

**Your application.properties:1-4**
```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/blog}
spring.datasource.username=${DB_USERNAME:blog}
spring.datasource.password=${DB_PASSWORD:blog}
spring.datasource.driver-class-name=org.postgresql.Driver
```

**What Spring Boot Does:**

```java
// Spring Boot auto-configures this (you don't write this code):
@Bean
public DataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://localhost:5432/blog");
    config.setUsername("blog");
    config.setPassword("blog");
    config.setDriverClassName("org.postgresql.Driver");

    // Default connection pool settings
    config.setMaximumPoolSize(10);  // Max 10 connections
    config.setMinimumIdle(5);       // Min 5 idle connections
    config.setConnectionTimeout(30000);  // 30 seconds

    return new HikariDataSource(config);
}
```

**Connection Pooling Explained:**

```
Your Application
    ↓ Need database connection
┌─────────────────────────────────────┐
│   HikariCP Connection Pool          │
├─────────────────────────────────────┤
│  [Conn 1] ← In use (UserController) │
│  [Conn 2] ← In use (PostController) │
│  [Conn 3] ← Available                │
│  [Conn 4] ← Available                │
│  [Conn 5] ← Available                │
│  ... (up to 10 connections)          │
└─────────────────────────────────────┘
    ↓
PostgreSQL Database
```

**WHY Connection Pooling?**
- Creating new database connection is slow (100-200ms)
- Reusing connections is fast (~1ms)
- Pool maintains ready-to-use connections

### 5.2 JPA/Hibernate Configuration

**Your application.properties:6-9**
```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=${SHOW_SQL:false}
spring.jpa.properties.hibernate.format_sql=${FORMAT_SQL:false}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

**Property Breakdown:**

#### `spring.jpa.hibernate.ddl-auto=update`

Controls how Hibernate manages database schema:

```
Options:
- none        → Do nothing (manual schema management)
- validate    → Validate schema matches entities (production)
- update      → Update schema (add new columns/tables) ✓ YOUR SETTING
- create      → Drop all tables and recreate (testing)
- create-drop → Create on startup, drop on shutdown (testing)
```

**What `update` Does:**

```java
// You add new field to entity:
@Entity
public class User {
    private String firstName;
    private String lastName;
    private String bio;  // ← NEW FIELD
}

// Hibernate generates and runs:
ALTER TABLE users ADD COLUMN bio VARCHAR(255);
```

**Production Recommendation:**
```properties
# Production
spring.jpa.hibernate.ddl-auto=validate

# Use Flyway or Liquibase for schema migrations instead
```

#### `spring.jpa.show-sql=false`

Controls SQL logging:

```properties
# Development
spring.jpa.show-sql=true

# Output:
Hibernate: select u1_0.id,u1_0.email,u1_0.password from users u1_0 where u1_0.email=?

# Production
spring.jpa.show-sql=false  # ✓ YOUR SETTING (for security)
```

#### `spring.jpa.properties.hibernate.format_sql=false`

Pretty-prints SQL (for development):

```properties
# Unformatted (your setting):
Hibernate: select u1_0.id,u1_0.email from users u1_0 where u1_0.email=?

# Formatted (format_sql=true):
Hibernate:
    select
        u1_0.id,
        u1_0.email
    from
        users u1_0
    where
        u1_0.email=?
```

#### `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect`

Tells Hibernate which SQL dialect to use:

```java
// Hibernate generates different SQL for different databases:

// PostgreSQL:
SELECT * FROM users LIMIT 10 OFFSET 20;

// MySQL:
SELECT * FROM users LIMIT 20, 10;

// Oracle:
SELECT * FROM users WHERE ROWNUM <= 30 AND ROWNUM > 20;
```

---

## 6. Security Configuration Properties

### 6.1 JWT Configuration

**Your application.properties:11-13**
```properties
security.jwt.secret-key=${JWT_SECRET_KEY}
security.jwt.expiration-time=${JWT_EXPIRATION:3600000}
```

**JWT_SECRET_KEY Requirements:**
- Must be Base64-encoded
- At least 256 bits (32 bytes) for HS256 algorithm
- Keep secret (don't commit to Git)

**Generate Secret Key:**
```bash
# Generate random 256-bit key
openssl rand -base64 32
# Output: 7JnMTZ8F9xK2pL3qR4sT5uV6wX7yZ8aB9cD0eF1gH2i=

# Set environment variable
export JWT_SECRET_KEY=7JnMTZ8F9xK2pL3qR4sT5uV6wX7yZ8aB9cD0eF1gH2i=
```

**Expiration Time:**
```properties
security.jwt.expiration-time=3600000  # 1 hour in milliseconds

# Common values:
# 15 minutes = 900000
# 1 hour     = 3600000 (your default)
# 1 day      = 86400000
# 7 days     = 604800000
```

### 6.2 Cookie Security Settings

**Your application.properties:17-18**
```properties
app.security.cookie.secure=${COOKIE_SECURE:true}
app.security.cookie.same-site=${COOKIE_SAME_SITE:Strict}
```

**What These Do:**

```java
// Your code might use these:
@Service
public class CookieService {
    @Value("${app.security.cookie.secure}")
    private boolean cookieSecure;  // true in production

    @Value("${app.security.cookie.same-site}")
    private String sameSite;  // "Strict"

    public ResponseCookie createJwtCookie(String token) {
        return ResponseCookie.from("jwt", token)
            .httpOnly(true)        // Cannot be accessed by JavaScript
            .secure(cookieSecure)  // Only sent over HTTPS
            .sameSite(sameSite)    // CSRF protection
            .path("/")
            .maxAge(3600)          // 1 hour
            .build();
    }
}
```

**Cookie Settings Explained:**

```properties
# secure=true
# Cookie only sent over HTTPS (not HTTP)
# Prevents man-in-the-middle attacks

# same-site=Strict
# Cookie not sent on cross-site requests
# Prevents CSRF attacks

Options:
- Strict → Never send cookie on cross-site requests
- Lax    → Send cookie on top-level navigation (GET)
- None   → Always send cookie (requires secure=true)
```

**Development vs Production:**

```properties
# Development (.env.local)
COOKIE_SECURE=false  # Allow HTTP
COOKIE_SAME_SITE=Lax

# Production (.env.production)
COOKIE_SECURE=true   # HTTPS only ✓ YOUR DEFAULT
COOKIE_SAME_SITE=Strict  ✓ YOUR DEFAULT
```

---

## 7. Logging Configuration

### 7.1 Log Levels

**Your application.properties:21-23**
```properties
logging.level.org.springframework.web=${LOG_LEVEL_WEB:WARN}
logging.level.org.springframework.security=${LOG_LEVEL_SECURITY:WARN}
logging.level._blog.blog=${LOG_LEVEL_APP:INFO}
```

**Log Levels (from most to least verbose):**
```
TRACE → DEBUG → INFO → WARN → ERROR → OFF
```

**What Gets Logged:**

```properties
# logging.level.org.springframework.web=WARN

# WARN and above (WARN, ERROR):
WARN : HTTP request failed: /api/users/999 (404 Not Found)
ERROR: Servlet.service() threw exception: NullPointerException

# Suppressed (DEBUG, INFO):
# INFO : Started UserController in 0.5 seconds
# DEBUG: Mapped URL path [/api/users] onto method [getUsers]
```

**Package-Specific Logging:**

```properties
# Your application
logging.level._blog.blog=INFO
# Logs: INFO, WARN, ERROR from your code

# Spring Framework web requests
logging.level.org.springframework.web=WARN
# Only logs: WARN, ERROR from Spring MVC

# Spring Security
logging.level.org.springframework.security=WARN
# Only logs: WARN, ERROR from security filters
```

### 7.2 Development vs Production Logging

**Development:**
```properties
# See all SQL queries
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Verbose logging
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.security=DEBUG
logging.level._blog.blog=DEBUG
```

**Production (Your Defaults):**
```properties
# Hide SQL (security - don't expose queries)
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# Minimal logging (performance + security)
logging.level.org.springframework.web=WARN
logging.level.org.springframework.security=WARN
logging.level._blog.blog=INFO
```

**WHY Minimal Logging in Production?**
- **Security**: Don't log sensitive data (passwords, tokens, queries)
- **Performance**: Writing logs is slow
- **Disk space**: Verbose logs grow quickly
- **Information disclosure**: Error details can help attackers

---

## 8. File Upload Configuration

### 8.1 File Size Limits

**Your application.properties:26-27, 34-36**
```properties
# Server-level limits (Tomcat)
server.tomcat.max-http-post-size=10MB
server.tomcat.max-swallow-size=10MB

# Spring multipart limits
upload.path=uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

**Why Two Sets of Limits?**

```
HTTP Request Flow:

Client uploads file
    ↓
Tomcat (server.tomcat.max-http-post-size) ✓ 10MB limit
    ↓
Spring MVC (spring.servlet.multipart.max-file-size) ✓ 10MB limit
    ↓
Your controller
```

**What Happens When Exceeded:**

```java
@PostMapping("/upload")
public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
    // If file > 10MB:
    // Exception: MaxUploadSizeExceededException
}
```

**Handle Size Limit Exception:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("error", "File size exceeds 10MB limit"));
    }
}
```

### 8.2 Custom Upload Path

**Your application.properties:34**
```properties
upload.path=uploads
```

**Using in Code:**

```java
@Service
public class FileUploadService {

    @Value("${upload.path}")
    private String uploadPath;  // "uploads"

    public String saveFile(MultipartFile file) throws IOException {
        // Create directory if not exists
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // Generate unique filename
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);

        // Save file
        file.transferTo(filePath.toFile());

        return filename;
    }
}
```

---

## 9. Profiles (Dev vs Production)

### 9.1 What are Profiles?

**Profiles** let you have different configurations for different environments.

**Create Profile-Specific Properties:**

```
resources/
  ├─ application.properties          (default, all environments)
  ├─ application-dev.properties      (development only)
  ├─ application-prod.properties     (production only)
  └─ application-test.properties     (testing only)
```

### 9.2 Example Profile Configuration

**application.properties** (default):
```properties
# Common for all environments
spring.application.name=The Dispatch Blog
server.port=8080
```

**application-dev.properties**:
```properties
# Development
spring.datasource.url=jdbc:postgresql://localhost:5432/blog
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level._blog.blog=DEBUG
app.security.cookie.secure=false
```

**application-prod.properties**:
```properties
# Production
spring.datasource.url=${DB_URL}
spring.jpa.show-sql=false
logging.level._blog.blog=WARN
app.security.cookie.secure=true
```

### 9.3 Activating Profiles

**Method 1: application.properties**
```properties
spring.profiles.active=dev
```

**Method 2: Environment Variable**
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar blog.jar
```

**Method 3: Command Line**
```bash
java -jar blog.jar --spring.profiles.active=prod
```

**Method 4: IDE (IntelliJ)**
```
Run > Edit Configurations > VM Options:
-Dspring.profiles.active=dev
```

### 9.4 Profile-Specific Beans

**Create beans only for specific profiles:**

```java
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("dev")
    public PasswordEncoder devPasswordEncoder() {
        // Weak password encoder for development (faster)
        return new PasswordEncoder() {
            public String encode(CharSequence rawPassword) {
                return "dev_" + rawPassword;  // No real encryption
            }
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals("dev_" + rawPassword);
            }
        };
    }

    @Bean
    @Profile("prod")
    public PasswordEncoder prodPasswordEncoder() {
        // Strong encryption for production
        return new BCryptPasswordEncoder(12);  // Cost factor 12
    }
}
```

**Usage:**
```bash
# Development - uses devPasswordEncoder
export SPRING_PROFILES_ACTIVE=dev

# Production - uses prodPasswordEncoder
export SPRING_PROFILES_ACTIVE=prod
```

---

## 10. How Auto-Configuration Works

### 10.1 The @Conditional Mechanism

Spring Boot uses `@Conditional` annotations to decide what to auto-configure.

**Example - DataSourceAutoConfiguration:**

```java
@Configuration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
//                    ↑ Only if these classes are on classpath
@EnableConfigurationProperties(DataSourceProperties.class)
//                              ↑ Bind application.properties to this class
public class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    //  ↑ Only create if user hasn't defined their own DataSource
    public DataSource dataSource(DataSourceProperties properties) {
        return DataSourceBuilder.create()
            .url(properties.getUrl())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .build();
    }
}
```

**Conditions:**
- `@ConditionalOnClass` - If class exists on classpath
- `@ConditionalOnMissingBean` - If bean doesn't already exist
- `@ConditionalOnProperty` - If property is set
- `@ConditionalOnWebApplication` - If this is a web app

### 10.2 Your Application's Auto-Configuration

**When you start your application:**

```java
SpringApplication.run(BlogApplication.class, args);
```

**Spring Boot checks:**

```
1. Check classpath for spring-boot-starter-web
   ✓ Found → TomcatEmbeddedServletContainerFactory
   ✓ Create Tomcat server on port 8080

2. Check classpath for spring-boot-starter-data-jpa
   ✓ Found → HibernateJpaAutoConfiguration
   ✓ Check for DataSource bean
   ✗ Not found → Create DataSource from application.properties
   ✓ Create EntityManagerFactory
   ✓ Create JpaTransactionManager

3. Check classpath for postgresql driver
   ✓ Found → Use PostgreSQL dialect

4. Check classpath for spring-boot-starter-security
   ✓ Found → SecurityAutoConfiguration
   ✓ Create default SecurityFilterChain
   ✓ Enable authentication

5. Check for @RestController classes
   ✓ Found UserController, PostController
   ✓ Register endpoint mappings

6. Start application!
```

### 10.3 Viewing Auto-Configuration Report

**Enable debug logging:**
```properties
# application.properties
debug=true
```

**Run application:**
```bash
./mvnw spring-boot:run
```

**Output:**
```
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------

   DataSourceAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'DataSource' (OnClassCondition)

   HibernateJpaAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'EntityManager' (OnClassCondition)

   SecurityAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'SecurityFilterChain' (OnClassCondition)

Negative matches:
-----------------

   MongoAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'MongoClient' (OnClassCondition)

   RedisAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'RedisClient' (OnClassCondition)
```

### 10.4 Overriding Auto-Configuration

**Spring Boot's auto-configuration can be overridden:**

```java
@Configuration
public class CustomDataSourceConfig {

    @Bean
    public DataSource dataSource() {
        // Your custom DataSource configuration
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://custom-host:5432/blog");
        config.setMaximumPoolSize(20);  // Override default (10)
        config.setConnectionTimeout(5000);  // 5 seconds

        return new HikariDataSource(config);
    }
}
```

**Spring Boot thinks:**
```
1. Check for DataSource bean
   ✓ Found user-defined bean
   ✗ Skip DataSourceAutoConfiguration

2. Use user's custom DataSource
```

**This is the @ConditionalOnMissingBean pattern** - Spring Boot only auto-configures if you haven't already defined a bean.

---

## Key Takeaways

### What You Learned

1. **Spring Boot vs Spring Framework**
   - Spring Boot = Spring + Auto-Configuration + Defaults
   - Eliminates XML configuration
   - Convention over configuration

2. **Auto-Configuration**
   - Based on classpath dependencies
   - Configures beans automatically
   - Can be overridden by defining your own beans

3. **application.properties**
   - Centralizes configuration
   - Supports environment variables: `${VAR:default}`
   - Hierarchical properties: `spring.datasource.url`

4. **Property Categories**
   - Database: `spring.datasource.*`
   - JPA: `spring.jpa.*`
   - Server: `server.*`
   - Logging: `logging.level.*`
   - Custom: `app.*`, `security.*`

5. **Profiles**
   - Different configurations for dev/prod
   - application-{profile}.properties
   - Activate with `spring.profiles.active`

6. **Security Best Practices**
   - Use environment variables for secrets
   - Different settings for dev/prod
   - Minimal logging in production

### Configuration Checklist

**Development:**
```properties
✓ spring.jpa.show-sql=true (see queries)
✓ logging.level.*=DEBUG (verbose logging)
✓ app.security.cookie.secure=false (allow HTTP)
✓ spring.jpa.hibernate.ddl-auto=update (auto schema updates)
```

**Production:**
```properties
✓ spring.jpa.show-sql=false (hide queries)
✓ logging.level.*=WARN (minimal logging)
✓ app.security.cookie.secure=true (HTTPS only)
✓ spring.jpa.hibernate.ddl-auto=validate (manual migrations)
✓ Use environment variables for secrets
```

---

## What's Next?

You now understand how Spring Boot auto-configures your application. Next:

**→ [04-JPA-HIBERNATE-BASICS.md](./04-JPA-HIBERNATE-BASICS.md)** - How your Java objects become database tables

**Key Questions for Next Section:**
- What is JPA and how is it different from Hibernate?
- What is the EntityManager and when is it used?
- How do `@Entity` classes map to database tables?
- What is a persistence context?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials

**Next**: JPA & Hibernate Basics 🎯
