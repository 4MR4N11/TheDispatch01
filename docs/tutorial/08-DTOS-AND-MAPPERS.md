# DTOs and Mappers: Data Transfer Objects

> **Core Concept**: Understand why we don't expose entities directly in REST APIs, how DTOs protect your application, and how to convert between entities and DTOs.

---

## Table of Contents
1. [What is a DTO?](#1-what-is-a-dto)
2. [Why Not Expose Entities Directly?](#2-why-not-expose-entities-directly)
3. [DTO Types](#3-dto-types)
4. [Creating DTOs](#4-creating-dtos)
5. [Mapping Strategies](#5-mapping-strategies)
6. [Nested DTOs](#6-nested-dtos)
7. [Validation in DTOs](#7-validation-in-dtos)
8. [Best Practices](#8-best-practices)

---

## 1. What is a DTO?

### 1.1 Definition

**DTO** (Data Transfer Object) is a simple object that carries data between processes.

**Purpose:**
- Transfer data across boundaries (Controller ↔ Service ↔ Client)
- Hide internal entity structure
- Control what data is exposed

**Your DTOs:**
```
dto/
├── UserResponse.java          - User data for API responses
├── PostResponse.java          - Post data for API responses
├── RegisterRequest.java       - Registration form data
├── LoginRequest.java          - Login credentials
├── PostRequest.java           - Create/update post data
└── CommentRequest.java        - Create comment data
```

### 1.2 DTO vs Entity

**Entity (Database Model):**
```java
@Entity
public class User {
    private Long id;
    private String username;
    private String password;          // ⚠️ SENSITIVE
    private String email;
    private List<Post> posts;         // Relationship
    private Set<Post> likedPosts;     // Relationship
    private Set<Subscription> subscriptions;
}
```

**DTO (API Model):**
```java
public class UserResponse {
    private Long id;
    private String username;
    // NO password field!          ✅ SECURE
    private String email;
    private List<PostResponse> posts;    // DTOs, not entities
    private List<String> subscriptions;  // Just usernames
}
```

**Key Differences:**
- DTO: No JPA annotations, no relationships, no sensitive data
- Entity: JPA annotations, relationships, all data

---

## 2. Why Not Expose Entities Directly?

### 2.1 Security Issues

**❌ Exposing Entity Directly:**
```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findById(id).get();
    }
}
```

**Response (JSON):**
```json
{
  "id": 1,
  "username": "john",
  "password": "$2a$10$slYQmyNdGzTn7ZYnPcwNbuH...",  // ⚠️ EXPOSED!
  "email": "john@example.com",
  "role": "USER",
  "banned": false,
  "posts": [...],
  "likedPosts": [...],
  "subscriptions": [...]
}
```

**Problems:**
1. **Password exposed** (even if hashed)
2. **All relationships loaded** (N+1 queries, huge response)
3. **Database structure exposed** (column names, relationships)
4. **Cannot customize response** (stuck with entity structure)

### 2.2 Circular Reference Problems

**Entity relationships are bidirectional:**
```java
@Entity
public class User {
    @OneToMany(mappedBy = "author")
    private List<Post> posts;
}

@Entity
public class Post {
    @ManyToOne
    private User author;
}
```

**Serializing User to JSON:**
```
User
 └─> posts: [Post1, Post2]
      ├─> Post1.author: User (same user!)
      │    └─> posts: [Post1, Post2]  // ⚠️ INFINITE LOOP!
      │         └─> ...
      └─> Post2.author: User
           └─> posts: [Post1, Post2]  // ⚠️ INFINITE LOOP!
```

**Error:**
```
com.fasterxml.jackson.databind.JsonMappingException:
Infinite recursion (StackOverflowError)
```

**Solutions with Entities (not recommended):**
```java
// 1. @JsonIgnore (breaks JSON completely)
@OneToMany(mappedBy = "author")
@JsonIgnore
private List<Post> posts;

// 2. @JsonManagedReference / @JsonBackReference (messy)
@OneToMany(mappedBy = "author")
@JsonManagedReference
private List<Post> posts;
```

**✅ Best Solution: Use DTOs** (no circular references!)

### 2.3 Performance Issues

**Exposing entities causes lazy loading problems:**
```java
@GetMapping("/users")
public List<User> getAllUsers() {
    return userRepository.findAll();  // 1 query
    // Jackson serializes users to JSON
    // Triggers lazy loading for each relationship!
}
```

**Generated SQL (N+1 problem):**
```sql
SELECT * FROM users;              -- 1 query (100 users)
SELECT * FROM posts WHERE author_id = 1;   -- Query for user 1's posts
SELECT * FROM posts WHERE author_id = 2;   -- Query for user 2's posts
-- ... 100 queries!
SELECT * FROM subscriptions WHERE subscriber_id = 1;  -- More queries!
-- ... 100 more queries!
-- Total: 201+ queries for one API call!
```

### 2.4 API Versioning Issues

**Entity structure changes:**
```java
@Entity
public class User {
    private String username;

    // Later, you split name into firstName/lastName:
    private String firstName;   // NEW
    private String lastName;    // NEW
}
```

**If exposing entities directly:**
- API response changes automatically
- Breaks all clients
- No backwards compatibility

**With DTOs:**
- Keep old DTO for API v1
- Create new DTO for API v2
- Both versions work

---

## 3. DTO Types

### 3.1 Response DTOs

**Purpose:** Send data from server to client.

**Your UserResponse:**
**`UserResponse.java:15-26`**
```java
public class UserResponse {
    private Long id;
    private String firstname;
    private String lastname;
    private String username;
    private String email;
    private String avatar;
    private String role;
    private boolean banned;
    private List<String> subscriptions;      // Simplified (just usernames)
    private List<PostResponse> posts;        // Nested DTOs
}
```

**Key Points:**
- No password field
- Role as String (not enum)
- Subscriptions simplified (just usernames, not full User objects)
- Posts as nested DTOs

**Your PostResponse:**
**`PostResponse.java:15-29`**
```java
public class PostResponse {
    private Long id;
    private String author;                   // Just username, not full User
    private String authorAvatar;
    private String title;
    private String content;
    private String media_type;
    private String media_url;
    private boolean hidden;
    private List<CommentResponse> comments;  // Nested DTOs
    private Date created_at;
    private Date updated_at;
    private long likeCount;                  // Computed field
    private List<String> likedByUsernames;   // Simplified
}
```

**Features:**
- Author represented as username string (not User object)
- Computed field: `likeCount` (not in entity)
- Flattened structure (no circular references)

### 3.2 Request DTOs

**Purpose:** Receive data from client to server.

**Your RegisterRequest:**
**`RegisterRequest.java:18-43`**
```java
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword  // Custom validator
    private String password;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 30)
    @JsonProperty("firstname")     // Maps JSON "firstname" to "firstName"
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 30)
    @JsonProperty("lastname")
    private String lastName;

    private String avatar;
}
```

**Features:**
- Validation annotations
- `@JsonProperty` for field name mapping
- Only fields needed for registration (no ID, no role, etc.)

**Your LoginRequest:**
```java
public class LoginRequest {
    private String username;
    private String password;
    // That's it! Just what's needed for login
}
```

**Your PostRequest:**
```java
public class PostRequest {
    private String title;
    private String content;
    private String media_url;
    // No author field - determined from Authentication
    // No likes, comments - those come later
}
```

---

## 4. Creating DTOs

### 4.1 DTO Structure Best Practices

**✅ Good DTO:**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;

    // Optional: Static factory method
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail()
        );
    }
}
```

**Features:**
- Lombok annotations (reduce boilerplate)
- All fields private
- Simple types (no entities, no JPA annotations)
- Optional factory method for conversion

### 4.2 Handling Null Values

**Your PostResponse has backward-compatible constructor:**
**`PostResponse.java:31-46`**
```java
// Constructor without like information for backward compatibility
public PostResponse(Long id, String author, String authorAvatar, String title,
                   String content, String media_type, String media_url,
                   List<CommentResponse> comments, Date created_at, Date updated_at) {
    this.id = id;
    this.author = author;
    this.authorAvatar = authorAvatar;
    this.title = title;
    this.content = content;
    this.media_type = media_type;
    this.media_url = media_url;
    this.hidden = false;
    this.comments = comments;
    this.created_at = created_at;
    this.updated_at = updated_at;
    this.likeCount = 0;              // Default value
    this.likedByUsernames = List.of();  // Empty list (not null)
}
```

**Best Practices:**
- Provide default values for optional fields
- Use empty collections instead of null
- Multiple constructors for different use cases

### 4.3 @JsonProperty for Field Mapping

**Your RegisterRequest:**
**`RegisterRequest.java:34-40`**
```java
@JsonProperty("firstname")
private String firstName;

@JsonProperty("lastname")
private String lastName;
```

**Why?**
- **JSON uses snake_case or lowercase:** `{"firstname": "John"}`
- **Java uses camelCase:** `String firstName`
- `@JsonProperty` bridges the gap

**Without @JsonProperty:**
```json
{
  "firstName": "John",   // Client must send camelCase
  "lastName": "Smith"
}
```

**With @JsonProperty:**
```json
{
  "firstname": "John",   // Client sends lowercase
  "lastname": "Smith"
}
```

---

## 5. Mapping Strategies

### 5.1 Manual Mapping (Your Approach)

**Your UserMapper:**
**`UserMapper.java:11-21`**
```java
public class UserMapper {
    public static User toEntity(RegisterRequest request, PasswordEncoder passwordEncoder) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))  // Hash password
                .avatar(request.getAvatar())
                .role(Role.USER)  // Default role
                .build();
    }
}
```

**Advantages:**
- Full control over mapping
- Can apply transformations (password hashing)
- Set default values (role)
- No external dependencies

**Usage in Service:**
```java
@Service
public class UserServiceImpl {
    public User register(RegisterRequest request) {
        User user = UserMapper.toEntity(request, passwordEncoder);
        return userRepository.save(user);
    }
}
```

### 5.2 Your PostMapper with Business Logic

**`PostMapper.java:11-22`**
```java
public static Post toEntity(PostRequest request, User author) {
    String mediaType = detectMediaType(request.getMedia_url());  // Business logic

    return Post.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .mediaType(mediaType)          // Computed field
            .mediaUrl(request.getMedia_url())
            .author(author)                // Injected parameter
            .likedBy(new HashSet<>())      // Initialize empty set
            .build();
}
```

**`detectMediaType()` method:**
**`PostMapper.java:24-60`**
```java
private static String detectMediaType(String url) {
    if (url == null || url.isEmpty()) {
        return null;
    }

    String lowerUrl = url.toLowerCase();

    // Image formats
    if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$")) {
        return "image";
    }

    // Video formats
    if (lowerUrl.matches(".*\\.(mp4|webm|ogg|mov|avi|wmv|flv|mkv)$")) {
        return "video";
    }

    // Check for hosting domains
    if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
        return "video";
    }

    return null;
}
```

**Key Features:**
- Business logic in mapper
- Regex pattern matching
- Domain detection
- Returns computed field

### 5.3 Inline Mapping in Controller

**Your PostController:**
**`PostController.java:51-70`**
```java
@GetMapping("/my-posts")
public ResponseEntity<List<PostResponse>> getMyPosts(Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());
    List<Post> posts = postService.getPostsByIdWithCommentsAndLikes(user.getId());
    List<PostResponse> respPosts = new ArrayList<>();

    for (Post p : posts) {
        List<CommentResponse> comments = commentService.getCommentsRespByPost(p.getId());
        respPosts.add(new PostResponse(
            p.getId(),
            p.getAuthor().getUsername(),      // Extract username
            p.getAuthor().getAvatar(),
            p.getTitle(),
            p.getContent(),
            p.getMediaType(),
            p.getMediaUrl(),
            p.isHidden(),
            comments,                         // Nested DTOs
            p.getCreatedAt(),
            p.getUpdatedAt(),
            p.getLikedBy().size(),            // Compute like count
            p.getLikedBy().stream()
                .map(User::getUsername)       // Extract usernames
                .toList()
        ));
    }
    return ResponseEntity.ok(respPosts);
}
```

**Pros:**
- Quick for simple mappings
- No need for separate mapper class

**Cons:**
- Code duplication if used in multiple places
- Controller becomes cluttered
- Hard to test mapping logic

### 5.4 MapStruct (Industry Standard - Not in Your Code)

**Alternative approach:**
```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", constant = "USER")
    User toEntity(RegisterRequest request);

    @Mapping(target = "subscriptions", source = "subscriptions", qualifiedByName = "subscriptionsToUsernames")
    UserResponse toDto(User user);
}
```

**Advantages:**
- Auto-generated code (compile-time)
- Type-safe
- Fast performance
- Reduces boilerplate

---

## 6. Nested DTOs

### 6.1 Handling Relationships

**Your UserResponse includes nested PostResponse:**
**`UserResponse.java:15-26`**
```java
public class UserResponse {
    private Long id;
    private String username;
    private List<String> subscriptions;   // Simplified: just usernames
    private List<PostResponse> posts;     // Nested DTOs
}
```

**Your PostResponse includes nested CommentResponse:**
**`PostResponse.java:15-29`**
```java
public class PostResponse {
    private Long id;
    private String author;                   // Simplified: just username
    private List<CommentResponse> comments;  // Nested DTOs
    private List<String> likedByUsernames;   // Simplified: just usernames
}
```

### 6.2 Levels of Detail

**Strategy: Different detail levels based on context**

**Minimal User (for lists):**
```java
public class UserSummary {
    private Long id;
    private String username;
    private String avatar;
    // No posts, no subscriptions
}
```

**Detailed User (for single user):**
```java
public class UserDetail {
    private Long id;
    private String username;
    private String email;
    private String avatar;
    private List<PostResponse> posts;        // Include posts
    private List<String> subscriptions;
    private int followerCount;
    private int followingCount;
}
```

**Usage:**
```java
// List endpoint - minimal data
@GetMapping("/users")
public List<UserSummary> getUsers() {
    return users.stream()
        .map(UserSummary::fromEntity)
        .toList();
}

// Detail endpoint - full data
@GetMapping("/users/{id}")
public UserDetail getUser(@PathVariable Long id) {
    User user = userService.getUser(id);
    return UserDetail.fromEntity(user);
}
```

### 6.3 Avoiding Deep Nesting

**❌ Too Deep:**
```java
public class UserResponse {
    private List<PostResponse> posts;
        // Each PostResponse has:
        private List<CommentResponse> comments;
            // Each CommentResponse has:
            private UserResponse author;   // ⚠️ CIRCULAR!
                // Each UserResponse has:
                private List<PostResponse> posts;  // ⚠️ INFINITE NESTING!
}
```

**✅ Flatten Where Possible:**
```java
public class PostResponse {
    private String author;  // Just username, not full UserResponse
    private String authorAvatar;
    private List<CommentResponse> comments;
}

public class CommentResponse {
    private String author;  // Just username
    private String content;
    private Date createdAt;
    // No nested author object
}
```

---

## 7. Validation in DTOs

### 7.1 Standard Validation Annotations

**Your RegisterRequest:**
**`RegisterRequest.java:19-30`**
```java
@NotBlank(message = "Username is required")
@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
private String username;

@NotBlank(message = "Email is required")
@Email(message = "Email must be valid")
private String email;

@NotBlank(message = "Password is required")
@StrongPassword  // Custom validator
private String password;
```

**Common Validation Annotations:**
```java
// Required
@NotNull       // Field cannot be null
@NotBlank      // String cannot be blank (not just whitespace)
@NotEmpty      // Collection/String cannot be empty

// Size
@Size(min = 3, max = 20)     // String/Collection size
@Min(18)                      // Number minimum
@Max(100)                     // Number maximum

// Format
@Email                        // Valid email format
@Pattern(regexp = "...")      // Custom regex pattern

// Numbers
@Positive                     // Must be > 0
@PositiveOrZero              // Must be >= 0
@Negative                     // Must be < 0

// Dates
@Past                         // Must be in the past
@Future                       // Must be in the future
```

### 7.2 Custom Validators

**Your @StrongPassword validator:**
```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "Password must be strong";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

**Implementation:**
```java
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 8) {
            return false;
        }

        // Check for uppercase, lowercase, digit, special character
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.matches(".*[!@#$%^&*()].*");

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
```

### 7.3 Validation in Controller

**Trigger validation with @Valid:**
```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    //                                        ↑ Triggers validation
    // If validation fails, Spring returns 400 Bad Request automatically
    userService.register(request);
    return ResponseEntity.ok(response);
}
```

**Error Response (automatic):**
```json
{
  "timestamp": "2025-11-04T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "errors": [
    {
      "field": "username",
      "message": "Username must be between 3 and 20 characters"
    },
    {
      "field": "email",
      "message": "Email must be valid"
    },
    {
      "field": "password",
      "message": "Password must be strong"
    }
  ]
}
```

---

## 8. Best Practices

### 8.1 DTO Naming Conventions

**✅ Clear Naming:**
```java
UserResponse         // Response DTO
UserRequest          // Generic request
RegisterRequest      // Specific request (registration)
LoginRequest         // Specific request (login)
UpdateProfileRequest // Specific request (profile update)
```

**❌ Unclear Naming:**
```java
UserDTO              // What kind? Request or response?
User                 // Conflicts with entity
UserData             // Too generic
```

### 8.2 Separation of Concerns

**✅ Separate DTOs for different operations:**
```java
// Registration - needs password, no ID
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
}

// Update profile - no password (separate endpoint), needs ID
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String avatar;
}

// User response - no password, has ID
public class UserResponse {
    private Long id;
    private String username;
    private String email;
}
```

**❌ Single DTO for everything:**
```java
public class UserDTO {
    private Long id;           // Not needed for registration
    private String password;   // Not needed for response
    private String email;
    // Used for registration, update, and response (confusing!)
}
```

### 8.3 Immutability (Optional)

**Immutable DTO with Records (Java 14+):**
```java
public record UserResponse(
    Long id,
    String username,
    String email,
    List<String> subscriptions
) {}

// Usage:
UserResponse user = new UserResponse(1L, "john", "john@example.com", List.of());
// user.setUsername(...); // ❌ Compile error - immutable!
```

**Benefits:**
- Thread-safe
- Prevents accidental modification
- Cleaner code (no getters/setters)

### 8.4 Documentation

**Document DTOs with JavaDoc:**
```java
/**
 * DTO for user registration.
 * Used by POST /auth/register endpoint.
 *
 * Password is validated for strength (min 8 chars, uppercase, lowercase, digit, special char).
 * Username must be unique in the system.
 */
public class RegisterRequest {
    /**
     * Unique username (3-20 characters)
     */
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    /**
     * Password (will be hashed before storage)
     * Must meet strength requirements
     */
    @NotBlank
    @StrongPassword
    private String password;
}
```

### 8.5 Testing DTOs

**Test serialization/deserialization:**
```java
@Test
void shouldSerializeUserResponse() {
    UserResponse user = new UserResponse(1L, "john", "john@example.com");

    String json = objectMapper.writeValueAsString(user);

    assertThat(json).contains("\"id\":1");
    assertThat(json).contains("\"username\":\"john\"");
}

@Test
void shouldDeserializeRegisterRequest() {
    String json = """
        {
          "username": "john",
          "email": "john@example.com",
          "password": "SecurePass123!"
        }
        """;

    RegisterRequest request = objectMapper.readValue(json, RegisterRequest.class);

    assertThat(request.getUsername()).isEqualTo("john");
    assertThat(request.getEmail()).isEqualTo("john@example.com");
}
```

---

## Key Takeaways

### What You Learned

1. **Why DTOs?**
   - Security (hide sensitive data like passwords)
   - Prevent circular references
   - Performance (control what data is loaded)
   - API versioning (change DTOs without changing entities)

2. **DTO Types**
   - **Response DTOs** - Send data to client (UserResponse, PostResponse)
   - **Request DTOs** - Receive data from client (RegisterRequest, PostRequest)

3. **Mapping**
   - **Manual mappers** (your approach) - full control
   - **Inline mapping** - quick but repetitive
   - **MapStruct** - industry standard (auto-generation)

4. **Nested DTOs**
   - Flatten where possible (author as string, not object)
   - Use summary DTOs for lists
   - Avoid deep nesting (causes performance issues)

5. **Validation**
   - `@Valid` in controller triggers validation
   - Standard annotations (@NotBlank, @Email, @Size)
   - Custom validators (@StrongPassword)

6. **Best Practices**
   - Clear naming (UserResponse, RegisterRequest)
   - Separate DTOs for different operations
   - Use empty collections instead of null
   - Document with JavaDoc

---

## What's Next?

You now understand DTOs and how to transfer data safely. Next:

**→ [09-SPRING-SECURITY-FUNDAMENTALS.md](./09-SPRING-SECURITY-FUNDAMENTALS.md)** - Security architecture and authentication

**Key Questions for Next Section:**
- How does Spring Security work?
- What are security filters?
- How is authentication stored?
- What is the SecurityContext?

**Completed**:
- ✅ Java Essentials
- ✅ Spring Core (IoC, DI)
- ✅ Spring Boot Essentials
- ✅ JPA & Hibernate Basics
- ✅ JPA Relationships
- ✅ Spring Data JPA Repositories
- ✅ REST Controllers & HTTP Flow
- ✅ DTOs and Mappers

**Next**: Spring Security Fundamentals 🎯
