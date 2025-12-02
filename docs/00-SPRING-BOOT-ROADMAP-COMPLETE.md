# Spring Boot Complete Roadmap - From Zero to Senior
## Using Your "The Dispatch" Blog Project

> **Goal**: Take you from Java basics to senior Spring Boot developer using YOUR actual codebase as the learning tool.

> **Your Level**: You know Java, new to Spring Boot. This guide explains **WHY** things work, not just **HOW** to use them.

---

## 📚 Documentation Structure

This roadmap follows the **Spring Boot Roadmap** best practices and maps each topic to **your actual code**.

---

## 🎯 Phase 1: Java Fundamentals (Review)

### ✅ Topics You Should Know
- **Java 17+ (LTS)** - Your project uses Java 17
- **OOP** - Classes, Interfaces, Inheritance
- **Collections** - List, Set, Map (used everywhere in your code)
- **Streams** - `.map()`, `.filter()`, `.collect()` (used in services)
- **Lambda** - Used in repository queries
- **Records** - Modern DTOs (could refactor your DTOs to Records)

### 📖 Quick Reference Document
**[01-JAVA-ESSENTIALS-FOR-SPRING.md](./tutorial/01-JAVA-ESSENTIALS-FOR-SPRING.md)**
- Collections in your code (User.posts, Post.likedBy)
- Streams in services (converting entities to DTOs)
- Lambda in repositories (custom queries)
- Why Spring Boot requires these features

---

## 🚀 Phase 2: Spring Boot Fundamentals

### 2.1 Spring Core Concepts

**[02-SPRING-CORE-IOC-DI.md](./tutorial/02-SPRING-CORE-IOC-DI.md)** ⭐ START HERE
- **What is IoC (Inversion of Control)?**
  - Example: How Spring creates your `UserService`
  - Why you don't write `new UserService()`

- **What is Dependency Injection?**
  - Your code: `UserController` needs `UserService`
  - How Spring automatically injects it
  - Constructor vs Field injection (why constructor is better)

- **Understanding Annotations**
  - `@Service` - Why UserServiceImpl has this
  - `@Repository` - Why UserRepository has this
  - `@Controller` - Why UserController has this
  - `@Autowired` - How dependencies are injected

- **ApplicationContext**
  - What it is (the Spring container)
  - When it's created (application startup)
  - How it manages your beans

- **Bean Lifecycle**
  - When your services are created
  - `@PostConstruct` - Initialize method
  - `@PreDestroy` - Cleanup method

---

### 2.2 Spring Boot Specifics

**[03-SPRING-BOOT-ESSENTIALS.md](./tutorial/03-SPRING-BOOT-ESSENTIALS.md)**
- **Auto-configuration** - Why you don't write XML
- **Starter Dependencies** - What `spring-boot-starter-web` includes
- **application.properties** - Your database config explained
- **Profiles** - dev vs production configurations
- **Component Scanning** - How Spring finds your classes

**Your Code Examples:**
```java
// Your BlogApplication.java
@SpringBootApplication  // ← What does this do?
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
        // ↑ What happens here? (explained in doc)
    }
}
```

---

## 💾 Phase 3: Data Access with Spring Data JPA

### 3.1 Understanding JPA & Hibernate

**[04-JPA-HIBERNATE-BASICS.md](./tutorial/04-JPA-HIBERNATE-BASICS.md)**
- **What is JPA?** - Java Persistence API (interface)
- **What is Hibernate?** - Implementation of JPA
- **ORM** - Object-Relational Mapping explained

**Your Entity Classes Explained:**
- `@Entity` - Maps class to database table
- `@Table(name = "users")` - Table name
- `@Id` - Primary key
- `@GeneratedValue` - Auto-increment ID

---

### 3.2 Entity Relationships

**[05-JPA-RELATIONSHIPS.md](./tutorial/05-JPA-RELATIONSHIPS.md)** ⭐ IMPORTANT
Using YOUR actual entities:

**One-to-Many: User → Posts**
```java
@Entity
public class User {
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL)
    private List<Post> posts;  // ← One user has many posts
}
```
- **What is `mappedBy`?**
- **What is `cascade`?**
- **When are posts deleted?**

**Many-to-One: Post → User**
```java
@Entity
public class Post {
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;  // ← Many posts belong to one user
}
```
- **What is `@JoinColumn`?**
- **How does this create foreign key?**

**Many-to-Many: Users ↔ Posts (Likes)**
```java
@ManyToMany
@JoinTable(name = "post_likes",
    joinColumns = @JoinColumn(name = "post_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id"))
private Set<User> likedBy;
```
- **Why Set instead of List?**
- **What is join table?**
- **How to add/remove likes?**

---

### 3.3 Spring Data JPA Repositories

**[06-SPRING-DATA-JPA-REPOSITORIES.md](./06-SPRING-DATA-JPA-REPOSITORIES.md)**

**Your Repository Example:**
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

**Explained:**
- **How does Spring implement this?** (you only wrote interface!)
- **Query derivation** - `findByUsername` → `SELECT * FROM users WHERE username = ?`
- **Return types** - Why `Optional`? Why not `null`?

**Custom Queries:**
```java
@Query("SELECT u FROM User u JOIN FETCH u.posts WHERE u.id = :id")
User findByIdWithPosts(@Param("id") Long id);
```
- **What is JPQL?**
- **What is `JOIN FETCH`?** (prevents N+1 problem)
- **When to use custom queries?**

---

### 3.4 Transactions

**[07-TRANSACTIONS.md](./07-TRANSACTIONS.md)** ⭐ CRITICAL

**Your Code:**
```java
@Transactional
public void deleteUser(Long id) {
    User user = userRepository.findById(id).orElseThrow();
    userRepository.delete(user);
}
```

**What `@Transactional` does:**
1. Starts database transaction
2. Executes your code
3. Commits if successful
4. Rolls back if exception

**Why you need it:**
```java
// Without @Transactional:
public void updatePostAndNotify(Long postId) {
    postRepository.update(postId);  // ✅ Succeeds
    notificationService.send();      // ❌ Fails
    // Problem: Post updated but notification not sent!
}

// With @Transactional:
@Transactional
public void updatePostAndNotify(Long postId) {
    postRepository.update(postId);  // ✅ Succeeds
    notificationService.send();      // ❌ Fails
    // Spring rolls back: Post NOT updated ✅
}
```

---

## 🌐 Phase 4: Building REST APIs

### 4.1 Spring MVC Basics

**[08-SPRING-MVC-CONTROLLERS.md](./08-SPRING-MVC-CONTROLLERS.md)**

**Your UserController explained:**
```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        // How does this work?
    }

    @PostMapping("/update")
    public ResponseEntity<String> updateUser(
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        // What is @Valid?
        // What is @RequestBody?
        // What is Authentication?
    }
}
```

**Explained:**
- **`@RestController`** - Combines `@Controller` + `@ResponseBody`
- **`@RequestMapping`** - Base URL for all methods
- **`@GetMapping`** - HTTP GET requests
- **`@PostMapping`** - HTTP POST requests
- **`@RequestBody`** - Converts JSON to Java object
- **`ResponseEntity`** - Control HTTP status codes
- **`Authentication`** - Current logged-in user (Spring Security)

---

### 4.2 Request/Response Flow

**[09-HTTP-REQUEST-FLOW.md](./09-HTTP-REQUEST-FLOW.md)**

**Complete flow for your app:**
```
User clicks "Login" button
    ↓
Angular sends: POST /api/auth/login
    ↓
Spring Security Filter Chain
    ↓
AuthController.login()
    ↓
AuthService.authenticate()
    ↓
UserRepository.findByUsername()
    ↓
Database query
    ↓
Generate JWT token
    ↓
Return token to Angular
    ↓
Angular stores token
    ↓
User is logged in ✅
```

---

### 4.3 DTOs (Data Transfer Objects)

**[10-DTOS-AND-MAPPERS.md](./10-DTOS-AND-MAPPERS.md)** ⭐ IMPORTANT

**Why DTOs?**

❌ **DON'T return entities directly:**
```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userService.getUser(id);  // ❌ BAD!
    // Exposes: password, email, ALL relationships
    // Causes: Infinite recursion (User → Posts → User → Posts...)
}
```

✅ **DO use DTOs:**
```java
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    return userMapper.toDto(user);  // ✅ GOOD!
    // Only exposes: id, username, avatar
}
```

**Your DTOs explained:**
- `UserResponse` - What to send to frontend
- `UpdateUserRequest` - What to receive from frontend
- `PostRequest` - Creating/updating posts
- Why separate request/response DTOs?

---

### 4.4 Validation

**[11-VALIDATION.md](./11-VALIDATION.md)**

**Your validation annotations:**
```java
public class RegisterRequest {
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 4, max = 30, message = "Username must be 4-30 characters")
    private String username;

    @Email(message = "Invalid email format")
    private String email;

    @StrongPassword  // ← Your custom validator!
    private String password;
}
```

**How it works:**
1. User sends invalid data
2. Spring validates before controller method
3. If invalid: Returns 400 Bad Request with error messages
4. If valid: Calls your method

---

## 🔒 Phase 5: Spring Security & JWT

### 5.1 Spring Security Basics

**[12-SPRING-SECURITY-FUNDAMENTALS.md](./12-SPRING-SECURITY-FUNDAMENTALS.md)** ⭐ CRITICAL

**Your SecurityConfig explained:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/posts/all").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        return http.build();
    }
}
```

**What this means:**
- `/auth/login`, `/auth/register` - Anyone can access
- `/posts/all` - Anyone can view posts
- Everything else - Must be logged in
- Stateless - No sessions, use JWT tokens

---

### 5.2 JWT Authentication

**[13-JWT-AUTHENTICATION.md](./13-JWT-AUTHENTICATION.md)** ⭐ MUST UNDERSTAND

**How your authentication works:**

**1. Login:**
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    // 1. Verify username/password
    // 2. Generate JWT token
    String token = jwtService.generateToken(user.getUsername());
    // 3. Return token
    return ResponseEntity.ok(new AuthResponse(token));
}
```

**2. JWT Token Structure:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huIiwiaWF0IjoxNjk...
│────────────────────│─│─────────────────────────────│─│────...
       Header          .        Payload                . Signature
```
- **Header**: Algorithm (HS256)
- **Payload**: Username, issued time, expiration
- **Signature**: Prevents tampering

**3. Using Token:**
```
GET /posts/create
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
    ↓
JwtAuthenticationFilter intercepts
    ↓
Validates token signature
    ↓
Extracts username from token
    ↓
Loads user from database
    ↓
Sets SecurityContext
    ↓
Controller receives Authentication object ✅
```

**Your JwtAuthenticationFilter explained** - How it intercepts every request.

---

### 5.3 Password Security

**[14-PASSWORD-SECURITY.md](./14-PASSWORD-SECURITY.md)**

**Your password handling:**
```java
// Registration:
String plainPassword = request.getPassword();  // "MyPassword123"
String hashedPassword = passwordEncoder.encode(plainPassword);
// Stores: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

// Login:
boolean matches = passwordEncoder.matches(plainPassword, user.getPassword());
```

**BCrypt explained:**
- Never stores plain passwords
- Salt prevents rainbow table attacks
- Why this hash is different every time

---

## 🎨 Phase 6: Best Practices & Patterns

### 6.1 Service Layer Pattern

**[15-SERVICE-LAYER-PATTERN.md](./15-SERVICE-LAYER-PATTERN.md)**

**Your architecture:**
```
Controller (HTTP layer)
    ↓
Service (Business logic)
    ↓
Repository (Database access)
    ↓
Database
```

**Why this separation?**
```java
// ❌ BAD - Controller talks directly to repository
@RestController
public class UserController {
    @Autowired
    private UserRepository userRepository;

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);  // No validation, no business logic!
    }
}

// ✅ GOOD - Service layer handles business logic
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/users")
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        return userService.createUser(request);  // Validation, logic, mapping!
    }
}
```

---

### 6.2 Exception Handling

**[16-EXCEPTION-HANDLING.md](./16-EXCEPTION-HANDLING.md)**

**Your exception handling strategy:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }
}
```

**Custom exceptions in your code:**
- `ResourceNotFoundException` - Entity not found
- `BadRequestException` - Invalid input
- `UnauthorizedException` - Not allowed

---

### 6.3 CORS Configuration

**[17-CORS-EXPLAINED.md](./17-CORS-EXPLAINED.md)**

**Your CORS config:**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowCredentials(true);
    return source;
}
```

**Why needed:**
- Angular runs on `localhost:4200`
- Spring Boot runs on `localhost:8080`
- Browser blocks cross-origin requests without CORS

---

## 🧪 Phase 7: Testing

### 7.1 Unit Testing

**[18-UNIT-TESTING.md](./18-UNIT-TESTING.md)**

**Testing your services:**
```java
@Test
void shouldCreateUser() {
    // Given
    CreateUserRequest request = new CreateUserRequest("john", "john@example.com");
    when(userRepository.save(any())).thenReturn(user);

    // When
    UserResponse response = userService.createUser(request);

    // Then
    assertEquals("john", response.getUsername());
    verify(userRepository).save(any());
}
```

---

### 7.2 Integration Testing

**[19-INTEGRATION-TESTING.md](./19-INTEGRATION-TESTING.md)**

**Testing your controllers:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGetUser() throws Exception {
        mockMvc.perform(get("/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john"));
    }
}
```

---

## 📊 Phase 8: Advanced Topics

### 8.1 Caching

**[20-CACHING.md](./20-CACHING.md)**
- `@Cacheable` - Cache method results
- When to use caching
- Cache eviction strategies

---

### 8.2 Pagination & Filtering

**[21-PAGINATION.md](./21-PAGINATION.md)**

**Paginating posts:**
```java
@GetMapping
public Page<PostResponse> getPosts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return postService.getAllPosts(pageable);
}
```

---

### 8.3 File Upload

**[22-FILE-UPLOAD.md](./22-FILE-UPLOAD.md)**

**Your image upload implementation:**
```java
@PostMapping("/upload")
public ResponseEntity<String> uploadImage(
        @RequestParam("file") MultipartFile file) {
    // Validation
    // Save to disk
    // Return URL
}
```

---

## 🚀 Phase 9: Deployment & DevOps

### 9.1 Docker

**[23-DOCKER.md](./23-DOCKER.md)**

**Your docker-compose.yml explained:**
```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: blog
      POSTGRES_USER: blog
      POSTGRES_PASSWORD: blog
    ports:
      - "5432:5432"

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      - postgres
```

---

### 9.2 Production Considerations

**[24-PRODUCTION-READY.md](./24-PRODUCTION-READY.md)**
- Environment variables for secrets
- Logging strategies
- Monitoring
- Security headers
- Rate limiting

---

## 📈 Learning Path

### Week 1-2: Core Concepts
- [ ] tutorial/01-JAVA-ESSENTIALS-FOR-SPRING.md
- [ ] tutorial/02-SPRING-CORE-IOC-DI.md ⭐
- [ ] tutorial/03-SPRING-BOOT-ESSENTIALS.md

### Week 3-4: Data Access
- [ ] tutorial/04-JPA-HIBERNATE-BASICS.md
- [ ] tutorial/05-JPA-RELATIONSHIPS.md ⭐
- [ ] 06-SPRING-DATA-JPA-REPOSITORIES.md
- [ ] 07-TRANSACTIONS.md ⭐

### Week 5-6: REST APIs
- [ ] 08-SPRING-MVC-CONTROLLERS.md
- [ ] 09-HTTP-REQUEST-FLOW.md
- [ ] 10-DTOS-AND-MAPPERS.md ⭐
- [ ] 11-VALIDATION.md

### Week 7-8: Security
- [ ] 12-SPRING-SECURITY-FUNDAMENTALS.md ⭐
- [ ] 13-JWT-AUTHENTICATION.md ⭐
- [ ] 14-PASSWORD-SECURITY.md

### Week 9-10: Best Practices
- [ ] 15-SERVICE-LAYER-PATTERN.md
- [ ] 16-EXCEPTION-HANDLING.md
- [ ] 17-CORS-EXPLAINED.md

### Week 11-12: Testing & Advanced
- [ ] 18-UNIT-TESTING.md
- [ ] 19-INTEGRATION-TESTING.md
- [ ] 20-CACHING.md
- [ ] 21-PAGINATION.md
- [ ] 22-FILE-UPLOAD.md

### Week 13-14: Production
- [ ] 23-DOCKER.md
- [ ] 24-PRODUCTION-READY.md

---

## 🎯 Success Criteria

After completing this roadmap, you will:

✅ Understand **WHY** Spring Boot exists
✅ Explain IoC and Dependency Injection
✅ Create and map entities with relationships
✅ Build secure REST APIs with JWT
✅ Use transactions properly
✅ Follow best practices (DTOs, validation, exceptions)
✅ Write tests
✅ Deploy to production

**You'll be a confident Spring Boot developer ready for senior positions!** 🚀

---

## 📞 How to Use This Roadmap

1. **Read documents in order** - Each builds on previous
2. **Look at your actual code** - Every example references YOUR codebase
3. **Modify and experiment** - Break things, fix them, learn
4. **Build a feature** - Apply knowledge by adding bookmark feature
5. **Ask questions** - Use debugging guide when stuck

---

**Ready to start? Begin with:** [02-SPRING-CORE-IOC-DI.md](./02-SPRING-CORE-IOC-DI.md) ⭐
