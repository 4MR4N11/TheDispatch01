# DTO (Data Transfer Object) Deep Dive

This document explains WHAT DTOs are, WHY we use them, HOW validation works, and provides line-by-line explanations of all DTO classes in the application.

---

## Table of Contents

1. [What Are DTOs?](#what-are-dtos)
2. [Why Not Expose Entities Directly?](#why-not-expose-entities-directly)
3. [Request DTOs vs Response DTOs](#request-dtos-vs-response-dtos)
4. [Validation Annotations Explained](#validation-annotations-explained)
5. [Custom Validators](#custom-validators)
6. [Lombok Annotations](#lombok-annotations)
7. [Jackson Annotations](#jackson-annotations)
8. [Line-by-Line: Request DTOs](#line-by-line-request-dtos)
9. [Line-by-Line: Response DTOs](#line-by-line-response-dtos)
10. [Entity to DTO Transformation](#entity-to-dto-transformation)
11. [Validation Flow](#validation-flow)
12. [Best Practices](#best-practices)

---

## What Are DTOs?

**DTO (Data Transfer Object)** is a design pattern for transferring data between layers or systems.

### Simple Definition

A DTO is a **plain object** that carries data between processes. It has:
- Fields to hold data
- Getters and setters
- **No business logic**
- **No database annotations**

### Example

```java
// This is a DTO
public class UserResponse {
    private String username;
    private String email;
    // Just data, no logic
}

// This is NOT a DTO (it's an entity)
@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    @OneToMany(mappedBy = "author")
    private List<Post> posts;
    // Database annotations, relationships
}
```

### Where DTOs Are Used

```
Frontend (Angular)
    ↓ HTTP Request with JSON
    ↓
Controller receives DTO (PostRequest)
    ↓
Controller converts DTO → Entity
    ↓
Service processes Entity
    ↓
Repository saves Entity to database
    ↓
Service returns Entity
    ↓
Controller converts Entity → DTO (PostResponse)
    ↓
    ↓ HTTP Response with JSON
    ↓
Frontend (Angular)
```

**Key point:** DTOs are the boundary between your application and the outside world.

---

## Why Not Expose Entities Directly?

### Problem 1: Security

**Bad (exposing entity):**
```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**Response includes:**
```json
{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "password": "$2a$10$N9qo...",  // ❌ PASSWORD HASH EXPOSED!
  "role": "USER",
  "banned": false,
  "posts": [...],
  "comments": [...]
}
```

**Good (using DTO):**
```java
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return new UserResponse(
        user.getUsername(),
        user.getEmail()
        // Only include what's safe
    );
}
```

**Response:**
```json
{
  "username": "alice",
  "email": "alice@example.com"
  // ✅ No password, no internal fields
}
```

### Problem 2: Lazy Loading Issues

**Bad:**
```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**What happens:**
```
1. Hibernate loads User entity
2. Jackson tries to serialize to JSON
3. Jackson accesses user.getPosts()
4. Posts are lazy-loaded → triggers database query
5. Jackson accesses post.getComments()
6. Comments are lazy-loaded → triggers database query
7. N+1 query problem!
```

**Good (using DTO):**
```java
public UserResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    // Transform immediately, no lazy loading during JSON serialization
    return new UserResponse(user.getUsername(), user.getEmail());
}
```

### Problem 3: Circular References

**Bad:**
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

**JSON serialization:**
```
User → Posts → Post → Author (User) → Posts → Post → Author (User) → ...
// ❌ Infinite loop!
```

**Good (using DTOs):**
```java
public class UserResponse {
    private String username;
    private List<PostResponse> posts;  // No circular reference
}

public class PostResponse {
    private String authorUsername;  // Just string, not full User
}
```

### Problem 4: API Coupling

**Bad:**
```java
// Directly expose entity
public User getUser(@PathVariable Long id) { }

// Later, you add field to User entity:
@Entity
public class User {
    private String internalNotes;  // For admin use only
}

// ❌ Automatically exposed in API!
```

**Good:**
```java
// Use DTO - complete control
public class UserResponse {
    private String username;
    private String email;
    // internalNotes NOT included
}

// Change entity freely without breaking API
```

---

## Request DTOs vs Response DTOs

### Request DTOs (Input)

**Purpose:** Validate and receive data from clients

**Characteristics:**
- Validation annotations (`@NotBlank`, `@Email`, `@Size`)
- Only fields client can send
- No derived/calculated fields

**Example:**
```java
public class PostRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @NotBlank
    private String content;

    // No id, no createdAt (server generates these)
}
```

**Used in:**
```java
@PostMapping("/posts/create")
public ResponseEntity<String> createPost(@Valid @RequestBody PostRequest request) {
    // request validated automatically
}
```

### Response DTOs (Output)

**Purpose:** Control what data is sent to clients

**Characteristics:**
- No validation annotations (server data is trusted)
- Include calculated/derived fields
- Nested DTOs for relationships

**Example:**
```java
public class PostResponse {
    private Long id;
    private String authorUsername;
    private String title;
    private String content;
    private Date createdAt;
    private List<CommentResponse> comments;  // Nested DTO
    private int likeCount;  // Calculated field
}
```

**Used in:**
```java
@GetMapping("/posts/{id}")
public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
    Post post = postService.getPost(id);
    return ResponseEntity.ok(transformToDto(post));
}
```

### Comparison

| Aspect | Request DTO | Response DTO |
|--------|-------------|--------------|
| **Validation** | Yes (`@Valid`) | No |
| **Annotations** | Many (`@NotBlank`, `@Size`) | Few (mostly Lombok) |
| **Purpose** | Receive data | Send data |
| **Fields** | Only settable fields | All readable fields + calculated |
| **Example** | RegisterRequest | UserResponse |

---

## Validation Annotations Explained

Validation annotations are processed by **Bean Validation API (JSR-380)** when you use `@Valid` in controllers.

### @NotBlank

```java
@NotBlank(message = "Username is required")
private String username;
```

**What it validates:**
- String is not null
- String is not empty ("")
- String is not whitespace only ("   ")

**Difference from @NotNull:**
- `@NotNull` - allows empty string
- `@NotBlank` - rejects empty string

### @NotNull

```java
@NotNull(message = "Role is required")
private Role role;
```

**What it validates:**
- Value is not null
- Works for any type (String, Integer, objects)

### @Email

```java
@Email(message = "Email must be valid")
private String email;
```

**What it validates:**
- Basic email format: `someone@example.com`
- Uses regex pattern
- Doesn't verify email exists

**Valid:**
- `alice@example.com` ✅
- `user+tag@domain.co.uk` ✅

**Invalid:**
- `notanemail` ❌
- `missing@domain` ❌ (no TLD)
- `@example.com` ❌ (no username)

### @Size

```java
@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
private String username;
```

**What it validates:**
- String length between min and max
- Works for Collections (list size)

**Examples:**
- `"ab"` → ❌ too short (min = 3)
- `"alice"` → ✅ valid
- `"verylongusernamethatexceedslimit"` → ❌ too long (max = 20)

### @Pattern

```java
@Pattern(regexp = "^(image|video|audio)?$", message = "Invalid media type")
private String mediaType;
```

**What it validates:**
- String matches regex pattern
- Powerful for custom formats

**Examples:**
```java
// Phone number
@Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$")
private String phone;  // 123-456-7890

// URL
@Pattern(regexp = "^https?://.*")
private String website;  // http://... or https://...
```

### @Min and @Max

```java
@Min(value = 18, message = "Must be at least 18 years old")
@Max(value = 120, message = "Age seems unrealistic")
private Integer age;
```

**What it validates:**
- Numeric value within range
- Works for Integer, Long, Double, etc.

### @Valid (Cascade Validation)

```java
public class PostRequest {
    @Valid  // ← Validates nested object
    private MediaRequest media;
}

public class MediaRequest {
    @NotBlank
    private String url;
}
```

**What it does:**
- Validates nested objects
- Cascades validation to child DTOs

---

## Custom Validators

### Creating Custom Annotation

**File:** `backend/src/main/java/_blog/blog/validation/StrongPassword.java`

```java
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be strong";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

**Annotation anatomy:**

1. **@Constraint(validatedBy = StrongPasswordValidator.class)**
   - Links annotation to validator class
   - Validator contains validation logic

2. **@Target({ElementType.FIELD, ElementType.PARAMETER})**
   - Where annotation can be used
   - FIELD = on class fields
   - PARAMETER = on method parameters

3. **@Retention(RetentionPolicy.RUNTIME)**
   - Annotation available at runtime
   - Spring can read it via reflection

4. **message()**
   - Default error message
   - Can be overridden: `@StrongPassword(message = "Custom message")`

5. **groups()**
   - For validation groups (advanced feature)
   - Allows conditional validation

6. **payload()**
   - For passing metadata (rarely used)

### Implementing Validator

**File:** `backend/src/main/java/_blog/blog/validation/StrongPasswordValidator.java`

```java
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String PASSWORD_PATTERN =
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        if (!pattern.matcher(password).matches()) {
            return false;
        }

        // Check against common passwords
        String[] commonPasswords = {"password", "12345678", "qwerty123"};
        String lowerPassword = password.toLowerCase();

        for (String common : commonPasswords) {
            if (lowerPassword.equals(common)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                    "This password is too common"
                ).addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
```

**How it works:**

1. **ConstraintValidator<StrongPassword, String>**
   - Generic interface
   - First type: annotation (StrongPassword)
   - Second type: field type (String)

2. **isValid() method**
   - Returns true = validation passes
   - Returns false = validation fails

3. **Regex pattern breakdown:**
   ```
   ^                        Start of string
   (?=.*[a-z])              Must contain lowercase letter
   (?=.*[A-Z])              Must contain uppercase letter
   (?=.*\d)                 Must contain digit
   (?=.*[@$!%*?&])          Must contain special character
   [A-Za-z\d@$!%*?&]{8,}    8+ allowed characters
   $                        End of string
   ```

4. **Custom error messages:**
   ```java
   context.disableDefaultConstraintViolation();
   context.buildConstraintViolationWithTemplate("Custom message")
       .addConstraintViolation();
   ```

### Usage

```java
public class RegisterRequest {
    @StrongPassword  // ← Use custom annotation
    private String password;
}
```

**Validation happens:**
```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
    // If password is weak, automatic 400 Bad Request
}
```

---

## Lombok Annotations

Lombok generates boilerplate code at compile time.

### @Getter and @Setter

```java
@Getter
@Setter
public class UserResponse {
    private String username;
    private String email;
}
```

**Generated code:**
```java
public class UserResponse {
    private String username;
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
```

**Why use it:**
- Reduces code from 20 lines to 5 lines
- Less boilerplate = more readable
- Changes automatically propagate (add field → getter/setter auto-generated)

### @NoArgsConstructor

```java
@NoArgsConstructor
public class UserResponse {
    private String username;
}
```

**Generated:**
```java
public UserResponse() {
}
```

**Why needed:**
- Jackson (JSON library) needs no-args constructor
- Creates empty object, then calls setters

### @AllArgsConstructor

```java
@AllArgsConstructor
public class UserResponse {
    private String username;
    private String email;
}
```

**Generated:**
```java
public UserResponse(String username, String email) {
    this.username = username;
    this.email = email;
}
```

**Why useful:**
- Quick object creation
- Immutable-style construction

### @Builder

```java
@Builder
public class NotificationDto {
    private String message;
    private boolean read;
}
```

**Generated:**
```java
public class NotificationDto {
    // ... fields ...

    public static NotificationDtoBuilder builder() {
        return new NotificationDtoBuilder();
    }

    public static class NotificationDtoBuilder {
        private String message;
        private boolean read;

        public NotificationDtoBuilder message(String message) {
            this.message = message;
            return this;
        }

        public NotificationDtoBuilder read(boolean read) {
            this.read = read;
            return this;
        }

        public NotificationDto build() {
            return new NotificationDto(message, read);
        }
    }
}
```

**Usage:**
```java
NotificationDto notification = NotificationDto.builder()
    .message("New comment on your post")
    .read(false)
    .build();
```

**Benefits:**
- Fluent API (readable)
- Optional fields (don't need to pass nulls)
- Clear what each value represents

---

## Jackson Annotations

Jackson converts between Java objects and JSON.

### @JsonProperty

```java
public class RegisterRequest {
    @JsonProperty("firstname")
    private String firstName;
}
```

**What it does:**
- Maps JSON field name to Java field name
- Handles naming differences

**JSON:**
```json
{
  "firstname": "Alice"
}
```

**Java:**
```java
request.getFirstName()  // "Alice"
```

**Why needed:**
- JSON uses camelCase or snake_case
- Java uses camelCase
- `@JsonProperty` bridges the gap

**Without @JsonProperty:**
- JSON `firstname` → Java field must be named `firstname`
- Or JSON must be `firstName` → Java `firstName`

**With @JsonProperty:**
- JSON can be `firstname`
- Java field is `firstName` (readable)

### Serialization vs Deserialization

**Deserialization (JSON → Java):**
```
POST /auth/register
Body: {"firstname": "Alice", "lastname": "Smith"}
    ↓
Jackson reads @JsonProperty("firstname")
    ↓
Calls setFirstName("Alice")
    ↓
RegisterRequest object created
```

**Serialization (Java → JSON):**
```
UserResponse response = new UserResponse();
response.setFirstName("Alice");
    ↓
Jackson reads @JsonProperty("firstname")
    ↓
Generates JSON: {"firstname": "Alice"}
    ↓
Sends to client
```

---

## Line-by-Line: Request DTOs

### RegisterRequest

**File:** `backend/src/main/java/_blog/blog/dto/RegisterRequest.java`

```java
package _blog.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import _blog.blog.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
```

**Imports:**
- `@JsonProperty` - Map JSON field names
- `@StrongPassword` - Custom password validator
- Validation annotations - `@Email`, `@NotBlank`, `@Size`
- Lombok annotations - `@Getter`, `@Setter`, etc.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
```

**Class annotations:**
- `@Getter/@Setter` - Generate getters/setters for all fields
- `@NoArgsConstructor` - Default constructor for Jackson
- `@AllArgsConstructor` - Constructor with all fields

```java
@NotBlank(message = "Username is required")
@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
private String username;
```

**Username validation:**
1. `@NotBlank` - Cannot be null, empty, or whitespace
2. `@Size(min = 3, max = 20)` - Length between 3-20 characters

**Error messages:**
- Custom messages shown to user
- Example: User submits `"ab"` → "Username must be between 3 and 20 characters"

```java
@NotBlank(message = "Email is required")
@Email(message = "Email must be valid")
private String email;
```

**Email validation:**
1. `@NotBlank` - Must have value
2. `@Email` - Must match email format

**Valid examples:**
- `alice@example.com` ✅
- `user+tag@domain.co.uk` ✅

**Invalid examples:**
- ` ` (blank) → "Email is required"
- `notanemail` → "Email must be valid"

```java
@NotBlank(message = "Password is required")
@StrongPassword
private String password;
```

**Password validation:**
1. `@NotBlank` - Must have value
2. `@StrongPassword` - Custom validator checks:
   - Minimum 8 characters
   - At least one uppercase (A-Z)
   - At least one lowercase (a-z)
   - At least one digit (0-9)
   - At least one special character (@$!%*?&)
   - Not a common password

**Why strong password validation?**
- Security best practice
- Prevents weak passwords like "password123"
- Reduces risk of account compromise

```java
@NotBlank(message = "First name is required")
@Size(min = 1, max = 30, message = "First name must be between 1 and 30 characters")
@JsonProperty("firstname")
private String firstName;
```

**First name:**
1. `@NotBlank` - Required
2. `@Size(min = 1, max = 30)` - Length limit
3. `@JsonProperty("firstname")` - JSON uses lowercase

**JSON mapping:**
- Frontend sends: `{"firstname": "Alice"}`
- Java receives: `firstName = "Alice"`

```java
@NotBlank(message = "Last name is required")
@Size(min = 1, max = 30, message = "Last name must be between 1 and 30 characters")
@JsonProperty("lastname")
private String lastName;

private String avatar;
```

**Last name:**
- Same validation as first name

**Avatar:**
- No validation (optional field)
- Can be null or empty
- Usually set later during profile update

### PostRequest

**File:** `backend/src/main/java/_blog/blog/dto/PostRequest.java`

```java
@Size(max = 200, message = "Title must not exceed 200 characters")
String title;
```

**Title:**
- Optional (no `@NotBlank`)
- If provided, max 200 characters
- Posts can exist without titles

```java
@NotBlank(message = "Content is required")
@Size(min = 1, max = 100000, message = "Content must be between 1 and 100000 characters")
String content;
```

**Content:**
- Required (`@NotBlank`)
- Between 1-100,000 characters
- Main body of post

**Why 100,000 limit?**
- Reasonable limit for post length
- Prevents abuse (posting entire books)
- Database column size limit

```java
@Pattern(regexp = "^(image|video|audio)?$", message = "Media type must be 'image', 'video', 'audio', or empty")
String media_type;
```

**Media type validation:**
- Regex: `^(image|video|audio)?$`
- Allows: "image", "video", "audio", or empty string
- `?` means optional (zero or one)

**Why Pattern?**
- Prevents invalid values like "pdf" or "executable"
- Only allowed media types

```java
@Size(max = 2048, message = "Media URL must not exceed 2048 characters")
@Pattern(regexp = "^(https?://.*|/uploads/.*)?$", message = "Must be HTTP/HTTPS URL or upload path")
String media_url;
```

**Media URL validation:**
1. Max 2048 characters (reasonable URL length)
2. Pattern: `^(https?://.*|/uploads/.*)?$`
   - `https?://.*` - External URLs (http or https)
   - `/uploads/.*` - Internal uploads
   - `?` - Optional (post without media)

**Valid examples:**
- `https://example.com/image.jpg` ✅
- `/uploads/images/photo.png` ✅
- ` ` (empty) ✅

**Invalid examples:**
- `ftp://example.com/file` ❌ (not http/https)
- `javascript:alert('xss')` ❌ (XSS attempt)

### CommentRequest

**File:** `backend/src/main/java/_blog/blog/dto/CommentRequest.java`

```java
@NotBlank(message = "Comment content cannot be empty")
@Size(min = 1, max = 5000, message = "Comment must be between 1 and 5000 characters")
private String content;
```

**Comment validation:**
- Must have content (`@NotBlank`)
- Max 5000 characters (shorter than posts)
- Comments are typically briefer

**Why 5000 limit?**
- Encourages concise comments
- Prevents comment spam
- Reasonable discussion length

### UpdateProfileRequest

**File:** `backend/src/main/java/_blog/blog/dto/UpdateProfileRequest.java`

```java
@Size(min=4, max=30, message = "Username must be between 4 and 30 characters")
private String username;

@Email(message = "Invalid email format")
private String email;

@JsonProperty("firstname")
private String firstName;

@JsonProperty("lastname")
private String lastName;

private String avatar;

@Size(min=8, max=50, message = "Password must be between 8 and 50 characters")
private String newPassword;

private String currentPassword;
```

**Key differences from RegisterRequest:**

1. **All fields optional**
   - No `@NotBlank` annotations
   - User can update only specific fields
   - Example: update only avatar, keep everything else

2. **Password change fields**
   - `newPassword` - New password to set
   - `currentPassword` - Current password for verification
   - Both required if changing password

3. **Less strict validation**
   - `@Size` without `@NotBlank`
   - Allows null values
   - Only validates IF value provided

**Usage example:**
```json
// Update only email
{
  "email": "newemail@example.com"
}

// Update password
{
  "currentPassword": "oldpass",
  "newPassword": "newpass"
}

// Update multiple fields
{
  "firstname": "Alice",
  "lastname": "Smith",
  "avatar": "/uploads/avatar.jpg"
}
```

### LoginRequest

**File:** `backend/src/main/java/_blog/blog/dto/LoginRequest.java`

```java
@JsonProperty("usernameOrEmail")
private String usernameOrEmail;

private String password;

@JsonProperty("username")
public void setUsername(String username) {
    this.usernameOrEmail = username;
}

public String getUsername() {
    return this.usernameOrEmail;
}

@JsonProperty("email")
public void setEmail(String email) {
    this.usernameOrEmail = email;
}
```

**Flexible field mapping:**
- Single internal field: `usernameOrEmail`
- Three JSON names: "usernameOrEmail", "username", "email"

**How it works:**

Frontend can send any of these:
```json
{"username": "alice", "password": "pass"}
{"email": "alice@example.com", "password": "pass"}
{"usernameOrEmail": "alice", "password": "pass"}
```

All three map to same field: `usernameOrEmail`

**Why?**
- Better UX - users can login with username OR email
- Single field internally - simpler code
- Jackson handles mapping

---

## Line-by-Line: Response DTOs

### UserResponse

**File:** `backend/src/main/java/_blog/blog/dto/UserResponse.java`

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String firstname;
    private String lastname;
    private String username;
    private String email;
    private String avatar;
    private String role;
    private boolean banned;
    private List<String> subscriptions;
    private List<PostResponse> posts;
}
```

**Fields explained:**

1. **id** - User's database ID
2. **firstname, lastname, username** - Basic info
3. **email** - Conditionally included (privacy)
4. **avatar** - Profile picture URL
5. **role** - "USER" or "ADMIN"
6. **banned** - Account status
7. **subscriptions** - List of usernames (not full User objects)
8. **posts** - Nested PostResponse DTOs

**No validation annotations:**
- Response DTOs don't need validation
- Server-generated data is trusted
- Validation is for INPUT, not OUTPUT

**Flattened relationships:**
```java
private List<String> subscriptions;  // Just usernames
```

Instead of:
```java
private List<User> subscriptions;  // Full User objects (❌ circular reference risk)
```

### PostResponse

**File:** `backend/src/main/java/_blog/blog/dto/PostResponse.java`

```java
public class PostResponse {
    private Long id;
    private String author;           // Username, not full User
    private String authorAvatar;
    private String title;
    private String content;
    private String media_type;
    private String media_url;
    private boolean hidden;
    private List<CommentResponse> comments;  // Nested DTOs
    private Date created_at;
    private Date updated_at;
    private long likeCount;          // Calculated field
    private List<String> likedByUsernames;  // Flattened
}
```

**Key features:**

1. **Flattened author:**
   - `String author` instead of `User author`
   - Avoids circular references
   - Reduces JSON size

2. **Nested comments:**
   - `List<CommentResponse>` - Full comment DTOs
   - Includes comment data in post response
   - Reduces API calls (one request gets post + comments)

3. **Calculated fields:**
   - `likeCount` - Derived from `likedBy.size()`
   - Not stored in database
   - Computed at transformation time

4. **Timestamps:**
   - `created_at`, `updated_at` - Date objects
   - Jackson serializes to ISO 8601: `"2024-01-15T10:30:00Z"`

### CommentResponse

**File:** `backend/src/main/java/_blog/blog/dto/CommentResponse.java`

```java
public class CommentResponse {
    private Long id;
    private String authorUsername;
    private String authorAvatar;
    private String content;
    private Date createdAt;
}
```

**Simple DTO:**
- Only essential comment data
- No nested relationships
- Flat structure

**No post reference:**
- Comments are always returned IN CONTEXT of a post
- No need to include post ID
- Reduces JSON size

### NotificationDto

**File:** `backend/src/main/java/_blog/blog/dto/NotificationDto.java`

```java
@Builder
public class NotificationDto {
    private Long id;
    private String actorUsername;
    private String actorAvatar;
    private NotificationType type;
    private String message;
    private Long postId;
    private Long commentId;
    private boolean read;
    private Date createdAt;
}
```

**@Builder annotation:**
- Allows fluent object creation
- Useful for optional fields

**Usage:**
```java
NotificationDto notification = NotificationDto.builder()
    .actorUsername("alice")
    .type(NotificationType.LIKE)
    .message("liked your post")
    .postId(42L)
    .read(false)
    .build();
```

**Enum field:**
```java
private NotificationType type;
```

- Enum serializes to string: `"LIKE"`, `"COMMENT"`, `"FOLLOW"`
- Type-safe in Java
- Readable in JSON

### AuthResponse

**File:** `backend/src/main/java/_blog/blog/dto/AuthResponse.java`

```java
public class AuthResponse {
    private String token;
    private String usernameOrEmail;
    private String role;
}
```

**Returned after login:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "usernameOrEmail": "alice",
  "role": "USER"
}
```

**Why include token in body?**
- Cookie stores token (for automatic sending)
- Body also includes token (for frontend storage/display)
- Provides flexibility (some clients don't use cookies)

---

## Entity to DTO Transformation

### Manual Transformation

**Controller code:**
```java
@GetMapping("/{id}")
public PostResponse getPost(@PathVariable Long id) {
    Post post = postService.getPostById(id);

    // Manual transformation
    List<CommentResponse> comments = new ArrayList<>();
    for (Comment c : post.getComments()) {
        comments.add(new CommentResponse(
            c.getId(),
            c.getAuthor().getUsername(),
            c.getAuthor().getAvatar(),
            c.getContent(),
            c.getCreatedAt()
        ));
    }

    return new PostResponse(
        post.getId(),
        post.getAuthor().getUsername(),
        post.getAuthor().getAvatar(),
        post.getTitle(),
        post.getContent(),
        post.getMediaType(),
        post.getMediaUrl(),
        post.isHidden(),
        comments,
        post.getCreatedAt(),
        post.getUpdatedAt(),
        post.getLikedBy().size(),
        post.getLikedBy().stream()
            .map(User::getUsername)
            .toList()
    );
}
```

**Pros:**
- Explicit and clear
- Full control over transformation
- Easy to debug

**Cons:**
- Verbose and repetitive
- Error-prone when adding fields
- Tedious for many DTOs

### Helper Methods

**Better approach:**
```java
public class PostMapper {
    public static PostResponse toResponse(Post post) {
        return new PostResponse(
            post.getId(),
            post.getAuthor().getUsername(),
            // ... all fields
        );
    }

    public static CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
            comment.getId(),
            comment.getAuthor().getUsername(),
            // ... all fields
        );
    }
}
```

**Usage:**
```java
@GetMapping("/{id}")
public PostResponse getPost(@PathVariable Long id) {
    Post post = postService.getPostById(id);
    return PostMapper.toResponse(post);
}
```

**Pros:**
- Centralized transformation logic
- Reusable across controllers
- Easier to maintain

### MapStruct (Advanced)

**Annotation-based mapping:**
```java
@Mapper
public interface PostMapper {
    @Mapping(source = "author.username", target = "authorUsername")
    @Mapping(source = "likedBy", target = "likeCount", qualifiedByName = "countLikes")
    PostResponse toResponse(Post post);

    @Named("countLikes")
    default int countLikes(Set<User> likedBy) {
        return likedBy.size();
    }
}
```

**Usage:**
```java
@Autowired
private PostMapper postMapper;

@GetMapping("/{id}")
public PostResponse getPost(@PathVariable Long id) {
    Post post = postService.getPostById(id);
    return postMapper.toResponse(post);
}
```

**Pros:**
- Minimal boilerplate
- Compile-time code generation (fast)
- Type-safe

**Cons:**
- Learning curve
- Complex mappings can be tricky
- Additional dependency

---

## Validation Flow

### Complete Flow: POST /auth/register

```
1. Client sends JSON:
   {
     "username": "alice",
     "email": "alice@example.com",
     "password": "Pass123!",
     "firstname": "Alice",
     "lastname": "Smith"
   }

2. Spring DispatcherServlet receives request

3. Jackson deserializes JSON → RegisterRequest object:
   RegisterRequest request = new RegisterRequest();
   request.setUsername("alice");
   request.setEmail("alice@example.com");
   // ... etc

4. Spring sees @Valid annotation in controller:
   public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request)

5. Spring's Validator checks constraints:

   a. @NotBlank on username:
      ✅ "alice" is not blank

   b. @Size(min=3, max=20) on username:
      ✅ "alice" has 5 characters (3 ≤ 5 ≤ 20)

   c. @Email on email:
      ✅ "alice@example.com" matches email pattern

   d. @StrongPassword on password:
      - Calls StrongPasswordValidator.isValid()
      - Checks regex pattern
      - Checks common passwords
      ✅ "Pass123!" is strong

6. If ALL validations pass:
   - Controller method executes
   - User is registered

7. If ANY validation fails:
   - Spring throws MethodArgumentNotValidException
   - Returns 400 Bad Request with error details:
     {
       "errors": [
         "Password must be at least 8 characters long..."
       ]
     }
```

### Validation Error Response

**Default error format:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "password",
      "message": "Password must be strong",
      "rejectedValue": "weak"
    },
    {
      "field": "email",
      "message": "Email must be valid",
      "rejectedValue": "notanemail"
    }
  ]
}
```

**Custom error handling:**
```java
@ControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }
}
```

**Cleaner response:**
```json
{
  "password": "Password must be strong",
  "email": "Email must be valid"
}
```

---

## Best Practices

### 1. Always Use DTOs for API Boundaries

**❌ Bad:**
```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

**✅ Good:**
```java
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return UserMapper.toResponse(user);
}
```

### 2. Separate Request and Response DTOs

**❌ Bad:**
```java
public class UserDto {
    private Long id;  // Should not be in request
    private String password;  // Should not be in response
    // ... mixing concerns
}
```

**✅ Good:**
```java
public class UserRequest {
    private String username;
    private String password;  // For input only
}

public class UserResponse {
    private Long id;  // For output only
    private String username;
    // No password!
}
```

### 3. Validate All Input

**❌ Bad:**
```java
public class PostRequest {
    private String title;  // No validation
    private String content;  // Could be empty
}
```

**✅ Good:**
```java
public class PostRequest {
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(min = 1, max = 100000)
    private String content;
}
```

### 4. Use Meaningful Validation Messages

**❌ Bad:**
```java
@NotBlank(message = "Error")
private String username;
```

**✅ Good:**
```java
@NotBlank(message = "Username is required")
@Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
private String username;
```

### 5. Flatten Nested Relationships

**❌ Bad:**
```java
public class PostResponse {
    private User author;  // Full entity
    private List<Comment> comments;  // Full entities
}
```

**✅ Good:**
```java
public class PostResponse {
    private String authorUsername;  // Just string
    private List<CommentResponse> comments;  // DTOs
}
```

### 6. Include Calculated Fields in Response DTOs

**✅ Good:**
```java
public class PostResponse {
    private List<String> likedByUsernames;
    private int likeCount;  // Calculated from likedByUsernames.size()
    private boolean isLikedByCurrentUser;  // Calculated
}
```

### 7. Use Lombok to Reduce Boilerplate

**❌ Without Lombok (40+ lines):**
```java
public class UserResponse {
    private String username;

    public UserResponse() {}

    public UserResponse(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
```

**✅ With Lombok (8 lines):**
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String username;
}
```

---

## Key Takeaways

### What Are DTOs?

- **Plain objects** for transferring data
- **No business logic**
- **No database annotations**
- **Boundary** between application and outside world

### Why Use DTOs?

- **Security** - Don't expose password hashes, internal fields
- **Lazy loading** - Avoid N+1 problems during JSON serialization
- **Circular references** - Prevent infinite loops
- **API stability** - Change entities without breaking API

### Request vs Response

- **Request DTOs** - Validation, only settable fields
- **Response DTOs** - No validation, all readable fields + calculated

### Validation

- **Bean Validation API** - `@NotBlank`, `@Size`, `@Email`
- **Custom validators** - Implement `ConstraintValidator`
- **Automatic** - Spring validates when `@Valid` present

### Transformation

- **Manual** - Explicit but verbose
- **Helper methods** - Centralized, reusable
- **MapStruct** - Annotation-based, compile-time

---

**Backend documentation complete!** Next would be Frontend (Angular) deep dive - components, services, routing, RxJS, signals, and how everything connects to the backend API. Continue?