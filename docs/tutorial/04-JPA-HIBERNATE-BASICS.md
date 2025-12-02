# JPA & Hibernate Basics: From Java Objects to Database Tables

> **Core Concept**: Understand how your Java classes become database tables and how Hibernate manages the translation between objects and SQL.

---

## Table of Contents
1. [JPA vs Hibernate - What's the Difference?](#1-jpa-vs-hibernate---whats-the-difference)
2. [Your First Entity - User](#2-your-first-entity---user)
3. [Entity Annotations Explained](#3-entity-annotations-explained)
4. [Primary Keys and ID Generation](#4-primary-keys-and-id-generation)
5. [Column Mapping](#5-column-mapping)
6. [Timestamps - Creation and Update](#6-timestamps---creation-and-update)
7. [Enumerations in Entities](#7-enumerations-in-entities)
8. [The EntityManager - Hibernate's Core](#8-the-entitymanager---hibernates-core)
9. [Persistence Context - First Level Cache](#9-persistence-context---first-level-cache)
10. [Entity Lifecycle](#10-entity-lifecycle)
11. [How Hibernate Generates SQL](#11-how-hibernate-generates-sql)

---

## 1. JPA vs Hibernate - What's the Difference?

### 1.1 The Relationship

```
┌─────────────────────────────────────┐
│         JPA (Specification)         │   ← Interface/API
│  "The contract - what methods exist"│
├─────────────────────────────────────┤
│      Hibernate (Implementation)     │   ← Implementation
│   "The actual code that does work"  │
└─────────────────────────────────────┘
```

**Analogy:**
- **JPA** = JDBC (specification)
- **Hibernate** = PostgreSQL Driver (implementation)

### 1.2 What is JPA?

**JPA** (Jakarta Persistence API, formerly Java Persistence API) is a **specification** that defines:
- Annotations: `@Entity`, `@Id`, `@Column`, etc.
- EntityManager API for persistence operations
- JPQL (Java Persistence Query Language)
- How entities map to tables

**JPA is just interfaces and annotations - no actual implementation.**

### 1.3 What is Hibernate?

**Hibernate** is an **implementation** of JPA (plus extra features):
- Implements all JPA annotations
- Provides EntityManager implementation
- Generates SQL queries
- Manages database connections
- Caching (first-level, second-level)
- Extra annotations: `@CreationTimestamp`, `@UpdateTimestamp`, `@Formula`

**Your pom.xml:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <!--
        This includes:
        - JPA API (jakarta.persistence.*)
        - Hibernate (org.hibernate.*)
        - Spring Data JPA
    -->
</dependency>
```

### 1.4 Why Care About the Distinction?

**You write code using JPA annotations:**
```java
import jakarta.persistence.*;  // JPA API

@Entity  // JPA annotation
public class User {
    @Id  // JPA annotation
    private Long id;
}
```

**But Hibernate does the work:**
```
Your Code (JPA) → Hibernate → SQL → Database
```

**Benefits:**
- Can switch from Hibernate to EclipseLink (another JPA implementation) with minimal code changes
- Standard API (JPA) documented by Jakarta EE
- Hibernate-specific features available when needed

---

## 2. Your First Entity - User

### 2.1 Complete User Entity

**`User.java:36-127`**
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 30)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 30)
    private String lastName;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false)
    private String password;

    @Email
    @Column(nullable = false, unique = true)
    private String email;

    private String avatar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean banned = false;

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private Date createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Date updatedAt;

    // Relationships (covered in next document)
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Post> Posts;

    @ManyToMany(mappedBy = "likedBy")
    private Set<Post> likedPosts = new HashSet<>();

    // Getters, setters, etc.
}
```

### 2.2 Generated Database Table

**Hibernate generates this SQL (PostgreSQL):**
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(30) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    username VARCHAR(30) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    avatar VARCHAR(255),
    role VARCHAR(255) NOT NULL,
    banned BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username),
    CONSTRAINT uk_email UNIQUE (email)
);
```

**Mapping:**
```
Java Field          →    Database Column
────────────────────────────────────────────
Long id             →    id BIGSERIAL
String firstName    →    first_name VARCHAR(30)
String lastName     →    last_name VARCHAR(30)
String username     →    username VARCHAR(30) UNIQUE
String password     →    password VARCHAR(255)
String email        →    email VARCHAR(255) UNIQUE
String avatar       →    avatar VARCHAR(255)
Role role           →    role VARCHAR(255)
boolean banned      →    banned BOOLEAN
Date createdAt      →    created_at TIMESTAMP
Date updatedAt      →    updated_at TIMESTAMP
```

---

## 3. Entity Annotations Explained

### 3.1 @Entity

**`User.java:36`**
```java
@Entity
public class User {
```

**What it does:**
- Marks this class as a JPA entity
- Tells Hibernate: "This class should be mapped to a database table"
- Hibernate scans for `@Entity` classes at startup

**Without @Entity:**
```java
// Just a regular Java class - Hibernate ignores it
public class User {
    private Long id;
    private String username;
}
```

**Requirements for @Entity classes:**
- Must have a no-argument constructor (can be private)
- Cannot be final
- Cannot have final fields (for lazy loading)
- Must have `@Id` annotation on at least one field

### 3.2 @Table

**`User.java:37`**
```java
@Entity
@Table(name = "users")
public class User {
```

**What it does:**
- Specifies the database table name
- Without `@Table`, Hibernate uses class name as table name

**Examples:**
```java
@Entity
@Table(name = "users")  // Table: users
public class User { }

@Entity  // Table: User (class name)
public class User { }

@Entity
@Table(name = "app_users", schema = "public")  // public.app_users
public class User { }
```

**Additional @Table attributes:**
```java
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"username"}),
        @UniqueConstraint(columnNames = {"email"})
    },
    indexes = {
        @Index(name = "idx_email", columnList = "email")
    }
)
```

### 3.3 Lombok Annotations

**`User.java:38-42`**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
```

**What Lombok generates at compile time:**

```java
// @Getter and @Setter generate:
public String getUsername() { return username; }
public void setUsername(String username) { this.username = username; }
// ... for all fields

// @NoArgsConstructor generates:
public User() { }

// @AllArgsConstructor generates:
public User(Long id, String firstName, String lastName, ...) {
    this.id = id;
    this.firstName = firstName;
    // ... all fields
}

// @Builder generates:
User user = User.builder()
    .username("john")
    .email("john@example.com")
    .build();
```

**Note**: Lombok is NOT part of JPA/Hibernate - it's a Java library that reduces boilerplate.

---

## 4. Primary Keys and ID Generation

### 4.1 @Id Annotation

**`User.java:44-46`**
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

**@Id** marks the primary key field.

**Requirements:**
- Every `@Entity` must have exactly one `@Id`
- Can be any type: Long, Integer, String, UUID, etc.
- Recommended: Long (supports large numbers, nullable for new entities)

### 4.2 @GeneratedValue Strategies

**IDENTITY Strategy (Your Code):**
```java
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

**How it works:**
```sql
-- PostgreSQL generates ID using SERIAL:
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY  -- Auto-incrementing
);

-- When you insert:
INSERT INTO users (username, email, ...) VALUES ('john', 'john@example.com', ...);
-- Database assigns: id = 1

INSERT INTO users (username, email, ...) VALUES ('jane', 'jane@example.com', ...);
-- Database assigns: id = 2
```

**In Java:**
```java
User user = new User();
user.setUsername("john");
System.out.println(user.getId());  // null (not yet saved)

userRepository.save(user);
System.out.println(user.getId());  // 1 (assigned by database)
```

**Other Strategies:**

#### SEQUENCE (PostgreSQL recommended for performance)
```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
@SequenceGenerator(name = "user_seq", sequenceName = "user_id_seq", allocationSize = 50)
private Long id;
```

**Generated SQL:**
```sql
CREATE SEQUENCE user_id_seq START WITH 1 INCREMENT BY 50;

-- Hibernate fetches 50 IDs at once:
SELECT nextval('user_id_seq');  -- Returns 1
-- Now Hibernate can assign IDs 1-50 without hitting database

INSERT INTO users (id, username, ...) VALUES (1, 'user1', ...);
INSERT INTO users (id, username, ...) VALUES (2, 'user2', ...);
-- ... up to 50 inserts before fetching next batch
```

**Benefits:**
- Batch inserts (better performance)
- Hibernate knows ID before INSERT (useful for associations)

#### AUTO (Default)
```java
@Id
@GeneratedValue(strategy = GenerationType.AUTO)
private Long id;
```
Hibernate chooses strategy based on database (SEQUENCE for PostgreSQL).

#### TABLE (Portable but slow)
```java
@Id
@GeneratedValue(strategy = GenerationType.TABLE)
private Long id;
```
Uses a separate table to track IDs (works on all databases, but slow).

### 4.3 UUID as Primary Key

**Alternative to Long:**
```java
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
```

**Generated:**
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);
```

**Benefits:**
- Globally unique (no database coordination needed)
- Can be generated in application code
- Good for distributed systems

**Drawbacks:**
- Larger storage (16 bytes vs 8 bytes for Long)
- Slower indexing (random vs sequential)
- Harder to debug (long random strings)

---

## 5. Column Mapping

### 5.1 @Column Annotation

**`User.java:48-49`**
```java
@Column(name = "first_name", nullable = false, length = 30)
private String firstName;
```

**@Column attributes:**

```java
@Column(
    name = "first_name",    // Database column name (default: firstName)
    nullable = false,       // NOT NULL constraint
    unique = true,          // UNIQUE constraint
    length = 30,            // VARCHAR(30) for String
    precision = 10,         // For BigDecimal (total digits)
    scale = 2,              // For BigDecimal (decimal places)
    columnDefinition = "TEXT",  // Custom SQL type
    insertable = true,      // Include in INSERT (default: true)
    updatable = true        // Include in UPDATE (default: true)
)
private String firstName;
```

### 5.2 Column Name Mapping

**Java (camelCase) → Database (snake_case):**

```java
@Column(name = "first_name")
private String firstName;
//             ↑             ↑
//       Java field    Database column
```

**Why explicit mapping?**
- Java convention: `camelCase` (firstName, lastName)
- Database convention: `snake_case` (first_name, last_name)
- Without `@Column(name = ...)`, Hibernate uses field name directly

**Example without explicit name:**
```java
private String firstName;
// Database column: firstName (camelCase - not conventional)
```

### 5.3 Data Type Mapping

**Java Type → PostgreSQL Type:**

| Java Type | PostgreSQL Type | Example |
|-----------|----------------|---------|
| `Long id` | `BIGINT` | `id BIGINT` |
| `Integer count` | `INTEGER` | `count INTEGER` |
| `String name` | `VARCHAR(255)` | `name VARCHAR(255)` |
| `@Column(length=30) String name` | `VARCHAR(30)` | `name VARCHAR(30)` |
| `@Column(columnDefinition="TEXT") String content` | `TEXT` | `content TEXT` |
| `boolean active` | `BOOLEAN` | `active BOOLEAN` |
| `Date createdAt` | `TIMESTAMP` | `created_at TIMESTAMP` |
| `LocalDateTime updatedAt` | `TIMESTAMP` | `updated_at TIMESTAMP` |
| `BigDecimal price` | `NUMERIC` | `price NUMERIC(10,2)` |

### 5.4 nullable and unique Constraints

**`User.java:54-56`**
```java
@Column(nullable = false, unique = true, length = 30)
private String username;
```

**Generated SQL:**
```sql
username VARCHAR(30) NOT NULL UNIQUE
```

**In Action:**
```java
// Try to save user without username:
User user = new User();
user.setEmail("john@example.com");
userRepository.save(user);
// Exception: ConstraintViolationException - username cannot be null

// Try to save duplicate username:
User user1 = new User();
user1.setUsername("john");
userRepository.save(user1);  // OK

User user2 = new User();
user2.setUsername("john");
userRepository.save(user2);  // Exception: unique constraint violated
```

### 5.5 Column Without @Column Annotation

**`User.java:65`**
```java
private String avatar;
```

**No @Column annotation? Hibernate uses defaults:**
- Column name: `avatar` (field name)
- Type: `VARCHAR(255)` (default String length)
- Nullable: `true` (no constraint)

**Generated SQL:**
```sql
avatar VARCHAR(255)
```

### 5.6 columnDefinition for Custom SQL Types

**`Post.java:48-49`**
```java
@Column(nullable = false, columnDefinition = "TEXT")
private String content;
```

**Generated SQL:**
```sql
content TEXT NOT NULL
```

**Why TEXT instead of VARCHAR?**
- `VARCHAR(n)`: Limited size (max 65,535 characters in PostgreSQL)
- `TEXT`: Unlimited size (perfect for blog post content)

**Other columnDefinition examples:**
```java
@Column(columnDefinition = "TEXT")
private String content;  // TEXT

@Column(columnDefinition = "JSON")
private String metadata;  // JSON column (PostgreSQL)

@Column(columnDefinition = "DECIMAL(10,2)")
private BigDecimal price;  // DECIMAL(10,2)
```

---

## 6. Timestamps - Creation and Update

### 6.1 @CreationTimestamp

**`User.java:90-92`**
```java
@CreationTimestamp
@Column(updatable = false, name = "created_at")
private Date createdAt;
```

**What it does:**
- Automatically sets timestamp when entity is **first saved**
- Never updates after creation (`updatable = false`)
- Hibernate feature (not JPA standard)

**In Action:**
```java
User user = new User();
user.setUsername("john");
System.out.println(user.getCreatedAt());  // null

userRepository.save(user);
System.out.println(user.getCreatedAt());  // 2025-11-04 10:30:00 (set automatically)

// Later update:
user.setEmail("newemail@example.com");
userRepository.save(user);
System.out.println(user.getCreatedAt());  // 2025-11-04 10:30:00 (UNCHANGED)
```

### 6.2 @UpdateTimestamp

**`User.java:94-96`**
```java
@UpdateTimestamp
@Column(name = "updated_at")
private Date updatedAt;
```

**What it does:**
- Automatically sets timestamp when entity is **created**
- Updates timestamp every time entity is **modified**
- Hibernate feature

**In Action:**
```java
User user = new User();
user.setUsername("john");
userRepository.save(user);
System.out.println(user.getUpdatedAt());  // 2025-11-04 10:30:00 (set on create)

// Wait 1 hour, then update:
user.setEmail("newemail@example.com");
userRepository.save(user);
System.out.println(user.getUpdatedAt());  // 2025-11-04 11:30:00 (UPDATED)
```

### 6.3 Alternative - JPA @PrePersist and @PreUpdate

**Standard JPA approach (without Hibernate annotations):**
```java
@Entity
public class User {
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**@PrePersist**: Called before first save
**@PreUpdate**: Called before every update

---

## 7. Enumerations in Entities

### 7.1 @Enumerated

**`User.java:67-70`**
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private Role role = Role.USER;
```

**Role enum:**
```java
public enum Role {
    USER,
    ADMIN,
    MODERATOR
}
```

### 7.2 EnumType.STRING vs EnumType.ORDINAL

**EnumType.STRING (Your Code - Recommended):**
```java
@Enumerated(EnumType.STRING)
private Role role;
```

**Database stores string value:**
```sql
role VARCHAR(255) NOT NULL

-- Database values:
'USER'
'ADMIN'
'MODERATOR'
```

**Benefits:**
- Human-readable in database
- Safe if enum order changes
- Easy to query: `SELECT * FROM users WHERE role = 'ADMIN'`

**EnumType.ORDINAL (Avoid):**
```java
@Enumerated(EnumType.ORDINAL)
private Role role;
```

**Database stores integer (0, 1, 2, ...):**
```sql
role INTEGER NOT NULL

-- Database values:
0  (USER)
1  (ADMIN)
2  (MODERATOR)
```

**Problem - Enum Order Change:**
```java
// Original enum:
public enum Role {
    USER,      // 0
    ADMIN,     // 1
    MODERATOR  // 2
}

// Later, you add new role at the beginning:
public enum Role {
    GUEST,     // 0 ← NEW
    USER,      // 1 (was 0!)
    ADMIN,     // 2 (was 1!)
    MODERATOR  // 3 (was 2!)
}

// Now database data is corrupt:
// - Users with role=0 were USER, now read as GUEST
// - Admins with role=1 now read as USER
```

**ALWAYS use EnumType.STRING for safety.**

### 7.3 Default Values

**`User.java:69-70`**
```java
@Builder.Default
private Role role = Role.USER;
```

**Without @Builder.Default:**
```java
User user = User.builder()
    .username("john")
    .email("john@example.com")
    .build();
System.out.println(user.getRole());  // null (no default)
```

**With @Builder.Default:**
```java
User user = User.builder()
    .username("john")
    .email("john@example.com")
    .build();
System.out.println(user.getRole());  // USER (default applied)
```

**Alternative - Direct Field Initialization:**
```java
private Role role = Role.USER;  // Works for new User() constructor
```

---

## 8. The EntityManager - Hibernate's Core

### 8.1 What is EntityManager?

The **EntityManager** is the interface for all database operations in JPA:
- `persist()` - Save new entity
- `merge()` - Update existing entity
- `remove()` - Delete entity
- `find()` - Find by primary key
- `createQuery()` - Execute JPQL

**You rarely use EntityManager directly** - Spring Data JPA handles it for you.

### 8.2 Spring Data JPA Hides EntityManager

**Your code:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // No implementation!
}
```

**What Spring Data JPA generates:**
```java
public class UserRepositoryImpl implements UserRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            entityManager.persist(user);  // INSERT
            return user;
        } else {
            return entityManager.merge(user);  // UPDATE
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        User user = entityManager.find(User.class, id);
        return Optional.ofNullable(user);
    }

    @Override
    public void deleteById(Long id) {
        User user = entityManager.find(User.class, id);
        if (user != null) {
            entityManager.remove(user);
        }
    }

    // ... all other methods
}
```

### 8.3 Direct EntityManager Usage

**When you need it (advanced cases):**
```java
@Service
public class UserServiceImpl {
    @PersistenceContext
    private EntityManager entityManager;

    public void bulkUpdateUserStatus() {
        // Native SQL query
        int updated = entityManager.createNativeQuery(
            "UPDATE users SET banned = true WHERE last_login < :date"
        ).setParameter("date", oneYearAgo)
         .executeUpdate();
    }

    public void detachUser(User user) {
        // Remove entity from persistence context
        entityManager.detach(user);
    }

    public void clearCache() {
        // Clear first-level cache
        entityManager.clear();
    }
}
```

---

## 9. Persistence Context - First Level Cache

### 9.1 What is Persistence Context?

The **Persistence Context** is Hibernate's **first-level cache** - a map of entities currently managed by EntityManager.

```
┌─────────────────────────────────────┐
│      Persistence Context (Cache)    │
├─────────────────────────────────────┤
│  User#1 → [id=1, username="john"]   │
│  User#2 → [id=2, username="jane"]   │
│  Post#5 → [id=5, title="Hello"]     │
└─────────────────────────────────────┘
         ↓ (if not in cache)
    Database Query
```

### 9.2 How It Works

**Example:**
```java
@Service
public class UserService {
    @Autowired
    private EntityManager entityManager;

    @Transactional
    public void demonstratePersistenceContext() {
        // First access - loads from database
        User user1 = entityManager.find(User.class, 1L);
        System.out.println(user1.getUsername());
        // SQL: SELECT * FROM users WHERE id = 1

        // Second access - returns cached instance (no SQL!)
        User user2 = entityManager.find(User.class, 1L);
        System.out.println(user2.getUsername());
        // No SQL query! Returns same instance from cache

        // Proof they're the same object:
        System.out.println(user1 == user2);  // true (same reference)

        // Modify user1:
        user1.setEmail("newemail@example.com");

        // user2 sees the change (same object):
        System.out.println(user2.getEmail());  // newemail@example.com
    }
}
```

### 9.3 Persistence Context Lifecycle

**Scope: One transaction**

```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void transaction1() {
        User user = userRepository.findById(1L).get();
        // EntityManager creates Persistence Context
        // User is now "managed" (in cache)

        user.setEmail("new@example.com");
        // Change tracked by Persistence Context

    } // Transaction ends → Persistence Context closed → SQL UPDATE executed

    @Transactional
    public void transaction2() {
        // NEW Persistence Context (new transaction)
        User user = userRepository.findById(1L).get();
        // Must load from database again
    }
}
```

### 9.4 Benefits of Persistence Context

1. **Performance** - Avoids redundant queries
2. **Consistency** - Same entity always returns same instance
3. **Dirty Checking** - Automatically detects changes
4. **Write-Behind** - Batches SQL updates at transaction end

---

## 10. Entity Lifecycle

### 10.1 Entity States

An entity goes through four states:

```
┌─────────────┐
│  Transient  │  (new entity, not in database, not in cache)
└──────┬──────┘
       │ entityManager.persist(user)
       ↓
┌─────────────┐
│  Managed    │  (in database, in cache, changes tracked)
└──────┬──────┘
       │ transaction ends
       ↓
┌─────────────┐
│  Detached   │  (in database, not in cache, changes NOT tracked)
└──────┬──────┘
       │ entityManager.merge(user)
       ↓
┌─────────────┐
│  Managed    │  (back in cache)
└──────┬──────┘
       │ entityManager.remove(user)
       ↓
┌─────────────┐
│  Removed    │  (will be deleted from database)
└─────────────┘
```

### 10.2 Transient State

**Newly created object, not yet saved:**

```java
User user = new User();
user.setUsername("john");
user.setEmail("john@example.com");
// State: Transient
// - Not in database
// - Not in Persistence Context
// - Has no ID

System.out.println(user.getId());  // null
```

### 10.3 Managed State

**After saving:**

```java
userRepository.save(user);
// State: Managed
// - Saved to database (id = 1)
// - In Persistence Context
// - Changes tracked automatically

System.out.println(user.getId());  // 1

// Changes are automatically tracked:
user.setEmail("newemail@example.com");
// At transaction end, Hibernate runs:
// UPDATE users SET email = 'newemail@example.com' WHERE id = 1
// (without calling save() again!)
```

### 10.4 Detached State

**After transaction closes:**

```java
@Transactional
public User getUser(Long id) {
    User user = userRepository.findById(id).get();
    // State: Managed (inside transaction)
    return user;
} // Transaction ends → user becomes Detached

// Outside transaction:
User user = getUser(1L);
// State: Detached
// - In database
// - NOT in Persistence Context
// - Changes NOT tracked

user.setEmail("newemail@example.com");
// This change is NOT saved to database automatically!

// Must call save() to persist changes:
userRepository.save(user);  // Calls merge() internally
```

### 10.5 Removed State

**Marked for deletion:**

```java
@Transactional
public void deleteUser(Long id) {
    User user = userRepository.findById(id).get();
    // State: Managed

    userRepository.delete(user);
    // State: Removed (marked for deletion)
    // Still exists in database until transaction commits

    System.out.println(user.getId());  // 1 (still has ID)
    System.out.println(user.getUsername());  // "john" (object still accessible)

} // Transaction commits → DELETE FROM users WHERE id = 1
```

---

## 11. How Hibernate Generates SQL

### 11.1 Saving a New User

**Your code:**
```java
User user = new User();
user.setUsername("john");
user.setEmail("john@example.com");
user.setFirstName("John");
user.setLastName("Smith");
user.setPassword("hashed_password");
userRepository.save(user);
```

**Hibernate generates (PostgreSQL):**
```sql
INSERT INTO users (
    username,
    email,
    first_name,
    last_name,
    password,
    avatar,
    role,
    banned,
    created_at,
    updated_at
) VALUES (
    'john',
    'john@example.com',
    'John',
    'Smith',
    'hashed_password',
    NULL,
    'USER',
    false,
    '2025-11-04 10:30:00',
    '2025-11-04 10:30:00'
) RETURNING id;
-- RETURNING id fetches the generated ID
```

### 11.2 Updating a User

**Your code:**
```java
User user = userRepository.findById(1L).get();
user.setEmail("newemail@example.com");
user.setFirstName("Jonathan");
// No need to call save()! Changes tracked automatically
```

**Hibernate generates:**
```sql
SELECT
    u.id, u.username, u.email, u.first_name, u.last_name,
    u.password, u.avatar, u.role, u.banned,
    u.created_at, u.updated_at
FROM users u
WHERE u.id = 1;

-- At transaction end (dirty checking):
UPDATE users
SET
    email = 'newemail@example.com',
    first_name = 'Jonathan',
    updated_at = '2025-11-04 11:30:00'
WHERE id = 1;
```

**Note**: Hibernate only updates changed fields (dirty checking).

### 11.3 Finding by ID

**Your code:**
```java
User user = userRepository.findById(1L).orElseThrow();
```

**Hibernate generates:**
```sql
SELECT
    u.id,
    u.username,
    u.email,
    u.first_name,
    u.last_name,
    u.password,
    u.avatar,
    u.role,
    u.banned,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.id = 1;
```

### 11.4 Deleting a User

**Your code:**
```java
User user = userRepository.findById(1L).get();
userRepository.delete(user);
```

**Hibernate generates:**
```sql
-- First, fetch the entity (if not already in cache):
SELECT * FROM users WHERE id = 1;

-- Then delete:
DELETE FROM users WHERE id = 1;
```

**Delete by ID (more efficient):**
```java
userRepository.deleteById(1L);
```

**Hibernate generates:**
```sql
-- No SELECT needed:
DELETE FROM users WHERE id = 1;
```

### 11.5 Viewing Generated SQL

**Enable SQL logging:**
```properties
# application.properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

**Output:**
```
Hibernate:
    insert
    into
        users
        (banned, created_at, email, first_name, last_name, password, role, updated_at, username, avatar)
    values
        (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)

TRACE BasicBinder - binding parameter [1] as [BOOLEAN] - [false]
TRACE BasicBinder - binding parameter [2] as [TIMESTAMP] - [2025-11-04 10:30:00]
TRACE BasicBinder - binding parameter [3] as [VARCHAR] - [john@example.com]
...
```

---

## Key Takeaways

### What You Learned

1. **JPA vs Hibernate**
   - JPA = specification (interfaces, annotations)
   - Hibernate = implementation (actual code)
   - You code against JPA, Hibernate does the work

2. **Entity Mapping**
   - `@Entity` - marks class as entity
   - `@Table` - specifies table name
   - `@Id` - primary key
   - `@Column` - column configuration
   - `@GeneratedValue` - ID generation strategy

3. **Annotations**
   - `@CreationTimestamp` - auto-set on create
   - `@UpdateTimestamp` - auto-update on modify
   - `@Enumerated` - map enums (use STRING, not ORDINAL)
   - `@Column(nullable=false)` - NOT NULL constraint
   - `@Column(unique=true)` - UNIQUE constraint

4. **EntityManager**
   - Core interface for persistence operations
   - Spring Data JPA hides it behind repositories
   - Use directly for advanced operations

5. **Persistence Context**
   - First-level cache (per transaction)
   - Tracks entity changes (dirty checking)
   - Improves performance (avoids redundant queries)

6. **Entity Lifecycle**
   - Transient → Managed → Detached → Removed
   - Only Managed entities have changes tracked automatically

7. **SQL Generation**
   - Hibernate generates SQL from entity operations
   - Uses dialect for database-specific SQL
   - Dirty checking: only updates changed fields

---

## What's Next?

You now understand how single entities map to tables. Next:

**→ [05-JPA-RELATIONSHIPS.md](./05-JPA-RELATIONSHIPS.md)** - Entity relationships (@OneToMany, @ManyToOne, @ManyToMany)

**Key Questions for Next Section:**
- How does `@OneToMany` work in User → Posts?
- What is `mappedBy` and why do we need it?
- What are `CascadeType` and `orphanRemoval`?
- How do join tables work for `@ManyToMany`?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics

**Next**: JPA Relationships 🎯
