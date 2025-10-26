# Advanced Concepts - DTOs, Mappers, Validation, and Error Handling

---

## Table of Contents

1. [DTOs - Data Transfer Objects](#dtos---data-transfer-objects)
2. [Mappers - Entity to DTO Conversion](#mappers---entity-to-dto-conversion)
3. [Validation Deep Dive](#validation-deep-dive)
4. [Error Handling](#error-handling)
5. [Exception Handling Best Practices](#exception-handling-best-practices)
6. [Testing Basics](#testing-basics)

---

## DTOs - Data Transfer Objects

### What is a DTO?

**DTO (Data Transfer Object)** is a pattern where you create simple objects specifically for transferring data between layers (especially over the network).

### Why NOT send Entities directly?

**Bad Practice**:
```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).get();
}
```

**Problems with this**:
1. **Security**: Exposes password, internal IDs, everything!
2. **Circular References**: User has Posts, Post has Comments, Comment has User → infinite JSON loop
3. **Over-fetching**: Client gets data they don't need
4. **Breaking Changes**: If you change entity structure, API breaks
5. **Database Leakage**: Lazy loading can trigger unexpected queries

**Good Practice**:
```java
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).get();
    return UserMapper.toResponse(user);
}
```

### DTO Pattern in Action

#### Request DTO - Incoming Data

**File**: `backend/src/main/java/_blog/blog/dto/PostRequest.java`

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title;

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 100000)
    String content;

    @Pattern(regexp = "^(image|video|audio)?$")
    String media_type;

    @Size(max = 2048)
    @Pattern(regexp = "^(https?://.*|/uploads/.*)?$")
    String media_url;
}
```

**Why this is useful**:
- ✅ Only accepts fields you want to accept
- ✅ Client can't set `id`, `createdAt`, `author` directly
- ✅ Validation annotations on fields
- ✅ Clear contract of what API expects

#### Response DTO - Outgoing Data

**File**: `backend/src/main/java/_blog/blog/dto/PostResponse.java`

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private String author;          // Just username, not full User object
    private String authorAvatar;
    private String title;
    private String content;
    private String media_type;
    private String media_url;
    private boolean hidden;
    private List<CommentResponse> comments;
    private Date created_at;
    private Date updated_at;
    private long likeCount;         // Computed field
    private List<String> likedByUsernames;  // Just usernames, not User objects
}
```

**Why this is useful**:
- ✅ Only sends what frontend needs
- ✅ Flattened structure (no deep nesting)
- ✅ No sensitive data (passwords, internal IDs)
- ✅ Computed fields (likeCount) included
- ✅ Can evolve independently from Entity

---

## Mappers - Entity to DTO Conversion

### What is a Mapper?

A **Mapper** is a class that converts between Entities (database layer) and DTOs (API layer).

### PostMapper Example

**File**: `backend/src/main/java/_blog/blog/mapper/PostMapper.java`

```java
public class PostMapper {

    public static Post toEntity(PostRequest request, User author) {
        String mediaType = detectMediaType(request.getMedia_url());

        return Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .mediaType(mediaType)
                .mediaUrl(request.getMedia_url())
                .author(author)
                .likedBy(new HashSet<>())
                .build();
    }

    private static String detectMediaType(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String lowerUrl = url.toLowerCase();

        // Image formats
        if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|svg)$")) {
            return "image";
        }

        // Video formats
        if (lowerUrl.matches(".*\\.(mp4|webm|ogg|mov)$")) {
            return "video";
        }

        // Video hosting
        if (lowerUrl.contains("youtube.com") || lowerUrl.contains("vimeo.com")) {
            return "video";
        }

        return null;
    }
}
```

### Mapper Explanation:

#### 1. Static Methods
```java
public static Post toEntity(PostRequest request, User author)
```
- **Static** because mappers are stateless
- No need to create instance: `PostMapper.toEntity(request, author)`
- Utility class pattern

#### 2. Builder Pattern
```java
return Post.builder()
    .title(request.getTitle())
    .content(request.getContent())
    .author(author)
    .build();
```
- Uses Lombok's `@Builder`
- Fluent API for creating objects
- Clear what fields are being set

#### 3. Business Logic in Mapper
```java
String mediaType = detectMediaType(request.getMedia_url());
```
- Smart detection of media type from URL
- Uses regex matching
- Checks file extensions and hosting domains
- Returns: "image", "video", "audio", or null

**Why this is smart**:
- Frontend doesn't need to specify media type
- Backend automatically determines it
- Less error-prone

### Complete Mapping Flow

**Creating a Post**:
```java
// Controller
@PostMapping("/create")
public ResponseEntity<String> createPost(@RequestBody PostRequest request, Authentication auth) {
    User user = userService.getUserByUsername(auth.getName());

    // 1. DTO → Entity (via Mapper)
    Post post = PostMapper.toEntity(request, user);

    // 2. Save to database
    Post savedPost = postRepository.save(post);

    // 3. Entity → DTO (manual in service)
    PostResponse response = new PostResponse(
        savedPost.getId(),
        savedPost.getAuthor().getUsername(),
        savedPost.getTitle(),
        // ... all fields
    );

    return ResponseEntity.ok("Post created");
}
```

**Direction Flow**:
```
Client (JSON)
    ↓
PostRequest (DTO)
    ↓ [Mapper.toEntity()]
Post (Entity)
    ↓ [Repository.save()]
Database
    ↓ [Repository.findById()]
Post (Entity)
    ↓ [Manual mapping or Mapper.toResponse()]
PostResponse (DTO)
    ↓
Client (JSON)
```

---

## Validation Deep Dive

### Built-in Validation Annotations

**File**: `backend/src/main/java/_blog/blog/dto/PostRequest.java`

```java
public class PostRequest {
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title;

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 100000, message = "Content must be between 1 and 100000 characters")
    String content;

    @Pattern(regexp = "^(image|video|audio)?$", message = "Media type must be 'image', 'video', 'audio', or empty")
    String media_type;

    @Size(max = 2048, message = "Media URL must not exceed 2048 characters")
    @Pattern(regexp = "^(https?://.*|/uploads/.*)?$", message = "Media URL must be a valid HTTP/HTTPS URL or relative upload path")
    String media_url;
}
```

### Validation Annotations Explained:

#### 1. @NotBlank
```java
@NotBlank(message = "Content is required")
String content;
```
- **Checks**: Not null, not empty string, not just whitespace
- **Difference from @NotNull**: `@NotNull` allows empty strings
- **Difference from @NotEmpty**: `@NotEmpty` allows whitespace-only strings

**Examples**:
```java
@NotBlank String content;

content = "Hello";     // ✅ Valid
content = "";          // ❌ Invalid (blank)
content = "   ";       // ❌ Invalid (whitespace only)
content = null;        // ❌ Invalid (null)
```

#### 2. @Size
```java
@Size(min = 1, max = 100000, message = "Content must be between 1 and 100000 characters")
String content;
```
- **Checks**: String length, Collection size, Array length
- **Attributes**: `min`, `max`, `message`

**Examples**:
```java
@Size(max = 200) String title;
@Size(min = 8, max = 50) String password;
@Size(min = 1) List<String> tags;
```

#### 3. @Pattern
```java
@Pattern(regexp = "^(image|video|audio)?$", message = "...")
String media_type;
```
- **Checks**: String matches regex pattern
- **Powerful**: Can validate any format (email, phone, URL, etc.)

**Common Patterns**:
```java
// Email
@Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
String email;

// Phone (US)
@Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$")
String phone;

// URL
@Pattern(regexp = "^https?://.*$")
String url;

// Alphanumeric only
@Pattern(regexp = "^[a-zA-Z0-9]+$")
String username;
```

#### 4. @Email
```java
@Email(message = "Invalid email format")
String email;
```
- **Checks**: Valid email format
- Built-in, no need to write regex

#### 5. Other Common Annotations

```java
// Null checks
@NotNull        // Cannot be null
@Null           // Must be null

// Numeric constraints
@Min(18)        // Minimum value
@Max(100)       // Maximum value
@Positive       // Must be > 0
@PositiveOrZero // Must be >= 0
@Negative       // Must be < 0
@DecimalMin("0.01")  // Minimum decimal value
@DecimalMax("999.99") // Maximum decimal value

// Date/Time
@Past           // Must be in the past
@PastOrPresent  // Must be in past or now
@Future         // Must be in the future
@FutureOrPresent // Must be in future or now

// Boolean
@AssertTrue     // Must be true
@AssertFalse    // Must be false
```

### Custom Validation

**Create your own validator**:

```java
// 1. Create annotation
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsernameValidator.class)
public @interface ValidUsername {
    String message() default "Invalid username";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 2. Create validator
public class UsernameValidator implements ConstraintValidator<ValidUsername, String> {

    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        if (username == null) return false;

        // Custom logic
        if (username.length() < 3) return false;
        if (username.contains(" ")) return false;
        if (username.matches(".*[^a-zA-Z0-9_].*")) return false;

        return true;
    }
}

// 3. Use it
public class RegisterRequest {
    @ValidUsername
    private String username;
}
```

### Validation in Action

**Controller**:
```java
@PostMapping("/create")
public ResponseEntity<String> createPost(@Valid @RequestBody PostRequest request) {
    // If validation fails, Spring automatically returns 400 Bad Request
    // with error messages
}
```

**What happens when validation fails**:

**Request**:
```json
{
  "title": "This title is way too long and exceeds the maximum allowed length of 200 characters which will cause a validation error to be thrown by the Spring validation framework making this request fail",
  "content": ""
}
```

**Response** (400 Bad Request):
```json
{
  "timestamp": "2025-10-23T20:00:00",
  "status": 400,
  "error": "Bad Request",
  "errors": [
    {
      "field": "title",
      "message": "Title must not exceed 200 characters"
    },
    {
      "field": "content",
      "message": "Content is required"
    }
  ]
}
```

---

## Error Handling

### The Problem

**Without error handling**:
```java
@GetMapping("/{id}")
public Post getPost(@PathVariable Long id) {
    return postRepository.findById(id).get();  // ❌ CRASHES if not found
}
```

**What happens**: HTTP 500 Internal Server Error (ugly!)

### Solution 1: Optional.orElseThrow()

```java
@GetMapping("/{id}")
public Post getPost(@PathVariable Long id) {
    return postRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Post not found"));
}
```

**Better, but**:
- RuntimeException gives HTTP 500 (should be 404)
- Generic error message
- No structure

### Solution 2: Custom Exceptions

**Step 1: Create exception**:
```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**Step 2: Use it**:
```java
@GetMapping("/{id}")
public Post getPost(@PathVariable Long id) {
    return postRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + id));
}
```

**Step 3: Handle it globally**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

**ErrorResponse DTO**:
```java
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}
```

**Now when post not found**:

**Response** (404 Not Found):
```json
{
  "status": 404,
  "message": "Post not found with id: 42",
  "timestamp": "2025-10-23T20:00:00"
}
```

### Common Custom Exceptions

```java
// 404 Not Found
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// 400 Bad Request
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}

// 403 Forbidden
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}

// 401 Unauthorized
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

// 409 Conflict
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

### Usage Examples

```java
// 404 - Resource not found
Post post = postRepository.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

// 403 - Not authorized to perform action
if (!post.getAuthor().equals(currentUser)) {
    throw new ForbiddenException("You cannot edit this post");
}

// 400 - Invalid input
if (request.getTitle().isEmpty()) {
    throw new BadRequestException("Title cannot be empty");
}

// 409 - Duplicate resource
if (userRepository.findByEmail(email).isPresent()) {
    throw new ConflictException("Email already exists");
}
```

---

## Exception Handling Best Practices

### 1. @RestControllerAdvice

**Create a global exception handler**:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handle specific exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        return new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            request.getDescription(false)
        );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex) {
        return new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
    }

    // Handle validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        return new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            errors
        );
    }

    // Handle JWT authentication errors
    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleJwtException(JwtException ex) {
        return new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Invalid or expired token",
            LocalDateTime.now()
        );
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred",
            LocalDateTime.now()
        );
    }
}
```

### 2. Structured Error Responses

**Simple Error**:
```java
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}
```

**Validation Error** (with field-level details):
```java
@Getter
@AllArgsConstructor
public class ValidationErrorResponse {
    private int status;
    private String message;
    private Map<String, String> fieldErrors;
}
```

**Detailed Error** (for debugging):
```java
@Getter
@AllArgsConstructor
public class DetailedErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private List<String> details;
}
```

---

## Testing Basics

### Why Test?

1. **Confidence**: Know your code works
2. **Refactoring**: Change code without breaking things
3. **Documentation**: Tests show how code should be used
4. **Bug Prevention**: Catch bugs before production

### Types of Tests

```
Unit Tests
    ↓ Test individual methods/classes
Integration Tests
    ↓ Test components working together
End-to-End Tests
    ↓ Test entire application flow
```

### Unit Test Example

**Test a service method**:

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void createPost_Success() {
        // Arrange (Given)
        User author = new User();
        author.setId(1L);
        author.setUsername("john");

        PostRequest request = new PostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");

        Post savedPost = Post.builder()
            .id(1L)
            .title("Test Post")
            .content("Test Content")
            .author(author)
            .build();

        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        // Act (When)
        Post result = postService.createPost(request, author);

        // Assert (Then)
        assertNotNull(result);
        assertEquals("Test Post", result.getTitle());
        assertEquals("Test Content", result.getContent());
        assertEquals(author, result.getAuthor());

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void getPostById_NotFound_ThrowsException() {
        // Arrange
        Long postId = 999L;
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            postService.getPostById(postId);
        });
    }
}
```

### Integration Test Example

**Test controller with real HTTP**:

```java
@SpringBootTest
@AutoConfigureMockMvc
class PostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "john", roles = "USER")
    void createPost_Success() throws Exception {
        PostRequest request = new PostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");

        mockMvc.perform(post("/posts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Post has been created."));
    }

    @Test
    void createPost_Unauthorized() throws Exception {
        PostRequest request = new PostRequest();
        request.setTitle("Test Post");

        mockMvc.perform(post("/posts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
```

### Test Annotations Explained:

```java
@ExtendWith(MockitoExtension.class)  // Enable Mockito for unit tests
@SpringBootTest                       // Load full Spring context
@AutoConfigureMockMvc                // Enable MockMvc for HTTP testing
@Mock                                 // Create mock object
@InjectMocks                          // Inject mocks into this object
@WithMockUser                         // Simulate authenticated user
```

### Test Structure (AAA Pattern):

```java
@Test
void testMethod() {
    // Arrange (Given) - Set up test data
    User user = new User();
    user.setUsername("john");

    // Act (When) - Execute the code being tested
    String result = userService.getUserFullName(user);

    // Assert (Then) - Verify the result
    assertEquals("John Doe", result);
}
```

---

## Angular Error Handling

### Frontend Error Handler

**File**: `frontend/src/app/core/utils/error-handler.ts`

```typescript
export class ErrorHandler {
  static getErrorMessage(error: any, defaultMessage: string): string {
    if (error.error?.message) {
      return error.error.message;
    }

    if (error.message) {
      return error.message;
    }

    if (error.status === 404) {
      return 'Resource not found';
    }

    if (error.status === 403) {
      return 'You do not have permission to perform this action';
    }

    if (error.status === 401) {
      return 'Please log in to continue';
    }

    if (error.status === 500) {
      return 'Server error. Please try again later';
    }

    return defaultMessage;
  }
}
```

### Using Error Handler

```typescript
// In component
deletePost(postId: number) {
  this.apiService.deletePost(postId).subscribe({
    next: () => {
      this.notificationService.success('Post deleted successfully');
      this.loadPosts();
    },
    error: (error) => {
      const message = ErrorHandler.getErrorMessage(error, 'Failed to delete post');
      this.notificationService.error(message);
    }
  });
}
```

### HTTP Error Interceptor

```typescript
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Redirect to login
        const router = inject(Router);
        router.navigate(['/login']);
      }

      if (error.status === 403) {
        // Show forbidden message
        const notificationService = inject(NotificationService);
        notificationService.error('Access denied');
      }

      return throwError(() => error);
    })
  );
};
```

---

## Key Takeaways

### DTOs
- ✅ Separate API layer from database layer
- ✅ Control what data is exposed
- ✅ Add validation annotations
- ✅ Prevent circular references

### Mappers
- ✅ Convert between Entities and DTOs
- ✅ Keep conversion logic in one place
- ✅ Use static methods for stateless mappers
- ✅ Can include business logic (like media type detection)

### Validation
- ✅ Use built-in annotations: @NotBlank, @Size, @Pattern
- ✅ Add custom validators when needed
- ✅ Validate at API boundary (controllers)
- ✅ Return clear error messages

### Error Handling
- ✅ Create custom exceptions for different scenarios
- ✅ Use @RestControllerAdvice for global handling
- ✅ Return structured error responses
- ✅ Log errors for debugging
- ✅ Never expose internal details to clients

### Testing
- ✅ Write unit tests for services
- ✅ Write integration tests for controllers
- ✅ Use AAA pattern (Arrange, Act, Assert)
- ✅ Mock dependencies in unit tests
- ✅ Test both success and failure cases

---

**Happy Learning!** 🚀
